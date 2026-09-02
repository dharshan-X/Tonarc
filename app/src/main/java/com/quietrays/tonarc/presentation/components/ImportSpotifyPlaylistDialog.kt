package com.quietrays.tonarc.presentation.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quietrays.tonarc.R
import com.quietrays.tonarc.presentation.viewmodel.PlaylistViewModel
import com.quietrays.tonarc.presentation.viewmodel.SpotifyImportState
import com.quietrays.tonarc.ui.theme.RoundedSans
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun ImportSpotifyPlaylistDialog(
    visible: Boolean,
    playlistViewModel: PlaylistViewModel,
    initialUrl: String? = null,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val importState by playlistViewModel.spotifyImportState.collectAsStateWithLifecycle()
    var inputUrl by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var customTitle by rememberSaveable { mutableStateOf("") }
    var saveAsCloud by rememberSaveable { mutableStateOf(true) }
    var saveAsLocal by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(visible, initialUrl) {
        if (visible) {
            if (!initialUrl.isNullOrBlank()) {
                inputUrl = initialUrl
                playlistViewModel.previewSpotifyPlaylist(initialUrl)
            } else {
                playlistViewModel.resetSpotifyImportState()
                inputUrl = ""
                customTitle = ""
                saveAsCloud = true
                saveAsLocal = true
            }
        }
    }

    LaunchedEffect(importState) {
        if (importState is SpotifyImportState.Preview) {
            customTitle = (importState as SpotifyImportState.Preview).playlist.title
        }
    }

    val dialogShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 24.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = 24.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBL = 36.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBR = 36.dp,
        smoothnessAsPercentBR = 60
    )

    Dialog(
        onDismissRequest = {
            playlistViewModel.resetSpotifyImportState()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SpotifyGreen.copy(alpha = 0.16f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.spotify_import_dialog_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = RoundedSans,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.spotify_import_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            playlistViewModel.resetSpotifyImportState()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }

                // Input Field (shown in Idle, Loading, or Error states)
                if (importState is SpotifyImportState.Idle ||
                    importState is SpotifyImportState.Loading ||
                    importState is SpotifyImportState.Error
                ) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Spotify Playlist Link") },
                        placeholder = { Text(stringResource(R.string.spotify_import_url_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (inputUrl.isNotBlank()) {
                                    IconButton(onClick = { inputUrl = "" }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear text",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!clip.isNullOrBlank()) {
                                            inputUrl = clip.trim()
                                            playlistViewModel.previewSpotifyPlaylist(inputUrl)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // State Content
                when (val state = importState) {
                    is SpotifyImportState.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = SpotifyGreen
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is SpotifyImportState.Preview -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(state.playlist.coverUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                                    error = painterResource(R.drawable.ic_music_placeholder),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.playlist.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val authorText = state.playlist.author?.takeIf { it.isNotBlank() }?.let { "By $it • " }.orEmpty()
                                    val trackCount = if (state.playlist.trackCount > 0) state.playlist.trackCount else state.playlist.tracks.size
                                    Text(
                                        text = "$authorText$trackCount tracks • Ready to import",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SpotifyGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Custom Title Option
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text(stringResource(R.string.spotify_import_playlist_name_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Option 1: Cloud streamable playlist
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveAsCloud,
                                onCheckedChange = { saveAsCloud = it },
                                colors = CheckboxDefaults.colors(checkedColor = SpotifyGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.spotify_import_save_cloud_checkbox),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Option 2: Match with local library
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveAsLocal,
                                onCheckedChange = { saveAsLocal = it },
                                colors = CheckboxDefaults.colors(checkedColor = SpotifyGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.spotify_import_save_local_checkbox),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    is SpotifyImportState.Matching -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val progress = if (state.total > 0) {
                                state.current.toFloat() / state.total.toFloat()
                            } else {
                                0f
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = SpotifyGreen,
                                trackColor = SpotifyGreen.copy(alpha = 0.2f)
                            )

                            Text(
                                text = "Matching track ${state.current} of ${state.total}: ${state.currentTrackTitle}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "Matching songs against local library & YouTube Music...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is SpotifyImportState.Success -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SpotifyGreen.copy(alpha = 0.16f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(32.dp)
                                )
                            }
                            Text(
                                text = "Successfully imported ${state.matchedCount} of ${state.totalCount} tracks into '${state.playlistTitle}'!",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is SpotifyImportState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    else -> {}
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (val state = importState) {
                        is SpotifyImportState.Success -> {
                            Button(
                                onClick = {
                                    playlistViewModel.resetSpotifyImportState()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                            ) {
                                Text(stringResource(R.string.spotify_import_done_btn))
                            }
                        }

                        is SpotifyImportState.Matching -> {
                            // Non-interactive while matching is in progress
                        }

                        is SpotifyImportState.Preview -> {
                            TextButton(
                                onClick = {
                                    playlistViewModel.resetSpotifyImportState()
                                }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }

                            Button(
                                onClick = {
                                    playlistViewModel.saveSpotifyPlaylist(
                                        playlist = state.playlist,
                                        customTitle = customTitle.takeIf { it.isNotBlank() },
                                        saveAsCloud = saveAsCloud,
                                        saveAsLocal = saveAsLocal
                                    )
                                },
                                enabled = (saveAsCloud || saveAsLocal),
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.spotify_import_save_btn))
                            }
                        }

                        is SpotifyImportState.Error -> {
                            TextButton(
                                onClick = {
                                    playlistViewModel.resetSpotifyImportState()
                                    onDismiss()
                                }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (inputUrl.isNotBlank()) {
                                        playlistViewModel.previewSpotifyPlaylist(inputUrl)
                                    } else {
                                        playlistViewModel.resetSpotifyImportState()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.spotify_import_try_again_btn))
                            }
                        }

                        else -> { // Idle or Loading
                            TextButton(
                                onClick = {
                                    playlistViewModel.resetSpotifyImportState()
                                    onDismiss()
                                }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }

                            FilledTonalButton(
                                onClick = {
                                    playlistViewModel.previewSpotifyPlaylist(inputUrl)
                                },
                                enabled = inputUrl.isNotBlank() && importState !is SpotifyImportState.Loading
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.spotify_import_fetch_btn))
                            }
                        }
                    }
                }
            }
        }
    }
}
