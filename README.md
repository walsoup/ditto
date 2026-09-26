<p align="center">
  <img src="art/icon.png" width="120" height="120" alt="Ditto App Icon" />
</p>

<h1 align="center">Ditto</h1>

<p align="center">
  <strong>Record voice notes anywhere. Paste them instantly into any chat.</strong>
</p>

<p align="center">
  <a href="https://github.com/walsoup/ditto/releases/latest"><img src="https://img.shields.io/badge/release-v0.0.3--alpha-2D6A4F?style=flat-square" alt="Release Version" /></a>
  <a href="https://github.com/walsoup/ditto/releases/latest"><img src="https://img.shields.io/badge/apk_size-1.5_MB-2D6A4F?style=flat-square" alt="APK Size" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-E8F1EC?style=flat-square&color=2D6A4F&labelColor=E8F1EC" alt="License" /></a>
  <img src="https://img.shields.io/badge/platform-Android_7.0%2B-lightgrey?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/network-100%25_offline-success?style=flat-square" alt="Network Offline" />
</p>

---

Ditto is an ultralight, distraction-free voice note assistant for Android. It floats on your screen edge as a minimal bubble. Tap to record, tap to stop—Ditto automatically pastes the audio clip straight into your active chat (Signal, WhatsApp, Telegram, Discord, Slack, SMS) without switching apps.

No ads. No accounts. No network permissions. 1.5 MB total size.

---

## Highlights

- **Per-App Floating Whitelist**: Choose exactly which apps show the floating recorder via an interactive search-and-check app list. The bubble automatically hides on the home screen or outside your chosen apps.
- **WhatsApp & Messenger Native Support**: Automatically encodes to compressed `.m4a` (AAC) and routes to native share targets when chatting in WhatsApp without "invalid file" errors.
- **Edge Floating Bubble**: One-tap voice recording accessible from any application via an overlay dock.
- **Auto-Paste Assistant**: Built-in accessibility integration copies the audio file and triggers paste into your active input field immediately after capture.
- **Thumb-Zone Recording Dock**: Ergonomic bottom pill featuring a monospace digital timer, real-time audio amplitude meter, and direct action buttons.
- **8 On-Device Voice Filters**: Real-time DSP profiles including Studio Polish, Cyber Robot, Chipmunk, Deep Titan, Lo-Fi Radio, Echo Chamber, and Fast Rant.
- **Zero Internet Permissions**: Ditto does not declare `android.permission.INTERNET`. Audio never leaves your physical device.
- **Featherweight (1.5 MB)**: Optimized with R8 whole-program tree-shaking and resource shrinking.
- **Flat Pastel Palette**: High-contrast, low-eye-strain Material 3 design using warm linen cream (`#FAF9F6`), soft sage (`#E8F1EC`), and forest accents (`#2D6A4F`).

---

## Download

Get the signed standalone APK from GitHub Releases:

👉 **[Download Ditto v0.0.3-alpha APK](https://github.com/walsoup/ditto/releases/download/0.0.3-alpha/ditto-v0.0.3-alpha.apk)** *(1.5 MB)*

Compatible with Android 7.0 (Nougat / API 24) and higher.

---

## Setup & Permissions

Ditto provides an interactive status dashboard on first launch:

| Component | Permission | Why it is needed |
|---|---|---|
| **Audio Input** | `RECORD_AUDIO` | Records your voice note through the microphone. |
| **Floating Dock** | `SYSTEM_ALERT_WINDOW` | Displays the edge recording bubble over other apps. |
| **Auto-Paste** | `AccessibilityService` | Detects when you stop recording and triggers paste in the active text field. |
| **Background Service** | `POST_NOTIFICATIONS` | Maintains audio capture state without being killed by Android battery management (Android 13+). |

*Note: The Auto-Paste accessibility service is strictly used to emit standard clipboard paste actions to your focused text field. It does not monitor keystrokes or log user content.*

---

## Design System

Ditto uses a strict **60-30-10 Pastel Material 3** visual language:

- **60% Base**: Warm linen cream (`#FAF9F6`) and clean card backgrounds (`#FFFFFF`).
- **30% Structure**: Soft sage containers (`#E8F1EC`), hairline dividers (`#E0E6E2`), slate text (`#1F2421`).
- **10% Accent**: Deep forest sage (`#2D6A4F`) for primary triggers, warm terracotta (`#D47255`) for live recording states.

No purple accents, no neon glows, no gradients.

---

## Building from Source

### Requirements
- **JDK 21** (`~/.local/jdk-21` or system path)
- **Android SDK** (API 36 compile SDK, min SDK 24)

### Steps

```bash
# Clone repository
git clone https://github.com/walsoup/ditto.git
cd ditto

# Set JDK 21
export JAVA_HOME="$HOME/.local/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

# Build debug APK
./gradlew assembleDebug

# Or build optimized release APK (1.18 MB)
./gradlew assembleRelease
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`  
Release APK output: `app/build/outputs/apk/release/app-release.apk`

---

## License

Distributed under the MIT License. Copyright (c) 2026 **walsoup**. See [LICENSE](LICENSE) for details.
