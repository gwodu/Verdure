import pytest

from app.services.decision_engine import PlanOutput, parse_xml_fallback, validate_plan


def test_xml_fallback_parsing():
    xml = """
<verdure_day>
  <focus initiative_id="init-1">Fix activation drop-off</focus>
  <why><b>Churn risk is rising</b><b>Conversion is flat</b></why>
  <success>New flow launched with 10 sessions observed</success>
  <micro_steps>
    <step minutes="15">Define hypothesis</step>
    <step minutes="20">Build experiment variant</step>
    <step minutes="15">Write announcement draft</step>
  </micro_steps>
  <approvals>
    <approval type="email_send_batch">Prepare user announcement batch</approval>
  </approvals>
  <confidence>0.75</confidence>
  <assumptions><a>No blocker from infra</a></assumptions>
</verdure_day>
"""
    plan = parse_xml_fallback(xml)
    assert plan.primary_focus == "Fix activation drop-off"
    assert len(plan.micro_steps) == 3
    assert plan.approvals[0]["type"] == "email_send_batch"


def test_validate_plan_rejects_invalid_approval_type():
    plan = PlanOutput(
        date="2026-03-01",
        primary_focus="Ship onboarding improvement",
        initiative_id=None,
        why=["Higher conversion", "Clear user pain"],
        success="Shipped and measured",
        micro_steps=[
            {"minutes": 15, "text": "Define hypothesis"},
            {"minutes": 20, "text": "Implement variant"},
            {"minutes": 15, "text": "Prepare message"},
        ],
        approvals=[{"type": "wire_money", "title": "Bad", "details": "No"}],
        confidence=0.6,
        assumptions=[],
    )

    with pytest.raises(ValueError):
        validate_plan(plan)
