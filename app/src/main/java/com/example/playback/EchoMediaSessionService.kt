package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.data.local.PreferencesManager
import com.example.diagnostics.DeviceCapabilityInspector
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EchoMediaSessionService : MediaSessionService() {

    companion object {
        const val CMD_SET_SKIP_SILENCE = "com.example.playback.SET_SKIP_SILENCE"
        const val CMD_SET_NOISY_PAUSE = "com.example.playback.SET_NOISY_PAUSE"
        const val CMD_APPLY_AUDIO_EFFECTS = "com.example.playback.APPLY_AUDIO_EFFECTS"
        const val EXTRA_ENABLED = "enabled"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val audioEffectsController = AudioEffectsController()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Prefer Media3 AV1 dav1d software extension renderer when hardware AV1 decoding isn't present
        val av1SwOnly = runCatching { DeviceCapabilityInspector.isAv1SoftwareOnly(this) }.getOrDefault(false)
        val extensionMode = if (av1SwOnly) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }
        val renderersFactory = NextRenderersFactory(this)
            .setExtensionRendererMode(extensionMode)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player

        // Bind DSP Equalizer, BassBoost, and Virtualizer directly to ExoPlayer's audio session ID
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            audioEffectsController.attachToSession(player.audioSessionId)
        }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                    audioEffectsController.attachToSession(audioSessionId)
                }
            }
        })

        // Restore persisted equalizer state immediately on service start
        serviceScope.launch {
            runCatching {
                val saved = PreferencesManager(applicationContext).settings.first()
                audioEffectsController.applyState(saved.toEqualizerState())
            }
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CMD_SET_SKIP_SILENCE, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SET_NOISY_PAUSE, Bundle.EMPTY))
                    .add(SessionCommand(CMD_APPLY_AUDIO_EFFECTS, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                val enabled = args.getBoolean(EXTRA_ENABLED, false)
                when (customCommand.customAction) {
                    CMD_SET_SKIP_SILENCE -> exoPlayer?.skipSilenceEnabled = enabled
                    CMD_SET_NOISY_PAUSE -> exoPlayer?.setHandleAudioBecomingNoisy(enabled)
                    CMD_APPLY_AUDIO_EFFECTS -> {
                        val eqState = EqualizerState.fromBundle(args)
                        exoPlayer?.audioSessionId?.let { sid ->
                            if (sid != C.AUDIO_SESSION_ID_UNSET && sid > 0) {
                                audioEffectsController.attachToSession(sid)
                            }
                        }
                        audioEffectsController.applyState(eqState)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        audioEffectsController.release()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
