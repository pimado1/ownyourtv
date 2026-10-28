package app.ownyourtv

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject

enum class Kind(val key: String) { ADS("ads"), TRACKING("tracking"), EXTRAS("extras"), APPS("apps") }

/** One app on this TV that OwnYourTV can switch off. [want] is the state the owner chose. */
class Item(val pkg: String, val label: String, val kind: Kind, val rec: Boolean, val verified: Boolean, val offNow: Boolean, val guessed: Boolean = false) {
    var want = offNow || rec
    val changes get() = want != offNow
}

object Catalog {
    /**
     * Never offered, whatever the catalogue or the name says: what Android or the TV needs to start, play a
     * channel or an HDMI input, take a remote, a keyboard or a network, and the tools people install apps with.
     */
    private val PROTECTED = Regex(
        "^(android|com\\.android\\.(systemui|shell|phone|settings|tv\\.settings|vending|bluetooth|nfc|networkstack.*|wifi.*|" +
            "permissioncontroller|packageinstaller|ext\\.services|ext\\.shared|providers\\..*|inputmethod.*|launcher.*|" +
            "externalstorage|documentsui|media.*|webview|server\\..*|se|location.*|keychain|certinstaller)|" +
            "android\\.ext\\..*|com\\.google\\.android\\.(gms|gsf|modulemetadata|permissioncontroller|packageinstaller|" +
            "ext\\..*|overlay\\..*|webview|tvlauncher|leanbacklauncher|apps\\.tv\\.launcherx|tungsten\\.setupwraith|" +
            "tv\\.remote\\..*|inputmethod.*|tts|marvin\\..*|configupdater|networkstack.*|tv)|" +
            ".*(factory|\\.fac\\b|\\.fac\\.|keyboard|inputmethod|ime\\b|bluetooth|remote|tvinput|tv\\.input|tuner|livetv|tvservice|" +
            "tv\\.service|hdmi|cec|middleware|systemserver|systemui|provider|setup|wizard|ota|update(r)?\\b|recovery|" +
            "mediatek\\.(tis|tv\\..*)|cltv|downloader|filelinked|sendfiles|ownyourtv).*)$",
        RegexOption.IGNORE_CASE
    )

    /** Names that say what an unknown app is. Found this way, an app is offered unticked, for the owner to decide. */
    private val GUESS = listOf(
        Kind.TRACKING to Regex("(^|\\.)(analytics|telemetry|tracking|tracker|logkit|logcollect|feedback|crashreport|diagnos|usagestat|statistic|bigdata|dataservice|acr|samba)", RegexOption.IGNORE_CASE),
        Kind.ADS to Regex("(^|\\.)(ads?|adservice|advert|promo|promotion|sponsor|recommend(ation)?s?|partnercustomizer|demo|exhibit|retail|storemode|messagebox|pushservice|marketing|channelplus|fastchannel)", RegexOption.IGNORE_CASE),
    )

    fun protectedPkg(pkg: String) = PROTECTED.matches(pkg)

    /** Catalogue entries installed on this TV, then the unknown ones whose name gives them away. */
    fun forTv(ctx: Context, installed: Set<String>, disabled: Set<String>): List<Item> {
        val json = ctx.assets.open("catalog.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("packages")
        val pm = ctx.packageManager
        val kinds = Kind.entries.associateBy { it.key }
        val out = mutableListOf<Item>()
        for (pkg in installed) {
            if (pkg == ctx.packageName) continue
            if (json.has(pkg)) {
                val e = json.getJSONObject(pkg)
                out += Item(pkg, label(pm, pkg), kinds[e.optString("k")] ?: Kind.EXTRAS, e.optBoolean("r"),
                    (e.optJSONArray("v")?.length() ?: 0) > 0, pkg in disabled)
            } else if (!protectedPkg(pkg)) {
                GUESS.firstOrNull { it.second.containsMatchIn(pkg) }?.let { (kind, _) ->
                    out += Item(pkg, label(pm, pkg), kind, rec = false, verified = false, offNow = pkg in disabled, guessed = true)
                }
            }
        }
        return out.sortedWith(compareByDescending<Item> { it.rec }.thenByDescending { it.verified }.thenBy { it.label.lowercase() })
    }

    private fun label(pm: PackageManager, pkg: String): String = runCatching {
        val ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        pm.getApplicationLabel(ai).toString().takeIf { it.isNotBlank() && it != pkg }
    }.getOrNull() ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
