# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Verdure** is an Android personal AI assistant application that uses on-device AI (Gemini Nano via Google AICore) to provide intelligent, privacy-first assistance. The project aims to create a secure, on-device AI experience integrated with LineageOS.

Key characteristics:
- **Privacy-first**: All AI processing happens on-device via Gemini Nano (no cloud API calls)
- **Tool-based architecture**: Extensible system where AI capabilities are modularized as "tools"
- **Android native**: Kotlin-based Android app targeting SDK 34

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
- **Coroutines**: 1.7.3 (for async AI operations)
- **AI Edge SDK**: 0.1.0 (Google's Gemini Nano on-device AI)

## Development Environment

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Java Version**: 17 (source/target compatibility)
- **Gradle**: Uses Java 21 JVM (configured in `gradle.properties`)

## Important Notes

### AICore Requirements
Gemini Nano requires AICore to be enabled on the device. If the app shows "Failed to initialize", the user needs to:
1. Enable Developer Options
2. Enable AICore in Developer Options
3. Download the Gemini Nano model (happens automatically when AICore is enabled)

This is referenced in `GeminiNanoEngine.kt:66` error message.

### Current Prototype Status
The app is in early prototype phase (version 1.0-prototype):
- NotificationTool's `getRecentNotifications()` returns empty list (not yet implemented)
- Tool routing is simple keyword matching, not AI-based
- Only basic UI for testing, no production interface yet

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
