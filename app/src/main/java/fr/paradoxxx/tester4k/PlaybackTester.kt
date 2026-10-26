package fr.paradoxxx.tester4k

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Lit une vidéo en streaming pendant [playMs] de lecture effective
 * et relève ce qui s'est passé : décodeur utilisé, images perdues, coupures, erreurs.
 */
@OptIn(UnstableApi::class)
class PlaybackTester(private val ctx: Context, private val view: PlayerView) {

    data class Outcome(
        val firstFrame: Boolean,
        val playedMs: Long,
        val rendered: Int,
        val dropped: Int,
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
        startTimeoutMs: Long = 15_000,
        maxTotalMs: Long = 30_000,
        onTick: (playedMs: Long) -> Unit = {}
    ): Outcome {
        val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(ctx, renderers).build()
        view.player = player
        player.volume = 0f

        var decoder: String? = null
        var droppedEvents = 0
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

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) { droppedEvents += droppedFrames }

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

        try {
            while (true) {
                delay(200)
                val now = SystemClock.elapsedRealtime()
                if (player.isPlaying && firstFrame) played += now - last
                last = now
                onTick(played)

                if (error != null || unsupported) break
                if (player.playbackState == Player.STATE_ENDED) break
                if (played >= playMs) break
                if (!firstFrame && now - t0 > startTimeoutMs) { timedOut = true; break }
                if (now - t0 > maxTotalMs) { timedOut = true; break }
            }

            val counters = player.videoDecoderCounters
            counters?.ensureUpdated()
            val rendered = counters?.renderedOutputBufferCount ?: 0
            val dropped = maxOf(droppedEvents, counters?.droppedBufferCount ?: 0)

            return Outcome(
                firstFrame = firstFrame,
                playedMs = played,
                rendered = rendered,
                dropped = dropped,
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
