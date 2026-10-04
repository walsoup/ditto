<p align="center">
  <img src="art/icon.png" width="120" height="120" alt="Ditto app icon" />
</p>

<h1 align="center">Ditto</h1>

<p align="center">
  Record a voice note and paste it into any chat on Android, without leaving the app you're in.
</p>

<p align="center">
  <a href="https://github.com/walsoup/ditto/releases/latest"><img src="https://img.shields.io/badge/release-v0.0.6--alpha-2D6A4F?style=flat-square" alt="Release" /></a>
  <img src="https://img.shields.io/badge/apk-1.6_MB-2D6A4F?style=flat-square" alt="APK size" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-2D6A4F?style=flat-square" alt="License" /></a>
  <img src="https://img.shields.io/badge/android-7.0%2B-lightgrey?style=flat-square" alt="Android 7.0+" />
</p>

---

Ditto puts a small bubble on the edge of your screen. Tap it to start recording, tap again to stop, and the clip gets pasted into whatever chat you have open. Works in WhatsApp, Telegram, Signal, Discord, Slack and SMS.

It's about 1.6 MB, has no ads, no accounts, and doesn't ask for the internet permission. Your audio stays on your phone.

## How to use it

1. [Install the APK](#install) and open Ditto. Grant the permissions on the checklist (see [Permissions](#permissions)).
2. Turn the bubble on in the app.
3. Choose which apps should show the bubble.
4. Open a chat and tap the bubble to start recording. Tap it again to stop.
5. The clip gets pasted into the text field. Send it.

## What it does

- **Floating bubble.** Sits on the edge of the screen and snaps to it. You can change the size, shape (circle, squircle, pill), color, and opacity, and it dims itself when you're not using it.
- **Per-app whitelist.** Pick which apps the bubble shows up in. Everywhere else, including the home screen, it stays hidden.
- **Auto-paste.** When you stop recording, Ditto copies the clip and pastes it into the text field you're in.
- **WhatsApp works properly.** Clips are encoded as `.m4a` (AAC) so WhatsApp doesn't reject them as invalid files.
- **Voice filters.** Seven of them, processed on the device: Studio Polish, Cyber Robot, Chipmunk, Deep Titan, Lo-Fi Radio, Echo Chamber, and Fast Rant. Mostly for fun.

## Install

Grab the APK from the [releases page](https://github.com/walsoup/ditto/releases/latest). It needs Android 7.0 or newer.

👉 **[Download Ditto v0.0.6-alpha APK](https://github.com/walsoup/ditto/releases/download/0.0.6-alpha/ditto-v0.0.6-alpha.apk)** *(1.6 MB)*

## Permissions

Ditto walks you through these on first launch.

| Permission | Why |
|---|---|
| Microphone | To record. |
| Display over other apps | To show the bubble on top of your chats. |
| Accessibility service | To paste the clip into the focused text field after you stop recording. It only triggers a paste. It doesn't read what you type. |
| Notifications (Android 13+) | Keeps the recorder alive so Android doesn't kill it in the background. |

## Build it yourself

You need JDK 21 and the Android SDK (compile SDK 36, min SDK 24).

```bash
git clone https://github.com/walsoup/ditto.git
cd ditto

export JAVA_HOME="$HOME/.local/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK
```

Outputs land in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

## License

MIT. See [LICENSE](LICENSE).
