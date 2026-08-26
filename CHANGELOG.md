# Changelog

All notable changes to the Tonarc music player will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0-alpha] - 2026-08-26

### Initial Alpha Milestone Release

#### 1. YouTube Music Library & Liked Songs Two-Way Sync
- Unified Library: Synced YouTube Music cloud playlists and Liked Music directly into the Library screen alongside local media.
- Pinned Liked Music Hero Card: Quick access to all your liked songs with one tap.
- Bidirectional Like Synchronization: Toggling favorite in the player optimistically updates local storage and syncs to YouTube Music via authenticated Innertube mutations.
- Offline Caching: Room database caching for instant offline playlist browsing.

#### 2. Instant Radio & Infinite Smart Queue
- SmartRadioEngine: 70/30 hybrid cloud discovery + local favorite interleaving.
- Zero-Latency Startup: Starts playback of seed songs immediately while asynchronously extending the queue in the background.
- Universal Entry Points: Start Instant Radio buttons available in song options menus, artist headers, albums, playlists, search, and queue sheet.
- Infinite Autoplay: Automatically pre-fetches next batches as the queue approaches completion.

#### 3. Auto-Lyrics & Multi-Source Karaoke Aggregation
- Zero-Latency Background Preloading: Reactive lyrics hydration on song playback start.
- Multi-Source Fallback: Embedded ID3/TTML -> LRCLIB studio synced -> YouTube Timed Text transcripts.
- Offline Room Caching: Fetched lyrics are persisted locally for offline karaoke access.
- Attributed Manual Search: Candidate search with source attribution badges (LRCLIB / YouTube Transcript).

#### 4. Time-of-Day Contextual Mixes & Discovery Radar
- 5 Dynamic Mood Mixes:
  - Morning Rise: Acoustic, soft indie & ambient wakeups.
  - Afternoon Energy: High-tempo hits, pop & workout drive.
  - Evening Chill: Downtempo, R&B, jazz & calm favorites.
  - Midnight Mood: Lofi, chillhop & late-night serenity.
  - Discovery Radar: Pure discovery with 0 recent plays in the last 7 days.
- Interactive Mood Carousel: Filter chip row with dynamic theme gradients.
- Infinite Mood Playback: Queue automatically continues generating tracks matching the active mood.

#### 5. Library Taste Profile Card
- Musical Archetype Classifier: Dynamically determines listener personas (Late-Night Audiophile, Acoustic Explorer, High-Energy Motivator, Eclectic Dreamer, Melody Connoisseur).
- Visual Analytics: Top 3 genres percentage progress bar, top 5 artist affinity ranking, and total listening time counters.
- 1-Tap Play Top Taste: Personalized instant mix of your most-loved songs with infinite queue extension.
- Native Android Sharing: Shareable music profile summary card.

#### 6. Brand Design & App Icons
- Vinyl Turntable Icon: Custom adaptive launcher icons (foreground, background, legacy round/squircle) across all screen densities.
- Themed Icons: Monochrome vector support for Android 13+ Material You themed icons.
- Status Bar Icon: Updated notification player small icon to the vinyl turntable vector.
- In-App Identity: High-resolution logo in the About screen.

#### 7. F-Droid Release Readiness
- Fastlane Store Metadata: Updated store descriptions, tags, and icons.
- F-Droid Recipe: Created metadata build recipe for com.quietrays.tonarc with NonFreeNet anti-feature disclosure.

---
