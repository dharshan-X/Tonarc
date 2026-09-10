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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
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
): String = when {
    isFolder -> "Folder"
    isSmart -> "Smart Mix"
    source.equals("YOUTUBE", ignoreCase = true) -> "YouTube Music"
    source.equals("SPOTIFY", ignoreCase = true) -> "Spotify"
    else -> "Tonarc Playlist"
}

internal fun resolvePlaylistSubtitleMeta(
    songCount: Int,
    totalDurationText: String,
    formatTag: String = "Lossless"
): String {
    val countText = if (songCount == 1) "1 song" else "$songCount songs"
    return "$countText • $totalDurationText • $formatTag"
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Clean Top Row: Only Back button & Options button (No Menu, No Profile button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
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
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
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

                Spacer(modifier = Modifier.height(10.dp))

                // Centered Cover Artwork with Dynamic Ambient Glow
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val activeGlow = if (glowColor != Color.Transparent) glowColor else MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        activeGlow.copy(alpha = 0.42f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    PlaylistCover(
                        playlist = playlist,
                        playlistSongs = songs,
                        size = 176.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(26.dp), clip = false)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Expressive Tag Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary
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

                Spacer(modifier = Modifier.height(6.dp))

                // Playlist Name
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Metadata Subtitle
                Text(
                    text = subtitleMeta,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
