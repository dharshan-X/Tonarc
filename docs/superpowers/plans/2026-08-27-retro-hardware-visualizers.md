# Retro Hardware Visualizers (Vintage Cassette & Dual Analog VU Meters) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement two optional, hardware-accelerated retro audio visualizer skins (Vintage Cassette Deck with tape dynamics and Dual Analog VU Meters with ballistic needles) for Tonarc's Now Playing and Visualizer Bottom Sheet.

**Architecture:** Extend `VisualizerModels.kt` with new `VisualizerMode` entries and dual-channel ballistic frame data in `AudioVisualizerEngine.kt`. Render pure vector graphics with Compose `DrawScope` in `AudioVisualizerView.kt` with zero runtime allocations, and expose interactive controls and previews in `VisualizerBottomSheet.kt`.

**Tech Stack:** Jetpack Compose, Material 3 Expressive, AndroidX Media3, Kotlin Coroutines, StateFlow, JUnit Jupiter.

## Global Constraints
- All Gradle commands must append `--no-daemon`.
- Zero per-frame object allocations in draw loops (reuse precomputed vector paths and math).
- Maintain 100% test pass rate with `./gradlew :app:testDebugUnitTest --no-daemon`.
- Ensure all file references use `file://` scheme markdown links.

---

### Task 1: Visualizer Models & Physics Engine Extension

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/visualizer/VisualizerModels.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/visualizer/AudioVisualizerEngine.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/visualizer/AudioVisualizerEngineTest.kt`

**Interfaces:**
- Produces:
  - `VisualizerMode.VINTAGE_CASSETTE` and `VisualizerMode.ANALOG_VU_METERS`
  - `VisualizerFrameData` with `leftNeedleAngle: Float`, `rightNeedleAngle: Float`, `leftPeak: Boolean`, `rightPeak: Boolean`, `tapeProgress: Float`, `spoolRotationAngle: Float`
  - `AudioVisualizerEngine.computeFrame(...)` with needle ballistics and tape calculations

- [ ] **Step 1: Write unit tests in `AudioVisualizerEngineTest.kt`**
  - Test needle deflection within $[-45^\circ, +45^\circ]$ bounds.
  - Test peak overload detection when energy exceeds $85\%$.
  - Test tape progress clamped between $0.0\text{f}$ and $1.0\text{f}$.

- [ ] **Step 2: Run test to confirm failure**
  - Run `./gradlew :app:testDebugUnitTest --tests "*.AudioVisualizerEngineTest" --no-daemon`.

- [ ] **Step 3: Update `VisualizerModels.kt`**
  - Add `VINTAGE_CASSETTE` and `ANALOG_VU_METERS` to `VisualizerMode`.
  - Add needle angles, peaks, and tape progress fields to `VisualizerFrameData`.

- [ ] **Step 4: Update `AudioVisualizerEngine.kt`**
  - Implement dual-channel ballistic attack/decay for Left (bands $0..15$) and Right (bands $16..31$).
  - Calculate `tapeProgress = (currentPositionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)`.
  - Calculate `spoolRotationAngle`.

- [ ] **Step 5: Run tests and verify passing**
  - Run `./gradlew :app:testDebugUnitTest --tests "*.AudioVisualizerEngineTest" --no-daemon`.

- [ ] **Step 6: Commit changes**
  - `git commit -m "feat(visualizer): extend models and physics engine with ballistic needles and tape dynamics"`

---

### Task 2: Vintage Cassette Deck Vector Rendering & UI Integration

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/visualizer/AudioVisualizerView.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/visualizer/VisualizerBottomSheet.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/player/FullPlayerContent.kt`

**Interfaces:**
- Consumes: `VisualizerFrameData.tapeProgress`, `spoolRotationAngle`, `VisualizerMode.VINTAGE_CASSETTE`
- Produces: `drawVintageCassette(...)` in `AudioVisualizerView.kt`

- [ ] **Step 1: Implement `drawVintageCassette` in `AudioVisualizerView.kt`**
  - Render acrylic cassette shell with rounded corners and 4 corner screws.
  - Render lower trapezoidal tape-head bridge with bronze head element.
  - Render dual tape supply and take-up circles with dynamic radii $R_{\text{supply}}$ and $R_{\text{takeup}}$.
  - Render spinning 6-tooth white hub gears.
  - Render cassette label with theme accent styling, Side A badge, and track title/artist text.
  - Render transparent center inspection window with calibration ticks.

- [ ] **Step 2: Add interactive gestures in player**
  - Add tap-to-click mechanical tape head animation.
  - Add double tap seek $\pm 10\text{s}$ with spool spin acceleration.

- [ ] **Step 3: Update `VisualizerBottomSheet.kt`**
  - Add `VINTAGE_CASSETTE` preview card in visualizer selector list.

- [ ] **Step 4: Run unit tests and assemble debug APK**
  - Run `./gradlew :app:testDebugUnitTest --no-daemon`.
  - Run `./gradlew assembleDebug --no-daemon`.

- [ ] **Step 5: Commit changes**
  - `git commit -m "feat(visualizer): implement vintage cassette deck vector rendering and interactive controls"`

---

### Task 3: Dual Analog VU Meters Vector Rendering & Verification

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/visualizer/AudioVisualizerView.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/visualizer/VisualizerBottomSheet.kt`

**Interfaces:**
- Consumes: `VisualizerFrameData.leftNeedleAngle`, `rightNeedleAngle`, `leftPeak`, `rightPeak`, `VisualizerMode.ANALOG_VU_METERS`
- Produces: `drawDualVuMeters(...)` in `AudioVisualizerView.kt`

- [ ] **Step 1: Implement `drawDualVuMeters` in `AudioVisualizerView.kt`**
  - Render twin gauge enclosure with brushed metallic finish.
  - Render warm incandescent backlit dial face with radial calibration marks ($-20\text{dB}$ to $+3\text{dB}$).
  - Render ballistic black needles pivoting from the bottom center of each gauge.
  - Render red PEAK overload LEDs with dynamic glow aura.
  - Render convex glass cover reflection gradient.

- [ ] **Step 2: Add interactive sensitivity toggle**
  - Support tapping the meter face to toggle between standard $0\text{dB}$ and $+6\text{dB}$ high-sensitivity modes.

- [ ] **Step 3: Update `VisualizerBottomSheet.kt`**
  - Add `ANALOG_VU_METERS` preview card in the visualizer mode selector.

- [ ] **Step 4: Full verification and build**
  - Run `./gradlew :app:testDebugUnitTest --no-daemon`.
  - Run `./gradlew assembleDebug --no-daemon`.

- [ ] **Step 5: Commit and push**
  - `git commit -m "feat(visualizer): implement dual analog VU meters with ballistic needles and peak LEDs"`
  - `git push tonarc main`
