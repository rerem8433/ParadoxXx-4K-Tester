package fr.paradoxxx.tester4k

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rapport texte complet, à copier-coller pour le support. */
object LogBuilder {

    fun build(
        ctx: Context,
        results: List<TestResult>,
        summary: List<SummaryLine>,
        net: NetworkProbe.Result,
        conn: NetworkProbe.Connection
    ): String = buildString {
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Exception) { "?" }

        appendLine("=== ParadoxXx 4K Tester $version ===")
        appendLine("Date : " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date()))
        appendLine()

        appendLine("--- Boîtier ---")
        appendLine("Modèle    : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Hardware  : ${Build.HARDWARE} · board ${Build.BOARD}" +
                if (Build.VERSION.SDK_INT >= 31) " · SoC ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "")
        appendLine("CPU ABI   : ${Build.SUPPORTED_ABIS.joinToString()} · ${Runtime.getRuntime().availableProcessors()} cœurs")
        appendLine("RAM       : ${ramMb(ctx)} Mo")
        appendLine("Interface : ${display(ctx)} (résolution de l'interface Android, pas forcément celle envoyée à la TV)")
        appendLine("HDMI      : " + (HdmiInfo.read()?.let { "${HdmiInfo.label(it)} (sortie réelle vers la TV)" } ?: "mode non lisible"))
        appendLine("Firmware  : ${Build.DISPLAY}")
        appendLine()

        appendLine("--- Connexion ---")
        appendLine("Type      : ${conn.type}" +
                (conn.wifiBand?.let { " $it" } ?: "") +
                (conn.ethernetSpeed?.let { " · port $it Mb/s" } ?: ""))
        appendLine("Débit     : " + if (net.ok) "${Verdict.fmt(net.mbps)} Mb/s" else "échec (${net.error})")
        appendLine()

        appendLine("--- Résumé ---")
        summary.forEach { appendLine("${it.icon} ${it.title} : ${it.text}") }
        appendLine()

        appendLine("--- Détail des tests ---")
        results.forEachIndexed { i, r ->
            appendLine("${i + 1}. ${r.status.icon} ${r.test.label} [${r.test.mime}, ${r.test.width}x${r.test.height}, ${r.test.bitrateMbps} Mb/s]")
            appendLine("   Verdict : ${r.status.label}")
            appendLine(r.logLines)
        }
        appendLine()

        appendLine("--- Décodeurs vidéo déclarés ---")
        decoders().forEach { appendLine(it) }
    }

    fun ramMb(ctx: Context): Long = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem / (1024 * 1024)
    } catch (_: Exception) { -1 }

    private fun display(ctx: Context): String = try {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = if (Build.VERSION.SDK_INT >= 23) {
            val m = d.mode
            "${m.physicalWidth}x${m.physicalHeight} @ ${"%.0f".format(Locale.US, m.refreshRate)} Hz"
        } else "?"
        val hdr = if (Build.VERSION.SDK_INT >= 24) {
            val types = d.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
            if (types.isEmpty()) "sans HDR" else "HDR " + types.joinToString(",") {
                when (it) {
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "DV"
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                    4 -> "HDR10+"
                    else -> it.toString()
                }
            }
        } else "HDR ?"
        "$mode · $hdr"
    } catch (_: Exception) { "?" }

    private fun decoders(): List<String> = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.startsWith("video/") } }
            .flatMap { info ->
                info.supportedTypes.filter { it.startsWith("video/") }.map { type ->
                    val hw = if (CodecProbe.isHardwareName(info.name)) "HW" else "SW"
                    val size = try {
                        val vc = info.getCapabilitiesForType(type).videoCapabilities
                        "max ${vc.supportedWidths.upper}x${vc.supportedHeights.upper}"
                    } catch (_: Exception) { "" }
                    "$hw  ${info.name}  $type  $size"
                }
            }
    } catch (e: Exception) { listOf("illisible : ${e.javaClass.simpleName}") }
}
