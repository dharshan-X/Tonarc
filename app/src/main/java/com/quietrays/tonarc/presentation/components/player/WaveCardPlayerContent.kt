package com.quietrays.tonarc.presentation.components.player

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.shadow
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
import com.quietrays.tonarc.presentation.components.SongInfoBottomSheet
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }

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
    val audioBadgeText = remember(
        currentSong.mimeType,
        currentSong.bitrate,
        currentSong.sampleRate,
        fullPlayerSlice.audioMetadata
    ) {
        val meta = fullPlayerSlice.audioMetadata
        val effectiveMime = meta.mimeType ?: currentSong.mimeType
        val effectiveBitrate = meta.bitrate ?: currentSong.bitrate
        val effectiveSampleRate = meta.sampleRate ?: currentSong.sampleRate
        formatAudioBadgeText(effectiveMime, effectiveBitrate, effectiveSampleRate)
    }
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

    // Outline & Border colors
    val cardBorderColor = if (isDark) Color.Transparent else Color(0xFF141219)
    val buttonBorderColor = if (isDark) Color.Transparent else Color(0xFF141219)
    val topCircleBorderColor = if (isDark) colorScheme.outlineVariant.copy(alpha = 0.5f) else Color(0xFF141219)

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
                // Ambient Radial Bloom bleeding extracted palette color into background
                val bloomAlpha = if (isDark) 0.18f else 0.08f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colorScheme.primary.copy(alpha = bloomAlpha),
                                    colorScheme.surfaceContainerLowest
                                ),
                                radius = 700f
                            )
                        )
                )

                // High-Resolution Album Artwork Layer: Cubic-eased 4-stop alpha gradient fade
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fadeHeightPx = 110.dp.toPx()
                            val startY = (size.height - fadeHeightPx).coerceAtLeast(0f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black,
                                        0.4f to Color.Black.copy(alpha = 0.85f),
                                        0.75f to Color.Black.copy(alpha = 0.40f),
                                        1.0f to Color.Transparent
                                    ),
                                    startY = startY,
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
                        val strokeWidthPx = 1.5.dp.toPx()
                        val w = size.width
                        val h = size.height

                        // 1. Card fill path with rounded bottom corners (frosted vertical gradient)
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
                        val cardBrush = Brush.verticalGradient(
                            colors = listOf(colorScheme.surfaceContainerLow, colorScheme.surfaceContainer)
                        )
                        drawPath(path = cardFillPath, brush = cardBrush)

                        // 2. 1.dp specular hairline highlight along the top edge of the card
                        drawLine(
                            color = colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.25f),
                            start = Offset(0f, 0.5f),
                            end = Offset(w, 0.5f),
                            strokeWidth = 1.dp.toPx()
                        )

                        // 3. Card outline border around the bottom curve
                        val cardBottomBorderColor = if (isDark) {
                            colorScheme.outlineVariant.copy(alpha = 0.5f)
                        } else {
                            cardBorderColor
                        }
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
                            color = cardBottomBorderColor,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Square)
                        )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = currentSong.artist,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    color = cardTextArtistColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (fullPlayerSlice.showPlayerFileInfo && audioBadgeText != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colorScheme.surfaceContainerHigh)
                                            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
                                            .clickable { showSongInfoBottomSheet = true }
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = audioBadgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp,
                                                letterSpacing = 0.4.sp
                                            ),
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
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

                    // Baseline Scrubber: Timestamps & Liquid Wave Scrubber
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

                        // Center Liquid Wave Scrubber
                        LiquidWaveScrubber(
                            currentPositionMs = currentPosition,
                            totalDurationMs = totalDuration,
                            isPlaying = isPlaying,
                            waveColor = colorScheme.primary,
                            trackInactiveColor = colorScheme.onSurface.copy(alpha = if (isDark) 0.22f else 0.18f),
                            bubbleContainerColor = colorScheme.surfaceContainerHighest,
                            bubbleContentColor = colorScheme.onSurface,
                            bubbleBorderColor = colorScheme.outlineVariant.copy(alpha = 0.7f),
                            thumbColor = if (isDark) colorScheme.primary else Color.White,
                            thumbBorderColor = if (isDark) Color.Transparent else colorScheme.outlineVariant,
                            onSeek = onSeek,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
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

            val targetWeights = resolveWaveCardButtonWeights(currentActiveButton)
            val targetScales = resolveWaveCardButtonScales(currentActiveButton)
            val dockSpring = spring<Float>(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMedium
            )
            val prevWeight by animateFloatAsState(targetValue = targetWeights.previous, animationSpec = dockSpring, label = "prevWeight")
            val playWeight by animateFloatAsState(targetValue = targetWeights.playPause, animationSpec = dockSpring, label = "playWeight")
            val nextWeight by animateFloatAsState(targetValue = targetWeights.next, animationSpec = dockSpring, label = "nextWeight")

            val prevScale by animateFloatAsState(targetValue = targetScales.previous, animationSpec = dockSpring, label = "prevScale")
            val playScale by animateFloatAsState(targetValue = targetScales.playPause, animationSpec = dockSpring, label = "playScale")
            val nextScale by animateFloatAsState(targetValue = targetScales.next, animationSpec = dockSpring, label = "nextScale")

            val prevIconScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.PREVIOUS) 1.08f else 1.0f,
                animationSpec = dockSpring,
                label = "prevIconScale"
            )
            val playIconScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.PLAY_PAUSE) 1.08f else 1.0f,
                animationSpec = dockSpring,
                label = "playIconScale"
            )
            val nextIconScale by animateFloatAsState(
                targetValue = if (currentActiveButton == WaveCardButtonType.NEXT) 1.08f else 1.0f,
                animationSpec = dockSpring,
                label = "nextIconScale"
            )

            val prevNextContainerColor = if (isDark) colorScheme.surfaceContainerHigh else Color(0xFFE5E4E7)
            val prevNextContentColor = if (isDark) colorScheme.onSurface else Color(0xFF141219)
            val prevNextBorderColor = if (isDark) colorScheme.outlineVariant.copy(alpha = 0.4f) else Color(0xFF141219)

            val playContainerColor = colorScheme.primaryContainer
            val playContentColor = colorScheme.onPrimaryContainer
            val playBorderColor = if (isDark) colorScheme.primary.copy(alpha = 0.5f) else Color(0xFF141219)

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
                    // Previous Track: Squircle Button (height 58.dp, rounded corners 20.dp)
                    AnimeExpressiveButton(
                        onClick = {
                            lastClickedButton = WaveCardButtonType.PREVIOUS
                            onPrevious()
                        },
                        modifier = Modifier
                            .weight(prevWeight)
                            .height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = prevNextContainerColor,
                        contentColor = prevNextContentColor,
                        borderColor = prevNextBorderColor,
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

                    // Play/Pause Track: Stadium Pill Button (height 64.dp, CircleShape) with Loading Indicator
                    AnimeExpressiveButton(
                        onClick = {
                            lastClickedButton = WaveCardButtonType.PLAY_PAUSE
                            onPlayPause()
                        },
                        modifier = Modifier
                            .weight(playWeight)
                            .height(64.dp),
                        shape = CircleShape,
                        containerColor = playContainerColor,
                        contentColor = playContentColor,
                        borderColor = playBorderColor,
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
                                    LoadingIndicator(
                                        modifier = Modifier.size(30.dp),
                                        color = playContentColor
                                    )
                                } else {
                                    MorphingPlayPauseIcon(
                                        isPlaying = isPlaying,
                                        tint = playContentColor,
                                        size = 32.dp
                                    )
                                }
                            }
                        }
                    }

                    // Next Track: Squircle Button (height 58.dp, rounded corners 20.dp)
                    AnimeExpressiveButton(
                        onClick = {
                            lastClickedButton = WaveCardButtonType.NEXT
                            onNext()
                        },
                        modifier = Modifier
                            .weight(nextWeight)
                            .height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = prevNextContainerColor,
                        contentColor = prevNextContentColor,
                        borderColor = prevNextBorderColor,
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

    // Song Info Bottom Sheet
    if (showSongInfoBottomSheet) {
        SongInfoBottomSheet(
            song = currentSong,
            isFavorite = isFavorite,
            onToggleFavorite = onFavoriteToggle,
            onDismiss = { showSongInfoBottomSheet = false }
        )
    }
}

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
            .shadow(2.dp, CircleShape, clip = false)
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
 * Format milliseconds into m:ss time string.
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
