package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LibraryQuickCategory
import com.example.data.model.LibrarySortOption
import com.example.data.model.MediaSource
import com.example.data.model.MediaTrack
import com.example.data.model.PlaylistUi
import com.example.ui.EchoTopSheetType
import com.example.ui.components.EchoQuickAccessCard
import com.example.ui.components.EchoTopBar
import com.example.ui.components.GroupedTrackRow
import com.example.ui.components.cleanArtistLabel
import com.example.ui.components.groupedCardShape
import com.example.ui.components.safeDrawableResId

private val libraryFilterChips = listOf("Playlists", "Songs", "Albums", "Artists", "Local")

@Composable
fun LibraryScreen(
    tracks: List<MediaTrack>,
    playlists: List<PlaylistUi>,
    artistArtworks: Map<String, String> = emptyMap(),
    currentTrackId: String?,
    selectedChip: String,
    sortOption: LibrarySortOption,
    sortAscending: Boolean,
    selectedQuickCategory: LibraryQuickCategory?,
    selectedPlaylistId: Long?,
    onSelectChip: (String) -> Unit,
    onCycleSortOption: () -> Unit,
    onToggleSortDirection: () -> Unit,
    onOpenQuickCategory: (LibraryQuickCategory?) -> Unit,
    onOpenPlaylist: (Long?) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onRescanMedia: () -> Unit,
    onTopBarAction: (EchoTopSheetType) -> Unit,
    onLogoClick: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onPlayTrack: (MediaTrack, List<MediaTrack>) -> Unit,
    onPlayNext: (MediaTrack) -> Unit,
    onAddToPlaylist: (MediaTrack) -> Unit,
    onToggleLike: (MediaTrack) -> Unit,
    onConvertOrExtract: (MediaTrack) -> Unit,
    onShowSongInfo: (MediaTrack) -> Unit = {},
    onChangeArtistImage: (String, Uri) -> Unit = { _, _ -> },
    onRequestPrefetchArtists: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var selectedArtistName by rememberSaveable { mutableStateOf<String?>(null) }

    val artistDetailImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val artist = selectedArtistName
        if (uri != null && !artist.isNullOrBlank()) {
            onChangeArtistImage(artist, uri)
        }
    }

    if (selectedArtistName != null) {
        val artistName = selectedArtistName!!
        BackHandler { selectedArtistName = null }
        val artistTracks = sortTracks(
            tracks.filter {
                it.artist.trim().ifBlank { "Unknown Artist" } == artistName ||
                    cleanArtistLabel(it) == artistName
            },
            sortOption,
            sortAscending
        )
        LibraryDetailList(
            title = artistName,
            tracks = artistTracks,
            currentTrackId = currentTrackId,
            onBack = { selectedArtistName = null },
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onToggleLike = onToggleLike,
            onConvertOrExtract = onConvertOrExtract,
            onShowSongInfo = onShowSongInfo,
            trailingHeaderAction = {
                IconButton(
                    onClick = {
                        artistDetailImagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("artist_detail_change_image_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = "Change artist image",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = modifier
        )
        return
    }

    if (selectedQuickCategory != null) {
        BackHandler { onOpenQuickCategory(null) }
        val categoryTracks = when (selectedQuickCategory) {
            LibraryQuickCategory.LIKED -> tracks.filter { it.isLiked }
            LibraryQuickCategory.DOWNLOADED -> tracks.filter { it.source == MediaSource.DOWNLOADED }
            LibraryQuickCategory.EXPORTED -> tracks.filter { it.source == MediaSource.EXPORTED }
            LibraryQuickCategory.CACHED -> tracks.filter { it.source == MediaSource.CACHED }
            LibraryQuickCategory.TOP_50 -> tracks.sortedByDescending { it.playCount }.take(50)
            LibraryQuickCategory.BOTTOM_50 -> tracks.sortedBy { it.playCount }.take(50)
            LibraryQuickCategory.LOCAL -> tracks.filter { it.source == MediaSource.LOCAL }
        }.let { list -> sortTracks(list, sortOption, sortAscending) }

        LibraryDetailList(
            title = selectedQuickCategory.title,
            tracks = categoryTracks,
            currentTrackId = currentTrackId,
            onBack = { onOpenQuickCategory(null) },
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onToggleLike = onToggleLike,
            onConvertOrExtract = onConvertOrExtract,
            onShowSongInfo = onShowSongInfo,
            modifier = modifier
        )
        return
    }

    if (selectedPlaylistId != null) {
        val playlist = playlists.find { it.id == selectedPlaylistId }
        if (playlist != null) {
            BackHandler { onOpenPlaylist(null) }
            LibraryDetailList(
                title = playlist.name,
                tracks = sortTracks(playlist.tracks, sortOption, sortAscending),
                currentTrackId = currentTrackId,
                onBack = { onOpenPlaylist(null) },
                onPlayTrack = onPlayTrack,
                onPlayNext = onPlayNext,
                onAddToPlaylist = onAddToPlaylist,
                onToggleLike = onToggleLike,
                onConvertOrExtract = onConvertOrExtract,
                onShowSongInfo = onShowSongInfo,
                modifier = modifier
            )
            return
        }
    }

    val sortedTracks = sortTracks(tracks, sortOption, sortAscending)

    val libraryChipsRow: @Composable () -> Unit = {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(libraryFilterChips) { chip ->
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
    }

    if (selectedChip == "Artists") {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("library_screen")
        ) {
            ArtistsTabContent(
                tracks = tracks,
                artistArtworks = artistArtworks,
                onOpenDrawer = onOpenDrawer,
                onSelectArtist = { name, _ -> selectedArtistName = name },
                onChangeArtistImage = onChangeArtistImage,
                onRequestPrefetchArtists = onRequestPrefetchArtists,
                chipsSlot = libraryChipsRow,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 92.dp)
        ) {
            item {
                EchoTopBar(
                    title = stringResource(R.string.library_title),
                    onActionClick = onTopBarAction,
                    onLogoClick = onLogoClick,
                    onMenuClick = onOpenDrawer
                )
            }

            // Filter Chips Row
            item {
                libraryChipsRow()
            }

            // Sort Pill Button + Circular Ascending/Descending Button
            item {
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
                            .clickable(onClick = onCycleSortOption)
                            .testTag("library_sort_pill")
                    ) {
                        Text(
                            text = sortOption.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onToggleSortDirection,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .testTag("library_sort_dir_btn")
                    ) {
                        Icon(
                            imageVector = if (sortAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = "Sort direction",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // List / Grid View Toggle Pill (Screenshot 9)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (!isGridView) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                                    )
                                    .clickable { isGridView = false }
                                    .testTag("library_list_view_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                    contentDescription = "List view",
                                    tint = if (!isGridView) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isGridView) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                                    )
                                    .clickable { isGridView = true }
                                    .testTag("library_grid_view_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GridView,
                                    contentDescription = "Grid view",
                                    tint = if (isGridView) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onRescanMedia,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Rescan local media",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            when (selectedChip) {
                "Playlists" -> {
                    // 2-Column Quick-Access Grid (Zero Play layout)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EchoQuickAccessCard(
                                    title = "Liked",
                                    subtitle = "${tracks.count { it.isLiked }} tracks",
                                    icon = Icons.Rounded.Favorite,
                                    onClick = { onOpenQuickCategory(LibraryQuickCategory.LIKED) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_card_liked")
                                )
                                EchoQuickAccessCard(
                                    title = "Downloaded",
                                    subtitle = "${tracks.count { it.source == MediaSource.DOWNLOADED }} tracks",
                                    icon = Icons.Rounded.CheckCircle,
                                    onClick = { onOpenQuickCategory(LibraryQuickCategory.DOWNLOADED) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_card_downloaded")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EchoQuickAccessCard(
                                    title = "Exported",
                                    subtitle = "${tracks.count { it.source == MediaSource.EXPORTED }} files",
                                    icon = Icons.Rounded.Outbox,
                                    onClick = { onOpenQuickCategory(LibraryQuickCategory.EXPORTED) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_card_exported")
                                )
                                EchoQuickAccessCard(
                                    title = "Cached",
                                    subtitle = "${tracks.count { it.source == MediaSource.CACHED }} tracks",
                                    icon = Icons.Rounded.Sync,
                                    onClick = { onOpenQuickCategory(LibraryQuickCategory.CACHED) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_card_cached")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EchoQuickAccessCard(
                                    title = "My top 50",
                                    subtitle = "Most played",
                                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                                    onClick = { onOpenQuickCategory(LibraryQuickCategory.TOP_50) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_card_top50")
                                )
                                EchoQuickAccessCard(
                                    title = "My bottom 50",
                                    subtitle = "Rediscover",
                                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                                    onClick = { onOpenQuickCategory(LibraryQuickCategory.BOTTOM_50) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_card_bottom50")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EchoQuickAccessCard(
                                    title = "Local",
                                    subtitle = "${tracks.count { it.source == MediaSource.LOCAL }} device",
                                    icon = Icons.Rounded.Folder,
                                    onClick = { onOpenQuickCategory(LibraryQuickCategory.LOCAL) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_card_local")
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Playlists Section
                    item {
                        Text(
                            text = stringResource(R.string.playlists_section),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    item {
                        val rows = playlists.chunked(2)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { playlist ->
                                        PlaylistSquareCard(
                                            playlist = playlist,
                                            onClick = { onOpenPlaylist(playlist.id) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                "Songs" -> {
                    item {
                        GroupedTrackSectionList(
                            tracks = sortedTracks,
                            currentTrackId = currentTrackId,
                            onPlayTrack = onPlayTrack,
                            onPlayNext = onPlayNext,
                            onAddToPlaylist = onAddToPlaylist,
                            onToggleLike = onToggleLike,
                            onConvertOrExtract = onConvertOrExtract,
                            onShowSongInfo = onShowSongInfo
                        )
                    }
                }

                "Albums" -> {
                    val albums = sortedTracks.groupBy {
                        it.album.takeIf { a -> a.isNotBlank() && !a.equals("<unknown>", true) } ?: "Zero Play"
                    }.entries.toList()
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            albums.forEachIndexed { index, (albumName, albumTracks) ->
                                val rep = albumTracks.first()
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(groupedCardShape(index, albums.size))
                                        .clickable { onPlayTrack(rep, albumTracks) },
                                    shape = groupedCardShape(index, albums.size),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val safeAlbumArt = safeDrawableResId(rep.artworkResId, R.drawable.img_cover_velvet_1794995218473)
                                        if (!rep.artworkUriString.isNullOrBlank()) {
                                            AsyncImage(
                                                model = rep.artworkUriString,
                                                contentDescription = albumName,
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = safeAlbumArt),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = safeAlbumArt),
                                                contentDescription = albumName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = albumName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${cleanArtistLabel(rep)} • ${albumTracks.size} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Artists" -> {
                    val artists = sortedTracks
                        .groupBy { cleanArtistLabel(it) }
                        .entries
                        .sortedBy { it.key.lowercase() }
                        .toList()
                    item {
                        if (isGridView) {
                            // 2-Column Circular Artist Portrait Grid (Screenshot 9)
                            val rows = artists.chunked(2)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                rows.forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        pair.forEach { (artistName, artistTracks) ->
                                            val rep = artistTracks.first()
                                            CircularArtistGridCard(
                                                artistName = artistName,
                                                representative = rep,
                                                onClick = { selectedArtistName = artistName },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                artists.forEachIndexed { index, (artistName, artistTracks) ->
                                    val rep = artistTracks.first()
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(groupedCardShape(index, artists.size))
                                            .clickable { selectedArtistName = artistName },
                                        shape = groupedCardShape(index, artists.size),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val safeArtistArt = safeDrawableResId(rep.artworkResId, R.drawable.img_cover_midnight_1794995218900)
                                            if (!rep.artworkUriString.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = rep.artworkUriString,
                                                    contentDescription = artistName,
                                                    contentScale = ContentScale.Crop,
                                                    error = painterResource(id = safeArtistArt),
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Image(
                                                    painter = painterResource(id = safeArtistArt),
                                                    contentDescription = artistName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = artistName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${artistTracks.size} tracks",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Local" -> {
                    val localTracks = sortedTracks.filter { it.source == MediaSource.LOCAL }
                    item {
                        if (localTracks.isEmpty()) {
                            EmptyLibraryNotice(
                                message = "No device audio or video files discovered yet. Tap refresh to scan MediaStore.",
                                onAction = onRescanMedia
                            )
                        } else {
                            GroupedTrackSectionList(
                                tracks = localTracks,
                                currentTrackId = currentTrackId,
                                onPlayTrack = onPlayTrack,
                                onPlayNext = onPlayNext,
                                onAddToPlaylist = onAddToPlaylist,
                                onToggleLike = onToggleLike,
                                onConvertOrExtract = onConvertOrExtract,
                                onShowSongInfo = onShowSongInfo
                            )
                        }
                    }
                }
            }
        }

        // Dark Circular + FAB in bottom-right corner
        FloatingActionButton(
            onClick = onCreatePlaylistClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("library_add_playlist_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.action_create_playlist)
            )
        }
    }
}

@Composable
private fun CircularArtistGridCard(
    artistName: String,
    representative: MediaTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("artist_grid_${artistName.lowercase().replace(" ", "_")}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val safeGridArt = safeDrawableResId(representative.artworkResId, R.drawable.img_cover_midnight_1794995218900)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (!representative.artworkUriString.isNullOrBlank()) {
                AsyncImage(
                    model = representative.artworkUriString,
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = safeGridArt),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = safeGridArt),
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artistName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlaylistSquareCard(
    playlist: PlaylistUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("playlist_card_${playlist.id}")
    ) {
        val safeCoverRes = safeDrawableResId(playlist.coverResId, R.drawable.img_cover_aurora_1794995218098)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = safeCoverRes),
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${playlist.tracks.size} songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun LibraryDetailList(
    title: String,
    tracks: List<MediaTrack>,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPlayTrack: (MediaTrack, List<MediaTrack>) -> Unit,
    onPlayNext: (MediaTrack) -> Unit,
    onAddToPlaylist: (MediaTrack) -> Unit,
    onToggleLike: (MediaTrack) -> Unit,
    onConvertOrExtract: (MediaTrack) -> Unit,
    onShowSongInfo: (MediaTrack) -> Unit = {},
    trailingHeaderAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                trailingHeaderAction?.invoke()
            }
        }
        item {
            if (tracks.isEmpty()) {
                EmptyLibraryNotice(
                    message = "No tracks in $title yet.",
                    onAction = null
                )
            } else {
                GroupedTrackSectionList(
                    tracks = tracks,
                    currentTrackId = currentTrackId,
                    onPlayTrack = onPlayTrack,
                    onPlayNext = onPlayNext,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleLike = onToggleLike,
                    onConvertOrExtract = onConvertOrExtract,
                    onShowSongInfo = onShowSongInfo
                )
            }
        }
    }
}

@Composable
private fun GroupedTrackSectionList(
    tracks: List<MediaTrack>,
    currentTrackId: String?,
    onPlayTrack: (MediaTrack, List<MediaTrack>) -> Unit,
    onPlayNext: (MediaTrack) -> Unit,
    onAddToPlaylist: (MediaTrack) -> Unit,
    onToggleLike: (MediaTrack) -> Unit,
    onConvertOrExtract: (MediaTrack) -> Unit,
    onShowSongInfo: (MediaTrack) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tracks.forEachIndexed { index, track ->
            GroupedTrackRow(
                track = track,
                index = index,
                totalCount = tracks.size,
                isActive = track.id == currentTrackId,
                onClick = { onPlayTrack(track, tracks) },
                onPlayNext = { onPlayNext(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onToggleLike = { onToggleLike(track) },
                onConvertOrExtract = { onConvertOrExtract(track) },
                onSongInfo = { onShowSongInfo(track) }
            )
        }
    }
}

@Composable
private fun EmptyLibraryNotice(
    message: String,
    onAction: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onAction)
                ) {
                    Text(
                        text = "Scan MediaStore",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun sortTracks(
    tracks: List<MediaTrack>,
    option: LibrarySortOption,
    ascending: Boolean
): List<MediaTrack> {
    val sorted = when (option) {
        LibrarySortOption.DATE_ADDED -> tracks.sortedByDescending { it.dateAdded }
        LibrarySortOption.TITLE -> tracks.sortedBy { it.title.lowercase() }
        LibrarySortOption.PLAY_COUNT -> tracks.sortedByDescending { it.playCount }
    }
    return if (ascending) sorted.reversed() else sorted
}
