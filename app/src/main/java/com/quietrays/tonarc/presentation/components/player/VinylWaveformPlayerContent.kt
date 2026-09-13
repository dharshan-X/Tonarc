package com.quietrays.tonarc.presentation.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.Song
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietrays.tonarc.presentation.components.LocalMaterialTheme
import com.quietrays.tonarc.presentation.viewmodel.PlayerSheetState
import com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Vinyl Waveform Player: A Material 3 Expressive full-screen player experience
 * featuring an authentic spinning grooved vinyl turntable, dynamic ambient halo,
 * song-based amplitude waveform scrubber, tactile squircle playback controls,
 * and a clean modern utility bar.
 */
@Composable
fun VinylWaveformPlayerContent(
    currentSong: Song,
    currentPlaybackQueue: List<Song> = emptyList(),
    currentQueueSourceName: String = "",
    currentMediaItemIndex: Int? = null,
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    expansionFractionProvider: () -> Float = { 1f },
    currentSheetState: PlayerSheetState = PlayerSheetState.EXPANDED,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    totalDurationProvider: () -> Long,
    isFavoriteProvider: () -> Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueueClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showAudioToolsBottomSheet by rememberSaveable { mutableStateOf(false) }

    val abRepeatState by playerViewModel.abRepeatState.collectAsStateWithLifecycle()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitchSemitones by playerViewModel.playbackPitchSemitones.collectAsStateWithLifecycle()

    val playerAccentColor = LocalMaterialTheme.current.primary
    val playerOnAccentColor = LocalMaterialTheme.current.onPrimary

    val totalDuration = totalDurationProvider().coerceAtLeast(1L)
    val isPlaying = isPlayingProvider()
    val isFavorite = isFavoriteProvider()

    // Synthesize realistic, deterministic waveform based on the current song
    val waveformBars = remember(currentSong.id, currentSong.title, currentSong.artist, totalDuration) {
        SongWaveformSynthesizer.generate(
            title = currentSong.title,
            artist = currentSong.artist,
            durationMs = totalDuration,
            barCount = 58
        )
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.surface,
                        Color(0xFF090604)
                    ),
                    radius = 1600f
                )
            )
            .padding(top = statusBarPadding, bottom = navBarPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar
            VinylPlayerTopBar(
                accentColor = playerAccentColor,
                onAccentColor = playerOnAccentColor,
                onEqualizerBadgeClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showAudioToolsBottomSheet = true
                },
                onQueueClick = onShowQueueClicked,
                onCollapseClick = onCollapse
            )

            // 2. Centered Song Metadata
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentSong.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = currentSong.title.ifBlank { stringResource(R.string.unknown_song_title) },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. Turntable Stage (Rotating Grooved Vinyl with Center Album Art and Halo)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val discDiameter = minOf(maxWidth * 0.88f, maxHeight * 0.95f, 280.dp)
                VinylTurntableStage(
                    song = currentSong,
                    diameter = discDiameter,
                    isPlaying = isPlaying,
                    accentColor = playerAccentColor,
                    onTogglePlayPause = onPlayPause
                )
            }

            // 4. Song-Generated Waveform Scrubber
            VinylWaveformScrubber(
                bars = waveformBars,
                currentPositionProvider = currentPositionProvider,
                totalDuration = totalDuration,
                accentColor = playerAccentColor,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Material 3 Expressive Playback Controls Row
            VinylExpressiveControlsRow(
                isPlaying = isPlaying,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                accentColor = playerAccentColor,
                onAccentColor = playerOnAccentColor,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 6. Clean Utility Bar
            VinylCleanUtilityBar(
                isFavorite = isFavorite,
                accentColor = playerAccentColor,
                onFavoriteToggle = onFavoriteToggle,
                onAudioToolsClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showAudioToolsBottomSheet = true
                },
                onQueueClick = onShowQueueClicked
            )
        }
    }

    if (showAudioToolsBottomSheet) {
        val totalDurationValue = totalDurationProvider()
        AudioToolsBottomSheet(
            abRepeatState = abRepeatState,
            playbackSpeed = playbackSpeed,
            playbackPitchSemitones = playbackPitchSemitones,
            currentPositionMs = currentPositionProvider(),
            totalDurationMs = totalDurationValue,
            onSetPointA = { playerViewModel.setAbRepeatPointA(it) },
            onSetPointB = { playerViewModel.setAbRepeatPointB(it) },
            onAdjustPointA = { playerViewModel.adjustAbRepeatPointA(it, totalDurationValue) },
            onAdjustPointB = { playerViewModel.adjustAbRepeatPointB(it, totalDurationValue) },
            onToggleLoop = { playerViewModel.toggleAbRepeatLoop() },
            onClearLoop = { playerViewModel.clearAbRepeat() },
            onPlaybackSpeedChange = { playerViewModel.setPlaybackSpeed(it) },
            onPlaybackPitchChange = { playerViewModel.setPlaybackPitch(it) },
            onResetAll = { playerViewModel.resetAudioTools() },
            onDismiss = { showAudioToolsBottomSheet = false }
        )
    }
}

/**
 * Top bar with yellow/accent equalizer badge, search, queue, and collapse actions.
 */
@Composable
private fun VinylPlayerTopBar(
    accentColor: Color,
    onAccentColor: Color,
    onEqualizerBadgeClick: () -> Unit,
    onQueueClick: () -> Unit,
    onCollapseClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dynamic Waveform Badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor)
                .clickable(onClick = onEqualizerBadgeClick)
                .semantics {
                    role = Role.Button
                    contentDescription = "Audio tools"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = onAccentColor,
                modifier = Modifier.size(22.dp)
            )
        }

        // Top Right Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onQueueClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onCollapseClick) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Centered vinyl turntable with realistic concentric grooves, ambient halo,
 * and smooth continuous rotation when playing.
 */
@Composable
private fun VinylTurntableStage(
    song: Song,
    diameter: Dp,
    isPlaying: Boolean,
    accentColor: Color,
    onTogglePlayPause: () -> Unit
) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val deltaSec = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastNanos = frameNanos
                    // Smooth 33.3 RPM rotation (200 degrees/sec)
                    rotationAngle = (rotationAngle + 200f * deltaSec) % 360f
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(diameter)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlayPause
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Spotlight Halo
        Box(
            modifier = Modifier
                .size(diameter * 1.15f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Vinyl Disc Body
        Canvas(
            modifier = Modifier
                .size(diameter)
                .graphicsLayer { rotationZ = rotationAngle }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Concentric vinyl shaded body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1C1714),
                        Color(0xFF29221D),
                        Color(0xFF1A1512),
                        Color(0xFF241D18),
                        Color(0xFF171310)
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Outer rim highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = maxRadius - 1f,
                center = center,
                style = Stroke(width = 1.2f)
            )

            // Concentric Grooves
            val grooveFractions = listOf(0.93f, 0.87f, 0.81f, 0.75f, 0.69f, 0.63f)
            val grooveColor = Color.White.copy(alpha = 0.045f)
            for (frac in grooveFractions) {
                drawCircle(
                    color = grooveColor,
                    radius = maxRadius * frac,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }
        }

        // Circular Center Album Artwork
        val centerArtDiameter = diameter * 0.55f
        Box(
            modifier = Modifier
                .size(centerArtDiameter)
                .graphicsLayer { rotationZ = rotationAngle }
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!song.albumArtUriString.isNullOrBlank()) {
                AsyncImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Center Spindle Hole
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF140E0A))
                    .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
        }
    }
}

/**
 * Interactive Audio Amplitude Waveform Scrubber:
 * Renders 58 amplitude bars generated uniquely per song with elapsed & total times,
 * dynamic progress color fill, and tap & drag seeking.
 */
@Composable
private fun VinylWaveformScrubber(
    bars: List<Float>,
    currentPositionProvider: () -> Long,
    totalDuration: Long,
    accentColor: Color,
    onSeek: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }

    val currentPosition = if (isDragging) dragPositionMs else currentPositionProvider()
    val progressFraction = (currentPosition.toFloat() / totalDuration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Waveform Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .padding(horizontal = 10.dp)
                    .pointerInput(totalDuration, bars.size) {
                        detectTapGestures { offset ->
                            val pct = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val targetMs = (pct * totalDuration).toLong()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeek(targetMs)
                        }
                    }
                    .pointerInput(totalDuration, bars.size) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val pct = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                dragPositionMs = (pct * totalDuration).toLong()
                            },
                            onDragEnd = {
                                isDragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeek(dragPositionMs)
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pct = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                dragPositionMs = (pct * totalDuration).toLong()
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = bars.size
                    val spacingPx = 2.dp.toPx()
                    val totalSpacing = spacingPx * (barCount - 1)
                    val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2f)
                    val maxHeight = size.height
                    val centerY = maxHeight / 2f
                    val playedCount = (progressFraction * barCount).roundToInt()

                    for (i in 0 until barCount) {
                        val x = i * (barWidth + spacingPx)
                        val barHeight = (bars[i] * maxHeight).coerceIn(6.dp.toPx(), maxHeight)
                        val top = centerY - barHeight / 2f
                        val isPlayed = i < playedCount
                        val color = if (isPlayed) accentColor else inactiveColor

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
            }

            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Material 3 Expressive playback controls row with 78dp squircle hero play/pause,
 * 56dp tonal squircle skip buttons, and active dynamic toggle buttons.
 */
@Composable
private fun VinylExpressiveControlsRow(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    accentColor: Color,
    onAccentColor: Color,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Repeat Button (Tonal toggle)
        val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isRepeatActive) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isRepeatActive) accentColor.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    CircleShape
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRepeatToggle()
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "Repeat mode"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = null,
                tint = if (isRepeatActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Previous Button (56dp Tonal Squircle)
        val prevInteractionSource = remember { MutableInteractionSource() }
        val isPrevPressed by prevInteractionSource.collectIsPressedAsState()
        val prevScale by animateFloatAsState(if (isPrevPressed) 0.92f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "prev_scale")

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPrevious()
            },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 6.dp,
            interactionSource = prevInteractionSource,
            modifier = Modifier
                .size(56.dp)
                .scale(prevScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous track",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Hero Play / Pause Button (78dp Dynamic Primary Squircle)
        val playInteractionSource = remember { MutableInteractionSource() }
        val isPlayPressed by playInteractionSource.collectIsPressedAsState()
        val playScale by animateFloatAsState(if (isPlayPressed) 0.92f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "play_scale")
        val playCornerRadius by animateFloatAsState(if (isPlayPressed) 32f else 26f, spring(stiffness = Spring.StiffnessMedium), label = "play_corner")

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlayPause()
            },
            shape = RoundedCornerShape(playCornerRadius.dp),
            color = accentColor,
            shadowElevation = 10.dp,
            interactionSource = playInteractionSource,
            modifier = Modifier
                .size(78.dp)
                .scale(playScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = onAccentColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Next Button (56dp Tonal Squircle)
        val nextInteractionSource = remember { MutableInteractionSource() }
        val isNextPressed by nextInteractionSource.collectIsPressedAsState()
        val nextScale by animateFloatAsState(if (isNextPressed) 0.92f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "next_scale")

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNext()
            },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 6.dp,
            interactionSource = nextInteractionSource,
            modifier = Modifier
                .size(56.dp)
                .scale(nextScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next track",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Shuffle Button (Tonal toggle)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isShuffleEnabled) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isShuffleEnabled) accentColor.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    CircleShape
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShuffleToggle()
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "Shuffle"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                tint = if (isShuffleEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Clean Utility Bar replacing fake social counters with clean Tonarc controls:
 * Favorite toggle, Audio Tools chip (1.0x • A-B), and Queue sheet trigger.
 */
@Composable
private fun VinylCleanUtilityBar(
    isFavorite: Boolean,
    accentColor: Color,
    onFavoriteToggle: () -> Unit,
    onAudioToolsClick: () -> Unit,
    onQueueClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favorite Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFavoriteToggle()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Favorite",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Audio Tools (Speed & Pitch & A-B loop)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onAudioToolsClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = "Audio tools",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "1.0x \u2022 A-B",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }

            // Up Next Queue
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onQueueClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Format milliseconds into m:ss time string.
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
