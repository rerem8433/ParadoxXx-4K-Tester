package fr.paradoxxx.tester4k

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object NetworkProbe {

    data class Connection(
        val type: String,        // « Câble Ethernet », « Wi-Fi », « Inconnue »
        val isWifi: Boolean,
        val wifiBand: String?,   // « 2,4 GHz », « 5 GHz »…
        val ethernetSpeed: Int?  // Mb/s négociés sur le port, si lisible
    )

    data class Result(val mbps: Double, val error: String?) {
        val ok get() = error == null && mbps > 0
    }

    @Suppress("DEPRECATION")
    fun connection(ctx: Context): Connection {
        var wifi = false
        var eth = false
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= 23) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                eth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            } else {
                val t = cm.activeNetworkInfo?.type
                wifi = t == ConnectivityManager.TYPE_WIFI
                eth = t == ConnectivityManager.TYPE_ETHERNET
            }
        } catch (_: Exception) { }

        var band: String? = null
        if (wifi) {
            try {
                val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val f = wm.connectionInfo?.frequency ?: 0
                band = when {
                    f in 2400..2500 -> "2,4 GHz"
                    f in 4900..5900 -> "5 GHz"
                    f > 5900 -> "6 GHz"
                    else -> null
                }
            } catch (_: Exception) { }
        }

        val ethSpeed = if (eth) try {
            File("/sys/class/net/eth0/speed").readText().trim().toIntOrNull()
        } catch (_: Exception) { null } else null

        val type = when { eth -> "Câble Ethernet"; wifi -> "Wi-Fi"; else -> "Inconnue" }
        return Connection(type, wifi, band, ethSpeed)
    }

    /** Télécharge pendant ~7 s (max 80 Mo) et calcule le débit réel. */
    suspend fun measure(url: String = TestCatalog.NETWORK_PROBE_URL): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Range", "bytes=0-83886079")
            }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext Result(0.0, "HTTP $code")

            val input = conn.inputStream
            val buf = ByteArray(64 * 1024)
            var total = 0L
            var start = 0L
            val maxBytes = 80L * 1024 * 1024
            while (true) {
                ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                if (start == 0L) start = SystemClock.elapsedRealtime() // chrono au 1er octet
                total += n
                val elapsed = SystemClock.elapsedRealtime() - start
                if (elapsed >= 7000 || total >= maxBytes) break
            }
            val sec = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1) / 1000.0
            Result(total * 8 / sec / 1_000_000.0, null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(0.0, e.javaClass.simpleName)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }
}
