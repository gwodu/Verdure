# Verdure 2.0 - Web-First MVP

## Stack
- Web: Next.js (App Router)
- Backend: FastAPI + Celery
- Data: Postgres + Redis
- Runtime: Docker Compose

## Quick Start
1. Create a `.env` from `.env.example` and fill provider keys.
2. Run `docker compose up --build`.
3. Open `http://localhost:3000`.
4. Login at `/login` using magic link flow.

## MVP Routes
- `/login`
- `/today`
- `/onboarding`
- `/settings`
- `/history`

## Backend Highlights
- Session-based auth via magic-link verification.
- LLM planner supports tool-calling (`verdure_create_daily_plan`) with strict contract validation.
- XML fallback parser is supported via `VERDURE_XML_FALLBACK_PLAN`.
- Approval-gated execution for external actions:
  - Gmail drafts prepared pre-approval, sent only on approval.
  - Calendar events prepared pre-approval, created only on approval.
  - Purchase intents are record-only.
- Stripe read-only signal ingestion from invoices and charges.
- Reliability:
  - Celery beat schedules daily planning for all users.
  - Worker retries with backoff for transient failures.
  - Failed jobs persisted in `failed_jobs` and retryable/dead-lettered.
  - Idempotency keys on prepared actions + execution attempt/error tracking.

## Required Config for Real Integrations
- `ANTHROPIC_API_KEY`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI`
- `TOKEN_ENCRYPTION_KEY` (Fernet key)
- Stripe restricted read-only API key (provided in Settings)

## Suggested Verification Flow
1. Login at `/login`.
2. Complete `/onboarding`.
3. Connect Google and Stripe in `/settings`.
4. Trigger `/today` daily plan generation and verify approvals appear.
5. Approve one email and one calendar action; verify external effects.
6. In `/settings`, click `Queue Daily Runs` then `Retry Failed Jobs` to test reliability paths.
