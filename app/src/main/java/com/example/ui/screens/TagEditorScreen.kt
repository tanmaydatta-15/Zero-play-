package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaTrack
import com.example.metadata.AudioMetadataEngine
import com.example.metadata.EditableAudioTags
import com.example.metadata.SongDetailedMetadata
import com.example.metadata.TagSaveResult
import com.example.ui.components.TrackArtwork
import kotlinx.coroutines.launch

@Composable
fun TagEditorScreen(
    track: MediaTrack,
    initialMetadata: SongDetailedMetadata?,
    onSaveCompleted: (EditableAudioTags, String?, Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember(track.id) { mutableStateOf(initialMetadata?.tags?.title ?: track.title) }
    var album by remember(track.id) { mutableStateOf(initialMetadata?.tags?.album ?: track.album) }
    var artist by remember(track.id) { mutableStateOf(initialMetadata?.tags?.artist ?: track.artist) }
    var albumArtist by remember(track.id) { mutableStateOf(initialMetadata?.tags?.albumArtist ?: track.albumArtist) }
    var composer by remember(track.id) { mutableStateOf(initialMetadata?.tags?.composer ?: track.composer) }
    var genre by remember(track.id) { mutableStateOf(initialMetadata?.tags?.genre ?: track.genre) }
    var lyricist by remember(track.id) { mutableStateOf(initialMetadata?.tags?.lyricist ?: track.lyricist) }
    var trackNumber by remember(track.id) { mutableStateOf(initialMetadata?.tags?.trackNumber ?: track.trackNumber) }
    var discNumber by remember(track.id) { mutableStateOf(initialMetadata?.tags?.discNumber ?: track.discNumber) }
    var year by remember(track.id) { mutableStateOf(initialMetadata?.tags?.year ?: track.year) }
    var comment by remember(track.id) { mutableStateOf(initialMetadata?.tags?.comment ?: track.comment) }
    var embeddedLyrics by remember(track.id) {
        mutableStateOf(
            initialMetadata?.tags?.embeddedLyrics
                ?: track.embeddedLyrics.ifBlank { track.lyrics.joinToString("\n") { it.text } }
        )
    }

    var selectedCoverUri by remember(track.id) { mutableStateOf<Uri?>(null) }
    var removeArtwork by remember(track.id) { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // If opened directly without pre-inspected metadata, inspect once to populate all 11 fields
    LaunchedEffect(track.id) {
        if (initialMetadata == null) {
            val inspected = AudioMetadataEngine.inspectTrack(context, track)
            val t = inspected.tags
            if (title.isBlank()) title = t.title
            if (album.isBlank()) album = t.album
            if (artist.isBlank()) artist = t.artist
            if (albumArtist.isBlank()) albumArtist = t.albumArtist
            if (composer.isBlank()) composer = t.composer
            if (genre.isBlank()) genre = t.genre
            if (lyricist.isBlank()) lyricist = t.lyricist
            if (trackNumber.isBlank()) trackNumber = t.trackNumber
            if (discNumber.isBlank()) discNumber = t.discNumber
            if (year.isBlank()) year = t.year
            if (comment.isBlank()) comment = t.comment
            if (embeddedLyrics.isBlank()) embeddedLyrics = t.embeddedLyrics
        }
    }

    fun buildCurrentTags(): EditableAudioTags = EditableAudioTags(
        title = title,
        album = album,
        artist = artist,
        albumArtist = albumArtist,
        composer = composer,
        genre = genre,
        lyricist = lyricist,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        comment = comment,
        embeddedLyrics = embeddedLyrics
    )

    // Photo Picker (zero-permission Google Play compliant)
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedCoverUri = uri
            removeArtwork = false
        }
    }

    lateinit var triggerSave: () -> Unit

    // Scoped Storage MediaStore write permission launcher
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            triggerSave()
        } else {
            isSaving = false
            onShowMessage("Storage write permission declined — tags not modified on disk.")
        }
    }

    triggerSave = {
        if (!isSaving) {
            isSaving = true
            val tags = buildCurrentTags()
            scope.launch {
                when (
                    val saveRes = AudioMetadataEngine.saveTags(
                        context = context,
                        track = track,
                        tags = tags,
                        newArtworkUri = selectedCoverUri,
                        removeArtwork = removeArtwork
                    )
                ) {
                    is TagSaveResult.Success -> {
                        isSaving = false
                        onSaveCompleted(saveRes.updatedTags, saveRes.updatedArtworkUri, saveRes.writtenToFile)
                    }
                    is TagSaveResult.NeedsWritePermission -> {
                        isSaving = false
                        runCatching {
                            writePermissionLauncher.launch(
                                IntentSenderRequest.Builder(saveRes.intentSender).build()
                            )
                        }.onFailure {
                            onShowMessage("Unable to request file write permission.")
                        }
                    }
                    is TagSaveResult.Error -> {
                        isSaving = false
                        onShowMessage(saveRes.message)
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("tag_editor_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top App Bar with Back button and Green Checkmark Save button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("tag_editor_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Edit tags",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Surface(
                        onClick = { triggerSave() },
                        shape = CircleShape,
                        color = Color(0xFF22C55E),
                        contentColor = Color.White,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(44.dp)
                            .testTag("tag_editor_save_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Save tags",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Square Album Artwork with Pick Gallery & Delete circular overlay buttons
                Box(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .fillMaxWidth(0.68f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        removeArtwork -> {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Artwork removed",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        selectedCoverUri != null -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(selectedCoverUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Selected album artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            TrackArtwork(
                                track = track,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 24.dp
                            )
                        }
                    }

                    // Bottom-end circular overlay buttons (Pick cover / Delete cover)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = {
                                coverPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tag_editor_pick_cover_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = "Pick new cover image",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                selectedCoverUri = null
                                removeArtwork = true
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tag_editor_delete_cover_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete existing artwork",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // EDIT EMBEDDED LYRICS action button
                FilledTonalButton(
                    onClick = { showLyricsDialog = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_editor_lyrics_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (embeddedLyrics.isBlank()) "EDIT EMBEDDED LYRICS" else "EDIT EMBEDDED LYRICS (${embeddedLyrics.lines().count { it.isNotBlank() }} lines)",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Editable metadata fields
                TagField("Title", title, { title = it }, testTag = "tag_field_title")
                TagField("Album", album, { album = it }, testTag = "tag_field_album")
                TagField("Artist", artist, { artist = it }, testTag = "tag_field_artist")
                TagField("Album-Artist", albumArtist, { albumArtist = it }, testTag = "tag_field_album_artist")
                TagField("Composer", composer, { composer = it }, testTag = "tag_field_composer")
                TagField("Genre", genre, { genre = it }, testTag = "tag_field_genre")
                TagField("Lyricist", lyricist, { lyricist = it }, testTag = "tag_field_lyricist")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TagField(
                        label = "Track number",
                        value = trackNumber,
                        onValueChange = { trackNumber = it },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        testTag = "tag_field_track_number"
                    )
                    TagField(
                        label = "Disc number",
                        value = discNumber,
                        onValueChange = { discNumber = it },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                        testTag = "tag_field_disc_number"
                    )
                }

                TagField(
                    label = "Year",
                    value = year,
                    onValueChange = { year = it },
                    keyboardType = KeyboardType.Number,
                    testTag = "tag_field_year"
                )
                TagField(
                    label = "Comment",
                    value = comment,
                    onValueChange = { comment = it },
                    singleLine = false,
                    minLines = 2,
                    testTag = "tag_field_comment"
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showLyricsDialog) {
        var draftLyrics by remember(embeddedLyrics) { mutableStateOf(embeddedLyrics) }
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = {
                Text(
                    text = "Embedded lyrics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = draftLyrics,
                    onValueChange = { draftLyrics = it },
                    placeholder = { Text("Paste plain or synced [mm:ss.xx] LRC lyrics...") },
                    minLines = 7,
                    maxLines = 12,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_editor_lyrics_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        embeddedLyrics = draftLyrics
                        showLyricsDialog = false
                    },
                    modifier = Modifier.testTag("tag_editor_lyrics_save_btn")
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TagField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.testTag(testTag)
    )
}
