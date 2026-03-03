from pydantic import BaseModel


class MicroStep(BaseModel):
    minutes: int
    text: str


class ApprovalCard(BaseModel):
    id: str
    title: str
    type: str
    details: str
    status: str


class TodayResponse(BaseModel):
    date: str
    primary_focus: str
    why: list[str]
    success: str
    micro_steps: list[MicroStep]
    confidence: float
    approvals: list[ApprovalCard]


class DailyRunRequest(BaseModel):
    force_refresh: bool = False
