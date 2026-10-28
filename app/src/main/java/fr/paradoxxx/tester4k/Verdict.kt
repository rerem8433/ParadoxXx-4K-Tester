package fr.paradoxxx.tester4k

import androidx.media3.common.PlaybackException
import org.json.JSONObject
import java.util.Locale

enum class Status(val icon: String, val label: String, val rank: Int) {
    OK("✅", "Lecture fluide", 7),
    MINOR("⚠️", "OK pour films et séries", 6),
    FALLBACK("➖", "Lu en HDR10 (pas de Dolby Vision)", 5),
    NETWORK("📶", "Connexion trop lente", 4),
    STUTTER("❌", "Trop saccadé", 3),
    UNSUPPORTED("❌", "Format non pris en charge", 2),
    MEMORY("❌", "Mémoire insuffisante (appli fermée par le système)", 2),
    UNAVAILABLE("❔", "Vidéo de test indisponible", 1)
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
            val why = if (!probe.anyDecoder) "Aucun décodeur pour ce format sur ce boîtier"
            else "Le décodeur annonce ne pas gérer ${t.width}×${t.height}"
            return TestResult(t, Status.UNSUPPORTED, why, testLog(probe, null))
        }

        val netTooSlow = netMbps > 0 && netMbps < t.bitrateMbps * 1.15
        val target = if (o.contentFps > 0) o.contentFps else 60.0
        val hw = when {
            o.decoderName == null -> ""
            CodecProbe.isHardwareName(o.decoderName) -> " (matériel)"
            else -> " (logiciel)"
        }
        val detail = buildString {
            append("Décodeur : ${o.decoderName ?: "aucun"}$hw")
            if (o.measuredMs > 0) append(" · ${fmt(o.fps)} i/s affichées sur ${fmt(target)}")
            if (o.rendered + o.dropped > 0) append(" · images perdues : ${o.dropped}/${o.rendered + o.dropped}")
            if (o.rebuffers > 0) append(" · coupures : ${o.rebuffers}")
            if (o.errorName != null) append(" · erreur : ${o.errorName}")
            if (o.timedOut && !o.firstFrame) append(" · aucune image en 15 s")
            if (!probe.sizeSupported && probe.anyDecoder) append(" · résolution non déclarée par le décodeur")
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
                finalDetail = "Pas de décodeur Dolby Vision · lu en HEVC/HDR10 à la place · $detail"
            }
        }
        return TestResult(t, status, finalDetail, testLog(probe, o))
    }

    /** Test pendant lequel Android a tué l'appli (mémoire saturée). */
    fun memoryKill(t: VideoTest) = TestResult(t, Status.MEMORY,
        "Le système a fermé l'appli pendant ce test : la mémoire (RAM) du boîtier était saturée",
        "   Lecture : appli tuée par le système pendant le test (lowmemorykiller probable)")

    private fun testLog(p: CodecProbe.Result, o: PlaybackTester.Outcome?): String = buildString {
        append("   Sonde   : décodeur=${p.decoderName ?: "-"} présent=${p.anyDecoder} résolution=${p.sizeSupported}" +
                " matériel=${p.hardware} profil10bits=${p.profileDeclared}")
        if (o == null) {
            append("\n   Lecture : non lancée")
        } else {
            append("\n   Lecture : 1re image=${o.firstFrame} lu=${o.playedMs} ms mesuré=${o.measuredMs} ms" +
                    " i/s=${"%.1f".format(Locale.US, o.fps)}/${"%.1f".format(Locale.US, o.contentFps)}" +
                    " affichées=${o.rendered} perdues=${o.dropped} coupures=${o.rebuffers}")
            append("\n   Décodeur: ${o.decoderName ?: "-"}" +
                    (o.errorName?.let { " · erreur=$it (${o.errorCode})" } ?: "") +
                    (if (o.videoUnsupported) " · piste vidéo non supportée" else "") +
                    (if (o.timedOut) " · délai dépassé" else ""))
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
            lines += SummaryLine("⚠️", "Sortie HDMI en ${HdmiInfo.label(hdmi)}",
                "Les vidéos en 50/60 images par seconde seront saccadées et les résultats 4K faussés. " +
                "Réglez la résolution sur une valeur en 60 Hz (ex. 1920x1080p60) dans " +
                "Paramètres › Affichage › HDMI, puis refaites le test.")
        }

        val killed = results.filter { it.status == Status.MEMORY }
        if (killed.isNotEmpty()) {
            val ram = ramMb?.let { " (${it} Mo de RAM)" } ?: ""
            lines += SummaryLine("⚠️", "Mémoire insuffisante$ram",
                "Le boîtier a fermé l'appli pendant : " + killed.joinToString(", ") { it.test.label } +
                ". Ces formats risquent aussi de faire planter les autres lecteurs.")
        }

        lines += groupLine(results, Group.FHD, "Full HD (1080p)")
        lines += groupLine(results, Group.UHD, "4K")

        val hdr = groupLine(results, Group.HDR10, "HDR10")
        val hdrUhdKo = results.any { it.test.group == Group.HDR10 && it.test.is4kOrMore && it.status == Status.STUTTER }
        val sdrUhdOk = results.any { it.test.group == Group.UHD && it.test.tenBit && it.status.rank >= Status.MINOR.rank }
        val hdrNote = when {
            hdrUhdKo && sdrUhdOk && hdrDisplay != true ->
                " · En 4K, la conversion HDR → image normale (votre écran ne signale pas le HDR) est trop lourde pour ce boîtier."
            hdrDisplay == false && (hdr.icon == "✅" || hdr.icon == "⚠️") ->
                " · Votre écran ne signale pas le HDR : l'image sera convertie."
            else -> ""
        }
        lines += hdr.copy(text = hdr.text + hdrNote)

        lines += groupLine(results, Group.DOLBY, "Dolby Vision")
        lines += groupLine(results, Group.K8, "8K")
        lines += av1(results)
        lines += connectionLine(net, conn)
        return lines
    }

    private fun groupLine(results: List<TestResult>, g: Group, title: String): SummaryLine {
        val rs = results.filter { it.test.group == g }
        if (rs.isEmpty()) return SummaryLine("❔", title, "Non testé")
        val best = rs.maxBy { it.status.rank }
        val okFormats = rs.filter { it.status == Status.OK || it.status == Status.MINOR }
            .map { it.test.formatLabel }.distinct()
        val formats = if (okFormats.isNotEmpty()) " — formats : " + okFormats.joinToString(", ") else ""

        val text = when (best.status) {
            Status.OK -> "Oui, lecture parfaitement fluide$formats"
            Status.MINOR -> "Oui pour les films et séries, limite pour le sport et les vidéos en 60 i/s$formats"
            Status.FALLBACK -> "Non, mais la vidéo est lue en HDR10 à la place"
            Status.NETWORK -> "Impossible à vérifier : la connexion est trop lente pour ce format"
            Status.STUTTER -> "Non : le boîtier n'est pas assez puissant (image saccadée)"
            Status.MEMORY -> "Non : le boîtier manque de mémoire (l'appli a été fermée)"
            Status.UNSUPPORTED -> if (g == Group.DOLBY) "Non : pas de Dolby Vision sur ce boîtier"
                else "Non : format non pris en charge par ce boîtier"
            Status.UNAVAILABLE -> "Test impossible (vidéo de test indisponible)"
        }
        val partial = rs.any { it.status.rank < Status.MINOR.rank } && best.status.rank >= Status.MINOR.rank
        val extra = when {
            partial && g == Group.UHD -> " (pas tous les formats)"
            partial && g == Group.HDR10 &&
                rs.none { it.test.is4kOrMore && it.status.rank >= Status.MINOR.rank } -> " (seulement en Full HD)"
            else -> ""
        }
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
        return SummaryLine(icon, title, "${fmt(m)} Mb/s — $verdict$tip")
    }
}
