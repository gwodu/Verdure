from pydantic import BaseModel, Field


class OnboardingRequest(BaseModel):
    timezone: str
    primary_goal_text: str
    revenue_model: str
    weekly_availability: str
    preferences_json: dict = Field(default_factory=dict)
    initiatives: list[str]


class OnboardingResponse(BaseModel):
    user_id: str
