package fr.paradoxxx.tester4k

/** Groupes affichés dans le résumé « grand public ». */
enum class Group(val title: String) {
    FHD("Full HD (1080p)"),
    UHD("4K"),
    HDR10("HDR10"),
    DOLBY("Dolby Vision"),
    K8("8K"),
    AV1("AV1 (nouveau format YouTube / Netflix)")
}

data class VideoTest(
    val id: String,
    val group: Group,
    val label: String,        // affiché pendant le test et dans les détails
    val formatLabel: String,  // court : « H.264 », « HEVC 10 bits »…
    val mime: String,
    val width: Int,
    val height: Int,
    val bitrateMbps: Int,
    val tenBit: Boolean = false,
    val dolbyVision: Boolean = false,
    private val path: String
) {
    val url: String get() = TestCatalog.BASE + path
    val is4kOrMore: Boolean get() = width >= 3840
}

object TestCatalog {
    const val BASE = "https://repo.jellyfin.org/test-videos/"

    /** Fichier lu en partie pour mesurer le débit réel (282 Mo, jamais téléchargé en entier). */
    const val NETWORK_PROBE_URL =
        BASE + "SDR/HEVC%2010bit/Test%20Jellyfin%204K%20HEVC%2010bit%2080M.mp4"

    private const val AVC = "video/avc"
    private const val HEVC = "video/hevc"
    private const val AV1 = "video/av01"
    private const val DV = "video/dolby-vision"

    val tests = listOf(
        // ---- Full HD ----
        VideoTest("fhd_h264", Group.FHD, "Full HD · H.264", "H.264", AVC, 1920, 1080, 10,
            path = "SDR/AVC/Test%20Jellyfin%201080p%20AVC%2010M.mp4"),
        VideoTest("fhd_hevc", Group.FHD, "Full HD · HEVC", "HEVC", HEVC, 1920, 1080, 10,
            path = "SDR/HEVC%208bit/Test%20Jellyfin%201080p%20HEVC%208bit%2010M.mp4"),
        VideoTest("fhd_hevc10", Group.FHD, "Full HD · HEVC 10 bits", "HEVC 10 bits", HEVC, 1920, 1080, 10,
            tenBit = true,
            path = "SDR/HEVC%2010bit/Test%20Jellyfin%201080p%20HEVC%2010bit%2010M.mp4"),
        VideoTest("fhd_av1", Group.AV1, "Full HD · AV1", "AV1", AV1, 1920, 1080, 5,
            tenBit = true,
            path = "SDR/AV1/Test%20Jellyfin%201080p%20AV1%2010bit%205M.mp4"),

        // ---- 4K ----
        VideoTest("uhd_h264", Group.UHD, "4K · H.264", "H.264", AVC, 3840, 2160, 40,
            path = "SDR/AVC/Test%20Jellyfin%204K%20AVC%2040M.mp4"),
        VideoTest("uhd_hevc", Group.UHD, "4K · HEVC", "HEVC", HEVC, 3840, 2160, 40,
            path = "SDR/HEVC%208bit/Test%20Jellyfin%204K%20HEVC%208bit%2040M.mp4"),
        VideoTest("uhd_hevc10", Group.UHD, "4K · HEVC 10 bits", "HEVC 10 bits", HEVC, 3840, 2160, 40,
            tenBit = true,
            path = "SDR/HEVC%2010bit/Test%20Jellyfin%204K%20HEVC%2010bit%2040M.mp4"),
        VideoTest("uhd_hevc10_80", Group.UHD, "4K · HEVC 10 bits haut débit (80 Mb/s)", "HEVC 80 Mb/s", HEVC, 3840, 2160, 80,
            tenBit = true,
            path = "SDR/HEVC%2010bit/Test%20Jellyfin%204K%20HEVC%2010bit%2080M.mp4"),
        VideoTest("uhd_av1", Group.AV1, "4K · AV1", "AV1", AV1, 3840, 2160, 40,
            tenBit = true,
            path = "SDR/AV1/Test%20Jellyfin%204K%20AV1%2010bit%2040M.mp4"),

        // ---- HDR ----
        VideoTest("hdr_fhd", Group.HDR10, "HDR10 · Full HD", "HDR10 1080p", HEVC, 1920, 1080, 10,
            tenBit = true,
            path = "HDR/HDR10/HEVC/Test%20Jellyfin%201080p%20HEVC%20HDR10%2010M.mp4"),
        VideoTest("hdr_uhd", Group.HDR10, "HDR10 · 4K", "HDR10 4K", HEVC, 3840, 2160, 40,
            tenBit = true,
            path = "HDR/HDR10/HEVC/Test%20Jellyfin%204K%20HEVC%20HDR10%2040M.mp4"),
        VideoTest("dv_uhd", Group.DOLBY, "Dolby Vision · 4K (profil 8.1)", "Dolby Vision 4K", DV, 3840, 2160, 32,
            dolbyVision = true,
            path = "HDR/Dolby%20Vision/Test%20Jellyfin%204K%20DV%20P8.1.mp4"),

        // ---- 8K ----
        VideoTest("k8_hevc", Group.K8, "8K · HEVC", "HEVC 8K", HEVC, 7680, 4320, 150,
            path = "SDR/HEVC%208bit/Test%20Jellyfin%208K%20HEVC%208bit%20150M.mp4")
    )
}
