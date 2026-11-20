package com.quietrays.tonarc.presentation.screens

import com.quietrays.tonarc.presentation.navigation.navigateSafely
import com.quietrays.tonarc.presentation.navigation.navigateSafelyReplacing

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryMusic
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.quietrays.tonarc.presentation.components.HomeSectionHeader
import com.quietrays.tonarc.presentation.components.HorizontalAlbumCarouselSection
import com.quietrays.tonarc.presentation.components.HorizontalAlbumCarouselSkeletonSection
import com.quietrays.tonarc.presentation.components.HorizontalArtistCarouselSection
import com.quietrays.tonarc.presentation.components.HorizontalPlaylistCarouselSection
import com.quietrays.tonarc.presentation.components.HorizontalSongCarouselSection
import com.quietrays.tonarc.presentation.components.MiniPlayerHeight
import com.quietrays.tonarc.presentation.components.QuickPicksShelfSection
import com.quietrays.tonarc.presentation.components.QuickPicksShelfSkeletonSection
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
import com.quietrays.tonarc.data.network.youtube.YouTubeGenreCatalog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.quietrays.tonarc.ui.theme.RoundedSans
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

    val displayRecentlyPlayedSongs = remember(filteredRecentlyPlayedSongs, yourMixSongs, keepListeningSongs) {
        if (filteredRecentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
            filteredRecentlyPlayedSongs.toImmutableList()
        } else {
            val fallbackSongs = (filteredRecentlyPlayedSongs.map { it.song } + keepListeningSongs + yourMixSongs)
                .distinctBy { it.id }
                .take(10)
            fallbackSongs.map {
                com.quietrays.tonarc.presentation.model.RecentlyPlayedSongUiModel(
                    song = it,
                    lastPlayedTimestamp = System.currentTimeMillis()
                )
            }.toImmutableList()
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
    val scrollThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
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

    val ambientGradientAlpha by animateFloatAsState(
        targetValue = if (isScrolledPastThreshold.value) 0f else 1f,
        animationSpec = tween(durationMillis = 320),
        label = "home_ambient_gradient_alpha"
    )
    val basePattern = settingsUiState.collagePattern
    val isAutoRotate = settingsUiState.collageAutoRotate
    val collagePatterns = remember { CollagePattern.entries }
    val activeCollagePattern = if (isAutoRotate) {
        var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
        LaunchedEffect(Unit) { rotationIndex++ }
        remember(rotationIndex) {
            collagePatterns[rotationIndex.coerceAtLeast(0) % collagePatterns.size]
        }
    } else {
        basePattern
    }
    val heroYouTubeSongs = remember(quickPicks, yourMixSongs) {
        if (quickPicks.isNotEmpty()) quickPicks else yourMixSongs
    }
    val inlineFavoriteArtistSections = remember(favoriteArtistSections) {
        favoriteArtistSections.take(2)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f * ambientGradientAlpha),
                            0.45f to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f * ambientGradientAlpha),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                HomeGradientTopBar(
                    onNavigationIconClick = {
                        navController.navigateSafely(Screen.Settings.route)
                    },
                    onProfileClick = {
                        navController.navigateSafely(Screen.Accounts.route)
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = paddingValuesParent.calculateBottomPadding()
                                + 38.dp + bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    item(
                        key = "home_filter_chips_row",
                        contentType = "home_filter_chips_row"
                    ) {
                        HomeSourceFilterRow(
                            selectedFilter = selectedHomeFilter,
                            onFilterSelected = { selectedHomeFilter = it }
                        )
                    }

                    if (selectedHomeFilter == HomeFilter.YOUTUBE_MUSIC) {
                        if (heroYouTubeSongs.isNotEmpty()) {
                            item(
                                key = "youtube_mix_hero",
                                contentType = "your_mix_hero"
                            ) {
                                YourMixHeroCard(
                                    eyebrow = "YOUTUBE MUSIC",
                                    title = "YouTube Mix",
                                    subtitle = "Instant radio & picks tailored for you",
                                    songs = heroYouTubeSongs,
                                    pattern = activeCollagePattern,
                                    isShuffleEnabled = isShuffleEnabled,
                                    onPlayShuffled = {
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = heroYouTubeSongs,
                                            queueName = "YouTube Mix",
                                            startAtZero = true
                                        )
                                    },
                                    onSongClick = { song ->
                                        playerViewModel.playSongWithSmartQueue(song, "YouTube Mix")
                                    }
                                )
                            }
                        } else if (isHomeRecommendationsLoading) {
                            item(
                                key = "your_mix_placeholder",
                                contentType = "your_mix_placeholder"
                            ) {
                                YourMixLoadingPlaceholder()
                            }
                        }
                    } else {
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
                                key = "your_mix_hero",
                                contentType = "your_mix_hero"
                            ) {
                                YourMixHeroCard(
                                    eyebrow = if (selectedHomeFilter == HomeFilter.LOCAL_ONLY) "LOCAL LIBRARY" else "PERSONALIZED MIX",
                                    title = stringResource(R.string.home_your_mix_title),
                                    subtitle = yourMixSong,
                                    songs = yourMixSongs,
                                    pattern = activeCollagePattern,
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
                                    },
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

                    if (displayRecentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                        item(
                            key = "recently_played_section",
                            contentType = "recently_played_section"
                        ) {
                            val recentlyPlayedFilteredQueue = remember(displayRecentlyPlayedSongs) {
                                displayRecentlyPlayedSongs.map { it.song }.toImmutableList()
                            }
                            RecentlyPlayedSection(
                                songs = displayRecentlyPlayedSongs,
                                onSongClick = { song ->
                                    if (recentlyPlayedFilteredQueue.isNotEmpty()) {
                                        playerViewModel.playSongs(
                                            songsToPlay = recentlyPlayedFilteredQueue,
                                            startSong = song,
                                            queueName = "Quick Access / Recently Played"
                                        )
                                    }
                                },
                                onOpenAllClick = {
                                    navController.navigateSafely(Screen.RecentlyPlayed.route)
                                },
                                themeStateHolder = playerViewModel.themeStateHolder,
                                currentSongId = currentSong?.id,
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            )
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
                                QuickPicksShelfSection(
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
                                QuickPicksShelfSkeletonSection(
                                    title = "Quick Picks",
                                    subtitle = "Instant radio & top tracks from YouTube Music"
                                )
                            }
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

                    if (inlineFavoriteArtistSections.isNotEmpty()) {
                        items(
                            items = inlineFavoriteArtistSections,
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

                    item(
                        key = "genres_and_moods_section",
                        contentType = "genres_and_moods_section"
                    ) {
                        GenresAndMoodsSection(
                            onGenreClick = { genreTitle ->
                                navController.navigateSafely(
                                    Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(genreTitle, "UTF-8"))
                                )
                            }
                        )
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
private fun HomeSourceFilterRow(
    selectedFilter: HomeFilter,
    onFilterSelected: (HomeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = remember {
        listOf(
            Triple(HomeFilter.ALL, "All", Icons.Rounded.AutoAwesome),
            Triple(HomeFilter.YOUTUBE_MUSIC, "YouTube Music", Icons.Rounded.MusicNote),
            Triple(HomeFilter.LOCAL_ONLY, "Local Only", Icons.Rounded.LibraryMusic)
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(filters, key = { it.first.name }) { (filter, label, icon) ->
            HomeSourceFilterPill(
                label = label,
                icon = icon,
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

@Composable
private fun HomeSourceFilterPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh.copy(alpha = 0.85f),
        animationSpec = tween(durationMillis = 220),
        label = "source_filter_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "source_filter_content"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 220),
        label = "source_filter_border"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun YourMixHeroCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    songs: ImmutableList<Song>,
    pattern: CollagePattern,
    isShuffleEnabled: Boolean,
    onPlayShuffled: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val titleStyle = rememberYourMixHeroTitleStyle()
    val shuffleContainerColor by animateColorAsState(
        targetValue = if (isShuffleEnabled) colors.primary else colors.primaryContainer,
        animationSpec = tween(durationMillis = 240),
        label = "hero_shuffle_container"
    )
    val shuffleContentColor by animateColorAsState(
        targetValue = if (isShuffleEnabled) colors.onPrimary else colors.onPrimaryContainer,
        animationSpec = tween(durationMillis = 240),
        label = "hero_shuffle_content"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = ShapeCache.smooth28,
        color = colors.surfaceContainerLow,
        border = BorderStroke(
            width = 0.75.dp,
            color = colors.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.primaryContainer.copy(alpha = 0.26f),
                            colors.tertiaryContainer.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = eyebrow,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp
                            ),
                            color = colors.primary,
                            maxLines = 1
                        )
                        Text(
                            text = title,
                            style = titleStyle,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        onClick = onPlayShuffled,
                        shape = ShapeCache.smooth20,
                        color = shuffleContainerColor,
                        contentColor = shuffleContentColor,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 46.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_shuffle_24),
                                contentDescription = stringResource(R.string.cd_shuffle_play),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Shuffle",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                AlbumArtCollage(
                    modifier = Modifier.fillMaxWidth(),
                    songs = songs,
                    padding = 10.dp,
                    height = 248.dp,
                    pattern = pattern,
                    onSongClick = onSongClick
                )
            }
        }
    }
}

@Composable
private fun YourMixLoadingPlaceholder() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = ShapeCache.smooth28,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 0.75.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(96.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .width(164.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .width(128.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
                ShimmerBox(
                    modifier = Modifier
                        .width(96.dp)
                        .height(44.dp)
                        .clip(ShapeCache.smooth20),
                    shape = ShapeCache.smooth20
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(228.dp)
                    .clip(ShapeCache.smooth20),
                contentAlignment = Alignment.Center
            ) {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = ShapeCache.smooth20
                )
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                    modifier = Modifier.size(64.dp)
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = ShapeCache.smooth28,
        color = colors.surfaceContainerLow,
        border = BorderStroke(
            width = 0.75.dp,
            color = colors.outlineVariant.copy(alpha = 0.26f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 248.dp)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = ShapeCache.expressiveHero,
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_empty_placeholder_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Bold
                        ),
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
                    shape = ShapeCache.smooth20
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


@Composable
fun GenresAndMoodsSection(
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            YouTubeGenreCatalog.findGenreOrMood("pop"),
            YouTubeGenreCatalog.findGenreOrMood("rock"),
            YouTubeGenreCatalog.findGenreOrMood("lofi"),
            YouTubeGenreCatalog.findGenreOrMood("classical"),
            YouTubeGenreCatalog.findGenreOrMood("hiphop"),
            YouTubeGenreCatalog.findGenreOrMood("jazz"),
            YouTubeGenreCatalog.findGenreOrMood("electronic"),
            YouTubeGenreCatalog.findGenreOrMood("chill")
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeSectionHeader(
            title = "Moods & Genres",
            subtitle = "Explore music by mood, style & genre"
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { genre ->
                val baseColor = Color(genre.colorHex)
                val darkAccent = Color(
                    red = (baseColor.red * 0.58f).coerceIn(0f, 1f),
                    green = (baseColor.green * 0.58f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 0.68f).coerceIn(0f, 1f),
                    alpha = 1f
                )

                Surface(
                    onClick = { onGenreClick(genre.title) },
                    modifier = Modifier.size(width = 166.dp, height = 96.dp),
                    shape = ShapeCache.smooth20,
                    color = baseColor,
                    contentColor = Color.White
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(baseColor, darkAccent)
                                )
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 18.dp, y = (-18).dp)
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                        )
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.20f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(24.dp)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 10.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = genre.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp
                                ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = genre.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = RoundedSans,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            genre.subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White.copy(alpha = 0.84f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberYourMixHeroTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(680),
                        FontVariation.width(128f),
                        FontVariation.Setting("ROND", 50f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(740),
            fontSize = 34.sp,
            lineHeight = 36.sp
        )
    }
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
