package com.quietrays.tonarc.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.quietrays.tonarc.desktop.model.DesktopPlaylist
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.EqualizerPreset
import com.quietrays.tonarc.desktop.model.ReplayGainMode
import com.quietrays.tonarc.desktop.model.ThemeMode
import com.quietrays.tonarc.desktop.state.DesktopUiState
import com.quietrays.tonarc.desktop.state.DetailPage

@Composable
fun PlaylistsScreen(
    state: DesktopUiState,
    onCreatePlaylist: (String) -> Unit,
    onOpenPlaylist: (DesktopPlaylist) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(newName)
                        newName = ""
                        showCreateDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.playlists.size} playlists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Playlist")
            }
        }

        Spacer(Modifier.height(18.dp))

        if (state.playlists.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create a hybrid playlist for your local and YouTube Music tracks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 184.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(state.playlists, key = { it.id }) { pl ->
                Box {
                    MediaGridCard(
                        title = pl.name,
                        subtitle = "${pl.trackIds.size} tracks",
                        artworkModel = pl.artworkUrl,
                        fallbackIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        onClick = { onOpenPlaylist(pl) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!pl.isYouTube) {
                        IconButton(
                            onClick = { onDeletePlaylist(pl.id) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    detail: DetailPage,
    state: DesktopUiState,
    onBack: () -> Unit,
    onPlayTrack: (DesktopTrack, List<DesktopTrack>) -> Unit,
    onPlayNext: (DesktopTrack) -> Unit,
    onAddToQueue: (DesktopTrack) -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    onAddToPlaylist: (DesktopPlaylist, DesktopTrack) -> Unit
) {
    val title: String
    val subtitle: String
    val artworkModel: Any?
    val tracks: List<DesktopTrack>
    val isLoading: Boolean
    val fallbackIcon = when (detail) {
        is DetailPage.Album -> Icons.Rounded.Album
        is DetailPage.Artist -> Icons.Rounded.Person
        is DetailPage.Playlist -> Icons.AutoMirrored.Rounded.PlaylistPlay
    }

    when (detail) {
        is DetailPage.Album -> {
            title = detail.album.title
            subtitle = detail.album.artist + (detail.album.year?.let { " • $it" } ?: "")
            artworkModel = detail.album.artworkModel
            tracks = detail.album.tracks
            isLoading = detail.isLoading
        }
        is DetailPage.Artist -> {
            title = detail.artist.name
            subtitle = detail.artist.subtitle ?: "Artist"
            artworkModel = detail.artist.artworkModel
            tracks = detail.artist.tracks
            isLoading = detail.isLoading
        }
        is DetailPage.Playlist -> {
            title = detail.playlist.name
            subtitle = detail.playlist.description.ifBlank { "${detail.tracks.size} tracks" }
            artworkModel = detail.playlist.artworkUrl ?: detail.tracks.firstOrNull()?.displayArtModel
            tracks = detail.tracks
            isLoading = detail.isLoading
        }
    }

    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DesktopArtwork(
                    model = artworkModel,
                    contentDescription = title,
                    modifier = Modifier.size(116.dp),
                    cornerRadius = 18.dp,
                    fallbackIcon = fallbackIcon
                )
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$subtitle • ${tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { tracks.firstOrNull()?.let { onPlayTrack(it, tracks) } },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    onPlayTrack(shuffled.first(), shuffled)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val showAlbum = maxWidth >= 680.dp
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(end = 12.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(tracks, key = { idx, t -> "detail_${idx}_${t.id}" }) { idx, track ->
                        TrackListRow(
                            track = track,
                            isCurrent = state.currentTrack?.id == track.id,
                            isPlaying = state.mpvState.isPlaying,
                            isFavorite = track.id in state.favoriteIds,
                            playlists = state.playlists,
                            showAlbumColumn = showAlbum,
                            indexLabel = (idx + 1).toString(),
                            onPlay = { onPlayTrack(track, tracks) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) }
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    state: DesktopUiState,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onReplayGainChange: (ReplayGainMode) -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "10-Band Equalizer & DSP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time mpv audio filter pipeline (${state.eqPresetName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { onSelectPreset(EqualizerPreset.PRESETS.first()) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset Flat")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqualizerPreset.PRESETS.forEach { preset ->
                FilterChip(
                    selected = state.eqPresetName == preset.name,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.name) }
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EqualizerPreset.BAND_LABELS.forEachIndexed { index, label ->
                    val gain = state.eqBands.getOrElse(index) { 0f }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "%+.1f".format(gain),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        VerticalSlider(
                            value = gain,
                            onValueChange = { onBandChange(index, it) },
                            valueRange = -12f..12f,
                            modifier = Modifier.height(170.dp).width(34.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "ReplayGain Normalization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ReplayGainMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = state.replayGain == mode,
                            onClick = { onReplayGainChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(idx, ReplayGainMode.entries.size),
                            label = { Text(mode.label) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "%.2fx".format(state.playbackSpeed),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.playbackSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    steps = 5
                )
            }
        }
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxHeight
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}

@Composable
fun SettingsScreen(
    state: DesktopUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddMusicFolder: () -> Unit,
    onRemoveMusicFolder: (String) -> Unit,
    onRescanLibrary: () -> Unit,
    onUpdateCookies: (String?) -> Unit
) {
    val scrollState = rememberScrollState()
    var cookiesDraft by remember(state.youtubeCookies) { mutableStateOf(state.youtubeCookies.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Local Music Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onAddMusicFolder, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Folder")
                        }
                        OutlinedButton(onClick = onRescanLibrary, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rescan")
                        }
                    }
                }
                if (state.musicFolders.isEmpty()) {
                    Text(
                        "No folders added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.musicFolders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = folder,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { onRemoveMusicFolder(folder) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove folder")
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Linux Audio Engine (mpv)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.mpvState.mpvAvailable) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (state.mpvState.mpvAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (state.mpvState.mpvAvailable) {
                                state.mpvState.mpvVersion ?: "mpv detected"
                            } else {
                                "mpv binary not found on PATH"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        state.mpvState.mpvBinaryPath?.let { path ->
                            Text(
                                text = "Binary: $path",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YouTube Music Session (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Anonymous browsing & streaming work without login. Optionally paste YouTube Music cookies to personalize recommendations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = cookiesDraft,
                    onValueChange = { cookiesDraft = it },
                    label = { Text("Cookie header (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUpdateCookies(cookiesDraft) }, shape = RoundedCornerShape(12.dp)) {
                        Text("Save Session")
                    }
                    if (!state.youtubeCookies.isNullOrBlank()) {
                        OutlinedButton(onClick = {
                            cookiesDraft = ""
                            onUpdateCookies(null)
                        }, shape = RoundedCornerShape(12.dp)) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keyboard Shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val shortcuts = listOf(
                    "Space" to "Play / Pause",
                    "Ctrl + F" to "Search library & YouTube Music",
                    "Ctrl + O" to "Add local music folder",
                    "Left / Right" to "Seek -5s / +5s",
                    "Ctrl + Left / Right" to "Previous / Next track",
                    "Ctrl + Up / Down" to "Volume +5% / -5%",
                    "M" to "Mute / Unmute",
                    "L" to "Toggle Lyrics pane",
                    "Q" to "Toggle Queue pane"
                )
                shortcuts.forEach { (key, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(desc, style = MaterialTheme.typography.bodyMedium)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
