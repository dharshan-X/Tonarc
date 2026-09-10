package com.quietrays.tonarc.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import coil.compose.AsyncImage
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.presentation.utils.LocalAppHapticsConfig
import com.quietrays.tonarc.presentation.utils.performAppCompatHapticFeedback
import com.quietrays.tonarc.ui.theme.RoundedSans
import com.quietrays.tonarc.utils.formatTotalDuration
import kotlinx.collections.immutable.ImmutableList

internal fun resolvePlaylistTagBadge(
    source: String,
    isFolder: Boolean,
    isSmart: Boolean
): String? = when {
    isFolder -> "Folder"
    isSmart -> "Smart Mix"
    source.equals("SPOTIFY", ignoreCase = true) -> "Spotify"
    else -> null
}

internal fun resolvePlaylistSubtitleMeta(
    songCount: Int,
    totalDurationText: String,
    formatTag: String = "Lossless"
): String {
    val countText = if (songCount == 1) "1 song" else "$songCount songs"
    return "$countText • $totalDurationText • $formatTag"
}

internal fun extractHeroAlbumArts(
    coverImageUri: String?,
    songs: List<Song>,
    maxArts: Int = 4
): List<String> {
    if (!coverImageUri.isNullOrBlank()) {
        return listOf(coverImageUri)
    }
    return songs.asSequence()
        .mapNotNull { it.albumArtUriString }
        .filter { it.isNotBlank() }
        .distinct()
        .take(maxArts)
        .toList()
}

@Composable
private fun PlaylistHeroArtworkBackground(
    playlist: Playlist,
    songs: ImmutableList<Song>,
    modifier: Modifier = Modifier
) {
    val albumArts = remember(playlist.coverImageUri, songs) {
        extractHeroAlbumArts(playlist.coverImageUri, songs)
    }

    Box(modifier = modifier) {
        when {
            albumArts.isEmpty() -> {
                val fallbackColor = playlist.coverColorArgb?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.surfaceContainerLowest
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(fallbackColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
            albumArts.size == 1 -> {
                AsyncImage(
                    model = albumArts[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
            albumArts.size == 2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AsyncImage(
                        model = albumArts[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    AsyncImage(
                        model = albumArts[1],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }
            albumArts.size == 3 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AsyncImage(
                        model = albumArts[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = albumArts[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        AsyncImage(
                            model = albumArts[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = albumArts[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        AsyncImage(
                            model = albumArts[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = albumArts[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        AsyncImage(
                            model = albumArts[3],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistHeroSection(
    playlist: Playlist,
    songs: ImmutableList<Song>,
    isFolderPlaylist: Boolean,
    isSmartPlaylist: Boolean,
    isPlaying: Boolean,
    onPlayFabClick: () -> Unit,
    onBackClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val moreOptionsLabel = stringResource(R.string.presentation_batch_b_more_options)
    val backLabel = stringResource(R.string.auth_cd_back)

    val glowColor = remember(playlist.coverColorArgb) {
        playlist.coverColorArgb?.let { Color(it) } ?: Color.Transparent
    }

    val tagText = remember(playlist.source, isFolderPlaylist, isSmartPlaylist) {
        resolvePlaylistTagBadge(playlist.source, isFolderPlaylist, isSmartPlaylist)
    }

    val formattedDuration = remember(songs) { formatTotalDuration(songs) }
    val subtitleMeta = remember(songs.size, formattedDuration) {
        resolvePlaylistSubtitleMeta(songs.size, formattedDuration)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Dark Rounded Hero Card Container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Full-bleed album arts filling the hero
                PlaylistHeroArtworkBackground(
                    playlist = playlist,
                    songs = songs,
                    modifier = Modifier.matchParentSize()
                )

                // Cinematic gradient scrim overlay for high contrast and readability
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.65f),
                                    0.25f to Color.Black.copy(alpha = 0.25f),
                                    0.55f to Color.Black.copy(alpha = 0.50f),
                                    0.80f to Color.Black.copy(alpha = 0.82f),
                                    1.0f to Color.Black.copy(alpha = 0.96f)
                                )
                            )
                        )
                )

                // Ambient custom cover color tint if present
                if (glowColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = 0.20f),
                                        Color.Transparent,
                                        glowColor.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                }

                // Foreground Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 38.dp)
                ) {
                    // Top Row: Back button & Options button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.40f),
                                contentColor = Color.White
                            ),
                            onClick = onBackClick
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = backLabel
                            )
                        }

                        if (!isFolderPlaylist) {
                            FilledTonalIconButton(
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.40f),
                                    contentColor = Color.White
                                ),
                                onClick = onOptionsClick
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = moreOptionsLabel
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(130.dp))

                    // Expressive Tag Badge (only shown when tagText is non-null)
                    if (!tagText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "● ${tagText.uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = RoundedSans,
                                    letterSpacing = 0.8.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Playlist Name
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 64.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Metadata Subtitle
                    Text(
                        text = subtitleMeta,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.padding(end = 64.dp)
                    )
                }
            }
        }

        // Seam-Overlapping Floating Action Button (56.dp)
        val fabScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.04f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "fabScale"
        )

        FloatingActionButton(
            onClick = {
                performAppCompatHapticFeedback(
                    view,
                    appHapticsConfig,
                    HapticFeedbackConstantsCompat.CONTEXT_CLICK
                )
                onPlayFabClick()
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-24).dp, y = 28.dp)
                .size(56.dp)
                .graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

