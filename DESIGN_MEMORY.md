# Design Memory

## Brand Tone
- **Adjectives:** premium, refined, calm.
- **Avoid:** card-on-card layouts, default Material template look, serif type, purple, gradients, glow.

## Layout & Spacing
- **Structure:** flat editorial layout. Sections separated by 1dp hairline rules, never boxed in cards.
- **Margins:** 24dp screen side margin; rhythm 4 / 8 / 12 / 16 / 24.
- **Corner radius:** dock 32, sheet top 28, big buttons 23 (46 tall), pills 10, keycap 7.
- **Elevation:** none on content. Sheets sit on a solid scrim.

## Typography
- **Headings:** system sans SemiBold with negative tracking. Wordmark "Ditto." at 44sp, with the period in `primary`.
- **Body:** system sans 15sp; secondary 14sp in `textSecondary`.
- **Numbers:** always monospace (timers, durations, 01/02/03 track numbers).

## Color
- **Primary:** forest sage `#2D6A4F` (dark theme `#7DBB9B`).
- **Recording/stop:** terracotta `#D47255` (dark theme `#E08A6E`).
- **Neutral strategy:** linen `#FAF9F6` background, white surfaces, sage containers, hairline `#E0E6E2`.
- **Semantic:** terracotta for recording and setup warnings; no separate error red.

## Interaction Patterns
- **Recording:** the bottom dock expands into a sheet (big mono timer, live waveform, Pause / Done / Cancel), then becomes a "Take ready" review with voice pills and a primary "Paste into {app}" button.
- **Navigation:** underlined text tabs (Record, Library, Settings), not an icon nav bar.
- **Lists:** numbered tracklists; tapping a row expands its actions inline.
- **Feedback:** snackbar with Undo on Android; on Linux a GNOME notification (doesn't steal focus).
- **Linux shortcut:** one key toggles recording (default F4), set up as a GNOME custom keybinding; the floating pill appears while recording.

## Accessibility Rules
- **Targets:** 44dp minimum.
- **Labels:** content descriptions on every icon-only button.
- **Motion:** 150–300ms; ease-out entering, ease-in leaving; honor reduced motion.

## Repo Conventions
- **Android:** Jetpack Compose. Tokens in `theme/`, shared primitives in `ui/design/`.
- **Linux:** Rust + gtk4-rs + libadwaita in `linux/`, tokens mirrored in `linux/src/style.css`.
- **Identity:** walsoup, MIT, app ID `com.walsoup.ditto`.
