package com.quietrays.tonarc.presentation.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quietrays.tonarc.R
import com.quietrays.tonarc.presentation.viewmodel.PlaylistViewModel
import com.quietrays.tonarc.presentation.viewmodel.YouTubeImportState
import com.quietrays.tonarc.ui.theme.RoundedSans
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun ImportYouTubePlaylistDialog(
    visible: Boolean,
    playlistViewModel: PlaylistViewModel,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val importState by playlistViewModel.youtubeImportState.collectAsStateWithLifecycle()
    var inputUrl by rememberSaveable { mutableStateOf("") }
    var customTitle by rememberSaveable { mutableStateOf("") }
    var saveAsLocal by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            playlistViewModel.resetYouTubeImportState()
            inputUrl = ""
            customTitle = ""
            saveAsLocal = false
        }
    }

    LaunchedEffect(importState) {
        if (importState is YouTubeImportState.Success) {
            onDismiss()
        }
        if (importState is YouTubeImportState.Preview) {
            customTitle = (importState as YouTubeImportState.Preview).title
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
            playlistViewModel.resetYouTubeImportState()
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.youtube_import_dialog_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = RoundedSans,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.youtube_import_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            playlistViewModel.resetYouTubeImportState()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }

                // Input Field
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text(stringResource(R.string.youtube_import_url_hint)) },
                    placeholder = { Text("https://music.youtube.com/playlist?list=PL...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    inputUrl = clip.trim()
                                    playlistViewModel.previewYouTubePlaylist(inputUrl)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Paste from clipboard"
                            )
                        }
                    }
                )

                // State content
                when (val state = importState) {
                    is YouTubeImportState.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Fetching playlist from YouTube...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is YouTubeImportState.Preview -> {
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
                                        .data(state.thumbnailUrl)
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
                                        text = state.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${state.trackCount} tracks • Ready to import",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Custom Title Option
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text("Playlist Name in Library") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Option to clone as local playlist too
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveAsLocal,
                                onCheckedChange = { saveAsLocal = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.youtube_import_save_local_checkbox),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    is YouTubeImportState.Error -> {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            playlistViewModel.resetYouTubeImportState()
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    if (importState is YouTubeImportState.Preview) {
                        val preview = importState as YouTubeImportState.Preview
                        Button(
                            onClick = {
                                playlistViewModel.saveYouTubePlaylist(
                                    playlistId = preview.playlistId,
                                    customTitle = customTitle,
                                    saveAsLocal = saveAsLocal
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.youtube_import_save_btn))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                playlistViewModel.previewYouTubePlaylist(inputUrl)
                            },
                            enabled = inputUrl.isNotBlank() && importState !is YouTubeImportState.Loading
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.youtube_import_fetch_btn))
                        }
                    }
                }
            }
        }
    }
}
