# Library Taste Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement an interactive, personalized Library Taste Profile Card showing listening time, musical archetype classification, top genres breakdown with visual progress bars, top engaged artists, and 1-tap "Play Top Taste" infinite playback.

**Architecture:** Create `TasteProfileManager` to process `EngagementDao` statistics; expose reactive profile flow and playback handler in `PlayerViewModel`; implement expandable Material 3 `TasteProfileCard` at the top of `LibraryScreen`.

**Tech Stack:** Kotlin 2.4, Room SQLite, Jetpack Compose Material 3 Expressive, Coroutines/StateFlow, JUnit 5.

## Global Constraints
- Append `--no-daemon` to all Gradle invocations.
- Always use `file://` scheme for markdown links.

---

### Task 1: Analytics & Archetype Engine in `TasteProfileManager.kt`

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/data/analytics/TasteProfileManager.kt`
- Create: `app/src/test/java/com/quietrays/tonarc/data/analytics/TasteProfileManagerTest.kt`

**Interfaces:**
- Produces: `TasteProfileManager.computeTasteProfile(): TasteProfile`

- [ ] **Step 1: Write failing tests in `TasteProfileManagerTest.kt` for archetype classification, genre ratio calculation, and artist ranking**
- [ ] **Step 2: Implement `TasteProfileManager.kt` with archetype heuristics and engagement metrics**
- [ ] **Step 3: Run unit tests to verify passing**
- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/analytics/TasteProfileManager.kt \
        app/src/test/java/com/quietrays/tonarc/data/analytics/TasteProfileManagerTest.kt
git commit -m "feat(analytics): implement TasteProfileManager with archetype classification and engagement metrics"
```

---

### Task 2: Reactive State & Playback in `PlayerViewModel.kt`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModelRadioTest.kt`

**Interfaces:**
- Produces: `PlayerViewModel.tasteProfile: StateFlow<TasteProfile?>`, `PlayerViewModel.playTopTasteMix()`

- [ ] **Step 1: Expose `tasteProfile` and `playTopTasteMix` in `PlayerViewModel.kt`**
- [ ] **Step 2: Add unit tests validating `playTopTasteMix`**
- [ ] **Step 3: Run unit tests to verify passing**
- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt \
        app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModelRadioTest.kt
git commit -m "feat(player): expose tasteProfile flow and playTopTasteMix action"
```

---

### Task 3: Expressive `TasteProfileCard.kt` & Library Integration

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/TasteProfileCard.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/LibraryScreen.kt`

**Interfaces:**
- Produces: Expandable hero card at the top of Library screen

- [ ] **Step 1: Implement `TasteProfileCard.kt` with archetype badge, genre ratio bars, artist affinities, and share profile intent**
- [ ] **Step 2: Embed `TasteProfileCard` in `LibraryScreen.kt` header**
- [ ] **Step 3: Run `./gradlew assembleDebug --no-daemon` to ensure Compose compilation succeeds**
- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/TasteProfileCard.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/screens/LibraryScreen.kt
git commit -m "feat(ui): add expandable TasteProfileCard to Library screen"
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
