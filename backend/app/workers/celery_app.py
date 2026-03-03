from celery import Celery
from celery.schedules import crontab
from kombu import Queue

from app.core.config import settings

celery_app = Celery("verdure", broker=settings.redis_url, backend=settings.redis_url)
celery_app.conf.update(
    task_default_queue="verdure",
    task_queues=(
        Queue("verdure"),
        Queue("verdure_scheduler"),
    ),
    task_routes={
        "app.workers.tasks.run_daily_plan_task": {"queue": "verdure"},
        "app.workers.tasks.schedule_all_users_daily_plan_task": {"queue": "verdure_scheduler"},
        "app.workers.tasks.retry_failed_jobs_task": {"queue": "verdure_scheduler"},
    },
    task_acks_late=True,
    task_reject_on_worker_lost=True,
    worker_prefetch_multiplier=1,
    task_track_started=True,
    result_expires=3600,
    beat_schedule={
        "schedule-daily-plan-for-all-users": {
            "task": "app.workers.tasks.schedule_all_users_daily_plan_task",
            "schedule": crontab(minute=0, hour=13),
        },
        "retry-failed-jobs": {
            "task": "app.workers.tasks.retry_failed_jobs_task",
            "schedule": crontab(minute="*/15"),
        },
    },
)
