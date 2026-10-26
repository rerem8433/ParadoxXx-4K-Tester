package fr.paradoxxx.tester4k

import androidx.media3.common.PlaybackException
import java.util.Locale

enum class Status(val icon: String, val label: String, val rank: Int) {
    OK("✅", "Lecture fluide", 7),
    MINOR("⚠️", "Petites saccades", 6),
    FALLBACK("➖", "Lu en HDR10 (pas de Dolby Vision)", 5),
    NETWORK("📶", "Connexion trop lente", 4),
    STUTTER("❌", "Trop saccadé", 3),
    UNSUPPORTED("❌", "Format non pris en charge", 2),
    UNAVAILABLE("❔", "Vidéo de test indisponible", 1)
}

data class TestResult(val test: VideoTest, val status: Status, val detail: String)

data class SummaryLine(val icon: String, val title: String, val text: String)

object Verdict {

    // ---------- Verdict d'un test ----------

    fun evaluate(t: VideoTest, probe: CodecProbe.Result, o: PlaybackTester.Outcome?, netMbps: Double): TestResult {
        if (o == null) {
            return TestResult(t, Status.UNSUPPORTED,
                if (!probe.anyDecoder) "Aucun décodeur pour ce format"
                else "Résolution ${t.width}×${t.height} refusée par le décodeur")
        }

        val netTooSlow = netMbps > 0 && netMbps < t.bitrateMbps * 1.15
        val total = o.rendered + o.dropped
        val ratio = if (total > 0) o.dropped.toDouble() / total else 0.0
        val hw = if (o.decoderName == null) "" else
            if (CodecProbe.isHardwareName(o.decoderName)) " (matériel)" else " (logiciel)"
        val detail = buildString {
            append("Décodeur : ${o.decoderName ?: "aucun"}$hw")
            if (total > 0) append(" · images perdues : ${o.dropped}/$total")
            if (o.rebuffers > 0) append(" · coupures : ${o.rebuffers}")
            if (o.errorName != null) append(" · erreur : ${o.errorName}")
            if (o.timedOut && !o.firstFrame) append(" · aucune image en 15 s")
        }

        var status = when {
            o.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                o.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> Status.UNAVAILABLE
            o.errorCode != null && o.errorCode in 2000..2999 -> Status.NETWORK
            o.errorCode != null -> Status.UNSUPPORTED           // 3xxx parsing, 4xxx décodage
            o.videoUnsupported -> Status.UNSUPPORTED
            !o.firstFrame -> when {
                o.decoderName == null && !netTooSlow -> Status.UNSUPPORTED
                netTooSlow -> Status.NETWORK
                else -> Status.STUTTER
            }
            ratio < 0.02 && o.rebuffers == 0 -> Status.OK
            o.rebuffers > 0 && netTooSlow -> Status.NETWORK
            ratio < 0.08 && o.rebuffers <= 1 -> Status.MINOR
            netTooSlow -> Status.NETWORK
            else -> Status.STUTTER
        }

        // Dolby Vision lu via la couche HDR10 de compatibilité
        if (t.dolbyVision && (status == Status.OK || status == Status.MINOR)) {
            val n = o.decoderName?.lowercase() ?: ""
            if (!(n.contains("dolby") || n.contains("dvhe") || n.contains(".dv"))) status = Status.FALLBACK
        }
        return TestResult(t, status, detail)
    }

    // ---------- Résumé grand public ----------

    fun summary(
        results: List<TestResult>,
        net: NetworkProbe.Result,
        conn: NetworkProbe.Connection,
        hdrDisplay: Boolean?
    ): List<SummaryLine> {
        val lines = mutableListOf<SummaryLine>()

        lines += group(results, Group.FHD, "Full HD (1080p)")
        lines += group(results, Group.UHD, "4K")

        val hdr = groupLine(results, Group.HDR10, "HDR10")
        val hdrNote = if (hdrDisplay == false && (hdr.icon == "✅" || hdr.icon == "⚠️"))
            " · Votre écran ne signale pas le HDR : l'image sera convertie." else ""
        lines += hdr.copy(text = hdr.text + hdrNote)

        lines += group(results, Group.DOLBY, "Dolby Vision")
        lines += group(results, Group.K8, "8K")
        lines += av1(results)
        lines += connectionLine(net, conn)
        return lines
    }

    private fun group(results: List<TestResult>, g: Group, title: String) = groupLine(results, g, title)

    private fun groupLine(results: List<TestResult>, g: Group, title: String): SummaryLine {
        val rs = results.filter { it.test.group == g }
        if (rs.isEmpty()) return SummaryLine("❔", title, "Non testé")
        val best = rs.maxBy { it.status.rank }
        val okFormats = rs.filter { it.status == Status.OK || it.status == Status.MINOR }
            .map { it.test.formatLabel }.distinct()
        val formats = if (okFormats.isNotEmpty()) " — formats : " + okFormats.joinToString(", ") else ""

        val text = when (best.status) {
            Status.OK -> "Oui, lecture fluide$formats"
            Status.MINOR -> "Oui, avec de légères saccades$formats"
            Status.FALLBACK -> "Non, mais la vidéo est lue en HDR10 à la place"
            Status.NETWORK -> "Impossible à vérifier : la connexion est trop lente pour ce format"
            Status.STUTTER -> "Non : le boîtier n'est pas assez puissant (image saccadée)"
            Status.UNSUPPORTED -> "Non : format non pris en charge par ce boîtier"
            Status.UNAVAILABLE -> "Test impossible (vidéo de test indisponible)"
        }
        // 4K / HDR : préciser si seule une partie passe
        val partial = rs.any { it.status.rank < Status.MINOR.rank } && best.status.rank >= Status.MINOR.rank
        val extra = if (partial && g == Group.UHD) " (pas tous les formats)" else
            if (partial && g == Group.HDR10) {
                val uhdOk = rs.any { it.test.is4kOrMore && it.status.rank >= Status.MINOR.rank }
                if (uhdOk) "" else " (seulement en Full HD)"
            } else ""
        return SummaryLine(best.status.icon, title, text + extra)
    }

    private fun av1(results: List<TestResult>): SummaryLine {
        val title = "AV1 (nouveau format YouTube / Netflix)"
        val rs = results.filter { it.test.group == Group.AV1 }
        val uhd = rs.firstOrNull { it.test.is4kOrMore }
        val fhd = rs.firstOrNull { !it.test.is4kOrMore }
        fun ok(r: TestResult?) = r != null && r.status.rank >= Status.MINOR.rank
        return when {
            ok(uhd) -> SummaryLine(uhd!!.status.icon, title, "Oui, jusqu'en 4K")
            ok(fhd) -> SummaryLine("⚠️", title, "En Full HD seulement")
            else -> groupLine(results, Group.AV1, title)
        }
    }

    private fun connectionLine(net: NetworkProbe.Result, c: NetworkProbe.Connection): SummaryLine {
        val type = c.type + (c.wifiBand?.let { " $it" } ?: "") +
                (c.ethernetSpeed?.let { " (port $it Mb/s)" } ?: "")
        val title = "Connexion — $type"
        if (!net.ok) return SummaryLine("❌", title, "Pas de connexion Internet utilisable (${net.error ?: "erreur"})")

        val m = net.mbps
        val speed = String.format(Locale.FRANCE, "%.0f Mb/s", m)
        val (icon, verdict) = when {
            m >= 60 -> "✅" to "Excellente : la 4K haut débit passe sans problème"
            m >= 25 -> "✅" to "Bonne : la 4K en streaming passe"
            m >= 10 -> "⚠️" to "Correcte : Full HD OK, 4K risquée"
            else -> "❌" to "Faible : trop lente pour la 4K, Full HD limite"
        }
        val tip = when {
            m < 25 && c.isWifi && c.wifiBand == "2,4 GHz" ->
                " · Conseil : passez en Wi-Fi 5 GHz ou branchez un câble Ethernet."
            m < 25 && c.isWifi -> " · Conseil : rapprochez la box du routeur ou branchez un câble."
            else -> ""
        }
        return SummaryLine(icon, title, "$speed — $verdict$tip")
    }
}
