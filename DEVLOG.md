# Verdure Development Log

## Session 1 - November 15, 2025

### What We Built
- ✅ Set up GitHub Actions workflow for automated APK builds
- ✅ Fixed Gradle configuration for CI/CD compatibility
- ✅ Created Android app structure with Kotlin
- ✅ Implemented basic UI with test buttons
- ✅ Created CLAUDE.md documentation

### What We Discovered
**Critical Finding:** Pixel 8A limitations with on-device AI
- Gemini Nano unavailable for third-party apps on Pixel 8A (8GB RAM limitation)
- Only Pixel 9+ devices support ML Kit GenAI APIs
- Experimental AICore SDK is deprecated
- Pixel 8A Gemini Nano restricted to system features only (Gboard, Recorder)

**Error encountered:**
```
AICore failed with error type 2-INFERENCE_ERROR
Error code 8-NOT_AVAILABLE: Required LLM feature not found
```

### Pivot Decision
**From:** Conversational AI assistant using Gemini Nano
**To:** "Silent partner" for intelligent process management

**Why:**
- Cloud APIs defeat privacy-first philosophy
- Need on-device solution that actually works on Pixel 8A
- Focus on practical intelligence over conversation

### New Direction: Notification Intelligence MVP

**Goal:** Smart notification prioritization using semantic + temporal understanding

**Why this approach:**
- Simple keywords ("urgent") are too brittle
- Need semantic understanding of context
- Temporal reasoning critical (deadline proximity)
- Achievable with lightweight on-device models

**Architecture:**
- Semantic embeddings (~80MB TensorFlow Lite model)
- Date/time extraction and urgency calculation
- Combined scoring: semantic importance × temporal urgency

### Roadmap Defined

**Stage 1: Notification Access** (Next milestone)
- [ ] Implement NotificationListenerService
- [ ] Request notification access permission
- [ ] Read: app, title, text, timestamp
- [ ] Display all notifications in app UI
- [ ] Test on Pixel 8A

**Stage 2: Intelligent Classification**
- [ ] Semantic embeddings for context understanding
- [ ] Temporal reasoning (extract dates, calculate urgency)
- [ ] Combine signals for priority scoring
- [ ] Reorder notifications (urgent/important first)

**Stage 3: Learning & Refinement**
- [ ] Track user behavior (which notifications opened)
- [ ] Improve accuracy over time
- [ ] Optional: Add Llama 3.2 1B for complex reasoning

### Technical Decisions

**Rejected:** Rule-based keyword matching alone
**Reason:** Too simplistic, doesn't understand context or deadlines

**Chosen:** Semantic embeddings + temporal extraction
**Reason:** Understands meaning AND urgency, runs on-device

**Future consideration:** Small LLM (Llama 3.2 1B) for complex tasks only

### Next Session Goals
1. Implement NotificationListenerService
2. Set up permission handling
3. Read notification data
4. Display in simple list UI
5. Prove we can access notification content

### Notes
- Android NotificationListenerService gives access to: app, title, text (preview), timestamp, category, priority
- Cannot read full email body, only preview (~2-3 sentences)
- This is sufficient for importance classification
- No ML model needed yet - get data access working first

---

## Session 2 - November 16, 2025

### What We Built

**Stage 1 MVP: Notification Access** ✅ COMPLETED
- ✅ Implemented `VerdureNotificationListener` service (NotificationListenerService)
- ✅ Created `NotificationData` model with app, title, text, timestamp, priority
- ✅ Added permission request flow with Settings integration
- ✅ Real-time notification display using StateFlow
- ✅ UI showing scrollable list with formatted timestamps

**Stage 2A: Day Planner with Temporal Prioritization** ✅ COMPLETED
- ✅ Calendar integration (`CalendarReader`) to fetch upcoming events
- ✅ System state monitoring (`SystemStateMonitor`) tracking:
  - Time of day (morning/afternoon/evening/night)
  - Do Not Disturb mode
  - Work hours detection
- ✅ Temporal prioritization algorithm combining:
  - Android base priority
  - Recency scoring (newer = more important)
  - Time-of-day context (night = lower priority)
  - DND mode consideration
- ✅ Day planner UI showing:
  - System context (time, DND status, work hours)
  - Upcoming calendar events with urgency labels
  - Color-coded prioritized notifications

**Files Changed:**
- Stage 1: 5 files, +267 lines
- Stage 2A: 6 files, +540 lines
- Total: 11 files modified/added

### What We Discovered

**Rapid Progress:**
- Completed both Stage 1 MVP and Stage 2A in one session
- Android's NotificationListenerService works perfectly for notification access
- Calendar API provides rich event data (title, start/end times, all-day flag)
- System state APIs (NotificationManager for DND, Calendar for time) are reliable

**Temporal Prioritization Insights:**
- Simple recency + priority + context scoring works well
- Time decay function: `1.0 / (1 + (ageMinutes / 60.0))` balances old vs new
- DND mode should reduce priority of non-urgent notifications
- Night-time notifications automatically deprioritized

**UI/UX Learnings:**
- Color-coded priorities (red/yellow/green) provide instant visual feedback
- Showing system context helps user understand why notifications are prioritized
- Calendar integration makes the app a "day planner" not just notification manager

### Technical Decisions

**Permission Handling:**
- Chose explicit Settings redirection over in-app explanation
- Users already familiar with notification permission flow
- Clear toast messages guide the user

**Temporal Scoring Algorithm:**
- Weighted combination: `base_priority + recency_score + context_modifiers`
- Context modifiers: -1 for night, -0.5 for DND on non-urgent
- Scales well, easy to tune weights

**Calendar Integration:**
- Read-only access, no modification needed
- Fetch today + tomorrow events (48-hour window)
- Urgency labels: <1hr = URGENT, <3hr = SOON, <24hr = TODAY

### Challenges Encountered

1. **Calendar Permission:**
   - Required READ_CALENDAR permission in manifest
   - Added runtime permission request flow

2. **StateFlow Threading:**
   - Needed to update UI from coroutines
   - Used `runOnUiThread` for TextView updates from Flow collection

3. **NFS File Issue:**
   - Temporary `.nfs` file appeared (likely from file system operations)
   - Deleted but reappeared, ultimately committed for safety

### Next Steps

**Stage 2B: Semantic Understanding** (Next milestone)
- [ ] Add sentence embeddings (TensorFlow Lite)
- [ ] Match notification content to calendar events
- [ ] Example: "Meeting link" notification → Match to "Team Meeting" calendar event
- [ ] Boost priority of notifications related to upcoming events

**Stage 3: Learning & Refinement**
- [ ] Track which notifications user actually opens
- [ ] Learn from user behavior patterns
- [ ] Adjust scoring weights based on user interactions

**Polish & Testing:**
- [ ] Test with real-world notifications over several days
- [ ] Refine temporal scoring weights
- [ ] Add user preferences for priority thresholds
- [ ] Improve UI/UX (icons, better formatting)

### Metrics

**Session Duration:** ~2 hours (10:00 - 12:00)
**Commits:** 3 (2 major features, 1 maintenance)
**Lines of Code:** ~800 added
**Stages Completed:** 2 of 3 planned stages

### Notes

- The app is now a functional day planner with intelligent notification prioritization
- No ML models needed yet - temporal + priority scoring works well
- Ready for real-world testing on Pixel 8A
- Semantic understanding (Stage 2B) will be the next major enhancement
