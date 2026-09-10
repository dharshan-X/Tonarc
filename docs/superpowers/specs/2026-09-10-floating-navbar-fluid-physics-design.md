# Floating Navigation Bar Fluid Physics Animation Design

**Date:** 2026-09-10  
**Feature:** Fluid Physics & Liquid Stretch/Snap Animations for `FloatingPillNavigationBar`  
**Status:** Approved

---

## 1. Overview & Objective

Enhance the modern floating capsule navigation bar (`FloatingPillNavigationBar`) in Tonarc with organic fluid physics. The goal is to make tab transitions feel tactile, elastic, and delightful—emulating a physical liquid droplet that stretches in transit and snaps into place—while preserving strict 60–120 FPS UI performance with zero stutter.

---

## 2. Core Physics Mechanics

### 2.1 Dual-Edge Liquid Stretch & Squash (Conservation of Volume)
Rather than translating a static rigid rectangle, the active indicator behaves as an elastic liquid droplet with distinct leading (head) and trailing (tail) spring behaviors:

1. **Target Positions**:
   - For slot index $i \in \{0, 1, 2\}$, base center offset is:
     $$X_i = \text{FloatingPillHorizontalPadding} + i \times \text{FloatingPillSlotWidth} + \frac{\text{FloatingPillSlotWidth} - \text{FloatingPillIndicatorWidth}}{2}$$
   - Slots: Home ($X_0 = 9\,\text{dp}$), Search ($X_1 = 81\,\text{dp}$), Library ($X_2 = 153\,\text{dp}$).

2. **Dual-Edge Dynamics**:
   - `headOffset`: Animates toward $X_{\text{target}}$ with responsive, fast spring physics:
     - `stiffness = Spring.StiffnessMedium` ($\approx 1500\text{f}$)
     - `dampingRatio = Spring.DampingRatioNoBouncy` ($1.0\text{f}$)
   - `tailOffset`: Animates toward $X_{\text{target}}$ with delayed trailing inertia:
     - `stiffness = Spring.StiffnessMediumLow` ($\approx 800\text{f}$)
     - `dampingRatio = Spring.DampingRatioLowBouncy` ($0.75\text{f}$)

3. **Elastic Stretch Calculation**:
   - The instantaneous center translation is:
     $$\text{translationX} = \frac{\text{headOffset} + \text{tailOffset}}{2}$$
   - Dynamic stretch distance:
     $$\Delta X = |\text{headOffset} - \text{tailOffset}|$$
   - Normalized stretch ratio:
     $$S_x = 1.0 + \frac{\Delta X}{\text{FloatingPillIndicatorWidth}} \times 0.45$$
     *(Capped at $1.35\times$ to avoid overflowing adjacent touch slots).*
   - Vertical squash (volume conservation):
     $$S_y = 1.0 - (S_x - 1.0) \times 0.35$$
     *(Squashes slightly down to $\approx 0.88\text{--}0.92\times$ during maximum elongation, snapping back to $1.0\times$ as head and tail align).*

### 2.2 Icon Spring Pop Animation
When a tab destination becomes active (`targetIndex == index`):
- The icon scale interpolates with a spring pop:
  - From $0.85\times$ to $1.12\times$ overshoot, settling smoothly into $1.0\times$.
  - Spec: `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`.
- Departing icon:
  - Linearly returns to $1.0\times$ without bounce.
- Icon color tint:
  - Animate smoothly via `animateColorAsState(targetValue = tint, animationSpec = tween(180))`.

---

## 3. Technical Architecture & Zero-Stutter Guarantee

### 3.1 100% RenderNode / Layer Execution
To guarantee zero dropped frames on high-refresh-rate displays:
- All transform properties (`translationX`, `scaleX`, `scaleY`, `transformOrigin`) must reside within:
  ```kotlin
  Modifier.graphicsLayer {
      translationX = centerOffset.toPx()
      scaleX = stretchScaleX
      scaleY = squashScaleY
  }
  ```
- No animation values may trigger Compose layout remeasurement passes or recomposition of `FloatingPillNavigationBar` children.
- Icon scale pops are applied via:
  ```kotlin
  Modifier.graphicsLayer {
      scaleX = iconScale
      scaleY = iconScale
  }
  ```

### 3.2 State Synchronization & Gesture Resilience
- `targetIndex` updates instantaneously on user tap for zero perceived touch latency.
- `LaunchedEffect(routeIndex)` synchronizes `targetIndex` with external route updates (e.g. system back gesture).
- Taps during an ongoing transit safely redirect the spring targets without glitching or snapping.
- Double-tap search remains preserved with proper debounce and timing.

---

## 4. Testing & Verification Plan

1. **Unit Testing**:
   - Update `FloatingPillNavigationBarTest.kt` to verify that offset and scale calculations for all slot indices ($0, 1, 2$) resolve within valid geometric bounds.
   - Verify that when `headOffset == tailOffset`, stretch ratio $S_x = 1.0$ and squash ratio $S_y = 1.0$.
2. **Build Verification**:
   - Run `./gradlew :app:testDebugUnitTest --no-daemon` to confirm compilation and test pass.
   - Run `./gradlew assembleDebug --no-daemon` to generate a verified APK.
3. **Manual Verification**:
   - Switching Home $\leftrightarrow$ Search $\leftrightarrow$ Library exhibits smooth liquid stretching and snapping.
   - Rapid double-tap and multi-tab tapping behaves deterministically without visual artifacts.
