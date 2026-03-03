import base64
from datetime import datetime, timedelta, timezone
from urllib.parse import urlencode

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.crypto import decrypt_secret, encrypt_secret
from app.models.models import Integrations

GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
GMAIL_DRAFT_URL = "https://gmail.googleapis.com/gmail/v1/users/me/drafts"
GMAIL_SEND_DRAFT_URL = "https://gmail.googleapis.com/gmail/v1/users/me/drafts/{draft_id}/send"
CALENDAR_EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events"

GOOGLE_SCOPES = [
    "https://www.googleapis.com/auth/gmail.compose",
    "https://www.googleapis.com/auth/gmail.send",
    "https://www.googleapis.com/auth/calendar.events",
    "https://www.googleapis.com/auth/calendar.readonly",
]


def build_google_oauth_url(client_id: str, redirect_uri: str, state: str) -> str:
    params = {
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "response_type": "code",
        "scope": " ".join(GOOGLE_SCOPES),
        "access_type": "offline",
        "prompt": "consent",
        "state": state,
    }
    return f"{GOOGLE_AUTH_URL}?{urlencode(params)}"


def exchange_google_code_for_tokens(
    code: str,
    client_id: str,
    client_secret: str,
    redirect_uri: str,
) -> dict:
    with httpx.Client(timeout=30) as client:
        response = client.post(
            GOOGLE_TOKEN_URL,
            data={
                "code": code,
                "client_id": client_id,
                "client_secret": client_secret,
                "redirect_uri": redirect_uri,
                "grant_type": "authorization_code",
            },
        )
        response.raise_for_status()
        return response.json()


def refresh_google_access_token(
    refresh_token: str,
    client_id: str,
    client_secret: str,
) -> dict:
    with httpx.Client(timeout=30) as client:
        response = client.post(
            GOOGLE_TOKEN_URL,
            data={
                "refresh_token": refresh_token,
                "client_id": client_id,
                "client_secret": client_secret,
                "grant_type": "refresh_token",
            },
        )
        response.raise_for_status()
        return response.json()


def _integration(db: Session, user_id: str) -> Integrations | None:
    return db.scalars(
        select(Integrations).where(Integrations.user_id == user_id, Integrations.provider == "google")
    ).first()


def get_valid_google_access_token(
    db: Session,
    user_id: str,
    client_id: str,
    client_secret: str,
) -> str:
    integration = _integration(db, user_id)
    if not integration or not integration.token_encrypted:
        raise ValueError("Google integration not connected")

    token = decrypt_secret(integration.token_encrypted)
    expiry_ts = integration.scopes_json and next(
        (s.split(":", 1)[1] for s in integration.scopes_json if s.startswith("expiry:")),
        None,
    )
    if expiry_ts:
        expires_at = datetime.fromisoformat(expiry_ts)
        if expires_at - datetime.now(timezone.utc) > timedelta(minutes=2):
            return token

    if not integration.refresh_token_encrypted:
        return token

    refresh_token = decrypt_secret(integration.refresh_token_encrypted)
    refreshed = refresh_google_access_token(refresh_token, client_id, client_secret)
    new_token = refreshed["access_token"]
    expires_in = int(refreshed.get("expires_in", 3600))
    expires_at = datetime.now(timezone.utc) + timedelta(seconds=expires_in)

    scopes = [s for s in (integration.scopes_json or []) if not s.startswith("expiry:")]
    scopes.append(f"expiry:{expires_at.isoformat()}")
    integration.token_encrypted = encrypt_secret(new_token)
    integration.scopes_json = scopes
    db.commit()
    return new_token


def _gmail_raw_message(to_email: str, subject: str, body: str) -> str:
    msg = f"To: {to_email}\r\nSubject: {subject}\r\n\r\n{body}"
    return base64.urlsafe_b64encode(msg.encode("utf-8")).decode("utf-8")


def create_gmail_draft(access_token: str, to_email: str, subject: str, body: str) -> str:
    raw = _gmail_raw_message(to_email, subject, body)
    payload = {"message": {"raw": raw}}
    with httpx.Client(timeout=30) as client:
        response = client.post(
            GMAIL_DRAFT_URL,
            json=payload,
            headers={"Authorization": f"Bearer {access_token}"},
        )
        response.raise_for_status()
        data = response.json()
        return data["id"]


def send_gmail_draft(access_token: str, draft_id: str) -> dict:
    url = GMAIL_SEND_DRAFT_URL.format(draft_id=draft_id)
    with httpx.Client(timeout=30) as client:
        response = client.post(
            url,
            json={"id": draft_id},
            headers={"Authorization": f"Bearer {access_token}"},
        )
        response.raise_for_status()
        return response.json()


def create_calendar_event(access_token: str, event_payload: dict) -> dict:
    with httpx.Client(timeout=30) as client:
        response = client.post(
            CALENDAR_EVENTS_URL,
            json=event_payload,
            headers={"Authorization": f"Bearer {access_token}"},
        )
        response.raise_for_status()
        return response.json()
