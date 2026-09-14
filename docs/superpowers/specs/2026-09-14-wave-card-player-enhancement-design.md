# Wave-Card Player Enhancement Design Specification

> Status: Approved by User
> Date: 2026-09-14
> Target File: [WaveCardPlayerContent.kt](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt)
> Related Files: [UserPreferencesRepository.kt](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/data/preferences/UserPreferencesRepository.kt), [PlayerViewModel.kt](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt), [UnifiedPlayerSheetLayers.kt](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/UnifiedPlayerSheetLayers.kt)

---

## 1. Overview & Problem Statement

The Wave-Card Player in Tonarc is an expressive, Material 3 music playback screen featuring a full-stage album art presentation, an elevated waveform card, and a three-button tactile transport dock.

While the baseline layout is established, user feedback highlighted several refinement opportunities:
1. **Scrubber Organic Fluidity**: The current single-strand mathematical sine wave feels synthetic and lacks acoustic reactivity and physical touch response.
2. **Card Materiality & Depth**: The middle card can feel flat without ambient depth, subtle specular edge highlights, or organic color bleeding from the artwork.
3. **Button Physics & Layout Breathing Room**: Transport buttons should feel responsive and bouncy with subtle spring dynamics without excessive weight distortion, rubber-banding, or cramped spacing.
4. **M3 Expressive Integration**: Seamlessly incorporate the official Material 3 Expressive [LoadingIndicator](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt#L874-L878) inside the play/pause pill for loading and buffering states.
5. **Strict Preference Respect**: Only display the Audio Tools (speed, pitch, A-B repeat) action button when explicitly enabled in user preferences.

This specification unifies **Direction A (Organic Liquid Wave)** and **Direction B (Frosted Studio Deck)** into a cohesive, production-grade Jetpack Compose design.

---

## 2. Visual Architecture & Depth System ("Frosted Studio Deck")

### 2.1 Vertical Proportions
- **Upper Stage (54%)**: Full-width album artwork stage.
- **Middle Wave-Card (30%)**: Rounded bottom card hosting title, metadata, audio badge, and the liquid wave scrubber.
- **Bottom Transport Dock (16%)**: Fixed-height ergonomic dock providing tactile media controls with generous finger clearance.

### 2.2 Artwork Stage & Ambient Bloom
- **Cubic Alpha Fade**: The bottom 110dp of the album artwork is masked using `CompositingStrategy.Offscreen` with a `BlendMode.DstIn` vertical cubic-eased alpha gradient, dissolving the artwork directly into the card background tone.
- **Ambient Radial Bloom**: Behind the artwork, an ambient radial gradient softly bleeds the dominant palette color into the surface background (18% opacity in Dark theme, 8% in Light theme), creating atmospheric depth without muddying contrast.
- **Top Glass Buttons**: Four circular glass action buttons (Collapse, Visualizer, Audio Tools, More Options) rendered with `2.dp` tonal elevation and subtle outline border. The Audio Tools button is conditionally rendered based on `showAudioToolsInPlayer`.

### 2.3 Middle Wave-Card Materiality
- **Shape & Frame**: Top edge flat, bottom corners rounded at `36.dp`.
- **Specular Hairline Highlight**: An inner `1.dp` solid stroke with 25% on-surface highlight lines the top boundary of the card, catching overhead ambient light.
- **Outer Border**: Subtle `1.5.dp` outline stroke using `outlineVariant` in Dark mode and a soft warm tint in Light mode, replacing harsh neobrutalist black borders.
- **Surface Fill**: Multi-stop subtle gradient transitioning smoothly from `surfaceContainerLow` to `surfaceContainer`.

---

## 3. Organic Liquid Wave Scrubber Engine

### 3.1 Dual-Layer Harmonic Spline Waveform
Instead of a single rigid sine curve, the scrubber renders two interwoven fluid waves:
1. **Primary Crest Wave**:
   - Stroke width: `3.dp`.
   - Color: Primary dynamic accent color (`primary`).
   - Motion: Dynamic fluid phase progression proportional to playback speed.
2. **Harmonic Echo Wave**:
   - Stroke width: `1.5.dp`.
   - Color: Primary accent at 40% alpha.
   - Frequency: 1.5x harmonic multiplier, phase-shifted by 45 degrees, creating authentic acoustic wave resonance.

### 3.2 Dynamic Playback States
- **Playing**: Fluid continuous wave ripple with gentle breathing amplitude (`8.dp` to `12.dp`).
- **Paused**: Waves gently settle into a low-amplitude calm baseline (`4.dp` wave height).
- **Buffering / Loading**: Gentle undulating wave pulse synchronized with the M3 Expressive loader in the play button.

### 3.3 Interactive Touch & Elastic Scrubber Physics
- **Vertical Expansion**: The scrubber bounds expand from `36.dp` resting height to `52.dp` upon touch with snappy spring motion (`stiffness = Spring.StiffnessMediumLow`, `dampingRatio = 0.78f`).
- **Pluck Amplitude Bump**: The wave height dynamically increases directly under the user's touch point (Gaussian curve multiplier), mimicking a physical vibrating guitar string.
- **Floating Time Micro-Bubble**:
  - A rounded stadium pill (`CircleShape`, `surfaceContainerHighest` fill with hairline border) floats `28.dp` directly above the touch point while dragging.
  - Displays formatted position (`mm:ss`) and scrub offset delta (`+0:15`, `-0:30`).
- **Tactile Haptic Feedback**:
  - Triggers `HapticFeedbackType.TextHandleMove` as the scrub point crosses timeline intervals.
  - Triggers a subtle confirmation haptic upon finger release.

---

## 4. Typography, Badges & Controls Integration

### 4.1 Metadata Hierarchy
- **Song Title**:
  - Material 3 `headlineSmall` (`FontWeight.Bold`), auto-sizing between `20.sp` and `24.sp`.
  - Automatic horizontal marquee scrolling with edge gradient masks when text overflows available width.
- **Artist & Album**:
  - `titleMedium` in `onSurfaceVariant` (85% opacity).
  - Clickable touch targets navigating directly to Artist or Album detail screens.
- **Header Actions**:
  - Favorite toggle (`Icons.Rounded.Favorite` / `FavoriteBorder`) with spring scale bounce.
  - Lyrics pill button (`Icons.Rounded.Notes` + `"Lyrics"` text label) with tactile ripple.

### 4.2 Technical Audio Badge (Hi-Res / Codec Chip)
- Compact stadium chip above the scrubber showing audio metadata (e.g. `FLAC • 24-bit 96.0 kHz` or `OPUS • 160 kbps`).
- Subtle `1.dp` outline border and `surfaceContainerHigh` container fill.
- Tapping triggers the [TrackDetailsDialog](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/TrackDetailsDialog.kt).

### 4.3 Auxiliary Controls Row
- Located above the transport dock with `16.dp` vertical clearance:
  - Shuffle toggle (`Icons.Rounded.Shuffle`).
  - Repeat toggle (`Icons.Rounded.Repeat` / `RepeatOne`).
  - Queue sheet trigger (`Icons.AutoMirrored.Rounded.QueueMusic`).
- Balanced spacing eliminating any cramped visual density.

---

## 5. Transport Dock & Ergonomics

### 5.1 Three-Button Ergonomic Dock
- **Previous Track**:
  - Squircle shape with rounded corners (`20.dp`), height `58.dp`.
  - Layout weight: resting `1.0f`, gentle press expansion to `1.15f`.
  - Fill: `surfaceContainerHigh`.
  - Border: `1.5.dp` outline matching the card theme.
  - Icon: `Icons.Rounded.SkipPrevious` (`28.dp`).
- **Play / Pause Stadium Pill**:
  - Full stadium capsule (`CircleShape`), height `64.dp`.
  - Layout weight: resting `1.75f`, subtle press flex to `1.85f`.
  - Fill: `primaryContainer`.
  - Border: `1.5.dp` accent outline.
  - **Dynamic Content States**:
    - *Buffering / Loading*: Material 3 Expressive [LoadingIndicator](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt#L874-L878) (`30.dp`) with shape-morphing indeterminate animation.
    - *Ready / Playing / Paused*: [MorphingPlayPauseIcon](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt#L880-L885) (`32.dp`) with fluid path morphing.
- **Next Track**:
  - Squircle shape with rounded corners (`20.dp`), height `58.dp`.
  - Layout weight: resting `1.0f`, gentle press expansion to `1.15f`.
  - Fill: `surfaceContainerHigh`.
  - Border: `1.5.dp` outline matching the card theme.
  - Icon: `Icons.Rounded.SkipNext` (`28.dp`).

### 5.2 Motion & Tactile Feedback
- Snappy spring dynamics: `spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)`.
- Subtle `0.96f` scale squeeze on press with immediate haptic response.
- Fully synchronized dynamic color adaptation across Light and Dark themes.

---

## 6. Engineering Conventions & Constraints

1. **Zero Emojis**: Strictly zero emojis in code, comments, string resources, test fixtures, documentation, or commit messages.
2. **File References**: All internal file links formatted with clickable `file://` scheme.
3. **Gradle Invocations**: Always append `--no-daemon` to all Gradle commands.
4. **Performance**:
   - Use `Modifier.graphicsLayer` for translations, scales, and alpha animations to bypass recomposition loops.
   - Use `derivedStateOf` for calculated animation fractions.
   - Canvas wave rendering runs inside `Canvas` with pre-allocated path objects to prevent GC pressure during fluid 60/120fps wave animations.

---

## 7. Verification & Test Plan

1. **Unit Testing**:
   - Run `./gradlew :app:testDebugUnitTest --no-daemon` to ensure all existing player and viewmodel tests pass cleanly.
2. **UI Inspection & Device Verification**:
   - Assemble debug APK: `./gradlew assembleDebug --no-daemon`.
   - Install on connected device `CPH2667` via ADB.
   - Capture screencaps across Light theme, Dark theme, Playing, Paused, and Buffering states.
   - Verify M3 Expressive `LoadingIndicator` displays with morphing polygons while loading/buffering.
   - Verify Audio Tools button visibility obeys `showAudioToolsInPlayer`.
   - Verify waveform expands on touch and displays the floating time micro-bubble.
