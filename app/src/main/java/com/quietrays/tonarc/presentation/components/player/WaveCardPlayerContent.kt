package com.quietrays.tonarc.presentation.components.player

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.Lyrics
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.repository.LyricsSearchResult
import com.quietrays.tonarc.presentation.components.LocalMaterialTheme
import com.quietrays.tonarc.presentation.components.LyricsSheet
import com.quietrays.tonarc.presentation.components.subcomps.FetchLyricsDialog
import com.quietrays.tonarc.presentation.visualizer.VisualizerBottomSheet
import com.quietrays.tonarc.presentation.viewmodel.LyricsSearchUiState
import com.quietrays.tonarc.presentation.viewmodel.PlayerSheetState
import com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel
import com.quietrays.tonarc.utils.LyricsImportSecurity
import com.quietrays.tonarc.utils.LyricsImportValidationResult
import com.quietrays.tonarc.utils.ValidatedLyricsImport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Wave-Card Player Content: Pixel-exact Material 3 Expressive player mirroring
 * the signature HTML/CSS mockup (material3-wave-card-player.html).
 *
 * Visual Architecture:
 * 1. Upper Section (~64%): Full-bleed album art stage with 4 anime-outlined top action circles
 *    and a smooth atmospheric vertical gradient fade into the middle card.
 * 2. Middle Section (~22%): Distinct elevated waveform card with rounded bottom corners (38.dp),
 *    2.5.dp solid anime ink bottom border, 4px cel drop shadow, song details header, zero-elevation
 *    circular lyrics toggle, and a multi-strand squiggly sine wave baseline scrubber.
 * 3. Bottom Section (~14%): Clean light off-white surface hosting EXACTLY THREE large Material 3
 *    expressive buttons (Squircle Previous, Stadium Pill Play/Pause, Squircle Next) rendered with
 *    solid black anime ink borders, 3.5.dp offset cel shadows, and bouncy spring interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveCardPlayerContent(
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
    lyricsProvider: () -> Lyrics? = { null },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var showAudioToolsBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showVisualizerBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val abRepeatState by playerViewModel.abRepeatState.collectAsStateWithLifecycle()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitchSemitones by playerViewModel.playbackPitchSemitones.collectAsStateWithLifecycle()

    val visualizerEnabled by playerViewModel.visualizerEnabled.collectAsStateWithLifecycle()
    val visualizerMode by playerViewModel.visualizerMode.collectAsStateWithLifecycle()
    val visualizerStyle by playerViewModel.visualizerStyle.collectAsStateWithLifecycle()

    val isAudioToolsActive = abRepeatState.isLoopActive ||
        kotlin.math.abs(playbackSpeed - 1.0f) > 0.01f ||
        playbackPitchSemitones != 0

    val totalDuration = totalDurationProvider().coerceAtLeast(1L)
    val isPlaying = isPlayingProvider()
    val isFavorite = isFavoriteProvider()
    val currentPosition = currentPositionProvider()

    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val playerUiState by playerViewModel.playerUiState.collectAsStateWithLifecycle()
    val isBuffering = stablePlayerState.isBuffering
    val isPreparingSong = playerUiState.preparingSongId != null && playerUiState.preparingSongId == currentSong.id
    val isSongLoading = isBuffering || isPreparingSong
    val lyricsSyncOffset = fullPlayerSlice.lyricsSyncOffset
    val immersiveLyricsEnabled = fullPlayerSlice.immersiveLyricsEnabled
    val immersiveLyricsTimeout = fullPlayerSlice.immersiveLyricsTimeout
    val isImmersiveTemporarilyDisabled = fullPlayerSlice.isImmersiveTemporarilyDisabled

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                coroutineScope.launch {
                    try {
                        val validation = validateLyricsImport(context, it)
                        val validatedImport: ValidatedLyricsImport = when (validation) {
                            is LyricsImportValidationResult.Valid -> validation.value
                            is LyricsImportValidationResult.Invalid -> {
                                playerViewModel.sendToast(
                                    LyricsImportSecurity.messageFor(validation.reason)
                                )
                                return@launch
                            }
                        }

                        val currentSongId = currentSong.id.toLongOrNull()
                        if (currentSongId == null) {
                            playerViewModel.sendToast("No song selected for lyrics import.")
                            return@launch
                        }

                        playerViewModel.importLyricsFromFile(currentSongId, validatedImport)
                        showFetchLyricsDialog = false
                        showLyricsSheet = true
                    } catch (e: Exception) {
                        playerViewModel.sendToast("Error reading file.")
                    }
                }
            }
        }
    )

    val onLyricsClick = {
        val lyrics = lyricsProvider()
        if (lyrics?.synced.isNullOrEmpty() && lyrics?.plain.isNullOrEmpty()) {
            showFetchLyricsDialog = true
        } else {
            showLyricsSheet = true
        }
    }

    LaunchedEffect(lyricsSearchUiState) {
        when (lyricsSearchUiState) {
            is LyricsSearchUiState.Success -> {
                if (showFetchLyricsDialog) {
                    showFetchLyricsDialog = false
                    showLyricsSheet = true
                    playerViewModel.resetLyricsSearchState()
                }
            }
            else -> Unit
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Color tokens supporting both Light and Dark Themes dynamically
    val colorScheme = LocalMaterialTheme.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = isSystemDark || colorScheme.surface.luminance() < 0.5f

    // Outline & Border colors (clean outlines only in light mode, transparent in dark mode)
    val cardBorderColor = if (isDark) Color.Transparent else Color(0xFF141219)
    val buttonBorderColor = if (isDark) Color.Transparent else Color(0xFF141219)
    val topCircleBorderColor = if (isDark) Color.Transparent else Color(0xFF141219)

    // Background surfaces
    val bottomBgColor = if (isDark) colorScheme.surfaceContainerLowest else Color(0xFFFAF8FC)
    val cardBgColor = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFE5E4E7)

    // Vibrant button colors
    val buttonBgColor = colorScheme.primary
    val buttonOnColor = colorScheme.onPrimary

    // Top action buttons fill & icon tint
    val topCircleBgColor = if (isDark) colorScheme.surfaceContainerHighest.copy(alpha = 0.85f) else Color(0xFFEDE9F2)
    val topCircleIconColor = if (isDark) colorScheme.onSurface else Color(0xFF141219)

    // Card text colors
    val cardTextTitleColor = if (isDark) colorScheme.onSurface else Color(0xFF141219)
    val cardTextArtistColor = if (isDark) colorScheme.onSurfaceVariant else Color(0xFF141219).copy(alpha = 0.72f)

    // Menu icons color
    val menuIconColor = if (isDark) colorScheme.onSurface else Color(0xFF141219)

    // Scrubber colors
    val scrubberWaveColor = if (isDark) colorScheme.primary else Color(0xFF141219)
    val scrubberTrackInactiveColor = if (isDark) colorScheme.onSurface.copy(alpha = 0.25f) else Color(0xFF141219).copy(alpha = 0.22f)
    val scrubberBorderColor = cardBorderColor
    val scrubberThumbColor = if (isDark) colorScheme.primary else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bottomBgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. UPPER SECTION: Full-bleed album art stage taking weight(1f) to hug screen comfortably
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(cardBgColor)
            ) {
                // High-Resolution Album Artwork Layer: Direct alpha mask fade (fades album art itself, no color overlay)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fadeHeightPx = 96.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = (size.height - fadeHeightPx).coerceAtLeast(0f),
                                    endY = size.height
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    if (!currentSong.albumArtUriString.isNullOrBlank()) {
                        AsyncImage(
                            model = currentSong.albumArtUriString,
                            contentDescription = currentSong.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            colorScheme.primaryContainer,
                                            colorScheme.secondaryContainer,
                                            cardBgColor
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }

                // Top Controls Bar (clean circular action buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding + 10.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Collapse Circle Button
                    AnimeCircleButton(
                        onClick = onCollapse,
                        iconTint = topCircleIconColor,
                        containerColor = topCircleBgColor,
                        borderColor = topCircleBorderColor,
                        contentDescription = "Collapse"
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Right Cluster: Audio Tools (if enabled), Queue, More Options
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Audio Tools Circle Button (visible only if enabled in settings)
                        if (fullPlayerSlice.showAudioTools) {
                            AnimeCircleButton(
                                onClick = { showAudioToolsBottomSheet = true },
                                iconTint = if (isAudioToolsActive) buttonOnColor else topCircleIconColor,
                                containerColor = if (isAudioToolsActive) buttonBgColor else topCircleBgColor,
                                borderColor = topCircleBorderColor,
                                contentDescription = "Audio tools"
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Queue Circle Button
                        AnimeCircleButton(
                            onClick = onShowQueueClicked,
                            iconTint = topCircleIconColor,
                            containerColor = topCircleBgColor,
                            borderColor = topCircleBorderColor,
                            contentDescription = "Queue"
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // More Options Circle Button
                        Box {
                            AnimeCircleButton(
                                onClick = { showMoreMenu = true },
                                iconTint = topCircleIconColor,
                                containerColor = topCircleBgColor,
                                borderColor = topCircleBorderColor,
                                contentDescription = "More options"
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Overflow Menu
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (isFavorite) Color(0xFFE91E63) else menuIconColor
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onFavoriteToggle()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isShuffleEnabled) "Shuffle: On" else "Shuffle: Off") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            tint = if (isShuffleEnabled) buttonBgColor else menuIconColor
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onShuffleToggle()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (repeatMode) {
                                                Player.REPEAT_MODE_ONE -> "Repeat: One"
                                                Player.REPEAT_MODE_ALL -> "Repeat: All"
                                                else -> "Repeat: Off"
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                            contentDescription = null,
                                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) buttonBgColor else menuIconColor
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onRepeatToggle()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Visualizer Settings") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = menuIconColor
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showVisualizerBottomSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. MIDDLE SECTION: Waveform Card with generous height 176.dp and spacious padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .drawBehind {
                        val cornerRadiusPx = 38.dp.toPx()
                        val strokeWidthPx = 2.dp.toPx()
                        val w = size.width
                        val h = size.height

                        // 1. Card fill path with rounded bottom corners
                        val cardFillPath = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(0f, h - cornerRadiusPx)
                            arcTo(
                                rect = Rect(
                                    left = 0f,
                                    top = h - 2 * cornerRadiusPx,
                                    right = 2 * cornerRadiusPx,
                                    bottom = h
                                ),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = -90f,
                                forceMoveTo = false
                            )
                            lineTo(w - cornerRadiusPx, h)
                            arcTo(
                                rect = Rect(
                                    left = w - 2 * cornerRadiusPx,
                                    top = h - 2 * cornerRadiusPx,
                                    right = w,
                                    bottom = h
                                ),
                                startAngleDegrees = 90f,
                                sweepAngleDegrees = -90f,
                                forceMoveTo = false
                            )
                            lineTo(w, 0f)
                            close()
                        }
                        drawPath(path = cardFillPath, color = cardBgColor)

                        // 2. Clean outline around the bottom curve (in light mode only, no grey border in dark mode)
                        if (!isDark) {
                            val borderStrokePath = Path().apply {
                                moveTo(0f, (h - cornerRadiusPx).coerceAtLeast(0f))
                                arcTo(
                                    rect = Rect(
                                        left = 0f,
                                        top = h - 2 * cornerRadiusPx,
                                        right = 2 * cornerRadiusPx,
                                        bottom = h
                                    ),
                                    startAngleDegrees = 180f,
                                    sweepAngleDegrees = -90f,
                                    forceMoveTo = false
                                )
                                lineTo(w - cornerRadiusPx, h)
                                arcTo(
                                    rect = Rect(
                                        left = w - 2 * cornerRadiusPx,
                                        top = h - 2 * cornerRadiusPx,
                                        right = w,
                                        bottom = h
                                    ),
                                    startAngleDegrees = 90f,
                                    sweepAngleDegrees = -90f,
                                    forceMoveTo = false
                                )
                                lineTo(w, (h - cornerRadiusPx).coerceAtLeast(0f))
                            }
                            drawPath(
                                path = borderStrokePath,
                                color = cardBorderColor,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Square)
                            )
                        }
                    }
                    .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 26.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Card Header: Song Details on Left, Flat Zero-Elevation Lyrics Button on Right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 21.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = cardTextTitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentSong.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = cardTextArtistColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Flat Lyrics Button (clean outline, zero shadow)
                        AnimeFlatLyricsButton(
                            onClick = onLyricsClick,
                            containerColor = topCircleBgColor,
                            borderColor = topCircleBorderColor,
                            iconTint = topCircleIconColor
                        )
                    }

                    // Baseline Scrubber: Timestamps & Multi-Strand Squiggly Sine Waveform
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Timestamp (0:00)
                        Text(
                            text = formatDuration(currentPosition),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.3.sp
                            ),
                            color = cardTextTitleColor,
                            modifier = Modifier.width(38.dp)
                        )

                        // Center Squiggly Sine Wave Scrubber Canvas
                        WaveformScrubberCanvas(
                            currentPositionMs = currentPosition,
                            totalDurationMs = totalDuration,
                            isPlaying = isPlaying,
                            waveColor = scrubberWaveColor,
                            trackInactiveColor = scrubberTrackInactiveColor,
                            borderColor = scrubberBorderColor,
                            thumbColor = scrubberThumbColor,
                            onSeek = onSeek,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .padding(horizontal = 4.dp)
                        )

                        // Right Timestamp (-3:57)
                        val remainingMs = (totalDuration - currentPosition).coerceAtLeast(0L)
                        Text(
                            text = "-" + formatDuration(remainingMs),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.3.sp
                            ),
                            color = cardTextTitleColor,
                            modifier = Modifier.width(42.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }

            // 3. BOTTOM SECTION: Generous height 116.dp + navBarPadding with spacious padding & refined M3 Expressive tactile feedback
            var activePressedButton by remember { mutableStateOf<WaveCardButtonType?>(null) }
            var lastClickedButton by remember { mutableStateOf<WaveCardButtonType?>(null) }

            LaunchedEffect(lastClickedButton) {
                if (lastClickedButton != null) {
                    delay(180L)
                    lastClickedButton = null
                }
            }

            val currentActiveButton = activePressedButton ?: lastClickedButton

            val tactileSpring = spring<Float>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )

            // Subtle, refined weight shifts (gentle tactile expansion, not extreme rubber stretch)
            val prevTargetWeight = when (currentActiveButton) {
                WaveCardButtonType.PREVIOUS -> 1.30f
                WaveCardButtonType.PLAY_PAUSE -> 0.85f
                WaveCardButtonType.NEXT -> 0.90f
                else -> 1.0f
            }
            val playTargetWeight = when (currentActiveButton) {
                WaveCardButtonType.PLAY_PAUSE -> 2.30f
                WaveCardButtonType.PREVIOUS -> 1.75f
                WaveCardButtonType.NEXT -> 1.75f
                else -> 2.0f
            }
            val nextTargetWeight = when (currentActiveButton) {
                WaveCardButtonType.NEXT -> 1.30f
                WaveCardButtonType.PLAY_PAUSE -> 0.85f
                WaveCardButtonType.PREVIOUS -> 0.90f
                else -> 1.0f
            }

            val prevWeight by animateFloatAsState(
                targetValue = prevTargetWeight,
                animationSpec = tactileSpring,
                label = "prevWeight"
            )
            val playWeight by animateFloatAsState(
                targetValue = playTargetWeight,
                animationSpec = tactileSpring,
                label = "playWeight"
            )
            val nextWeight by animateFloatAsState(
                targetValue = nextTargetWeight,
                animationSpec = tactileSpring,
                label = "nextWeight"
            )

            // Uniform tactile scaling on press/click without asymmetrical distortion
            val prevScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.PREVIOUS) 0.95f else 1.0f,
                animationSpec = tactileSpring,
                label = "prevScale"
            )
            val playScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.PLAY_PAUSE) 0.95f else 1.0f,
                animationSpec = tactileSpring,
                label = "playScale"
            )
            val nextScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.NEXT) 0.95f else 1.0f,
                animationSpec = tactileSpring,
                label = "nextScale"
            )

            val prevIconScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.PREVIOUS) 1.08f else 1.0f,
                animationSpec = tactileSpring,
                label = "prevIconScale"
            )
            val playIconScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.PLAY_PAUSE) 1.08f else 1.0f,
                animationSpec = tactileSpring,
                label = "playIconScale"
            )
            val nextIconScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.NEXT) 1.08f else 1.0f,
                animationSpec = tactileSpring,
                label = "nextIconScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp + navBarPadding)
                    .background(bottomBgColor)
                    .padding(horizontal = 24.dp)
                    .padding(top = 22.dp, bottom = 22.dp + navBarPadding),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track: Squircle Button (gentle flex expansion 1.0 -> 1.30)
                    AnimeExpressiveButton(
                        onClick = {
                            lastClickedButton = WaveCardButtonType.PREVIOUS
                            onPrevious()
                        },
                        modifier = Modifier
                            .weight(prevWeight)
                            .height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = buttonBgColor,
                        contentColor = buttonOnColor,
                        borderColor = buttonBorderColor,
                        onPressedChange = { pressed ->
                            activePressedButton = if (pressed) WaveCardButtonType.PREVIOUS else null
                        },
                        scale = prevScale
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = prevIconScale
                                    scaleY = prevIconScale
                                }
                        )
                    }

                    // Play/Pause Track: Stadium Pill Button (gentle flex expansion 2.0 -> 2.30) with Loading Indicator
                    AnimeExpressiveButton(
                        onClick = {
                            lastClickedButton = WaveCardButtonType.PLAY_PAUSE
                            onPlayPause()
                        },
                        modifier = Modifier
                            .weight(playWeight)
                            .height(64.dp),
                        shape = CircleShape,
                        containerColor = buttonBgColor,
                        contentColor = buttonOnColor,
                        borderColor = buttonBorderColor,
                        onPressedChange = { pressed ->
                            activePressedButton = if (pressed) WaveCardButtonType.PLAY_PAUSE else null
                        },
                        scale = playScale
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = playIconScale
                                scaleY = playIconScale
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = isSongLoading,
                                animationSpec = tween(durationMillis = 200),
                                label = "playLoadingCrossfade"
                            ) { loading ->
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.8.dp,
                                        color = buttonOnColor
                                    )
                                } else {
                                    MorphingPlayPauseIcon(
                                        isPlaying = isPlaying,
                                        tint = buttonOnColor,
                                        size = 32.dp
                                    )
                                }
                            }
                        }
                    }

                    // Next Track: Squircle Button (gentle flex expansion 1.0 -> 1.30)
                    AnimeExpressiveButton(
                        onClick = {
                            lastClickedButton = WaveCardButtonType.NEXT
                            onNext()
                        },
                        modifier = Modifier
                            .weight(nextWeight)
                            .height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = buttonBgColor,
                        contentColor = buttonOnColor,
                        borderColor = buttonBorderColor,
                        onPressedChange = { pressed ->
                            activePressedButton = if (pressed) WaveCardButtonType.NEXT else null
                        },
                        scale = nextScale
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = nextIconScale
                                    scaleY = nextIconScale
                                }
                        )
                    }
                }
            }
        }
    }

    // Audio Tools Bottom Sheet
    if (showAudioToolsBottomSheet) {
        val totalDurationValue = totalDuration
        AudioToolsBottomSheet(
            abRepeatState = abRepeatState,
            playbackSpeed = playbackSpeed,
            playbackPitchSemitones = playbackPitchSemitones,
            currentPositionMs = currentPosition,
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

    // Visualizer Sheet
    if (showVisualizerBottomSheet) {
        VisualizerBottomSheet(
            isEnabled = visualizerEnabled,
            selectedMode = visualizerMode,
            selectedStyle = visualizerStyle,
            isPlaying = isPlaying,
            currentPositionMs = currentPosition,
            onToggleEnabled = { playerViewModel.setVisualizerEnabled(it) },
            onSelectMode = { playerViewModel.setVisualizerMode(it) },
            onSelectStyle = { playerViewModel.setVisualizerStyle(it) },
            onDismiss = { showVisualizerBottomSheet = false }
        )
    }

    // Lyrics Full Sheet
    AnimatedVisibility(
        visible = showLyricsSheet,
        enter = slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
        exit = slideOutVertically(
            targetOffsetY = { it / 6 },
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playbackPositionFlow = playerViewModel.currentPlaybackPosition,
            lyricsSearchUiState = lyricsSearchUiState,
            resetLyricsForCurrentSong = {
                showLyricsSheet = false
                playerViewModel.resetLyricsForCurrentSong()
            },
            onSearchLyrics = { forcePick -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
            onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
            onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
            onImportLyrics = { filePickerLauncher.launch(LyricsImportSecurity.pickerMimeTypes()) },
            onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = { currentSong.id.let { songId -> playerViewModel.setLyricsSyncOffset(songId, it) } },
            lyricsTextStyle = MaterialTheme.typography.titleLarge,
            colorScheme = LocalMaterialTheme.current,
            onBackClick = { showLyricsSheet = false },
            onSaveLyricsToFile = playerViewModel::saveLyricsToFile,
            onSeekTo = { playerViewModel.seekTo(it) },
            onPlayPause = { playerViewModel.playPause() },
            onNext = onNext,
            onPrev = onPrevious,
            immersiveLyricsEnabled = immersiveLyricsEnabled,
            immersiveLyricsTimeout = immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
            onSetImmersiveTemporarilyDisabled = { playerViewModel.setImmersiveTemporarilyDisabled(it) },
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle
        )
    }

    // Manual Lyrics Fetch Dialog
    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = currentSong,
                onConfirm = { forcePick: Boolean -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
                onPickResult = { result: LyricsSearchResult -> playerViewModel.acceptLyricsSearchResultForCurrentSong(result) },
                onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
                onDismiss = {
                    showFetchLyricsDialog = false
                    playerViewModel.resetLyricsSearchState()
                },
                onImport = {
                    filePickerLauncher.launch(LyricsImportSecurity.pickerMimeTypes())
                }
            )
        }
    }
}

private enum class WaveCardButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * Top Circular Action Button: 44.dp circle with clean outline border,
 * tactile spring bounce animation on press/click, and responsive ripple indication.
 */
@Composable
private fun AnimeCircleButton(
    onClick: () -> Unit,
    iconTint: Color,
    containerColor: Color,
    borderColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scaleAnim.animateTo(
                targetValue = 0.88f,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing)
            )
        } else {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
            .clip(CircleShape)
            .background(containerColor)
            .border(1.5.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = iconTint),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        scaleAnim.animateTo(0.88f, tween(80))
                        scaleAnim.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            content()
        }
    }
}

/**
 * Flat Zero-Elevation Lyrics Button: 40.dp circle, clean outline border,
 * ZERO elevation, ZERO shadow, tactile spring bounce animation, and ripple indication.
 */
@Composable
private fun AnimeFlatLyricsButton(
    onClick: () -> Unit,
    containerColor: Color,
    borderColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scaleAnim.animateTo(
                targetValue = 0.88f,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing)
            )
        } else {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
            .clip(CircleShape)
            .background(containerColor)
            .border(1.5.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = iconTint),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        scaleAnim.animateTo(0.88f, tween(80))
                        scaleAnim.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.rounded_lyrics_24),
            contentDescription = "Lyrics",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Material 3 Expressive Button: Clean outline border, NO drop shadows,
 * dynamic weight & corner morphing, squash-and-stretch scale, and responsive ripple indication.
 */
@Composable
private fun AnimeExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    borderWidth: Dp = 1.5.dp,
    onPressedChange: (Boolean) -> Unit = {},
    scale: Float = 1f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        onPressedChange(isPressed)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .border(borderWidth, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = contentColor),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * Waveform Scrubber Canvas: Renders the multi-strand squiggly sine wave track for
 * the played progress, a clean outlined capsule thumb, and a straight inactive track.
 */
@Composable
private fun WaveformScrubberCanvas(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    waveColor: Color,
    trackInactiveColor: Color,
    borderColor: Color,
    thumbColor: Color = Color.White,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = totalDurationMs.coerceAtLeast(1L)
    val realFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayFraction = if (isScrubbing) scrubFraction else realFraction

    val infiniteTransition = rememberInfiniteTransition(label = "waveScrubberRibbons")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 2800 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(
        modifier = modifier
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek((frac * safeDuration).toLong())
                }
            }
            .pointerInput(safeDuration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isScrubbing = true
                        scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isScrubbing = false
                        onSeek((scrubFraction * safeDuration).toLong())
                    },
                    onDragCancel = {
                        isScrubbing = false
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        val trackMarginPx = 4.dp.toPx()
        val trackStart = trackMarginPx
        val trackEnd = w - trackMarginPx
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)

        val thumbX = trackStart + displayFraction * trackWidth
        val thumbWPx = if (isScrubbing) 8.dp.toPx() else 6.dp.toPx()
        val thumbHPx = if (isScrubbing) 24.dp.toPx() else 20.dp.toPx()
        val gapPx = 6.dp.toPx()

        // 1. Inactive Track (Unplayed)
        val inactStart = thumbX + (thumbWPx / 2f) + gapPx
        if (inactStart < trackEnd) {
            drawLine(
                color = trackInactiveColor,
                start = Offset(inactStart, centerY),
                end = Offset(trackEnd, centerY),
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 2. Active Track (Played): Animated Multi-Strand Squiggly Sine Waves
        val actEnd = thumbX - (thumbWPx / 2f) - gapPx
        if (actEnd > trackStart) {
            if (isPlaying && !isScrubbing) {
                val strands = listOf(
                    Triple(26.dp.toPx(), 5.5.dp.toPx(), 0f),
                    Triple(36.dp.toPx(), 4.2.dp.toPx(), 2.2f),
                    Triple(52.dp.toPx(), 3.0.dp.toPx(), 4.2f)
                )

                strands.forEachIndexed { idx, (wl, amp, phaseOffset) ->
                    val path = Path()
                    var first = true
                    var x = trackStart
                    val step = 2f

                    while (x <= actEnd) {
                        val distFromEnds = kotlin.math.min(x - trackStart, actEnd - x)
                        val env = (distFromEnds / 8.dp.toPx()).coerceIn(0f, 1f)
                        val angle = ((x - trackStart) / wl) * 2 * Math.PI - phase + phaseOffset
                        val waveY = centerY + (sin(angle).toFloat() * amp * env)

                        if (first) {
                            path.moveTo(x, waveY)
                            first = false
                        } else {
                            path.lineTo(x, waveY)
                        }
                        x += step
                    }

                    val alpha = when (idx) {
                        0 -> 0.98f
                        1 -> 0.75f
                        else -> 0.52f
                    }
                    val strokeW = when (idx) {
                        0 -> 3.5.dp.toPx()
                        1 -> 2.6.dp.toPx()
                        else -> 1.8.dp.toPx()
                    }

                    drawPath(
                        path = path,
                        color = waveColor.copy(alpha = alpha),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            } else {
                // Paused or scrubbing: Clean solid horizontal line
                drawLine(
                    color = waveColor,
                    start = Offset(trackStart, centerY),
                    end = Offset(actEnd, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 3. Capsule Thumb with Clean Outline (no neobrutalism shadow)
        val thumbLeft = thumbX - thumbWPx / 2f
        val thumbTop = centerY - thumbHPx / 2f
        val radiusPx = 3.dp.toPx()

        // Thumb Surface Fill
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = androidx.compose.ui.geometry.Size(thumbWPx, thumbHPx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx)
        )

        // Thumb Solid Outline Border (light mode only)
        if (borderColor != Color.Transparent) {
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = androidx.compose.ui.geometry.Size(thumbWPx, thumbHPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = 1.8.dp.toPx())
            )
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
