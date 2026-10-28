package fr.paradoxxx.tester4k

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Lit une vidéo en streaming pendant [playMs] de lecture effective et relève :
 * décodeur utilisé, images par seconde réellement affichées (après 2 s de chauffe),
 * images perdues, coupures, erreurs.
 */
@OptIn(UnstableApi::class)
class PlaybackTester(private val ctx: Context, private val view: PlayerView) {

    data class Outcome(
        val firstFrame: Boolean,
        val playedMs: Long,
        val rendered: Int,        // images affichées sur la période mesurée
        val dropped: Int,         // images perdues sur la période mesurée
        val measuredMs: Long,     // durée de la période mesurée (hors chauffe)
        val fps: Double,          // images/s réellement affichées
        val contentFps: Double,   // images/s de la vidéo
        val rebuffers: Int,
        val decoderName: String?,
        val errorCode: Int?,
        val errorName: String?,
        val videoUnsupported: Boolean,
        val timedOut: Boolean
    )

    suspend fun run(
        url: String,
        playMs: Long = 10_000,
        warmupMs: Long = 2_000,
        startTimeoutMs: Long = 15_000,
        maxTotalMs: Long = 30_000,
        onTick: (playedMs: Long) -> Unit = {}
    ): Outcome {
        val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        // Tampon réduit : on ne lit que 10 s, inutile de garder jusqu'à ~130 Mo en mémoire
        // (important sur les boîtiers à 1 Go de RAM)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(4_000, 12_000, 1_000, 2_000)
            .setTargetBufferBytes(24 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val player = ExoPlayer.Builder(ctx, renderers).setLoadControl(loadControl).build()
        view.player = player
        player.volume = 0f

        var decoder: String? = null
        var firstFrame = false
        var error: PlaybackException? = null
        var unsupported = false
        var rebuffers = 0

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) { decoder = decoderName }

            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) { firstFrame = true }
        })

        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = e }

            override fun onTracksChanged(tracks: Tracks) {
                if (tracks.containsType(C.TRACK_TYPE_VIDEO) &&
                    !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities = */ true)
                ) unsupported = true
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING && firstFrame) rebuffers++
            }
        })

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true

        val t0 = SystemClock.elapsedRealtime()
        var last = t0
        var played = 0L
        var timedOut = false

        // Instantané des compteurs à la fin de la chauffe
        var warm = false
        var r0 = 0
        var d0 = 0
        var playedAtWarm = 0L

        try {
            while (true) {
                delay(200)
                val now = SystemClock.elapsedRealtime()
                if (player.isPlaying && firstFrame) played += now - last
                last = now
                onTick(played)

                if (!warm && played >= warmupMs) {
                    val c = player.videoDecoderCounters
                    c?.ensureUpdated()
                    r0 = c?.renderedOutputBufferCount ?: 0
                    d0 = c?.droppedBufferCount ?: 0
                    playedAtWarm = played
                    warm = true
                }

                if (error != null || unsupported) break
                if (player.playbackState == Player.STATE_ENDED) break
                if (played >= playMs) break
                if (!firstFrame && now - t0 > startTimeoutMs) { timedOut = true; break }
                if (now - t0 > maxTotalMs) { timedOut = true; break }
            }

            val c = player.videoDecoderCounters
            c?.ensureUpdated()
            val rTot = c?.renderedOutputBufferCount ?: 0
            val dTot = c?.droppedBufferCount ?: 0

            val rendered: Int
            val dropped: Int
            val measured: Long
            if (warm && played - playedAtWarm >= 1000) {
                rendered = rTot - r0
                dropped = dTot - d0
                measured = played - playedAtWarm
            } else {
                rendered = rTot
                dropped = dTot
                measured = played
            }
            val sec = measured / 1000.0
            val fps = if (sec > 0) rendered / sec else 0.0
            val fmtFps = player.videoFormat?.frameRate ?: Format.NO_VALUE.toFloat()
            val contentFps = when {
                fmtFps > 0 -> fmtFps.toDouble()
                sec > 0 -> (rendered + dropped) / sec
                else -> 0.0
            }

            return Outcome(
                firstFrame = firstFrame,
                playedMs = played,
                rendered = rendered,
                dropped = dropped,
                measuredMs = measured,
                fps = fps,
                contentFps = contentFps,
                rebuffers = rebuffers,
                decoderName = decoder,
                errorCode = error?.errorCode,
                errorName = error?.errorCodeName,
                videoUnsupported = unsupported,
                timedOut = timedOut
            )
        } finally {
            player.stop()
            view.player = null
            player.release()
        }
    }
}
