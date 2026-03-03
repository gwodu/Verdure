from datetime import datetime, timedelta, timezone
import hashlib

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.integrations.google import create_calendar_event, create_gmail_draft, get_valid_google_access_token, send_gmail_draft
from app.models.models import Approvals, AuditLog, EpisodicEvents, PreparedActions, Users


def _build_idempotency_key(user_id: str, linked_focus_id: str, approval_type: str, title: str, details: str) -> str:
    raw = f"{user_id}|{linked_focus_id}|{approval_type}|{title}|{details}"
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    return f"pa_{digest}"


def create_audit_log(db: Session, user_id: str, action_type: str, summary: str, metadata: dict | None = None) -> None:
    db.add(
        AuditLog(
            user_id=user_id,
            actor="user",
            action_type=action_type,
            summary=summary,
            metadata_json=metadata or {},
        )
    )


def create_event(db: Session, user_id: str, event_type: str, summary: str, refs: dict | None = None) -> None:
    db.add(
        EpisodicEvents(
            user_id=user_id,
            type=event_type,
            summary=summary,
            refs_json=refs or {},
        )
    )


def prepare_action_for_review(
    db: Session,
    user: Users,
    linked_focus_id: str,
    approval_type: str,
    title: str,
    details: str,
) -> PreparedActions:
    payload: dict = {"details": details}
    external_refs: dict = {}

    idempotency_key = _build_idempotency_key(user.id, linked_focus_id, approval_type, title, details)
    existing = db.scalars(select(PreparedActions).where(PreparedActions.idempotency_key == idempotency_key)).first()
    if existing:
        return existing

    if approval_type == "email_send_batch":
        access_token = get_valid_google_access_token(
            db,
            user.id,
            settings.google_client_id,
            settings.google_client_secret,
        )
        draft_id = create_gmail_draft(
            access_token=access_token,
            to_email=user.email,
            subject=title,
            body=details,
        )
        external_refs = {"draft_ids": [draft_id], "sent_draft_ids": []}

    elif approval_type == "calendar_invite":
        start = datetime.now(timezone.utc) + timedelta(days=1)
        end = start + timedelta(minutes=30)
        payload = {
            "summary": title,
            "description": details,
            "start": {"dateTime": start.isoformat()},
            "end": {"dateTime": end.isoformat()},
        }

    elif approval_type == "purchase_intent":
        payload = {"intent": details}

    prepared = PreparedActions(
        user_id=user.id,
        type=approval_type,
        title=title,
        payload_json=payload,
        status="pending_review",
        linked_focus_id=linked_focus_id,
        external_refs_json=external_refs,
        idempotency_key=idempotency_key,
        attempt_count=0,
        last_error=None,
    )
    db.add(prepared)
    db.flush()

    db.add(
        Approvals(
            user_id=user.id,
            prepared_action_id=prepared.id,
            status="pending_review",
            created_at=datetime.utcnow(),
        )
    )
    return prepared


def execute_prepared_action(db: Session, user: Users, prepared_action: PreparedActions) -> None:
    if prepared_action.status == "executed" or prepared_action.executed_at is not None:
        return

    try:
        if prepared_action.type == "email_send_batch":
            access_token = get_valid_google_access_token(
                db,
                user.id,
                settings.google_client_id,
                settings.google_client_secret,
            )
            draft_ids = prepared_action.external_refs_json.get("draft_ids", [])
            sent_ids = set(prepared_action.external_refs_json.get("sent_draft_ids", []))
            for draft_id in draft_ids:
                if draft_id in sent_ids:
                    continue
                send_gmail_draft(access_token, draft_id)
                sent_ids.add(draft_id)
            prepared_action.external_refs_json = {
                **(prepared_action.external_refs_json or {}),
                "sent_draft_ids": sorted(sent_ids),
            }

        elif prepared_action.type == "calendar_invite":
            if prepared_action.external_refs_json.get("calendar_event_id"):
                prepared_action.status = "executed"
                prepared_action.executed_at = datetime.utcnow()
                return

            access_token = get_valid_google_access_token(
                db,
                user.id,
                settings.google_client_id,
                settings.google_client_secret,
            )
            event = create_calendar_event(access_token, prepared_action.payload_json)
            prepared_action.external_refs_json = {
                **(prepared_action.external_refs_json or {}),
                "calendar_event_id": event.get("id"),
                "calendar_event_link": event.get("htmlLink"),
            }

        prepared_action.status = "executed"
        prepared_action.executed_at = datetime.utcnow()
        prepared_action.attempt_count += 1
        prepared_action.last_error = None
    except Exception as exc:
        prepared_action.attempt_count += 1
        prepared_action.last_error = str(exc)
        raise


def apply_decision(db: Session, user: Users, approval: Approvals, decision: str, reason: str | None) -> str:
    prepared = db.get(PreparedActions, approval.prepared_action_id)
    if not prepared:
        raise ValueError("Prepared action not found")

    if decision == "approve":
        approval.status = "approved"
        prepared.status = "approved"
        execute_prepared_action(db, user, prepared)
        create_audit_log(
            db,
            approval.user_id,
            "approval_executed",
            f"Approved and executed action {prepared.id}",
            {"approval_id": approval.id, "attempt_count": prepared.attempt_count},
        )
        create_event(
            db,
            approval.user_id,
            "action_executed",
            f"Executed action: {prepared.title}",
            {"prepared_action_id": prepared.id},
        )
        return "executed"

    approval.status = "declined"
    prepared.status = "declined"
    approval.decision_reason = reason
    create_audit_log(
        db,
        approval.user_id,
        "approval_declined",
        f"Declined action {prepared.id}",
        {"approval_id": approval.id, "reason": reason},
    )
    return "declined"
