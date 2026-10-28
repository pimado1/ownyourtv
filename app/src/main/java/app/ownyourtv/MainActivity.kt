package app.ownyourtv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var root: FrameLayout
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("own", MODE_PRIVATE) }

    private var tv: Tv? = null
    private var items: List<Item> = emptyList()
    private var ourHome = false
    private var wantHome = true
    private var screen = ""
    private var poll: Runnable? = null
    private var still = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        app.ownyourtv.home.HomeKeyService.keepHomeOn(this)
        root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(20, 26, 18), Ui.INK, Ui.INK))
        }
        setContentView(root)
        // Screenshots of each screen for the store page and for checking translations.
        when (intent.getStringExtra("preview")) {
            "dev" -> { still = true; devMode() }
            "done" -> finished(17, 2, 0)
            "restore" -> restore()
            "list" -> connect()
            else -> welcome()
        }
    }

    override fun onDestroy() {
        stopPoll()
        thread { tv?.close() }
        super.onDestroy()
    }

    @Deprecated("TV back key")
    override fun onBackPressed() {
        when (screen) {
            "list", "restore" -> home()
            "progress" -> Unit
            else -> super.onBackPressed()
        }
    }

    private fun adbOn() = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    private fun show(name: String, v: View, focus: View? = null) {
        stopPoll()
        screen = name
        root.removeAllViews()
        v.alpha = 0f
        root.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        v.animate().alpha(1f).setDuration(220).start()
        (focus ?: v).post { (focus ?: v.focusSearch(View.FOCUS_DOWN))?.requestFocus() }
    }

    private fun page() = ui.col().apply { setPadding(ui.dp(64), ui.dp(30), ui.dp(64), ui.dp(24)); clipChildren = false; clipToPadding = false }

    private fun stopPoll() { poll?.let { main.removeCallbacks(it) }; poll = null }

    private fun every(ms: Long, f: () -> Unit) {
        stopPoll()
        val r = object : Runnable { override fun run() { f(); if (poll === this) main.postDelayed(this, ms) } }
        poll = r
        main.postDelayed(r, ms)
    }

    // 1. Welcome.
    private fun welcome() {
        val p = page()
        val split = ui.row(48)
        val left = ui.col()
        left.addView(ui.row().apply {
            addView(ui.title("Own", 64)); addView(ui.title("Your", 64).apply { setTextColor(Ui.LIME) }); addView(ui.title("TV", 64))
        })
        left.addView(ui.text(getString(R.string.tagline), 26, Ui.LIME, ui.display), ui.lp().apply { topMargin = ui.dp(8) })
        left.addView(ui.text(getString(R.string.w_lead), 18, Ui.GREY), ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(22) })
        val points = ui.col(14)
        kotlin.collections.listOf(R.string.w_p1, R.string.w_p2, R.string.w_p3).forEach { id ->
            points.addView(ui.row(14).apply { addView(ui.check(true)); addView(ui.text(getString(id), 18), ui.lp(0, WRAP_CONTENT, 1f)) }, ui.lp(MATCH_PARENT))
        }
        left.addView(points, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(28) })
        val go = ui.button(getString(R.string.start), true) { if (adbOn()) connect() else devMode() }
        left.addView(go, ui.lp().apply { topMargin = ui.dp(34) })
        split.addView(left, ui.lp(0, WRAP_CONTENT, 1f))
        split.addView(android.widget.ImageView(this).apply { setImageResource(R.drawable.logo) }, ui.lp(ui.dp(250), ui.dp(250)))
        p.addView(split, ui.lp(MATCH_PARENT))
        show("welcome", p, go)
    }

    // 2. Developer mode, guided.
    private fun devMode() {
        val p = page()
        p.addView(ui.chip("1 / 3"))
        p.addView(ui.title(getString(R.string.d_title)), ui.lp().apply { topMargin = ui.dp(10) })
        p.addView(ui.text(getString(R.string.d_lead), 18, Ui.GREY), ui.lp().apply { topMargin = ui.dp(10) })
        val steps = ui.row(18)
        val about = ui.button(getString(R.string.d_open_about), true) { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) }
        val dev = ui.button(getString(R.string.d_open_dev), false) { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
        listOf(Triple(R.string.d_s1, R.string.d_s1b, about), Triple(R.string.d_s2, R.string.d_s2b, null), Triple(R.string.d_s3, R.string.d_s3b, dev))
            .forEachIndexed { i, (t, b, btn) ->
                val c = ui.col(10).apply {
                    background = ui.rounded(Ui.CARD, 20); setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(22))
                    addView(ui.text("${i + 1}", 30, Ui.LIME, ui.display))
                    addView(ui.text(getString(t), 20, bold = true))
                    addView(ui.text(getString(b), 14, Ui.GREY))
                    if (btn != null) addView(btn, ui.lp().apply { topMargin = ui.dp(8) })
                }
                steps.addView(c, ui.lp(0, MATCH_PARENT, 1f))
            }
        p.addView(steps, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(30) })
        val wait = ui.text(getString(R.string.d_wait), 15, Ui.LIME, ui.mono)
        p.addView(wait, ui.lp().apply { topMargin = ui.dp(26) })
        show("dev", p, about)
        every(1000) { wait.alpha = if (wait.alpha > 0.6f) 0.45f else 1f; if (adbOn() && !still) connect() }
    }

    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    override fun onResume() {
        super.onResume()
        if (screen == "dev" && adbOn() && !still) connect()
    }

    // 3. The TV asks its owner; OwnYourTV waits for the OK.
    private fun connect() {
        val p = page()
        p.addView(ui.chip("2 / 3"))
        p.addView(ui.title(getString(R.string.c_title)), ui.lp().apply { topMargin = ui.dp(10) })
        p.addView(ui.text(getString(R.string.c_lead), 20), ui.lp().apply { topMargin = ui.dp(12) })
        val mock = ui.col(14).apply {
            background = ui.rounded(Ui.CARD, 20, 2, Color.argb(50, 255, 255, 255))
            setPadding(ui.dp(28), ui.dp(24), ui.dp(28), ui.dp(24))
            addView(ui.text(getString(R.string.c_mock_title), 20, bold = true))
            addView(ui.row(12).apply { addView(ui.check(true)); addView(ui.text(getString(R.string.c_mock_always), 16)) })
            addView(ui.row(16).apply {
                addView(ui.text(getString(R.string.cancel).uppercase(), 15, Ui.GREY, ui.mono))
                addView(ui.text("  OK  ", 15, Ui.INK, ui.mono).apply { background = ui.rounded(Ui.LIME, 8); setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6)) })
            })
        }
        p.addView(mock, ui.lp(ui.dp(520)).apply { topMargin = ui.dp(28) })
        p.addView(ui.text(getString(R.string.c_why), 15, Ui.GREY), ui.lp().apply { topMargin = ui.dp(22) })
        val status = ui.text(getString(R.string.c_waiting), 16, Ui.LIME, ui.mono)
        p.addView(status, ui.lp().apply { topMargin = ui.dp(22) })
        val help = ui.row(16).apply { visibility = View.GONE }
        help.addView(ui.button(getString(R.string.d_open_dev), false) { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })
        help.addView(ui.button(getString(R.string.c_retry), true) { connect() })
        p.addView(help, ui.lp().apply { topMargin = ui.dp(16) })
        show("connect", p, p)
        val started = System.currentTimeMillis()
        every(600) {
            status.alpha = if (status.alpha > 0.6f) 0.45f else 1f
            if (System.currentTimeMillis() - started > 15000 && help.visibility != View.VISIBLE) {
                status.text = getString(R.string.c_stuck); help.visibility = View.VISIBLE; help.getChildAt(1).requestFocus()
            }
        }
        thread {
            if (!(1..8).any { Tv.portOpen() || run { Thread.sleep(700); false } }) {
                main.post { if (screen == "connect") { stopPoll(); status.text = getString(R.string.c_no_port); status.setTextColor(Ui.RED) } }
                return@thread
            }
            val t = runCatching { Tv.connect(this) }.getOrNull() ?: return@thread
            tv?.close()
            tv = t
            load()
            main.post { if (screen == "connect") { if (intent.getStringExtra("preview") == "list") category(Kind.TRACKING, getString(R.string.h_track)) else home() } }
        }
    }

    private fun pkgs(flag: String) = tv!!.sh("pm list packages $flag").out.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()

    private fun load() {
        val installed = pkgs("")
        val disabled = pkgs("-d")
        items = Catalog.forTv(this, installed, disabled)
        ourHome = homeNow() == HOME_ACTIVITY
        wantHome = true
    }

    private fun homeNow() = tv!!.sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").out.lines().last().trim()

    private fun changes() = items.count { it.changes } + if (wantHome != ourHome) 1 else 0

    // 4. The dashboard: every choice in one screen.
    private fun home() {
        val p = page()
        val head = ui.row(16)
        head.addView(ui.chip("3 / 3"))
        head.addView(ui.text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", 14, Ui.GREY, ui.mono))
        p.addView(head)
        p.addView(ui.title(getString(R.string.h_title), 30), ui.lp().apply { topMargin = ui.dp(4) })

        val body = ui.row(28).apply { gravity = Gravity.TOP; clipChildren = false }
        val grid = ui.col(12).apply { clipChildren = false }
        val homeCard = card(getString(R.string.h_home), getString(R.string.h_home_b),
            when { ourHome && wantHome -> getString(R.string.h_home_on); wantHome -> getString(R.string.h_home_will); ourHome -> getString(R.string.h_home_back); else -> getString(R.string.h_home_skip) },
            if (wantHome) 1f else 0f, wide = true, check = wantHome)
        homeCard.setOnClickListener { wantHome = !wantHome; home() }
        grid.addView(homeCard, ui.lp(MATCH_PARENT))
        val kinds = listOf(Kind.ADS to (R.string.h_ads to R.string.h_ads_b), Kind.TRACKING to (R.string.h_track to R.string.h_track_b),
            Kind.EXTRAS to (R.string.h_extras to R.string.h_extras_b), Kind.APPS to (R.string.h_apps to R.string.h_apps_b))
        var first: View = homeCard
        kinds.chunked(2).forEach { pair ->
            val r = ui.row(12).apply { clipChildren = false }
            pair.forEach { (k, s) ->
                val list = items.filter { it.kind == k }
                val off = list.count { it.want }
                val c = card(getString(s.first), getString(s.second),
                    if (list.isEmpty()) getString(R.string.own_h_none) else getString(R.string.h_count, off, list.size),
                    if (list.isEmpty()) 0f else off / list.size.toFloat())
                c.isEnabled = list.isNotEmpty()
                c.setOnClickListener { if (list.isNotEmpty()) category(k, getString(s.first)) }
                r.addView(c, ui.lp(0, WRAP_CONTENT, 1f))
            }
            grid.addView(r, ui.lp(MATCH_PARENT))
        }
        body.addView(grid, ui.lp(0, WRAP_CONTENT, 1f))

        val side = ui.col(14).apply { gravity = Gravity.START; clipChildren = false }
        val n = changes()
        side.addView(ui.row(14).apply {
            gravity = Gravity.BOTTOM
            addView(ui.text("$n", 84, if (n > 0) Ui.LIME else Ui.DIM, ui.display))
            addView(ui.text(getString(R.string.h_changes), 20, Ui.GREY), ui.lp().apply { bottomMargin = ui.dp(12) })
        })
        val apply = ui.button(if (n > 0) getString(R.string.h_apply_short) else getString(R.string.h_apply_none), n > 0) { if (changes() > 0) apply() }
        side.addView(apply, ui.lp(MATCH_PARENT))
        side.addView(ui.button(getString(R.string.h_restore), false) { restore() }, ui.lp(MATCH_PARENT))
        side.addView(ui.text(getString(R.string.h_safe), 13, Ui.GREY), ui.lp(MATCH_PARENT))
        body.addView(side, ui.lp(ui.dp(300)))
        p.addView(body, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(12) })
        if (n > 0) first = apply
        show("home", p, first)
    }

    private fun card(title: String, sub: String, status: String, frac: Float, wide: Boolean = false, check: Boolean? = null): View {
        val c = ui.col(4).apply { setPadding(ui.dp(20), ui.dp(13), ui.dp(20), ui.dp(13)) }
        ui.focusable(c)
        val top = ui.row(12)
        if (check != null) top.addView(ui.check(check))
        top.addView(ui.ellipsize(ui.text(title, if (wide) 21 else 19, bold = true)), ui.lp(0, WRAP_CONTENT, 1f))
        c.addView(top, ui.lp(MATCH_PARENT))
        c.addView(ui.ellipsize(ui.text(sub, 13, Ui.GREY), 2), ui.lp(MATCH_PARENT))
        val foot = ui.row(14)
        foot.addView(ui.ellipsize(ui.text(status, 13, if (frac > 0) Ui.LIME else Ui.GREY, ui.mono)))
        val bar = FrameLayout(this).apply { background = ui.rounded(Color.argb(30, 255, 255, 255), 3) }
        bar.addView(View(this).apply { background = ui.rounded(Ui.LIME, 3); pivotX = 0f; scaleX = frac.coerceIn(0f, 1f) },
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        foot.addView(bar, ui.lp(0, ui.dp(5), 1f))
        c.addView(foot, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(4) })
        return c
    }

    // 5. One category, app by app.
    private fun category(kind: Kind, name: String) {
        val list = items.filter { it.kind == kind }
        val p = page()
        val head = ui.row(18)
        head.addView(ui.ellipsize(ui.title(name, 32)), ui.lp(0, WRAP_CONTENT, 1f))
        val checks = mutableListOf<Pair<Item, TextView>>()
        fun refresh() = checks.forEach { (it, box) -> box.text = if (it.want) "✓" else ""; box.background = if (it.want) ui.rounded(Ui.LIME, 8) else ui.rounded(Color.TRANSPARENT, 8, 2, Ui.DIM) }
        head.addView(ui.button(getString(R.string.l_tick_rec), false) { list.forEach { it.want = it.rec || it.offNow }; refresh() })
        head.addView(ui.button(getString(R.string.l_untick), false) { list.forEach { it.want = false }; refresh() })
        val done = ui.button(getString(R.string.l_done), true) { home() }
        head.addView(done)
        p.addView(head, ui.lp(MATCH_PARENT))
        p.addView(ui.text(getString(R.string.l_hint), 15, Ui.GREY), ui.lp().apply { topMargin = ui.dp(8) })
        val rows = ui.col(10)
        list.forEach { item ->
            val r = ui.row(18).apply { setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14)) }
            ui.focusable(r, radius = 16)
            val box = ui.check(item.want)
            checks += item to box
            r.addView(box)
            val txt = ui.col(4)
            txt.addView(ui.ellipsize(ui.text(item.label, 18, bold = true)))
            val note = when { item.verified -> " · " + getString(R.string.l_verified); item.guessed -> " · " + getString(R.string.l_detected); else -> "" }
            txt.addView(ui.ellipsize(ui.text(kindName(item.kind) + note, 13, Ui.GREY)))
            r.addView(txt, ui.lp(0, WRAP_CONTENT, 1f))
            val tag = when { item.offNow -> getString(R.string.l_off); item.rec -> getString(R.string.l_rec); else -> "" }
            if (tag.isNotEmpty()) r.addView(ui.chip(tag, if (item.offNow) Ui.GREY else Ui.LIME))
            r.setOnClickListener { item.want = !item.want; refresh() }
            rows.addView(r, ui.lp(MATCH_PARENT))
        }
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false; clipChildren = false; setPadding(ui.dp(22), ui.dp(12), ui.dp(22), ui.dp(24)); addView(rows) }
        p.addView(scroll, ui.lp(MATCH_PARENT, 0, 1f).apply { topMargin = ui.dp(14) })
        show("list", p, rows.getChildAt(0) ?: done)
    }

    private fun kindName(k: Kind) = getString(when (k) {
        Kind.ADS -> R.string.k_ads; Kind.TRACKING -> R.string.k_tracking; Kind.EXTRAS -> R.string.k_extras; Kind.APPS -> R.string.k_apps
    })

    // 6. Applying, step by step, in plain words.
    private class Step(val label: String, val run: () -> Boolean) { var view: TextView? = null }

    private fun apply() = runSteps(buildSteps())

    private fun buildSteps(): List<Step> {
        val t = tv ?: return emptyList()
        val steps = mutableListOf<Step>()
        items.filter { it.changes }.forEach { item ->
            if (item.want) steps += Step(getString(R.string.p_off, item.label)) {
                t.sh("pm disable-user --user 0 ${item.pkg}").out.contains("disabled").also { if (it) remember(OURS, item.pkg) }
            } else steps += Step(getString(R.string.p_on, item.label)) {
                t.sh("pm enable ${item.pkg}").out.contains("enabled").also { if (it) forget(OURS, item.pkg) }
            }
        }
        if (wantHome && !ourHome) {
            steps += Step(getString(R.string.p_perms)) {
                setOurHome(true)
                t.sh("pm grant $packageName android.permission.BLUETOOTH_CONNECT 2>/dev/null; pm grant $packageName android.permission.READ_TV_LISTINGS 2>/dev/null; " +
                    "cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; cmd appops set $packageName AUTO_START allow 2>/dev/null; true").ok
            }
            steps += Step(getString(R.string.p_keys)) {
                val s = t.sh("settings get secure enabled_accessibility_services").out.trim()
                val next = when { s.contains(KEY_SERVICE) -> s; s.isEmpty() || s == "null" -> KEY_SERVICE; else -> "$s:$KEY_SERVICE" }
                t.sh("settings put secure enabled_accessibility_services $next; settings put secure accessibility_enabled 1").ok
            }
            steps += Step(getString(R.string.p_home)) { makeHome(t) }
        } else if (!wantHome && ourHome) {
            steps += Step(getString(R.string.p_restore_home)) { undoHome(t) }
        }
        return steps
    }

    /** the home screen's Home entry stays off until the owner picks the home screen, so the TV never asks which home to use. */
    private fun setOurHome(on: Boolean) {
        packageManager.setComponentEnabledSetting(android.content.ComponentName(this, app.ownyourtv.home.HomeActivity::class.java),
            if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP)
        getSharedPreferences(app.ownyourtv.home.HomeKeyService.PREFS, MODE_PRIVATE).edit()
            .putBoolean(app.ownyourtv.home.HomeKeyService.KEY_CHOSEN, on).apply()
        // OwnYourTV has already done what the home screen's own first-run steps would ask for.
        if (on) getSharedPreferences("horizon_onboarding", MODE_PRIVATE).edit().putBoolean("done", true).apply()
    }

    /** The original home comes back: what OwnYourTV switched off for the home screen comes back on, the home screen steps aside. */
    private fun undoHome(t: Tv): Boolean {
        prefs.getStringSet(OURS_HOME, emptySet())!!.forEach { if (t.sh("pm enable $it").out.contains("enabled")) forget(OURS_HOME, it) }
        setOurHome(false)
        val s = t.sh("settings get secure enabled_accessibility_services").out.trim()
        val next = s.split(':').filter { it.isNotBlank() && it != "null" && it != KEY_SERVICE }.joinToString(":")
        t.sh(if (next.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $next")
        return homeNow() != HOME_ACTIVITY
    }

    private fun makeHome(t: Tv): Boolean {
        t.sh("cmd package set-home-activity --user 0 $HOME_ACTIVITY")
        if (homeNow() == HOME_ACTIVITY) return true
        // Google TV keeps its own home on the Home button: switch the stock homes off, never the setup wizard
        // that owns the Wi-Fi network.
        val enabled = pkgs("-e")
        STOCK_HOMES.filter { it in enabled }.forEach { if (t.sh("pm disable-user --user 0 $it").out.contains("disabled")) remember(OURS_HOME, it) }
        val wizard = "com.google.android.tungsten.setupwraith"
        if (wizard in enabled && !t.sh("dumpsys wifi | grep -o 'cname=[^ ]*'").out.contains("setupwraith")) {
            if (t.sh("pm disable-user --user 0 $wizard").out.contains("disabled")) remember(OURS_HOME, wizard)
        }
        t.sh("cmd package set-home-activity --user 0 $HOME_ACTIVITY")
        return homeNow() == HOME_ACTIVITY
    }

    private fun remember(key: String, pkg: String) = prefs.edit().putStringSet(key, (prefs.getStringSet(key, emptySet())!! + pkg)).apply()
    private fun forget(key: String, pkg: String) = prefs.edit().putStringSet(key, (prefs.getStringSet(key, emptySet())!! - pkg)).apply()

    private fun runSteps(steps: List<Step>, title: String = getString(R.string.p_title)) {
        val p = page()
        p.addView(ui.title(title))
        val list = ui.col(10)
        steps.forEach { s ->
            val v = ui.text("·  ${s.label}", 17, Ui.DIM)
            s.view = v
            list.addView(v)
        }
        val scroll = ScrollView(this).apply { addView(list) }
        p.addView(scroll, ui.lp(MATCH_PARENT, 0, 1f).apply { topMargin = ui.dp(24) })
        show("progress", p, p)
        thread {
            var failed = 0
            steps.forEach { s ->
                main.post { s.view?.apply { text = "…  ${s.label}"; setTextColor(Ui.PAPER); scroll.smoothScrollTo(0, top) } }
                val ok = runCatching { s.run() }.getOrDefault(false)
                if (!ok) failed++
                main.post { s.view?.apply { text = (if (ok) "✓  " else "✗  ") + s.label; setTextColor(if (ok) Ui.LIME else Ui.RED) } }
            }
            val off = items.count { it.want && !it.offNow }
            val on = items.count { !it.want && it.offNow }
            runCatching { load() }
            main.postDelayed({ finished(off, on, failed) }, 700)
        }
    }

    // 7. Done.
    private fun finished(off: Int, on: Int, failed: Int) {
        val p = page().apply { gravity = Gravity.CENTER_VERTICAL }
        p.addView(ui.title(getString(R.string.f_title), 56))
        p.addView(ui.text(getString(R.string.f_sum, off, on), 22, Ui.LIME, ui.mono), ui.lp().apply { topMargin = ui.dp(14) })
        if (failed > 0) p.addView(ui.text(getString(R.string.f_failed, failed), 16, Ui.RED), ui.lp().apply { topMargin = ui.dp(10) })
        val lock = ui.button(getString(R.string.f_close), true) {
            thread { runCatching { tv?.sh("settings put global adb_enabled 0") }; main.post { goHome() } }
        }
        p.addView(lock, ui.lp().apply { topMargin = ui.dp(36) })
        p.addView(ui.text(getString(R.string.f_close_b), 14, Ui.GREY), ui.lp().apply { topMargin = ui.dp(8) })
        p.addView(ui.button(getString(R.string.f_keep), false) { goHome() }, ui.lp().apply { topMargin = ui.dp(22) })
        show("done", p, lock)
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    // Undo: everything OwnYourTV switched off comes back on, and the original home with it.
    private fun restore() {
        val p = page().apply { gravity = Gravity.CENTER_VERTICAL }
        p.addView(ui.title(getString(R.string.r_title), 44))
        p.addView(ui.text(getString(R.string.r_lead), 18, Ui.GREY).apply { maxWidth = ui.dp(640) }, ui.lp().apply { topMargin = ui.dp(14) })
        val r = ui.row(16)
        val cancel = ui.button(getString(R.string.cancel), false) { home() }
        r.addView(cancel)
        r.addView(ui.button(getString(R.string.r_go), true) {
            val t = tv ?: return@button
            val steps = mutableListOf<Step>()
            prefs.getStringSet(OURS, emptySet())!!.forEach { pkg ->
                steps += Step(getString(R.string.p_on, items.firstOrNull { it.pkg == pkg }?.label ?: pkg)) {
                    t.sh("pm enable $pkg").out.contains("enabled").also { if (it) { forget(OURS, pkg); forget(OURS_HOME, pkg) } }
                }
            }
            if (ourHome || prefs.getStringSet(OURS_HOME, emptySet())!!.isNotEmpty()) steps += Step(getString(R.string.p_restore_home)) { undoHome(t) }
            items.forEach { if (it.pkg in prefs.getStringSet(OURS, emptySet())!!) it.want = false }
            runSteps(steps)
        })
        p.addView(r, ui.lp().apply { topMargin = ui.dp(30) })
        show("restore", p, cancel)
    }

    companion object {
        const val HOME_ACTIVITY = "app.ownyourtv/.home.HomeActivity"
        const val KEY_SERVICE = "app.ownyourtv/.home.HomeKeyService"
        const val OURS = "ours"
        const val OURS_HOME = "ours_home"
        val STOCK_HOMES = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher")
    }
}
