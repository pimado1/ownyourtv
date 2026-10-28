package app.ownyourtv

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * OwnYourTV's TV database (assets/tvs.json, built by tools/build_tvs.py): the TVs lists were tested on, what each
 * switched off and kept, its stock home and its home-screen quirks, and per brand what its lists switch off.
 */
class TvDb private constructor(json: JSONObject) {
    private val pkgs = json.getJSONArray("pkgs").strings()
    private val kinds = json.getJSONArray("kinds").strings()
    private val tvs = json.getJSONArray("tvs")
    private val brands = json.getJSONObject("brands")

    /** One TV from the database, recognised on this set. */
    class Tv(val id: String, val name: String, val brand: String, val home: String, val flags: Set<String>,
             val off: Set<String>, val rec: Set<String>, val keep: Set<String>)

    /** What lists of one brand switch off and keep, for a TV of that brand that is not in the database. */
    class Brand(val name: String, val lists: Int, val seen: Set<String>, val rec: Set<String>, val keep: Set<String>)

    fun kindOf(pkg: String): String? = pkgs.indexOf(pkg).takeIf { it >= 0 }?.let { kinds[it] }

    /** The database TV this set is, matched on the names Android reports (model, device, product, build). */
    fun detect(): Tv? {
        val me = listOf(Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.HARDWARE, Build.FINGERPRINT)
            .joinToString(" ").lowercase()
        val maker = brandKey()
        var best: JSONObject? = null
        var bestScore = 0
        for (i in 0 until tvs.length()) {
            val t = tvs.getJSONObject(i)
            val tokens = t.getJSONArray("m").strings()
            var score = tokens.sumOf { tok -> if (Regex("(^|[^a-z0-9])" + Regex.escape(tok) + "($|[^a-z0-9])").containsMatchIn(me)) tok.length else 0 }
            if (score == 0) continue
            if (maker != null && t.getString("b").contains(maker)) score += 2
            if (score > bestScore) { best = t; bestScore = score }
        }
        // One short token alone (a board name, say) is too weak a match to trust.
        return best?.takeIf { bestScore >= 5 }?.let { t ->
            Tv(t.getString("id"), t.getString("n"), t.getString("b"), t.optString("home"), t.getJSONArray("f").strings().toSet(),
                t.getJSONArray("off").names(), t.getJSONArray("rec").names(), t.getJSONArray("keep").names())
        }
    }

    fun brand(): Brand? {
        val key = brandKey() ?: return null
        val name = brands.keys().asSequence().firstOrNull { it == key || it.startsWith("$key ") || key.startsWith(it) } ?: return null
        val b = brands.getJSONObject(name)
        return Brand(name, b.getInt("lists"), b.getJSONArray("seen").names(), b.getJSONArray("rec").names(), b.getJSONArray("keep").names())
    }

    private fun brandKey(): String? = (Build.BRAND.takeIf { it.isNotBlank() && it.lowercase() != "android" } ?: Build.MANUFACTURER)
        ?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JSONArray.names(): Set<String> = (0 until length()).map { pkgs[getInt(it)] }.toSet()

    companion object {
        private fun JSONArray.strings() = (0 until length()).map { getString(it) }

        @Volatile private var cached: TvDb? = null

        fun load(ctx: Context): TvDb = cached ?: ctx.assets.open("tvs.json").bufferedReader().use {
            TvDb(JSONObject(it.readText()))
        }.also { cached = it }
    }
}
