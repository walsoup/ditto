# Linen

The design system behind Ditto, as a folder you copy into any Android project. It's Jetpack Compose on top of Material 3: Linen sets the colors, type and shapes, and adds the components Material doesn't have (setting rows, numbered track rows, text tabs, the recording dock, waveforms).

Package `com.walsoup.linen`, MIT license.

## The rules

These matter more than the code. If a screen breaks one, it isn't Linen.

1. **60-30-10 color.** About 60% linen and white surfaces, 30% sage containers, hairlines and slate text, 10% accent. Forest green marks the one main action on a screen. Terracotta means live, recording, stop or "needs attention", and nothing else.
2. **Flat.** No purple or violet, no gradients, no glow or neon, no drop shadows used as decoration. Separate sections with 1dp hairlines, not cards.
3. **Numbers are monospace.** Timers, durations, counts and list indexes use the `mono*` styles so they don't jitter as they change. Words use the system sans.
4. **Thumb zone first.** The main action sits at the bottom. Recording uses the `RecordDock` pill (dot and timer, level meter, 44dp button), never a big round record button.
5. **Buttons don't wrap.** Every button and pill label is one line, with explicit padding.
6. **Light and dark are both first-class.** Read colors from `Linen.colors`, never hard-code hex values in screens.

## Using it in a project

1. Put Linen in the root of the project as `design/`, either by copying the folder or with `git clone https://github.com/walsoup/linen design && rm -rf design/.git`.
2. Tell your agent: "set up design/ by following design/AGENTS.md".

The agent wires the module into Gradle, wraps the app in `LinenTheme` and adds a pointer to the project's `AGENTS.md`, so later sessions build screens from Linen without being told again.

To do it by hand:

```kotlin
// settings.gradle.kts
include(":design")

// app/build.gradle.kts
dependencies {
    implementation(project(":design"))
}
```

Then wrap `setContent` in `LinenTheme { … }` and delete the app's old `Theme.kt`, `Color.kt` and `Type.kt`.

### Requirements

- An Android project that already uses Compose, with the Android application plugin and Compose compiler plugin declared (`apply false`) in the root `build.gradle.kts`. `design/` borrows both from there.
- AGP 9 (built-in Kotlin). On AGP 8, add `id("org.jetbrains.kotlin.android")` to `design/build.gradle.kts`.
- JDK 21, compileSdk 36, minSdk 24.

## Examples

[`src/debug/java/com/walsoup/linen/examples/Examples.kt`](src/debug/java/com/walsoup/linen/examples/Examples.kt) has four reference screens built only from Linen, each with light and dark previews:

| Example | Shows |
|---|---|
| `ListScreenExample` | Page header, grouped `TrackRow` list, `RecordDock` and `TextTabs` in the thumb zone |
| `SettingsScreenExample` | Sections of `SwitchLine` and `SettingLine`, a warning row, a `Pill` group |
| `ReviewSheetExample` | Bottom sheet with a `monoHero` timer, `Waveform` with progress, Ghost / Tonal / Primary buttons |
| `ControlsExample` | Every `ActionButton` style, a disabled button, `EmptyState` |

They live in the debug source set: they compile in debug builds, so they break loudly if the API changes, and they never ship in a release APK. Open the file in Android Studio to see the previews.

## Setting up a screen

```kotlin
setContent {
    LinenTheme {
        Column(Modifier.padding(horizontal = ScreenMargin)) {
            PageHeader(overline = "Tuesday 14 October", title = "Notes")
            SectionTitle("Recent")
            TrackRow(index = 1, title = "Groceries", subtitle = "Today, 09:12", durationMs = 42_000)
            SwitchLine("Keep history", checked = keep, onCheckedChange = { keep = it })
            SettingLine("Storage", value = "12 MB", onClick = { /* … */ })
        }
    }
}
```

`LinenTheme` also maps the tokens onto `MaterialTheme`, so stock Material components (dialogs, menus, sliders, snackbars) pick up the palette without extra styling.

Inside the theme, read tokens through `Linen`:

```kotlin
Text("Saved", style = Linen.type.caption, color = Linen.colors.textSecondary)
Box(Modifier.background(Linen.colors.container, RoundedCornerShape(LinenDimens.CardRadius)))
```

Outside Compose (notification colors, Views, overlays drawn before a theme exists), use `LinenPalette`, which holds the raw light-mode brand colors.

An app that needs its own accent can pass a modified color set. Keep the roles intact:

```kotlin
LinenTheme(lightColors = LightLinenColors.copy(primary = Color(0xFF3A6B8A))) { /* … */ }
```

## Tokens

The same values live in [`tokens.json`](tokens.json) for non-Android ports such as the GTK desktop app.

### Color roles

| Role | Light | Dark | Use for |
|---|---|---|---|
| `bg` | `#FAF9F6` | `#121513` | Screen background |
| `surface` | `#FFFFFF` | `#1A1E1B` | Docks, dialogs, raised areas |
| `container` | `#E8F1EC` | `#22302A` | Tonal buttons, selected pills |
| `containerLow` | `#F1F5F2` | `#1D2520` | Disabled fills, subtle wells |
| `hairline` | `#E0E6E2` | `#2A322D` | 1dp dividers and borders |
| `text` | `#1F2421` | `#E8ECE9` | Primary text |
| `textSecondary` | `#526058` | `#A3B0A8` | Subtitles, values |
| `muted` | `#8A9A90` | `#6F7D75` | Indexes, chevrons, hints |
| `primary` | `#2D6A4F` | `#7DBB9B` | The main action, selection |
| `onPrimary` | `#FFFFFF` | `#0F1A14` | Content on `primary` |
| `rec` | `#D47255` | `#E08A6E` | Recording, stop, warnings |
| `recSoft` | `#FBF0EC` | `#33241F` | Background behind `rec` content |
| `inactive` | `#D0D7D3` | `#3A433E` | Idle meters, unchecked switches |
| `scrim` | slate at 38% | black at 50% | Behind sheets and dialogs |

### Type

| Style | Size / line | Weight | Use for |
|---|---|---|---|
| `display` | 44 / 46, tracking -2 | SemiBold | Screen wordmark |
| `headline` | 34 / 38 | SemiBold | Large headings |
| `title` | 19 / 24 | SemiBold | Section titles |
| `body` | 15 / 21 | Regular | Row labels, prose |
| `bodySecondary` | 14 / 20 | Regular | Values, ledes |
| `caption` | 12 / 16 | Regular | Subtitles |
| `overline` | 12 / 16, +0.5, uppercase | Medium | Dates, group labels |
| `label` | 14 / 18 | SemiBold | Buttons, pills |
| `monoHero` | 54 / 60 | SemiBold | Big live timer |
| `monoDock` | 17 / 22 | SemiBold | Dock timer |
| `monoSmall` | 13 / 18 | Medium | Durations in lists |
| `monoTiny` | 12 / 16 | Medium | List indexes |

### Spacing and shape (`LinenDimens`)

| Token | Value |
|---|---|
| `ScreenMargin` | 24dp |
| `RowMinHeight` | 52dp |
| `TouchTarget` | 44dp |
| `ButtonHeight` / `ButtonRadius` | 46dp / 23dp |
| `PillHeight` / `PillRadius` | 36dp / 10dp |
| `DockHeight` / `DockRadius` | 64dp / 32dp |
| `CardRadius` | 12dp |
| `DialogRadius` | 24dp |
| `SheetRadius` | 28dp (top corners) |
| `KeycapRadius` | 7dp |

## Components

| Component | What it is |
|---|---|
| `PageHeader(overline, title, lede?)` | Overline, wordmark, optional lede and a closing hairline. The top of every main screen. |
| `Wordmark(text)` | Display title ending in a green period. |
| `SectionTitle`, `GroupLabel` | Section heading; small uppercase label above a group of rows. |
| `Hairline()` | The 1dp divider. |
| `SettingLine(label, value?, subtitle?, emphasis, warning, onClick?)` | Settings row with a value and chevron. `warning` turns it terracotta. |
| `SwitchLine(label, checked, onCheckedChange, subtitle?)` | Settings row with a switch; the whole row is the touch target. |
| `LinenSwitch` | The themed switch on its own. |
| `TrackRow(index, title, subtitle, durationMs, highlighted)` | Numbered list row with a monospace index and duration. |
| `TextTabs(tabs, selected, label, onSelect)` | Bottom navigation as words with an underline. |
| `ActionButton(text, onClick, style, icon?)` | 46dp pill. `Primary` for the one main action, `Tonal` for the rest, `Ghost` for dismiss, `Record` for live or stop. |
| `RoundIconButton(icon, contentDescription, onClick)` | 44dp tonal icon button. Always pass a real description. |
| `Pill(text, selected, onClick)` | Chip for picking one option from a short set. |
| `RecordDock(elapsedMs, level, isRecording, onRecord, label, icon?)` | Thumb-zone recording capsule. Pass lambdas so only the timer and meter redraw. |
| `LevelMeter(level, active)` | The dock's vertical bar meter, usable alone. |
| `Waveform(levels, progress, alignEnd)` | 3dp-bar waveform for playback or live input. |
| `EmptyState(text)` | One line saying what's missing and how to fill it. |
| `formatClock(ms)` | `mm:ss`. |

Wiring the record dock to a recorder that exposes `StateFlow`s (`Icons.Default.Mic` comes from `material-icons-extended`, which the app adds itself; Linen only ships the core icons):

```kotlin
val elapsed by recorder.elapsedMs.collectAsState()
val level by recorder.level.collectAsState()
RecordDock(
    elapsedMs = { elapsed },
    level = { level },
    isRecording = recording,
    onRecord = ::toggle,
    icon = Icons.Default.Mic,
)
```

## Files

| File | Contents |
|---|---|
| `Colors.kt` | `LinenColors` roles with light and dark sets, `LinenPalette` raw colors |
| `Type.kt` | `LinenType`: system sans for words, monospace for numbers |
| `Theme.kt` | `LinenTheme`, the `Linen.colors` / `Linen.type` accessors, `LinenDimens` |
| `Structure.kt` | Headers, section titles, hairlines, empty state |
| `Lines.kt` | Setting rows, switch rows, track rows |
| `Controls.kt` | Buttons, icon buttons, pills, text tabs |
| `Audio.kt` | Record dock, level meter, waveform, `formatClock` |
| `examples/Examples.kt` | Reference screens with light and dark previews (debug only) |
| `tokens.json` | The same values for non-Compose ports |
| `AGENTS.md` | The setup steps and rules your agent follows |

## Changing Linen

Each project gets its own copy, so copies drift. [walsoup/linen](https://github.com/walsoup/linen) is the original: change the system there, not in an app's screens, then copy it over the projects' `design/` folders (Ditto's included). When a token changes, update `tokens.json` in the same commit, and the GTK stylesheet on the `desktop` branch if it's affected.
