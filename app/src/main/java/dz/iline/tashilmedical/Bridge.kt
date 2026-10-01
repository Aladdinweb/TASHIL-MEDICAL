package dz.iline.tashilmedical
import android.util.Base64
import org.json.*
import java.net.HttpURLConnection
import java.net.URL

data class Staff(val name: String, val role: String)
data class Alert(val id: String, val uid: String, val from: String, val target: String, val ts: Long, val sos: Boolean)

/** GitHub Bridge: one JSON file per user / alert under tm/<etabSerial>/<deptSerial>/ */
object Bridge {
    private const val RAW = "application/vnd.github.raw+json"
    private val repo = "https://api.github.com/repos/${BuildConfig.GH_OWNER}/${BuildConfig.GH_REPO}"
    private fun base(p: Profile) = "tm/${p.etab}/${p.dept}"

    private fun call(m: String, url: String, body: String? = null, accept: String = "application/vnd.github+json"): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = m; c.connectTimeout = 10000; c.readTimeout = 10000
        c.setRequestProperty("Authorization", "Bearer ${BuildConfig.GH_TOKEN}")
        c.setRequestProperty("Accept", accept)
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "")
    }

    private fun put(path: String, j: JSONObject) {
        val url = "$repo/contents/$path"
        val (c, t) = call("GET", url)
        val b = JSONObject().put("message", "tm sync")
            .put("content", Base64.encodeToString(j.toString().toByteArray(), Base64.NO_WRAP))
        if (c == 200) b.put("sha", JSONObject(t).getString("sha"))
        call("PUT", url, b.toString())
    }

    private fun names(path: String): List<String> {
        val (c, t) = call("GET", "$repo/contents/$path")
        if (c != 200) return emptyList()
        val a = JSONArray(t); return (0 until a.length()).map { a.getJSONObject(it).getString("name") }
    }
    private fun get(path: String) = JSONObject(call("GET", "$repo/contents/$path", accept = RAW).second)

    fun setStatus(p: Profile) = put("${base(p)}/staff/${p.uid}.json", JSONObject()
        .put("name", p.name).put("role", p.role).put("shift", p.shift)
        .put("status", if (p.onDuty) "EN_SERVICE" else "HORS_SERVICE").put("ts", System.currentTimeMillis()))

    /** Staff currently En Service (heartbeat < 5 min old) in my establishment + department. */
    fun staff(p: Profile): List<Staff> {
        val now = System.currentTimeMillis()
        return names("${base(p)}/staff").mapNotNull { n ->
            val j = runCatching { get("${base(p)}/staff/$n") }.getOrNull() ?: return@mapNotNull null
            if (j.optString("status") == "EN_SERVICE" && now - j.optLong("ts") < 300000) Staff(j.getString("name"), j.getString("role")) else null
        }
    }

    fun sendAlert(p: Profile, target: String, sos: Boolean = false) {
        val ts = System.currentTimeMillis(); val id = "${ts}_${p.uid}"
        put("${base(p)}/alerts/$id.json", JSONObject().put("id", id).put("uid", p.uid)
            .put("from", "${p.name} (${p.role})").put("target", target).put("sos", sos).put("ts", ts))
    }

    fun newAlerts(p: Profile, since: Long): List<Alert> = names("${base(p)}/alerts")
        .filter { (it.substringBefore('_').toLongOrNull() ?: 0) > since }
        .mapNotNull { n -> runCatching { get("${base(p)}/alerts/$n") }.getOrNull() }
        .map { Alert(it.getString("id"), it.getString("uid"), it.getString("from"), it.getString("target"), it.getLong("ts"), it.optBoolean("sos")) }

    /** (tag, apkUrl) of the latest GitHub Release. */
    fun latest(): Pair<String, String> {
        val j = JSONObject(call("GET", "$repo/releases/latest").second)
        return j.getString("tag_name") to j.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
    }
}
