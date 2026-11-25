package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

enum class DrawerDestination {
    LIBRARY,
    FOLDERS,
    QUEUE,
    EQUALIZER,
    LISTENING_STATS,
    SCAN_MEDIA,
    SETTINGS,
    ABOUT
}

@Composable
fun EchoNavigationDrawerSheet(
    totalTracksCount: Int,
    folderCount: Int,
    activeDestination: DrawerDestination,
    onSelectDestination: (DrawerDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier
            .width(304.dp)
            .fillMaxHeight()
            .testTag("navigation_drawer_sheet"),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            // Zero Play Unified Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_zero_play_logo),
                    contentDescription = "Zero Play",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Zero Play",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Unified",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            NavigationDrawerItem(
                label = { Text("Library") },
                icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                badge = {
                    DrawerCountBadge(count = totalTracksCount)
                },
                selected = activeDestination == DrawerDestination.LIBRARY,
                onClick = { onSelectDestination(DrawerDestination.LIBRARY) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_library")
            )

            NavigationDrawerItem(
                label = { Text("Folders") },
                icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                badge = {
                    DrawerCountBadge(count = folderCount)
                },
                selected = activeDestination == DrawerDestination.FOLDERS,
                onClick = { onSelectDestination(DrawerDestination.FOLDERS) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_folders")
            )

            NavigationDrawerItem(
                label = { Text("Playing queue") },
                icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                selected = activeDestination == DrawerDestination.QUEUE,
                onClick = { onSelectDestination(DrawerDestination.QUEUE) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_queue")
            )

            NavigationDrawerItem(
                label = { Text("Equalizer") },
                icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                selected = activeDestination == DrawerDestination.EQUALIZER,
                onClick = { onSelectDestination(DrawerDestination.EQUALIZER) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_equalizer")
            )

            NavigationDrawerItem(
                label = { Text("Listening stats") },
                icon = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                selected = activeDestination == DrawerDestination.LISTENING_STATS,
                onClick = { onSelectDestination(DrawerDestination.LISTENING_STATS) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_stats")
            )

            NavigationDrawerItem(
                label = { Text("Scan media") },
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                selected = false,
                onClick = { onSelectDestination(DrawerDestination.SCAN_MEDIA) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_scan_media")
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = activeDestination == DrawerDestination.SETTINGS,
                onClick = { onSelectDestination(DrawerDestination.SETTINGS) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_settings")
            )

            NavigationDrawerItem(
                label = { Text("About") },
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                selected = activeDestination == DrawerDestination.ABOUT,
                onClick = { onSelectDestination(DrawerDestination.ABOUT) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_item_about")
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DrawerCountBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
