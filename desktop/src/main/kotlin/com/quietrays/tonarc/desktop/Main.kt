package com.quietrays.tonarc.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.quietrays.tonarc.desktop.model.DesktopNavDestination
import com.quietrays.tonarc.desktop.model.InspectorTab
import com.quietrays.tonarc.desktop.state.DesktopAppController
import com.quietrays.tonarc.desktop.state.DesktopUiState
import com.quietrays.tonarc.desktop.theme.TonarcDesktopTheme
import com.quietrays.tonarc.desktop.ui.BottomDesktopPlayerBar
import com.quietrays.tonarc.desktop.ui.CompactBottomNavBar
import com.quietrays.tonarc.desktop.ui.DesktopInspectorPane
import com.quietrays.tonarc.desktop.ui.DesktopSidebar
import com.quietrays.tonarc.desktop.ui.DetailScreen
import com.quietrays.tonarc.desktop.ui.EqualizerScreen
import com.quietrays.tonarc.desktop.ui.ExpandedPlayerModalOverlay
import com.quietrays.tonarc.desktop.ui.HomeScreen
import com.quietrays.tonarc.desktop.ui.LibraryScreen
import com.quietrays.tonarc.desktop.ui.MediumNavigationRail
import com.quietrays.tonarc.desktop.ui.PlaylistsScreen
import com.quietrays.tonarc.desktop.ui.SearchScreen
import com.quietrays.tonarc.desktop.ui.SettingsScreen
import com.quietrays.tonarc.desktop.ui.pickDirectoryNative
import com.quietrays.tonarc.desktop.ui.rememberTonarcLogoBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.awt.Dimension

fun main() = application {
    val controller = remember { DesktopAppController() }
    val uiState by controller.uiState.collectAsState()
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 820.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    val iconBitmap = rememberTonarcLogoBitmap()

    setSingletonImageLoaderFactory { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }))
            }
            .build()
    }

    Window(
        onCloseRequest = {
            controller.shutdown()
            exitApplication()
        },
        state = windowState,
        title = uiState.currentTrack?.let { "${it.title} — ${it.artist} • Tonarc" } ?: "Tonarc",
        icon = iconBitmap?.let { BitmapPainter(it) }
    ) {
        DisposableEffect(window) {
            window.minimumSize = Dimension(460, 560)
            onDispose {}
        }

        TonarcDesktopTheme(themeMode = uiState.themeMode) {
            TonarcDesktopRoot(
                controller = controller,
                state = uiState
            )
        }
    }
}

@Composable
fun TonarcDesktopRoot(
    controller: DesktopAppController,
    state: DesktopUiState
) {
    val scope = rememberCoroutineScope()

    val openFolderPicker: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            pickDirectoryNative()?.let { path ->
                controller.addMusicFolder(path)
            }
        }
    }

    LaunchedEffect(state.statusMessage) {
        if (state.statusMessage != null) {
            delay(3500)
            controller.dismissStatusMessage()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.isCtrlPressed && event.key == Key.F -> {
                        controller.focusSearch()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.O -> {
                        openFolderPicker()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionRight -> {
                        controller.playNext()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionLeft -> {
                        controller.playPrevious()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionUp -> {
                        controller.adjustVolume(5)
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionDown -> {
                        controller.adjustVolume(-5)
                        true
                    }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.isCtrlPressed) return@onKeyEvent false
                when (event.key) {
                    Key.Spacebar -> {
                        controller.togglePlayPause()
                        true
                    }
                    Key.DirectionRight -> {
                        controller.seekRelative(5_000L)
                        true
                    }
                    Key.DirectionLeft -> {
                        controller.seekRelative(-5_000L)
                        true
                    }
                    Key.M -> {
                        controller.toggleMute()
                        true
                    }
                    Key.L -> {
                        controller.toggleInspectorPane(InspectorTab.LYRICS)
                        true
                    }
                    Key.Q -> {
                        controller.toggleInspectorPane(InspectorTab.QUEUE)
                        true
                    }
                    else -> false
                }
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 1040.dp
            val isMedium = maxWidth >= 640.dp && maxWidth < 1040.dp
            val isCompact = maxWidth < 640.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (isExpanded) {
                            DesktopSidebar(
                                currentDestination = state.currentDestination,
                                expanded = state.sidebarExpanded,
                                localTrackCount = state.localTracks.size,
                                onSelectDestination = controller::navigate,
                                onToggleExpanded = controller::toggleSidebarExpanded,
                                onAddMusicFolder = openFolderPicker
                            )
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        } else if (isMedium) {
                            MediumNavigationRail(
                                currentDestination = state.currentDestination,
                                onSelectDestination = controller::navigate,
                                onAddMusicFolder = openFolderPicker
                            )
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            MainContentHost(
                                controller = controller,
                                state = state,
                                onAddFolder = openFolderPicker
                            )

                            if (isExpanded) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 14.dp, end = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { controller.toggleInspectorPane(InspectorTab.LYRICS) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Lyrics,
                                            contentDescription = "Toggle Lyrics (L)",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = { controller.toggleInspectorPane(InspectorTab.QUEUE) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = "Toggle Queue (Q)",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = { controller.toggleInspectorPane(null) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ViewSidebar,
                                            contentDescription = "Toggle Now Playing Inspector",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            this@Row.AnimatedVisibility(
                                visible = state.statusMessage != null,
                                enter = fadeIn() + slideInVertically { it },
                                exit = fadeOut() + slideOutVertically { it },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                            ) {
                                state.statusMessage?.let { msg ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.inverseSurface,
                                        shape = RoundedCornerShape(14.dp),
                                        shadowElevation = 6.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = msg,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.inverseOnSurface
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(
                                                onClick = controller::dismissStatusMessage,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = "Dismiss",
                                                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isExpanded && state.showInspectorPane) {
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            DesktopInspectorPane(
                                state = state,
                                onSelectTab = controller::selectInspectorTab,
                                onTogglePlayPause = controller::togglePlayPause,
                                onNext = { controller.playNext() },
                                onPrevious = controller::playPrevious,
                                onSeek = controller::seekTo,
                                onToggleShuffle = controller::toggleShuffle,
                                onCycleRepeat = controller::cycleRepeatMode,
                                onVolumeChange = controller::setVolume,
                                onToggleMute = controller::toggleMute,
                                onToggleFavorite = controller::toggleFavorite,
                                onJumpToQueueIndex = controller::jumpToQueueIndex,
                                onRemoveFromQueue = controller::removeFromQueue,
                                onClearQueue = controller::clearQueue
                            )
                        }
                    }

                    if (!isExpanded && state.currentTrack != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        BottomDesktopPlayerBar(
                            state = state,
                            isCompact = isCompact,
                            onTogglePlayPause = controller::togglePlayPause,
                            onNext = { controller.playNext() },
                            onPrevious = controller::playPrevious,
                            onVolumeChange = controller::setVolume,
                            onToggleMute = controller::toggleMute,
                            onOpenSheet = { controller.setCompactPlayerSheetVisible(true) }
                        )
                    }

                    if (isCompact) {
                        CompactBottomNavBar(
                            currentDestination = state.currentDestination,
                            onSelectDestination = controller::navigate
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isExpanded && state.showCompactPlayerSheet && state.currentTrack != null,
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 3 }
                ) {
                    ExpandedPlayerModalOverlay(
                        state = state,
                        onDismiss = { controller.setCompactPlayerSheetVisible(false) },
                        onSelectTab = controller::selectInspectorTab,
                        onTogglePlayPause = controller::togglePlayPause,
                        onNext = { controller.playNext() },
                        onPrevious = controller::playPrevious,
                        onSeek = controller::seekTo,
                        onToggleShuffle = controller::toggleShuffle,
                        onCycleRepeat = controller::cycleRepeatMode,
                        onVolumeChange = controller::setVolume,
                        onToggleMute = controller::toggleMute,
                        onToggleFavorite = controller::toggleFavorite,
                        onJumpToQueueIndex = controller::jumpToQueueIndex,
                        onRemoveFromQueue = controller::removeFromQueue,
                        onClearQueue = controller::clearQueue
                    )
                }
            }
        }
    }
}

@Composable
private fun MainContentHost(
    controller: DesktopAppController,
    state: DesktopUiState,
    onAddFolder: () -> Unit
) {
    val detail = state.detailPage
    if (detail != null) {
        DetailScreen(
            detail = detail,
            state = state,
            onBack = controller::closeDetail,
            onPlayTrack = controller::playTrack,
            onPlayNext = controller::playNextInQueue,
            onAddToQueue = controller::addToQueue,
            onToggleFavorite = controller::toggleFavorite,
            onAddToPlaylist = { pl, track -> controller.addTrackToPlaylist(pl.id, track) }
        )
        return
    }

    when (state.currentDestination) {
        DesktopNavDestination.HOME -> HomeScreen(
            state = state,
            onPlayTrack = controller::playTrack,
            onPlayNext = controller::playNextInQueue,
            onAddToQueue = controller::addToQueue,
            onToggleFavorite = controller::toggleFavorite,
            onAddToPlaylist = { pl, track -> controller.addTrackToPlaylist(pl.id, track) },
            onOpenAlbum = controller::openAlbum,
            onOpenPlaylist = controller::openPlaylist,
            onRefreshHome = controller::loadHomeShelves,
            onAddMusicFolder = onAddFolder,
            onResolveTrack = controller::resolveTrackById
        )
        DesktopNavDestination.SEARCH -> SearchScreen(
            state = state,
            onQueryChange = controller::updateSearchQuery,
            onFilterChange = controller::setSearchFilter,
            onPlayTrack = controller::playTrack,
            onPlayNext = controller::playNextInQueue,
            onAddToQueue = controller::addToQueue,
            onToggleFavorite = controller::toggleFavorite,
            onAddToPlaylist = { pl, track -> controller.addTrackToPlaylist(pl.id, track) },
            onOpenAlbum = controller::openAlbum,
            onOpenArtist = controller::openArtist,
            onOpenPlaylist = controller::openPlaylist
        )
        DesktopNavDestination.LIBRARY -> LibraryScreen(
            state = state,
            onSelectTab = controller::selectLibraryTab,
            onSelectSort = controller::selectLibrarySort,
            onRescan = controller::rescanLibrary,
            onAddFolder = onAddFolder,
            onRemoveFolder = controller::removeMusicFolder,
            onPlayTrack = controller::playTrack,
            onPlayNext = controller::playNextInQueue,
            onAddToQueue = controller::addToQueue,
            onToggleFavorite = controller::toggleFavorite,
            onAddToPlaylist = { pl, track -> controller.addTrackToPlaylist(pl.id, track) },
            onOpenAlbum = controller::openAlbum,
            onOpenArtist = controller::openArtist,
            onResolveTrack = controller::resolveTrackById
        )
        DesktopNavDestination.PLAYLISTS -> PlaylistsScreen(
            state = state,
            onCreatePlaylist = { name -> controller.createPlaylist(name) },
            onOpenPlaylist = controller::openPlaylist,
            onDeletePlaylist = controller::deletePlaylist
        )
        DesktopNavDestination.EQUALIZER -> EqualizerScreen(
            state = state,
            onSelectPreset = controller::selectEqPreset,
            onBandChange = controller::setEqBand,
            onReplayGainChange = controller::setReplayGain,
            onSpeedChange = controller::setPlaybackSpeed
        )
        DesktopNavDestination.SETTINGS -> SettingsScreen(
            state = state,
            onThemeModeChange = controller::setThemeMode,
            onAddMusicFolder = onAddFolder,
            onRemoveMusicFolder = controller::removeMusicFolder,
            onRescanLibrary = controller::rescanLibrary,
            onUpdateCookies = controller::updateYouTubeCookies
        )
    }
}
