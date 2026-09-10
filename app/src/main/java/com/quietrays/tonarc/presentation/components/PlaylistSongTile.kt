package com.quietrays.tonarc.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.ui.theme.RoundedSans
import com.quietrays.tonarc.utils.formatDuration

data class PastelBadgePalette(
    val background: Color,
    val iconTint: Color
)

private val PastelPalettes = listOf(
    PastelBadgePalette(Color(0xFF7FC4FD), Color(0xFF0D47A1)), // 0: Blue
    PastelBadgePalette(Color(0xFFDCE775), Color(0xFF556B2F)), // 1: Lime
    PastelBadgePalette(Color(0xFFB39DDB), Color(0xFF4A148C)), // 2: Purple
    PastelBadgePalette(Color(0xFFFFCC80), Color(0xFFE65100)), // 3: Orange
    PastelBadgePalette(Color(0xFFD4E157), Color(0xFF33691E)), // 4: Chartreuse
    PastelBadgePalette(Color(0xFFA5D6A7), Color(0xFF1B5E20)), // 5: Mint
    PastelBadgePalette(Color(0xFFC5E1A5), Color(0xFF2E7D32)), // 6: Sage
    PastelBadgePalette(Color(0xFFFFAB91), Color(0xFFB71C1C)), // 7: Coral
    PastelBadgePalette(Color(0xFF90CAF9), Color(0xFF0D47A1)), // 8: Sky
    PastelBadgePalette(Color(0xFFCE93D8), Color(0xFF4A148C))  // 9: Lavender
)

fun resolvePastelBadgePalette(index: Int): PastelBadgePalette {
    val safeIndex = ((index % PastelPalettes.size) + PastelPalettes.size) % PastelPalettes.size
    return PastelPalettes[safeIndex]
}

@Composable
fun AnimatedEqualizerWaveBars(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bar1Scale by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b1"
    )
    val bar2Scale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b2"
    )
    val bar3Scale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(18.dp * bar1Scale)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(18.dp * bar2Scale)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(18.dp * bar3Scale)
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun PlaylistSongTile(
    song: Song,
    index: Int,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isReorderMode: Boolean,
    isRemoveMode: Boolean,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onMoreOptionsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    val palette = remember(index) { resolvePastelBadgePalette(index) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder Drag Handle Slot
            AnimatedVisibility(
                visible = isReorderMode,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dragHandle != null) {
                        dragHandle()
                    } else {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = stringResource(R.string.presentation_batch_b_reorder_song),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            // Remove Button Slot
            AnimatedVisibility(
                visible = isRemoveMode,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.cd_remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            // 44.dp Pastel Circular Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(palette.background, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentSong && isPlaying) {
                    AnimatedEqualizerWaveBars(
                        color = palette.iconTint,
                        modifier = Modifier.size(width = 18.dp, height = 18.dp)
                    )
                } else if (!song.albumArtUriString.isNullOrBlank()) {
                    SmartImage(
                        model = song.albumArtUriString,
                        shape = CircleShape,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        targetSize = SmartImageCompactListTargetSize
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.rounded_music_note_24),
                        contentDescription = null,
                        tint = palette.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Two-Line Title & Artist Typography
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = RoundedSans
                    ),
                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.displayArtist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = RoundedSans),
                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing Actions: Duration & More Options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatDuration(song.duration),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = RoundedSans
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = { onMoreOptionsClick(song) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(
                            R.string.presentation_batch_e_more_options_for_song,
                            song.title
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Hairline Divider
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
    }
}
