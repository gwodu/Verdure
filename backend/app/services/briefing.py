from datetime import datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.models import Commitments, DailyFocus, Initiatives, Signals, Users


def build_briefing_packet(db: Session, user_id: str) -> str:
    user = db.get(Users, user_id)
    initiatives = db.scalars(
        select(Initiatives).where(Initiatives.user_id == user_id, Initiatives.status == "active")
    ).all()
    commitments = db.scalars(select(Commitments).where(Commitments.user_id == user_id)).all()

    since = datetime.utcnow() - timedelta(days=14)
    signals = db.scalars(select(Signals).where(Signals.user_id == user_id, Signals.occurred_at >= since)).all()
    recent_focus = db.scalars(
        select(DailyFocus).where(DailyFocus.user_id == user_id).order_by(DailyFocus.date.desc()).limit(5)
    ).all()

    goal = user.primary_goal_text if user else "Not provided"
    constraints = f"Timezone: {user.timezone if user else 'Unknown'} | Availability: {user.weekly_availability if user else 'Unknown'}"
    context = f"Revenue model: {user.revenue_model if user else 'Unknown'}"

    initiative_lines = "\n".join([f"- {i.title} ({i.impact_level}/{i.urgency_level})" for i in initiatives]) or "- None"
    commitment_lines = "\n".join([f"- {c.type} with {c.counterparty} ({c.status})" for c in commitments]) or "- None"
    signal_lines = "\n".join([f"- {s.source}:{s.kind}" for s in signals]) or "- None"
    momentum_lines = "\n".join([f"- {f.date}: {f.focus_text}" for f in recent_focus]) or "- No recent focus"

    return (
        "GOAL (30 days)\n"
        f"{goal}\n\n"
        "CONSTRAINTS (today)\n"
        f"{constraints}\n\n"
        "BUSINESS CONTEXT\n"
        f"{context}\n\n"
        "ACTIVE INITIATIVES\n"
        f"{initiative_lines}\n\n"
        "COMMITMENTS\n"
        f"{commitment_lines}\n\n"
        "SIGNALS (7-14 day summaries)\n"
        f"{signal_lines}\n\n"
        "MOMENTUM (recent focus + neglect)\n"
        f"{momentum_lines}\n"
    )
