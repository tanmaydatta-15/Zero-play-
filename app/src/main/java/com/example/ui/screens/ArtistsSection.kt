package com.example.ui.screens

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MediaTrack
import com.example.data.repository.ArtistArtworkManager
import com.example.ui.components.cleanArtistLabel
import com.example.ui.components.groupedCardShape
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private data class ArtistDisplayEntry(
    val name: String,
    val tracks: List<MediaTrack>,
    val cachedPortraitPath: String?,
    val fallbackTrackArtUri: String?
)

@Composable
fun ArtistsTabContent(
    tracks: List<MediaTrack>,
    filterChips: List<String>,
    selectedChip: String,
    artistSearchQuery: String,
    artistSortAscending: Boolean,
    artistArtworks: Map<String, String>,
    autoDownloadArtistImages: Boolean,
    onSelectChip: (String) -> Unit,
    onArtistSearchQueryChange: (String) -> Unit,
    onToggleArtistSort: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSelectArtist: (String) -> Unit,
    onEnsureArtistArtworks: (Collection<String>) -> Unit,
    onChangeArtistImage: (String, Uri) -> Unit,
    onResetArtistImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGridLayout by rememberSaveable { mutableStateOf(true) }
    var showCastDialog by remember { mutableStateOf(false) }
    var artistForImageDialog by remember { mutableStateOf<String?>(null) }
    var pendingImageArtistName by remember { mutableStateOf<String?>(null) }

    // Zero-permission Android Photo Picker for custom artist portraits
    val artistImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetArtist = pendingImageArtistName
        pendingImageArtistName = null
        if (uri != null && !targetArtist.isNullOrBlank()) {
            onChangeArtistImage(targetArtist, uri)
        }
    }

    val allArtistNames = remember(tracks) {
        tracks.map { cleanArtistLabel(it) }.distinct()
    }

    LaunchedEffect(allArtistNames, autoDownloadArtistImages) {
        if (allArtistNames.isNotEmpty()) {
            onEnsureArtistArtworks(allArtistNames)
        }
    }

    val artists = remember(tracks, artistSearchQuery, artistSortAscending, artistArtworks) {
        val query = artistSearchQuery.trim().lowercase()
        val grouped = tracks.groupBy { cleanArtistLabel(it) }
            .filter { (name, _) -> query.isEmpty() || name.lowercase().contains(query) }
            .map { (name, artistTracks) ->
                val key = ArtistArtworkManager.normalizeArtistKey(name)
                val cachedPath = artistArtworks[key]
                val fallbackArt = artistTracks.firstOrNull { !it.artworkUriString.isNullOrBlank() }?.artworkUriString
                ArtistDisplayEntry(
                    name = name,
                    tracks = artistTracks,
                    cachedPortraitPath = cachedPath,
                    fallbackTrackArtUri = fallbackArt
                )
            }
        if (artistSortAscending) {
            grouped.sortedBy { it.name.lowercase() }
        } else {
            grouped.sortedByDescending { it.name.lowercase() }
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("artists_tab_screen")
    ) {
        // Top Pill Search Bar ("Search your music" with hamburger drawer on left + Cast icon on right)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("artists_drawer_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open navigation menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (artistSearchQuery.isEmpty()) {
                        Text(
                            text = "Search your music",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = artistSearchQuery,
                        onValueChange = onArtistSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("artists_search_input")
                    )
                }

                if (artistSearchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onArtistSearchQueryChange("") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { showCastDialog = true },
                    modifier = Modifier.testTag("artists_cast_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cast,
                        contentDescription = "Audio output and Cast",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Library Filter Chips Row (keeps navigation between Library tabs seamless)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterChips) { chip ->
                val selected = selectedChip == chip
                FilterChip(
                    selected = selected,
                    onClick = { onSelectChip(chip) },
                    label = {
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("library_chip_${chip.lowercase()}")
                )
            }
        }

        // NAME ↑/↓ Sort Chip + List/Grid Layout Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleArtistSort)
                    .testTag("artists_sort_name_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NAME",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (artistSortAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = if (artistSortAscending) "Sorted A to Z" else "Sorted Z to A",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "${artists.size} artists",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { isGridLayout = !isGridLayout },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .testTag("artists_layout_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isGridLayout) Icons.AutoMirrored.Rounded.FormatListBulleted else Icons.Rounded.GridView,
                    contentDescription = if (isGridLayout) "Switch to list layout" else "Switch to grid layout",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Main Artists Grid / List with Draggable Fast-Scroll Thumb
        if (artists.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (artistSearchQuery.isBlank()) {
                            "No artists discovered yet"
                        } else {
                            "No artists match \"$artistSearchQuery\""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val currentScrollFraction by remember(artists.size, isGridLayout) {
                derivedStateOf {
                    if (artists.size <= 1) {
                        0f
                    } else if (isGridLayout) {
                        val first = gridState.firstVisibleItemIndex
                        (first.toFloat() / (artists.size - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    } else {
                        val first = listState.firstVisibleItemIndex
                        (first.toFloat() / (artists.size - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isGridLayout) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 28.dp, top = 4.dp, bottom = 92.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artists_grid")
                    ) {
                        items(artists, key = { it.name }) { entry ->
                            ArtistGridAvatarCard(
                                entry = entry,
                                onClick = { onSelectArtist(entry.name) },
                                onLongClick = { artistForImageDialog = entry.name }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 28.dp, top = 4.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artists_list")
                    ) {
                        itemsIndexed(artists, key = { _, item -> item.name }) { index, entry ->
                            ArtistListRowCard(
                                entry = entry,
                                index = index,
                                totalCount = artists.size,
                                onClick = { onSelectArtist(entry.name) },
                                onLongClick = { artistForImageDialog = entry.name }
                            )
                        }
                    }
                }

                // Draggable Fast-Scroll Thumb on Right Edge
                FastScrollOverlay(
                    itemCount = artists.size,
                    scrollFraction = currentScrollFraction,
                    letterForFraction = { frac ->
                        val idx = (frac * (artists.size - 1)).roundToInt().coerceIn(0, artists.lastIndex)
                        artists[idx].name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                    },
                    onScrubFraction = { frac ->
                        val targetIdx = (frac * (artists.size - 1)).roundToInt().coerceIn(0, artists.lastIndex)
                        scope.launch {
                            if (isGridLayout) {
                                gridState.scrollToItem(targetIdx)
                            } else {
                                listState.scrollToItem(targetIdx)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 8.dp, bottom = 84.dp, end = 4.dp)
                )
            }
        }
    }

    // Long-press Artist Options Dialog ("Change artist image" / "Reset")
    artistForImageDialog?.let { selectedArtist ->
        val hasCached = artistArtworks.containsKey(ArtistArtworkManager.normalizeArtistKey(selectedArtist))
        AlertDialog(
            onDismissRequest = { artistForImageDialog = null },
            title = { Text(selectedArtist, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                pendingImageArtistName = selectedArtist
                                artistForImageDialog = null
                                artistImagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("dialog_change_artist_image_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Change artist image", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (hasCached) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onResetArtistImage(selectedArtist)
                                    artistForImageDialog = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Reset cached artist image", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { artistForImageDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Cast / Audio Output Info Dialog
    if (showCastDialog) {
        val outputDevices = remember(showCastDialog) { queryAudioOutputs(context) }
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            icon = { Icon(Icons.Rounded.Cast, contentDescription = null) },
            title = { Text("Audio Output & Cast", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Active audio playback routes on this device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    outputDevices.forEach { deviceLabel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Speaker,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = deviceLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCastDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistGridAvatarCard(
    entry: ArtistDisplayEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp)
            .testTag("artist_grid_${entry.name.lowercase().replace(" ", "_")}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtistCircularPortrait(
            artistName = entry.name,
            cachedPortraitPath = entry.cachedPortraitPath,
            fallbackTrackArtUri = entry.fallbackTrackArtUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${entry.tracks.size} ${if (entry.tracks.size == 1) "song" else "songs"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistListRowCard(
    entry: ArtistDisplayEntry,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupedCardShape(index, totalCount))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = groupedCardShape(index, totalCount),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistCircularPortrait(
                artistName = entry.name,
                cachedPortraitPath = entry.cachedPortraitPath,
                fallbackTrackArtUri = entry.fallbackTrackArtUri,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.tracks.size} ${if (entry.tracks.size == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArtistCircularPortrait(
    artistName: String,
    cachedPortraitPath: String?,
    fallbackTrackArtUri: String?,
    modifier: Modifier = Modifier
) {
    val cachedFile = remember(cachedPortraitPath) {
        cachedPortraitPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
    }
    var imageFailed by remember(cachedFile, fallbackTrackArtUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        val model: Any? = when {
            cachedFile != null -> cachedFile
            !fallbackTrackArtUri.isNullOrBlank() && !imageFailed -> fallbackTrackArtUri
            else -> null
        }

        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = artistName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.46f)
            )
        }
    }
}

@Composable
private fun FastScrollOverlay(
    itemCount: Int,
    scrollFraction: Float,
    letterForFraction: (Float) -> String,
    onScrubFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (itemCount < 4) return

    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDragging) dragFraction else scrollFraction
    val thumbHeightDp = 46.dp

    Box(
        modifier = modifier
            .width(26.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(itemCount) {
                detectTapGestures { offset ->
                    val frac = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                    dragFraction = frac
                    onScrubFraction(frac)
                }
            }
            .pointerInput(itemCount) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val frac = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        dragFraction = frac
                        onScrubFraction(frac)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                        dragFraction = frac
                        onScrubFraction(frac)
                    }
                )
            }
            .testTag("artists_fast_scroll_thumb")
    ) {
        val thumbOffsetY = ((trackHeightPx - 120f).coerceAtLeast(1f) * activeFraction).roundToInt()

        // Track rail
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )

        // Draggable pill thumb
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = 0, y = thumbOffsetY) }
                .padding(end = 2.dp)
                .width(if (isDragging) 12.dp else 8.dp)
                .height(thumbHeightDp)
                .clip(CircleShape)
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
        )

        // Letter indicator bubble when actively scrubbing
        if (isDragging) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = -120, y = thumbOffsetY) }
                    .size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = letterForFraction(dragFraction),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private fun queryAudioOutputs(context: Context): List<String> {
    return runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return listOf("Device Speaker")
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { info ->
                when (info.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> info.productName?.toString()?.takeIf { it.isNotBlank() }
                        ?.let { "Bluetooth • $it" } ?: "Bluetooth Audio"
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C Audio DAC"
                    AudioDeviceInfo.TYPE_HDMI -> "HDMI / External Display"
                    else -> null
                }
            }
            .distinct()
        outputs.ifEmpty { listOf("Device Speaker") }
    }.getOrDefault(listOf("Device Speaker"))
}
