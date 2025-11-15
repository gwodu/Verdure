# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
- Future: Optional small LLM (Llama 3.2 1B) for complex tasks only

## Build Commands

All commands should be run from the `VerdureApp/` directory.

### Build the app
```bash
cd VerdureApp
./gradlew build
```

### Build debug APK
```bash
cd VerdureApp
./gradlew assembleDebug
```

### Build release APK
```bash
cd VerdureApp
./gradlew assembleRelease
```

### Clean build
```bash
cd VerdureApp
./gradlew clean
```

### Install on connected device
```bash
cd VerdureApp
./gradlew installDebug
```

## Testing

No test framework is currently set up. When adding tests, use the standard Android testing setup.

## Architecture

### Core Components

The codebase follows a layered architecture with three main layers:

1. **Core Layer** (`com.verdure.core/`)
   - `GeminiNanoEngine`: Low-level wrapper around Google's Gemini Nano model via AICore. Handles initialization, configuration, and content generation. All AI inference happens on-device.
   - `VerdureAI`: Central orchestrator that manages tool registration, routes user requests to appropriate tools, and falls back to direct LLM conversation when no tool matches.

2. **Tools Layer** (`com.verdure.tools/`)
   - `Tool`: Base interface for all AI-powered capabilities
   - `NotificationTool`: Example tool for analyzing and prioritizing notifications (prototype stage)
   - Tools are registered with VerdureAI at runtime and invoked based on keyword matching (prototype) or AI-based intent detection (planned)

3. **UI Layer** (`com.verdure.ui/`)
   - `MainActivity`: Entry point that initializes the AI engine, registers tools, and provides test UI

### Tool System Design

The tool system is designed for extensibility:
- Each tool implements the `Tool` interface with `name`, `description`, and `execute()` method
- Tools receive parameters as `Map<String, Any>` for flexibility
- Tools can use the Gemini engine for AI-powered processing
- Currently uses keyword-based routing in `VerdureAI.kt:45-81`, planned enhancement to use Gemini for intelligent tool selection

### Request Flow

User input → `VerdureAI.processRequest()` → Tool selection (keyword-based) → Tool execution → Response

For unmatched queries, falls back to direct Gemini Nano conversation.

## Key Dependencies

- **Kotlin**: 1.9.22
- **Android Gradle Plugin**: 8.2.0
- **AndroidX**: Core KTX, AppCompat, Material Design
- **Coroutines**: 1.7.3 (for async operations)
- **AI Edge SDK**: 0.0.1-exp01 (Deprecated - non-functional on Pixel 8A, will be replaced)

**Planned additions:**
- TensorFlow Lite (for on-device ML models)
- Sentence Transformers (for semantic embeddings)
- Optional: MLC LLM (for Llama 3.2 if needed)

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
- GeminiNanoEngine present but non-functional on Pixel 8A (see limitations above)

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
