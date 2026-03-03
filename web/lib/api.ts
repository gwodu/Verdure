const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {})
    },
    cache: "no-store"
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export type TodayPayload = {
  date: string;
  primary_focus: string;
  why: string[];
  success: string;
  micro_steps: Array<{ minutes: number; text: string }>;
  confidence: number;
  approvals: Array<{
    id: string;
    title: string;
    type: string;
    details: string;
    status: string;
  }>;
};

export function requestMagicLink(email: string) {
  return request<{ status: string; magic_link: string }>("/api/auth/magic-link/request", {
    method: "POST",
    body: JSON.stringify({ email })
  });
}

export function verifyMagicLink(token: string) {
  return request<{ status: string }>("/api/auth/magic-link/verify", {
    method: "POST",
    body: JSON.stringify({ token })
  });
}

export function me() {
  return request<{ user_id: string; email: string }>("/api/auth/me");
}

export function logout() {
  return request<{ status: string }>("/api/auth/logout", { method: "POST" });
}

export function getToday() {
  return request<TodayPayload>("/api/today");
}

export function postOnboarding(payload: Record<string, unknown>) {
  return request<{ user_id: string }>("/api/onboarding", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function decideApproval(approvalId: string, decision: "approve" | "decline", reason?: string) {
  return request<{ status: string }>(`/api/approvals/${approvalId}/decision`, {
    method: "POST",
    body: JSON.stringify({ decision, reason })
  });
}

export function triggerDailyPlan(forceRefresh = false) {
  return request<{ status: string }>("/api/daily/run", {
    method: "POST",
    body: JSON.stringify({ force_refresh: forceRefresh })
  });
}

export function getGoogleOAuthStart() {
  return request<{ auth_url: string }>("/api/integrations/google/oauth/start");
}

export function connectStripe(apiKey: string) {
  return request<{ status: string }>("/api/integrations/stripe/connect", {
    method: "POST",
    body: JSON.stringify({ api_key: apiKey })
  });
}

export function ingestStripeSignals() {
  return request<{ status: string; ingested: number }>("/api/signals/stripe/ingest", {
    method: "POST"
  });
}

export function disconnectIntegration(provider: "google" | "stripe") {
  return request<{ status: string }>(`/api/integrations/${provider}/disconnect`, {
    method: "POST"
  });
}

export function scheduleAllDailyRuns() {
  return request<{ status: string }>("/api/daily/schedule-all", { method: "POST" });
}

export function retryFailedDailyRuns() {
  return request<{ status: string }>("/api/daily/retry-failed", { method: "POST" });
}
