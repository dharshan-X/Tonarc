# Design Specification: Contextual Time-of-Day Daily Mixes & Discovery Radar (Phase 4)

**Author:** Tonarc Team  
**Date:** 2026-08-26  
**Status:** Approved  
**Target:** Android App (`com.quietrays/tonarc`)  

---

## 1. Overview

Phase 4 elevates the daily music discovery experience in Tonarc by introducing:
1. **Dynamic Time-of-Day Contextual Mixes**: Morning Rise, Afternoon Energy, Evening Chill, and Midnight Mood.
2. **Discovery Radar**: Dedicated mix focusing 100% on unheard/rarely heard tracks matching user affinity.
3. **Interactive Mix Switcher & Mood Filter Chips**: In `DailyMixSection` and `DailyMixScreen`.
4. **Mood-Aware Infinite Autoplay**: Seamless queue extension aligned with the selected mix mood.

---

## 2. Architecture & Algorithms

### 2.1 Mood Taxonomy & Hour Windows

| Mood | Window | Genres / Tags | Target Vibe |
|---|---|---|---|
| `MORNING_FOCUS` | 05:00 - 11:59 | Acoustic, Folk, Ambient, Classical, Soft Indie, Low BPM | Gentle, uplifting wakeup |
| `ENERGY_BOOST` | 12:00 - 17:59 | Pop, Rock, Dance, Electronic, EDM, Hip-Hop, Upbeat | High motivation & workout |
| `EVENING_CHILL` | 18:00 - 22:59 | R&B, Soul, Jazz, Downtempo, Indie, Mellow Pop | Unwinding & relaxing |
| `MIDNIGHT_LOFI` | 23:00 - 04:59 | Lofi, Chillhop, Synthwave, Ambient, Instrumental | Serenity & late night focus |
| `DISCOVERY_RADAR`| Universal | User Top Genres (played 0 times in last 7 days) | Pure discovery |

### 2.2 `DailyMixManager.kt` Contextual Synthesis
- `computeContextualMix(mood: MixMood, allSongs: List<Song>, favoriteSongIds: Set<String>, limit: Int = 30): List<Song>`
- Filters candidates based on keyword matching against `song.genre`, `song.title`, and `song.artist`.
- For `DISCOVERY_RADAR`, strictly filters out songs where `stats.lastPlayedTimestamp > System.currentTimeMillis() - 7 * 86400000L` and play count $\ge 3$.

### 2.3 `DailyMixStateHolder.kt` Multi-Mix State
- Holds `availableMixes: StateFlow<List<ContextualMix>>` and `selectedMixMood: StateFlow<MixMood>`.
- Generates all 5 mixes concurrently on app launch / refresh.
- Exposes `selectMood(mood: MixMood)`.

---

## 3. UI Presentation

- **Hero Card**: Dynamically shows current time-of-day greeting and theme colors.
- **Mood Filter Row**: Scrollable Material 3 `FilterChip` / `InputChip` row for switching mixes with zero reload delay.
- **Queue Source Attribute**: Displays `QueueSource.ContextualMix(mood)` with mood-specific styling and infinite radio synthesis.

---

## 4. Verification & Testing

- Unit test suite: `./gradlew :app:testDebugUnitTest --no-daemon`
- Debug APK build: `./gradlew assembleDebug --no-daemon`
