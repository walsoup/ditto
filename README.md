<p align="center">
  <img src="art/icon.png" width="120" height="120" alt="Ditto app icon" />
</p>

<h1 align="center">Ditto</h1>

<p align="center">
  Record a voice note over any app, then paste it into the chat you were already in.
</p>

<p align="center">
  <a href="https://github.com/walsoup/ditto/releases/latest">Download the APK</a> · Android 7.0+ · about 1.6 MB · <a href="LICENSE">MIT</a>
</p>

---

Ditto is a small Android voice recorder for chats. Tap the floating bubble or double-tap Volume Down, say what you need to say, and stop. Ditto copies the clip and can paste it into the message field for you.

It has no account, no ads, and no `INTERNET` permission. Recording and voice effects run on the phone.

## Install

1. Download `app-release.apk` from the [latest release](https://github.com/walsoup/ditto/releases/latest).
2. If Android asks, allow your browser or file manager to install unknown apps.
3. Open Ditto. The home screen shows a **Finish setup** row until the permissions it needs are granted.

The current version is [v1.0.1](https://github.com/walsoup/ditto/releases/tag/v1.0.1).

## Using it

### From the app

Tap **Record** in the dock at the bottom of the screen. The dock opens into a recording sheet with a timer, a live waveform, and Pause, Done, and Cancel.

Tap **Done** and the sheet turns into **Take ready**:

- Play the take back.
- Pick a voice: Raw, Studio, Robot, Chipmunk, Titan, Lo-Fi, Echo, or Fast. The default comes from the Voice row on the home screen.
- Tap **Paste into _app_** to send it to the last chat app you used. Ditto copies the clip, opens that app, and, with **Automatic paste** on, pastes it into the focused field. For WhatsApp, Ditto hands the clip to WhatsApp's own share flow instead. If Ditto doesn't know your last app yet, the button just says **Copy**.
- **Share** opens Android's share sheet. **Discard** deletes the take.

### From the floating bubble

Turn on **Bubble** on the home screen and allow **Display over other apps**. Open a chat, focus the message field, and tap the bubble to record. Tap it again to stop. With **Automatic paste** on, Ditto pastes the clip right away, with no review step.

To show the bubble only in some apps, turn on **Only in chosen apps** in Settings and pick them. This needs the accessibility service, which tells Ditto which app is in front.

### Double-tap Volume Down

Turn on **Double-tap Volume Down** in Settings and enable Ditto's accessibility service. It's off by default.

Two presses within 450 ms start recording through the floating recorder. Do it again to stop. You'll feel one vibration on start and two on stop. Single presses still change the volume, and so can the handled second press on some devices.

### Quick Settings tile

Add Ditto's tile from the Quick Settings editor. It turns the floating bubble on and off; it doesn't record by itself.

## Library and settings

The **Library** tab lists every note grouped by Today, Yesterday, and date. Tap a note to Play, Copy, Share, or Delete it. Deleting shows an Undo, and the file is kept until the undo window closes. The same screen has **Keep history** (turn it off and notes are deleted after use) and **Delete all notes**.

**Settings** covers:

- **Pasting:** Automatic paste and the accessibility service.
- **Bubble:** chosen apps, size, shape (circle, squircle, pill), color, opacity, dim when idle, and snap to edge, with a live preview.
- **Triggers:** Double-tap Volume Down and the Quick Settings tile.
- **Format:** M4A (AAC) or WAV.

The app follows your system's light or dark theme.

## Permissions and privacy

| Permission | Why Ditto asks for it |
|---|---|
| Microphone (`RECORD_AUDIO`) | Recording. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | The floating bubble. |
| Accessibility service | Knowing which app is in front (for chosen apps and "Paste into"), finding the focused text field to request Android's paste action, and catching the Volume Down double-tap. |
| Installed apps (`QUERY_ALL_PACKAGES`) | Listing your apps when you choose where the bubble appears. Declared in the manifest; Android doesn't prompt for it. |
| Notifications (`POST_NOTIFICATIONS`, Android 13+) | The floating recorder's foreground-service notification. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`) | Keeping the floating recorder and microphone running. Declared in the manifest. |
| Vibration (`VIBRATE`) | Start and stop feedback. |

The accessibility service can read window content and filter key events. Ditto's code only uses that to read package names, find the focused editable field, and watch for Volume Down. It doesn't log what you type, but the access Android grants is broader than what Ditto uses, so it's worth knowing.

Ditto never uploads audio. Copying puts the clip's URI on the clipboard and lets the receiving app read that one file; sharing does the same. Once you send a clip, the chat app's own rules apply. Android backup is enabled, so your system backup may include Ditto's data.

## Compatibility

Ditto needs Android 7.0 (API 24) or newer. Pasting depends on the chat app, the Android version, and the device, and there's no tested list of apps yet. If clipboard paste doesn't work somewhere, use **Share**.

If something breaks, [open an issue](https://github.com/walsoup/ditto/issues) and include:

- Your Android version and phone model.
- The chat app and its version.
- How you recorded (app, bubble, or Volume Down) and how you sent it (Paste, Copy, or Share).
- What you expected and what happened.

Please leave out private audio and chat contents.

## Ditto for Linux (in progress)

The [`desktop`](https://github.com/walsoup/ditto/tree/desktop) branch has a GNOME version. Press F4 anywhere to start a note, press it again to stop, then Ctrl+V to paste the file into a chat. It uses GTK 4 and libadwaita, and the shortcut can be changed in the app. The shared logic lives in a separate crate so Windows and macOS versions can reuse it later.

It hasn't had a full build or test yet. See [`desktop/README.md`](https://github.com/walsoup/ditto/blob/desktop/desktop/README.md) on that branch for setup.

## Build it yourself

You need JDK 21 and the Android SDK with platform 36.

```bash
git clone https://github.com/walsoup/ditto.git
cd ditto
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. To sign with your own key, copy `.env.example` to `.env` and fill it in, or set the same values as environment variables. Without a keystore the release build falls back to debug signing. Keep keys and passwords out of git.

## License

MIT, by walsoup. See [LICENSE](LICENSE).
