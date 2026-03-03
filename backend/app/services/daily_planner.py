from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.models import Approvals, DailyFocus, PreparedActions, Users
from app.services.actions import create_audit_log, prepare_action_for_review
from app.services.briefing import build_briefing_packet
from app.services.decision_engine import generate_plan, validate_plan


def run_daily_plan_for_user(
    db: Session,
    user: Users,
    force_refresh: bool = False,
    source: str = "manual",
) -> DailyFocus:
    packet = build_briefing_packet(db, user.id)
    plan = generate_plan(packet)
    validate_plan(plan)

    create_audit_log(
        db,
        user.id,
        "briefing_packet_built",
        "Built daily briefing packet",
        {"packet": packet, "source": source},
    )

    today = date.fromisoformat(plan.date)
    focus = db.scalars(select(DailyFocus).where(DailyFocus.user_id == user.id, DailyFocus.date == today)).first()

    if focus and force_refresh:
        db.query(Approvals).filter(Approvals.user_id == user.id).delete()
        db.query(PreparedActions).filter(PreparedActions.linked_focus_id == focus.id).delete()
        db.delete(focus)
        db.flush()
        focus = None

    if not focus:
        focus = DailyFocus(
            user_id=user.id,
            date=today,
            focus_text=plan.primary_focus,
            why_bullets_json=plan.why,
            success_text=plan.success,
            micro_steps_json=plan.micro_steps,
            confidence=plan.confidence,
        )
        db.add(focus)
        db.flush()

    db.query(PreparedActions).filter(PreparedActions.linked_focus_id == focus.id).delete()

    for approval in plan.approvals[:3]:
        prepare_action_for_review(
            db,
            user=user,
            linked_focus_id=focus.id,
            approval_type=approval["type"],
            title=approval["title"],
            details=approval["details"],
        )

    create_audit_log(
        db,
        user.id,
        "daily_plan_generated",
        "Generated daily focus and approvals",
        {"source": source},
    )
    return focus
