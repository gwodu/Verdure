from app.core.auth import create_magic_token, create_session_token, verify_magic_token, verify_session_token


def test_magic_token_roundtrip():
    token = create_magic_token("founder@example.com")
    email = verify_magic_token(token)
    assert email == "founder@example.com"


def test_session_token_roundtrip():
    token = create_session_token("user-123")
    user_id = verify_session_token(token)
    assert user_id == "user-123"
