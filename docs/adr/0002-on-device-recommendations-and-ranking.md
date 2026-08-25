# 0002: On-Device Candidate Aggregation, Personalized Ranking, and Visitor Data Architecture

Tonarc provides private, on-device recommendation algorithms, cold-start favorite artist mixes, structured concurrent candidate aggregators, and persistent InnerTube visitor tokens without external tracking servers or third-party telemetry.

## Context

Users require personalized recommendations, daily mixes, and favorite artist carousels that work immediately out of the box (including cold start on new installs), respect artist diversity, and query multiple remote and local candidates efficiently without UI stutters or infinite re-fetching loops.

## Decision

1. **On-Device Candidate Aggregator**: Aggregate candidates concurrently from multiple independent providers (recently played, top local artists, user-chosen favorite artists, and YouTube browse sections) via Kotlin structured concurrency (`coroutineScope` + `async`).
2. **Personalized Scoring & Ranker**: Apply an on-device weighted scoring model in `PersonalizedRanker` that factors in recent playback frequency, completion rate, favorite status, and time decay without requiring external AI servers.
3. **Artist Diversity Enforcement**: Enforce artist variety using composite string keys (`artistKey(song)`) rather than integer IDs to avoid collisions between local audio tracks (`artistId = 0L`) and online metadata.
4. **Instant Cold-Start Discovery**: Remove high engagement thresholds (e.g. requiring 20 plays before generating mixes), allowing new users to immediately enjoy curated artist and genre carousels based on initial setup preferences.
5. **Persistent InnerTube Visitor Token**: Store and cache `visitorData` in Jetpack DataStore (`PreferencesKeys.YOUTUBE_VISITOR_DATA`) so cold app launches immediately supply valid session context headers (`X-YouTube-Visitor-Data`) to InnerTube endpoints.
6. **In-Flight Job Guarding**: Protect all background recommendation fetch operations (`loadHomeRecommendations()`) with active `Job` guards to eliminate redundant concurrent requests during UI recomposition.

## Consequences

- Recommendations generate instantly on the user's device while fully preserving privacy.
- Cold-start mix generation succeeds on fresh installs without waiting for extended user listening history.
- Network bandwidth is minimized by caching visitor tokens across app restarts and guarding asynchronous recommendation jobs.
