# Verdure Web-First MVP Checklist

Stack choice: Next.js (App Router) + FastAPI + Celery + Postgres + Redis + Docker Compose.

## 1) Project Setup and Foundations
- [x] Create monorepo structure (`/web`, `/backend`, `/infra`)
- [x] Add `docker-compose.yml` for `web`, `backend`, `worker`, `postgres`, `redis`
- [x] Add shared environment management (`.env.example`)
- [x] Add base CI checks (compile/build workflow)

## 2) Core Data Model and Persistence
- [x] Implement Postgres schema for MVP tables
- [x] Add migrations + seed data path (migration in place; seed pending)
- [x] Add repository/service layer boundaries

## 3) Auth + User Session
- [x] Implement login (magic link)
- [x] Add backend auth middleware/dependency
- [x] Protect `/today`, `/settings`, `/history`

## 4) Onboarding Flow (`/onboarding`)
- [x] Build onboarding form (snapshot, initiatives, availability/preferences)
- [x] Save onboarding data (`Users`, `Initiatives`)
- [x] Add integration connect UI

## 5) Integrations (Google + Stripe)
- [x] Google OAuth connect/disconnect
- [x] Encrypted token storage in `Integrations`
- [x] Gmail draft + Calendar execution clients
- [x] Stripe read-only signal ingestion

## 6) Briefing Packet Builder
- [x] Compile goal/constraints/context/initiatives/commitments/signals/momentum
- [x] Deterministic formatting + validation
- [x] Persist packet snapshot for auditability

## 7) Daily Decision Engine (LLM)
- [x] Tool-calling path (`verdure_create_daily_plan`)
- [x] Server-side strict rule enforcement
- [x] XML fallback parser + validator
- [x] Persist `DailyFocus` + `PreparedActions`

## 8) `/today` Page
- [x] Build single-page sections (Focus, Approvals, Quick Capture)
- [x] Wire to backend APIs
- [x] Keep UI minimal and uncluttered

## 9) Approval Preparation and Execution
- [x] Email flow (draft creation + external refs)
- [x] Calendar flow (prepare only pre-approval)
- [x] Purchase intent flow (record-only)
- [x] Approve path (execute, update status, audit/event logs)
- [x] Decline path (status + reason + audit)

## 10) Scheduling + Reliability
- [x] Daily job + manual trigger
- [x] Idempotency keys for execution
- [x] Retries and dead-letter strategy
- [x] Failure visibility in logs

## 11) MVP Acceptance Verification
- [ ] User can onboard
- [ ] User receives daily focus
- [ ] User sees approval queue
- [ ] User can approve email drafts and send
- [ ] No external action occurs without approval
- [ ] Decisions persist in DB
- [ ] Actions are auditable
