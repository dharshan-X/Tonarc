# Design Specification: Retro Hardware Visualizers (Vintage Cassette & Dual Analog VU Meters)

## 1. Overview
This specification details the architecture, vector geometry, ballistic physics, and touch interactions for two new optional retro hardware visualizer skins in **Tonarc**:
1. **Vintage Cassette Deck (`VINTAGE_CASSETTE`)**: Animated dual-spool cassette with magnetic tape volume dynamics, rotating 6-tooth hubs, realistic acrylic shell, and handwritten metadata label.
2. **Dual Analog VU Meters (`ANALOG_VU_METERS`)**: Twin illuminated analog needle gauges with ballistic physics (fast attack, damped exponential decay), dB scale markings, red peak overload LEDs, and convex glass reflections.

Both modes integrate seamlessly into the existing hardware-accelerated Compose `AudioVisualizerView` and `VisualizerBottomSheet`, supporting instant toggling and Material You dynamic theming.

---

## 2. Architectural & Data Model Changes

### 2.1 Visualizer Models (`VisualizerModels.kt`)
Extend `VisualizerMode` enum:
```kotlin
@Immutable
enum class VisualizerMode(val storageKey: String, val displayName: String, val description: String) {
    SPECTRUM_BARS("spectrum_bars", "Spectrum Bars", "32-band dynamic equalizer bars with peak decay"),
    FLUID_WAVE("fluid_wave", "Fluid Wave", "Organic multi-layer fluid sine wave oscillating to rhythm"),
    CIRCULAR_PULSE("circular_pulse", "Circular Pulse", "Radial aura pulsing around album art with bass energy"),
    VINYL_TURNTABLE("vinyl_turntable", "Vinyl Turntable", "Authentic spinning vinyl record with tonearm"),
    VINTAGE_CASSETTE("vintage_cassette", "Vintage Cassette", "Retro dual-spool cassette with magnetic tape physics"),
    ANALOG_VU_METERS("analog_vu_meters", "Analog VU Meters", "Dual backlit analog needle meters with ballistic dB dynamics");

    companion object {
        fun fromStorageKey(key: String?): VisualizerMode =
            entries.find { it.storageKey.equals(key, ignoreCase = true) } ?: SPECTRUM_BARS
    }
}
```

### 2.2 Frame Physics & State Extension (`VisualizerFrameData`)
Extend `VisualizerFrameData` with dual-channel needle angles and tape progression:
```kotlin
@Immutable
data class VisualizerFrameData(
    val frequencyBands: FloatArray = FloatArray(32),
    val wavePoints: FloatArray = FloatArray(64),
    val bassEnergy: Float = 0f,
    val rotationAngle: Float = 0f,
    val leftNeedleAngle: Float = -45f,
    val rightNeedleAngle: Float = -45f,
    val leftPeak: Boolean = false,
    val rightPeak: Boolean = false,
    val tapeProgress: Float = 0f
)
```

---

## 3. Physics & Ballistics Engine (`AudioVisualizerEngine.kt`)

### 3.1 Analog VU Needle Ballistics
Standard VU meter ballistics mandate a 300ms integration time to 99% deflection with slight overshoot (~1% to 1.5%):
- **Attack Phase**: Fast response ($\tau_{\text{attack}} \approx 15\text{ms}$) following peak energy in left ($0..15$ bands) and right ($16..31$ bands).
- **Decay Phase**: Damped exponential fallback ($\tau_{\text{decay}} \approx 280\text{ms}$).
- **Needle Angle Mapping**: Normalized dB energy ($-\infty\text{dB} \rightarrow +3\text{dB}$) mapped to arc degrees: $-45^\circ$ (rest at $-20\text{dB}$) to $+45^\circ$ ($+3\text{dB}$ peak limit).
- **Peak LED**: Triggers when instantaneous energy exceeds the $0\text{dB}$ threshold ($> 85\%$ max level).

### 3.2 Cassette Magnetic Tape Progression
- **Tape Thickness Formula**:
  - Supply Reel Radius: $R_{\text{supply}} = R_{\min} + (R_{\max} - R_{\min}) \cdot \sqrt{\max(0.02, 1.0 - \text{tapeProgress})}$
  - Take-up Reel Radius: $R_{\text{takeup}} = R_{\min} + (R_{\max} - R_{\min}) \cdot \sqrt{\max(0.02, \text{tapeProgress})}$
- **Spool Hub Rotation**: Hubs rotate at $\omega = 180^\circ/\text{s}$ when playing, accelerating during seek operations.

---

## 4. Vector Geometry & Rendering (`AudioVisualizerView.kt`)

### 4.1 Vintage Cassette Deck
- **Body Shell**: Rounded rectangle with beveled inner edges, 4 corner metallic screws with cross-head slots, and lower trapezoidal tape-head bridge.
- **Center Inspection Window**: Clear acrylic window with horizontal level guide ticks ($0, 50, 100$).
- **Reels & Spools**:
  - Dual dark magnetic tape circles layered under spools with dynamic radius $R_{\text{supply}}$ and $R_{\text{takeup}}$.
  - 6-tooth white plastic hub gears with center axle hole.
- **Cassette Label**:
  - Text: `"A • SIDE"`, `"NORMAL BIAS / TYPE I"`, and active `Song Title` and `Artist Name`.
  - Gradient accent band tinted by the current theme palette.

### 4.2 Dual Analog VU Meters
- **Enclosure**: Dual twin rectangular gauge windows set inside a brushed gunmetal/aluminum frame.
- **Dial Plate**:
  - Warm incandescent gradient background (amber/tungsten for `ACCENT`, cool studio white for `MONOCHROME`).
  - Arced calibration scale: black ticks for $-20\text{dB} \dots 0\text{dB}$, red zone for $+1\text{dB} \dots +3\text{dB}$.
  - Printed scale numbers: `"-20"`, `"-10"`, `"-5"`, `"0"`, `"+3"`, `"VU"`.
- **Needles & Peak LEDs**:
  - Tapered black needle with pivot cap.
  - Overload LED at top right of each meter glowing bright red on peaks with soft radial aura.
- **Convex Glass Cover**: Diagonal specular gradient sheen simulating physical curved glass.

---

## 5. Touch Gestures & Interactive Feedback

1. **Cassette Deck Gestures**:
   - **Single Tap**: Triggers mechanical tape-head engage animation with haptic tick.
   - **Double Tap Left/Right**: Seeks $\pm 10\text{s}$ and triggers accelerated spool spin.
2. **VU Meters Gestures**:
   - **Single Tap**: Toggles sensitivity mode between Studio Standard ($\text{0dB}$) and High Dynamic Sensitivity ($\text{+6dB}$) with backlighting warmth pulse.

---

## 6. Verification & Test Plan

1. **Unit Tests**:
   - `AudioVisualizerEngineTest`: Validate needle ballistics clamp, tape progress calculation, and decay rates.
   - `VisualizerModelsTest`: Verify enum parsing and storage key stability.
2. **Build Verification**:
   - Run `./gradlew :app:testDebugUnitTest --no-daemon`.
   - Run `./gradlew assembleDebug --no-daemon`.
