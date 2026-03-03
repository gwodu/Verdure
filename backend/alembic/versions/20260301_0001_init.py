"""initial schema

Revision ID: 20260301_0001
Revises:
Create Date: 2026-03-01 00:00:00
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "20260301_0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("email", sa.String(), nullable=False),
        sa.Column("timezone", sa.String(), nullable=False),
        sa.Column("primary_goal_text", sa.Text(), nullable=False),
        sa.Column("revenue_model", sa.String(), nullable=False),
        sa.Column("weekly_availability", sa.String(), nullable=False),
        sa.Column("preferences_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_index("ix_users_email", "users", ["email"], unique=True)

    op.create_table(
        "initiatives",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("impact_level", sa.String(), nullable=False),
        sa.Column("urgency_level", sa.String(), nullable=False),
        sa.Column("last_progress_at", sa.DateTime(), nullable=True),
    )
    op.create_index("ix_initiatives_user_id", "initiatives", ["user_id"], unique=False)

    op.create_table(
        "commitments",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("type", sa.String(), nullable=False),
        sa.Column("counterparty", sa.String(), nullable=False),
        sa.Column("due_date", sa.Date(), nullable=True),
        sa.Column("source", sa.String(), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
    )
    op.create_index("ix_commitments_user_id", "commitments", ["user_id"], unique=False)

    op.create_table(
        "daily_focus",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("focus_text", sa.Text(), nullable=False),
        sa.Column("why_bullets_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("success_text", sa.Text(), nullable=False),
        sa.Column("micro_steps_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("confidence", sa.Float(), nullable=False),
    )
    op.create_index("ix_daily_focus_user_id", "daily_focus", ["user_id"], unique=False)
    op.create_index("ix_daily_focus_date", "daily_focus", ["date"], unique=False)

    op.create_table(
        "prepared_actions",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("type", sa.String(), nullable=False),
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("payload_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("external_refs_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("linked_focus_id", sa.String(), sa.ForeignKey("daily_focus.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("executed_at", sa.DateTime(), nullable=True),
    )
    op.create_index("ix_prepared_actions_user_id", "prepared_actions", ["user_id"], unique=False)

    op.create_table(
        "approvals",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column(
            "prepared_action_id",
            sa.String(),
            sa.ForeignKey("prepared_actions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("decision_reason", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("decided_at", sa.DateTime(), nullable=True),
    )
    op.create_index("ix_approvals_user_id", "approvals", ["user_id"], unique=False)
    op.create_index("ix_approvals_prepared_action_id", "approvals", ["prepared_action_id"], unique=False)

    op.create_table(
        "signals",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("source", sa.String(), nullable=False),
        sa.Column("kind", sa.String(), nullable=False),
        sa.Column("occurred_at", sa.DateTime(), nullable=False),
        sa.Column("summary_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
    )
    op.create_index("ix_signals_user_id", "signals", ["user_id"], unique=False)
    op.create_index("ix_signals_occurred_at", "signals", ["occurred_at"], unique=False)

    op.create_table(
        "episodic_events",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("type", sa.String(), nullable=False),
        sa.Column("summary", sa.Text(), nullable=False),
        sa.Column("refs_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("occurred_at", sa.DateTime(), nullable=False),
    )
    op.create_index("ix_episodic_events_user_id", "episodic_events", ["user_id"], unique=False)

    op.create_table(
        "audit_log",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("actor", sa.String(), nullable=False),
        sa.Column("action_type", sa.String(), nullable=False),
        sa.Column("summary", sa.Text(), nullable=False),
        sa.Column("metadata_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_index("ix_audit_log_user_id", "audit_log", ["user_id"], unique=False)

    op.create_table(
        "integrations",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("user_id", sa.String(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("provider", sa.String(), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("scopes_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("token_encrypted", sa.Text(), nullable=True),
        sa.Column("refresh_token_encrypted", sa.Text(), nullable=True),
    )
    op.create_index("ix_integrations_user_id", "integrations", ["user_id"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_integrations_user_id", table_name="integrations")
    op.drop_table("integrations")
    op.drop_index("ix_audit_log_user_id", table_name="audit_log")
    op.drop_table("audit_log")
    op.drop_index("ix_episodic_events_user_id", table_name="episodic_events")
    op.drop_table("episodic_events")
    op.drop_index("ix_signals_occurred_at", table_name="signals")
    op.drop_index("ix_signals_user_id", table_name="signals")
    op.drop_table("signals")
    op.drop_index("ix_approvals_prepared_action_id", table_name="approvals")
    op.drop_index("ix_approvals_user_id", table_name="approvals")
    op.drop_table("approvals")
    op.drop_index("ix_prepared_actions_user_id", table_name="prepared_actions")
    op.drop_table("prepared_actions")
    op.drop_index("ix_daily_focus_date", table_name="daily_focus")
    op.drop_index("ix_daily_focus_user_id", table_name="daily_focus")
    op.drop_table("daily_focus")
    op.drop_index("ix_commitments_user_id", table_name="commitments")
    op.drop_table("commitments")
    op.drop_index("ix_initiatives_user_id", table_name="initiatives")
    op.drop_table("initiatives")
    op.drop_index("ix_users_email", table_name="users")
    op.drop_table("users")
