package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.CodecCapability
import com.example.diagnostics.CodecSupportLevel
import com.example.diagnostics.DeviceCapabilityInspector

@Composable
fun DeviceInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val report = remember(context) { DeviceCapabilityInspector.inspect(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.testTag("device_info_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeveloperBoard,
                    contentDescription = "Device Diagnostics",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Device Info & Codecs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Live MediaCodecList Hardware Diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Device & Build Section
                DiagnosticGroupCard(
                    title = "DEVICE & BUILD",
                    icon = Icons.Rounded.PhoneAndroid
                ) {
                    DiagnosticKeyValueRow("Model", report.deviceModel)
                    DiagnosticKeyValueRow("OS", report.androidVersion)
                    DiagnosticKeyValueRow("Build number", report.buildNumber)
                    DiagnosticKeyValueRow("Kernel", report.kernelVersion)
                    DiagnosticKeyValueRow("ABIs", report.supportedAbis)
                }

                // 2. Display Section
                DiagnosticGroupCard(
                    title = "DISPLAY",
                    icon = Icons.Rounded.ScreenshotMonitor
                ) {
                    DiagnosticKeyValueRow("Resolution", report.displayResolution)
                    DiagnosticKeyValueRow("Density", "${report.displayDensityDpi} dpi")
                    DiagnosticKeyValueRow("Refresh rate", "${report.refreshRateHz} Hz")
                    DiagnosticKeyValueRow("HDR support", report.hdrCapabilities)
                }

                // 3. Video & Audio Decoding Capabilities
                DiagnosticGroupCard(
                    title = "VIDEO & AUDIO DECODING",
                    icon = Icons.Rounded.Memory
                ) {
                    report.codecs.forEach { codec ->
                        CodecCapabilityRow(codec = codec)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val recommended = report.recommendedAutoVideoCodec()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (report.isAv1SoftwareOnly) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (report.isAv1SoftwareOnly) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (report.isAv1SoftwareOnly) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto Codec Pick: ${recommended.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (report.isAv1SoftwareOnly) {
                                        "AV1 is software-decoded on this device. Media3 dav1d software extension is active for smooth AV1 playback; choose Auto/HEVC/H.264 for lowest battery use."
                                    } else {
                                        "Hardware AV1 acceleration detected — 4K AV1 + Opus MKV files decode natively with minimal battery impact."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                modifier = Modifier.testTag("device_info_close_btn")
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun DiagnosticGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun DiagnosticKeyValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodecCapabilityRow(codec: CodecCapability) {
    val (pillBg, pillFg) = when (codec.decodeSupport) {
        CodecSupportLevel.HARDWARE -> Color(0xFF1B5E20).copy(alpha = 0.22f) to Color(0xFF4CAF50)
        CodecSupportLevel.SOFTWARE_ONLY -> Color(0xFFE65100).copy(alpha = 0.20f) to Color(0xFFFF9800)
        CodecSupportLevel.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (codec.isAudio) Icons.Rounded.GraphicEq else Icons.Rounded.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = codec.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = codec.media3ExtensionNote ?: codec.primaryDecoderName ?: codec.mimeType
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = pillBg
        ) {
            Text(
                text = codec.decodeSupport.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = pillFg,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
