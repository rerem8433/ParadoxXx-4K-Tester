package fr.paradoxxx.tester4k

import androidx.annotation.StringRes
import androidx.media3.common.PlaybackException
import org.json.JSONObject
import java.util.Locale

enum class Status(val icon: String, @StringRes val labelRes: Int, val rank: Int) {
    OK("✅", R.string.st_ok, 7),
    MINOR("⚠️", R.string.st_minor, 6),
    FALLBACK("➖", R.string.st_fallback, 5),
    NETWORK("📶", R.string.st_network, 4),
    STUTTER("❌", R.string.st_stutter, 3),
    UNSUPPORTED("❌", R.string.st_unsupported, 2),
    MEMORY("❌", R.string.st_memory, 2),
    UNAVAILABLE("❔", R.string.st_unavailable, 1);

    val label: String get() = L.s(labelRes)
}

data class TestResult(
    val test: VideoTest,
    val status: Status,
    val detail: String,
    val logLines: String          // détail brut pour le rapport
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", test.id).put("status", status.name).put("detail", detail).put("log", logLines)

    companion object {
        fun fromJson(o: JSONObject): TestResult? {
            val t = TestCatalog.tests.firstOrNull { it.id == o.optString("id") } ?: return null
            val st = try { Status.valueOf(o.optString("status")) } catch (_: Exception) { return null }
            return TestResult(t, st, o.optString("detail"), o.optString("log"))
        }
    }
}

data class SummaryLine(val icon: String, val title: String, val text: String)

object Verdict {

    /** En dessous : même un film en 24 i/s saccade. */
    private const val MIN_FPS = 23.5

    fun fmt(v: Double) = String.format(Locale.FRANCE, "%.0f", v)

    // ---------- Verdict d'un test ----------

    fun evaluate(t: VideoTest, probe: CodecProbe.Result, o: PlaybackTester.Outcome?, netMbps: Double): TestResult {
        if (o == null) {
            val why = if (!probe.anyDecoder) L.s(R.string.why_no_decoder)
            else L.s(R.string.why_size, t.width, t.height)
            return TestResult(t, Status.UNSUPPORTED, why, testLog(probe, null))
        }

        val netTooSlow = netMbps > 0 && netMbps < t.bitrateMbps * 1.15
        val target = if (o.contentFps > 0) o.contentFps else 60.0
        val hw = when {
            o.decoderName == null -> ""
            CodecProbe.isHardwareName(o.decoderName) -> L.s(R.string.dec_hw)
            else -> L.s(R.string.dec_sw)
        }
        val detail = buildString {
            append(L.s(R.string.d_decoder, o.decoderName ?: L.s(R.string.d_none), hw))
            if (o.measuredMs > 0) append(L.s(R.string.d_fps, fmt(o.fps), fmt(target)))
            if (o.rendered + o.dropped > 0) append(L.s(R.string.d_dropped, o.dropped, o.rendered + o.dropped))
            if (o.rebuffers > 0) append(L.s(R.string.d_rebuf, o.rebuffers))
            if (o.errorName != null) append(L.s(R.string.d_error, o.errorName))
            if (o.timedOut && !o.firstFrame) append(L.s(R.string.d_noframe))
            if (!probe.sizeSupported && probe.anyDecoder) append(L.s(R.string.d_nosize))
        }

        var status = when {
            o.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                o.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> Status.UNAVAILABLE
            o.errorCode != null && o.errorCode in 2000..2999 -> Status.NETWORK
            o.errorCode != null -> Status.UNSUPPORTED           // 3xxx lecture du fichier, 4xxx décodage
            o.videoUnsupported -> Status.UNSUPPORTED
            !o.firstFrame -> when {
                netTooSlow -> Status.NETWORK
                o.decoderName == null -> Status.UNSUPPORTED
                else -> Status.STUTTER
            }
            // Le réseau n'est en cause que s'il y a eu des coupures (mise en mémoire tampon)
            o.rebuffers > 0 && netTooSlow -> Status.NETWORK
            o.fps >= target * 0.92 && o.rebuffers == 0 -> Status.OK
            o.fps >= MIN_FPS && o.rebuffers <= 1 -> Status.MINOR
            else -> Status.STUTTER
        }

        // Dolby Vision : sans décodeur DV, le lecteur se rabat sur la couche HEVC/HDR10
        var finalDetail = detail
        if (t.dolbyVision && status != Status.UNAVAILABLE && status != Status.NETWORK) {
            val n = o.decoderName?.lowercase() ?: ""
            val realDv = n.contains("dolby") || n.contains("dvhe") || n.contains(".dv")
            if (!realDv) {
                status = if (o.firstFrame && o.fps >= MIN_FPS) Status.FALLBACK else Status.UNSUPPORTED
                finalDetail = L.s(R.string.d_dv, detail)
            }
        }
        return TestResult(t, status, finalDetail, testLog(probe, o))
    }

    /** Test pendant lequel Android a tué l'appli (mémoire saturée). */
    fun memoryKill(t: VideoTest) = TestResult(t, Status.MEMORY,
        L.s(R.string.mem_kill),
        L.s(R.string.tl_killed))

    /** Test 4K non lancé : un test 4K précédent a déjà saturé la mémoire. */
    fun memorySkip(t: VideoTest) = TestResult(t, Status.MEMORY,
        L.s(R.string.mem_skip),
        L.s(R.string.tl_skipped))

    private fun testLog(p: CodecProbe.Result, o: PlaybackTester.Outcome?): String = buildString {
        append(L.s(R.string.tl_probe, p.decoderName ?: "-", p.anyDecoder, p.sizeSupported,
            p.hardware, p.profileDeclared))
        if (o == null) {
            append(L.s(R.string.tl_notrun))
        } else {
            append(L.s(R.string.tl_play, o.firstFrame, o.playedMs, o.measuredMs,
                "%.1f".format(Locale.US, o.fps), "%.1f".format(Locale.US, o.contentFps),
                o.rendered, o.dropped, o.rebuffers))
            append(L.s(R.string.tl_dec, o.decoderName ?: "-",
                o.errorName?.let { L.s(R.string.tl_err, it, o.errorCode ?: 0) } ?: "",
                if (o.videoUnsupported) L.s(R.string.tl_track) else "",
                if (o.timedOut) L.s(R.string.tl_timeout) else ""))
        }
    }

    // ---------- Résumé grand public ----------

    fun summary(
        results: List<TestResult>,
        net: NetworkProbe.Result,
        conn: NetworkProbe.Connection,
        hdrDisplay: Boolean?,
        hdmi: HdmiInfo.Mode? = null,
        ramMb: Long? = null
    ): List<SummaryLine> {
        val lines = mutableListOf<SummaryLine>()

        // Avertissement en tête : sortie HDMI en 24/25/30 Hz
        if (hdmi != null && hdmi.hz != null && hdmi.hz < 50) {
            lines += SummaryLine("⚠️", L.s(R.string.hdmi_warn_title, HdmiInfo.label(hdmi)),
                L.s(R.string.hdmi_warn_text))
        }

        val killed = results.filter { it.status == Status.MEMORY }
        if (killed.isNotEmpty()) {
            val ram = ramMb?.let { L.s(R.string.mem_ram, it) } ?: ""
            lines += SummaryLine("⚠️", L.s(R.string.mem_title, ram),
                L.s(R.string.mem_text, killed.joinToString(", ") { it.test.label }))
        }

        lines += groupLine(results, Group.FHD, Group.FHD.title)
        lines += groupLine(results, Group.UHD, Group.UHD.title)

        val hdr = groupLine(results, Group.HDR10, Group.HDR10.title)
        val hdrUhdKo = results.any { it.test.group == Group.HDR10 && it.test.is4kOrMore && it.status == Status.STUTTER }
        val sdrUhdOk = results.any { it.test.group == Group.UHD && it.test.tenBit && it.status.rank >= Status.MINOR.rank }
        val hdrNote = when {
            hdrUhdKo && sdrUhdOk && hdrDisplay != true -> L.s(R.string.hdr_note_heavy)
            hdrDisplay == false && (hdr.icon == "✅" || hdr.icon == "⚠️") -> L.s(R.string.hdr_note_convert)
            else -> ""
        }
        lines += hdr.copy(text = hdr.text + hdrNote)

        lines += groupLine(results, Group.DOLBY, Group.DOLBY.title)
        lines += groupLine(results, Group.K8, Group.K8.title)
        lines += av1(results)
        lines += connectionLine(net, conn)
        return lines
    }

    private fun groupLine(results: List<TestResult>, g: Group, title: String): SummaryLine {
        val rs = results.filter { it.test.group == g }
        if (rs.isEmpty()) return SummaryLine("❔", title, L.s(R.string.not_tested))
        val best = rs.maxBy { it.status.rank }
        val okFormats = rs.filter { it.status == Status.OK || it.status == Status.MINOR }
            .map { it.test.formatLabel }.distinct()
        val formats = if (okFormats.isNotEmpty()) L.s(R.string.formats, okFormats.joinToString(", ")) else ""

        val text = when (best.status) {
            Status.OK -> L.s(R.string.s_ok, formats)
            Status.MINOR -> L.s(R.string.s_minor, formats)
            Status.FALLBACK -> L.s(R.string.s_fallback)
            Status.NETWORK -> L.s(R.string.s_network)
            Status.STUTTER -> L.s(R.string.s_stutter)
            Status.MEMORY -> L.s(R.string.s_memory)
            Status.UNSUPPORTED -> if (g == Group.DOLBY) L.s(R.string.s_no_dv) else L.s(R.string.s_unsupported)
            Status.UNAVAILABLE -> L.s(R.string.s_unavailable)
        }
        val partial = rs.any { it.status.rank < Status.MINOR.rank } && best.status.rank >= Status.MINOR.rank
        val extra = when {
            partial && g == Group.UHD -> L.s(R.string.s_partial_uhd)
            partial && g == Group.HDR10 &&
                rs.none { it.test.is4kOrMore && it.status.rank >= Status.MINOR.rank } -> L.s(R.string.s_partial_hdr)
            else -> ""
        }
        // Un échec mêlé à un plantage mémoire : la vraie cause est la mémoire
        if (best.status.rank < Status.MINOR.rank && rs.any { it.status == Status.MEMORY }) {
            return SummaryLine(Status.MEMORY.icon, title, L.s(R.string.s_memory_format))
        }
        return SummaryLine(best.status.icon, title, text + extra)
    }

    private fun av1(results: List<TestResult>): SummaryLine {
        val title = Group.AV1.title
        val rs = results.filter { it.test.group == Group.AV1 }
        val uhd = rs.firstOrNull { it.test.is4kOrMore }
        val fhd = rs.firstOrNull { !it.test.is4kOrMore }
        fun ok(r: TestResult?) = r != null && r.status.rank >= Status.MINOR.rank
        return when {
            ok(uhd) -> SummaryLine(uhd!!.status.icon, title, L.s(R.string.av1_4k))
            ok(fhd) -> SummaryLine("⚠️", title, L.s(R.string.av1_fhd))
            else -> groupLine(results, Group.AV1, title)
        }
    }

    private fun connectionLine(net: NetworkProbe.Result, c: NetworkProbe.Connection): SummaryLine {
        val type = c.type + (c.bandLabel?.let { " $it" } ?: "") +
                (c.ethernetSpeed?.let { L.s(R.string.conn_port, it) } ?: "")
        val title = L.s(R.string.conn_title, type)
        if (!net.ok) return SummaryLine("❌", title, L.s(R.string.conn_none, net.error ?: L.s(R.string.conn_error)))

        val m = net.mbps
        val (icon, verdict) = when {
            m >= 60 -> "✅" to L.s(R.string.conn_exc)
            m >= 25 -> "✅" to L.s(R.string.conn_good)
            m >= 10 -> "⚠️" to L.s(R.string.conn_ok)
            else -> "❌" to L.s(R.string.conn_low)
        }
        val tip = when {
            m < 25 && c.isWifi && c.wifiBand == "2,4" -> L.s(R.string.tip_24)
            m < 25 && c.isWifi -> L.s(R.string.tip_wifi)
            else -> ""
        }
        return SummaryLine(icon, title, L.s(R.string.conn_line, fmt(m), verdict, tip))
    }
}
