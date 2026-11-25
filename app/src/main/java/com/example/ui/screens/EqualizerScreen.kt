package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.EqualizerPreset
import com.example.playback.EqualizerSpec
import com.example.playback.EqualizerState
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    state: EqualizerState,
    onToggleMaster: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onChangeBandDb: (bandIndex: Int, db: Float) -> Unit,
    onToggleBassBoost: (Boolean) -> Unit,
    onChangeBassBoostStrength: (Int) -> Unit,
    onToggleVirtualizer: (Boolean) -> Unit,
    onChangeVirtualizerStrength: (Int) -> Unit,
    onResetFlat: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar with Back button and Master On/Off Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("equalizer_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Equalizer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (state.enabled) "10-Band DSP • ${state.preset.label}" else "Bypassed (Off)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onResetFlat,
                    modifier = Modifier.testTag("equalizer_reset_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Flat")
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggleMaster,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("equalizer_master_switch")
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 10-Band Vertical Frequency Slider Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (state.enabled) 1f else 0.52f)
                        .testTag("equalizer_bands_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "10-Band Graphic EQ",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "±12 dB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(256.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left dB scale guide
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(top = 14.dp, bottom = 44.dp, end = 4.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "+12",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "0",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "-12",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 10 vertical frequency bands
                            val bands = EqualizerSpec.normalizeBands(state.bandLevelsDb)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                EqualizerSpec.BAND_LABELS.forEachIndexed { index, freqLabel ->
                                    val bandDb = bands[index]
                                    VerticalEqBandColumn(
                                        freqLabel = freqLabel,
                                        dbValue = bandDb,
                                        enabled = state.enabled,
                                        onValueChange = { newDb ->
                                            onChangeBandDb(index, newDb)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("eq_band_$index")
                                    )
                                }
                            }
                        }
                    }
                }

                // Horizontally Scrollable Preset Filter Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EqualizerPreset.entries.forEach { preset ->
                            val selected = state.preset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectPreset(preset) },
                                label = {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("eq_preset_${preset.name.lowercase()}")
                            )
                        }
                    }
                }

                // Bass Boost Effect Card
                AudioEffectSliderCard(
                    icon = Icons.Rounded.Speaker,
                    title = "Bass Boost",
                    subtitle = "Lifts the bottom two octaves without touching the rest of the curve",
                    enabled = state.enabled && state.bassBoostEnabled,
                    masterEnabled = state.enabled,
                    strength = state.bassBoostStrength,
                    onToggle = onToggleBassBoost,
                    onStrengthChange = onChangeBassBoostStrength,
                    switchTestTag = "eq_bass_boost_switch",
                    sliderTestTag = "eq_bass_boost_slider"
                )

                // Surround Sound (Virtualizer) Effect Card
                AudioEffectSliderCard(
                    icon = Icons.Rounded.SurroundSound,
                    title = "Surround Sound",
                    subtitle = "Widens the stereo image to simulate spatial depth",
                    enabled = state.enabled && state.virtualizerEnabled,
                    masterEnabled = state.enabled,
                    strength = state.virtualizerStrength,
                    onToggle = onToggleVirtualizer,
                    onStrengthChange = onChangeVirtualizerStrength,
                    switchTestTag = "eq_virtualizer_switch",
                    sliderTestTag = "eq_virtualizer_slider"
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun VerticalEqBandColumn(
    freqLabel: String,
    dbValue: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedDb by animateFloatAsState(
        targetValue = dbValue,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.8f),
        label = "eq_band_$freqLabel"
    )
    val activeColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant
    val thumbColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Frequency label at top
        Text(
            text = freqLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Vertical slider track with drag & tap gestures
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = 1f - (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val db = EqualizerSpec.MIN_DB + fraction * (EqualizerSpec.MAX_DB - EqualizerSpec.MIN_DB)
                        val stepped = (db * 2f).roundToInt() / 2f
                        onValueChange(EqualizerSpec.clampDb(stepped))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val fraction = 1f - (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val db = EqualizerSpec.MIN_DB + fraction * (EqualizerSpec.MAX_DB - EqualizerSpec.MIN_DB)
                        val stepped = (db * 2f).roundToInt() / 2f
                        onValueChange(EqualizerSpec.clampDb(stepped))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val padY = 10.dp.toPx()
                val usableHeight = (size.height - padY * 2f).coerceAtLeast(1f)
                val topY = padY
                val bottomY = size.height - padY
                val zeroY = topY + usableHeight / 2f

                val normalized = ((animatedDb - EqualizerSpec.MIN_DB) /
                    (EqualizerSpec.MAX_DB - EqualizerSpec.MIN_DB)).coerceIn(0f, 1f)
                val thumbY = bottomY - normalized * usableHeight

                // Zero dB tick
                drawLine(
                    color = zeroLineColor.copy(alpha = 0.6f),
                    start = Offset(cx - 7.dp.toPx(), zeroY),
                    end = Offset(cx + 7.dp.toPx(), zeroY),
                    strokeWidth = 1.dp.toPx()
                )

                // Background rail
                val trackWidth = 4.dp.toPx()
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(cx - trackWidth / 2f, topY),
                    size = Size(trackWidth, usableHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                // Active gain fill from 0 dB center line to thumb
                val fillTop = minOf(zeroY, thumbY)
                val fillHeight = kotlin.math.abs(thumbY - zeroY)
                if (fillHeight > 0.5f) {
                    drawLine(
                        color = activeColor,
                        start = Offset(cx, fillTop),
                        end = Offset(cx, fillTop + fillHeight),
                        strokeWidth = trackWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Tactile thumb
                drawCircle(
                    color = thumbColor,
                    radius = 7.dp.toPx(),
                    center = Offset(cx, thumbY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(cx, thumbY)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live dB readout below each band
        val formattedDb = when {
            animatedDb > 0.05f -> "+${animatedDb.roundToInt()}"
            animatedDb < -0.05f -> "${animatedDb.roundToInt()}"
            else -> "0"
        }
        Text(
            text = "${formattedDb}dB",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled && kotlin.math.abs(animatedDb) > 0.1f) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AudioEffectSliderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    masterEnabled: Boolean,
    strength: Int,
    onToggle: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    switchTestTag: String,
    sliderTestTag: String
) {
    val percent = ((strength.coerceIn(0, 1000) / 1000f) * 100f).roundToInt()
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (masterEnabled) 1f else 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    enabled = masterEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag(switchTestTag)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = strength.coerceIn(0, 1000).toFloat(),
                    onValueChange = { onStrengthChange(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(sliderTestTag)
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(42.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
