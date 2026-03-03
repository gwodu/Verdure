from datetime import datetime, timedelta, timezone

from sqlalchemy import select

from app.db.session import SessionLocal
from app.models.models import FailedJobs, Users
from app.services.actions import create_audit_log
from app.services.daily_planner import run_daily_plan_for_user
from app.workers.celery_app import celery_app


@celery_app.task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=True,
    retry_jitter=True,
    retry_kwargs={"max_retries": 3},
)
def run_daily_plan_task(self, user_id: str, force_refresh: bool = False, source: str = "worker") -> str:
    db = SessionLocal()
    user_exists = False
    try:
        user = db.get(Users, user_id)
        if not user:
            raise ValueError("User not found")
        user_exists = True

        focus = run_daily_plan_for_user(db, user, force_refresh=force_refresh, source=source)
        db.commit()
        return focus.id
    except Exception as exc:
        if user_exists:
            create_audit_log(
                db,
                user_id,
                "daily_plan_worker_failed",
                "Background daily planner failed",
                {
                    "error": str(exc),
                    "task_id": self.request.id,
                    "retry": self.request.retries,
                },
            )
        db.add(
            FailedJobs(
                task_name="run_daily_plan_task",
                entity_type="user",
                entity_id=user_id,
                payload_json={"force_refresh": force_refresh, "source": source},
                error=str(exc),
                retry_count=self.request.retries,
                status="open",
                next_retry_at=datetime.now(timezone.utc) + timedelta(minutes=15),
            )
        )
        db.commit()
        raise
    finally:
        db.close()


@celery_app.task(bind=True)
def schedule_all_users_daily_plan_task(self) -> dict:
    db = SessionLocal()
    queued = 0
    try:
        users = db.scalars(select(Users.id)).all()
        for user_id in users:
            run_daily_plan_task.delay(user_id=user_id, force_refresh=False, source="scheduled")
            queued += 1

        return {"queued": queued}
    finally:
        db.close()


@celery_app.task(bind=True)
def retry_failed_jobs_task(self) -> dict:
    db = SessionLocal()
    retried = 0
    try:
        now = datetime.now(timezone.utc)
        failed_jobs = db.scalars(
            select(FailedJobs).where(
                FailedJobs.status == "open",
                FailedJobs.next_retry_at.is_not(None),
                FailedJobs.next_retry_at <= now,
            )
        ).all()

        for failed in failed_jobs:
            if failed.task_name == "run_daily_plan_task" and failed.retry_count < 5:
                run_daily_plan_task.delay(
                    user_id=failed.entity_id,
                    force_refresh=bool(failed.payload_json.get("force_refresh", False)),
                    source="retry_failed",
                )
                failed.status = "retried"
                failed.resolved_at = now
                retried += 1
            else:
                failed.status = "dead_letter"
                failed.resolved_at = now

        db.commit()
        return {"retried": retried}
    finally:
        db.close()
