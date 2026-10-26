package fr.paradoxxx.tester4k

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var home: View
    private lateinit var run: View
    private lateinit var results: View
    private lateinit var logPanel: View
    private lateinit var runTitle: TextView
    private lateinit var runStatus: TextView
    private lateinit var runProgress: ProgressBar
    private lateinit var resultsHeader: TextView
    private lateinit var resultsList: LinearLayout
    private lateinit var resultsScroll: ScrollView
    private lateinit var logText: TextView
    private lateinit var logPath: TextView
    private lateinit var btnStart: Button
    private lateinit var btnDetails: Button
    private lateinit var btnLog: Button
    private lateinit var tester: PlaybackTester

    private var job: Job? = null
    private var showDetails = false
    private var lastResults: List<TestResult> = emptyList()
    private var lastSummary: List<SummaryLine> = emptyList()
    private var lastLog: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        home = findViewById(R.id.home)
        run = findViewById(R.id.run)
        results = findViewById(R.id.results)
        logPanel = findViewById(R.id.logPanel)
        runTitle = findViewById(R.id.runTitle)
        runStatus = findViewById(R.id.runStatus)
        runProgress = findViewById(R.id.runProgress)
        resultsHeader = findViewById(R.id.resultsHeader)
        resultsList = findViewById(R.id.resultsList)
        resultsScroll = findViewById(R.id.resultsScroll)
        logText = findViewById(R.id.logText)
        logPath = findViewById(R.id.logPath)
        btnStart = findViewById(R.id.btnStart)
        btnDetails = findViewById(R.id.btnDetails)
        btnLog = findViewById(R.id.btnLog)
        tester = PlaybackTester(this, findViewById<PlayerView>(R.id.playerView))

        findViewById<TextView>(R.id.deviceInfo).text = deviceLine()

        btnStart.setOnClickListener { startTests() }
        findViewById<Button>(R.id.btnRestart).setOnClickListener { startTests() }
        btnDetails.setOnClickListener {
            showDetails = !showDetails
            renderResults()
        }
        btnLog.setOnClickListener { openLog() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyLog() }
        findViewById<Button>(R.id.btnLogBack).setOnClickListener {
            show(results)
            btnLog.requestFocus()
        }
        show(home)
        btnStart.requestFocus()
    }

    // ---------------- Déroulement ----------------

    private fun startTests() {
        job?.cancel()
        show(run)
        val tests = TestCatalog.tests
        runProgress.max = tests.size + 1
        runProgress.progress = 0

        job = lifecycleScope.launch {
            runTitle.text = "Mesure de la connexion…"
            runStatus.text = "Téléchargement d'un échantillon pendant quelques secondes"
            val conn = NetworkProbe.connection(this@MainActivity)
            val net = NetworkProbe.measure()
            runStatus.text = if (net.ok) "Débit mesuré : ${Verdict.fmt(net.mbps)} Mb/s" else "Connexion : ${net.error}"
            runProgress.progress = 1
            delay(1200)

            val out = mutableListOf<TestResult>()
            tests.forEachIndexed { i, t ->
                runTitle.text = "Test ${i + 1}/${tests.size} — ${t.label}"
                runStatus.text = "Vérification du décodeur…"
                val probe = CodecProbe.probe(t)
                // On ne saute que l'absence totale de décodeur, ou une 8K que le décodeur refuse.
                // Sinon on tente la lecture : les déclarations des firmwares sont souvent fausses.
                val skip = (!probe.anyDecoder && !t.dolbyVision) ||
                        (probe.anyDecoder && !probe.sizeSupported && t.width > 3840)

                val outcome = if (skip) {
                    runStatus.text = "Format non pris en charge par ce boîtier — test ignoré"
                    delay(1000)
                    null
                } else {
                    runStatus.text = "Chargement de la vidéo…"
                    tester.run(t.url) { played ->
                        if (played > 0) runStatus.text = "Lecture : ${played / 1000} / 10 s"
                    }
                }
                out += Verdict.evaluate(t, probe, outcome, if (net.ok) net.mbps else 0.0)
                runProgress.progress = i + 2
            }

            lastResults = out
            val hdmi = HdmiInfo.read()
            lastSummary = Verdict.summary(out, net, conn, displayHdr10(), hdmi)
            lastLog = LogBuilder.build(this@MainActivity, out, lastSummary, net, conn)
            saveLog(lastLog)
            showDetails = false
            renderResults()
            show(results)
            btnDetails.requestFocus()
        }
    }

    // ---------------- Résultats ----------------

    private fun renderResults() {
        resultsHeader.text = deviceLine() + hdrLine()
        btnDetails.text = if (showDetails) "Masquer les détails" else "Détails techniques"
        resultsList.removeAllViews()

        for (l in lastSummary) {
            resultsList.addView(row("${l.icon}  ${l.title}", l.text, 22f, 17f))
        }
        if (showDetails) {
            resultsList.addView(sectionTitle("Détail des ${lastResults.size} tests"))
            for (r in lastResults) {
                resultsList.addView(row("${r.status.icon}  ${r.test.label} — ${r.status.label}", r.detail, 17f, 14f))
            }
        }
        resultsScroll.scrollTo(0, 0)
    }

    private fun row(title: String, sub: String, titleSp: Float, subSp: Float): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.row_bg)
            isFocusable = true
            val p = dp(14)
            setPadding(p, dp(10), p, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        box.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSp)
        })
        box.addView(TextView(this).apply {
            text = sub
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.muted))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, subSp)
        })
        return box
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setPadding(0, dp(16), 0, dp(8))
    }

    // ---------------- Rapport ----------------

    private fun logFile(): File? = getExternalFilesDir(null)?.let { File(it, "paradoxxx-4k-log.txt") }

    private fun saveLog(text: String) {
        try { logFile()?.writeText(text) } catch (_: Exception) { }
    }

    private fun openLog() {
        logText.text = lastLog
        val f = logFile()
        logPath.text = if (f != null && f.exists())
            "Aussi enregistré dans : ${f.absolutePath}" else "Rapport non enregistré sur le stockage"
        show(logPanel)
        findViewById<ScrollView>(R.id.logScroll).scrollTo(0, 0)
        findViewById<Button>(R.id.btnCopy).requestFocus()
    }

    private fun copyLog() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ParadoxXx 4K Tester", lastLog))
            Toast.makeText(this, "Rapport copié dans le presse-papiers", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Copie impossible", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- Divers ----------------

    private fun show(v: View) {
        for (x in listOf(home, run, results, logPanel)) x.visibility = if (x == v) View.VISIBLE else View.GONE
    }

    private fun deviceLine(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${Build.HARDWARE}"

    private fun hdrLine(): String = when (displayHdr10()) {
        true -> " · Écran HDR10 détecté"
        false -> " · Écran sans HDR"
        null -> ""
    }

    private fun displayHdr10(): Boolean? {
        if (Build.VERSION.SDK_INT < 24) return null
        return try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val types = dm.getDisplay(Display.DEFAULT_DISPLAY)?.hdrCapabilities?.supportedHdrTypes
                ?: return false
            types.contains(Display.HdrCapabilities.HDR_TYPE_HDR10)
        } catch (_: Exception) { null }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            logPanel.visibility == View.VISIBLE -> { show(results); btnLog.requestFocus() }
            run.visibility == View.VISIBLE || results.visibility == View.VISIBLE -> {
                job?.cancel()
                show(home)
                btnStart.requestFocus()
            }
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Quitter l'appli pendant un test l'interrompt proprement
        if (run.visibility == View.VISIBLE) {
            job?.cancel()
            show(home)
        }
    }
}
