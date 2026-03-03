"use client";

import { useEffect, useState } from "react";
import { me, requestMagicLink, verifyMagicLink } from "../../lib/api";

export default function LoginPage() {
  const [email, setEmail] = useState("founder@example.com");
  const [token, setToken] = useState("");
  const [magicLink, setMagicLink] = useState("");
  const [status, setStatus] = useState("");

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const queryToken = params.get("token");
    if (queryToken) {
      setToken(queryToken);
    }
  }, []);

  async function onRequest() {
    const response = await requestMagicLink(email);
    setMagicLink(response.magic_link);
    setStatus("Magic link generated. Open it or paste token below.");
  }

  async function onVerify() {
    await verifyMagicLink(token);
    await me();
    window.location.href = "/today";
  }

  return (
    <div className="card stack">
      <h1>Login</h1>
      <p className="small">Passwordless magic link login for MVP.</p>

      <label className="stack">
        <span className="small">Email</span>
        <input value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <button onClick={() => void onRequest()}>Request Magic Link</button>

      {magicLink ? (
        <label className="stack">
          <span className="small">Magic Link (dev)</span>
          <input value={magicLink} readOnly />
        </label>
      ) : null}

      <label className="stack">
        <span className="small">Token</span>
        <input value={token} onChange={(e) => setToken(e.target.value)} placeholder="Paste token from link" />
      </label>
      <button onClick={() => void onVerify()}>Verify and Sign In</button>

      {status ? <p className="small">{status}</p> : null}
    </div>
  );
}
