"use client";

import { useEffect, useState } from "react";
import { decideApproval, getToday, triggerDailyPlan, type TodayPayload } from "../../lib/api";

export default function TodayPage() {
  const [data, setData] = useState<TodayPayload | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    try {
      setError(null);
      setData(await getToday());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load today");
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  async function runPlan() {
    try {
      await triggerDailyPlan();
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run plan");
    }
  }

  async function onDecision(approvalId: string, decision: "approve" | "decline") {
    try {
      await decideApproval(approvalId, decision);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Decision failed");
    }
  }

  return (
    <div className="stack">
      <div className="card stack">
        <h1>Today&apos;s Focus</h1>
        {data?.primary_focus ? (
          <>
            <h2>{data.primary_focus}</h2>
            <ul>
              {data.why.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
            <p className="small">Success: {data.success}</p>
            <p className="small">Confidence: {Math.round(data.confidence * 100)}%</p>
            <button onClick={runPlan}>Start</button>
          </>
        ) : (
          <>
            <p className="small">No plan yet for today.</p>
            <button onClick={runPlan}>Generate Today&apos;s Focus</button>
          </>
        )}
      </div>

      <div className="card stack">
        <h2>Requires Your Approval ({data?.approvals.length || 0})</h2>
        {(data?.approvals || []).slice(0, 3).map((approval) => (
          <div className="card" key={approval.id}>
            <h3>{approval.title}</h3>
            <p className="small">{approval.type}</p>
            <p>{approval.details}</p>
            <div className="row">
              <button className="secondary">Preview</button>
              <button onClick={() => onDecision(approval.id, "approve")}>Approve & Execute</button>
              <button className="secondary" onClick={() => onDecision(approval.id, "decline")}>
                Decline
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className="card stack">
        <h2>Quick Capture</h2>
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Add note or constraint"
          aria-label="Quick capture"
        />
        <p className="small">Saved to later iteration: quick-capture persistence.</p>
      </div>

      {error ? <p className="small">Error: {error}</p> : null}
    </div>
  );
}
