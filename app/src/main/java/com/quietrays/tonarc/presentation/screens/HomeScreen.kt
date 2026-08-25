package com.quietrays.tonarc.presentation.screens

import com.quietrays.tonarc.presentation.navigation.navigateSafely
import com.quietrays.tonarc.presentation.navigation.navigateSafelyReplacing

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.CollagePattern
import com.quietrays.tonarc.presentation.components.AlbumArtCollage
import com.quietrays.tonarc.presentation.components.BetaInfoBottomSheet
import com.quietrays.tonarc.presentation.components.ChangelogBottomSheet
import com.quietrays.tonarc.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.quietrays.tonarc.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.quietrays.tonarc.presentation.components.DailyMixSection
import com.quietrays.tonarc.presentation.components.HomeGradientTopBar
import com.quietrays.tonarc.presentation.components.HomeOptionsBottomSheet
import com.quietrays.tonarc.presentation.components.HorizontalAlbumCarouselSection
import com.quietrays.tonarc.presentation.components.HorizontalAlbumCarouselSkeletonSection
import com.quietrays.tonarc.presentation.components.HorizontalArtistCarouselSection
import com.quietrays.tonarc.presentation.components.HorizontalArtistCarouselSkeletonSection
import com.quietrays.tonarc.presentation.components.HorizontalPlaylistCarouselSection
import com.quietrays.tonarc.presentation.components.HorizontalSongCarouselSection
import com.quietrays.tonarc.presentation.components.HorizontalSongCarouselSkeletonSection
import com.quietrays.tonarc.presentation.components.MiniPlayerHeight
import com.quietrays.tonarc.presentation.components.RecentlyPlayedSection
import com.quietrays.tonarc.presentation.components.RecentlyPlayedSectionMinSongsToShow
import com.quietrays.tonarc.presentation.components.ShimmerBox
import com.quietrays.tonarc.presentation.components.SmartImage
import com.quietrays.tonarc.presentation.components.StatsOverviewCard
import com.quietrays.tonarc.presentation.components.resolveMainScreenBottomGradientHeight
import com.quietrays.tonarc.presentation.model.collectRecentlyPlayedSongIds
import com.quietrays.tonarc.presentation.model.mapRecentlyPlayedSongs
import com.quietrays.tonarc.presentation.components.subcomps.PlayingEqIcon
import com.quietrays.tonarc.presentation.navigation.Screen
import com.quietrays.tonarc.presentation.components.StreamingProviderSheet
import com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel
import com.quietrays.tonarc.presentation.viewmodel.SettingsViewModel
import com.quietrays.tonarc.presentation.viewmodel.StatsViewModel
import com.quietrays.tonarc.ui.theme.ExpTitleTypography
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.quietrays.tonarc.ui.theme.ShapeCache
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource
import com.quietrays.tonarc.presentation.components.rememberModalSheetState

private const val HomeLoadingPlaceholderMinDurationMillis = 1200L

enum class HomeFilter {
    ALL,
    YOUTUBE_MUSIC,
    LOCAL_ONLY
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val homeMixPreviewSongs by playerViewModel.homeMixPreviewSongs.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val keepListeningSongs by playerViewModel.keepListeningSongs.collectAsStateWithLifecycle()
    val fromCommunitySongs by playerViewModel.fromCommunitySongs.collectAsStateWithLifecycle()
    val trendingCommunityPlaylists by playerViewModel.trendingCommunityPlaylists.collectAsStateWithLifecycle()
    val featuredPlaylists by playerViewModel.featuredPlaylists.collectAsStateWithLifecycle()
    val mixedForYouPlaylists by playerViewModel.mixedForYouPlaylists.collectAsStateWithLifecycle()
    val newAlbums by playerViewModel.newAlbums.collectAsStateWithLifecycle()
    val quickPicks by playerViewModel.quickPicks.collectAsStateWithLifecycle()
    val favoriteArtists by playerViewModel.favoriteArtists.collectAsStateWithLifecycle()
    val favoriteArtistSections by playerViewModel.favoriteArtistSections.collectAsStateWithLifecycle()
    val favoriteArtistsSongs by playerViewModel.favoriteArtistsSongs.collectAsStateWithLifecycle()
    val isHomeRecommendationsLoading by playerViewModel.isHomeRecommendationsLoading.collectAsStateWithLifecycle()
    val artistImages = remember(favoriteArtistSections) {
        favoriteArtistSections.associate { it.artistName to it.artistImageUrl }
    }
    var selectedHomeFilter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        playerViewModel.loadHomeRecommendations()
    }

    val usesFallbackHomeMix = remember(curatedYourMixSongs, dailyMixSongs) {
        curatedYourMixSongs.isEmpty() && dailyMixSongs.isEmpty()
    }
    val yourMixSongs = remember(curatedYourMixSongs, dailyMixSongs, homeMixPreviewSongs, selectedHomeFilter) {
        val baseMix = when {
            curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            else -> homeMixPreviewSongs
        }
        when (selectedHomeFilter) {
            HomeFilter.ALL -> baseMix
            HomeFilter.YOUTUBE_MUSIC -> {
                val ytOnly = baseMix.filter { it.youtubeId != null || it.id.startsWith("youtube_") || it.path.startsWith("youtube://") }.toImmutableList()
                if (ytOnly.isNotEmpty()) ytOnly else baseMix
            }
            HomeFilter.LOCAL_ONLY -> {
                val localOnly = baseMix.filter { it.youtubeId == null && !it.id.startsWith("youtube_") && !it.path.startsWith("youtube://") }.toImmutableList()
                if (localOnly.isNotEmpty()) localOnly else baseMix
            }
        }
    }
    var homePlaceholderRefreshGeneration by rememberSaveable { mutableIntStateOf(0) }
    var hasHomeLoadingMinimumElapsed by rememberSaveable(homePlaceholderRefreshGeneration) {
        mutableStateOf(false)
    }

    LaunchedEffect(homePlaceholderRefreshGeneration, yourMixSongs.isEmpty()) {
        if (yourMixSongs.isEmpty()) {
            hasHomeLoadingMinimumElapsed = false
            delay(HomeLoadingPlaceholderMinDurationMillis)
            hasHomeLoadingMinimumElapsed = true
        } else {
            hasHomeLoadingMinimumElapsed = true
        }
    }

    val shouldShowYourMixLoadingPlaceholder = yourMixSongs.isEmpty() && !hasHomeLoadingMinimumElapsed
    val recentSongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = 64
        )
    }
    val recentlyPlayedSourceSongsInitialValue = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) persistentListOf<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentlyPlayedSourceSongsInitialValue)
    val latestRecentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            maxItems = 64
        )
    }
    var recentlyPlayedSongs by rememberSaveable { mutableStateOf(latestRecentlyPlayedSongs) }
    val latestRecentlyPlayedSongsState = rememberUpdatedState(latestRecentlyPlayedSongs)

    LaunchedEffect(latestRecentlyPlayedSongs, lifecycleOwner) {
        val isHomeVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (recentlyPlayedSongs.isEmpty() || !isHomeVisible) {
            recentlyPlayedSongs = latestRecentlyPlayedSongs
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recentlyPlayedSongs = latestRecentlyPlayedSongsState.value
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    val filteredDailyMixSongs = remember(dailyMixSongs, selectedHomeFilter) {
        when (selectedHomeFilter) {
            HomeFilter.ALL -> dailyMixSongs
            HomeFilter.YOUTUBE_MUSIC -> dailyMixSongs.filter { it.youtubeId != null || it.id.startsWith("youtube_") || it.path.startsWith("youtube://") }.toImmutableList()
            HomeFilter.LOCAL_ONLY -> dailyMixSongs.filter { it.youtubeId == null && !it.id.startsWith("youtube_") && !it.path.startsWith("youtube://") }.toImmutableList()
        }
    }

    val filteredRecentlyPlayedSongs = remember(recentlyPlayedSongs, selectedHomeFilter) {
        when (selectedHomeFilter) {
            HomeFilter.ALL -> recentlyPlayedSongs
            HomeFilter.YOUTUBE_MUSIC -> recentlyPlayedSongs.filter { it.song.youtubeId != null || it.song.id.startsWith("youtube_") || it.song.path.startsWith("youtube://") }
            HomeFilter.LOCAL_ONLY -> recentlyPlayedSongs.filter { it.song.youtubeId == null && !it.song.id.startsWith("youtube_") && !it.song.path.startsWith("youtube://") }
        }
    }

    val filteredKeepListeningSongs = remember(keepListeningSongs, selectedHomeFilter) {
        when (selectedHomeFilter) {
            HomeFilter.ALL -> keepListeningSongs
            HomeFilter.YOUTUBE_MUSIC -> keepListeningSongs.filter { it.youtubeId != null || it.id.startsWith("youtube_") || it.path.startsWith("youtube://") }.toImmutableList()
            HomeFilter.LOCAL_ONLY -> keepListeningSongs.filter { it.youtubeId == null && !it.id.startsWith("youtube_") && !it.path.startsWith("youtube://") }.toImmutableList()
        }
    }

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || hasHomeLoadingMinimumElapsed || isBenchmarkMode
    }

    val yourMixSong: String = "Today's Mix for you"

    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong }
    }.collectAsStateWithLifecycle(initialValue = null)

    val isShuffleEnabled by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.isShuffleEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.isPlaying }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showChangelogBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalSheetState()
    val betaSheetState = rememberModalSheetState()
    val scope = rememberCoroutineScope()
    LocalContext.current

    val homeStatsOverview by statsViewModel.homeOverview.collectAsStateWithLifecycle()

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 180.dp.toPx() } }
    val isScrolledPastThreshold = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    var savedScrollIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var needsScrollRestore by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, listState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                savedScrollIndex = listState.firstVisibleItemIndex
                savedScrollOffset = listState.firstVisibleItemScrollOffset
                needsScrollRestore = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        needsScrollRestore,
        yourMixSongs.isNotEmpty(),
        dailyMixSongs.isNotEmpty(),
        recentlyPlayedSongs.size,
        homeStatsOverview
    ) {
        if (!needsScrollRestore) return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems == 0) return@LaunchedEffect
        val targetIndex = savedScrollIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
        listState.scrollToItem(targetIndex, savedScrollOffset)
        needsScrollRestore = false
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val onHomeRefresh: () -> Unit = {
        isRefreshing = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            try {
                homePlaceholderRefreshGeneration++
                settingsViewModel.refreshLibrary()
                playerViewModel.forceUpdateDailyMix()
                playerViewModel.loadHomeRecommendations(forceRefresh = true)
                delay(600)
            } finally {
                isRefreshing = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                HomeGradientTopBar(
                    onNavigationIconClick = {
                        navController.navigateSafely(Screen.Settings.route)
                    },
                    onMoreOptionsClick = {
                        showChangelogBottomSheet = true
                    },
                    onBetaClick = {
                        showBetaInfoBottomSheet = true
                    },
                    onStreamingClick = {
                          showStreamingProviderSheet = true
                    },
                    onMenuClick = {
                    },
                    isScrolled = isScrolledPastThreshold.value
                )
            }
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onHomeRefresh,
                state = pullToRefreshState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(
                        bottom = paddingValuesParent.calculateBottomPadding()
                                + 38.dp + bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                item(
                    key = "home_filter_chips_row",
                    contentType = "home_filter_chips_row"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToggleButton(
                            checked = selectedHomeFilter == HomeFilter.ALL,
                            onCheckedChange = { selectedHomeFilter = HomeFilter.ALL },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("All", style = MaterialTheme.typography.labelMedium)
                        }

                        ToggleButton(
                            checked = selectedHomeFilter == HomeFilter.YOUTUBE_MUSIC,
                            onCheckedChange = { selectedHomeFilter = HomeFilter.YOUTUBE_MUSIC },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("YouTube Music", style = MaterialTheme.typography.labelMedium)
                        }

                        ToggleButton(
                            checked = selectedHomeFilter == HomeFilter.LOCAL_ONLY,
                            onCheckedChange = { selectedHomeFilter = HomeFilter.LOCAL_ONLY },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Local Only", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (selectedHomeFilter != HomeFilter.YOUTUBE_MUSIC) {
                    if (yourMixSongs.isEmpty()) {
                        item(
                            key = "your_mix_placeholder",
                            contentType = "your_mix_placeholder"
                        ) {
                            if (shouldShowYourMixLoadingPlaceholder) {
                                YourMixLoadingPlaceholder()
                            } else {
                                YourMixEmptyPlaceholder(
                                    onRefresh = {
                                        homePlaceholderRefreshGeneration++
                                        settingsViewModel.refreshLibrary()
                                        playerViewModel.forceUpdateDailyMix()
                                    }
                                )
                            }
                        }
                    } else {
                        item(
                            key = "your_mix_header",
                            contentType = "your_mix_header"
                        ) {
                            YourMixHeader(
                                song = yourMixSong,
                                isShuffleEnabled = isShuffleEnabled,
                                onPlayShuffled = {
                                    if (usesFallbackHomeMix) {
                                        playerViewModel.shuffleAllSongs(queueName = "Your Mix")
                                    } else {
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = yourMixSongs,
                                            queueName = "Your Mix",
                                            startAtZero = true,
                                        )
                                    }
                                }
                            )
                        }

                        item(
                            key = "album_art_collage",
                            contentType = "album_art_collage"
                        ) {
                            val basePattern = settingsUiState.collagePattern
                            val isAutoRotate = settingsUiState.collageAutoRotate
                            val patterns = remember { CollagePattern.entries }

                            val activePattern = if (isAutoRotate) {
                                var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
                                LaunchedEffect(Unit) { rotationIndex++ }
                                remember(rotationIndex) {
                                    patterns[rotationIndex.coerceAtLeast(0) % patterns.size]
                                }
                            } else {
                                basePattern
                            }

                            AlbumArtCollage(
                                modifier = Modifier.fillMaxWidth(),
                                songs = yourMixSongs,
                                padding = 14.dp,
                                height = 400.dp,
                                pattern = activePattern,
                                onSongClick = { song ->
                                    if (usesFallbackHomeMix) {
                                        playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                                    } else {
                                        playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                                    }
                                }
                            )
                        }
                    }
                }

                if (filteredKeepListeningSongs.isNotEmpty()) {
                    item(
                        key = "keep_listening_section",
                        contentType = "keep_listening_section"
                    ) {
                        HorizontalSongCarouselSection(
                            title = "Keep Listening",
                            subtitle = "Pick up where you left off",
                            songs = filteredKeepListeningSongs,
                            currentPlayingSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onSongClick = { song ->
                                playerViewModel.showAndPlaySong(song, filteredKeepListeningSongs, "Keep Listening")
                            }
                        )
                    }
                }

                if (selectedHomeFilter != HomeFilter.LOCAL_ONLY) {
                    if (quickPicks.isNotEmpty()) {
                        item(
                            key = "quick_picks_section",
                            contentType = "quick_picks_section"
                        ) {
                            HorizontalSongCarouselSection(
                                title = "Quick Picks",
                                subtitle = "Instant radio & top tracks from YouTube Music",
                                songs = quickPicks,
                                currentPlayingSongId = currentSong?.id,
                                isPlaying = isPlaying,
                                onSongClick = { song ->
                                    playerViewModel.playSongWithSmartQueue(song, "Quick Picks")
                                }
                            )
                        }
                    } else if (isHomeRecommendationsLoading) {
                        item(
                            key = "quick_picks_skeleton",
                            contentType = "quick_picks_skeleton"
                        ) {
                            HorizontalSongCarouselSkeletonSection(
                                title = "Quick Picks",
                                subtitle = "Instant radio & top tracks from YouTube Music"
                            )
                        }
                    }
                }

                if (selectedHomeFilter != HomeFilter.LOCAL_ONLY) {
                    if (newAlbums.isNotEmpty()) {
                        item(
                            key = "new_albums_section",
                            contentType = "new_albums_section"
                        ) {
                            HorizontalAlbumCarouselSection(
                                title = "New Releases & Singles",
                                subtitle = "Latest albums and tracks on YouTube Music",
                                albums = newAlbums,
                                onAlbumClick = { album ->
                                    navController.navigateSafely(Screen.AlbumDetail.createRoute(album.id))
                                }
                            )
                        }
                    } else if (isHomeRecommendationsLoading) {
                        item(
                            key = "new_albums_skeleton",
                            contentType = "new_albums_skeleton"
                        ) {
                            HorizontalAlbumCarouselSkeletonSection(
                                title = "New Releases & Singles",
                                subtitle = "Latest albums and tracks on YouTube Music"
                            )
                        }
                    }
                }

                if (favoriteArtists.isNotEmpty()) {
                    item(
                        key = "favorite_artists_section",
                        contentType = "favorite_artists_section"
                    ) {
                        HorizontalArtistCarouselSection(
                            title = stringResource(R.string.home_section_favorite_artists),
                            subtitle = stringResource(R.string.home_section_favorite_artists_subtitle),
                            artists = favoriteArtists.toList(),
                            artistImages = artistImages,
                            onArtistClick = { artistName ->
                                navController.navigateSafely(Screen.FavoriteArtistSongs.createRoute(artistName))
                            }
                        )
                    }
                }

                if (favoriteArtistSections.isNotEmpty()) {
                    items(
                        items = favoriteArtistSections,
                        key = { "fav_artist_section_${it.artistName}" },
                        contentType = { "fav_artist_top_songs_section" }
                    ) { section ->
                        HorizontalSongCarouselSection(
                            title = section.artistName,
                            subtitle = "Top songs for you",
                            songs = section.songs,
                            currentPlayingSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            seeAllText = "View More",
                            onSeeAllClick = {
                                navController.navigateSafely(Screen.FavoriteArtistSongs.createRoute(section.artistName))
                            },
                            onSongClick = { song ->
                                playerViewModel.showAndPlaySong(
                                    song,
                                    section.songs,
                                    "Top Songs • ${section.artistName}"
                                )
                            }
                        )
                    }
                } else if (favoriteArtistsSongs.isNotEmpty()) {
                    item(
                        key = "favorite_artists_songs_section",
                        contentType = "favorite_artists_songs_section"
                    ) {
                        HorizontalSongCarouselSection(
                            title = stringResource(R.string.home_favorite_artists_mix_title),
                            subtitle = "Top songs from your favorite artists",
                            songs = favoriteArtistsSongs,
                            currentPlayingSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            seeAllText = "View More",
                            onSeeAllClick = if (favoriteArtists.isNotEmpty()) {
                                {
                                    val firstArtist = favoriteArtists.firstOrNull() ?: ""
                                    if (firstArtist.isNotBlank()) {
                                        navController.navigateSafely(Screen.FavoriteArtistSongs.createRoute(firstArtist))
                                    }
                                }
                            } else null,
                            onSongClick = { song ->
                                playerViewModel.showAndPlaySong(song, favoriteArtistsSongs, "Favorite Artists Mix")
                            }
                        )
                    }
                }

                if (filteredDailyMixSongs.isNotEmpty()) {
                    item(
                        key = "daily_mix_section",
                        contentType = "daily_mix_section"
                    ) {
                        DailyMixSection(
                            songs = filteredDailyMixSongs,
                            onClickOpen = {
                                navController.navigateSafely(Screen.DailyMixScreen.route)
                            },
                            onNavigateToAlbum = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.AlbumDetail.createRoute(song.albumId),
                                    patternToPop = Screen.AlbumDetail.route
                                )
                            },
                            onNavigateToArtist = { song ->
                                navController.navigateSafelyReplacing(
                                    route = Screen.ArtistDetail.createRoute(song.artistId),
                                    patternToPop = Screen.ArtistDetail.route
                                )
                            },
                            onNavigateToGenre = { song ->
                                song.genre?.let {
                                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                                }
                            },
                            playerViewModel = playerViewModel
                        )
                    }
                }

                if (filteredRecentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                    item(
                        key = "recently_played_section",
                        contentType = "recently_played_section"
                    ) {
                        val recentlyPlayedFilteredQueue = remember(filteredRecentlyPlayedSongs) {
                            filteredRecentlyPlayedSongs.map { it.song }.toImmutableList()
                        }
                        RecentlyPlayedSection(
                            songs = remember(filteredRecentlyPlayedSongs) { filteredRecentlyPlayedSongs.toImmutableList() },
                            onSongClick = { song ->
                                if (recentlyPlayedFilteredQueue.isNotEmpty()) {
                                    playerViewModel.playSongs(
                                        songsToPlay = recentlyPlayedFilteredQueue,
                                        startSong = song,
                                        queueName = "Recently Played"
                                    )
                                }
                            },
                            onOpenAllClick = {
                                navController.navigateSafely(Screen.RecentlyPlayed.route)
                            },
                            themeStateHolder = playerViewModel.themeStateHolder,
                            currentSongId = currentSong?.id,
                            contentPadding = PaddingValues(start = 8.dp, end = 24.dp)
                        )
                    }
                }

                if (featuredPlaylists.isNotEmpty() && selectedHomeFilter != HomeFilter.LOCAL_ONLY) {
                    item(
                        key = "featured_playlists_section",
                        contentType = "featured_playlists_section"
                    ) {
                        HorizontalPlaylistCarouselSection(
                            title = "Featured Playlists & Charts",
                            subtitle = "Curated collections & top charts from YouTube Music",
                            playlists = featuredPlaylists,
                            onPlaylistClick = { playlist ->
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                            }
                        )
                    }
                }

                if (fromCommunitySongs.isNotEmpty() && selectedHomeFilter != HomeFilter.LOCAL_ONLY) {
                    item(
                        key = "from_community_section",
                        contentType = "from_community_section"
                    ) {
                        HorizontalSongCarouselSection(
                            title = "Trending Hits",
                            subtitle = "Popular tracks on YouTube Music",
                            songs = fromCommunitySongs,
                            currentPlayingSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onSongClick = { song ->
                                playerViewModel.showAndPlaySong(song, fromCommunitySongs, "Trending Hits")
                            }
                        )
                    }
                }

                if (trendingCommunityPlaylists.isNotEmpty() && selectedHomeFilter != HomeFilter.LOCAL_ONLY) {
                    item(
                        key = "trending_community_playlists_section",
                        contentType = "trending_community_playlists_section"
                    ) {
                        HorizontalPlaylistCarouselSection(
                            title = "Trending Community Playlists",
                            subtitle = "Popular public playlists from creators",
                            playlists = trendingCommunityPlaylists,
                            onPlaylistClick = { playlist ->
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                            }
                        )
                    }
                }

                if (mixedForYouPlaylists.isNotEmpty() && selectedHomeFilter != HomeFilter.LOCAL_ONLY) {
                    item(
                        key = "mixed_for_you_section",
                        contentType = "mixed_for_you_section"
                    ) {
                        HorizontalPlaylistCarouselSection(
                            title = "Mixed For You",
                            subtitle = "Personalized genre & mood mixes",
                            playlists = mixedForYouPlaylists,
                            onPlaylistClick = { playlist ->
                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                            }
                        )
                    }
                }

                if (homeStatsOverview != null && selectedHomeFilter != HomeFilter.YOUTUBE_MUSIC) {
                    item(
                        key = "listening_stats_preview",
                        contentType = "listening_stats_preview"
                    ) {
                        StatsOverviewCard(
                            summary = homeStatsOverview,
                            onClick = { navController.navigateSafely(Screen.Stats.route) }
                        )
                    }
                }
            }
        }
    }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.2f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {

        }
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigateSafely(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }
    if (showChangelogBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChangelogBottomSheet = false },
            sheetState = sheetState
        ) {
            ChangelogBottomSheet()
        }
    }
    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBetaInfoBottomSheet = false },
            sheetState = betaSheetState,
        ) {
            BetaInfoBottomSheet()
        }
    }
    if (showStreamingProviderSheet) {
        val isNavidromeLoggedIn by navidromeViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJellyfinLoggedIn by jellyfinViewModel.isLoggedIn.collectAsStateWithLifecycle()
        StreamingProviderSheet(
            onDismissRequest = { showStreamingProviderSheet = false },
            isNavidromeLoggedIn = isNavidromeLoggedIn,
            onNavigateToNavidromeDashboard = {
                navController.navigateSafely(Screen.NavidromeDashboard.route)
            },
            isJellyfinLoggedIn = isJellyfinLoggedIn,
            onNavigateToJellyfinDashboard = {
                navController.navigateSafely(Screen.JellyfinDashboard.route)
            }
        )
    }
}

@Composable
private fun YourMixLoadingPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier
                    .width(180.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp)),
                shape = RoundedCornerShape(6.dp)
            )
            ShimmerBox(
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                shape = androidx.compose.foundation.shape.CircleShape
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(AbsoluteSmoothCornerShape(24.dp, 60)),
            shape = AbsoluteSmoothCornerShape(24.dp, 60),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }
    }
}

@Composable
private fun YourMixEmptyPlaceholder(
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 256.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = ShapeCache.expressiveHero,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_empty_placeholder_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.home_empty_placeholder_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FilledTonalButton(
                onClick = onRefresh,
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 22.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 22.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 22.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 22.dp,
                    smoothnessAsPercentBL = 60,
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.home_empty_placeholder_refresh))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(
    song: String,
    isShuffleEnabled: Boolean = false,
    onPlayShuffled: () -> Unit
) {
    val buttonCorners = 68.dp
    val colors = MaterialTheme.colorScheme

    val titleStyle = rememberYourMixTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.home_your_mix_title),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
            )

            Text(
                text = song,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        LargeExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp),
            onClick = onPlayShuffled,
            containerColor = if (isShuffleEnabled) colors.primary else colors.tertiaryContainer,
            contentColor = if (isShuffleEnabled) colors.onPrimary else colors.onTertiaryContainer,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = buttonCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = buttonCorners,
                smoothnessAsPercentTL = 60,
                cornerRadiusBL = buttonCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = buttonCorners,
                smoothnessAsPercentBL = 60,
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_shuffle_24),
                contentDescription = stringResource(R.string.cd_shuffle_play),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


@Composable
fun SongListItemFavs(
    modifier: Modifier = Modifier,
    cardCorners: Dp = 12.dp,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCurrentSong) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isCurrentSong) colors.primary else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cardCorners),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = albumArtUrl,
                    contentDescription = stringResource(R.string.cd_album_art_for_title, title),
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist, style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            if (isCurrentSong) {
                PlayingEqIcon(
                    modifier = Modifier
                        .weight(0.1f)
                        .padding(start = 8.dp)
                        .size(width = 18.dp, height = 16.dp),
                    color = colors.primary,
                    isPlaying = isPlaying
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    val isThisSongPlaying = remember(song.id, stablePlayerState.currentSong?.id, stablePlayerState.isPlaying) {
        song.id == stablePlayerState.currentSong?.id
    }

    SongListItemFavs(
        modifier = modifier,
        cardCorners = 0.dp,
        title = song.title,
        artist = song.displayArtist,
        albumArtUrl = song.albumArtUriString,
        isPlaying = stablePlayerState.isPlaying,
        isCurrentSong = song.id == stablePlayerState.currentSong?.id,
        onClick = onClick
    )
}


@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberYourMixTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(636),
                        FontVariation.width(152f),
                        FontVariation.Setting("ROND", 50f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(760),
            fontSize = 64.sp,
            lineHeight = 62.sp
        )
    }
}
