from pydantic import BaseModel


class ApprovalDecisionRequest(BaseModel):
    decision: str
    reason: str | None = None


class ApprovalDecisionResponse(BaseModel):
    status: str
