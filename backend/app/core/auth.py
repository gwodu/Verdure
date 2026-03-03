from itsdangerous import BadSignature, URLSafeTimedSerializer
from fastapi import Cookie, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.models.models import Users

SESSION_COOKIE_NAME = "verdure_session"

_serializer = URLSafeTimedSerializer(settings.secret_key)


def create_magic_token(email: str) -> str:
    return _serializer.dumps({"email": email}, salt="magic-link")


def verify_magic_token(token: str, max_age_seconds: int = 900) -> str:
    try:
        payload = _serializer.loads(token, salt="magic-link", max_age=max_age_seconds)
    except BadSignature as exc:
        raise HTTPException(status_code=401, detail="Invalid or expired token") from exc
    return payload["email"]


def create_session_token(user_id: str) -> str:
    return _serializer.dumps({"user_id": user_id}, salt="session")


def verify_session_token(token: str, max_age_seconds: int = 60 * 60 * 24 * 7) -> str:
    try:
        payload = _serializer.loads(token, salt="session", max_age=max_age_seconds)
    except BadSignature as exc:
        raise HTTPException(status_code=401, detail="Invalid session") from exc
    return payload["user_id"]


def require_user(
    session_token: str | None = Cookie(default=None, alias=SESSION_COOKIE_NAME),
    db: Session = Depends(get_db),
) -> Users:
    if not session_token:
        raise HTTPException(status_code=401, detail="Authentication required")
    user_id = verify_session_token(session_token)
    user = db.get(Users, user_id)
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return user
