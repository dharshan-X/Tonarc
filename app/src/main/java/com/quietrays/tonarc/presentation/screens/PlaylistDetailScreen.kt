package com.quietrays.tonarc.presentation.screens

import com.quietrays.tonarc.presentation.navigation.navigateSafely
import com.quietrays.tonarc.presentation.navigation.navigateSafelyReplacing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.PlaylistShapeType
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.model.SortOption
import com.quietrays.tonarc.data.model.isSmartPlaylist
import com.quietrays.tonarc.presentation.components.LibrarySortBottomSheet
import com.quietrays.tonarc.presentation.components.MiniPlayerHeight
import com.quietrays.tonarc.presentation.components.PlaylistBottomSheet
import com.quietrays.tonarc.presentation.components.PlaylistHeroSection
import com.quietrays.tonarc.presentation.components.PlaylistSongTile
import com.quietrays.tonarc.presentation.components.SongInfoBottomSheet
import com.quietrays.tonarc.presentation.components.SongPickerBottomSheet
import com.quietrays.tonarc.presentation.components.rememberModalSheetState
import com.quietrays.tonarc.presentation.components.resolveNavBarOccupiedHeight
import com.quietrays.tonarc.presentation.components.resolvePlaylistTileShape
import com.quietrays.tonarc.presentation.navigation.Screen
import com.quietrays.tonarc.presentation.utils.LocalAppHapticsConfig
import com.quietrays.tonarc.presentation.utils.performAppCompatHapticFeedback
import com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel
import com.quietrays.tonarc.presentation.viewmodel.PlaylistSongsOrderMode
import com.quietrays.tonarc.presentation.viewmodel.PlaylistViewModel
import com.quietrays.tonarc.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.quietrays.tonarc.ui.theme.HideStatusBarEffect
import com.quietrays.tonarc.ui.theme.RoundedSans
import com.quietrays.tonarc.utils.formatSongCount
import com.quietrays.tonarc.utils.formatTotalDuration
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
private fun PlaylistActionChip(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isActive && isDanger -> MaterialTheme.colorScheme.errorContainer
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        isActive && isDanger -> MaterialTheme.colorScheme.onErrorContainer
        isActive -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = RoundedSans
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val hasCurrentSong by remember {
        derivedStateOf { playerStableState.currentSong != null }
    }
    val context = LocalContext.current
    val fallbackPlaylistName = stringResource(R.string.shortcut_playlist_short)
    val sortSongsLabel = stringResource(R.string.presentation_batch_b_sort_songs)
    val moreOptionsLabel = stringResource(R.string.presentation_batch_b_more_options)
    val shuffleLabel = stringResource(R.string.shortcut_shuffle_short)
    val addSongsCd = stringResource(R.string.presentation_batch_b_add_songs)
    val addLabel = stringResource(R.string.presentation_batch_b_add)
    val removeLabel = stringResource(R.string.cd_remove)
    val removeSongsCd = stringResource(R.string.presentation_batch_b_remove_songs)
    val reorderLabel = stringResource(R.string.presentation_batch_b_reorder)
    val reorderSongsCd = stringResource(R.string.presentation_batch_b_reorder_songs)
    val reorderSongCd = stringResource(R.string.presentation_batch_b_reorder_song)
    val playlistEmptyTitle = stringResource(R.string.presentation_batch_b_playlist_empty_title)
    val playlistEmptyFolder = stringResource(R.string.presentation_batch_b_playlist_empty_folder_body)
    val playlistEmptyAddHint = stringResource(R.string.presentation_batch_b_playlist_empty_add_hint)
    val playlistOptionsTitle = stringResource(R.string.presentation_batch_b_playlist_options_title)
    val editPlaylistLabel = stringResource(R.string.presentation_batch_b_edit_playlist)
    val deletePlaylistLabel = stringResource(R.string.presentation_batch_b_delete_playlist)
    val setDefaultTransitionLabel = stringResource(R.string.presentation_batch_b_set_default_transition)
    val exportPlaylistLabel = stringResource(R.string.presentation_batch_b_export_playlist)
    val deletePlaylistConfirmTitle = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_title)
    val deletePlaylistConfirmBody = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_body)
    val sortSheetTitle = stringResource(R.string.presentation_batch_b_sort_songs)
    val toastAddedToQueue = stringResource(R.string.toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.toast_playing_next)
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val isSmartPlaylist = currentPlaylist?.isSmartPlaylist == true
    val isEditablePlaylist = !isFolderPlaylist && !isSmartPlaylist
    val songsInPlaylist = uiState.currentPlaylistSongs

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isRemoveModeEnabled by remember { mutableStateOf(false) }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val m3uExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportM3u(playlist, it, context)
            }
        }
    }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    HideStatusBarEffect()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var localReorderableSongs by remember(songsInPlaylist) { mutableStateOf(songsInPlaylist) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromIndex = localReorderableSongs.indexOfFirst { it.id == from.key }
            val toIndex = localReorderableSongs.indexOfFirst { it.id == to.key }
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                localReorderableSongs = localReorderableSongs.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
                if (lastMovedFrom == null) {
                    lastMovedFrom = fromIndex
                }
                lastMovedTo = toIndex
            }
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging, isEditablePlaylist) {
        if (isEditablePlaylist && !reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        } else if (!isEditablePlaylist && !reorderableState.isAnyItemDragging) {
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    BackHandler(enabled = isReorderModeEnabled || isRemoveModeEnabled) {
        isReorderModeEnabled = false
        isRemoveModeEnabled = false
    }

    val immutableSongs = remember(songsInPlaylist) { songsInPlaylist.toImmutableList() }
    val hasCurrentSongInPlaylist = remember(localReorderableSongs, playerStableState.currentSong) {
        localReorderableSongs.any { it.id == playerStableState.currentSong?.id }
    }
    val hasCloudSongs = remember(songsInPlaylist) {
        songsInPlaylist.any { com.quietrays.tonarc.data.offline.CloudOfflineRepository.isCloudSong(it) }
    }

    val showCollapsedTopBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
        }
    }

    when {
        uiState.isLoading && currentPlaylist == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        }
        uiState.playlistNotFound -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                FilledTonalIconButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 4.dp),
                    onClick = onBackClick
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.auth_cd_back)
                    )
                }
                Text(stringResource(id = R.string.playlist_not_found))
            }
        }
        currentPlaylist == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        }
        localReorderableSongs.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                PlaylistHeroSection(
                    playlist = currentPlaylist,
                    songs = persistentListOf(),
                    isFolderPlaylist = isFolderPlaylist,
                    isSmartPlaylist = isSmartPlaylist,
                    isPlaying = false,
                    onPlayFabClick = {},
                    onBackClick = onBackClick,
                    onOptionsClick = { showPlaylistOptionsSheet = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            Icons.Filled.MusicOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = playlistEmptyTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = RoundedSans
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val emptyMessage = when {
                            isFolderPlaylist -> playlistEmptyFolder
                            isSmartPlaylist -> stringResource(R.string.presentation_batch_b_playlist_empty_smart_body)
                            else -> playlistEmptyAddHint
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (isEditablePlaylist) {
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { showAddSongsSheet = true },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.presentation_batch_b_add_songs),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = RoundedSans
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = if (hasCurrentSong) bottomBarHeightDp + MiniPlayerHeight + 24.dp else bottomBarHeightDp + 24.dp
                    )
                ) {
                    item(key = "playlist_hero") {
                        PlaylistHeroSection(
                            playlist = currentPlaylist,
                            songs = immutableSongs,
                            isFolderPlaylist = isFolderPlaylist,
                            isSmartPlaylist = isSmartPlaylist,
                            isPlaying = playerStableState.isPlaying && hasCurrentSongInPlaylist,
                            onPlayFabClick = {
                                if (localReorderableSongs.isNotEmpty()) {
                                    if (hasCurrentSongInPlaylist) {
                                        playerViewModel.playPause()
                                    } else {
                                        playerViewModel.playSongs(
                                            localReorderableSongs,
                                            localReorderableSongs.first(),
                                            currentPlaylist.name,
                                            currentPlaylist.id
                                        )
                                        if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                    }
                                }
                            },
                            onBackClick = onBackClick,
                            onOptionsClick = { showPlaylistOptionsSheet = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item(key = "playlist_action_chips") {
                        Spacer(modifier = Modifier.height(28.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp)
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditablePlaylist) {
                                PlaylistActionChip(
                                    label = addLabel,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = addSongsCd,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = { showAddSongsSheet = true }
                                )

                                PlaylistActionChip(
                                    label = reorderLabel,
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.drag_order_icon),
                                            contentDescription = reorderSongsCd,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    isActive = isReorderModeEnabled,
                                    onClick = {
                                        isReorderModeEnabled = !isReorderModeEnabled
                                        if (isReorderModeEnabled) isRemoveModeEnabled = false
                                    }
                                )

                                PlaylistActionChip(
                                    label = removeLabel,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircleOutline,
                                            contentDescription = removeSongsCd,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    isActive = isRemoveModeEnabled,
                                    isDanger = true,
                                    onClick = {
                                        isRemoveModeEnabled = !isRemoveModeEnabled
                                        if (isRemoveModeEnabled) isReorderModeEnabled = false
                                    }
                                )
                            }

                            PlaylistActionChip(
                                label = shuffleLabel,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = shuffleLabel,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    if (localReorderableSongs.isNotEmpty()) {
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = localReorderableSongs,
                                            queueName = currentPlaylist.name,
                                            playlistId = currentPlaylist.id,
                                            startAtZero = true,
                                        )
                                    }
                                }
                            )

                            PlaylistActionChip(
                                label = sortSongsLabel,
                                icon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = sortSongsLabel,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { playerViewModel.showSortingSheet() }
                            )

                            if (hasCloudSongs) {
                                PlaylistActionChip(
                                    label = stringResource(R.string.cloud_album_download),
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDownload,
                                            contentDescription = stringResource(R.string.cloud_album_download),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = { playlistViewModel.downloadPlaylist(songsInPlaylist) }
                                )
                            }
                        }
                    }

                    item(key = "playlist_tracklist_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_tracklist_header),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RoundedSans
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatSongCount(localReorderableSongs.size),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = RoundedSans,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        localReorderableSongs,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "playlist_song" }
                    ) { index, song ->
                        val playbackUiState by remember(song.id, playerViewModel) {
                            playerViewModel.stablePlayerState
                                .map { state ->
                                    val isCurrent = state.currentSong?.id == song.id
                                    LibrarySongPlaybackUiState(
                                        isCurrentSong = isCurrent,
                                        isPlaying = isCurrent && state.isPlaying
                                    )
                                }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = LibrarySongPlaybackUiState())

                        val isFirst = index == 0
                        val isLast = index == localReorderableSongs.lastIndex

                        ReorderableItem(
                            state = reorderableState,
                            key = song.id,
                        ) { isDragging ->
                            val scale by animateFloatAsState(
                                if (isDragging) 1.03f else 1f,
                                label = "scale"
                            )

                            val tileShape = remember(isFirst, isLast) {
                                resolvePlaylistTileShape(isFirst, isLast, largeRadius = 16.dp, smallRadius = 4.dp)
                            }

                            val itemBgColor = when {
                                playbackUiState.isCurrentSong && playbackUiState.isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                playbackUiState.isCurrentSong -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            }

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .clip(tileShape)
                                    .background(itemBgColor)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                            ) {
                                PlaylistSongTile(
                                    song = song,
                                    index = index,
                                    isCurrentSong = playbackUiState.isCurrentSong,
                                    isPlaying = playbackUiState.isPlaying,
                                    isReorderMode = isReorderModeEnabled,
                                    isRemoveMode = isRemoveModeEnabled,
                                    onClick = {
                                        playerViewModel.playSongs(
                                            localReorderableSongs,
                                            song,
                                            currentPlaylist.name,
                                            currentPlaylist.id
                                        )
                                    },
                                    onRemoveClick = {
                                        if (isEditablePlaylist) {
                                            currentPlaylist.let {
                                                playlistViewModel.removeSongFromPlaylist(it.id, song.id)
                                            }
                                        }
                                    },
                                    onMoreOptionsClick = stableOnMoreOptionsClick,
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstantsCompat.GESTURE_START
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        performAppCompatHapticFeedback(
                                                            view,
                                                            appHapticsConfig,
                                                            HapticFeedbackConstantsCompat.GESTURE_END
                                                        )
                                                    }
                                                )
                                                .size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = reorderSongCd,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    showDivider = false
                                )
                            }
                        }
                    }
                }

                // Pinned Collapsed Top App Bar
                AnimatedVisibility(
                    visible = showCollapsedTopBar,
                    enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(250)) { -it / 2 },
                    exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(250)) { -it / 2 },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 4.dp
                    ) {
                        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                        val collapsedTopPadding = remember(statusBarTop, cutoutTop) {
                            when {
                                statusBarTop > 0.dp -> statusBarTop
                                cutoutTop > 0.dp -> cutoutTop
                                else -> 0.dp
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = collapsedTopPadding)
                                .height(64.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                onClick = {
                                    if (isReorderModeEnabled || isRemoveModeEnabled) {
                                        isReorderModeEnabled = false
                                        isRemoveModeEnabled = false
                                    } else {
                                        onBackClick()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.auth_cd_back)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = currentPlaylist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = RoundedSans
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        R.string.presentation_batch_f_status_bullet_step,
                                        formatSongCount(songsInPlaylist.size),
                                        formatTotalDuration(songsInPlaylist)
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = RoundedSans),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isReorderModeEnabled || isRemoveModeEnabled) {
                                    FilledTonalButton(
                                        onClick = {
                                            isReorderModeEnabled = false
                                            isRemoveModeEnabled = false
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.action_done),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = RoundedSans
                                            )
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { playerViewModel.showSortingSheet() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                                            contentDescription = sortSongsLabel,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (hasCloudSongs) {
                                        IconButton(
                                            onClick = { playlistViewModel.downloadPlaylist(songsInPlaylist) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudDownload,
                                                contentDescription = stringResource(R.string.cloud_album_download),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (!isFolderPlaylist) {
                                        FilledTonalIconButton(
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            onClick = { showPlaylistOptionsSheet = true }
                                        ) {
                                            Icon(Icons.Filled.MoreVert, moreOptionsLabel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSongsSheet && currentPlaylist != null && isEditablePlaylist) {
        SongPickerBottomSheet(
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsSheet = false
            }
        )
    }
    if (showPlaylistOptionsSheet && !isFolderPlaylist) {
        val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = playlistOptionsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    currentPlaylist?.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_edit_24),
                    label = editPlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showEditPlaylistDialog = true
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_delete_24),
                    label = deletePlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showDeleteConfirmation = true
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.outline_graph_1_24),
                    label = setDefaultTransitionLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        navController.navigateSafely(Screen.EditTransition.createRoute(playlistId))
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_attach_file_24),
                    label = exportPlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        val sanitizedName = PlaylistViewModel.sanitizeFileName(
                            currentPlaylist?.name ?: fallbackPlaylistName
                        )
                        m3uExportLauncher.launch("$sanitizedName.m3u")
                    }
                )
                if (currentPlaylist?.source == "YOUTUBE") {
                    PlaylistActionItem(
                        icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.PlaylistAdd),
                        label = stringResource(R.string.youtube_save_to_local),
                        onClick = {
                            showPlaylistOptionsSheet = false
                            currentPlaylist.name.let { name ->
                                playlistViewModel.cloneYouTubePlaylistToLocal(
                                    playlistName = name,
                                    songs = songsInPlaylist
                                )
                            }
                        }
                    )
                    PlaylistActionItem(
                        icon = rememberVectorPainter(Icons.Rounded.Refresh),
                        label = stringResource(R.string.youtube_refresh_playlist),
                        onClick = {
                            showPlaylistOptionsSheet = false
                            playlistViewModel.loadPlaylistDetails(playlistId)
                        }
                    )
                }
            }
        }
    }

    if (showEditPlaylistDialog && currentPlaylist != null) {
        val initialShapeType = try {
            currentPlaylist.coverShapeType?.let { PlaylistShapeType.valueOf(it) } ?: PlaylistShapeType.Circle
        } catch (e: Exception) {
            PlaylistShapeType.Circle
        }

        EditPlaylistDialog(
            visible = showEditPlaylistDialog,
            currentName = currentPlaylist.name,
            currentImageUri = currentPlaylist.coverImageUri,
            currentColor = currentPlaylist.coverColorArgb,
            currentIconName = currentPlaylist.coverIconName,
            currentShapeType = initialShapeType,
            currentShapeDetail1 = currentPlaylist.coverShapeDetail1,
            currentShapeDetail2 = currentPlaylist.coverShapeDetail2,
            currentShapeDetail3 = currentPlaylist.coverShapeDetail3,
            currentShapeDetail4 = currentPlaylist.coverShapeDetail4,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4 ->
                playlistViewModel.updatePlaylistParameters(
                    playlistId = currentPlaylist.id,
                    name = name,
                    coverImageUri = imageUri,
                    coverColor = color,
                    coverIcon = icon,
                    cropScale = scale,
                    cropPanX = panX,
                    cropPanY = panY,
                    coverShapeType = shapeType,
                    coverShapeDetail1 = d1,
                    coverShapeDetail2 = d2,
                    coverShapeDetail3 = d3,
                    coverShapeDetail4 = d4
                )
                showEditPlaylistDialog = false
            }
        )
    }
    if (showDeleteConfirmation && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(deletePlaylistConfirmTitle) },
            text = {
                Text(deletePlaylistConfirmBody)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.deletePlaylist(currentPlaylist.id)
                        onDeletePlayListClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.delete_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf {
                currentSong?.let {
                    favoriteIds.contains(
                        it.id
                    )
                }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onStartRadio = {
                    playerViewModel.playInstantRadio(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastAddedToQueue)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastPlayingNext)
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafelyReplacing(
                            route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                            patternToPop = Screen.GenreDetail.route
                        )
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                removeFromListTrigger = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, currentSong.id)
                }
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = persistentListOf(currentSong),
                    onDismiss = {
                        showPlaylistBottomSheet = false
                    },
                    currentPlaylistId = playlistId,
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()

    if (isSortSheetVisible) {
        val isManualMode = uiState.playlistSongsOrderMode is PlaylistSongsOrderMode.Manual
        val rawOption = uiState.currentPlaylistSongsSortOption
        val currentSortOption = if (isManualMode) {
            SortOption.SongDefaultOrder
        } else if (currentPlaylist != null) {
            rawOption
        } else {
            SortOption.SongTitleAZ
        }

        val songSortOptions = persistentListOf(
            SortOption.SongDefaultOrder,
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )

        LibrarySortBottomSheet(
            title = sortSheetTitle,
            options = songSortOptions,
            selectedOption = currentSortOption,
            onDismiss = { playerViewModel.hideSortingSheet() },
            onOptionSelected = { option ->
                 playlistViewModel.sortPlaylistSongs(option)
                 playerViewModel.hideSortingSheet()
                 scope.launch {
                     kotlinx.coroutines.delay(100)
                     listState.animateScrollToItem(0)
                 }
            },
            onDirectionToggle = { option ->
                playlistViewModel.sortPlaylistSongs(option)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(0)
                }
            },
            showViewToggle = false 
        )
    }
}

@Composable
private fun PlaylistActionItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    val renameTitle = stringResource(R.string.presentation_batch_b_rename_playlist_dialog_title)
    val newNameLabel = stringResource(R.string.presentation_batch_b_new_name)
    val renameAction = stringResource(R.string.action_rename)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(renameTitle) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(newNameLabel) },
                shape = CircleShape,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            ) { Text(renameAction, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    )
}
