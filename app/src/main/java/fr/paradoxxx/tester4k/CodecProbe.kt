package fr.paradoxxx.tester4k

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * Interroge MediaCodecList : ce que le firmware DÉCLARE savoir décoder.
 * Sert à sauter instantanément les tests impossibles (8K, AV1 absent…).
 */
object CodecProbe {

    data class Result(
        val anyDecoder: Boolean,     // au moins un décodeur pour ce format
        val sizeSupported: Boolean,  // un décodeur accepte cette résolution
        val hardware: Boolean,
        val profileDeclared: Boolean,
        val decoderName: String?
    )

    // Valeurs numériques pour rester compatible avant Android 10
    private val TEN_BIT_PROFILES = setOf(2, 4096, 8192) // Main10, Main10 HDR10, Main10 HDR10+ (HEVC et AV1)

    fun probe(t: VideoTest): Result {
        val infos = try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        } catch (e: Exception) {
            // Liste illisible : on tente la lecture quand même
            return Result(true, true, false, true, null)
        }

        var any = false
        var best: Result? = null
        for (info in infos) {
            if (info.isEncoder) continue
            val type = info.supportedTypes.firstOrNull { it.equals(t.mime, ignoreCase = true) } ?: continue
            any = true
            val caps = try { info.getCapabilitiesForType(type) } catch (e: Exception) { continue }
            val vc = caps.videoCapabilities ?: continue
            val sizeOk = try {
                vc.isSizeSupported(t.width, t.height) || vc.isSizeSupported(t.height, t.width)
            } catch (e: Exception) { false }
            if (!sizeOk) continue

            val profOk = !t.tenBit || caps.profileLevels.any { it.profile in TEN_BIT_PROFILES }
            val r = Result(true, true, isHardware(info), profOk, info.name)
            if (best == null || score(r) > score(best)) best = r
        }
        return best ?: Result(any, false, false, false, null)
    }

    fun hasDecoderFor(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    } catch (e: Exception) { false }

    private fun score(r: Result) = (if (r.hardware) 2 else 0) + (if (r.profileDeclared) 1 else 0)

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else isHardwareName(info.name)

    /** Heuristique sur le nom : les décodeurs Google/AOSP sont logiciels. */
    fun isHardwareName(name: String?): Boolean {
        if (name == null) return false
        val n = name.lowercase()
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") ||
                n.startsWith("c2.google.") || n.contains("ffmpeg") || n.contains(".sw."))
    }
}
