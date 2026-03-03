from cryptography.fernet import Fernet

from app.core.config import settings


def _get_fernet() -> Fernet:
    if not settings.token_encryption_key:
        raise ValueError("TOKEN_ENCRYPTION_KEY is required for token encryption")
    return Fernet(settings.token_encryption_key.encode("utf-8"))


def encrypt_secret(value: str) -> str:
    return _get_fernet().encrypt(value.encode("utf-8")).decode("utf-8")


def decrypt_secret(value: str) -> str:
    return _get_fernet().decrypt(value.encode("utf-8")).decode("utf-8")
