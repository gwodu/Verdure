# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Session Start Protocol

**IMPORTANT:** At the start of EVERY new conversation session, Claude Code should automatically:
1. Read `DEVLOG.md` to understand the latest project state
2. Identify the most recent session entry and commit hash
3. Note any "Ready for Testing" items, blockers, or open decisions
4. Provide a brief status summary (2-3 sentences) without being asked

This ensures Claude is always up-to-date on project progress without manual prompting.

## Project Overview

**Verdure** is an Android personal AI assistant that provides intelligent, privacy-first assistance through on-device processing. The goal is to create a "silent partner" that intelligently manages processes and notifications without being chatty or intrusive.

Key characteristics:
- **Privacy-first**: All processing happens on-device (no cloud API calls)
- **Silent partner philosophy**: Intelligent automation over conversation
- **Tool-based architecture**: Extensible system where capabilities are modularized as "tools"
- **Android native**: Kotlin-based Android app targeting SDK 34

## Current Status & Important Context

### Gemini Nano Limitations on Pixel 8A
- Pixel 8A does NOT support ML Kit GenAI APIs (only Pixel 9+ devices)
- The experimental AICore SDK (`com.google.ai.edge.aicore`) is deprecated
- Gemini Nano on Pixel 8A only powers system features (Gboard, Recorder)
- No third-party app access for custom prompts due to 8GB RAM limitation

### New Direction: Notification Intelligence
Instead of relying on unavailable LLM APIs, Verdure focuses on practical, achievable intelligence:
- Smart notification prioritization using semantic understanding + temporal reasoning
- On-device processing using lightweight models or rule-based systems
- On-device LLM: Gemma 3 1B (4-bit quantized) via Google MediaPipe

## Build System

**Primary build method: GitHub Actions CI/CD**

All builds are automated through GitHub Actions. When you push code to the repository:
1. GitHub Actions automatically builds the APK
2. APK is available as an artifact in the workflow run
3. Download from: https://github.com/gwodu/Verdure/actions

**IMPORTANT:** Do NOT build locally using Gradle unless explicitly requested. Always commit changes and push to trigger the automated build.

### Local Build Commands (optional)

If local builds are required, all commands should be run from the `VerdureApp/` directory.

**Build debug APK:**
```bash
cd VerdureApp
./gradlew clean
./gradlew assembleDebug --no-daemon --max-workers=2
```

**Build release APK:**
```bash
cd VerdureApp
./gradlew assembleRelease --no-daemon --max-workers=2
```

**Install on connected device:**
```bash
cd VerdureApp
./gradlew installDebug
```

**Note:** Use `--no-daemon --max-workers=2` to avoid resource exhaustion on limited systems.

## DEVLOG Documentation Standards

Document sessions in `DEVLOG.md` focusing on **decisions and tradeoffs only**.

### Format

```markdown
## Session X - Date

**Decision:** [What was decided]
**Why:** [Root cause, problem solved]
**Tradeoff:** [What gained vs what lost]
```

### Example

```markdown
**Decision:** Java 21 required (not Java 17)
**Why:** MediaPipe compiled with Java 21, cannot downgrade
**Tradeoff:** Future-proof LTS vs no backwards compatibility (acceptable, new project)

**Decision:** Disable Jetifier
**Why:** Can't process Java 21 bytecode, MediaPipe already uses AndroidX
**Tradeoff:** Build succeeds vs can't use pre-AndroidX libs (all deps modern)
```

**Keep it concise. Skip implementation details (git has that). Capture WHY, not WHAT.**

## Testing

No test framework is currently set up. When adding tests, use the standard Android testing setup.

## Architecture

### End Goal: LLM as the User Interface

**Vision:** Verdure uses a conversational LLM as the primary UX. Users interact through natural language, and the AI orchestrates various tools to accomplish tasks. The LLM acts as an intelligent router, deciding which tools to invoke based on user intent.

**Example interaction:**
- User: "What's urgent today?"
- LLM understands intent → invokes NotificationTool + CalendarTool
- Tools execute → return data
- LLM synthesizes → responds: "You have 2 urgent notifications and a meeting in 30 min"

### Core Principles

1. **LLM as UX**: Natural language interface, not traditional buttons/menus
2. **Extensible tools**: Easily add new capabilities by implementing the `Tool` interface
3. **On-device AI**: All processing happens locally (Gemma 3 1B via MediaPipe)
4. **Tool orchestration**: LLM decides which tools to use and synthesizes responses
5. **Privacy-first**: No cloud APIs, all data stays on device

### Architecture Layers

```
User speaks naturally
    ↓
LLM understands intent (Gemma 3 1B on-device)
    ↓
VerdureAI routes to appropriate tool(s)
    ↓
Tools execute (read notifications, set reminders, etc.)
    ↓
LLM synthesizes results into natural response
```

#### 1. **Core Layer** (`com.verdure.core/`)

**`LLMEngine`** - Interface for any LLM backend
- Abstraction allows swapping LLM implementations
- Current: `MediaPipeLLMEngine` (Gemma 3 1B via Google MediaPipe)
- Future: Could support other models

**`VerdureAI`** - Central orchestrator
- Routes user requests to appropriate tools
- Manages tool registry (extensible - add any number of tools)
- Falls back to direct LLM conversation when no tool needed
- Currently: keyword-based routing → Future: AI-based intent detection

#### 2. **Tools Layer** (`com.verdure.tools/`)

**Concept:** Tools are capabilities the LLM can invoke to accomplish tasks.

**`Tool` interface:**
```kotlin
interface Tool {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, Any>): String
}
```

**Extensibility:** Simply implement `Tool` interface and register with VerdureAI.

**Current tools:**
- `NotificationTool`: Analyzes and prioritizes notifications

**Planned tools:**
- `ReminderTool`: Sets alarms/reminders via Android APIs
- `CalendarTool`: Reads calendar, creates events
- `MessageTool`: Drafts replies, sends texts
- `ContextTool`: Provides location/time context
- And more... (infinitely extensible)

**Key distinction:**
- Tools **process data on-demand** (when LLM invokes them)
- Tools can use Android APIs directly (set alarms, send texts, etc.)
- Tools return results to LLM for synthesis

#### 3. **Services Layer** (`com.verdure.services/`)

**Concept:** Services passively collect data 24/7 in the background.

**Current:**
- `VerdureNotificationListener`: Listens for notifications, stores in StateFlow

**Planned:**
- `CalendarSyncService`: Monitors calendar changes
- `LocationService`: Tracks location context

**Key distinction:**
- Services are **Android background components** (run 24/7)
- Services **collect data**, Tools **process data**
- Services = passive listeners, Tools = active processors

#### 4. **UI Layer** (`com.verdure.ui/`)

**Current:** Basic testing UI

**Future:** Conversational interface where LLM is the UX

### Services vs Tools

**Why separate?**

| Services (Background) | Tools (On-Demand) |
|-----------------------|-------------------|
| Collect data 24/7 | Process when LLM asks |
| No AI inference | Use LLM for analysis |
| Android framework components | Verdure abstractions |
| Example: Listen for notifications | Example: Analyze notification importance |

**Analogy:** Services are sensors (always collecting), Tools are actuators (do things when needed).

### Extensibility

**Adding a new tool is simple:**
1. Create class implementing `Tool` interface
2. Define `name`, `description`, and `execute()` logic
3. Register with VerdureAI in `MainActivity`
4. LLM can now invoke your tool

**Example - Adding a WeatherTool:**
```kotlin
class WeatherTool : Tool {
    override val name = "weather"
    override val description = "Gets current weather"

    override suspend fun execute(params: Map<String, Any>): String {
        // Call weather API or read local data
        return "Sunny, 72°F"
    }
}

// In MainActivity:
verdureAI.registerTool(WeatherTool())
```

Now users can ask: "What's the weather?" and the LLM will route to WeatherTool.

### Request Flow

**Simple conversation:**
```
User: "Tell me a joke"
→ VerdureAI: No tool needed
→ LLMEngine.generateContent()
→ Response
```

**Tool orchestration:**
```
User: "What's urgent today?"
→ VerdureAI: Routes to NotificationTool + CalendarTool
→ Tools execute, return data
→ LLM synthesizes results
→ "You have 2 urgent notifications and a meeting at 3pm"
```

**Future - Multi-tool orchestration:**
```
User: "Remind me to call Mom when I get home"
→ LLM analyzes intent
→ Invokes LocationService (detect home)
→ Invokes ReminderTool (set reminder with location trigger)
→ "Done! I'll remind you when you arrive home"
```

### Technology Stack

**LLM:** Gemma 3 1B (4-bit quantized) via Google MediaPipe
- ~600-800 MB model size
- Runs entirely on-device
- No internet required
- Official Google solution for on-device AI

**Android APIs:** Tools interact directly with system
- `AlarmManager` for reminders
- `CalendarContract` for calendar
- `SmsManager` for messaging
- `NotificationManager` for notifications

**No cloud dependencies** - everything on-device, privacy-first.

## Key Dependencies

- **Kotlin**: 1.9.22
- **Android Gradle Plugin**: 8.2.0
- **AndroidX**: Core KTX, AppCompat, Material Design
- **Coroutines**: 1.7.3 (for async operations)
- **MediaPipe Tasks GenAI**: 0.10.27 (for on-device Gemma 3 1B inference)

**Deprecated:**
- **AI Edge SDK**: 0.0.1-exp01 (non-functional on Pixel 8A)
- **llama.cpp**: 3.0.0 (not Android-compatible, replaced by MediaPipe)

**Future additions:**
- TensorFlow Lite (for sentence embeddings)
- Sentence Transformers (for semantic similarity)

## Development Environment

- **Min SDK**: 31 (Android 12) - Required by AICore SDK (though not used in production)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Java Version**: 17 (source/target compatibility)
- **Gradle**: Uses system Java (avoid hardcoded paths for CI/CD compatibility)

## Important Notes

### Testing Device
- **Primary device**: Google Pixel 8A
- **OS**: Stock Android (not LineageOS yet)
- **RAM**: 8GB (limits on-device AI capabilities)

### Notification Access Permission
The app requires notification listener permission:
1. Settings → Apps → Special app access → Notification access
2. Enable for Verdure
3. Grant permission when prompted

This is required for NotificationListenerService to function.

### Current Prototype Status
The app is in early prototype phase (version 1.0-prototype):
- NotificationTool's `getRecentNotifications()` returns empty list (not yet implemented)
- Tool routing is simple keyword matching, not AI-based
- Only basic UI for testing, no production interface yet
- MediaPipeLLMEngine implemented with Gemma 3 1B (requires model pushed via adb for testing)

## Development Roadmap

### MVP: Intelligent Notification Manager

**Goal:** Receive all phone notifications and prioritize them by importance/urgency

**Stage 1: Notification Access (Next milestone)**
- [ ] Implement NotificationListenerService
- [ ] Request notification access permission
- [ ] Read notification data: app, title, text, timestamp
- [ ] Display all notifications in app UI
- [ ] Test on Pixel 8A

**Stage 2: Intelligent Classification (Future)**
- [ ] Implement semantic understanding via sentence embeddings (TensorFlow Lite)
- [ ] Add temporal reasoning (extract dates, calculate urgency based on proximity)
- [ ] Combine semantic + temporal signals for importance scoring
- [ ] Reorder notifications: urgent/important at top
- [ ] User can configure app priorities and keywords

**Stage 3: Learning & Refinement (Future)**
- [ ] Track which notifications user actually opens
- [ ] Learn from user behavior
- [ ] Improve classification accuracy over time
- [ ] Optional: Add Llama 3.2 1B for complex reasoning

### Future Tools
- App launcher based on context
- Calendar/schedule optimization
- Email/message drafting assistance (if LLM becomes available)
- Task tracking across notifications (e.g., follow-up on job interview emails)

## Technical Approach

### Notification Intelligence Architecture

**Why not just keywords?**
- Keyword matching ("urgent", "important") is too brittle
- Different apps phrase importance differently
- Need semantic understanding of context

**Why temporal reasoning matters?**
- "Meeting tomorrow 9am" → URGENT
- "Meeting next month" → Can wait
- "Deadline in 2 hours" → CRITICAL
- Need to extract dates/times and calculate proximity to now

**Solution: Semantic Embeddings + Temporal Extraction**
1. **Sentence embeddings** (~80MB model, TensorFlow Lite)
   - Understand semantic meaning, not just keywords
   - Compare notification to learned "urgent" vs "can wait" patterns

2. **Date/time extraction & proximity calculation**
   - Parse relative dates ("tomorrow", "next week")
   - Parse absolute dates ("Jan 15", "3pm")
   - Calculate urgency based on time delta

3. **Combined scoring**
   - Semantic importance × Temporal urgency = Final priority

## Package Structure

```
com.verdure/
├── core/          - AI engine and orchestration
├── tools/         - Modular AI capabilities
├── ui/            - Android UI components
├── services/      - (Empty, planned for background services)
└── data/          - (Empty, planned for data models)
```

## Adding a New Tool

1. Create a new class in `com.verdure.tools/` implementing `Tool` interface
2. Define unique `name` and descriptive `description`
3. Implement `suspend fun execute(params: Map<String, Any>): String`
4. Register the tool in `MainActivity.onCreate()` after AI initialization
5. Add routing logic in `VerdureAI.processRequest()` (or wait for AI-based routing)
