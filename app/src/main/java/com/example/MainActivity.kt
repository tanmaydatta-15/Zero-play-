package com.example

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ThemeModePref
import com.example.ui.EchoMusicApp
import com.example.ui.EchoViewModel
import com.example.ui.theme.EchoMusicTheme

class MainActivity : ComponentActivity() {

    private val echoViewModel: EchoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by echoViewModel.settings.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val useDarkTheme = when (settings.themeMode) {
                ThemeModePref.SYSTEM -> systemInDark
                ThemeModePref.DARK -> true
                ThemeModePref.LIGHT -> false
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !useDarkTheme
                    controller.isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            EchoMusicTheme(
                darkTheme = useDarkTheme,
                pureBlackAmoled = settings.pureBlackAmoled,
                dynamicColor = settings.dynamicColor
            ) {
                EchoMusicApp(
                    viewModel = echoViewModel,
                    onEnterPipMode = { triggerPictureInPicture() }
                )
            }
        }
    }

    private fun triggerPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val playback = echoViewModel.playbackState.value
        val autoPip = echoViewModel.settings.value.autoPipForVideo
        if (autoPip && playback.isVideoContent && playback.isPlaying) {
            triggerPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        echoViewModel.setPipMode(isInPictureInPictureMode)
    }
}
