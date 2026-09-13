package com.quietrays.tonarc.presentation.components.player

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
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
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Wave-Card Player: A Material 3 Expressive full-screen player experience
 * featuring an upper 66% vertical album artwork area with smooth gradient fade,
 * a middle 20% wave card with intertwined animated sine wave ribbons, a sleek
 * thumb-free center progress slider, dedicated zero-elevation lyrics toggle,
 * and bottom 14% controls rendered with solid anime outlines and offset shadows.
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

    // Dynamic color tokens resolved from the active MaterialTheme
    val colorScheme = MaterialTheme.colorScheme
    val cardColor = colorScheme.surfaceContainerHigh
    val bottomSurfaceColor = colorScheme.surfaceContainerLowest
    val animeBorderColor = Color(0xFF1A1A1A)
    val animeShadowColor = Color(0xFF1A1A1A)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .padding(bottom = navBarPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Upper Section (~64% vertical weight): Full-bleed vertical album art + top controls + song title overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.64f)
            ) {
                // Album Artwork
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
                                        colorScheme.surfaceContainerHigh
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // Bottom gradient fade transitioning smoothly into the wave card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.48f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    cardColor.copy(alpha = 0.82f),
                                    cardColor
                                )
                            )
                        )
                )

                // Top action bar overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding + 6.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Collapse button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surface.copy(alpha = 0.72f))
                            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onCollapse),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Action buttons: Audio Tools, Queue, Favorite
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Audio Tools / Equalizer
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isAudioToolsActive) colorScheme.primary
                                    else colorScheme.surface.copy(alpha = 0.72f)
                                )
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                                .clickable { showAudioToolsBottomSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Audio tools",
                                tint = if (isAudioToolsActive) colorScheme.onPrimary else colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Queue button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surface.copy(alpha = 0.72f))
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onShowQueueClicked),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Favorite button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surface.copy(alpha = 0.72f))
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                                .clickable(onClick = onFavoriteToggle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                                tint = if (isFavorite) colorScheme.error else colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Song Title & Artist text overlay at bottom of art section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentSong.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp
                        ),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Middle Section (~22% vertical weight): Wave Card container with rounded bottom corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.22f)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(cardColor)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Sine Wave & Lyrics Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Intertwined Animated Sine Wave Ribbons
                        IntertwinedSineWaveVisualizer(
                            isPlaying = isPlaying,
                            waveColor1 = colorScheme.primary.copy(alpha = 0.52f),
                            waveColor2 = colorScheme.tertiary.copy(alpha = 0.44f),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Dedicated Lyrics Button (no elevation, no shadow)
                        IconButton(
                            onClick = onLyricsClick,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_lyrics_24),
                                contentDescription = "Lyrics",
                                tint = colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Sleek Center Slider Without Thumb
                    SleekCenterScrubber(
                        currentPositionMs = currentPosition,
                        totalDurationMs = totalDuration,
                        trackColor = colorScheme.onSurface.copy(alpha = 0.16f),
                        activeColor = colorScheme.primary,
                        handleColor = colorScheme.onSurface,
                        textColor = colorScheme.onSurfaceVariant,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Bottom Section (~14% vertical weight): Controls surface with Anime Outline rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.14f)
                    .background(bottomSurfaceColor)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Toggle
                    AnimeButton(
                        onClick = onShuffleToggle,
                        shape = CircleShape,
                        containerColor = if (isShuffleEnabled) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                        contentColor = if (isShuffleEnabled) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                        borderColor = animeBorderColor,
                        shadowColor = animeShadowColor,
                        shadowOffset = 2.5.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Previous Button: Squircle with Anime Outline
                    AnimeButton(
                        onClick = onPrevious,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurface,
                        borderColor = animeBorderColor,
                        shadowColor = animeShadowColor,
                        modifier = Modifier.size(width = 62.dp, height = 54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play/Pause Button: Stadium Pill with Anime Outline
                    AnimeButton(
                        onClick = onPlayPause,
                        shape = RoundedCornerShape(28.dp),
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer,
                        borderColor = animeBorderColor,
                        shadowColor = animeShadowColor,
                        shadowOffset = 4.dp,
                        modifier = Modifier.size(width = 96.dp, height = 56.dp)
                    ) {
                        MorphingPlayPauseIcon(
                            isPlaying = isPlaying,
                            tint = colorScheme.onPrimaryContainer,
                            size = 32.dp
                        )
                    }

                    // Next Button: Squircle with Anime Outline
                    AnimeButton(
                        onClick = onNext,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurface,
                        borderColor = animeBorderColor,
                        shadowColor = animeShadowColor,
                        modifier = Modifier.size(width = 62.dp, height = 54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Repeat Toggle
                    AnimeButton(
                        onClick = onRepeatToggle,
                        shape = CircleShape,
                        containerColor = if (repeatMode != Player.REPEAT_MODE_OFF) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                        contentColor = if (repeatMode != Player.REPEAT_MODE_OFF) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                        borderColor = animeBorderColor,
                        shadowColor = animeShadowColor,
                        shadowOffset = 2.5.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            modifier = Modifier.size(20.dp)
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

/**
 * Anime Button with solid black outline border, hard offset drop shadow,
 * and tactile bouncy press scaling.
 */
@Composable
private fun AnimeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderWidth: Dp = 2.dp,
    borderColor: Color = Color(0xFF1A1A1A),
    shadowOffset: Dp = 3.5.dp,
    shadowColor: Color = Color(0xFF1A1A1A),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "animeButtonScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        // Hard-edge offset shadow layer underneath
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(shape)
                .background(shadowColor)
        )

        // Foreground interactive surface with solid outline border
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(containerColor)
                .border(borderWidth, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
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
}

/**
 * Intertwined animated sine wave ribbons.
 * Draws continuous, traveling sine waves on a Canvas reacting smoothly to playback state.
 */
@Composable
private fun IntertwinedSineWaveVisualizer(
    isPlaying: Boolean,
    waveColor1: Color,
    waveColor2: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sineWaves")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 3200 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -(2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 4600 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val path1 = Path()
        val path2 = Path()

        val amplitude1 = height * 0.32f
        val amplitude2 = height * 0.24f

        val step = 4f
        var x = 0f

        val wavelength1 = width * 0.65f
        val wavelength2 = width * 0.48f

        while (x <= width) {
            val y1 = centerY + amplitude1 * sin((x / wavelength1) * 2 * Math.PI + phase1).toFloat()
            val y2 = centerY + amplitude2 * sin((x / wavelength2) * 2 * Math.PI + phase2).toFloat()

            if (x == 0f) {
                path1.moveTo(x, y1)
                path2.moveTo(x, y2)
            } else {
                path1.lineTo(x, y1)
                path2.lineTo(x, y2)
            }
            x += step
        }

        drawPath(
            path = path1,
            color = waveColor1,
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = path2,
            color = waveColor2,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Sleek center progress scrubber without a bulky circular thumb knob.
 * Features a clean rounded track with a vertical pill handle and tabular time indicators.
 */
@Composable
private fun SleekCenterScrubber(
    currentPositionMs: Long,
    totalDurationMs: Long,
    trackColor: Color,
    activeColor: Color,
    handleColor: Color,
    textColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = totalDurationMs.coerceAtLeast(1L)
    val realFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayFraction = if (isScrubbing) scrubFraction else realFraction
    val displayedMs = (displayFraction * safeDuration).toLong()

    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((newFraction * safeDuration).toLong())
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
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val handleWidthDp = 5.dp
            val handleHeightDp = 16.dp

            // Inactive track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(trackColor)
            )

            // Active track
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayFraction)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )

            // Sleek capsule handle (no bulky round knob)
            val handleOffsetXDp = (displayFraction * maxWidth.value).dp - (handleWidthDp / 2)
            Box(
                modifier = Modifier
                    .offset(x = handleOffsetXDp.coerceAtLeast(0.dp))
                    .size(width = handleWidthDp, height = handleHeightDp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(handleColor)
                    .border(0.5.dp, trackColor, RoundedCornerShape(2.5.dp))
            )
        }

        // Time indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayedMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = textColor
            )
            Text(
                text = "-" + formatDuration((safeDuration - displayedMs).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = textColor
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
