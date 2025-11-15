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
