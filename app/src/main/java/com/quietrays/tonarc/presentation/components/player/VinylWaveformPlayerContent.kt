package com.quietrays.tonarc.presentation.components.player

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class TurntableGestureMode {
    UNDECIDED,
    SLIDE,
    ROTATE
}

/**
 * Vinyl Waveform Player: A Material 3 Expressive full-screen player experience
 * featuring an authentic spinning grooved vinyl turntable with slide and rotational scrub gestures,
 * dynamic Monet theme background, live reactive waveform visualizer, tactile squircle playback controls,
 * and a balanced utility bar with dedicated lyrics integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val abRepeatState by playerViewModel.abRepeatState.collectAsStateWithLifecycle()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitchSemitones by playerViewModel.playbackPitchSemitones.collectAsStateWithLifecycle()

    val visualizerEnabled by playerViewModel.visualizerEnabled.collectAsStateWithLifecycle()
    val visualizerMode by playerViewModel.visualizerMode.collectAsStateWithLifecycle()
    val visualizerStyle by playerViewModel.visualizerStyle.collectAsStateWithLifecycle()

    val isAudioToolsActive = abRepeatState.isLoopActive ||
        kotlin.math.abs(playbackSpeed - 1.0f) > 0.01f ||
        playbackPitchSemitones != 0

    val playerAccentColor = LocalMaterialTheme.current.primary
    val playerOnAccentColor = LocalMaterialTheme.current.onPrimary

    val totalDuration = totalDurationProvider().coerceAtLeast(1L)
    val isPlaying = isPlayingProvider()
    val isFavorite = isFavoriteProvider()

    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
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
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    )
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
            // 1. Top Bar with Audio Tools conditionally displayed only when active/enabled
            VinylPlayerTopBar(
                accentColor = playerAccentColor,
                onAccentColor = playerOnAccentColor,
                isAudioToolsActive = isAudioToolsActive,
                visualizerEnabled = visualizerEnabled,
                onVisualizerBadgeClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showVisualizerBottomSheet = true
                },
                onAudioToolsClick = {
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

            // 3. Turntable Stage: Horizontal slide for track skip, circular drag for duration scrub
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
                    currentPositionMs = currentPositionProvider(),
                    totalDurationMs = totalDuration,
                    onSeek = onSeek,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onTogglePlayPause = onPlayPause
                )
            }

            // 4. Song-Generated Dynamic Waveform Scrubber
            VinylWaveformScrubber(
                bars = waveformBars,
                currentPositionProvider = currentPositionProvider,
                totalDuration = totalDuration,
                isPlaying = isPlaying,
                accentColor = playerAccentColor,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Material 3 Expressive Playback Controls Row with Spring Physics
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

            // 6. Clean Balanced Utility Bar: Favorite | Lyrics | Queue
            VinylCleanUtilityBar(
                isFavorite = isFavorite,
                accentColor = playerAccentColor,
                onFavoriteToggle = onFavoriteToggle,
                onLyricsClick = onLyricsClick,
                onQueueClick = onShowQueueClicked
            )
        }
    }

    // Interactive Audio Tools Sheet (Visible when opened from active tools indicator)
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

    // Visualizer Sheet
    if (showVisualizerBottomSheet) {
        VisualizerBottomSheet(
            isEnabled = visualizerEnabled,
            selectedMode = visualizerMode,
            selectedStyle = visualizerStyle,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionProvider(),
            onToggleEnabled = { playerViewModel.setVisualizerEnabled(it) },
            onSelectMode = { playerViewModel.setVisualizerMode(it) },
            onSelectStyle = { playerViewModel.setVisualizerStyle(it) },
            onDismiss = { showVisualizerBottomSheet = false }
        )
    }

    // Full Lyrics Sheet
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

/**
 * Top bar with optional visualizer badge, conditional active audio tools button, queue, and collapse actions.
 */
@Composable
private fun VinylPlayerTopBar(
    accentColor: Color,
    onAccentColor: Color,
    isAudioToolsActive: Boolean,
    visualizerEnabled: Boolean,
    onVisualizerBadgeClick: () -> Unit,
    onAudioToolsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onCollapseClick: () -> Unit
) {
    val audioToolsContentDesc = stringResource(R.string.audio_tools_title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visualizer / Waveform Badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (visualizerEnabled) accentColor
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .clickable(onClick = onVisualizerBadgeClick)
                .semantics {
                    role = Role.Button
                    contentDescription = "Visualizer settings"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = if (visualizerEnabled) onAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        // Top Right Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Audio Tools button: visible only if audio tools is active/modified
            if (isAudioToolsActive) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f))
                        .clickable(onClick = onAudioToolsClick)
                        .semantics {
                            role = Role.Button
                            contentDescription = audioToolsContentDesc
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

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
 * continuous 33.3 RPM rotation, horizontal slide gesture to skip songs,
 * and manual circular rotation around the center to scrub song duration.
 */
@Composable
private fun VinylTurntableStage(
    song: Song,
    diameter: Dp,
    isPlaying: Boolean,
    accentColor: Color,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isManualRotating by remember { mutableStateOf(false) }
    var scrubTargetPositionMs by remember { mutableLongStateOf(0L) }
    var lastHapticTickSec by remember { mutableLongStateOf(-1L) }

    val slideOffsetAnimatable = remember { Animatable(0f) }

    // Dynamic vinyl grooves based on current Monet theme
    val grooveTone1 = MaterialTheme.colorScheme.surfaceContainerLowest
    val grooveTone2 = MaterialTheme.colorScheme.surfaceContainerLow
    val grooveTone3 = MaterialTheme.colorScheme.surfaceContainer

    // Automatic 33.3 RPM rotation during playback when not being manually scrubbed
    LaunchedEffect(isPlaying, isManualRotating) {
        if (isPlaying && !isManualRotating) {
            var lastNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val deltaSec = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastNanos = frameNanos
                    rotationAngle = (rotationAngle + 200f * deltaSec) % 360f
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(diameter)
            .graphicsLayer {
                translationX = slideOffsetAnimatable.value
            }
            .pointerInput(song.id, totalDurationMs) {
                val centerPx = Offset(size.width / 2f, size.height / 2f)
                val outerRadiusPx = minOf(size.width, size.height) / 2f
                val innerArtRadiusPx = outerRadiusPx * 0.55f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        val distanceFromCenter = (downPos - centerPx).getDistance()

                        var lastAngle = atan2(
                            (downPos.y - centerPx.y).toDouble(),
                            (downPos.x - centerPx.x).toDouble()
                        ).toFloat()

                        var totalAngleDelta = 0f
                        var gestureMode = TurntableGestureMode.UNDECIDED

                        var currentScrubPos = currentPositionMs
                        scrubTargetPositionMs = currentScrubPos

                        val isTouchOnGrooves = distanceFromCenter in (innerArtRadiusPx * 0.75f)..outerRadiusPx

                        while (true) {
                            val event = awaitPointerEvent()
                            val dragEvent = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!dragEvent.pressed) break

                            val currentPos = dragEvent.position
                            val absDx = kotlin.math.abs(currentPos.x - downPos.x)
                            val absDy = kotlin.math.abs(currentPos.y - downPos.y)

                            val currentAngle = atan2(
                                (currentPos.y - centerPx.y).toDouble(),
                                (currentPos.x - centerPx.x).toDouble()
                            ).toFloat()

                            var angleDiff = currentAngle - lastAngle
                            while (angleDiff > Math.PI) angleDiff -= (2 * Math.PI).toFloat()
                            while (angleDiff < -Math.PI) angleDiff += (2 * Math.PI).toFloat()

                            val angleDiffDegrees = Math.toDegrees(angleDiff.toDouble()).toFloat()
                            totalAngleDelta += angleDiffDegrees
                            lastAngle = currentAngle

                            if (gestureMode == TurntableGestureMode.UNDECIDED) {
                                val absAngle = kotlin.math.abs(totalAngleDelta)
                                if (isTouchOnGrooves && absAngle > 6f) {
                                    gestureMode = TurntableGestureMode.ROTATE
                                    isManualRotating = true
                                    dragEvent.consume()
                                } else if (absDx > 24f && absDx > absDy * 1.3f) {
                                    gestureMode = TurntableGestureMode.SLIDE
                                    dragEvent.consume()
                                } else if (absAngle > 10f) {
                                    gestureMode = TurntableGestureMode.ROTATE
                                    isManualRotating = true
                                    dragEvent.consume()
                                }
                            }

                            if (gestureMode == TurntableGestureMode.ROTATE) {
                                dragEvent.consume()
                                rotationAngle = (rotationAngle + angleDiffDegrees) % 360f

                                // 360-degree rotation maps to 30 seconds of seeking
                                val seekDeltaMs = (angleDiffDegrees / 360f * 30_000L).toLong()
                                currentScrubPos = (currentScrubPos + seekDeltaMs).coerceIn(0L, totalDurationMs)
                                scrubTargetPositionMs = currentScrubPos

                                val currentSec = currentScrubPos / 1000L
                                if (currentSec != lastHapticTickSec) {
                                    lastHapticTickSec = currentSec
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            } else if (gestureMode == TurntableGestureMode.SLIDE) {
                                dragEvent.consume()
                                val deltaX = currentPos.x - dragEvent.previousPosition.x
                                coroutineScope.launch {
                                    slideOffsetAnimatable.snapTo(slideOffsetAnimatable.value + deltaX * 0.85f)
                                }
                            }
                        }

                        // Gesture completion
                        if (gestureMode == TurntableGestureMode.ROTATE) {
                            isManualRotating = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeek(currentScrubPos)
                        } else if (gestureMode == TurntableGestureMode.SLIDE) {
                            val currentOffset = slideOffsetAnimatable.value
                            val swipeThreshold = 75.dp.toPx()
                            if (currentOffset < -swipeThreshold) {
                                // Slide left: Skip to Next Song with fluid spring transition
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    slideOffsetAnimatable.animateTo(-450f, tween(160, easing = FastOutLinearInEasing))
                                    onNext()
                                    slideOffsetAnimatable.snapTo(450f)
                                    slideOffsetAnimatable.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            } else if (currentOffset > swipeThreshold) {
                                // Slide right: Skip to Previous Song with fluid spring transition
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    slideOffsetAnimatable.animateTo(450f, tween(160, easing = FastOutLinearInEasing))
                                    onPrevious()
                                    slideOffsetAnimatable.snapTo(-450f)
                                    slideOffsetAnimatable.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            } else {
                                // Rebound back to center
                                coroutineScope.launch {
                                    slideOffsetAnimatable.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                    )
                                }
                            }
                        } else if (gestureMode == TurntableGestureMode.UNDECIDED) {
                            onTogglePlayPause()
                        }
                    }
                },
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

            // Concentric vinyl shaded body dynamically tinted with Monet surface tokens
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        grooveTone1,
                        grooveTone2,
                        grooveTone1,
                        grooveTone3,
                        grooveTone1
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Outer rim highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = maxRadius - 1f,
                center = center,
                style = Stroke(width = 1.2f)
            )

            // Concentric Grooves
            val grooveFractions = listOf(0.93f, 0.87f, 0.81f, 0.75f, 0.69f, 0.63f)
            val grooveColor = Color.White.copy(alpha = 0.05f)
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
                .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape),
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
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
        }

        // Floating Duration Scrub Badge displayed during manual disc rotation
        androidx.compose.animation.AnimatedVisibility(
            visible = isManualRotating,
            enter = fadeIn(tween(120)) + scaleIn(tween(120)),
            exit = fadeOut(tween(160)) + scaleOut(tween(160)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                    Text(
                        text = formatDuration(scrubTargetPositionMs),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * Interactive Audio Amplitude Waveform Scrubber:
 * Dynamic acoustic traveling wave synchronized with playback, dynamic playhead bounce,
 * elapsed & total times, and responsive touch seeking.
 */
@Composable
private fun VinylWaveformScrubber(
    bars: List<Float>,
    currentPositionProvider: () -> Long,
    totalDuration: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }

    val currentPosition = if (isDragging) dragPositionMs else currentPositionProvider()
    val progressFraction = (currentPosition.toFloat() / totalDuration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    // Dynamic wave phase for live traveling acoustic rhythm during active playback
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformPulseTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

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
                        val isPlayed = i < playedCount

                        // Fluid acoustic traveling pulse across waveform bars
                        val dynamicMultiplier = if (isPlaying) {
                            val travelingPulse = sin(wavePhase + (i * 0.36)).toFloat()
                            val playheadDistance = kotlin.math.abs(i - playedCount)
                            val playheadBounce = if (playheadDistance <= 2) {
                                (1.0f + 0.22f * sin(wavePhase * 2.0 + i).toFloat())
                            } else 1.0f

                            (1.0f + 0.16f * travelingPulse) * playheadBounce
                        } else {
                            1.0f
                        }

                        val baseBarHeight = (bars[i] * maxHeight).coerceIn(6.dp.toPx(), maxHeight)
                        val barHeight = (baseBarHeight * dynamicMultiplier).coerceIn(4.dp.toPx(), maxHeight)
                        val top = centerY - barHeight / 2f
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
 * Material 3 Expressive playback controls row with realistic bouncy spring physics on press/release,
 * hero play/pause morphing icon, and dynamic tonal skip and toggle buttons.
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

    val bouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Repeat Button (Tonal toggle with press physics)
        val repeatInteractionSource = remember { MutableInteractionSource() }
        val isRepeatPressed by repeatInteractionSource.collectIsPressedAsState()
        val repeatScale by animateFloatAsState(
            targetValue = if (isRepeatPressed) 0.88f else 1f,
            animationSpec = bouncySpring,
            label = "repeat_scale"
        )
        val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF

        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(repeatScale)
                .clip(CircleShape)
                .background(if (isRepeatActive) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isRepeatActive) accentColor.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    CircleShape
                )
                .clickable(
                    interactionSource = repeatInteractionSource,
                    indication = null
                ) {
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
                tint = if (isRepeatActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Previous Button (56dp Tonal Squircle with bouncy spring physics)
        val prevInteractionSource = remember { MutableInteractionSource() }
        val isPrevPressed by prevInteractionSource.collectIsPressedAsState()
        val prevScale by animateFloatAsState(
            targetValue = if (isPrevPressed) 0.88f else 1f,
            animationSpec = bouncySpring,
            label = "prev_scale"
        )

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

        // Hero Play / Pause Button (78dp Dynamic Squircle with Morphing Icon & Bouncy Physics)
        val playInteractionSource = remember { MutableInteractionSource() }
        val isPlayPressed by playInteractionSource.collectIsPressedAsState()
        val playScale by animateFloatAsState(
            targetValue = if (isPlayPressed) 0.88f else 1f,
            animationSpec = bouncySpring,
            label = "play_scale"
        )
        val playCornerRadius by animateFloatAsState(
            targetValue = if (isPlaying) 30f else 24f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "play_corner"
        )

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
                MorphingPlayPauseIcon(
                    isPlaying = isPlaying,
                    tint = onAccentColor,
                    size = 36.dp
                )
            }
        }

        // Next Button (56dp Tonal Squircle with bouncy spring physics)
        val nextInteractionSource = remember { MutableInteractionSource() }
        val isNextPressed by nextInteractionSource.collectIsPressedAsState()
        val nextScale by animateFloatAsState(
            targetValue = if (isNextPressed) 0.88f else 1f,
            animationSpec = bouncySpring,
            label = "next_scale"
        )

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

        // Shuffle Button (Tonal toggle with press physics)
        val shuffleInteractionSource = remember { MutableInteractionSource() }
        val isShufflePressed by shuffleInteractionSource.collectIsPressedAsState()
        val shuffleScale by animateFloatAsState(
            targetValue = if (isShufflePressed) 0.88f else 1f,
            animationSpec = bouncySpring,
            label = "shuffle_scale"
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(shuffleScale)
                .clip(CircleShape)
                .background(if (isShuffleEnabled) accentColor.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    BorderStroke(
                        1.dp,
                        if (isShuffleEnabled) accentColor.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    CircleShape
                )
                .clickable(
                    interactionSource = shuffleInteractionSource,
                    indication = null
                ) {
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
                tint = if (isShuffleEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Balanced Utility Bar:
 * Favorite toggle | Dedicated Lyrics button | Queue sheet trigger.
 * Each item has equal weight, press spring physics, and strict single-line text constraints to eliminate overflow.
 */
@Composable
private fun VinylCleanUtilityBar(
    isFavorite: Boolean,
    accentColor: Color,
    onFavoriteToggle: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Favorite Button
            val favSource = remember { MutableInteractionSource() }
            val isFavPressed by favSource.collectIsPressedAsState()
            val favScale by animateFloatAsState(if (isFavPressed) 0.90f else 1f, bouncySpring, label = "fav_scale")

            Row(
                modifier = Modifier
                    .weight(1f)
                    .scale(favScale)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = favSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFavoriteToggle()
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Favorite",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. Lyrics Button
            val lyricsSource = remember { MutableInteractionSource() }
            val isLyricsPressed by lyricsSource.collectIsPressedAsState()
            val lyricsScale by animateFloatAsState(if (isLyricsPressed) 0.90f else 1f, bouncySpring, label = "lyrics_scale")

            Row(
                modifier = Modifier
                    .weight(1f)
                    .scale(lyricsScale)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = lyricsSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLyricsClick()
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_lyrics_24),
                    contentDescription = "Lyrics",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. Queue Button
            val queueSource = remember { MutableInteractionSource() }
            val isQueuePressed by queueSource.collectIsPressedAsState()
            val queueScale by animateFloatAsState(if (isQueuePressed) 0.90f else 1f, bouncySpring, label = "queue_scale")

            Row(
                modifier = Modifier
                    .weight(1f)
                    .scale(queueScale)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = queueSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onQueueClick()
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
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
