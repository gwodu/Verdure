"use client";

import { useState } from "react";
import { postOnboarding } from "../../lib/api";

export default function OnboardingPage() {
  const [form, setForm] = useState({
    timezone: "America/New_York",
    primary_goal_text: "Reach $10k MRR in 90 days",
    revenue_model: "B2B SaaS",
    weekly_availability: "20 hours",
    initiative_1: "Improve activation flow",
    initiative_2: "Launch outbound founder-led sales",
    initiative_3: "Tighten churn interventions"
  });
  const [savedUserId, setSavedUserId] = useState<string | null>(null);

  async function submit() {
    const response = await postOnboarding({
      timezone: form.timezone,
      primary_goal_text: form.primary_goal_text,
      revenue_model: form.revenue_model,
      weekly_availability: form.weekly_availability,
      preferences_json: {},
      initiatives: [form.initiative_1, form.initiative_2, form.initiative_3]
    });
    setSavedUserId(response.user_id);
  }

  return (
    <div className="card stack">
      <h1>Onboarding</h1>
      {Object.entries(form).map(([key, value]) => (
        <label key={key} className="stack">
          <span className="small">{key}</span>
          <input value={value} onChange={(e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))} />
        </label>
      ))}
      <button onClick={submit}>Save Snapshot</button>
      {savedUserId ? <p className="small">Saved user: {savedUserId}</p> : null}
      <p className="small">Google/Stripe connect lives in Settings.</p>
    </div>
  );
}
