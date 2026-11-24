package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val EchoDarkColorScheme = darkColorScheme(
    primary = EchoPrimary,
    onPrimary = EchoOnPrimary,
    primaryContainer = EchoPrimaryContainer,
    onPrimaryContainer = EchoOnPrimaryContainer,
    secondary = EchoSecondary,
    onSecondary = EchoOnSecondary,
    secondaryContainer = EchoSecondaryContainer,
    onSecondaryContainer = EchoOnSecondaryContainer,
    tertiary = EchoTertiary,
    onTertiary = EchoOnTertiary,
    tertiaryContainer = EchoTertiaryContainer,
    onTertiaryContainer = EchoOnTertiaryContainer,
    background = EchoBackground,
    onBackground = EchoOnBackground,
    surface = EchoSurface,
    onSurface = EchoOnSurface,
    onSurfaceVariant = EchoOnSurfaceVariant,
    surfaceContainerLowest = EchoSurfaceContainerLowest,
    surfaceContainerLow = EchoSurfaceContainerLow,
    surfaceContainer = EchoSurfaceContainer,
    surfaceContainerHigh = EchoSurfaceContainerHigh,
    surfaceContainerHighest = EchoSurfaceContainerHighest,
    outline = EchoOutline,
    outlineVariant = EchoOutlineVariant,
    error = EchoError
)

private val EchoAmoledColorScheme = EchoDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E13),
    surfaceContainer = Color(0xFF15151C),
    surfaceContainerHigh = Color(0xFF1D1D26)
)

private val EchoLightColorScheme = lightColorScheme(
    primary = EchoLightPrimary,
    onPrimary = EchoLightOnPrimary,
    primaryContainer = EchoLightPrimaryContainer,
    onPrimaryContainer = EchoLightOnPrimaryContainer,
    secondary = EchoLightSecondary,
    onSecondary = EchoLightOnSecondary,
    secondaryContainer = EchoLightSecondaryContainer,
    onSecondaryContainer = EchoLightOnSecondaryContainer,
    tertiary = EchoLightTertiary,
    onTertiary = EchoLightOnTertiary,
    tertiaryContainer = EchoLightTertiaryContainer,
    onTertiaryContainer = EchoLightOnTertiaryContainer,
    background = EchoLightBackground,
    onBackground = EchoLightOnSurface,
    surface = EchoLightSurface,
    onSurface = EchoLightOnSurface,
    onSurfaceVariant = EchoLightOnSurfaceVariant,
    surfaceContainerLowest = EchoLightSurfaceContainerLowest,
    surfaceContainerLow = EchoLightSurfaceContainerLow,
    surfaceContainer = EchoLightSurfaceContainer,
    surfaceContainerHigh = EchoLightSurfaceContainerHigh,
    surfaceContainerHighest = EchoLightSurfaceContainerHighest,
    outline = EchoLightOutline,
    outlineVariant = EchoLightOutlineVariant,
    error = EchoLightError
)

@Composable
fun EchoMusicTheme(
    darkTheme: Boolean = true,
    pureBlackAmoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && pureBlackAmoled) {
                base.copy(background = Color.Black, surface = Color.Black)
            } else {
                base
            }
        }
        darkTheme && pureBlackAmoled -> EchoAmoledColorScheme
        darkTheme -> EchoDarkColorScheme
        else -> EchoLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EchoTypography,
        content = content
    )
}
