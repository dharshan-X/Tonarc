package com.quietrays.tonarc.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.ContextualMix
import com.quietrays.tonarc.data.MixMood
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.presentation.components.resolveNavBarOccupiedHeight
import com.quietrays.tonarc.presentation.components.subcomps.EnhancedSongListItem
import com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel
import com.quietrays.tonarc.presentation.viewmodel.PlaylistViewModel
import com.quietrays.tonarc.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.Calendar

fun getMoodGradientColors(mood: MixMood): List<Color> {
    return when (mood) {
        MixMood.MORNING_FOCUS -> listOf(
            Color(0xFFF59E0B), // Warm Amber
            Color(0xFFEA580C), // Orange
            Color(0xFFD97706)  // Golden Amber
        )
        MixMood.ENERGY_BOOST -> listOf(
            Color(0xFFFF5722), // Vibrant Coral
            Color(0xFFE91E63), // Magenta
            Color(0xFF00BCD4)  // Cyan
        )
        MixMood.EVENING_CHILL -> listOf(
            Color(0xFF3F51B5), // Deep Indigo
            Color(0xFF7C4DFF), // Violet
            Color(0xFFE91E63)  // Rose
        )
        MixMood.MIDNIGHT_LOFI -> listOf(
            Color(0xFF0F172A), // Dark Slate / Obsidian
            Color(0xFF0D9488), // Teal
            Color(0xFF1E293B)  // Obsidian Blue
        )
        MixMood.DISCOVERY_RADAR -> listOf(
            Color(0xFF10B981), // Emerald
            Color(0xFF06B6D4)  // Cyan
        )
    }
}

fun getMoodGreeting(mood: MixMood, calendar: Calendar = Calendar.getInstance()): String {
    return when (mood) {
        MixMood.MORNING_FOCUS -> "Good Morning"
        MixMood.ENERGY_BOOST -> "Afternoon Energy"
        MixMood.EVENING_CHILL -> "Good Evening"
        MixMood.MIDNIGHT_LOFI -> "Late Night Vibes"
        MixMood.DISCOVERY_RADAR -> "Discovery Radar"
    }
}

@Composable
fun MoodFilterChipsRow(
    selectedMood: MixMood,
    onMoodSelected: (MixMood) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(MixMood.entries, key = { it.name }) { mood ->
            MoodFilterChip(
                mood = mood,
                selected = mood == selectedMood,
                onClick = { onMoodSelected(mood) }
            )
        }
    }
}

@Composable
fun MoodFilterChip(
    mood: MixMood,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moodColors = getMoodGradientColors(mood)
    val accentColor = moodColors.first()

    val containerColor by animateColorAsState(
        targetValue = if (selected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300),
        label = "MoodChipContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "MoodChipContentColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "MoodChipBorderColor"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Tab },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = borderColor
        ),
        tonalElevation = if (selected) 4.dp else 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = mood.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DailyMixSection(
    songs: ImmutableList<Song>,
    playerViewModel: PlayerViewModel,
    onClickOpen: () -> Unit = {},
    onNavigateToAlbum: (Song) -> Unit = {},
    onNavigateToArtist: (Song) -> Unit = {},
    onNavigateToGenre: (Song) -> Unit = {},
) {
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val dailyMixQueueName = stringResource(R.string.presentation_batch_g_daily_mix_queue_name)

    val contextualMixes by playerViewModel.contextualMixes.collectAsStateWithLifecycle()
    val selectedMood by playerViewModel.selectedMood.collectAsStateWithLifecycle()

    val activeMix = remember(contextualMixes, selectedMood) {
        contextualMixes.firstOrNull { it.mood == selectedMood }
    }
    val displaySongs = remember(activeMix, songs) {
        activeMix?.songs?.takeIf { it.isNotEmpty() }?.toImmutableList() ?: songs
    }
    val currentQueueName = remember(activeMix, selectedMood, dailyMixQueueName) {
        activeMix?.title ?: selectedMood.displayName
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(16.dp))
        MoodFilterChipsRow(
            selectedMood = selectedMood,
            onMoodSelected = { playerViewModel.selectMood(it) },
            modifier = Modifier.padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            DailyMixCard(
                songs = displaySongs,
                selectedMood = selectedMood,
                activeMix = activeMix,
                playerViewModel = playerViewModel,
                queueName = currentQueueName,
                onClickOpen = onClickOpen,
                onMoreOptionsClick = { song ->
                    playerViewModel.selectSongForInfo(song)
                    showSongInfoSheet = true
                }
            )
        }
    }

    if (showSongInfoSheet && selectedSongForInfo != null) {
        val song = selectedSongForInfo!!
        SongInfoBottomSheet(
            song = song,
            isFavorite = favoriteSongIds.contains(song.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = {
                showSongInfoSheet = false
                showPlaylistBottomSheet = false
            },
            onPlaySong = {
                playerViewModel.showAndPlaySong(
                    song = song,
                    contextSongs = displaySongs,
                    queueName = currentQueueName,
                    isVoluntaryPlay = false
                )
                showSongInfoSheet = false
            },
            onStartRadio = {
                playerViewModel.playInstantRadio(song)
                showSongInfoSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(song)
                showSongInfoSheet = false
            },
            onAddNextToQueue = {
                playerViewModel.addSongNextToQueue(song)
                showSongInfoSheet = false
            },
            onAddToPlayList = {
                showPlaylistBottomSheet = true
            },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                onNavigateToAlbum(song)
                showSongInfoSheet = false
            },
            onNavigateToArtist = {
                onNavigateToArtist(song)
                showSongInfoSheet = false
            },
            onNavigateToGenre = {
                onNavigateToGenre(song)
                showSongInfoSheet = false
            },
            onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                playerViewModel.editSongMetadata(
                    song,
                    newTitle,
                    newArtist,
                    newAlbum,
                    newAlbumArtist,
                    newComposer,
                    newGenre,
                    newLyrics,
                    newTrackNumber,
                    newDiscNumber,
                    replayGainTrackGainDb,
                    replayGainAlbumGainDb,
                    coverArtUpdate
                )
            },
            removeFromListTrigger = {}
        )

        if (showPlaylistBottomSheet) {
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = persistentListOf(song),
                onDismiss = { showPlaylistBottomSheet = false },
                bottomBarHeight = bottomBarHeightDp,
                playerViewModel = playerViewModel,
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DailyMixCard(
    songs: ImmutableList<Song>,
    selectedMood: MixMood,
    activeMix: ContextualMix?,
    queueName: String,
    onClickOpen: () -> Unit,
    playerViewModel: PlayerViewModel,
    onMoreOptionsClick: (Song) -> Unit
) {
    val headerSongs = remember(songs) { songs.take(3).toImmutableList() }
    val visibleSongs = remember(songs) { songs.take(4).toImmutableList() }
    val cornerRadius = 30.dp
    Card(
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusBR = cornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = cornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = cornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusTL = cornerRadius,
            smoothnessAsPercentBR = 60
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DailyMixHeader(
                thumbnails = headerSongs,
                selectedMood = selectedMood,
                activeMix = activeMix
            )
            DailyMixSongList(
                songs = visibleSongs,
                playbackQueue = songs,
                playerViewModel = playerViewModel,
                queueName = queueName,
                onMoreOptionsClick = onMoreOptionsClick
            )
            ViewAllDailyMixButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = 6.dp,
                        bottom = 6.dp
                    ),
                onClickOpen = {
                    onClickOpen()
                },
            )
        }
    }
}

@Composable
fun DailyMixHeader(
    thumbnails: ImmutableList<Song>,
    selectedMood: MixMood = MixMood.MORNING_FOCUS,
    activeMix: ContextualMix? = null
) {
    val titleStyle = rememberDailyMixTitleStyle()
    val moodColors = getMoodGradientColors(selectedMood)

    val color0 by animateColorAsState(targetValue = moodColors[0], animationSpec = tween(500), label = "headerGrad0")
    val color1 by animateColorAsState(targetValue = moodColors[1], animationSpec = tween(500), label = "headerGrad1")
    val color2 by animateColorAsState(
        targetValue = if (moodColors.size > 2) moodColors[2] else moodColors[1],
        animationSpec = tween(500),
        label = "headerGrad2"
    )
    val animatedColors = if (moodColors.size > 2) listOf(color0, color1, color2) else listOf(color0, color1)

    fun shapeConditionalModifier(index: Int): Modifier {
        if (index == 0) {
            return Modifier.size(50.dp).padding(top = 4.dp)
        } else {
            if (index == 1) {
                return Modifier.size(44.dp).aspectRatio(1f).padding(bottom = 4.dp)
            }
            return Modifier.size(48.dp)
        }
    }

    val headingText = remember(selectedMood, activeMix) {
        if (selectedMood == MixMood.DISCOVERY_RADAR) "DISCOVERY RADAR"
        else getMoodGreeting(selectedMood).uppercase()
    }
    val subtitleText = remember(selectedMood, activeMix) {
        activeMix?.subtitle ?: selectedMood.subtitle
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(
                brush = Brush.horizontalGradient(colors = animatedColors)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = headingText,
                    style = titleStyle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier.padding(start = 1.dp),
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy((-16).dp)
            ) {
                thumbnails.forEachIndexed { index, song ->
                    val modifier = shapeConditionalModifier(index)
                    Box(
                        modifier = modifier
                            .clip(threeShapeSwitch(index))
                            .border(2.dp, MaterialTheme.colorScheme.surface, threeShapeSwitch(index))
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun threeShapeSwitch(index: Int, thirdShapeCornerRadius: Dp = 16.dp): Shape {
    return when (index) {
        0 -> RoundedStarShape(
            sides = 6,
            rotation = 10f
        )
        1 -> CircleShape
        2 -> AbsoluteSmoothCornerShape(
            cornerRadiusBL = thirdShapeCornerRadius,
            cornerRadiusTR = thirdShapeCornerRadius,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentTR = 60,
            cornerRadiusTL = thirdShapeCornerRadius,
            cornerRadiusBR = thirdShapeCornerRadius,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentBR = 60
        )
        else -> CircleShape
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DailyMixSongList(
    songs: ImmutableList<Song>,
    playbackQueue: ImmutableList<Song>,
    playerViewModel: PlayerViewModel,
    queueName: String,
    onMoreOptionsClick: (Song) -> Unit
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val itemContainerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        songs.forEach { song ->
            EnhancedSongListItem(
                song = song,
                isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                isPlaying = stablePlayerState.isPlaying && stablePlayerState.currentSong?.id == song.id,
                containerColorOverride = itemContainerColor,
                onMoreOptionsClick = onMoreOptionsClick,
                customShape = RoundedCornerShape(10.dp),
                showAlbumArt = false,
                onClick = {
                    playerViewModel.showAndPlaySong(
                        song = song,
                        contextSongs = playbackQueue,
                        queueName = queueName,
                        isVoluntaryPlay = false
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ViewAllDailyMixButton(
    modifier: Modifier = Modifier,
    onClickOpen: () -> Unit
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = {
            onClickOpen()
        },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.Transparent
        ),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 10.dp,
            cornerRadiusTR = 10.dp,
            smoothnessAsPercentTL = 70,
            smoothnessAsPercentTR = 70,
            cornerRadiusBL = 60.dp,
            cornerRadiusBR = 60.dp,
            smoothnessAsPercentBL = 70,
            smoothnessAsPercentBR = 70
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_daily_mix_see_all),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Icon(
                painter = painterResource(R.drawable.rounded_arrow_forward_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberDailyMixTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(630),
                        FontVariation.width(136f),
                        FontVariation.grade(40),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(630),
            fontSize = 20.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.35).sp
        )
    }
}
