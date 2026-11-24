package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UpdateChannelPref
import com.example.ui.components.formatBytes
import com.example.updater.AppReleaseInfo

@Composable
fun AutoUpdateScreen(
    installedVersionLabel: String,
    autoUpdateEnabled: Boolean,
    selectedChannel: UpdateChannelPref,
    updateRepoSlug: String,
    directUpdateFeedUrl: String,
    isCheckingUpdates: Boolean,
    updateCheckResultMessage: String?,
    availableRelease: AppReleaseInfo?,
    latestGitHubRelease: AppReleaseInfo? = null,
    isDownloadingUpdate: Boolean,
    updateDownloadProgress: Float,
    downloadedApkPath: String?,
    needsInstallPermission: Boolean,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onSelectChannel: (UpdateChannelPref) -> Unit,
    onCheckForUpdates: (repoOverride: String?, feedOverride: String?) -> Unit,
    onDownloadAndInstall: () -> Unit,
    onForceDownloadRelease: (AppReleaseInfo) -> Unit = {},
    onRetryInstallApk: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var repoText by remember(updateRepoSlug) { mutableStateOf(updateRepoSlug) }
    var directFeedText by remember(directUpdateFeedUrl) { mutableStateOf(directUpdateFeedUrl) }
    var showDirectFeedInput by remember(directUpdateFeedUrl) {
        mutableStateOf(directUpdateFeedUrl.isNotBlank())
    }
    var showSetupGuideDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("auto_update_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar with Back button and large Expressive title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("auto_update_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = installedVersionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    IconButton(onClick = { showSetupGuideDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = "GitHub & AI Studio Update Guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Auto update",
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            // Primary-container pill banner toggle
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onToggleAutoUpdate(!autoUpdateEnabled) }
                    .testTag("auto_update_enable_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable auto update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = onToggleAutoUpdate,
                        thumbContent = if (autoUpdateEnabled) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("auto_update_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Update channel header
            Text(
                text = "Update channel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            // Radio options
            UpdateChannelPref.entries.forEach { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectChannel(channel) }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .testTag("update_channel_${channel.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    RadioButton(
                        selected = selectedChannel == channel,
                        onClick = { onSelectChannel(channel) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // GitHub & AI Studio Update Source Card
            Text(
                text = "Update source (GitHub / AI Studio)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = repoText,
                    onValueChange = { repoText = it },
                    label = { Text("Public GitHub Repo (owner/repo or URL)") },
                    placeholder = { Text("nilimadas567345-sudo/Zero-play-") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Code, contentDescription = null)
                    },
                    trailingIcon = {
                        val slugOrDefault = repoText.trim().ifBlank { "nilimadas567345-sudo/Zero-play-" }
                        val releasesUrl = if (slugOrDefault.startsWith("http")) {
                            slugOrDefault.removeSuffix(".git").trimEnd('/') + "/releases"
                        } else {
                            "https://github.com/${slugOrDefault.trim('/')}/releases"
                        }
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.testTag("open_github_releases_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Open GitHub Releases"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_repo_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDirectFeedInput = !showDirectFeedInput }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (showDirectFeedInput) "Hide Direct URL"
                            else "Direct APK / JSON URL"
                        )
                    }

                    TextButton(onClick = { showSetupGuideDialog = true }) {
                        Text("AI Studio -> GitHub guide")
                    }
                }

                AnimatedVisibility(visible = showDirectFeedInput) {
                    OutlinedTextField(
                        value = directFeedText,
                        onValueChange = { directFeedText = it },
                        label = { Text("Direct .apk or version.json URL (optional)") },
                        placeholder = { Text("https://.../app-debug.apk or version.json") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_feed_input")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Right-aligned Check for updates tonal pill button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = {
                        onCheckForUpdates(repoText.trim(), directFeedText.trim())
                    },
                    enabled = !isCheckingUpdates && !isDownloadingUpdate,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("check_for_updates_btn")
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Update,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCheckingUpdates) "Checking..." else "Check for updates",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Available Release Self-Update Card
            AnimatedVisibility(visible = availableRelease != null) {
                availableRelease?.let { release ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .testTag("update_available_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${release.title} (${release.resolvedVersionLabel})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val sizeNote = if (release.apkSizeBytes > 0L) {
                                        "APK size: ${formatBytes(release.apkSizeBytes)}"
                                    } else {
                                        "Ready to install"
                                    }
                                    Text(
                                        text = if (release.isPrerelease) "Preview • $sizeNote" else "Stable • $sizeNote",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5
                            )

                            if (isDownloadingUpdate) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LinearProgressIndicator(
                                        progress = { updateDownloadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "Downloading update package... ${(updateDownloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                when {
                                    needsInstallPermission -> {
                                        Button(
                                            onClick = onOpenUnknownSourcesSettings,
                                            shape = CircleShape,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Allow Install")
                                        }
                                        OutlinedButton(
                                            onClick = onRetryInstallApk,
                                            shape = CircleShape,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Install APK")
                                        }
                                    }
                                    downloadedApkPath != null -> {
                                        Button(
                                            onClick = onRetryInstallApk,
                                            shape = CircleShape,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Install Update")
                                        }
                                    }
                                    !release.apkDownloadUrl.isNullOrBlank() -> {
                                        Button(
                                            onClick = onDownloadAndInstall,
                                            enabled = !isDownloadingUpdate,
                                            shape = CircleShape,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isDownloadingUpdate) "Downloading..." else "Download & Update")
                                        }
                                    }
                                }

                                if (release.releasePageUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(release.releasePageUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            }
                                        },
                                        shape = CircleShape
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Release")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !updateCheckResultMessage.isNullOrBlank() && availableRelease == null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = updateCheckResultMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (latestGitHubRelease != null && !latestGitHubRelease.apkDownloadUrl.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onForceDownloadRelease(latestGitHubRelease) },
                                    enabled = !isDownloadingUpdate,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("force_reinstall_latest_apk_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val sizeTag = if (latestGitHubRelease.apkSizeBytes > 0L) {
                                        " (${formatBytes(latestGitHubRelease.apkSizeBytes)})"
                                    } else ""
                                    Text("Reinstall Latest APK$sizeTag")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )

            // Info section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Update channel information",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Install pre-release builds to preview new features and changes.\n\nThere will be some instability in these versions, so please don't hesitate to give us feedback if you experience any problems to help us improve the app for the future.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }

    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            title = {
                Text(
                    text = "Google AI Studio -> GitHub Self-Update",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Zero Play is pre-configured with nilimadas567345-sudo/Zero-play- and includes .github/workflows/release-apk.yml:\n\n" +
                        "1. Push changes from Google AI Studio to https://github.com/nilimadas567345-sudo/Zero-play-.\n\n" +
                        "2. GitHub Actions automatically increments versionCode & versionName on every push to main, builds the signed APK, and publishes a new GitHub Release with ZeroPlay-v*.apk + version.json.\n\n" +
                        "3. Zero Play auto-checks on launch (or when you tap Check for updates), compares release titles, tags, version.json, and publish timestamps, downloads the APK in parallel, and opens the Android installer directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showSetupGuideDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}
