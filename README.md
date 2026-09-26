# Ditto

Record voice notes and paste them directly into your chats.

Ditto is an Android audio utility built with Jetpack Compose. It lets you record a voice note from a floating screen-edge bubble and immediately paste the audio clip into whatever app you're in—WhatsApp, Signal, Telegram, Discord, or Messages—without jumping between windows.

---

## Features

- **Thumb-Zone Recording Dock**: Ergonomic bottom pill with a monospace timer, real-time amplitude bars, and a compact record button.
- **Floating Overlay Bubble**: An edge-docked microphone button accessible from any screen.
- **Auto-Paste Assistant**: Optional Accessibility Service that triggers an Android paste action right as you stop recording, dropping the audio into the focused input field.
- **On-Device Voice Filters**: 8 local DSP sound profiles (Raw, Studio polish, Cyber Robot, Chipmunk, Deep Titan, Lo-Fi Radio, Echo, Fast Rant).
- **Format Toggle**: Export as standard 16-bit uncompressed WAV or compact AAC/M4A via hardware MediaCodec.
- **Local-First & Private**: All capture, filtering, and storage happen strictly on your device. Zero telemetry, zero network permissions.
- **Pastel Material 3**: Flat warm linen cream (`#FAF9F6`) and soft sage (`#E8F1EC`) palette with no neon glows or gradients.

---

## Permissions Setup

Ditto includes an interactive setup checklist on the main screen to guide you through required permissions:

| Permission | Purpose |
|------------|---------|
| **Microphone** | Records audio notes (`RECORD_AUDIO`). |
| **Display Overlay** | Shows the floating bubble over other apps (`SYSTEM_ALERT_WINDOW`). |
| **Auto-Paste Service** | Detects the active text cursor and executes the paste action (`AccessibilityService`). |
| **Notifications** | Keeps the background recording foreground service active on Android 13+ (`POST_NOTIFICATIONS`). |

---

## Building from Source

### Prerequisites
- Android SDK (API level 36 compile SDK, min SDK 24)
- JDK 21

### Build & Run

```bash
# Clone the repository
git clone https://github.com/walsoup/ditto.git
cd ditto

# Set Java 21 environment
export JAVA_HOME="$HOME/.local/jdk-21" # adjust to your JDK 21 path
export PATH="$JAVA_HOME/bin:$PATH"

# Build debug APK
./gradlew assembleDebug

# Install and start on connected device
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.walsoup.ditto/.MainActivity
```

---

## License

MIT License. Copyright (c) 2026 walsoup. See [LICENSE](LICENSE) for details.
