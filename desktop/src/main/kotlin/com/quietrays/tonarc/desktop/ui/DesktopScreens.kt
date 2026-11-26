package com.quietrays.tonarc.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietrays.tonarc.desktop.model.DesktopAlbum
import com.quietrays.tonarc.desktop.model.DesktopArtist
import com.quietrays.tonarc.desktop.model.DesktopPlaylist
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.LibrarySortOption
import com.quietrays.tonarc.desktop.model.LibraryTab
import com.quietrays.tonarc.desktop.model.SearchFilter
import com.quietrays.tonarc.desktop.model.TrackSource
import com.quietrays.tonarc.desktop.state.DesktopUiState
import com.quietrays.tonarc.desktop.state.DetailPage
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: DesktopUiState,
    onPlayTrack: (DesktopTrack, List<DesktopTrack>) -> Unit,
    onPlayNext: (DesktopTrack) -> Unit,
    onAddToQueue: (DesktopTrack) -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    onAddToPlaylist: (DesktopPlaylist, DesktopTrack) -> Unit,
    onOpenAlbum: (DesktopAlbum) -> Unit,
    onOpenPlaylist: (DesktopPlaylist) -> Unit,
    onRefreshHome: () -> Unit,
    onAddMusicFolder: () -> Unit,
    onResolveTrack: (String) -> DesktopTrack?
) {
    val listState = rememberLazyListState()
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..22 -> "Good evening"
            else -> "Late night listening"
        }
    }

    val recentTracks = remember(state.recentTrackIds, state.localTracks) {
        state.recentTrackIds.mapNotNull { onResolveTrack(it) }
    }

    val localAlbums = remember(state.localTracks) {
        state.localTracks
            .groupBy { it.album to (it.albumArtist ?: it.artist) }
            .map { (key, tracks) ->
                DesktopAlbum(
                    id = "local_album:${key.first}:${key.second}",
                    title = key.first,
                    artist = key.second,
                    year = tracks.firstOrNull { it.year > 0 }?.year,
                    artworkModel = tracks.firstOrNull { it.artworkPath != null }?.artworkPath,
                    trackCount = tracks.size,
                    source = TrackSource.LOCAL,
                    tracks = tracks
                )
            }
            .take(12)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!state.mpvState.mpvAvailable) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "mpv audio backend not detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Install mpv (e.g. sudo apt install mpv / pacman -S mpv / dnf install mpv) to enable playback.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                        MaterialTheme.colorScheme.surfaceContainer
                                    )
                                )
                            )
                            .padding(22.dp)
                    ) {
                        Column {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${state.localTracks.size} local tracks • YouTube Music Innertube discovery ready",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.localTracks.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            val shuffled = state.localTracks.shuffled()
                                            onPlayTrack(shuffled.first(), shuffled)
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Shuffle Library")
                                    }
                                }
                                FilledTonalButton(
                                    onClick = onAddMusicFolder,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add Music Folder")
                                }
                                OutlinedButton(
                                    onClick = onRefreshHome,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Refresh Picks")
                                }
                            }
                        }
                    }
                }
            }

            if (recentTracks.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Jump Back In",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(recentTracks.take(12), key = { "recent_${it.id}" }) { track ->
                                MediaGridCard(
                                    title = track.title,
                                    subtitle = track.artist,
                                    artworkModel = track.displayArtModel,
                                    badge = if (track.source == TrackSource.YOUTUBE) "YTM" else track.mimeType,
                                    fallbackIcon = Icons.Rounded.MusicNote,
                                    onClick = { onPlayTrack(track, recentTracks) },
                                    onPlayClick = { onPlayTrack(track, recentTracks) },
                                    modifier = Modifier.width(168.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (localAlbums.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Local Albums",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(localAlbums, key = { it.id }) { album ->
                                MediaGridCard(
                                    title = album.title,
                                    subtitle = "${album.artist} • ${album.trackCount} tracks",
                                    artworkModel = album.artworkModel,
                                    fallbackIcon = Icons.Rounded.Album,
                                    onClick = { onOpenAlbum(album) },
                                    onPlayClick = {
                                        album.tracks.firstOrNull()?.let { first ->
                                            onPlayTrack(first, album.tracks)
                                        }
                                    },
                                    modifier = Modifier.width(172.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.isLoadingHome && state.homeShelves.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading YouTube Music picks...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            items(state.homeShelves, key = { it.title }) { shelf ->
                Column {
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!shelf.subtitle.isNullOrBlank()) {
                        Text(
                            text = shelf.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    if (shelf.tracks.isNotEmpty()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 760.dp) 2 else 1
                            val shownTracks = shelf.tracks.take(8)
                            if (columns == 2) {
                                val left = shownTracks.filterIndexed { i, _ -> i % 2 == 0 }
                                val right = shownTracks.filterIndexed { i, _ -> i % 2 == 1 }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        left.forEach { track ->
                                            TrackListRow(
                                                track = track,
                                                isCurrent = state.currentTrack?.id == track.id,
                                                isPlaying = state.mpvState.isPlaying,
                                                isFavorite = track.id in state.favoriteIds,
                                                playlists = state.playlists,
                                                showAlbumColumn = false,
                                                onPlay = { onPlayTrack(track, shelf.tracks) },
                                                onPlayNext = { onPlayNext(track) },
                                                onAddToQueue = { onAddToQueue(track) },
                                                onToggleFavorite = { onToggleFavorite(track) },
                                                onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) }
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        right.forEach { track ->
                                            TrackListRow(
                                                track = track,
                                                isCurrent = state.currentTrack?.id == track.id,
                                                isPlaying = state.mpvState.isPlaying,
                                                isFavorite = track.id in state.favoriteIds,
                                                playlists = state.playlists,
                                                showAlbumColumn = false,
                                                onPlay = { onPlayTrack(track, shelf.tracks) },
                                                onPlayNext = { onPlayNext(track) },
                                                onAddToQueue = { onAddToQueue(track) },
                                                onToggleFavorite = { onToggleFavorite(track) },
                                                onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    shownTracks.forEach { track ->
                                        TrackListRow(
                                            track = track,
                                            isCurrent = state.currentTrack?.id == track.id,
                                            isPlaying = state.mpvState.isPlaying,
                                            isFavorite = track.id in state.favoriteIds,
                                            playlists = state.playlists,
                                            showAlbumColumn = false,
                                            onPlay = { onPlayTrack(track, shelf.tracks) },
                                            onPlayNext = { onPlayNext(track) },
                                            onAddToQueue = { onAddToQueue(track) },
                                            onToggleFavorite = { onToggleFavorite(track) },
                                            onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (shelf.albums.isNotEmpty() || shelf.playlists.isNotEmpty()) {
                        Spacer(Modifier.height(if (shelf.tracks.isNotEmpty()) 12.dp else 0.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(shelf.albums, key = { it.id }) { album ->
                                MediaGridCard(
                                    title = album.title,
                                    subtitle = album.artist,
                                    artworkModel = album.artworkModel,
                                    badge = "Album",
                                    fallbackIcon = Icons.Rounded.Album,
                                    onClick = { onOpenAlbum(album) },
                                    modifier = Modifier.width(172.dp)
                                )
                            }
                            items(shelf.playlists, key = { it.id }) { pl ->
                                MediaGridCard(
                                    title = pl.name,
                                    subtitle = pl.description.ifBlank { "YouTube Music Playlist" },
                                    artworkModel = pl.artworkUrl,
                                    badge = "Playlist",
                                    fallbackIcon = Icons.Rounded.LibraryMusic,
                                    onClick = { onOpenPlaylist(pl) },
                                    modifier = Modifier.width(172.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: DesktopUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onPlayTrack: (DesktopTrack, List<DesktopTrack>) -> Unit,
    onPlayNext: (DesktopTrack) -> Unit,
    onAddToQueue: (DesktopTrack) -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    onAddToPlaylist: (DesktopPlaylist, DesktopTrack) -> Unit,
    onOpenAlbum: (DesktopAlbum) -> Unit,
    onOpenArtist: (DesktopArtist) -> Unit,
    onOpenPlaylist: (DesktopPlaylist) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.searchFocusTrigger) {
        runCatching { focusRequester.requestFocus() }
    }

    val localMatches = remember(state.searchQuery, state.localTracks) {
        val q = state.searchQuery.trim()
        if (q.isEmpty()) emptyList()
        else state.localTracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search local library and YouTube Music (Ctrl+F)") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
        )

        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.searchFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.searchQuery.isBlank()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Search across local FLAC/MP3 tracks and YouTube Music",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val showAlbumCol = maxWidth >= 680.dp
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, end = 12.dp)
                ) {
                    if (state.isSearching) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Searching YouTube Music...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (localMatches.isNotEmpty() &&
                        (state.searchFilter == SearchFilter.ALL || state.searchFilter == SearchFilter.LOCAL)
                    ) {
                        item {
                            Text(
                                text = "Local Library (${localMatches.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        itemsIndexed(localMatches.take(30), key = { _, t -> "search_local_${t.id}" }) { idx, track ->
                            TrackListRow(
                                track = track,
                                isCurrent = state.currentTrack?.id == track.id,
                                isPlaying = state.mpvState.isPlaying,
                                isFavorite = track.id in state.favoriteIds,
                                playlists = state.playlists,
                                showAlbumColumn = showAlbumCol,
                                indexLabel = (idx + 1).toString(),
                                onPlay = { onPlayTrack(track, localMatches) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) }
                            )
                        }
                    }

                    if (state.ytSearchSongs.isNotEmpty() &&
                        (state.searchFilter == SearchFilter.ALL || state.searchFilter == SearchFilter.YOUTUBE_SONGS)
                    ) {
                        item {
                            Text(
                                text = "YouTube Music Songs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        itemsIndexed(state.ytSearchSongs, key = { idx, t -> "yt_song_${idx}_${t.id}" }) { idx, track ->
                            TrackListRow(
                                track = track,
                                isCurrent = state.currentTrack?.id == track.id,
                                isPlaying = state.mpvState.isPlaying,
                                isFavorite = track.id in state.favoriteIds,
                                playlists = state.playlists,
                                showAlbumColumn = showAlbumCol,
                                indexLabel = (idx + 1).toString(),
                                onPlay = { onPlayTrack(track, state.ytSearchSongs) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) }
                            )
                        }
                    }

                    if (state.ytSearchAlbums.isNotEmpty() &&
                        (state.searchFilter == SearchFilter.ALL || state.searchFilter == SearchFilter.YOUTUBE_ALBUMS)
                    ) {
                        item {
                            Text(
                                text = "Albums",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.ytSearchAlbums, key = { it.id }) { album ->
                                    MediaGridCard(
                                        title = album.title,
                                        subtitle = album.artist,
                                        artworkModel = album.artworkModel,
                                        fallbackIcon = Icons.Rounded.Album,
                                        onClick = { onOpenAlbum(album) },
                                        modifier = Modifier.width(168.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.ytSearchArtists.isNotEmpty() &&
                        (state.searchFilter == SearchFilter.ALL || state.searchFilter == SearchFilter.YOUTUBE_ARTISTS)
                    ) {
                        item {
                            Text(
                                text = "Artists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.ytSearchArtists, key = { it.id }) { artist ->
                                    MediaGridCard(
                                        title = artist.name,
                                        subtitle = artist.subtitle ?: "Artist",
                                        artworkModel = artist.artworkModel,
                                        fallbackIcon = Icons.Rounded.Person,
                                        onClick = { onOpenArtist(artist) },
                                        modifier = Modifier.width(168.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.ytSearchPlaylists.isNotEmpty() &&
                        (state.searchFilter == SearchFilter.ALL || state.searchFilter == SearchFilter.YOUTUBE_PLAYLISTS)
                    ) {
                        item {
                            Text(
                                text = "Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.ytSearchPlaylists, key = { it.id }) { pl ->
                                    MediaGridCard(
                                        title = pl.name,
                                        subtitle = pl.description.ifBlank { "Playlist" },
                                        artworkModel = pl.artworkUrl,
                                        fallbackIcon = Icons.Rounded.LibraryMusic,
                                        onClick = { onOpenPlaylist(pl) },
                                        modifier = Modifier.width(168.dp)
                                    )
                                }
                            }
                        }
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
fun LibraryScreen(
    state: DesktopUiState,
    onSelectTab: (LibraryTab) -> Unit,
    onSelectSort: (LibrarySortOption) -> Unit,
    onRescan: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onPlayTrack: (DesktopTrack, List<DesktopTrack>) -> Unit,
    onPlayNext: (DesktopTrack) -> Unit,
    onAddToQueue: (DesktopTrack) -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    onAddToPlaylist: (DesktopPlaylist, DesktopTrack) -> Unit,
    onOpenAlbum: (DesktopAlbum) -> Unit,
    onOpenArtist: (DesktopArtist) -> Unit,
    onResolveTrack: (String) -> DesktopTrack?
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortedTracks = remember(state.localTracks, state.librarySort) {
        when (state.librarySort) {
            LibrarySortOption.TITLE -> state.localTracks.sortedBy { it.title.lowercase() }
            LibrarySortOption.ARTIST -> state.localTracks.sortedBy { it.artist.lowercase() }
            LibrarySortOption.ALBUM -> state.localTracks.sortedBy { it.album.lowercase() }
            LibrarySortOption.DURATION -> state.localTracks.sortedByDescending { it.durationMs }
            LibrarySortOption.DATE_ADDED -> state.localTracks.sortedByDescending { it.dateAddedMs }
        }
    }

    val favoriteTracks = remember(state.favoriteIds, state.localTracks) {
        state.favoriteIds.mapNotNull { onResolveTrack(it) }
    }

    val albums = remember(state.localTracks) {
        state.localTracks
            .groupBy { it.album to (it.albumArtist ?: it.artist) }
            .map { (key, tracks) ->
                DesktopAlbum(
                    id = "lib_album:${key.first}:${key.second}",
                    title = key.first,
                    artist = key.second,
                    year = tracks.firstOrNull { it.year > 0 }?.year,
                    artworkModel = tracks.firstOrNull { it.artworkPath != null }?.artworkPath,
                    trackCount = tracks.size,
                    source = TrackSource.LOCAL,
                    tracks = tracks
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    val artists = remember(state.localTracks) {
        state.localTracks
            .groupBy { it.artist }
            .map { (name, tracks) ->
                DesktopArtist(
                    id = "lib_artist:$name",
                    name = name,
                    artworkModel = tracks.firstOrNull { it.artworkPath != null }?.artworkPath,
                    subtitle = "${tracks.size} tracks",
                    source = TrackSource.LOCAL,
                    tracks = tracks
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.isScanningLibrary) "Scanning folders..." else "${state.localTracks.size} tracks in ${state.musicFolders.size} folder(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { sortMenuExpanded = true }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(state.librarySort.label)
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        LibrarySortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSelectSort(opt)
                                }
                            )
                        }
                    }
                }
                FilledTonalButton(onClick = onAddFolder, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Folder")
                }
                IconButton(onClick = onRescan) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.libraryTab == tab,
                    onClick = { onSelectTab(tab) },
                    label = { Text(tab.label) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.libraryTab) {
                LibraryTab.SONGS -> TrackCollectionPane(
                    tracks = sortedTracks,
                    emptyTitle = "No local audio files found",
                    emptySubtitle = "Add a music folder (FLAC, MP3, M4A, Opus, OGG, WAV) to build your local library.",
                    state = state,
                    onPlayTrack = onPlayTrack,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onEmptyAction = onAddFolder
                )
                LibraryTab.FAVORITES -> TrackCollectionPane(
                    tracks = favoriteTracks,
                    emptyTitle = "No favorites yet",
                    emptySubtitle = "Click the heart icon on any local or YouTube Music song to save it here.",
                    state = state,
                    onPlayTrack = onPlayTrack,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist
                )
                LibraryTab.ALBUMS -> {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 172.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(end = 12.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums, key = { it.id }) { album ->
                            MediaGridCard(
                                title = album.title,
                                subtitle = "${album.artist} • ${album.trackCount} tracks",
                                artworkModel = album.artworkModel,
                                fallbackIcon = Icons.Rounded.Album,
                                onClick = { onOpenAlbum(album) },
                                onPlayClick = {
                                    album.tracks.firstOrNull()?.let { first -> onPlayTrack(first, album.tracks) }
                                }
                            )
                        }
                    }
                }
                LibraryTab.ARTISTS -> {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 172.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(end = 12.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(artists, key = { it.id }) { artist ->
                            MediaGridCard(
                                title = artist.name,
                                subtitle = artist.subtitle ?: "",
                                artworkModel = artist.artworkModel,
                                fallbackIcon = Icons.Rounded.Person,
                                onClick = { onOpenArtist(artist) },
                                onPlayClick = {
                                    artist.tracks.firstOrNull()?.let { first -> onPlayTrack(first, artist.tracks) }
                                }
                            )
                        }
                    }
                }
                LibraryTab.FOLDERS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.musicFolders.isEmpty()) {
                            Text(
                                text = "No music folders configured.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.musicFolders.forEach { folder ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = folder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = { onRemoveFolder(folder) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove folder")
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

@Composable
private fun TrackCollectionPane(
    tracks: List<DesktopTrack>,
    emptyTitle: String,
    emptySubtitle: String,
    state: DesktopUiState,
    onPlayTrack: (DesktopTrack, List<DesktopTrack>) -> Unit,
    onPlayNext: (DesktopTrack) -> Unit,
    onAddToQueue: (DesktopTrack) -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    onAddToPlaylist: (DesktopPlaylist, DesktopTrack) -> Unit,
    onEmptyAction: (() -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(10.dp))
                Text(emptyTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    emptySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onEmptyAction != null) {
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onEmptyAction) { Text("Add Music Folder") }
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showAlbumColumn = maxWidth >= 680.dp
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(end = 12.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                TrackListRow(
                    track = track,
                    isCurrent = state.currentTrack?.id == track.id,
                    isPlaying = state.mpvState.isPlaying,
                    isFavorite = track.id in state.favoriteIds,
                    playlists = state.playlists,
                    showAlbumColumn = showAlbumColumn,
                    indexLabel = (idx + 1).toString(),
                    onPlay = { onPlayTrack(track, tracks) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onAddToPlaylist = { pl -> onAddToPlaylist(pl, track) }
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}
