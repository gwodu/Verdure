import os
from dataclasses import dataclass


@dataclass
class Settings:
    database_url: str = os.getenv(
        "DATABASE_URL", "postgresql+psycopg2://verdure:verdure@postgres:5432/verdure"
    )
    redis_url: str = os.getenv("REDIS_URL", "redis://redis:6379/0")
    cors_origins: str = os.getenv("CORS_ORIGINS", "http://localhost:3000")
    frontend_url: str = os.getenv("FRONTEND_URL", "http://localhost:3000")
    secret_key: str = os.getenv("SECRET_KEY", "dev-secret-change-me")
    token_encryption_key: str = os.getenv("TOKEN_ENCRYPTION_KEY", "")
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-4.1-mini")
    xml_fallback_plan: str = os.getenv("VERDURE_XML_FALLBACK_PLAN", "")
    google_client_id: str = os.getenv("GOOGLE_CLIENT_ID", "")
    google_client_secret: str = os.getenv("GOOGLE_CLIENT_SECRET", "")
    google_redirect_uri: str = os.getenv(
        "GOOGLE_REDIRECT_URI", "http://localhost:8000/api/integrations/google/oauth/callback"
    )


settings = Settings()
