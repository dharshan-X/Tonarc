package com.quietrays.tonarc.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietrays.tonarc.data.model.Lyrics
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.InspectorTab
import com.quietrays.tonarc.desktop.model.RepeatMode
import com.quietrays.tonarc.desktop.state.DesktopUiState

@Composable
fun DesktopInspectorPane(
    state: DesktopUiState,
    onSelectTab: (InspectorTab) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight().width(358.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                InspectorTab.entries.forEachIndexed { index, tab ->
                    val label = if (tab == InspectorTab.QUEUE && state.queue.isNotEmpty()) {
                        "Queue (${state.queue.size})"
                    } else {
                        tab.label
                    }
                    SegmentedButton(
                        selected = state.inspectorTab == tab,
                        onClick = { onSelectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = InspectorTab.entries.size
                        ),
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when (state.inspectorTab) {
                InspectorTab.NOW_PLAYING -> NowPlayingTabContent(
                    state = state,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onVolumeChange = onVolumeChange,
                    onToggleMute = onToggleMute,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.weight(1f)
                )
                InspectorTab.QUEUE -> QueueTabContent(
                    queue = state.queue,
                    currentIndex = state.queueIndex,
                    isPlaying = state.mpvState.isPlaying,
                    onJumpToIndex = onJumpToQueueIndex,
                    onRemoveAt = onRemoveFromQueue,
                    onClearQueue = onClearQueue,
                    modifier = Modifier.weight(1f)
                )
                InspectorTab.LYRICS -> LyricsTabContent(
                    track = state.currentTrack,
                    lyrics = state.currentLyrics,
                    isLoading = state.isLoadingLyrics,
                    positionMs = state.mpvState.positionMs,
                    onSeekToMs = onSeek,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun NowPlayingTabContent(
    state: DesktopUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack
    val mpv = state.mpvState
    val effectiveDuration = mpv.durationMs.takeIf { it > 0L } ?: (track?.durationMs ?: 0L)

    if (track == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DesktopArtwork(
                    model = null,
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                    cornerRadius = 24.dp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Nothing playing yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pick a song from Home, Search, or your Library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val isFav = track.id in state.favoriteIds
    val playCorner by animateDpAsState(if (mpv.isPlaying) 18.dp else 28.dp)

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            DesktopArtwork(
                model = track.displayArtModel,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 24.dp
            )
            if (state.isResolvingStream || mpv.isBuffering) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(26.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onToggleFavorite(track) }) {
                Icon(
                    imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (track.codecBadge.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SourceBadge(
                    label = track.codecBadge,
                    isYouTube = track.source == com.quietrays.tonarc.desktop.model.TrackSource.YOUTUBE
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        InteractiveSeekSlider(
            positionMs = mpv.positionMs,
            durationMs = effectiveDuration,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
            }
            Surface(
                onClick = onTogglePlayPause,
                shape = RoundedCornerShape(playCorner),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (mpv.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (mpv.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleMute, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (mpv.isMuted || mpv.volume == 0) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = "Mute",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Slider(
                value = mpv.volume.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).height(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${mpv.volume}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp)
            )
        }
    }
}

@Composable
fun QueueTabContent(
    queue: List<DesktopTrack>,
    currentIndex: Int,
    isPlaying: Boolean,
    onJumpToIndex: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Your playback queue is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val listState = rememberLazyListState()
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${queue.size} tracks in queue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClearQueue) {
                Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(end = 10.dp)
            ) {
                itemsIndexed(queue, key = { idx, item -> "${idx}_${item.id}" }) { index, track ->
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { onJumpToIndex(index) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DesktopArtwork(
                            model = track.displayArtModel,
                            contentDescription = track.title,
                            modifier = Modifier.size(38.dp),
                            cornerRadius = 8.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isCurrent) {
                            IconButton(
                                onClick = { onRemoveAt(index) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (isPlaying) {
                            Text(
                                text = "PLAYING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
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

@Composable
fun LyricsTabContent(
    track: DesktopTrack?,
    lyrics: Lyrics,
    isLoading: Boolean,
    positionMs: Long,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Play a song to view synchronized lyrics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(10.dp))
                Text("Searching lyrics...", style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    val synced = lyrics.synced.orEmpty()
    val plain = lyrics.plain.orEmpty()

    if (synced.isEmpty() && plain.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No lyrics found for \"${track.title}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val listState = rememberLazyListState()

    if (synced.isNotEmpty()) {
        val activeIndex = remember(synced, positionMs) {
            var idx = -1
            for (i in synced.indices) {
                if (synced[i].time <= positionMs + 180L) idx = i else break
            }
            idx
        }

        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) {
                val target = (activeIndex - 3).coerceAtLeast(0)
                listState.animateScrollToItem(target)
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)
            ) {
                itemsIndexed(synced) { idx, line ->
                    val isActive = idx == activeIndex
                    val textColor by animateColorAsState(
                        if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else Color.Transparent
                            )
                            .clickable { onSeekToMs(line.time.toLong()) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = line.line.ifBlank { "♪" },
                            style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp)
            ) {
                itemsIndexed(plain) { _, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
fun BottomDesktopPlayerBar(
    state: DesktopUiState,
    isCompact: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return
    val mpv = state.mpvState
    val duration = mpv.durationMs.takeIf { it > 0L } ?: track.durationMs
    val progress = if (duration > 0L) (mpv.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenSheet),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DesktopArtwork(
                    model = track.displayArtModel,
                    contentDescription = track.title,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 10.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isCompact) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(140.dp)) {
                        IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (mpv.isMuted || mpv.volume == 0) {
                                    Icons.AutoMirrored.Rounded.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                },
                                contentDescription = "Mute",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Slider(
                            value = mpv.volume.toFloat(),
                            onValueChange = { onVolumeChange(it.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                    }
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (mpv.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                }

                IconButton(onClick = onOpenSheet) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Expand Player")
                }
            }
        }
    }
}

@Composable
fun ExpandedPlayerModalOverlay(
    state: DesktopUiState,
    onDismiss: () -> Unit,
    onSelectTab: (InspectorTab) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: (DesktopTrack) -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse Player")
                }
                SingleChoiceSegmentedButtonRow {
                    InspectorTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = state.inspectorTab == tab,
                            onClick = { onSelectTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(index, InspectorTab.entries.size),
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                Spacer(Modifier.width(40.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state.inspectorTab) {
                    InspectorTab.NOW_PLAYING -> NowPlayingTabContent(
                        state = state,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onVolumeChange = onVolumeChange,
                        onToggleMute = onToggleMute,
                        onToggleFavorite = onToggleFavorite,
                        modifier = Modifier.align(Alignment.Center).width(460.dp)
                    )
                    InspectorTab.QUEUE -> QueueTabContent(
                        queue = state.queue,
                        currentIndex = state.queueIndex,
                        isPlaying = state.mpvState.isPlaying,
                        onJumpToIndex = onJumpToQueueIndex,
                        onRemoveAt = onRemoveFromQueue,
                        onClearQueue = onClearQueue
                    )
                    InspectorTab.LYRICS -> LyricsTabContent(
                        track = state.currentTrack,
                        lyrics = state.currentLyrics,
                        isLoading = state.isLoadingLyrics,
                        positionMs = state.mpvState.positionMs,
                        onSeekToMs = onSeek
                    )
                }
            }
        }
    }
}
