<p align="center">
  <img src="art/icon.png" width="120" height="120" alt="Ditto app icon" />
</p>

<h1 align="center">Ditto</h1>

<p align="center">
  Record from a floating bubble or a double-tap of Volume Down. Share the voice note without opening a separate recorder.
</p>

<p align="center">
  <a href="https://github.com/walsoup/ditto/releases/latest">Download the latest APK</a> · Android 7.0+ · <a href="LICENSE">MIT license</a>
</p>

---

Ditto is a small Android voice recorder that sits over the app you're using. Start a recording, pause when you need to, then copy or share the clip. Optional auto-paste tries to put the audio into the focused chat field; WhatsApp uses a direct share flow instead.

No account, no ads, and no `INTERNET` permission. Recording and voice filters run on your phone. Sending a clip through another app is handled by that app.

**Alpha software:** chat integration depends on the receiving app, Android version, and device. There is no published compatibility test matrix yet, so support for specific apps and devices is unverified, not guaranteed.

## Install

1. Open the [latest release](https://github.com/walsoup/ditto/releases/latest) and download `app-release.apk` under **Assets**.
2. Allow your browser or file manager to install unknown apps if Android asks, then install the APK.
3. Open Ditto and follow the permission checklist below.

Current release: [v0.0.7-alpha](https://github.com/walsoup/ditto/releases/tag/v0.0.7-alpha). Its [APK](https://github.com/walsoup/ditto/releases/download/v0.0.7-alpha/app-release.apk) is about 1.6 MB. Minimum Android version: 7.0 (API 24).

## Record and send

1. Allow microphone access. For the floating bubble, also allow **Display over other apps** and turn the bubble on.
2. If you only want the bubble in certain apps, enable **Target Apps Filter** in Settings and choose those apps. Foreground-app detection needs the accessibility service.
3. Open your chat and focus its message field. Tap the bubble to start recording; use pause/resume if needed, then stop.
4. With auto-paste enabled, Ditto copies the audio and attempts a paste. For WhatsApp, it prepares M4A audio and opens that app's share flow. You still choose the recipient and send it yourself.
5. For review and filters before sending, turn auto-paste off. Use the recording's Copy or Share controls when you're ready. If a chat cannot accept audio from the clipboard, try Share instead.

You can also record inside Ditto without the floating bubble. The accessibility service is not needed for basic in-app recording and manual sharing.

### Double-tap Volume Down

Enable **Double-Tap Volume Down** in Settings and enable Ditto's accessibility service. The shortcut is **off by default**.

Press Volume Down twice within 450 ms to start recording, or again while recording to stop. It uses the floating recorder service. Start gives one vibration pulse; stop gives two, on devices with vibration support.

The shortcut toggles recording, not pause/resume. Single presses still pass through; the handled second press is consumed, so volume changes can still occur. Key handling depends on the device and Android's accessibility support.

### Quick Settings tile

Add Ditto's tile from Android's Quick Settings editor. It turns the floating bubble service on or off; it does not start or stop a recording by itself.

## What else it does

- **Pause and resume:** available in the in-app recording dock and floating overlay.
- **Bubble controls:** size, opacity, circle/squircle/pill shapes, color themes, idle dimming, and snap-to-edge.
- **On-device voice filters:** preview and apply effects before copying or sharing, with auto-paste off.
- **Audio formats:** M4A (AAC) and WAV for exports, clipboard copying, and sharing.
- **History:** replay and reuse saved clips, with undo for individual deletions.

## What's new in v0.0.7-alpha

- Pause and resume an ongoing recording from the in-app dock or floating overlay.
- Start/stop vibration feedback for recording, including volume-key triggers.
- Fixed history deletion undo: undo now keeps the audio file instead of restoring an empty reference.

See the [release notes](https://github.com/walsoup/ditto/releases/tag/v0.0.7-alpha).

## Permissions and privacy

| Permission or access | What Ditto uses it for |
|---|---|
| Microphone (`RECORD_AUDIO`) | Record your voice. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Show the floating recorder over other apps. |
| Accessibility service | Detect the foreground app for the app filter and sharing route; inspect the active window's UI nodes to find the focused field and request Android's paste action; filter key events for the optional Volume Down shortcut. |
| Installed-app visibility (`QUERY_ALL_PACKAGES`) | List installed apps so you can select where the bubble appears. This is declared in the manifest, not a separate runtime permission prompt. |
| Notifications (`POST_NOTIFICATIONS`, Android 13+) | Show the floating recorder's foreground-service notification. |
| Foreground services (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`) | Run the floating recorder and microphone service. These are manifest declarations. |
| Vibration (`VIBRATE`) | Recording start/stop feedback. |

Accessibility is powerful access. Ditto's service is configured to retrieve window content and request key-event filtering. The current code uses package names and UI-node focus/editability to find the paste target; it does not implement a typed-text logger. That is narrower than its granted access, so "it only triggers a paste" would be misleading.

The manifest does **not** request `INTERNET`. Ditto has no built-in audio upload. Copying puts an audio URI on Android's clipboard and grants the receiving app access to the file; sharing also grants file access. Once you send a recording, the receiving app's own storage, network, and privacy rules apply.

Android backup is enabled in the manifest, so "no internet permission" is not a promise that system backups never leave the device.

## Compatibility and feedback

Android 7.0+ is the build's minimum requirement, not proof that every device or chat app works. The code has a WhatsApp-specific M4A/share route; other apps use clipboard paste or Android sharing. These paths still need device testing.

If something breaks, [open an issue](https://github.com/walsoup/ditto/issues) with:

- Android version and device model.
- Chat app and its version.
- Whether you used the bubble, volume shortcut, in-app recorder, auto-paste, or Share.
- What you expected and what happened.

Don't include private audio or chat contents.

## Build it yourself

Requirements: JDK 21 and the Android SDK with platform 36 installed. The app targets SDK 36 and supports API 24+.

```bash
git clone https://github.com/walsoup/ditto.git
cd ditto

# Make sure JAVA_HOME points to your JDK 21 installation.
chmod +x gradlew
./gradlew assembleDebug
./gradlew assembleRelease
```

APKs land in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

For your own release signing, configure the values in `.env.example` through a local `.env` or environment variables. Without a configured, existing release keystore, the build falls back to debug signing. Keep signing keys and passwords out of git.

## License

MIT. See [LICENSE](LICENSE).
