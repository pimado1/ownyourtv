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

    /**
     * Never offered, even when a tested list switched it off: switched off, these left a TV stuck on its boot logo
     * that only a factory reset brought back (com.android.sdksandbox on a TCL G08, CleanYourTV #2557).
     */
    private val NEVER = Regex("^com\\.android\\.sdksandbox$")

    /**
     * Offered, never ticked: on some builds of the same model these hold a content provider a TV service needs, and
     * the TV crash-loops without it (the partner customizer's tvlauncher.widget on TCL Google TV 14, CleanYourTV #2138).
     */
    private val NO_TICK = Regex(".*\\.partnercustomizer(\\..*)?$")

    /**
     * What OwnYourTV offers on this TV, in order of trust: what a list switched off on this very model, then
     * OwnYourTV's own catalogue, then what lists of the same brand switch off, then unknown apps whose name gives
     * them away. Never what this model's list kept on purpose, and only ticked when checked.
     */
    fun forTv(ctx: Context, installed: Set<String>, disabled: Set<String>, tv: TvDb.Tv?, brand: TvDb.Brand?): List<Item> {
        val json = ctx.assets.open("catalog.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("packages")
        val db = TvDb.load(ctx)
        val pm = ctx.packageManager
        val kinds = Kind.entries.associateBy { it.key }
        val keep = (tv?.keep ?: emptySet()) + (if (tv == null) brand?.keep ?: emptySet() else emptySet())
        val out = mutableListOf<Item>()
        for (pkg in installed) {
            if (pkg == ctx.packageName || pkg in keep || HOME_STEP.contains(pkg) || NEVER.matches(pkg)) continue
            val own = json.optJSONObject(pkg)
            val onModel = tv != null && pkg in tv.off
            val onBrand = brand != null && pkg in brand.seen
            val kind = kinds[own?.optString("k") ?: db.kindOf(pkg) ?: ""]
            when {
                onModel -> out += Item(pkg, label(pm, pkg), kind ?: Kind.EXTRAS, rec = pkg in tv!!.rec, verified = true, offNow = pkg in disabled)
                own != null -> out += Item(pkg, label(pm, pkg), kind ?: Kind.EXTRAS, own.optBoolean("r"),
                    (own.optJSONArray("v")?.length() ?: 0) > 0, pkg in disabled)
                onBrand && !protectedPkg(pkg) -> out += Item(pkg, label(pm, pkg), kind ?: Kind.EXTRAS,
                    rec = tv == null && pkg in brand!!.rec, verified = false, offNow = pkg in disabled, guessed = false)
                !protectedPkg(pkg) -> GUESS.firstOrNull { it.second.containsMatchIn(pkg) }?.let { (k, _) ->
                    out += Item(pkg, label(pm, pkg), k, rec = false, verified = false, offNow = pkg in disabled, guessed = true)
                }
            }
        }
        // A list that switches off an app people use (a streaming app, voice, casting) did it by choice: never ticked.
        out.filter { it.kind == Kind.APPS && !it.offNow }.forEach { it.want = false }
        out.replaceAll { if (it.rec && NO_TICK.matches(it.pkg)) Item(it.pkg, it.label, it.kind, false, it.verified, it.offNow, it.guessed) else it }
        return out.sortedWith(compareByDescending<Item> { it.rec }.thenByDescending { it.verified }.thenBy { it.label.lowercase() })
    }

    /** The home step handles these itself, in the right order and only when it is safe. */
    private val HOME_STEP = setOf("com.google.android.tungsten.setupwraith", "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.amazon.tv.launcher")

    private fun label(pm: PackageManager, pkg: String): String = runCatching {
        val ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
        pm.getApplicationLabel(ai).toString().takeIf { it.isNotBlank() && it != pkg }
    }.getOrNull() ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
