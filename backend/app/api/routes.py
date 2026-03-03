from datetime import date, datetime, timedelta, timezone
from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.auth import (
    SESSION_COOKIE_NAME,
    create_magic_token,
    create_session_token,
    require_user,
    verify_magic_token,
    verify_session_token,
)
from app.core.config import settings
from app.core.crypto import encrypt_secret
from app.db.session import get_db
from app.integrations.google import build_google_oauth_url, exchange_google_code_for_tokens
from app.integrations.stripe_client import connect_stripe_key, ingest_recent_stripe_signals
from app.models.models import Approvals, DailyFocus, Initiatives, Integrations, PreparedActions, Users
from app.schemas.approval import ApprovalDecisionRequest, ApprovalDecisionResponse
from app.schemas.auth import AuthMeResponse, MagicLinkRequest, MagicLinkRequestResponse, MagicLinkVerifyRequest
from app.schemas.integrations import StripeConnectRequest
from app.schemas.onboarding import OnboardingRequest, OnboardingResponse
from app.schemas.today import DailyRunRequest, TodayResponse
from app.services.actions import apply_decision, create_audit_log
from app.services.daily_planner import run_daily_plan_for_user
from app.workers.tasks import retry_failed_jobs_task, schedule_all_users_daily_plan_task

router = APIRouter(prefix="/api")


def _get_or_create_user_by_email(db: Session, email: str) -> Users:
    existing = db.scalars(select(Users).where(Users.email == email)).first()
    if existing:
        return existing

    user = Users(
        email=email,
        timezone="UTC",
        primary_goal_text="",
        revenue_model="",
        weekly_availability="",
        preferences_json={},
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.post("/auth/magic-link/request", response_model=MagicLinkRequestResponse)
def request_magic_link(payload: MagicLinkRequest, db: Session = Depends(get_db)) -> MagicLinkRequestResponse:
    _get_or_create_user_by_email(db, payload.email)
    token = create_magic_token(payload.email)
    link = f"{settings.frontend_url}/login?token={token}"
    return MagicLinkRequestResponse(status="ok", magic_link=link)


@router.post("/auth/magic-link/verify")
def verify_magic_link(payload: MagicLinkVerifyRequest, response: Response, db: Session = Depends(get_db)) -> dict:
    email = verify_magic_token(payload.token)
    user = _get_or_create_user_by_email(db, email)

    session_token = create_session_token(user.id)
    response.set_cookie(
        key=SESSION_COOKIE_NAME,
        value=session_token,
        httponly=True,
        samesite="lax",
        secure=False,
        max_age=60 * 60 * 24 * 7,
        path="/",
    )
    return {"status": "authenticated"}


@router.get("/auth/me", response_model=AuthMeResponse)
def auth_me(current_user: Users = Depends(require_user)) -> AuthMeResponse:
    return AuthMeResponse(user_id=current_user.id, email=current_user.email)


@router.post("/auth/logout")
def logout(response: Response) -> dict:
    response.delete_cookie(SESSION_COOKIE_NAME, path="/")
    return {"status": "logged_out"}


@router.post("/onboarding", response_model=OnboardingResponse)
def onboarding(
    payload: OnboardingRequest,
    db: Session = Depends(get_db),
    current_user: Users = Depends(require_user),
) -> OnboardingResponse:
    current_user.timezone = payload.timezone
    current_user.primary_goal_text = payload.primary_goal_text
    current_user.revenue_model = payload.revenue_model
    current_user.weekly_availability = payload.weekly_availability
    current_user.preferences_json = payload.preferences_json

    db.query(Initiatives).filter(Initiatives.user_id == current_user.id).delete()
    for title in payload.initiatives[:3]:
        if title.strip():
            db.add(Initiatives(user_id=current_user.id, title=title.strip()))

    create_audit_log(db, current_user.id, "onboarding_completed", "Completed onboarding", {})
    db.commit()
    return OnboardingResponse(user_id=current_user.id)


@router.post("/daily/run")
def run_daily_plan(
    payload: DailyRunRequest,
    db: Session = Depends(get_db),
    current_user: Users = Depends(require_user),
) -> dict:
    try:
        run_daily_plan_for_user(db, current_user, force_refresh=payload.force_refresh, source="manual_api")
    except Exception as exc:
        create_audit_log(
            db,
            current_user.id,
            "daily_plan_failed",
            "Daily plan generation failed",
            {"error": str(exc)},
        )
        db.commit()
        raise HTTPException(status_code=400, detail=f"Daily plan failed: {exc}") from exc
    db.commit()
    return {"status": "ok"}


@router.post("/daily/schedule-all")
def schedule_all_daily_runs(current_user: Users = Depends(require_user)) -> dict:
    schedule_all_users_daily_plan_task.delay()
    return {"status": "queued"}


@router.post("/daily/retry-failed")
def retry_failed_daily_runs(current_user: Users = Depends(require_user)) -> dict:
    retry_failed_jobs_task.delay()
    return {"status": "queued"}


@router.get("/today", response_model=TodayResponse)
def get_today(db: Session = Depends(get_db), current_user: Users = Depends(require_user)) -> TodayResponse:
    today = date.today()
    focus = db.scalars(
        select(DailyFocus).where(DailyFocus.user_id == current_user.id, DailyFocus.date == today)
    ).first()
    if not focus:
        return TodayResponse(
            date=str(today),
            primary_focus="",
            why=[],
            success="",
            micro_steps=[],
            confidence=0,
            approvals=[],
        )

    rows = db.execute(
        select(Approvals, PreparedActions)
        .join(PreparedActions, Approvals.prepared_action_id == PreparedActions.id)
        .where(
            Approvals.user_id == current_user.id,
            PreparedActions.linked_focus_id == focus.id,
            Approvals.status == "pending_review",
        )
    ).all()

    approvals = [
        {
            "id": approval.id,
            "title": prepared.title,
            "type": prepared.type,
            "details": prepared.payload_json.get("details", prepared.payload_json.get("description", "")),
            "status": approval.status,
        }
        for approval, prepared in rows
    ]

    return TodayResponse(
        date=str(focus.date),
        primary_focus=focus.focus_text,
        why=focus.why_bullets_json,
        success=focus.success_text,
        micro_steps=focus.micro_steps_json,
        confidence=focus.confidence,
        approvals=approvals,
    )


@router.post("/approvals/{approval_id}/decision", response_model=ApprovalDecisionResponse)
def decide_approval(
    approval_id: str,
    payload: ApprovalDecisionRequest,
    db: Session = Depends(get_db),
    current_user: Users = Depends(require_user),
) -> ApprovalDecisionResponse:
    approval = db.get(Approvals, approval_id)
    if not approval or approval.user_id != current_user.id:
        raise HTTPException(status_code=404, detail="Approval not found")
    if approval.status != "pending_review":
        raise HTTPException(status_code=400, detail="Approval already decided")
    if payload.decision not in {"approve", "decline"}:
        raise HTTPException(status_code=400, detail="Invalid decision")

    try:
        status = apply_decision(db, current_user, approval, payload.decision, payload.reason)
    except Exception as exc:
        create_audit_log(
            db,
            current_user.id,
            "approval_execution_failed",
            "Approval execution failed",
            {"approval_id": approval.id, "error": str(exc)},
        )
        db.commit()
        raise HTTPException(status_code=400, detail=f"Execution failed: {exc}") from exc
    approval.decided_at = datetime.utcnow()
    db.commit()
    return ApprovalDecisionResponse(status=status)


@router.get("/integrations/google/oauth/start")
def google_oauth_start(current_user: Users = Depends(require_user)) -> dict:
    if not settings.google_client_id:
        raise HTTPException(status_code=400, detail="GOOGLE_CLIENT_ID is not configured")

    state = create_session_token(current_user.id)
    auth_url = build_google_oauth_url(settings.google_client_id, settings.google_redirect_uri, state)
    return {"auth_url": auth_url}


@router.get("/integrations/google/oauth/callback")
def google_oauth_callback(
    code: str = Query(...),
    state: str = Query(...),
    db: Session = Depends(get_db),
) -> Response:
    user_id = verify_session_token(state)
    user = db.get(Users, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    token_data = exchange_google_code_for_tokens(
        code,
        settings.google_client_id,
        settings.google_client_secret,
        settings.google_redirect_uri,
    )

    scopes = token_data.get("scope", "").split()
    expires_in = int(token_data.get("expires_in", 3600))
    expiry = (datetime.now(timezone.utc) + timedelta(seconds=expires_in)).isoformat()

    integration = db.scalars(
        select(Integrations).where(Integrations.user_id == user.id, Integrations.provider == "google")
    ).first()
    if not integration:
        integration = Integrations(user_id=user.id, provider="google")
        db.add(integration)

    integration.status = "connected"
    integration.scopes_json = scopes + [f"expiry:{expiry}", f"expires_in:{expires_in}"]
    integration.token_encrypted = encrypt_secret(token_data["access_token"])
    refresh_token = token_data.get("refresh_token")
    integration.refresh_token_encrypted = encrypt_secret(refresh_token) if refresh_token else None

    create_audit_log(db, user.id, "integration_connected", "Connected google", {})
    db.commit()

    redirect_url = f"{settings.frontend_url}/settings?google={quote('connected')}"
    return Response(status_code=302, headers={"Location": redirect_url})


@router.post("/integrations/stripe/connect")
def stripe_connect(
    payload: StripeConnectRequest,
    db: Session = Depends(get_db),
    current_user: Users = Depends(require_user),
) -> dict:
    connect_stripe_key(db, current_user.id, payload.api_key)
    create_audit_log(db, current_user.id, "integration_connected", "Connected stripe", {})
    db.commit()
    return {"status": "connected"}


@router.post("/signals/stripe/ingest")
def stripe_ingest(db: Session = Depends(get_db), current_user: Users = Depends(require_user)) -> dict:
    ingested = ingest_recent_stripe_signals(db, current_user.id)
    create_audit_log(db, current_user.id, "signals_ingested", f"Ingested {ingested} stripe signals", {})
    db.commit()
    return {"status": "ok", "ingested": ingested}


@router.post("/integrations/{provider}/disconnect")
def disconnect_integration(
    provider: str,
    db: Session = Depends(get_db),
    current_user: Users = Depends(require_user),
) -> dict:
    integration = db.scalars(
        select(Integrations).where(Integrations.user_id == current_user.id, Integrations.provider == provider)
    ).first()
    if not integration:
        raise HTTPException(status_code=404, detail="Integration not found")

    integration.status = "disconnected"
    integration.token_encrypted = None
    integration.refresh_token_encrypted = None
    create_audit_log(db, current_user.id, "integration_disconnected", f"Disconnected {provider}", {})
    db.commit()
    return {"status": "disconnected"}
