# Design Specification: Library Taste Profile Card (Phase 5)

**Author:** Tonarc Team  
**Date:** 2026-08-26  
**Status:** Approved  
**Target:** Android App (`com.quietrays/tonarc`)  

---

## 1. Overview

Phase 5 introduces the **Library Taste Profile Card**, an interactive analytics and playback component positioned at the top of the Library screen. It calculates real-time listening statistics from Room's `EngagementDao`, classifies musical archetypes, presents visual genre ratio breakdowns, ranks top artists, and provides a 1-tap "Play Top Taste" infinite playback action.

---

## 2. Architecture & Metrics

### 2.1 `TasteProfileManager.kt`
- **Location:** `app/src/main/java/com/quietrays/tonarc/data/analytics/TasteProfileManager.kt`
- **Responsibilities:**
  - Queries `EngagementDao.getAllEngagements()` and `MusicRepository.getAllSongsOnce()`.
  - Calculates total duration (hours/mins) and total plays.
  - Aggregates genre frequency and computes percentage ratios (`GenreRatio`).
  - Aggregates artist play counts and total duration (`ArtistAffinity`).
  - Evaluates listening timestamp distribution and top genres to assign an archetype:
    - *"🌌 Late-Night Audiophile"*
    - *"🌅 Morning Explorer"*
    - *"⚡ High-Energy Motivator"*
    - *"🎧 Eclectic Dreamer"*
    - *"🎵 Melody Connoisseur"*
  - Returns `TasteProfile` object.

### 2.2 UI & Playback Integration
- **`TasteProfileCard.kt`**: Material 3 Expressive expandable card with animated progress bars, archetype badge, top artists list, and share button.
- **`PlayerViewModel.kt`**: Exposes `tasteProfile: StateFlow<TasteProfile?>` and `playTopTasteMix()`.

---

## 3. Verification & Testing

- Unit test suite: `./gradlew :app:testDebugUnitTest --no-daemon`
- Debug APK build: `./gradlew assembleDebug --no-daemon`
