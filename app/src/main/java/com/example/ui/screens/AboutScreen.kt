package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.example.R

private const val DEV_INSTAGRAM_URL = "https://www.instagram.com/tanmay.sudo?dmpr=MXdncXZob2xja2k2dA=="
private const val GITHUB_REPO_URL = "https://github.com/tanmaydatta-15/Zero-play-"

private enum class AboutModalType(val title: String, val body: String) {
    CHANGELOG(
        title = "Zero Play v2.0 Changelog",
        body = "• Hybrid 3-Route Extraction Engine: NewPipeExtractor fast path (YouTube, SoundCloud, PeerTube, Bandcamp), yt-dlp-android universal fallback (1,000+ sites), and Direct Stream / HLS (.m3u8) remuxing\n" +
            "• FFmpegKit full-gpl muxing & transcoding: MKV (Matroska), MP4, WebM, AV1 (libsvtav1 / libaom-av1), VP9, HEVC, H.264, Opus (libopus), MP3 (libmp3lame 320k/256k/192k/128k/V0), M4A & FLAC\n" +
            "• Media3 ExoPlayer AV1 software decoder (media3-decoder-av1 / dav1d) for smooth AV1 playback on software-only devices\n" +
            "• Live Device Info & Hardware/Software Codec Diagnostics sheet in Settings\n" +
            "• WorkManager foreground download queue with live progress, speed, ETA & Cancel notifications"
    ),
    STACK(
        title = "Zero Play Core Stack",
        body = "• AndroidX Media3 1.9.0 ExoPlayer + media3-decoder-av1 (dav1d) + MediaSessionService\n" +
            "• NewPipeExtractor v0.27.1 + OkHttp fast extraction router\n" +
            "• yt-dlp-android (0.18.1) universal fallback + FFmpegKit full-gpl 8.1.9\n" +
            "• WorkManager foreground dataSync download queue + MediaStore / Room indexing\n" +
            "• Material Design 3 Expressive with System / Light / Dark & AMOLED black themes"
    ),
    DONATE(
        title = "Support Zero Play",
        body = "Zero Play is ad-free and built with passion by Tanmay Datta for high-fidelity offline playback and local media conversion."
    ),
    FEEDBACK(
        title = "Provide Feedback",
        body = "Have a bug report or feature idea? Reach out on Instagram (@tanmay.sudo) or inspect codecs under Settings > Device Info."
    ),
    RATE(
        title = "Rate Zero Play",
        body = "Enjoying Zero Play's audio, video, and converter toolkit? Share it with friends who love clean ad-free media playback."
    ),
    LICENSES(
        title = "Open Source Licences",
        body = "• TeamNewPipe / NewPipeExtractor (GPL-3.0)\n" +
            "• youtubedl-android / yt-dlp (GPL-3.0 / Unlicense)\n" +
            "• FFmpegKit full-gpl (GPL-3.0: libsvtav1, libaom-av1, libdav1d, libopus, libmp3lame, libx264, libx265, Matroska)\n" +
            "• AndroidX Media3, ExoPlayer, Transformer & AV1 Decoder (Apache 2.0)\n" +
            "• Jetpack Compose, Material 3, WorkManager, Room & DataStore (Apache 2.0)\n" +
            "• OkHttp & Coil Image Loading (Apache 2.0)"
    )
}

@Composable
fun AboutScreen(
    onOpenAutoUpdate: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var activeModal by remember { mutableStateOf<AboutModalType?>(null) }
    val versionBadgeText = remember(context) {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val vName = pkgInfo?.versionName ?: "1.0"
        val vCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 1L
        "$vName ($vCode)"
    }

    val openInstagram: () -> Unit = {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DEV_INSTAGRAM_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    val openGitHubRepo: () -> Unit = {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("about_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("about_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Card 1: Zero Play Logo, Name, Version Pill & Action Buttons
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF131315)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_zero_play_logo),
                                    contentDescription = "Zero Play Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Zero Play",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = versionBadgeText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { activeModal = AboutModalType.CHANGELOG },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Release Notes",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = openInstagram,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("about_instagram_btn")
                                ) {
                                    InstagramGlyph(
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Instagram",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Card 2: Developed and designed by Tanmay Datta with Overlapping Circular Portrait
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 42.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 56.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "DEVELOPED AND DESIGNED BY",
                                    style = MaterialTheme.typography.labelMedium,
                                    letterSpacing = 1.4.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tanmay Datta",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(18.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable(onClick = openInstagram)
                                            .testTag("about_dev_instagram_icon")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            InstagramGlyph(
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    AboutCircularActionButton(
                                        icon = Icons.Rounded.Language,
                                        contentDescription = "Core Stack",
                                        onClick = { activeModal = AboutModalType.STACK }
                                    )
                                    AboutCircularActionButton(
                                        icon = Icons.Rounded.Code,
                                        contentDescription = "Open Source Licenses",
                                        onClick = { activeModal = AboutModalType.LICENSES }
                                    )
                                    AboutCircularActionButton(
                                        icon = Icons.Rounded.Email,
                                        contentDescription = "Provide Feedback",
                                        onClick = { activeModal = AboutModalType.FEEDBACK }
                                    )
                                }
                            }
                        }

                        // Overlapping Circular Developer Portrait
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .align(Alignment.TopCenter)
                                .clip(CircleShape)
                                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_dev_tanmay_datta),
                                contentDescription = "Tanmay Datta",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                // Card 3: SUPPORT Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = "SUPPORT",
                                style = MaterialTheme.typography.labelMedium,
                                letterSpacing = 1.4.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )

                            AboutSupportRow(
                                icon = Icons.Rounded.Update,
                                title = "Auto update",
                                onClick = onOpenAutoUpdate,
                                testTag = "about_auto_update_row"
                            )
                            AboutSupportRow(
                                icon = Icons.Rounded.Code,
                                title = "GitHub repository",
                                onClick = openGitHubRepo,
                                testTag = "about_github_repo_row"
                            )
                            AboutSupportRow(
                                icon = Icons.Rounded.History,
                                title = "Changelog",
                                onClick = { activeModal = AboutModalType.CHANGELOG }
                            )
                            AboutSupportRow(
                                icon = Icons.Rounded.FavoriteBorder,
                                title = "Donate",
                                onClick = { activeModal = AboutModalType.DONATE }
                            )
                            AboutSupportRow(
                                icon = Icons.Rounded.Info,
                                title = "Provide feedback",
                                onClick = { activeModal = AboutModalType.FEEDBACK }
                            )
                            AboutSupportRow(
                                icon = Icons.Rounded.StarOutline,
                                title = "Rate Zero Play",
                                onClick = { activeModal = AboutModalType.RATE }
                            )
                            AboutSupportRow(
                                icon = Icons.Rounded.Description,
                                title = "Open source licences",
                                onClick = { activeModal = AboutModalType.LICENSES }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Unified",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 28.dp)
                    )
                }
            }
        }
    }

    activeModal?.let { modal ->
        AlertDialog(
            onDismissRequest = { activeModal = null },
            title = {
                Text(
                    text = modal.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = modal.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { activeModal = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun InstagramGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.11f
        val inset = strokeWidth / 2f
        val rectSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val corner = size.minDimension * 0.28f
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, inset),
            size = rectSize,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.22f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.065f,
            center = Offset(size.width * 0.74f, size.height * 0.26f)
        )
    }
}

@Composable
private fun AboutCircularActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AboutSupportRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
