package com.quietrays.tonarc.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.presentation.components.MiniPlayerHeight
import com.quietrays.tonarc.presentation.components.PlaylistBottomSheet
import com.quietrays.tonarc.presentation.components.SmartImage
import com.quietrays.tonarc.presentation.components.SongInfoBottomSheet
import com.quietrays.tonarc.presentation.components.resolveNavBarOccupiedHeight
import com.quietrays.tonarc.presentation.navigation.Screen
import com.quietrays.tonarc.presentation.navigation.navigateSafely
import com.quietrays.tonarc.presentation.navigation.navigateSafelyReplacing
import com.quietrays.tonarc.presentation.viewmodel.FavoriteArtistSongsViewModel
import com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel
import com.quietrays.tonarc.presentation.viewmodel.PlaylistViewModel
import com.quietrays.tonarc.ui.theme.LocalTonarcDarkTheme
import com.quietrays.tonarc.ui.theme.RoundedSans
import com.quietrays.tonarc.utils.formatDuration
import com.quietrays.tonarc.utils.formatSongCount
import kotlinx.collections.immutable.persistentListOf
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteArtistSongsScreen(
    artistName: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: FavoriteArtistSongsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val isMiniPlayerVisible by remember { derivedStateOf { stablePlayerState.currentSong != null } }

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val bottomPadding = if (isMiniPlayerVisible) MiniPlayerHeight + bottomBarHeightDp + 16.dp else bottomBarHeightDp + 16.dp

    var showSongOptionsSheet by remember { mutableStateOf<Song?>(null) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    val totalDurationMs = remember(uiState.songs) {
        uiState.songs.sumOf { it.duration }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 6 && !uiState.isLoadingMore && uiState.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.artistName.ifBlank { artistName },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedSans
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            when {
                uiState.isLoading && uiState.songs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.songs.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No songs found for this artist",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 155.dp),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = bottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header Banner as Full Width Span
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ArtistHeaderSection(
                                artistName = uiState.artistName.ifBlank { artistName },
                                artistImageUrl = uiState.artistImageUrl,
                                songCount = uiState.songs.size,
                                totalDurationMs = totalDurationMs,
                                isFavorite = uiState.isFavorite,
                                onPlayAll = {
                                    if (uiState.songs.isNotEmpty()) {
                                        playerViewModel.showAndPlaySong(
                                            uiState.songs.first(),
                                            uiState.songs,
                                            "Artist • ${uiState.artistName}"
                                        )
                                    }
                                },
                                onShuffle = {
                                    if (uiState.songs.isNotEmpty()) {
                                        val shuffled = uiState.songs.shuffled()
                                        playerViewModel.showAndPlaySong(
                                            shuffled.first(),
                                            shuffled,
                                            "Artist Shuffle • ${uiState.artistName}"
                                        )
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite() }
                            )
                        }

                        // Songs Grid Cards
                        itemsIndexed(
                            items = uiState.songs,
                            key = { _, song -> song.id }
                        ) { _, song ->
                            val isCurrentlyPlaying = stablePlayerState.currentSong?.id == song.id
                            ArtistSongGridCard(
                                song = song,
                                isPlaying = isCurrentlyPlaying && stablePlayerState.isPlaying,
                                isCurrentSong = isCurrentlyPlaying,
                                onClick = {
                                    playerViewModel.showAndPlaySong(
                                        song,
                                        uiState.songs,
                                        "Artist • ${uiState.artistName}"
                                    )
                                },
                                onMoreClick = {
                                    showSongOptionsSheet = song
                                }
                            )
                        }

                        // Loading more footer
                        if (uiState.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Song Options Bottom Sheet
    showSongOptionsSheet?.let { song ->
        val isFavorite = favoriteSongIds.contains(song.id)
        SongInfoBottomSheet(
            song = song,
            isFavorite = isFavorite,
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = { showSongOptionsSheet = null },
            onPlaySong = {
                playerViewModel.showAndPlaySong(
                    song,
                    uiState.songs,
                    "Artist • ${uiState.artistName}"
                )
                showSongOptionsSheet = null
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(song)
                showSongOptionsSheet = null
            },
            onAddNextToQueue = {
                playerViewModel.addSongNextToQueue(song)
                showSongOptionsSheet = null
            },
            onAddToPlayList = {
                showPlaylistBottomSheet = true
            },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                showSongOptionsSheet = null
                navController.navigateSafelyReplacing(
                    route = Screen.AlbumDetail.createRoute(song.albumId),
                    patternToPop = Screen.AlbumDetail.route
                )
            },
            onNavigateToArtist = {
                showSongOptionsSheet = null
                navController.navigateSafelyReplacing(
                    route = Screen.ArtistDetail.createRoute(song.artistId),
                    patternToPop = Screen.ArtistDetail.route
                )
            },
            onNavigateToGenre = {
                showSongOptionsSheet = null
                val encodedGenre = song.genre?.let { java.net.URLEncoder.encode(it, "UTF-8") }
                if (encodedGenre != null) {
                    navController.navigateSafely(Screen.GenreDetail.createRoute(encodedGenre))
                }
            },
            onEditSong = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
            removeFromListTrigger = { showSongOptionsSheet = null }
        )
    }

    if (showPlaylistBottomSheet) {
        val selectedSong = showSongOptionsSheet
        if (selectedSong != null) {
            val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = persistentListOf(selectedSong),
                onDismiss = { showPlaylistBottomSheet = false },
                bottomBarHeight = bottomBarHeightDp,
                playerViewModel = playerViewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistHeaderSection(
    artistName: String,
    artistImageUrl: String?,
    songCount: Int,
    totalDurationMs: Long,
    isFavorite: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artist Avatar
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(AbsoluteSmoothCornerShape(36.dp, 60))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!artistImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artistImageUrl,
                    contentDescription = artistName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Artist Name
        Text(
            text = artistName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedSans,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle (Songs & Duration)
        Text(
            text = "${formatSongCount(songCount)} • ${formatDuration(totalDurationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f, fill = false),
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play",
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = RoundedSans
                )
            }

            FilledTonalButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f, fill = false),
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Shuffle",
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = RoundedSans
                )
            }

            FilledTonalIconButton(
                onClick = onToggleFavorite,
                shape = AbsoluteSmoothCornerShape(16.dp, 60)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArtistSongGridCard(
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(16.dp, 60))
            .clickable(onClick = onClick),
        shape = AbsoluteSmoothCornerShape(16.dp, 60),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSong) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Square Album Art Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(AbsoluteSmoothCornerShape(12.dp, 60))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Playing Indicator Overlay
                if (isCurrentSong) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Song Title & More Options Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.album.ifBlank { formatDuration(song.duration) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Song options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
