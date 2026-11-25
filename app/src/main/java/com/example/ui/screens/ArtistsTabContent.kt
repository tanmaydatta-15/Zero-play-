package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaTrack
import com.example.data.repository.ArtistArtworkManager
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

data class ArtistGroupItem(
    val name: String,
    val tracks: List<MediaTrack>,
    val cachedPhotoPath: String?,
    val fallbackTrackArtUri: String?
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsTabContent(
    tracks: List<MediaTrack>,
    artistArtworks: Map<String, String>,
    onOpenDrawer: () -> Unit,
    onSelectArtist: (String, List<MediaTrack>) -> Unit,
    onChangeArtistImage: (artistName: String, imageUri: Uri) -> Unit,
    onRequestPrefetchArtists: (List<String>) -> Unit,
    chipsSlot: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(true) }
    var isGridMode by remember { mutableStateOf(true) }
    var pendingImageArtist by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val artist = pendingImageArtist
        pendingImageArtist = null
        if (uri != null && !artist.isNullOrBlank()) {
            onChangeArtistImage(artist, uri)
        }
    }

    val allArtists = remember(tracks, artistArtworks, sortAscending, query) {
        val grouped = tracks.groupBy { it.artist.trim().ifBlank { "Unknown Artist" } }
        val items = grouped.map { (name, list) ->
            val key = ArtistArtworkManager.normalizeArtistKey(name)
            val cached = artistArtworks[key]?.takeIf { File(it).exists() }
            val fallbackUri = list.firstNotNullOfOrNull { t ->
                t.artworkUriString?.takeIf { it.isNotBlank() }
            }
            ArtistGroupItem(
                name = name,
                tracks = list,
                cachedPhotoPath = cached,
                fallbackTrackArtUri = fallbackUri
            )
        }
        val filtered = if (query.isBlank()) {
            items
        } else {
            val q = query.trim().lowercase()
            items.filter { it.name.lowercase().contains(q) }
        }
        if (sortAscending) filtered.sortedBy { it.name.lowercase() }
        else filtered.sortedByDescending { it.name.lowercase() }
    }

    // Trigger background offline-first prefetch whenever artist list changes
    LaunchedEffect(allArtists.map { it.name }) {
        if (allArtists.isNotEmpty()) {
            onRequestPrefetchArtists(allArtists.map { it.name })
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var isFastScrolling by remember { mutableStateOf(false) }
    var fastScrollFraction by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("artists_tab_content")
    ) {
        // Top pill search bar ("Search your music") with hamburger drawer icon on left and Cast icon on right
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(52.dp)
                .testTag("artists_search_pill")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("artists_drawer_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open navigation drawer",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search your music",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
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

                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.testTag("artists_cast_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cast,
                            contentDescription = "Cast media",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        chipsSlot?.invoke()

        // Controls Row: NAME ↑/↓ sort chip + List/Grid toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterChip(
                selected = true,
                onClick = { sortAscending = !sortAscending },
                label = {
                    Text(
                        text = if (sortAscending) "NAME ↑" else "NAME ↓",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (sortAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("artists_sort_chip")
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${allArtists.size} artists",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(
                    onClick = { isGridMode = !isGridMode },
                    modifier = Modifier.testTag("artists_layout_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isGridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = if (isGridMode) "Switch to list view" else "Switch to grid view",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (allArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "No artists in your library yet." else "No artists matching \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val trackHeightPx = with(LocalDensity.current) { maxHeight.toPx().coerceAtLeast(1f) }
                val thumbHeightDp = 44.dp
                val thumbHeightPx = with(LocalDensity.current) { thumbHeightDp.toPx() }

                val currentScrollFraction = if (isFastScrolling) {
                    fastScrollFraction
                } else {
                    val total = allArtists.size.coerceAtLeast(1)
                    val firstVis = if (isGridMode) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                    (firstVis.toFloat() / (total - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                }

                if (isGridMode) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 28.dp, top = 8.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artists_grid")
                    ) {
                        items(allArtists, key = { it.name }) { item ->
                            ArtistGridCard(
                                item = item,
                                onClick = { onSelectArtist(item.name, item.tracks) },
                                onChangeImage = {
                                    pendingImageArtist = item.name
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 28.dp, top = 6.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artists_list")
                    ) {
                        items(allArtists, key = { it.name }) { item ->
                            ArtistListRow(
                                item = item,
                                onClick = { onSelectArtist(item.name, item.tracks) },
                                onChangeImage = {
                                    pendingImageArtist = item.name
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                }

                // Draggable Fast-Scroll Thumb on right edge
                if (allArtists.size >= 4) {
                    val usableHeight = (trackHeightPx - thumbHeightPx - 120f).coerceAtLeast(1f)
                    val thumbOffsetY = (currentScrollFraction * usableHeight).roundToInt()
                    val activeIndex = ((allArtists.lastIndex) * currentScrollFraction).roundToInt()
                        .coerceIn(0, allArtists.lastIndex)
                    val activeLetter = allArtists[activeIndex].name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"

                    // Floating letter bubble while dragging
                    if (isFastScrolling) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset { IntOffset(-132, (thumbOffsetY - 8).coerceAtLeast(0)) }
                                .size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = activeLetter,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .width(26.dp)
                            .pointerInput(allArtists.size, isGridMode) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        isFastScrolling = true
                                        val frac = (offset.y / usableHeight).coerceIn(0f, 1f)
                                        fastScrollFraction = frac
                                        val targetIdx = (frac * allArtists.lastIndex).roundToInt()
                                            .coerceIn(0, allArtists.lastIndex)
                                        scope.launch {
                                            if (isGridMode) gridState.scrollToItem(targetIdx)
                                            else listState.scrollToItem(targetIdx)
                                        }
                                    },
                                    onDragEnd = { isFastScrolling = false },
                                    onDragCancel = { isFastScrolling = false },
                                    onVerticalDrag = { change, _ ->
                                        change.consume()
                                        val frac = (change.position.y / usableHeight).coerceIn(0f, 1f)
                                        fastScrollFraction = frac
                                        val targetIdx = (frac * allArtists.lastIndex).roundToInt()
                                            .coerceIn(0, allArtists.lastIndex)
                                        scope.launch {
                                            if (isGridMode) gridState.scrollToItem(targetIdx)
                                            else listState.scrollToItem(targetIdx)
                                        }
                                    }
                                )
                            }
                            .testTag("artists_fast_scroll_track")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                            color = if (isFastScrolling) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset { IntOffset(0, thumbOffsetY) }
                                .width(if (isFastScrolling) 10.dp else 6.dp)
                                .height(thumbHeightDp)
                        ) {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistGridCard(
    item: ArtistGroupItem,
    onClick: () -> Unit,
    onChangeImage: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            ArtistCircularAvatar(
                cachedPhotoPath = item.cachedPhotoPath,
                fallbackTrackArtUri = item.fallbackTrackArtUri,
                artistName = item.name,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle corner menu button for "Change artist image"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Surface(
                    onClick = { menuExpanded = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Artist options for ${item.name}",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Change artist image") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onChangeImage()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${item.tracks.size} ${if (item.tracks.size == 1) "track" else "tracks"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ArtistListRow(
    item: ArtistGroupItem,
    onClick: () -> Unit,
    onChangeImage: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistCircularAvatar(
                cachedPhotoPath = item.cachedPhotoPath,
                fallbackTrackArtUri = item.fallbackTrackArtUri,
                artistName = item.name,
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.tracks.size} ${if (item.tracks.size == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Artist options"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Change artist image") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onChangeImage()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistCircularAvatar(
    cachedPhotoPath: String?,
    fallbackTrackArtUri: String?,
    artistName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val model: Any? = remember(cachedPhotoPath, fallbackTrackArtUri) {
        when {
            !cachedPhotoPath.isNullOrBlank() && File(cachedPhotoPath).exists() -> File(cachedPhotoPath)
            !fallbackTrackArtUri.isNullOrBlank() -> Uri.parse(fallbackTrackArtUri)
            else -> null
        }
    }

    var failedToLoad by remember(model) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (model != null && !failedToLoad) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = artistName,
                contentScale = ContentScale.Crop,
                onError = { failedToLoad = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = artistName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxSize(0.46f)
            )
        }
    }
}
