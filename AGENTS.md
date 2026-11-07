# Workspace Guidelines & Project Rules

## 1. Developer Identity & Licensing
- **Developer Name**: `walsoup`
- **Default License**: MIT License
- All "About", "Legal", Settings screens, or open source metadata must attribute `walsoup` and use the MIT License.
- **Package & Application ID**: Always use `com.walsoup.<appname_lowercase>` (never use boilerplate `com.example.*`).
- **FileProvider Authority**: Use `${applicationId}.provider` in `AndroidManifest.xml` and `"${context.packageName}.provider"` in Kotlin.

## 2. UI/UX Design System (Tumble / Shore sea glass)
- **UI**: build every screen with the Tumble / Shore design language; follow `linen-tumble/AGENTS.md`. Ditto's adoption layer lives in `app/.../ui/tumble/` (`DittoHomePalette`, `LocalTumbleStage`, `TumbleGround`).
- **Migration**: strangler pattern. `LinenTheme` stays outermost in `MainActivity` while pieces migrate to glass one by one. New glass goes through `ui/tumble/`; do not add new Linen components.
- **Tumble roles for Ditto**: home = Seafoam primary actions; Milk (day) / Obsidian (night) neutrals; Ember = recording/stop/destructive (replaces terracotta); Cobalt = info, Kelp = success, Amber = warning.
- **Strict Visual Invariants**:
  - **NO PURPLE**: never use the Sea Lavender palette.
  - **NO GRADIENTS**: flat solid grounds only (no gradient backdrops). Kit-internal glass frost shading is the material, not a gradient.
  - **NO GLOW/NEON EFFECTS**: never use Citrine glow.
  - **GLASS IS SCARCE**: ~12 pieces per view, max 2 deep. Text, rows and dividers stay etched on Linen.

## 3. Ergonomics & Audio Recording Controls
- **NO Large Circular Record Buttons**: Do not generate oversized circular FABs for recording.
- **Thumb-Zone Recording Dock**: Use an ergonomic horizontal pill capsule placed in the bottom thumb zone containing:
  1. Monospace digital elapsed timer (`00:00`).
  2. Vertical real-time amplitude/level meter.
  3. Compact pill button (height 44dp) with explicit `contentPadding` and `maxLines = 1` to prevent text wrapping.

## 4. Communication Style (/i-have-adhd)
- **Action First**: Lead directly with the immediate next action.
- **Structured**: Use numbered task lists with fewer than 5 items.
- **Time Estimates**: Provide specific time estimates for each task (e.g., `(~1 minute)`).
- **Zero Filler**: No preamble, conversational pleasantries, or recaps.

## 5. Build Environment & Hardware Deployment
- **Java Home**: Android Gradle builds require JDK 21 at `~/.local/jdk-21`.
  - Always export:
    ```bash
    export JAVA_HOME="$HOME/.local/jdk-21"
    export PATH="$JAVA_HOME/bin:$PATH"
    ```
- **Device Deployment**:
  - Detect attached devices via `adb devices`.
  - Install built debug APKs with `adb install -r -d <apk-path>`.
  - Launch main activity with `adb shell am start -n <applicationId>/<namespace>.MainActivity` (debug id is `com.walsoup.ditto.debug`; the `.MainActivity` shorthand does not resolve with the debug suffix).
