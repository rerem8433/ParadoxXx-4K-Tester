package fr.paradoxxx.tester4k

import java.io.File

/**
 * Mode HDMI réellement envoyé à la TV (lu dans sysfs, au mieux).
 * L'API Android Display renvoie souvent la taille de l'interface (ex. 1280x720 @ 60 Hz),
 * pas le mode HDMI réel (ex. 3840x2160p30).
 */
object HdmiInfo {

    data class Mode(val raw: String, val width: Int, val height: Int, val hz: Int?)

    private val CANDIDATES = listOf(
        "/sys/class/drm/card0-HDMI-A-1/mode",
        "/sys/class/drm/card1-HDMI-A-1/mode",
        "/sys/class/display/HDMI/mode",
        "/sys/class/display/mode"            // Amlogic : ex. « 2160p30hz »
    )

    fun read(): Mode? {
        for (p in CANDIDATES) {
            val raw = try { File(p).readText().trim() } catch (_: Exception) { null }
            if (raw.isNullOrEmpty()) continue
            parse(raw)?.let { return it }
        }
        return null
    }

    private fun parse(raw: String): Mode? {
        // « 3840x2160p30 », « 1920x1080p60.00 », « 1920x1080i50 »
        Regex("""(\d{3,4})x(\d{3,4})[pi]?(\d{2,3})?""").find(raw)?.let { m ->
            return Mode(raw, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())
        }
        // Amlogic : « 2160p30hz », « 1080p60hz »
        Regex("""(\d{3,4})[pi](\d{2,3})""").find(raw)?.let { m ->
            val h = m.groupValues[1].toInt()
            val w = when (h) { 2160 -> 3840; 1080 -> 1920; 720 -> 1280; 480 -> 720; else -> 0 }
            return Mode(raw, w, h, m.groupValues[2].toIntOrNull())
        }
        return null
    }

    fun label(m: Mode): String = "${m.width}x${m.height}" + (m.hz?.let { " à $it Hz" } ?: "")
}
