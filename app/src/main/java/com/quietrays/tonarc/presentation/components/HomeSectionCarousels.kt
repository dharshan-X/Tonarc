package com.quietrays.tonarc.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.model.Album
import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.presentation.components.subcomps.PlayingEqIcon
import com.quietrays.tonarc.ui.theme.RoundedSans
import com.quietrays.tonarc.ui.theme.ShapeCache
import kotlinx.collections.immutable.ImmutableList

@Composable
fun HomeSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    seeAllText: String = "View More",
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onSeeAllClick != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                onClick = onSeeAllClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = seeAllText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeSongCard(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artShape = ShapeCache.smooth16
    val borderColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
    }

    Column(
        modifier = modifier
            .width(144.dp)
            .clip(artShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(artShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = if (isCurrentSong) 1.5.dp else 0.75.dp,
                    color = borderColor,
                    shape = artShape
                )
        ) {
            SmartImage(
                model = song.albumArtUriString ?: song.path,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isCurrentSong) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingEqIcon(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                isPlaying = isPlaying,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = song.displayArtist.ifBlank { song.artist },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HomePlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = ShapeCache.smooth20

    Surface(
        onClick = onClick,
        modifier = modifier.width(164.dp),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 0.75.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                if (!playlist.coverImageUri.isNullOrBlank()) {
                    SmartImage(
                        model = playlist.coverImageUri,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f)),
                                startY = 80f
                            )
                        )
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (playlist.source == "YOUTUBE") "YouTube Music" else "Community Mix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HorizontalSongCarouselSection(
    title: String,
    subtitle: String? = null,
    songs: ImmutableList<Song>,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    seeAllText: String = "View More",
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val distinctSongs = remember(songs) { songs.distinctBy { it.id } }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle,
            seeAllText = seeAllText,
            onSeeAllClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = distinctSongs,
                key = { index, song -> "${song.id}_$index" }
            ) { _, song ->
                HomeSongCard(
                    song = song,
                    isCurrentSong = currentPlayingSongId == song.id,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
fun QuickPicksShelfSection(
    title: String,
    subtitle: String? = null,
    songs: ImmutableList<Song>,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    seeAllText: String = "View More",
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val columns = remember(songs) {
        songs.distinctBy { it.id }.take(16).chunked(4)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle,
            seeAllText = seeAllText,
            onSeeAllClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = columns,
                key = { colIndex, column -> "quick_picks_col_${colIndex}_${column.firstOrNull()?.id}" }
            ) { _, columnSongs ->
                Column(
                    modifier = Modifier.width(296.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    columnSongs.forEach { song ->
                        QuickPicksTrackRow(
                            song = song,
                            isCurrentSong = currentPlayingSongId == song.id,
                            isPlaying = isPlaying,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPicksTrackRow(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val rowShape = ShapeCache.smooth14
    val containerColor = if (isCurrentSong) {
        colors.primaryContainer.copy(alpha = 0.42f)
    } else {
        colors.surfaceContainerLow
    }

    Surface(
        onClick = onClick,
        shape = rowShape,
        color = containerColor,
        border = if (isCurrentSong) {
            BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(ShapeCache.smooth12)
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = song.albumArtUriString ?: song.path,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isCurrentSong) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.displayArtist.ifBlank { song.artist },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrentSong) {
                PlayingEqIcon(
                    color = colors.primary,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(width = 18.dp, height = 16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun QuickPicksShelfSkeletonSection(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            HomeSectionHeader(
                title = title,
                subtitle = subtitle
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(2) {
                Column(
                    modifier = Modifier.width(296.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) {
                        Surface(
                            shape = ShapeCache.smooth14,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(ShapeCache.smooth12),
                                    shape = ShapeCache.smooth12
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.75f)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.45f)
                                            .height(11.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalPlaylistCarouselSection(
    title: String,
    subtitle: String? = null,
    playlists: ImmutableList<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) return

    val distinctPlaylists = remember(playlists) { playlists.distinctBy { it.id } }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = distinctPlaylists,
                key = { index, playlist -> "${playlist.id}_$index" }
            ) { _, playlist ->
                HomePlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
fun HomeAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artShape = ShapeCache.smooth20

    Column(
        modifier = modifier
            .width(152.dp)
            .clip(artShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(artShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f))
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                    shape = artShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!album.albumArtUriString.isNullOrBlank()) {
                SmartImage(
                    model = album.albumArtUriString,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                            startY = 100f
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = album.artist.ifBlank { "YouTube Music" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HorizontalAlbumCarouselSection(
    title: String,
    subtitle: String? = null,
    albums: ImmutableList<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) return

    val distinctAlbums = remember(albums) { albums.distinctBy { it.id } }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = distinctAlbums,
                key = { index, album -> "${album.id}_$index" }
            ) { _, album ->
                HomeAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
fun HorizontalArtistCarouselSection(
    title: String,
    subtitle: String? = null,
    artists: List<String>,
    artistImages: Map<String, String?> = emptyMap(),
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) return

    val distinctArtists = remember(artists) { artists.distinct() }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = title,
            subtitle = subtitle
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = distinctArtists,
                key = { index, artistName -> "${artistName}_$index" }
            ) { _, artistName ->
                HomeArtistCard(
                    artistName = artistName,
                    imageUrl = artistImages[artistName],
                    onClick = { onArtistClick(artistName) }
                )
            }
        }
    }
}

@Composable
fun HomeArtistCard(
    artistName: String,
    imageUrl: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(108.dp)
            .clip(ShapeCache.smooth16)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
                .border(
                    width = 1.25.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            SmartImage(
                model = imageUrl,
                contentDescription = artistName,
                shape = CircleShape,
                placeholderResId = R.drawable.ic_music_placeholder,
                errorResId = R.drawable.ic_music_placeholder,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artistName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HomeSongCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(144.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(144.dp)
                .clip(ShapeCache.smooth16),
            shape = ShapeCache.smooth16
        )

        Spacer(modifier = Modifier.height(8.dp))

        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
fun HomeAlbumCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(152.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(152.dp)
                .clip(ShapeCache.smooth20),
            shape = ShapeCache.smooth20
        )

        Spacer(modifier = Modifier.height(8.dp))

        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.50f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
fun HomeArtistCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(108.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            shape = CircleShape
        )

        Spacer(modifier = Modifier.height(8.dp))

        ShimmerBox(
            modifier = Modifier
                .width(74.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
fun HorizontalSongCarouselSkeletonSection(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            HomeSectionHeader(
                title = title,
                subtitle = subtitle
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .width(160.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(5) {
                HomeSongCardSkeleton()
            }
        }
    }
}

@Composable
fun HorizontalAlbumCarouselSkeletonSection(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            HomeSectionHeader(
                title = title,
                subtitle = subtitle
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .width(180.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .width(120.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(5) {
                HomeAlbumCardSkeleton()
            }
        }
    }
}

@Composable
fun HorizontalArtistCarouselSkeletonSection(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            HomeSectionHeader(
                title = title,
                subtitle = subtitle
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .width(150.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) {
            items(6) {
                HomeArtistCardSkeleton()
            }
        }
    }
}
