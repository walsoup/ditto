# Design Implementation Plan: Ditto (Android redesign + Ditto for Linux)

## Summary
- **Scope:** app-wide redesign of the Android app, plus a new Linux desktop app in the same design language.
- **Winner:** Variant F, which combines Variant E's flat editorial layout (set in sans-serif) with Variant D's record, review and paste flow.
- **Fixes:** the generic Material-template look, inconsistent spacing/radii/type, and unclear hierarchy.
- **Fixed constraints (AGENTS.md):** sage/terracotta palette, thumb-zone pill dock (mono timer, level meter, 44dp pill button), no purple, no gradients, no glow.

## Design tokens

### Color
| Token | Light | Dark |
|---|---|---|
| `bg` | `#FAF9F6` | `#121513` |
| `surface` | `#FFFFFF` | `#1A1E1B` |
| `container` | `#E8F1EC` | `#22302A` |
| `containerLow` | `#F1F5F2` | `#1D2520` |
| `hairline` | `#E0E6E2` | `#2A322D` |
| `text` | `#1F2421` | `#E8ECE9` |
| `textSecondary` | `#526058` | `#A3B0A8` |
| `muted` | `#8A9A90` | `#6F7D75` |
| `primary` | `#2D6A4F` | `#7DBB9B` |
| `onPrimary` | `#FFFFFF` | `#0F1A14` |
| `rec` | `#D47255` | `#E08A6E` |
| `recSoft` | `#FBF0EC` | `#33241F` |
| `inactive` | `#D0D7D3` | `#3A433E` |
| `scrim` | `#1F2421` @ 38% | `#000000` @ 50% |

### Type (sans for all text, mono for every number)
| Token | Spec | Used for |
|---|---|---|
| `display` | 44sp SemiBold, tracking -2sp, line 44sp | "Ditto." wordmark |
| `title` | 19sp SemiBold, tracking -0.4sp | Section titles ("Recent", "Take ready") |
| `body` | 15sp Normal | Rows, labels |
| `bodySecondary` | 14sp Normal, `textSecondary` | Lede, row values |
| `caption` | 12sp Normal, `textSecondary` | Track subtitles |
| `overline` | 12sp Medium, uppercase, tracking +0.5sp | Date line |
| `monoHero` | 54sp SemiBold mono | Recording sheet timer |
| `monoDock` | 17sp SemiBold mono | Dock timer |
| `monoSmall` | 13sp / 12sp mono | Durations / track numbers |

Fonts: system sans (Roboto on Android, Inter/Cantarell on GNOME) and system monospace. No bundled fonts, so the APK stays small.

### Shape and spacing
- Screen side margin 24dp; vertical rhythm 4 / 8 / 12 / 16 / 24dp.
- No cards. Sections are separated by 1dp `hairline` rules.
- Radii: dock 32dp, sheet top 28dp, big action buttons 23dp (46dp tall), voice pills 10dp, keycap 7dp.
- Elevation: none on content; the sheet sits on a solid scrim.

## Android: files to change

- [ ] `theme/Color.kt`: replace with light/dark token sets above.
- [ ] `theme/Theme.kt`: real dark scheme plus a `DittoColors` CompositionLocal for `rec`, `recSoft`, `inactive`, `muted`, `containerLow`, `hairline`.
- [ ] `theme/Type.kt`: the type scale above, plus `DittoType` for the mono styles.
- [ ] `ui/design/` (new): `Hairline`, `SettingLine`, `TrackRow`, `RecordDock`, `TextTabs`, `VoicePills`, `Waveform`, `KeyValueSwitchLine`.
- [ ] `ui/RecordFlow.kt` (new): the recording sheet and the "Take ready" review sheet (scrim, slide-up 250ms ease-out, slide-down 200ms ease-in).
- [ ] `MainActivity.kt`: slim down to app shell plus `HomeScreen`; drop the `Sage*` aliases.
- [ ] `ui/HistoryScreen.kt` → `ui/LibraryScreen.kt`: tracklist grouped by day; tapping a row expands Play / Paste / Share / Delete inline; snackbar undo stays.
- [ ] `ui/SettingsScreen.kt`: rebuild as hairline sections (Paste, Bubble, Triggers, Format, About).
- [ ] `ui/AppSelectionDialog.kt`: restyle with tokens.
- [ ] `service/FloatingBubbleService.kt`: bubble colors from tokens; behavior unchanged (stopping still auto-pastes).
- [ ] `core/audio/AudioModel.kt`: add `targetPackage: String?` to `HistoryItem`; add short names for voice pills.
- [ ] `core/audio/AudioRecorderEngine.kt`: keep a rolling amplitude history for the live and review waveforms.
- [ ] `data/HistoryManager.kt`: persist `targetPackage` and `defaultFilter`.

## Android: screens
1. **Home (Record tab):** date overline, "Ditto." in `display`, a one-line lede, then hairline rows: a terracotta "Finish setup · 3/4" row (only while permissions are missing), Bubble, Voice, Format. Below them, "Recent" with the top 3 tracks, then the dock and the text tabs.
2. **Recording sheet:** the dock expands into a sheet with a "RECORDING" kicker, `monoHero` timer, live waveform, and Pause / Done (terracotta) / Cancel.
3. **Take ready:** the same sheet changes to playback, voice pills (preselected to the default voice), the primary "Paste into {last app}" button, and Share / Discard. Paste copies the clip, opens the app, and auto-pastes through the accessibility service; if there is no last app the button says "Copy".
4. **Library tab:** all takes grouped Today / Yesterday / date, plus the history on/off row.
5. **Settings tab:** hairline sections, the bubble appearance editor, triggers, and About (walsoup, MIT).

## Linux: Ditto for Linux (`linux/`)
- **Stack:** Rust, `gtk4` 0.9 and `libadwaita` 0.7 (GTK 4.14, libadwaita 1.5 on Zorin 18). App ID `com.walsoup.ditto`.
- **One-time build dependencies:** `sudo apt install libgtk-4-dev libadwaita-1-dev pkg-config`.
- **Single instance:** `ditto` opens the window; `ditto --toggle` asks the running instance to start or stop (starting it in the background if needed).
- **Shortcut:** on first run and on change, Ditto writes a GNOME custom keybinding (`<F4>` → `ditto --toggle`) with `gsettings`. User-level, no sudo.
- **Recording:** `pw-record --rate 48000 --channels 1 --format s16 -` piped into the app (RMS drives the meter), then `ffmpeg` encodes to OGG Opus (default), M4A or WAV and applies voice filters (Studio, Titan, Lo-Fi via `-af`).
- **Copy:** the file goes on the clipboard as a file (`text/uri-list` and `x-special/gnome-copied-files`) through GTK, with `wl-copy` as a fallback. The user presses Ctrl+V.
- **Main window:** two columns with a vertical hairline: left has date, "Ditto.", lede, and an `F4` keycap row; right has the "Recent" tracklist. The dock spans the bottom.
- **Floating pill:** a small undecorated window while recording (timer, meter, "F4 to stop", Done). Mutter decides where it appears and gives it focus; on stop it closes, so focus returns to the chat.
- **"Copied" toast:** a GNOME notification ("Copied · press Ctrl+V to send"). Notifications don't steal focus; a window would.
- **Style:** `linux/src/style.css` holds the same tokens; follows the system light/dark setting through `AdwStyleManager`.

## Required UI states
- **Empty:** Recent shows "No notes yet. Tap Record or press F4." in `bodySecondary`.
- **Missing permissions:** a terracotta setup row on Home opens a setup list.
- **Mic busy:** a snackbar (Android) or notification (Linux) says "Microphone unavailable".
- **Paste failed:** the clip stays copied and a snackbar says "Copied. Long-press the chat field to paste".
- **Disabled:** buttons use `containerLow` with `muted` content.

## Accessibility
- [ ] Touch targets ≥ 44dp (dock button 44dp tall, rows ≥ 52dp).
- [ ] Every icon-only button has a content description.
- [ ] Text contrast ≥ 4.5:1 in both themes (primary on bg: 6.6:1 light, 8.3:1 dark).
- [ ] Respect the system animator duration scale for the sheet transitions.
- [ ] GTK: every action reachable by keyboard; Esc closes the floating pill (same as Done).

## Testing
- [ ] Unit: filter mapping, history JSON migration (old items without `targetPackage`), Linux toggle state machine.
- [ ] Manual on device: record from the dock → review → paste into WhatsApp; bubble flow still auto-pastes; light and dark.
- [ ] Manual on GNOME: F4 toggles from any app; Ctrl+V pastes the file into Telegram Desktop, Discord and WhatsApp Web (Chrome).

## Build order
1. Tokens and theme (light and dark) plus `ui/design/` components.
2. Home, recording sheet, review sheet.
3. Library and Settings restyle; bubble colors.
4. Linux crate: window and style, recorder, clipboard, toggle and shortcut, floating pill, notification.
