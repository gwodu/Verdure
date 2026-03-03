from dataclasses import dataclass
from datetime import date
import json
import xml.etree.ElementTree as ET

from openai import OpenAI

from app.core.config import settings

ALLOWED_APPROVAL_TYPES = {"email_send_batch", "calendar_invite", "purchase_intent"}


@dataclass
class PlanOutput:
    date: str
    primary_focus: str
    initiative_id: str | None
    why: list[str]
    success: str
    micro_steps: list[dict]
    approvals: list[dict]
    confidence: float
    assumptions: list[str]


def _tool_schema() -> list[dict]:
    return [
        {
            "type": "function",
            "function": {
                "name": "verdure_create_daily_plan",
                "description": "Create Verdure's single daily focus plan with approval-gated actions.",
                "parameters": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": [
                        "date",
                        "primary_focus",
                        "initiative_id",
                        "why",
                        "success",
                        "micro_steps",
                        "approvals",
                        "confidence",
                        "assumptions",
                    ],
                    "properties": {
                        "date": {"type": "string"},
                        "primary_focus": {"type": "string"},
                        "initiative_id": {"type": ["string", "null"]},
                        "why": {"type": "array", "items": {"type": "string"}},
                        "success": {"type": "string"},
                        "micro_steps": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "additionalProperties": False,
                                "required": ["minutes", "text"],
                                "properties": {
                                    "minutes": {"type": "integer"},
                                    "text": {"type": "string"},
                                },
                            },
                        },
                        "approvals": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "additionalProperties": False,
                                "required": ["type", "title", "details"],
                                "properties": {
                                    "type": {"type": "string"},
                                    "title": {"type": "string"},
                                    "details": {"type": "string"},
                                },
                            },
                        },
                        "confidence": {"type": "number"},
                        "assumptions": {"type": "array", "items": {"type": "string"}},
                    },
                },
            },
        }
    ]


def _system_prompt() -> str:
    return (
        "You are Verdure's daily decision engine.\n"
        "Rules:\n"
        "- Return exactly one tool call to verdure_create_daily_plan.\n"
        "- Exactly ONE primary_focus.\n"
        "- 3 to 6 micro_steps.\n"
        "- Approvals max 3.\n"
        "- Allowed approval types only: email_send_batch, calendar_invite, purchase_intent.\n"
        "- No irreversible action without approval.\n"
        "- Keep focus concrete and execution-ready for today's highest-impact work."
    )


def _plan_from_tool_args(args: dict) -> PlanOutput:
    return PlanOutput(
        date=str(args.get("date") or date.today()),
        primary_focus=str(args.get("primary_focus") or "").strip(),
        initiative_id=args.get("initiative_id"),
        why=[str(x).strip() for x in args.get("why", []) if str(x).strip()],
        success=str(args.get("success") or "").strip(),
        micro_steps=[
            {"minutes": int(step["minutes"]), "text": str(step["text"]).strip()}
            for step in args.get("micro_steps", [])
            if isinstance(step, dict) and str(step.get("text", "")).strip()
        ],
        approvals=[
            {
                "type": str(a.get("type", "")).strip(),
                "title": str(a.get("title", "")).strip(),
                "details": str(a.get("details", "")).strip(),
            }
            for a in args.get("approvals", [])
            if isinstance(a, dict)
        ],
        confidence=float(args.get("confidence", 0.0)),
        assumptions=[str(x).strip() for x in args.get("assumptions", []) if str(x).strip()],
    )


def _llm_tool_call_plan(briefing_packet: str) -> PlanOutput:
    if not settings.openai_api_key:
        raise ValueError("OPENAI_API_KEY missing")

    client = OpenAI(api_key=settings.openai_api_key)
    completion = client.chat.completions.create(
        model=settings.openai_model,
        temperature=0.2,
        tools=_tool_schema(),
        tool_choice={"type": "function", "function": {"name": "verdure_create_daily_plan"}},
        messages=[
            {"role": "system", "content": _system_prompt()},
            {
                "role": "user",
                "content": (
                    "Generate today's plan from this packet. If uncertain, include assumptions.\n\n"
                    f"{briefing_packet}"
                ),
            },
        ],
    )

    tool_calls = completion.choices[0].message.tool_calls or []
    if len(tool_calls) != 1:
        raise ValueError("Expected exactly one tool call")

    tool_call = tool_calls[0]
    if tool_call.function.name != "verdure_create_daily_plan":
        raise ValueError("Unexpected tool name")

    args = json.loads(tool_call.function.arguments)
    return _plan_from_tool_args(args)


def generate_plan(briefing_packet: str) -> PlanOutput:
    # Priority order: explicit XML fallback -> real tool-calling LLM -> deterministic fallback.
    xml_fallback = settings.xml_fallback_plan
    if xml_fallback:
        plan = parse_xml_fallback(xml_fallback)
        validate_plan(plan)
        return plan

    try:
        plan = _llm_tool_call_plan(briefing_packet)
        validate_plan(plan)
        return plan
    except Exception:
        plan = deterministic_fallback_plan()
        validate_plan(plan)
        return plan


def deterministic_fallback_plan() -> PlanOutput:
    return PlanOutput(
        date=str(date.today()),
        primary_focus="Ship one conversion-critical activation experiment",
        initiative_id=None,
        why=[
            "Activation friction is limiting trial-to-paid conversion.",
            "A focused experiment can move revenue faster than broad maintenance.",
            "Execution fits today's available time block.",
        ],
        success="Experiment released and first 10 user interactions observed.",
        micro_steps=[
            {"minutes": 20, "text": "Define hypothesis and target segment."},
            {"minutes": 30, "text": "Implement simplified activation screen variant."},
            {"minutes": 20, "text": "Create announcement draft for trial users."},
            {"minutes": 15, "text": "Review metrics checkpoints and rollback plan."},
        ],
        approvals=[
            {
                "type": "email_send_batch",
                "title": "Activation experiment email draft batch",
                "details": "Draft outreach to trial users announcing streamlined onboarding.",
            }
        ],
        confidence=0.78,
        assumptions=["No production incident blocks release window."],
    )


def parse_xml_fallback(xml_text: str) -> PlanOutput:
    root = ET.fromstring(xml_text.strip())
    if root.tag != "verdure_day":
        raise ValueError("Invalid root element")

    focus_el = root.find("focus")
    why_el = root.find("why")
    success_el = root.find("success")
    steps_el = root.find("micro_steps")
    approvals_el = root.find("approvals")
    confidence_el = root.find("confidence")
    assumptions_el = root.find("assumptions")

    if focus_el is None or success_el is None or steps_el is None or confidence_el is None:
        raise ValueError("Missing required plan elements")

    why = [b.text.strip() for b in (why_el.findall("b") if why_el is not None else []) if b.text]
    micro_steps = []
    for step in steps_el.findall("step"):
        minutes = int(step.attrib.get("minutes", "15"))
        text = (step.text or "").strip()
        if text:
            micro_steps.append({"minutes": minutes, "text": text})

    approvals = []
    if approvals_el is not None:
        for approval in approvals_el.findall("approval"):
            a_type = approval.attrib.get("type", "").strip()
            details = (approval.text or "").strip()
            approvals.append(
                {
                    "type": a_type,
                    "title": f"Prepared {a_type.replace('_', ' ')} action",
                    "details": details,
                }
            )

    assumptions = []
    if assumptions_el is not None:
        assumptions = [a.text.strip() for a in assumptions_el.findall("a") if a.text]

    return PlanOutput(
        date=str(date.today()),
        primary_focus=(focus_el.text or "").strip(),
        initiative_id=focus_el.attrib.get("initiative_id"),
        why=why,
        success=(success_el.text or "").strip(),
        micro_steps=micro_steps,
        approvals=approvals,
        confidence=float((confidence_el.text or "0.0").strip()),
        assumptions=assumptions,
    )


def validate_plan(plan: PlanOutput) -> None:
    date.fromisoformat(plan.date)
    if not plan.primary_focus.strip():
        raise ValueError("Plan must include one primary focus")
    if not (3 <= len(plan.micro_steps) <= 6):
        raise ValueError("Plan must contain 3-6 micro steps")
    if len(plan.approvals) > 3:
        raise ValueError("Plan approvals must be <= 3")
    if not plan.success.strip():
        raise ValueError("Plan must include success criteria")
    if len(plan.why) < 2:
        raise ValueError("Plan must include at least 2 why bullets")
    if not (0 <= plan.confidence <= 1):
        raise ValueError("Confidence must be between 0 and 1")
    for step in plan.micro_steps:
        if step["minutes"] <= 0:
            raise ValueError("Micro-step minutes must be positive")
        if not step["text"].strip():
            raise ValueError("Micro-step text required")
    for approval in plan.approvals:
        if approval["type"] not in ALLOWED_APPROVAL_TYPES:
            raise ValueError(f"Unsupported approval type: {approval['type']}")
        if not approval["title"]:
            raise ValueError("Approval title required")
