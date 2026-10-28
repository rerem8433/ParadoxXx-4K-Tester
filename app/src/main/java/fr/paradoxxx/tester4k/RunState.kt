package fr.paradoxxx.tester4k

import android.content.Context
import org.json.JSONArray

/**
 * Sauvegarde la progression sur le stockage, test par test.
 * Si Android tue l'appli pendant un test (mémoire saturée), on le détecte
 * au lancement suivant et on peut reprendre au test d'après.
 */
class RunState(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("run_state", Context.MODE_PRIVATE)

    data class Snapshot(
        val done: List<TestResult>,
        val crashedTest: VideoTest,
        val net: NetworkProbe.Result
    )

    fun begin(net: NetworkProbe.Result) {
        prefs.edit()
            .clear()
            .putBoolean("active", true)
            .putFloat("mbps", net.mbps.toFloat())
            .putString("netError", net.error)
            .putString("done", "[]")
            .commit()
    }

    /** Écrit AVANT de lancer la lecture : si l'appli meurt, ce test sera le coupable. */
    fun markRunning(t: VideoTest) {
        prefs.edit().putString("running", t.id).commit()
    }

    fun saveDone(results: List<TestResult>) {
        val arr = JSONArray()
        results.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("done", arr.toString()).remove("running").commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    /** Non nul si la dernière série de tests a été interrompue par un arrêt brutal de l'appli. */
    fun crashed(): Snapshot? {
        if (!prefs.getBoolean("active", false)) return null
        val runningId = prefs.getString("running", null) ?: return null
        val test = TestCatalog.tests.firstOrNull { it.id == runningId } ?: return null
        val done = try {
            val arr = JSONArray(prefs.getString("done", "[]"))
            (0 until arr.length()).mapNotNull { TestResult.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
        val net = NetworkProbe.Result(prefs.getFloat("mbps", 0f).toDouble(), prefs.getString("netError", null))
        return Snapshot(done, test, net)
    }
}
