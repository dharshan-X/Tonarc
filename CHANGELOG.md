# Changelog

All notable changes to the Tonarc music player will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Floating Pill Navigation, Spotify In-App Authentication & Performance

#### 1. Modern Minimalist Floating Pill Navigation Bar
- Floating Pill Style: Added a sleek, stadium-shaped (`CircleShape`) floating pill navigation bar option selectable under **Settings > Appearance > Navigation Bar Style**.
- Fluid Physics & Liquid Stretch/Squash: Asymmetric dual-edge spring physics (`headOffset` and `tailOffset`) creating organic horizontal elongation (up to $1.35\times$) and volume-conserving vertical squash during indicator motion, snapping elastically into place upon destination arrival.
- Active Icon Spring Pop: Bouncy spring pop animation (`0.90 -> 1.12 -> 1.0`) on the newly selected tab icon, rendered with hardware-accelerated `graphicsLayer` scaling.
- Hardware-Accelerated Sliding Indicator: All translation, stretch, and squash transforms run purely inside `Modifier.graphicsLayer { ... }` on the RenderNode, eliminating recompositions and layout passes on the UI thread for zero-stutter navigation transitions.
- Zero-Latency Visual Response: Immediate visual feedback on tab selection decoupled from route backstack resolution, with smooth animated color tint transitions (`animateColorAsState`).
- Search Double-Tap: Double-tapping the Search tab instantly activates and focuses the search bar for quick music lookups.
- Coordinated MiniPlayer Layout: Coordinated collapsed MiniPlayer bottom offset and rounded corner transition (`32.dp`) in `SheetVisualState`, ensuring seamless aesthetic alignment above the floating capsule.
- Dynamic System Insets & Screen Transitions: Fluid hardware-accelerated translation and alpha fading during player expansion and sub-route navigation.

#### 2. Spotify In-App Authentication & Playlist Importer
- In-App WebView Authentication: Direct in-app Spotify login flow (`SpotifyLoginActivity`) with desktop user-agent, modern TOTP token retrieval for `/api/token`, and session cookie sanitization.
- Accounts Screen Integration: Connect and disconnect Spotify accounts under the Accounts screen with status indicators.
- Authenticated Access & Private Playlists: Authenticated web player token retrieval, user profile fetching, and private playlist detection.
- Pathfinder GraphQL Fetcher: Resilient track extraction using Spotify's Pathfinder GraphQL API with sanitized session cookies.
- Hybrid Matching Engine: Matches imported Spotify playlists against local library files and YouTube Music online catalog for instant streaming and playback.
- Android Share Sheet Integration: Import Spotify playlists directly from Spotify URLs via the system share sheet.
- Zero-Stutter Importer Dialog: Optimized `ImportSpotifyPlaylistDialog` with memoized shapes and throttled progress updates for fluid multi-track matching.

#### 3. Taste Profile & Listening Analytics
- Streamed & Cached Track Inclusion: Taste Profile analytics and listening statistics now incorporate streamed and cached YouTube Music tracks.
- Cleaner Analytics: Filtered placeholder artists and albums from listening stats calculations.

#### 4. Material 3 Expressive Playlist Detail Screen Redesign
- Full-Page Unified Scroll Architecture: The entire screen scrolls as a single continuous document (`LazyColumn`) from top to bottom with zero nested inner scroll containers and completely clean edges (eliminated `ExpressiveScrollBar`).
- Dark Rounded Hero Card: Deep-toned surface container (`#16151a`) with expressive bottom curvature (`38.dp` radius), centered 176dp artwork with dynamic ambient glow matching theme/cover art, clean top with back and options buttons (no menu or profile buttons), and bold typography.
- Seam-Overlapping Play FAB: Tactile 56dp circular floating action button (`CircleShape`) anchored across the hero card dividing seam (`offset(x = -24.dp, y = 28.dp)`), driving play/pause state with spring bounce animation.
- Subtle Outlined List Container: Tracklist framed by a crisp 1dp outline border (`outlineVariant`) with 20dp rounded corners and slim 10dp side margins, with surface background matching the page seamlessly (0dp elevation and zero shadow).
- Circular Pastel Badges: 44dp circular badges with soft pastel tonal backgrounds (Blue, Lime, Purple, Orange, Chartreuse, Mint, Sage, Coral, Sky, Lavender) and saturated iconography / album art.
- Live Animated Equalizer: Real-time 3-bar animated equalizer wave bars displayed inside the pastel badge of the actively playing track.
- Action Chips Row: Scrollable chips for "+ Add Songs", "↕ Reorder", "✕ Remove", "🔀 Shuffle", and "⇅ Sort" with dedicated active states.
- Pinned Collapsing Top Bar: Fluid top app bar fading in as user scrolls through the tracklist with one-tap "Done" button during reorder/remove modes.
- 100% Feature Parity Preserved: Retained song picker sheet, drag-and-drop reordering with haptic feedback, item removal, library sorting sheet, playlist options sheet (edit, delete, default transition, M3U export), song info sheet, and offline cloud downloads.

---

## [0.1.0-alpha] - 2026-08-26

### Initial Alpha Milestone Release

#### 1. YouTube Music Library, Liked Songs Two-Way Sync & Genre Browsing
- Unified Library: Synced YouTube Music cloud playlists and Liked Music directly into the Library screen alongside local media.
- Pinned Liked Music Hero Card: Quick access to all your liked songs with one tap.
- YouTube Playlist Import: Import and save any YouTube or YouTube Music playlist by URL or ID directly to your Library Playlists section, with preview and local cloning options.
- YouTube Music Genre & Mood Browsing: Explore and stream YouTube Music curated hits, trending tracks, and playlists across popular genres (Pop, Rock, Hip-Hop, Electronic, R&B, Indie, Jazz, Classical, Metal, K-Pop, Latin, Lofi, etc.) and moods (Chill, Workout, Focus, Sleep, Energy, Party, Romance).
- Multi-Source Genre Search: Toggle seamlessly between local library genres and YouTube Music online catalog in Search and Explore views.
- Online Genre Exploration: Genre details view features online trending songs and curated YouTube playlists with one-tap playback and radio launch.
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

#### 8. Real-Time Audio-Reactive Visualizers & Audiophile Vinyl Turntable
- 4 Hardware-Accelerated Modes:
  - Spectrum Bars: 32-band dynamic equalizer frequency bars with peak hold decay.
  - Fluid Wave: Multi-layer organic fluid sine wave responding to playback rhythm.
  - Circular Pulse: Radial expanding acoustic aura around the album art.
  - Vinyl Turntable: Realistic rotating vinyl disc with concentric micro-grooves and authentic S-curved tonearm.
- Audiophile Tonearm Architecture:
  - Sculpted S-curve tube geometry with multi-layer brushed silver and specular highlights.
  - Heavy counterweight assembly with stainless steel shaft and knurled tracking force ring.
  - Gimbal pivot housing with bearing and center screw detailing.
  - Aerodynamic beveled headshell with weight-reduction slots, finger lift cue hook, phono cartridge, and glowing diamond stylus tip.
  - Physical elevation drop shadow and subtle audio-reactive bass resonance tracking.
- Custom Color Styling: Theme Accent, Dynamic Gradient, and Monochrome options.
- 1-Tap Quick Control: Dedicated visualizer button in Now Playing action bar to toggle on/off or open the visualizer effects bottom sheet.
- Settings Integration: Appearance settings toggle and mode selector under Now Playing.

#### 9. Library UI, Taste Profile Ergonomics & Genre Enhancements
- Ultra-Compact Taste Profile Card: Redesigned Taste Profile card with low-profile header, full-surface touch responsiveness, direct 1-tap "Play" mix button, and dismiss capability.
- Preserved Library Viewport: Restricted taste card to the Playlists tab to maintain 100% field-of-view for browsing songs, albums, and artists.
- Multi-Delimiter Genre Parsing: Support for `/`, `;`, `|`, and `,` genre delimiters across Room DAOs and repository layers.
- Smart Genre Query Matching: Precise keyword disambiguation preventing partial keyword collisions in YouTube Music catalog lookups.

#### 10. Repository & Build Modernization
- Updated all links and metadata to `dharshan-X/Tonarc`.
- Excluded documentation and markdown files (`**.md`) from triggering unnecessary GitHub Actions CI workflows.

---
