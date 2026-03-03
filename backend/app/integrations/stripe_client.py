from datetime import datetime, timedelta, timezone

import stripe
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.crypto import decrypt_secret, encrypt_secret
from app.models.models import Integrations, Signals


def connect_stripe_key(db: Session, user_id: str, api_key: str) -> None:
    integration = db.scalars(
        select(Integrations).where(Integrations.user_id == user_id, Integrations.provider == "stripe")
    ).first()
    if not integration:
        integration = Integrations(user_id=user_id, provider="stripe")
        db.add(integration)
    integration.status = "connected"
    integration.scopes_json = ["read_only"]
    integration.token_encrypted = encrypt_secret(api_key)
    integration.refresh_token_encrypted = None
    db.commit()


def _get_stripe_key(db: Session, user_id: str) -> str:
    integration = db.scalars(
        select(Integrations).where(Integrations.user_id == user_id, Integrations.provider == "stripe")
    ).first()
    if not integration or integration.status != "connected" or not integration.token_encrypted:
        raise ValueError("Stripe integration not connected")
    return decrypt_secret(integration.token_encrypted)


def ingest_recent_stripe_signals(db: Session, user_id: str, days: int = 14) -> int:
    stripe.api_key = _get_stripe_key(db, user_id)
    since = int((datetime.now(timezone.utc) - timedelta(days=days)).timestamp())

    ingested = 0
    recent_signals = db.scalars(
        select(Signals).where(
            Signals.user_id == user_id,
            Signals.source == "stripe",
            Signals.occurred_at >= datetime.now(timezone.utc) - timedelta(days=days),
        )
    ).all()
    existing_ids = {s.summary_json.get("id") for s in recent_signals}

    invoices = stripe.Invoice.list(created={"gte": since}, limit=100)
    for inv in invoices.auto_paging_iter():
        inv_id = inv.get("id")
        if inv_id in existing_ids:
            continue

        occurred = datetime.fromtimestamp(inv.get("created", since), tz=timezone.utc)
        db.add(
            Signals(
                user_id=user_id,
                source="stripe",
                kind="invoice",
                occurred_at=occurred,
                summary_json={
                    "id": inv_id,
                    "status": inv.get("status"),
                    "amount_paid": inv.get("amount_paid"),
                    "currency": inv.get("currency"),
                    "customer": inv.get("customer"),
                },
            )
        )
        ingested += 1
        existing_ids.add(inv_id)

    charges = stripe.Charge.list(created={"gte": since}, limit=100)
    for ch in charges.auto_paging_iter():
        charge_id = ch.get("id")
        if charge_id in existing_ids:
            continue

        occurred = datetime.fromtimestamp(ch.get("created", since), tz=timezone.utc)
        db.add(
            Signals(
                user_id=user_id,
                source="stripe",
                kind="charge",
                occurred_at=occurred,
                summary_json={
                    "id": charge_id,
                    "status": ch.get("status"),
                    "amount": ch.get("amount"),
                    "currency": ch.get("currency"),
                    "customer": ch.get("customer"),
                    "paid": ch.get("paid"),
                },
            )
        )
        ingested += 1
        existing_ids.add(charge_id)

    db.commit()
    return ingested
