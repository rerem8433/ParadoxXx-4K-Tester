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
        appendLine(L.s(R.string.lg_date, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date())))
        appendLine()

        appendLine(L.s(R.string.lg_box))
        appendLine(L.s(R.string.lg_model, "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"))
        appendLine(L.s(R.string.lg_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
        val soc = if (Build.VERSION.SDK_INT >= 31) L.s(R.string.lg_soc, Build.SOC_MANUFACTURER, Build.SOC_MODEL) else ""
        appendLine(L.s(R.string.lg_hw, Build.HARDWARE, Build.BOARD, soc))
        appendLine(L.s(R.string.lg_cpu, Build.SUPPORTED_ABIS.joinToString(), Runtime.getRuntime().availableProcessors()))
        appendLine(L.s(R.string.lg_ram, ramMb(ctx)))
        appendLine(L.s(R.string.lg_iface, display(ctx)))
        appendLine(HdmiInfo.read()?.let { L.s(R.string.lg_hdmi, HdmiInfo.label(it)) } ?: L.s(R.string.lg_hdmi_none))
        appendLine(L.s(R.string.lg_fw, Build.DISPLAY))
        appendLine()

        appendLine(L.s(R.string.lg_conn))
        appendLine(L.s(R.string.lg_type, conn.type +
                (conn.bandLabel?.let { " $it" } ?: "") +
                (conn.ethernetSpeed?.let { L.s(R.string.lg_port, it) } ?: "")))
        appendLine(if (net.ok) L.s(R.string.lg_speed, Verdict.fmt(net.mbps))
                   else L.s(R.string.lg_speed_fail, net.error ?: "?"))
        appendLine()

        appendLine(L.s(R.string.lg_summary))
        summary.forEach { appendLine("${it.icon} ${it.title} : ${it.text}") }
        appendLine()

        appendLine(L.s(R.string.lg_details))
        results.forEachIndexed { i, r ->
            appendLine(L.s(R.string.lg_test, i + 1, r.status.icon, r.test.label, r.test.mime,
                r.test.width, r.test.height, r.test.bitrateMbps))
            appendLine(L.s(R.string.lg_verdict, r.status.label))
            appendLine(r.logLines)
        }
        appendLine()

        appendLine(L.s(R.string.lg_decoders))
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
            if (types.isEmpty()) L.s(R.string.lg_no_hdr) else "HDR " + types.joinToString(",") {
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
    } catch (e: Exception) { listOf(L.s(R.string.lg_unreadable, e.javaClass.simpleName)) }
}
