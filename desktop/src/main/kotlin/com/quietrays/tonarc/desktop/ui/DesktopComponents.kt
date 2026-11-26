package com.quietrays.tonarc.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quietrays.tonarc.desktop.model.DesktopPlaylist
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.TrackSource
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun pickDirectoryNative(title: String = "Select Music Folder"): String? {
    // Prefer native Linux desktop file dialogs (zenity / kdialog), fallback to Swing JFileChooser
    val zenityPath = listOf("/usr/bin/zenity", "/bin/zenity").firstOrNull { File(it).canExecute() }
    if (zenityPath != null) {
        val selected = runCatching {
            val proc = ProcessBuilder(zenityPath, "--file-selection", "--directory", "--title=$title")
                .redirectErrorStream(false)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0 && out.isNotBlank()) out else null
        }.getOrNull()
        if (selected != null) return selected
    }

    val kdialogPath = listOf("/usr/bin/kdialog", "/bin/kdialog").firstOrNull { File(it).canExecute() }
    if (kdialogPath != null) {
        val selected = runCatching {
            val proc = ProcessBuilder(kdialogPath, "--getexistingdirectory", System.getProperty("user.home"))
                .redirectErrorStream(false)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0 && out.isNotBlank()) out else null
        }.getOrNull()
        if (selected != null) return selected
    }

    var chosen: String? = null
    runCatching {
        val task = Runnable {
            val chooser = JFileChooser(System.getProperty("user.home")).apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = title
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chosen = chooser.selectedFile?.absolutePath
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            task.run()
        } else {
            SwingUtilities.invokeAndWait(task)
        }
    }
    return chosen
}

@Composable
fun DesktopArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote
) {
    val resolvedModel = remember(model) {
        when (model) {
            is String -> if (model.startsWith("/")) File(model) else model
            else -> model
        }
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(28.dp)
        )
        if (resolvedModel != null) {
            AsyncImage(
                model = resolvedModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListRow(
    track: DesktopTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    playlists: List<DesktopPlaylist>,
    showAlbumColumn: Boolean = true,
    indexLabel: String? = null,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (DesktopPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var playlistSubmenuExpanded by remember { mutableStateOf(false) }

    val bgColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (indexLabel != null) {
            Box(
                modifier = Modifier.width(34.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent && isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (isHovered) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = indexLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DesktopArtwork(
            model = track.displayArtModel,
            contentDescription = track.title,
            modifier = Modifier.size(46.dp),
            cornerRadius = 10.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.source == TrackSource.YOUTUBE) {
                    Spacer(Modifier.width(6.dp))
                    SourceBadge(label = "YTM", isYouTube = true)
                } else if (!track.mimeType.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    SourceBadge(label = track.mimeType.take(6), isYouTube = false)
                }
            }
        }

        if (showAlbumColumn) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = track.album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isHovered) 0.9f else 0.45f
                ),
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = formatDurationMs(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Track options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = {
                    menuExpanded = false
                    playlistSubmenuExpanded = false
                }
            ) {
                DropdownMenuItem(
                    text = { Text("Play Now") },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                    onClick = {
                        menuExpanded = false
                        onPlay()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                    onClick = {
                        menuExpanded = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                    onClick = {
                        menuExpanded = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                    leadingIcon = {
                        Icon(
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleFavorite()
                    }
                )
                val customPlaylists = playlists.filter { !it.isYouTube }
                if (customPlaylists.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist...") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                        onClick = { playlistSubmenuExpanded = !playlistSubmenuExpanded }
                    )
                    if (playlistSubmenuExpanded) {
                        customPlaylists.forEach { pl ->
                            DropdownMenuItem(
                                text = { Text("  • ${pl.name}") },
                                onClick = {
                                    menuExpanded = false
                                    playlistSubmenuExpanded = false
                                    onAddToPlaylist(pl)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceBadge(label: String, isYouTube: Boolean) {
    val bg = if (isYouTube) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = if (isYouTube) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun MediaGridCard(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    badge: String? = null,
    fallbackIcon: ImageVector = Icons.Rounded.Album,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(18.dp)
            ),
        color = if (isHovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                DesktopArtwork(
                    model = artworkModel,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 14.dp,
                    fallbackIcon = fallbackIcon
                )
                if (badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (onPlayClick != null && isHovered) {
                    Surface(
                        onClick = onPlayClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InteractiveSeekSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTimestamps: Boolean = true
) {
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val currentFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayedFraction = draggingFraction ?: currentFraction
    val displayedMs = (displayedFraction * safeDuration).toLong()

    Column(modifier = modifier) {
        Slider(
            value = displayedFraction,
            onValueChange = { draggingFraction = it },
            onValueChangeFinished = {
                val target = draggingFraction?.let { (it * safeDuration).toLong() }
                draggingFraction = null
                if (target != null) onSeek(target)
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
        if (showTimestamps) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMs(displayedMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDurationMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
