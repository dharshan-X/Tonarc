package com.quietrays.tonarc.desktop.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietrays.tonarc.desktop.model.DesktopNavDestination
import org.jetbrains.skia.Image

private fun DesktopNavDestination.icon(): ImageVector = when (this) {
    DesktopNavDestination.HOME -> Icons.Rounded.Home
    DesktopNavDestination.SEARCH -> Icons.Rounded.Search
    DesktopNavDestination.LIBRARY -> Icons.Rounded.LibraryMusic
    DesktopNavDestination.PLAYLISTS -> Icons.AutoMirrored.Rounded.PlaylistPlay
    DesktopNavDestination.EQUALIZER -> Icons.Rounded.Equalizer
    DesktopNavDestination.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun rememberTonarcLogoBitmap(): ImageBitmap? {
    return remember {
        runCatching {
            val stream = Thread.currentThread().contextClassLoader
                ?.getResourceAsStream("images/tonarc_icon.png")
                ?: return@runCatching null
            stream.use { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
        }.getOrNull()
    }
}

@Composable
fun DesktopSidebar(
    currentDestination: DesktopNavDestination,
    expanded: Boolean,
    localTrackCount: Int,
    onSelectDestination: (DesktopNavDestination) -> Unit,
    onToggleExpanded: () -> Unit,
    onAddMusicFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) {
        NavigationRail(
            modifier = modifier.fillMaxHeight().width(78.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            header = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TonarcBrandBadge(size = 38.dp)
                    Spacer(Modifier.height(6.dp))
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Expand sidebar")
                    }
                }
            }
        ) {
            Spacer(Modifier.height(8.dp))
            DesktopNavDestination.entries.forEach { dest ->
                NavigationRailItem(
                    selected = currentDestination == dest,
                    onClick = { onSelectDestination(dest) },
                    icon = { Icon(dest.icon(), contentDescription = dest.label) },
                    label = { Text(dest.label, maxLines = 1) }
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onAddMusicFolder,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Add Music Folder")
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(232.dp)
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TonarcBrandBadge(size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tonarc",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Desktop Audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Collapse sidebar")
                }
            }

            Spacer(Modifier.height(20.dp))

            DesktopNavDestination.entries.forEach { dest ->
                val selected = currentDestination == dest
                val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .clickable { onSelectDestination(dest) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = dest.icon(),
                        contentDescription = dest.label,
                        tint = fg,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = fg,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(12.dp))

            Text(
                text = "$localTrackCount local tracks indexed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onAddMusicFolder,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Folder", maxLines = 1)
            }
        }
    }
}

@Composable
fun MediumNavigationRail(
    currentDestination: DesktopNavDestination,
    onSelectDestination: (DesktopNavDestination) -> Unit,
    onAddMusicFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight().width(76.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            TonarcBrandBadge(size = 38.dp, modifier = Modifier.padding(top = 8.dp))
        }
    ) {
        Spacer(Modifier.height(8.dp))
        DesktopNavDestination.entries.forEach { dest ->
            NavigationRailItem(
                selected = currentDestination == dest,
                onClick = { onSelectDestination(dest) },
                icon = { Icon(dest.icon(), contentDescription = dest.label) },
                label = { Text(dest.label, maxLines = 1) }
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAddMusicFolder, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Add Folder")
        }
    }
}

@Composable
fun CompactBottomNavBar(
    currentDestination: DesktopNavDestination,
    onSelectDestination: (DesktopNavDestination) -> Unit
) {
    val primaryItems = remember {
        listOf(
            DesktopNavDestination.HOME,
            DesktopNavDestination.SEARCH,
            DesktopNavDestination.LIBRARY,
            DesktopNavDestination.PLAYLISTS,
            DesktopNavDestination.SETTINGS
        )
    }
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp
    ) {
        primaryItems.forEach { dest ->
            NavigationBarItem(
                selected = currentDestination == dest,
                onClick = { onSelectDestination(dest) },
                icon = { Icon(dest.icon(), contentDescription = dest.label) },
                label = { Text(dest.label, maxLines = 1) }
            )
        }
    }
}

@Composable
fun TonarcBrandBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val logo = rememberTonarcLogoBitmap()
    if (logo != null) {
        Image(
            bitmap = logo,
            contentDescription = "Tonarc",
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "T",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
