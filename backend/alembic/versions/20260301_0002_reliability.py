"""reliability primitives

Revision ID: 20260301_0002
Revises: 20260301_0001
Create Date: 2026-03-01 01:00:00
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "20260301_0002"
down_revision = "20260301_0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_unique_constraint("uq_daily_focus_user_date", "daily_focus", ["user_id", "date"])

    op.add_column("prepared_actions", sa.Column("idempotency_key", sa.String(), nullable=True))
    op.add_column("prepared_actions", sa.Column("attempt_count", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("prepared_actions", sa.Column("last_error", sa.Text(), nullable=True))
    op.create_index("ix_prepared_actions_idempotency_key", "prepared_actions", ["idempotency_key"], unique=True)

    op.execute("UPDATE prepared_actions SET idempotency_key = id WHERE idempotency_key IS NULL")
    op.alter_column("prepared_actions", "idempotency_key", nullable=False)

    op.create_table(
        "failed_jobs",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("task_name", sa.String(), nullable=False),
        sa.Column("entity_type", sa.String(), nullable=False),
        sa.Column("entity_id", sa.String(), nullable=False),
        sa.Column("payload_json", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("error", sa.Text(), nullable=False),
        sa.Column("retry_count", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(), nullable=False),
        sa.Column("next_retry_at", sa.DateTime(), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("resolved_at", sa.DateTime(), nullable=True),
    )
    op.create_index("ix_failed_jobs_task_name", "failed_jobs", ["task_name"], unique=False)
    op.create_index("ix_failed_jobs_entity_id", "failed_jobs", ["entity_id"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_failed_jobs_entity_id", table_name="failed_jobs")
    op.drop_index("ix_failed_jobs_task_name", table_name="failed_jobs")
    op.drop_table("failed_jobs")

    op.drop_index("ix_prepared_actions_idempotency_key", table_name="prepared_actions")
    op.drop_column("prepared_actions", "last_error")
    op.drop_column("prepared_actions", "attempt_count")
    op.drop_column("prepared_actions", "idempotency_key")

    op.drop_constraint("uq_daily_focus_user_date", "daily_focus", type_="unique")
