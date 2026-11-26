package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ThemeModePref
import com.example.data.model.MediaType
import com.example.ui.components.DrawerDestination
import com.example.ui.components.EchoMiniPlayerBar
import com.example.ui.components.EchoNavigationDrawerSheet
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AutoUpdateScreen
import com.example.ui.screens.ConvertScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ListeningStatsScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TagEditorScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.sheets.AddToPlaylistDialog
import com.example.ui.sheets.AudioExtractorSheet
import com.example.ui.sheets.CodecDiagnosticSheet
import com.example.ui.sheets.CreatePlaylistDialog
import com.example.ui.sheets.EchoTopActionSheet
import com.example.ui.sheets.MediaDownloaderSheet
import com.example.ui.sheets.PhotoConverterSheet
import com.example.ui.sheets.PlaylistEditorSheet
import com.example.ui.sheets.SongInfoBottomSheet
import com.example.ui.sheets.VideoCompressorSheet
import kotlinx.coroutines.launch

enum class EchoTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_tab_home"),
    SEARCH(R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search, "nav_tab_search"),
    CONVERT(R.string.nav_convert, Icons.Rounded.Transform, Icons.Outlined.Transform, "nav_tab_convert"),
    LIBRARY(R.string.nav_library, Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic, "nav_tab_library")
}

@Composable
fun EchoMusicApp(
    viewModel: EchoViewModel,
    onEnterPipMode: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val artistArtworks by viewModel.artistArtworks.collectAsStateWithLifecycle()
    val chartTracks by viewModel.filteredCharts.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val conversionJobs by viewModel.conversionJobs.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val systemInDark = isSystemInDarkTheme()
    val isDarkThemeActive = when (settings.themeMode) {
        ThemeModePref.SYSTEM -> systemInDark
        ThemeModePref.DARK -> true
        ThemeModePref.LIGHT -> false
    }

    var currentTab by rememberSaveable { mutableStateOf(EchoTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val folderCount = remember(tracks) {
        tracks.map { it.folderPath.trimEnd('/').ifBlank { "Internal Storage" } }.distinct().size
    }
    val activeDrawerDestination = when {
        uiState.showFoldersScreen -> DrawerDestination.FOLDERS
        uiState.showEqualizerScreen -> DrawerDestination.EQUALIZER
        uiState.showListeningStatsScreen -> DrawerDestination.LISTENING_STATS
        uiState.showSettingsScreen -> DrawerDestination.SETTINGS
        uiState.showAboutScreen -> DrawerDestination.ABOUT
        else -> DrawerDestination.LIBRARY
    }

    val shouldKeepScreenOn = settings.keepScreenOn && playbackState.isPlaying
    DisposableEffect(view, shouldKeepScreenOn) {
        view.keepScreenOn = shouldKeepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.rescanMediaStore()
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = mediaPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            permissionLauncher.launch(mediaPermissions)
        }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !uiState.isPlayerExpanded &&
            !uiState.showSettingsScreen &&
            !uiState.showAboutScreen &&
            !uiState.showAutoUpdateScreen &&
            !uiState.showEqualizerScreen &&
            !uiState.showFoldersScreen &&
            !uiState.showListeningStatsScreen &&
            uiState.tagEditorTrack == null,
        drawerContent = {
            EchoNavigationDrawerSheet(
                totalTracksCount = tracks.size,
                folderCount = folderCount,
                activeDestination = activeDrawerDestination,
                onSelectDestination = { dest ->
                    scope.launch { drawerState.close() }
                    when (dest) {
                        DrawerDestination.LIBRARY -> {
                            viewModel.openFolders(false)
                            viewModel.openEqualizer(false)
                            viewModel.openListeningStats(false)
                            viewModel.openSettings(false)
                            viewModel.openAbout(false)
                            currentTab = EchoTab.LIBRARY
                        }
                        DrawerDestination.FOLDERS -> viewModel.openFolders(true)
                        DrawerDestination.QUEUE -> {
                            if (playbackState.currentTrack != null) {
                                viewModel.setPlayerExpanded(true)
                            } else {
                                viewModel.showStatus("Start playing a track to view the queue")
                            }
                        }
                        DrawerDestination.EQUALIZER -> viewModel.openEqualizer(true)
                        DrawerDestination.LISTENING_STATS -> viewModel.openListeningStats(true)
                        DrawerDestination.SCAN_MEDIA -> viewModel.rescanMediaStore()
                        DrawerDestination.SETTINGS -> viewModel.openSettings(true)
                        DrawerDestination.ABOUT -> viewModel.openAbout(true)
                    }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 680.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        EchoTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.statusBars,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp)
                        ) {
                            AnimatedVisibility(
                                visible = playbackState.currentTrack != null,
                                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                            ) {
                                EchoMiniPlayerBar(
                                    playbackState = playbackState,
                                    showProgressBar = settings.showMiniProgressBar,
                                    onExpand = { viewModel.setPlayerExpanded(true) },
                                    onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                                    onSkipNext = { viewModel.playbackController.skipNext() },
                                    onDismiss = viewModel::dismissPlayback
                                )
                            }
                            if (!useNavigationRail) {
                                EchoFloatingPillNavBar(
                                    currentTab = currentTab,
                                    onSelectTab = { currentTab = it }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val pendingRelease = uiState.availableRelease
                        AnimatedVisibility(
                            visible = pendingRelease != null && !uiState.dismissedOtaBanner
                        ) {
                            pendingRelease?.let { rel ->
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 760.dp)
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .clickable { viewModel.openAutoUpdate(true) }
                                        .testTag("ota_update_banner")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SystemUpdateAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Update ${rel.resolvedVersionLabel} available",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Tap to download & install from GitHub",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { viewModel.openAutoUpdate(true) },
                                            shape = CircleShape,
                                            modifier = Modifier.testTag("ota_banner_update_btn")
                                        ) {
                                            Text("Update", style = MaterialTheme.typography.labelMedium)
                                        }
                                        IconButton(
                                            onClick = viewModel::dismissOtaBanner,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Dismiss update banner",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val contentModifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)

                            when (currentTab) {
                            EchoTab.HOME -> HomeScreen(
                                tracks = tracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                onTopBarAction = viewModel::openTopSheet,
                                onLogoClick = { viewModel.openAbout(true) },
                                onOpenDrawer = openDrawer,
                                onOpenDownloader = { viewModel.openDownloadSheet() },
                                onRescanMedia = viewModel::rescanMediaStore,
                                onOpenCodecDiagnostics = { viewModel.openDeviceInfo(true) },
                                onPlayTrack = viewModel::playTrack,
                                onPlayNext = viewModel::playNextInQueue,
                                onAddToPlaylist = viewModel::openPlaylistPickerForTrack,
                                onToggleLike = viewModel::toggleLike,
                                onConvertOrExtract = { track ->
                                    if (track.mediaType == MediaType.VIDEO) {
                                        viewModel.openAudioExtractor(Uri.parse(track.uriString), track.title)
                                    } else {
                                        currentTab = EchoTab.CONVERT
                                        viewModel.openAudioExtractor()
                                    }
                                },
                                onShowSongInfo = viewModel::openSongInfo,
                                modifier = contentModifier
                            )

                            EchoTab.SEARCH -> SearchScreen(
                                searchQuery = uiState.searchQuery,
                                matchingTracks = chartTracks,
                                currentTrackId = playbackState.currentTrack?.id,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onOpenDownloaderSheet = viewModel::openDownloadSheet,
                                onOpenCodecDiagnostics = { viewModel.openDeviceInfo(true) },
                                onLogoClick = { viewModel.openAbout(true) },
                                onOpenDrawer = openDrawer,
                                onPlayTrack = viewModel::playTrack,
                                onPlayNext = viewModel::playNextInQueue,
                                onAddToPlaylist = viewModel::openPlaylistPickerForTrack,
                                onToggleLike = viewModel::toggleLike,
                                onConvertOrExtract = { track ->
                                    if (track.mediaType == MediaType.VIDEO) {
                                        viewModel.openAudioExtractor(Uri.parse(track.uriString), track.title)
                                    } else {
                                        currentTab = EchoTab.CONVERT
                                        viewModel.openAudioExtractor()
                                    }
                                },
                                onShowSongInfo = viewModel::openSongInfo,
                                modifier = contentModifier
                            )

                            EchoTab.CONVERT -> ConvertScreen(
                                conversionJobs = conversionJobs,
                                downloads = downloads,
                                onOpenPhotoConverter = viewModel::openPhotoConverter,
                                onOpenVideoCompressor = { viewModel.openVideoConverter() },
                                onOpenAudioExtractor = { viewModel.openAudioExtractor() },
                                onOpenDownloader = { viewModel.openDownloadSheet() },
                                onOpenCodecDiagnostics = { viewModel.openDeviceInfo(true) },
                                onRetryDownload = viewModel::retryDownload,
                                onDismissDownload = viewModel::dismissDownload,
                                onRetryFailedConversions = viewModel::retryFailedConversions,
                                onClearFinishedConversions = viewModel::clearFinishedConversions,
                                onTopBarAction = viewModel::openTopSheet,
                                onLogoClick = { viewModel.openAbout(true) },
                                onOpenDrawer = openDrawer,
                                modifier = contentModifier
                            )

                            EchoTab.LIBRARY -> LibraryScreen(
                                tracks = tracks,
                                playlists = playlists,
                                artistArtworks = artistArtworks,
                                currentTrackId = playbackState.currentTrack?.id,
                                selectedChip = uiState.selectedLibraryChip,
                                sortOption = uiState.librarySortOption,
                                sortAscending = uiState.librarySortAscending,
                                selectedQuickCategory = uiState.selectedQuickCategory,
                                selectedPlaylistId = uiState.selectedPlaylistId,
                                onSelectChip = viewModel::selectLibraryChip,
                                onCycleSortOption = viewModel::cycleSortOption,
                                onToggleSortDirection = viewModel::toggleSortDirection,
                                onOpenQuickCategory = viewModel::openQuickCategory,
                                onOpenPlaylist = viewModel::openPlaylist,
                                onCreatePlaylistClick = viewModel::openCreatePlaylistEditor,
                                onEditPlaylist = viewModel::openEditPlaylistEditor,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                onRescanMedia = viewModel::rescanMediaStore,
                                onTopBarAction = viewModel::openTopSheet,
                                onLogoClick = { viewModel.openAbout(true) },
                                onOpenDrawer = openDrawer,
                                onPlayTrack = viewModel::playTrack,
                                onPlayNext = viewModel::playNextInQueue,
                                onAddToPlaylist = viewModel::openPlaylistPickerForTrack,
                                onToggleLike = viewModel::toggleLike,
                                onConvertOrExtract = { track ->
                                    if (track.mediaType == MediaType.VIDEO) {
                                        viewModel.openAudioExtractor(Uri.parse(track.uriString), track.title)
                                    } else {
                                        currentTab = EchoTab.CONVERT
                                        viewModel.openAudioExtractor()
                                    }
                                },
                                onShowSongInfo = viewModel::openSongInfo,
                                onChangeArtistImage = viewModel::saveManualArtistImage,
                                onRequestPrefetchArtists = viewModel::prefetchArtistImages,
                                modifier = contentModifier
                            )
                        }
                        }
                    }
                }
            }
        }

        // Full-Screen Dedicated Video Player or Music Now Playing Overlay
        AnimatedVisibility(
            visible = (uiState.isPlayerExpanded || uiState.isInPipMode) && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            if (playbackState.currentTrack?.mediaType == MediaType.VIDEO) {
                val videoLibrary = remember(tracks) {
                    tracks.filter { it.mediaType == MediaType.VIDEO }
                }
                VideoPlayerScreen(
                    playbackState = playbackState,
                    player = viewModel.playbackController.playerOrNull,
                    allVideos = videoLibrary,
                    isFullScreen = uiState.isFullScreenVideo,
                    isInPipMode = uiState.isInPipMode,
                    onBack = { viewModel.setPlayerExpanded(false) },
                    onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                    onPause = { viewModel.playbackController.pause() },
                    onSeekTo = { viewModel.playbackController.seekTo(it) },
                    onSkipNext = { viewModel.playbackController.skipNext() },
                    onSkipPrevious = { viewModel.playbackController.skipPrevious() },
                    onSetSpeed = { viewModel.playbackController.setPlaybackSpeed(it) },
                    onSetRepeatMode = { viewModel.playbackController.setRepeatMode(it) },
                    onToggleLike = viewModel::toggleLike,
                    onAddToPlaylist = viewModel::openPlaylistPickerForTrack,
                    onSelectVideo = { selectedVideo ->
                        viewModel.playTrack(selectedVideo, videoLibrary.ifEmpty { listOf(selectedVideo) })
                    },
                    onToggleFullScreen = { viewModel.setFullScreenVideo(!uiState.isFullScreenVideo) },
                    onEnterPipMode = onEnterPipMode
                )
            } else {
                NowPlayingScreen(
                    playbackState = playbackState,
                    isFullBleedMode = settings.fullBleedPlayer,
                    playerBlurBackground = settings.playerBlurBackground,
                    showQualityBadge = settings.showQualityBadge,
                    showSongDnaBadge = settings.showSongDnaBadge,
                    wordByWordLyrics = settings.wordByWordLyrics,
                    onCollapse = { viewModel.setPlayerExpanded(false) },
                    onToggleFullBleed = viewModel::toggleFullBleedPlayer,
                    onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                    onSeekTo = { viewModel.playbackController.seekTo(it) },
                    onSkipNext = { viewModel.playbackController.skipNext() },
                    onSkipPrevious = { viewModel.playbackController.skipPrevious() },
                    onToggleLike = viewModel::toggleLike,
                    onDownloadTrack = { track ->
                        viewModel.openDownloadSheet(
                            if (track.uriString.startsWith("http")) track.uriString else ""
                        )
                    },
                    onSetSpeed = { viewModel.playbackController.setPlaybackSpeed(it) },
                    onToggleShuffle = { viewModel.playbackController.toggleShuffle() },
                    onCycleRepeat = { viewModel.playbackController.cycleRepeatMode() },
                    onConvertOrExtract = { track ->
                        viewModel.setPlayerExpanded(false)
                        currentTab = EchoTab.CONVERT
                        viewModel.openAudioExtractor()
                    },
                    onAddToPlaylist = viewModel::openPlaylistPickerForTrack,
                    onSelectQueueTrack = { track ->
                        viewModel.playTrack(track, playbackState.queue)
                    },
                    onOpenEqualizer = {
                        viewModel.openEqualizer(true)
                    },
                    onOpenSongInfo = viewModel::openSongInfo,
                    onOpenSettings = {
                        viewModel.setPlayerExpanded(false)
                        viewModel.openSettings(true)
                    }
                )
            }
        }

        // Full-Screen Folders Overlay
        AnimatedVisibility(
            visible = uiState.showFoldersScreen && !uiState.isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FoldersScreen(
                tracks = tracks,
                currentTrackId = playbackState.currentTrack?.id,
                onPlayTrack = viewModel::playTrack,
                onPlayNext = viewModel::playNextInQueue,
                onToggleLike = viewModel::toggleLike,
                onAddToPlaylist = viewModel::openPlaylistPickerForTrack,
                onShowSongInfo = viewModel::openSongInfo,
                onBack = { viewModel.openFolders(false) }
            )
        }

        // Full-Screen Listening Stats Overlay
        AnimatedVisibility(
            visible = uiState.showListeningStatsScreen && !uiState.isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ListeningStatsScreen(
                tracks = tracks,
                currentTrackId = playbackState.currentTrack?.id,
                onPlayTrack = viewModel::playTrack,
                onShowSongInfo = viewModel::openSongInfo,
                onBack = { viewModel.openListeningStats(false) }
            )
        }

        // Full-Screen Settings Overlay
        AnimatedVisibility(
            visible = uiState.showSettingsScreen && !uiState.isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            SettingsScreen(
                settings = settings,
                trackCount = tracks.size,
                playlistCount = playlists.size,
                conversionCount = conversionJobs.size,
                viewModel = viewModel,
                onBack = { viewModel.openSettings(false) }
            )
        }

        // Full-Screen 10-Band Equalizer & Audio Effects Overlay
        AnimatedVisibility(
            visible = uiState.showEqualizerScreen && !uiState.isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            EqualizerScreen(
                state = settings.toEqualizerState(),
                onToggleMaster = viewModel::setEqEnabled,
                onSelectPreset = viewModel::selectEqPreset,
                onChangeBandDb = viewModel::setEqBandDb,
                onToggleBassBoost = viewModel::setEqBassBoostEnabled,
                onChangeBassBoostStrength = viewModel::setEqBassBoostStrength,
                onToggleVirtualizer = viewModel::setEqVirtualizerEnabled,
                onChangeVirtualizerStrength = viewModel::setEqVirtualizerStrength,
                onResetFlat = viewModel::resetEqualizerFlat,
                onBack = { viewModel.openEqualizer(false) }
            )
        }

        // Full-Screen About Zero Play Overlay
        AnimatedVisibility(
            visible = uiState.showAboutScreen && !uiState.isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            AboutScreen(
                onOpenAutoUpdate = { viewModel.openAutoUpdate(true) },
                onBack = { viewModel.openAbout(false) }
            )
        }

        // Full-Screen Auto Update Overlay
        AnimatedVisibility(
            visible = uiState.showAutoUpdateScreen && !uiState.isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            AutoUpdateScreen(
                installedVersionLabel = viewModel.getInstalledVersionLabel(),
                autoUpdateEnabled = settings.autoUpdateEnabled,
                selectedChannel = settings.updateChannel,
                updateRepoSlug = settings.updateRepoSlug,
                directUpdateFeedUrl = settings.directUpdateFeedUrl,
                isCheckingUpdates = uiState.isCheckingUpdates,
                updateCheckResultMessage = uiState.updateCheckResultMessage,
                availableRelease = uiState.availableRelease,
                latestGitHubRelease = uiState.latestGitHubRelease,
                isDownloadingUpdate = uiState.isDownloadingUpdate,
                updateDownloadProgress = uiState.updateDownloadProgress,
                updateDownloadStage = uiState.updateDownloadStage,
                downloadedApkPath = uiState.downloadedApkPath,
                needsInstallPermission = uiState.needsInstallPermission,
                onToggleAutoUpdate = viewModel::setAutoUpdateEnabled,
                onSelectChannel = viewModel::setUpdateChannel,
                onCheckForUpdates = viewModel::checkForUpdates,
                onDownloadAndInstall = { viewModel.downloadAndInstallUpdate() },
                onForceDownloadRelease = { rel -> viewModel.downloadAndInstallUpdate(rel) },
                onRetryInstallApk = viewModel::retryInstallDownloadedApk,
                onOpenUnknownSourcesSettings = viewModel::openUnknownSourcesSettings,
                onBack = { viewModel.openAutoUpdate(false) }
            )
        }

        // Full-Screen Audio Tag Editor Overlay
        AnimatedVisibility(
            visible = uiState.tagEditorTrack != null && !uiState.isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            uiState.tagEditorTrack?.let { editTrack ->
                TagEditorScreen(
                    track = editTrack,
                    initialMetadata = uiState.tagEditorInitialMetadata,
                    onSaveCompleted = { tags, artUri, writtenToFile ->
                        viewModel.onTagEditorSaved(editTrack, tags, artUri, writtenToFile)
                    },
                    onShowMessage = viewModel::showStatus,
                    onBack = viewModel::dismissTagEditor
                )
            }
        }

        // Bottom Sheets & Dialogs
        uiState.downloadSheetUrl?.let { initialUrl ->
            MediaDownloaderSheet(
                initialUrl = initialUrl,
                defaultMode = settings.sealDownloadMode,
                defaultPreset = settings.defaultDownloadPreset,
                defaultPreferredVideoFormat = settings.sealPreferredVideoFormat,
                defaultPreferredCodec = settings.preferredDownloadCodec,
                defaultVideoContainer = settings.sealVideoContainer,
                defaultVideoQualityLimit = settings.sealVideoQualityLimit,
                defaultAudioFormat = settings.sealAudioFormat,
                defaultAudioQuality = settings.sealAudioQuality,
                defaultMp3Bitrate = settings.mp3Bitrate,
                defaultConvertAudio = settings.convertSealAudio,
                defaultEmbedSubtitles = settings.embedSubtitles,
                defaultSaveThumbnail = settings.saveDownloadThumbnail,
                onSaveConfigurePrefs = viewModel::saveSealConfigurePrefs,
                onDismiss = viewModel::dismissDownloadSheet,
                onStartDownload = viewModel::startDownload
            )
        }

        if (uiState.showPhotoConverterSheet) {
            PhotoConverterSheet(
                defaultFormat = settings.defaultPhotoFormat,
                defaultQuality = settings.defaultPhotoQuality,
                defaultScalePercent = settings.defaultPhotoScalePercent,
                defaultSkipSmall = settings.skipSmallCompressionSavings,
                onDismiss = viewModel::dismissConverterSheets,
                onStartConvert = viewModel::startPhotoBatchConvert
            )
        }

        if (uiState.showVideoConverterSheet) {
            VideoCompressorSheet(
                preselectedUri = uiState.preselectedConverterUri,
                preselectedTitle = uiState.preselectedConverterTitle,
                inspectedMetrics = uiState.inspectedVideoMetrics,
                defaultPreset = settings.defaultVideoPreset,
                defaultCodec = settings.defaultVideoCodec,
                defaultContainer = settings.defaultVideoContainer,
                defaultBitrateMbps = settings.defaultVideoBitrateMbps,
                defaultStripAudio = settings.stripVideoAudioByDefault,
                defaultSkipSmall = settings.skipSmallCompressionSavings,
                onInspectVideo = viewModel::inspectVideoForSheet,
                onDismiss = viewModel::dismissConverterSheets,
                onStartCompress = viewModel::startVideoCompress
            )
        }

        if (uiState.showAudioExtractorSheet) {
            AudioExtractorSheet(
                preselectedUri = uiState.preselectedConverterUri,
                preselectedTitle = uiState.preselectedConverterTitle,
                defaultMp3Bitrate = settings.mp3Bitrate,
                onDismiss = viewModel::dismissConverterSheets,
                onStartExtract = viewModel::startAudioExtract
            )
        }

        if (uiState.showDeviceInfoDialog) {
            CodecDiagnosticSheet(
                onDismiss = { viewModel.openDeviceInfo(false) }
            )
        }

        if (uiState.showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = viewModel::dismissCreatePlaylistDialog,
                onCreate = { name -> viewModel.createPlaylist(name) }
            )
        }

        if (uiState.showPlaylistEditorSheet) {
            val liveEditingPlaylist = uiState.editingPlaylist?.let { edit ->
                playlists.find { it.id == edit.id } ?: edit
            }
            PlaylistEditorSheet(
                editingPlaylist = liveEditingPlaylist,
                allTracks = tracks,
                onSave = viewModel::savePlaylistEditor,
                onDelete = viewModel::deletePlaylist,
                onDismiss = viewModel::dismissPlaylistEditor
            )
        }

        uiState.trackForPlaylistDialog?.let { track ->
            AddToPlaylistDialog(
                track = track,
                playlists = playlists,
                onDismiss = viewModel::dismissPlaylistPicker,
                onSelectPlaylist = { playlistId -> viewModel.addTrackToPlaylist(playlistId, track) },
                onCreateNewPlaylist = { name -> viewModel.createPlaylist(name, track.id) }
            )
        }

        uiState.songInfoTrack?.let { infoTrack ->
            val liveTrack = tracks.find { it.id == infoTrack.id } ?: infoTrack
            SongInfoBottomSheet(
                track = liveTrack,
                onEditTags = { details -> viewModel.openTagEditor(liveTrack, details) },
                onDismiss = viewModel::dismissSongInfo
            )
        }

        uiState.activeTopSheet?.let { sheetType ->
            EchoTopActionSheet(
                sheetType = sheetType,
                tracks = tracks,
                darkTheme = isDarkThemeActive,
                dynamicColor = settings.dynamicColor,
                onToggleDarkTheme = { dark ->
                    viewModel.setThemeMode(if (dark) ThemeModePref.DARK else ThemeModePref.LIGHT)
                },
                onToggleDynamicColor = viewModel::setDynamicColor,
                onRescanMedia = viewModel::rescanMediaStore,
                onPlayTrack = { track -> viewModel.playTrack(track, tracks) },
                onOpenFullSettings = { viewModel.openSettings(true) },
                onDismiss = { viewModel.openTopSheet(null) }
            )
        }
        }
    }
}

@Composable
private fun EchoFloatingPillNavBar(
    currentTab: EchoTab,
    onSelectTab: (EchoTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoTab.entries.forEach { tab ->
                val selected = currentTab == tab
                Surface(
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.80f)
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable { onSelectTab(tab) }
                        .testTag(tab.testTag)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
