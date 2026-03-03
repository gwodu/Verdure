from pydantic import BaseModel, EmailStr


class MagicLinkRequest(BaseModel):
    email: EmailStr


class MagicLinkRequestResponse(BaseModel):
    status: str
    magic_link: str


class MagicLinkVerifyRequest(BaseModel):
    token: str


class AuthMeResponse(BaseModel):
    user_id: str
    email: str
