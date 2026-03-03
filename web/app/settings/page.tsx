"use client";

import { useState } from "react";
import {
  connectStripe,
  disconnectIntegration,
  getGoogleOAuthStart,
  ingestStripeSignals,
  retryFailedDailyRuns,
  scheduleAllDailyRuns
} from "../../lib/api";

export default function SettingsPage() {
  const [status, setStatus] = useState("");
  const [stripeKey, setStripeKey] = useState("");

  async function onGoogleConnect() {
    const response = await getGoogleOAuthStart();
    window.location.href = response.auth_url;
  }

  async function onStripeConnect() {
    const response = await connectStripe(stripeKey);
    setStatus(`stripe: ${response.status}`);
  }

  async function onStripeIngest() {
    const response = await ingestStripeSignals();
    setStatus(`stripe signals ingested: ${response.ingested}`);
  }

  async function onDisconnect(provider: "google" | "stripe") {
    const response = await disconnectIntegration(provider);
    setStatus(`${provider}: ${response.status}`);
  }

  async function onScheduleAll() {
    const response = await scheduleAllDailyRuns();
    setStatus(`scheduler: ${response.status}`);
  }

  async function onRetryFailed() {
    const response = await retryFailedDailyRuns();
    setStatus(`retry: ${response.status}`);
  }

  return (
    <div className="card stack">
      <h1>Settings</h1>
      <p>Integrations</p>

      <div className="card stack">
        <h2>Google</h2>
        <div className="row">
          <button onClick={() => void onGoogleConnect()}>Connect Google OAuth</button>
          <button className="secondary" onClick={() => void onDisconnect("google")}>
            Disconnect
          </button>
        </div>
      </div>

      <div className="card stack">
        <h2>Stripe</h2>
        <label className="stack">
          <span className="small">Restricted read-only API key</span>
          <input value={stripeKey} onChange={(e) => setStripeKey(e.target.value)} placeholder="rk_live_..." />
        </label>
        <div className="row">
          <button onClick={() => void onStripeConnect()}>Connect Stripe</button>
          <button className="secondary" onClick={() => void onStripeIngest()}>
            Ingest Signals
          </button>
          <button className="secondary" onClick={() => void onDisconnect("stripe")}>
            Disconnect
          </button>
        </div>
      </div>

      <div className="card stack">
        <h2>Reliability Ops</h2>
        <div className="row">
          <button onClick={() => void onScheduleAll()}>Queue Daily Runs</button>
          <button className="secondary" onClick={() => void onRetryFailed()}>
            Retry Failed Jobs
          </button>
        </div>
      </div>

      {status ? <p className="small">{status}</p> : null}
    </div>
  );
}
