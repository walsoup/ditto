# Linen design system

This folder is a drop-in Android library (`com.walsoup.linen`) holding walsoup's design system for Jetpack Compose: tokens, a theme and components. Every screen in a host app is built from it. Values live in code (`src/main/java/com/walsoup/linen/`) and in `tokens.json` for non-Compose ports.

## Wiring it into a project

Run these once, when `design/` has just been copied into a project that doesn't use it yet.

1. In `settings.gradle.kts`, add `include(":design")`.
2. In the root `build.gradle.kts`, make sure the Android application plugin and the Compose compiler plugin are declared with `apply false`. `design/` applies `com.android.library` and `org.jetbrains.kotlin.plugin.compose` without versions and resolves them from there. On AGP 8 (no built-in Kotlin), also add `id("org.jetbrains.kotlin.android")` to `design/build.gradle.kts`.
3. In `app/build.gradle.kts`, add `implementation(project(":design"))`.
4. Wrap the root composable (in `setContent`) in `LinenTheme { … }`, and delete the app's own `Theme.kt`, `Color.kt` and `Type.kt` once nothing references them.
5. Add this line to the host project's `AGENTS.md` so later sessions find this guide: `UI: build every screen with the Linen design system; follow design/AGENTS.md.`

Done when `./gradlew :app:compileDebugKotlin` passes and no file under `app/` defines its own color scheme or hex colors for UI.

## Building screens

- Read colors from `Linen.colors`, type from `Linen.type`, spacing and radii from `LinenDimens`. Hex values and raw `dp` radii in screen code mean a token is missing: add it to Linen instead.
- `LinenPalette` holds raw light-mode colors for code that runs outside a `LinenTheme` (notifications, Views, window overlays).
- For a new screen, start from the closest reference in `src/debug/java/com/walsoup/linen/examples/Examples.kt` (list screen, settings, review sheet, controls).
- Reach for a Linen component first, then a stock Material 3 component (it's already themed by `LinenTheme`), and only then a custom one.
- Change the system here, not in an app's screens. When a token changes, update `tokens.json` and the tables in `README.md` in the same edit.

### The look

The look is **flat** and **calm**: solid surfaces, 1dp hairlines between sections, tonal fills, one accent per screen.

- Color follows 60-30-10: about 60% `bg`/`surface`, 30% `container`, `hairline` and `text`, 10% `primary` or `rec`.
- Separate sections with `Hairline()` and whitespace rather than cards or shadows. Use a card only for something that floats (a dialog, a bubble, a sheet).
- All numbers that change or line up (timers, durations, counts, list indexes) use the `mono*` styles.
- Buttons, pills and tabs are single-line: `maxLines = 1` with explicit `contentPadding`.
- Recording uses `RecordDock`, the horizontal pill capsule in the bottom thumb zone with a timer, a level meter and a 44dp button.
- Every screen works in light and dark; check both.
- Hard guardrails (the owner's rules): no purple or violet, no gradients, no glow or neon effects, no large circular record buttons.

### Color roles

| Role | Use for |
|---|---|
| `bg` | Screen background |
| `surface` | Docks, dialogs, sheets, anything raised |
| `container` | Tonal buttons, selected pills, icon button fills |
| `containerLow` | Disabled fills, subtle wells |
| `hairline` | 1dp dividers and borders |
| `text` / `textSecondary` | Primary text / subtitles and values |
| `muted` | List indexes, chevrons, hints |
| `primary` / `onPrimary` | The one main action, selection, links |
| `rec` / `recSoft` | Live, recording, stop, destructive, needs-attention; `recSoft` is the fill behind `rec` content |
| `inactive` | Idle meters, unchecked switch tracks, unplayed waveform bars |
| `scrim` | Behind dialogs and sheets |

### Which component

| Need | Use |
|---|---|
| Top of a main screen | `PageHeader(overline, title, lede?)` (overline is usually today's date) |
| Section heading / label above a group of rows | `SectionTitle` / `GroupLabel` |
| Divider | `Hairline()` |
| Settings row with a value or navigation | `SettingLine(label, value?, subtitle?, emphasis, warning, onClick?)` |
| Settings toggle | `SwitchLine(label, checked, onCheckedChange, subtitle?)` |
| List of items with a number and a duration | `TrackRow(index, title, subtitle, durationMs, highlighted)` |
| Bottom navigation | `TextTabs(tabs, selected, label, onSelect)` |
| Buttons | `ActionButton(text, onClick, style)`: `Primary` for the main action, `Tonal` for others, `Ghost` for dismiss, `Record` for live or stop |
| Icon-only button | `RoundIconButton(icon, contentDescription, onClick)` with a real description |
| Pick one of a few options | `Pill(text, selected, onClick)` in a row |
| Recording controls | `RecordDock(elapsedMs = { ms }, level = { lvl }, isRecording, onRecord, label, icon)`; pass lambdas so only the timer and meter redraw |
| Audio level / waveform | `LevelMeter(level, active)` / `Waveform(levels, progress, alignEnd)` |
| Nothing to show | `EmptyState(text)`, one line saying what's missing and how to fill it |
| Clock text | `formatClock(ms)` gives `mm:ss` |

Screen padding is `ScreenMargin` (24dp) on both sides; rows run edge to edge inside it.
