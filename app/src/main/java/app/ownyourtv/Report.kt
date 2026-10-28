package app.ownyourtv

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The anonymous report an owner may choose to send at the end: which TV this is and what worked, so the TV
 * database grows from real sets. Only names Android reports for the model, never anything about the person.
 */
object Report {
    const val URL_REPORT = "https://ownyourtv-reports.WORKERS_SUBDOMAIN.workers.dev/report"

    private fun name(s: String?) = (s ?: "").replace(Regex("[^A-Za-z0-9 ._+\\-()]"), "").trim().take(64).ifEmpty { "unknown" }

    fun build(app: String, known: String?, home: String, wifi: Boolean, off: Int, on: Int, failed: Int, failedPkgs: List<String>) = JSONObject().apply {
        put("v", 1)
        put("app", name(app))
        put("brand", name(Build.BRAND)); put("maker", name(Build.MANUFACTURER)); put("model", name(Build.MODEL))
        put("device", name(Build.DEVICE)); put("product", name(Build.PRODUCT))
        put("android", name(Build.VERSION.RELEASE)); put("sdk", Build.VERSION.SDK_INT)
        put("known", known ?: JSONObject.NULL)
        put("home", home); put("wifi", wifi)
        put("off", off.coerceIn(0, 999)); put("on", on.coerceIn(0, 999)); put("failed", failed.coerceIn(0, 999))
        put("failedPkgs", JSONArray(failedPkgs.take(40)))
    }

    /** One line for the screen, so the owner sees what is sent. */
    fun preview(r: JSONObject) = "${r.getString("brand")} ${r.getString("model")} · Android ${r.getString("android")} · ${r.getString("home")}"

    fun send(r: JSONObject): Boolean = runCatching {
        val c = URL(URL_REPORT).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        c.outputStream.use { it.write(r.toString().toByteArray()) }
        (c.responseCode == 200).also { c.disconnect() }
    }.getOrDefault(false)
}
