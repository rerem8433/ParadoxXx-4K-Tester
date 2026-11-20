package fr.paradoxxx.tester4k

import androidx.annotation.StringRes

/** Groupes affichés dans le résumé « grand public ». */
enum class Group(@StringRes val titleRes: Int) {
    FHD(R.string.g_fhd),
    UHD(R.string.g_uhd),
    HDR10(R.string.g_hdr),
    DOLBY(R.string.g_dv),
    K8(R.string.g_8k),
    AV1(R.string.g_av1);

    val title: String get() = L.s(titleRes)
}

data class VideoTest(
    val id: String,
    val group: Group,
    @StringRes val labelRes: Int,   // affiché pendant le test et dans les détails
    @StringRes val formatRes: Int,  // court : « H.264 », « HEVC 10 bits »…
    val mime: String,
    val width: Int,
    val height: Int,
    val bitrateMbps: Int,
    val tenBit: Boolean = false,
    val dolbyVision: Boolean = false,
    private val path: String
) {
    val label: String get() = L.s(labelRes)
    val formatLabel: String get() = L.s(formatRes)
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
        VideoTest("fhd_h264", Group.FHD, R.string.t_fhd_h264, R.string.f_h264, AVC, 1920, 1080, 10,
            path = "SDR/AVC/Test%20Jellyfin%201080p%20AVC%2010M.mp4"),
        VideoTest("fhd_hevc", Group.FHD, R.string.t_fhd_hevc, R.string.f_hevc, HEVC, 1920, 1080, 10,
            path = "SDR/HEVC%208bit/Test%20Jellyfin%201080p%20HEVC%208bit%2010M.mp4"),
        VideoTest("fhd_hevc10", Group.FHD, R.string.t_fhd_hevc10, R.string.f_hevc10, HEVC, 1920, 1080, 10,
            tenBit = true,
            path = "SDR/HEVC%2010bit/Test%20Jellyfin%201080p%20HEVC%2010bit%2010M.mp4"),
        VideoTest("fhd_av1", Group.AV1, R.string.t_fhd_av1, R.string.f_av1, AV1, 1920, 1080, 5,
            tenBit = true,
            path = "SDR/AV1/Test%20Jellyfin%201080p%20AV1%2010bit%205M.mp4"),

        // ---- 4K ----
        VideoTest("uhd_h264", Group.UHD, R.string.t_uhd_h264, R.string.f_h264, AVC, 3840, 2160, 40,
            path = "SDR/AVC/Test%20Jellyfin%204K%20AVC%2040M.mp4"),
        VideoTest("uhd_hevc", Group.UHD, R.string.t_uhd_hevc, R.string.f_hevc, HEVC, 3840, 2160, 40,
            path = "SDR/HEVC%208bit/Test%20Jellyfin%204K%20HEVC%208bit%2040M.mp4"),
        VideoTest("uhd_hevc10", Group.UHD, R.string.t_uhd_hevc10, R.string.f_hevc10, HEVC, 3840, 2160, 40,
            tenBit = true,
            path = "SDR/HEVC%2010bit/Test%20Jellyfin%204K%20HEVC%2010bit%2040M.mp4"),
        VideoTest("uhd_hevc10_80", Group.UHD, R.string.t_uhd_hevc10_80, R.string.f_hevc80, HEVC, 3840, 2160, 80,
            tenBit = true,
            path = "SDR/HEVC%2010bit/Test%20Jellyfin%204K%20HEVC%2010bit%2080M.mp4"),
        VideoTest("uhd_av1", Group.AV1, R.string.t_uhd_av1, R.string.f_av1, AV1, 3840, 2160, 40,
            tenBit = true,
            path = "SDR/AV1/Test%20Jellyfin%204K%20AV1%2010bit%2040M.mp4"),

        // ---- HDR ----
        VideoTest("hdr_fhd", Group.HDR10, R.string.t_hdr_fhd, R.string.f_hdr_fhd, HEVC, 1920, 1080, 10,
            tenBit = true,
            path = "HDR/HDR10/HEVC/Test%20Jellyfin%201080p%20HEVC%20HDR10%2010M.mp4"),
        VideoTest("hdr_uhd", Group.HDR10, R.string.t_hdr_uhd, R.string.f_hdr_uhd, HEVC, 3840, 2160, 40,
            tenBit = true,
            path = "HDR/HDR10/HEVC/Test%20Jellyfin%204K%20HEVC%20HDR10%2040M.mp4"),
        VideoTest("dv_uhd", Group.DOLBY, R.string.t_dv, R.string.f_dv, DV, 3840, 2160, 32,
            dolbyVision = true,
            path = "HDR/Dolby%20Vision/Test%20Jellyfin%204K%20DV%20P8.1.mp4"),

        // ---- 8K ----
        VideoTest("k8_hevc", Group.K8, R.string.t_8k, R.string.f_8k, HEVC, 7680, 4320, 150,
            path = "SDR/HEVC%208bit/Test%20Jellyfin%208K%20HEVC%208bit%20150M.mp4")
    )
}
