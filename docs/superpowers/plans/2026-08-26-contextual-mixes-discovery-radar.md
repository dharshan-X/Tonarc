# Contextual Mixes & Discovery Radar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement dynamic time-of-day contextual daily mixes (Morning Rise, Afternoon Energy, Evening Chill, Midnight Mood) and Discovery Radar (100% unheard/rarely heard recommendations), complete with interactive mood filter chips, time-aware hero banners, and infinite mood playback.

**Architecture:** Extend `DailyMixManager` with mood clustering & 7-day exclusion heuristics; update `DailyMixStateHolder` and `PlayerViewModel` to manage multi-mix state and infinite mood queues; enhance `DailyMixSection` & `DailyMixScreen` with dynamic greeting heroes and smooth mood filter chips.

**Tech Stack:** Kotlin 2.4, Room SQLite, Jetpack Compose Material 3 Expressive, Coroutines/StateFlow, JUnit 5.

## Global Constraints
- Append `--no-daemon` to all Gradle invocations.
- Always use `file://` scheme for markdown links.

---

### Task 1: Contextual Synthesis Engine & Discovery Radar in `DailyMixManager.kt`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/data/DailyMixManager.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/data/DailyMixManagerTest.kt`

**Interfaces:**
- Produces: `DailyMixManager.computeContextualMix(mood, allSongs, favoriteSongIds, limit)`

- [ ] **Step 1: Write failing tests in `DailyMixManagerTest.kt` for mood filtering, time-of-day resolution, and Discovery Radar 7-day exclusion**
- [ ] **Step 2: Implement `MixMood`, `ContextualMix`, and `computeContextualMix` in `DailyMixManager.kt`**
- [ ] **Step 3: Run unit tests to verify passing**
- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/DailyMixManager.kt \
        app/src/test/java/com/quietrays/tonarc/data/DailyMixManagerTest.kt
git commit -m "feat(dailymix): implement contextual time-of-day mixes and Discovery Radar engine"
```

---

### Task 2: Multi-Mix Reactive State & Infinite Playback in `DailyMixStateHolder.kt` & `PlayerViewModel.kt`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/DailyMixStateHolder.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/DailyMixStateHolderTest.kt`

**Interfaces:**
- Produces: `DailyMixStateHolder.contextualMixes`, `DailyMixStateHolder.selectedMood`, `PlayerViewModel.playContextualMix(mix)`

- [ ] **Step 1: Write failing test in `DailyMixStateHolderTest.kt` for multi-mix generation and mood selection**
- [ ] **Step 2: Update `DailyMixStateHolder.kt` to generate all 5 contextual mixes and expose selection flows**
- [ ] **Step 3: Implement `playContextualMix` in `PlayerViewModel.kt` with infinite mood auto-generation**
- [ ] **Step 4: Run unit tests to verify passing**
- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/DailyMixStateHolder.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt \
        app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/DailyMixStateHolderTest.kt
git commit -m "feat(dailymix): wire multi-mix state holder and infinite mood playback"
```

---

### Task 3: Dynamic Hero Greeting, Mood Filter Chips & Discovery Radar UI

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/DailyMixSection.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/DailyMixScreen.kt`

**Interfaces:**
- Produces: Interactive hero card with time greeting, mood filter chip carousel, Discovery Radar surface

- [ ] **Step 1: Add time-of-day greeting header, dynamic gradient theming, and mood filter chips to `DailyMixSection.kt`**
- [ ] **Step 2: Update `DailyMixScreen.kt` with mood switcher header and Discovery Radar metadata**
- [ ] **Step 3: Run `./gradlew assembleDebug --no-daemon` to ensure Compose compilation succeeds**
- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/DailyMixSection.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/screens/DailyMixScreen.kt
git commit -m "feat(ui): add time-of-day greeting hero, mood filter chips, and Discovery Radar UI"
```

---

### Task 4: End-to-End Verification & Release

- [ ] **Step 1: Run full unit test suite**
Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Assemble Debug APK**
Run: `./gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Merge and Push to GitHub**
```bash
git push origin main
```
