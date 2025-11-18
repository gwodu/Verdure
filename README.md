# Verdure

**Personal AI Assistant for Android**

Verdure is an Android app that provides intelligent, privacy-first assistance through on-device AI processing. It acts as a "silent partner" that intelligently manages processes and notifications without being chatty or intrusive.

## Key Features

- **Privacy-first**: All AI processing happens on-device (no cloud API calls)
- **Smart notification prioritization**: Intelligent ranking based on importance and urgency
- **Calendar integration**: Day planner with upcoming events
- **Tool-based architecture**: Extensible system where capabilities are modularized
- **On-device LLM**: Llama 3.2 1B running entirely on your phone via llama.cpp

## Building the App

### Automated Build (Recommended)

All builds are automated through GitHub Actions:

1. Push code to the repository
2. GitHub Actions automatically builds the APK
3. Download APK from: https://github.com/gwodu/Verdure/actions

### Local Build (Optional)

```bash
cd VerdureApp
./gradlew clean
./gradlew assembleDebug --no-daemon --max-workers=2
```

## Setting Up the AI Model

Verdure uses **Llama 3.2 1B Instruct (Q4_K_M quantized)** for on-device AI inference.

### Download the Model

1. **Download from HuggingFace:**
   - Model: [bartowski/Llama-3.2-1B-Instruct-GGUF](https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF)
   - File: `Llama-3.2-1B-Instruct-Q4_K_M.gguf`
   - Size: ~700 MB

2. **Direct download link:**
   ```bash
   wget https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf
   ```

### Place the Model in Your App

1. **Create the models directory:**
   ```bash
   mkdir -p VerdureApp/app/src/main/assets/models
   ```

2. **Copy the model file:**
   ```bash
   cp Llama-3.2-1B-Instruct-Q4_K_M.gguf VerdureApp/app/src/main/assets/models/
   ```

3. **Verify:**
   ```bash
   ls -lh VerdureApp/app/src/main/assets/models/
   # Should show: Llama-3.2-1B-Instruct-Q4_K_M.gguf (~700 MB)
   ```

### Build with Model

Once the model is in place, build the APK as usual. The model will be bundled into the APK (final APK size will be ~800 MB).

**Note:** GitHub Actions builds will fail if the model is not present in the assets directory.

## Permissions Required

1. **Notification Listener Access**: Required to read notifications
   - Settings → Apps → Special app access → Notification access → Enable for Verdure

2. **Calendar Access**: Required to read calendar events
   - Granted at runtime when you first open the app

## Testing the AI

Once installed with the model:

1. Open Verdure
2. Grant permissions
3. Tap **"Test LLM: Say Hello"** button
4. You should see a real AI response from Llama 3.2 running on your device!

## Architecture

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

**Key components:**
- **LLMEngine**: Abstraction for swappable LLM backends (currently llama.cpp)
- **VerdureAI**: Central orchestrator that routes requests to tools
- **Tools**: Modular capabilities (NotificationTool, etc.)
- **Services**: Background data collection (VerdureNotificationListener, etc.)

## Development

- **Language**: Kotlin
- **Min SDK**: 31 (Android 12)
- **Target SDK**: 34 (Android 14)
- **Primary Device**: Google Pixel 8A

For development logs and session notes, see [DEVLOG.md](DEVLOG.md).

## License

(Add your license here)
