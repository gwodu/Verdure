from pydantic import BaseModel


class StripeConnectRequest(BaseModel):
    api_key: str
