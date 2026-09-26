# Workspace Guidelines & Project Rules

## 1. Developer Identity & Licensing
- **Developer Name**: `walsoup`
- **Default License**: MIT License
- All "About", "Legal", Settings screens, or open source metadata must attribute `walsoup` and use the MIT License.
- **Package & Application ID**: Always use `com.walsoup.<appname_lowercase>` (never use boilerplate `com.example.*`).
- **FileProvider Authority**: Use `${applicationId}.provider` in `AndroidManifest.xml` and `"${context.packageName}.provider"` in Kotlin.

## 2. UI/UX Design System (Pastel Material 3)
- **Palette**: Flat Pastel Material 3 using the 60-30-10 rule:
  - 60% Base: Warm linen cream (`#FAF9F6`) and clean white card backgrounds (`#FFFFFF`).
  - 30% Structure: Soft sage containers (`#E8F1EC`), hairline borders (`#E0E6E2`), slate text (`#1F2421`).
  - 10% Accent: Deep forest sage (`#2D6A4F`) for primary actions, warm terracotta (`#D47255`) for recording/stop states.
- **Strict Visual Invariants**:
  - **NO PURPLE**: Do not use neon purple or violet accents.
  - **NO GRADIENTS**: Use solid flat surfaces and subtle Material 3 tonal elevations.
  - **NO GLOW/NEON EFFECTS**: Keep visual complexity low and clean.

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
  - Launch main activity with `adb shell am start -n <applicationId>/.MainActivity`.
