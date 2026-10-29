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
import app.ownyourtv.home.Recheck
import app.ownyourtv.home.Update
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
    private var fastNow = false
    private var wantFast = false
    private var homeRefused = false
    /** Apps switched off by name after a TV update, counted with the catalogue's on the last screen. */
    private var extraOff = 0
    /** What happened to the home screen this run, for the report: ok, refused, blocked, kept or already. */
    private var homeResult = "kept"
    private val failedPkgs = mutableListOf<String>()
    private var knownTv: TvDb.Tv? = null
    private var wizardOn = false
    private var wizardOwnsWifi = false
    /** Why the OwnYourTV home cannot be this TV's home, or null when it can. */
    private var homeBlock: Int? = null
    /** Where the remote's Back key goes in the assistant. */
    private var back: (() -> Unit)? = null
    private var screen = ""
    private var poll: Runnable? = null
    private var still = false
    /** Opened from the home screen's "update" pop-up: once connected, the update comes first. */
    private var wantUpdate = false
    private var wantGuard = false
    private var updateStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        app.ownyourtv.home.HomeKeeper.keepHomeOn(this)
        root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(20, 26, 18), Ui.INK, Ui.INK))
        }
        setContentView(root)
        // ownyourtv.py on a computer: Android lets only the app itself switch its home entry on, so the script
        // asks the app to, and only while USB debugging is on (the script needs it anyway).
        if (computerHome(intent)) {
            finish()
            return
        }
        // Screenshots of each screen for the store page and for checking translations.
        when (intent.getStringExtra("preview")) {
            "dev" -> { still = true; devMode() }
            "done" -> finished(17, 2, 0)
            "restore" -> restore()
            "list" -> connect()
            "wifi" -> wifiStep()
            else -> when {
                intent.getBooleanExtra(Update.EXTRA, false) -> startUpdate()
                intent.getBooleanExtra(Update.EXTRA_CHECK, false) -> checkUpdate()
                intent.getBooleanExtra(EXTRA_GUARD, false) -> { wantGuard = true; if (adbOn()) connect() else devMode() }
                intent.getBooleanExtra(Recheck.EXTRA, false) -> if (adbOn()) connect() else devMode()
                else -> welcome()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (computerHome(intent)) return
        if (intent.getBooleanExtra(Update.EXTRA, false)) startUpdate()
        else if (intent.getBooleanExtra(Update.EXTRA_CHECK, false)) checkUpdate()
        else if (intent.getBooleanExtra(Recheck.EXTRA, false)) { if (tv != null) recheck() else if (adbOn()) connect() else devMode() }
    }

    private fun computerHome(i: Intent): Boolean {
        if (!i.getBooleanExtra(EXTRA_HOME, false) || !adbOn()) return false
        setOurHome(i.getBooleanExtra(EXTRA_HOME_ON, true))
        return true
    }

    override fun onDestroy() {
        stopPoll()
        thread { tv?.close() }
        super.onDestroy()
    }

    @Deprecated("TV back key")
    override fun onBackPressed() {
        val b = back
        when {
            b != null -> b()
            screen == "list" || screen == "restore" || screen == "dock" -> home()
            screen == "progress" -> Unit
            else -> super.onBackPressed()
        }
    }

    private fun adbOn() = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    private fun show(name: String, v: View, focus: View? = null, onBack: (() -> Unit)? = null) {
        back = onBack
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
        val actions = ui.row(16)
        actions.addView(go)
        val upd = ui.button(Update.available(this)?.let { getString(R.string.up_button, it) } ?: getString(R.string.up_search), false) { checkUpdate() }
        actions.addView(upd)
        left.addView(actions, ui.lp().apply { topMargin = ui.dp(34) })
        // Opening the app is a good moment to ask: a newer version shows on the button at once.
        thread { if (Update.check(this)) main.post { Update.available(this)?.let { if (screen == "welcome") upd.text = getString(R.string.up_button, it) } } }
        split.addView(left, ui.lp(0, WRAP_CONTENT, 1f))
        split.addView(android.widget.ImageView(this).apply { setImageResource(R.drawable.logo) }, ui.lp(ui.dp(250), ui.dp(250)))
        p.addView(split, ui.lp(MATCH_PARENT))
        show("welcome", p, go)
    }

    // 2. Developer mode, guided.
    private fun devMode() {
        val p = page()
        p.addView(stepper(0), ui.lp(MATCH_PARENT).apply { bottomMargin = ui.dp(22) })
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
        // Back from Android's confirmation without updating: the button is ready again.
        if (screen == "update") updateStatus?.text = ""
    }

    // 3. The TV asks its owner; OwnYourTV waits for the OK.
    private fun connect() {
        val p = page()
        p.addView(stepper(0), ui.lp(MATCH_PARENT).apply { bottomMargin = ui.dp(22) })
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
            Update.checkIfDue(this)
            val back = Recheck.cameBack(this)
            main.post {
                if (screen == "connect") when {
                    wantUpdate && Update.canInstall(this) -> updateScreen()
                    wantGuard -> {
                        wantGuard = false
                        app.ownyourtv.home.Profiles.enableGuard(this)
                        startActivity(Intent(this, ProfilesActivity::class.java)); finish()
                    }
                    !prefs.getBoolean(SETUP_DONE, false) -> wizTv()
                    back.isNotEmpty() -> recheck()
                    else -> home()
                }
            }
        }
    }

    private fun pkgs(flag: String) = tv!!.sh("pm list packages $flag").out.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()

    private fun load() {
        val installed = pkgs("")
        val disabled = pkgs("-d")
        val db = TvDb.load(this)
        knownTv = db.detect()
        items = Catalog.forTv(this, installed, disabled, knownTv, db.brand())
        ourHome = homeNow() == HOME_ACTIVITY
        wizardOn = WIZARD in installed && WIZARD !in disabled
        wizardOwnsWifi = wizardOn && tv!!.sh("dumpsys wifi | grep -o 'cname=[^ ]*'").out.contains("setupwraith")
        val flags = knownTv?.flags ?: emptySet()
        val homes = tv!!.sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME").out
        homeBlock = when {
            ourHome -> null
            // Firmware that only lets its own list of apps be the home, or turns the setup wizard back on at boot.
            "home_allowlist" in flags || "wizard_returns" in flags -> R.string.h_home_no
            // Without a fallback home, a TV whose stock home is off has nothing to start before the first unlock.
            !homes.contains("FallbackHome") && !homes.contains(".settings") -> R.string.h_home_no
            else -> null
        }
        wantHome = ourHome || homeBlock == null
        fastNow = prefs.contains(ANIM)
        wantFast = fastNow
        Recheck.remember(this)
        tv!!.sh("pm grant $packageName android.permission.POST_NOTIFICATIONS 2>/dev/null; true")
        // So that later updates from GitHub install without the owner hunting for "unknown apps" in the settings.
        tv!!.sh("appops set $packageName REQUEST_INSTALL_PACKAGES allow 2>/dev/null; true")
        // So that a child's profile can switch its guard (an accessibility service) on by itself, without ADB.
        tv!!.sh("pm grant $packageName android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; true")
    }

    // After a TV update: what came back on is switched off again in one press.
    private fun recheck() {
        val back = Recheck.cameBack(this)
        if (back.isEmpty()) { home(); return }
        val p = page().apply { gravity = Gravity.CENTER_VERTICAL }
        p.addView(ui.title(getString(R.string.rc_title), 48))
        p.addView(ui.text(resources.getQuantityString(R.plurals.rc_count, back.size, back.size), 22, Ui.LIME, ui.mono), ui.lp().apply { topMargin = ui.dp(12) })
        p.addView(ui.text(Recheck.labels(this, back, 12), 17, Ui.PAPER).apply { maxWidth = ui.dp(900) }, ui.lp().apply { topMargin = ui.dp(16) })
        p.addView(ui.text(getString(R.string.rc_why), 16, Ui.GREY).apply { maxWidth = ui.dp(820) }, ui.lp().apply { topMargin = ui.dp(12) })
        val r = ui.row(16)
        val fix = ui.button(getString(R.string.rc_fix), true) {
            items.forEach { if (it.pkg in back) it.want = true }
            val chosen = getSharedPreferences(app.ownyourtv.home.HomeKeeper.PREFS, MODE_PRIVATE).getBoolean(app.ownyourtv.home.HomeKeeper.KEY_CHOSEN, false)
            val stockBack = back.any { it in prefs.getStringSet(OURS_HOME, emptySet())!! }
            wantHome = ourHome || (chosen && stockBack && homeBlock == null)
            wantFast = fastNow
            if (wantHome && !ourHome && wizardOwnsWifi) { wifiStep(); return@button }
            // Apps switched off by an older catalogue are not in today's lists: they are switched off by name.
            val steps = buildSteps().toMutableList()
            val listed = items.map { it.pkg }.toSet()
            val t = tv
            back.filter { it in prefs.getStringSet(OURS, emptySet())!! && it !in listed && t != null }.forEach { pkg ->
                steps += Step(getString(R.string.p_off, Recheck.labels(this, listOf(pkg), 1))) { t!!.sh("pm disable-user --user 0 $pkg").out.contains("disabled").also { if (it) extraOff++ } }
            }
            if (steps.isEmpty()) home() else runSteps(steps)
        }
        r.addView(fix)
        r.addView(ui.button(getString(R.string.rc_keep), false) { Recheck.keep(this, back); home() })
        r.addView(ui.button(getString(R.string.rc_later), false) { Recheck.later(this, back); home() })
        p.addView(r, ui.lp().apply { topMargin = ui.dp(32) })
        show("recheck", p, fix)
    }

    private fun homeNow() = tv!!.sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").out.lines().last().trim()

    private fun changes() = items.count { it.changes } + (if (wantHome != ourHome) 1 else 0) + (if (wantFast && !fastNow) 1 else 0)

    // 4. The dashboard: every choice in one screen.
    private fun home() {
        val p = page()
        val head = ui.row(16)
        head.addView(ui.text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", 14, Ui.GREY, ui.mono))
        head.addView(ui.chip(getString(if (knownTv != null) R.string.h_tv_known else R.string.h_tv_unknown), if (knownTv != null) Ui.LIME else Ui.GREY))
        p.addView(head)
        p.addView(ui.title(getString(R.string.h_title), 30), ui.lp().apply { topMargin = ui.dp(4) })

        val body = ui.row(28).apply { gravity = Gravity.TOP; clipChildren = false }
        val grid = ui.col(12).apply { clipChildren = false }
        val block = homeBlock
        val homeSub = if ("cast_needs_wizard" in (knownTv?.flags ?: emptySet()) && !ourHome) getString(R.string.h_home_cast) else getString(R.string.h_home_b)
        val homeCard = card(getString(R.string.h_home), homeSub,
            when { block != null -> getString(block); ourHome && wantHome -> getString(R.string.h_home_on); wantHome -> getString(R.string.h_home_will); ourHome -> getString(R.string.h_home_back); else -> getString(R.string.h_home_skip) },
            if (wantHome) 1f else 0f, wide = true, check = wantHome)
        homeCard.setOnClickListener { if (block == null) { wantHome = !wantHome; home() } }
        val dock = app.ownyourtv.home.Dock.current(this)
        val dockCard = card(getString(R.string.dk_title), dock.take(4).joinToString(", ") { it.label }.ifEmpty { getString(R.string.dk_empty) },
            resources.getQuantityString(R.plurals.dk_count, dock.size, dock.size), if (dock.isEmpty()) 0f else 1f)
        dockCard.setOnClickListener { dockScreen() }
        val top = ui.row(12).apply { clipChildren = false }
        top.addView(homeCard, ui.lp(0, MATCH_PARENT, 1.25f))
        top.addView(dockCard, ui.lp(0, MATCH_PARENT, 1f))
        grid.addView(top, ui.lp(MATCH_PARENT))
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
                r.addView(c, ui.lp(0, MATCH_PARENT, 1f))
            }
            grid.addView(r, ui.lp(MATCH_PARENT))
        }
        body.addView(grid, ui.lp(0, WRAP_CONTENT, 1f))

        val side = ui.col(14).apply { gravity = Gravity.START; clipChildren = false }
        val best = ui.col(4).apply { setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14)) }
        ui.focusable(best, Ui.LIME, Color.WHITE, 18, ring = false)
        best.addView(ui.text(getString(R.string.opt_btn), 19, Ui.INK, bold = true))
        best.addView(ui.text(getString(R.string.opt_sub), 13, Color.rgb(40, 48, 20)))
        best.setOnClickListener { optimise() }
        side.addView(best, ui.lp(MATCH_PARENT))
        val n = changes()
        side.addView(ui.row(14).apply {
            gravity = Gravity.BOTTOM
            addView(ui.text("$n", 84, if (n > 0) Ui.LIME else Ui.DIM, ui.display))
            addView(ui.text(getString(R.string.h_changes), 20, Ui.GREY), ui.lp().apply { bottomMargin = ui.dp(12) })
        })
        val apply = ui.button(if (n > 0) getString(R.string.h_apply_short) else getString(R.string.h_apply_none), false) { if (changes() > 0) apply() }
        side.addView(apply, ui.lp(MATCH_PARENT))
        Update.available(this)?.let { v -> side.addView(ui.button(getString(R.string.up_button, v), false) { updateScreen() }, ui.lp(MATCH_PARENT)) }
        side.addView(ui.button(getString(R.string.h_restore), false) { restore() }, ui.lp(MATCH_PARENT))
        side.addView(ui.button(getString(R.string.z_restart), false) { load2 { wizTv() } }, ui.lp(MATCH_PARENT))
        side.addView(ui.text(getString(R.string.h_safe), 13, Ui.GREY), ui.lp(MATCH_PARENT))
        body.addView(side, ui.lp(ui.dp(300)))
        p.addView(body, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(12) })
        first = if (n > 0) apply else best
        show("home", p, first)
    }

    // "Check for an update": GitHub now, then the update screen or "up to date".
    private fun checkUpdate() {
        val p = page().apply { gravity = Gravity.CENTER_VERTICAL }
        p.addView(ui.title(getString(R.string.up_search), 48))
        val status = ui.text(getString(R.string.up_searching), 18, Ui.LIME, ui.mono)
        p.addView(status, ui.lp().apply { topMargin = ui.dp(16) })
        val leave = { if (tv != null) home() else welcome() }
        val ok = ui.button(getString(android.R.string.ok), true) { leave() }
        p.addView(ok, ui.lp().apply { topMargin = ui.dp(32) })
        show("check", p, ok) { leave() }
        thread {
            val reached = Update.check(this)
            main.post {
                if (screen != "check") return@post
                when {
                    Update.available(this) != null -> startUpdate()
                    reached -> { status.setTextColor(Ui.PAPER); status.text = getString(R.string.up_latest, Update.current(this)) }
                    else -> { status.setTextColor(Ui.RED); status.text = getString(R.string.up_offline) }
                }
            }
        }
    }

    private fun startUpdate() {
        if (Update.canInstall(this)) { updateScreen(); return }
        // First update: OwnYourTV needs ADB once to allow itself to install apps.
        wantUpdate = true
        if (adbOn()) connect() else devMode()
    }

    // A newer OwnYourTV from GitHub, checked against this app's own key, then installed by Android.
    private fun updateScreen() {
        wantUpdate = false
        val version = Update.available(this) ?: run { if (tv != null) home() else finish(); return }
        val p = page().apply { gravity = Gravity.CENTER_VERTICAL }
        p.addView(ui.title(getString(R.string.up_title, version), 48))
        p.addView(ui.text(getString(R.string.up_lead), 20, Ui.PAPER).apply { maxWidth = ui.dp(860) }, ui.lp().apply { topMargin = ui.dp(14) })
        p.addView(ui.text(getString(R.string.up_safe), 16, Ui.GREY).apply { maxWidth = ui.dp(860) }, ui.lp().apply { topMargin = ui.dp(10) })
        val status = ui.text("", 16, Ui.LIME, ui.mono)
        updateStatus = status
        val r = ui.row(16)
        var busy = false
        val go = ui.button(getString(R.string.up_now), true) {}
        go.setOnClickListener {
            if (busy) return@setOnClickListener
            busy = true
            status.setTextColor(Ui.LIME); status.text = getString(R.string.up_download)
            thread {
                val res = Update.install(this)
                main.post {
                    busy = false
                    when (res) {
                        Update.Result.OK -> status.text = getString(R.string.up_install)
                        Update.Result.NOT_OURS -> { status.setTextColor(Ui.RED); status.text = getString(R.string.up_bad) }
                        else -> { status.setTextColor(Ui.RED); status.text = getString(R.string.up_failed) }
                    }
                }
            }
        }
        r.addView(go)
        val leave = { if (tv != null) home() else finish() }
        r.addView(ui.button(getString(R.string.rc_later), false) { Update.later(this); leave() })
        p.addView(r, ui.lp().apply { topMargin = ui.dp(32) })
        p.addView(status, ui.lp().apply { topMargin = ui.dp(20) })
        show("update", p, go) { leave() }
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
    private fun category(kind: Kind, name: String, step: Int? = null, onDone: () -> Unit = { home() }, onBack: (() -> Unit)? = null) {
        val list = items.filter { it.kind == kind }
        val p = page()
        if (step != null) p.addView(stepper(step), ui.lp(MATCH_PARENT).apply { bottomMargin = ui.dp(18) })
        val head = ui.row(18)
        head.addView(ui.ellipsize(ui.title(name, 32)), ui.lp(0, WRAP_CONTENT, 1f))
        val checks = mutableListOf<Pair<Item, TextView>>()
        fun refresh() = checks.forEach { (it, box) -> box.text = if (it.want) "✓" else ""; box.background = if (it.want) ui.rounded(Ui.LIME, 8) else ui.rounded(Color.TRANSPARENT, 8, 2, Ui.DIM) }
        head.addView(ui.button(getString(R.string.l_tick_rec), false) { list.forEach { it.want = it.rec || it.offNow }; refresh() })
        head.addView(ui.button(getString(R.string.l_untick), false) { list.forEach { it.want = false }; refresh() })
        val done = ui.button(getString(if (step != null) R.string.z_next else R.string.l_done), true) { onDone() }
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
        if (list.isEmpty()) rows.addView(ui.text(getString(R.string.z_none), 17, Ui.GREY))
        show("list", p, rows.getChildAt(0)?.takeIf { it.isFocusable } ?: done, onBack)
    }

    // The home screen's dock: the apps in it, in order, and every app that can go in it.
    private fun dockScreen(focusPkg: String? = null, step: Int? = null, onDone: () -> Unit = { home() }, onBack: (() -> Unit)? = null) {
        val chosen = app.ownyourtv.home.Dock.current(this).toMutableList()
        val all = app.ownyourtv.home.Dock.available(this)
        val p = page()
        if (step != null) p.addView(stepper(step), ui.lp(MATCH_PARENT).apply { bottomMargin = ui.dp(18) })
        val head = ui.row(18)
        head.addView(ui.ellipsize(ui.title(getString(R.string.dk_title), 32)), ui.lp(0, WRAP_CONTENT, 1f))
        head.addView(ui.button(getString(R.string.dk_reset), false) { app.ownyourtv.home.Dock.reset(this); dockScreen(null, step, onDone, onBack) })
        val done = ui.button(getString(if (step != null) R.string.z_next else R.string.l_done), true) { onDone() }
        head.addView(done)
        p.addView(head, ui.lp(MATCH_PARENT))
        p.addView(ui.text(getString(R.string.dk_hint), 15, Ui.GREY), ui.lp().apply { topMargin = ui.dp(8) })

        fun save(list: List<app.ownyourtv.home.Dock.Entry>, focus: String?) {
            app.ownyourtv.home.Dock.save(this, list.map { it.pkg }); dockScreen(focus, step, onDone, onBack)
        }
        fun art(e: app.ownyourtv.home.Dock.Entry) = android.widget.ImageView(this).apply {
            setImageDrawable(e.art); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = ui.rounded(Color.argb(20, 255, 255, 255), 8)
        }
        var focusView: View? = null
        val cols = ui.row(28).apply { gravity = Gravity.TOP; clipChildren = false }

        val left = ui.col(10).apply { clipChildren = false }
        left.addView(ui.chip(getString(R.string.dk_in, chosen.size)))
        chosen.forEachIndexed { i, e ->
            val r = ui.row(12).apply { setPadding(ui.dp(14), ui.dp(10), ui.dp(10), ui.dp(10)); background = ui.rounded(Ui.CARD, 14) }
            r.addView(ui.text("${i + 1}", 18, Ui.LIME, ui.display), ui.lp(ui.dp(26)))
            r.addView(art(e), ui.lp(ui.dp(64), ui.dp(36)))
            r.addView(ui.ellipsize(ui.text(e.label, 17, bold = true)), ui.lp(0, WRAP_CONTENT, 1f))
            fun small(t: String, enabled: Boolean, f: () -> Unit) = ui.text(t, 18, if (enabled) Ui.PAPER else Ui.DIM, bold = true).apply {
                gravity = Gravity.CENTER
                if (enabled) { ui.focusable(this, Color.argb(18, 255, 255, 255), Color.argb(50, 255, 255, 255), 10); setOnClickListener { f() } }
            }
            r.addView(small("↑", i > 0) { chosen.add(i - 1, chosen.removeAt(i)); save(chosen, "${e.pkg}#3") }, ui.lp(ui.dp(44), ui.dp(40)))
            r.addView(small("↓", i < chosen.size - 1) { chosen.add(i + 1, chosen.removeAt(i)); save(chosen, "${e.pkg}#4") }, ui.lp(ui.dp(44), ui.dp(40)))
            val remove = small("✕", true) { chosen.removeAt(i); save(chosen, (chosen.getOrNull(i) ?: chosen.lastOrNull())?.let { "${it.pkg}#5" }) }
            r.addView(remove, ui.lp(ui.dp(44), ui.dp(40)))
            if (focusPkg?.substringBefore('#') == e.pkg) {
                val want = r.getChildAt(focusPkg!!.substringAfter('#', "5").toIntOrNull() ?: 5)
                focusView = if (want?.isFocusable == true) want else remove
            }
            left.addView(r, ui.lp(MATCH_PARENT))
        }
        if (chosen.isEmpty()) left.addView(ui.text(getString(R.string.dk_empty), 15, Ui.GREY))

        val right = ui.col(10).apply { clipChildren = false }
        right.addView(ui.chip(getString(R.string.dk_add)))
        val inDock = chosen.map { it.pkg }.toSet()
        val left2 = all.filter { it.pkg !in inDock }
        left2.forEachIndexed { k, e ->
            val r = ui.row(12).apply { setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10)) }
            ui.focusable(r, radius = 14)
            r.addView(art(e), ui.lp(ui.dp(64), ui.dp(36)))
            r.addView(ui.ellipsize(ui.text(e.label, 17)), ui.lp(0, WRAP_CONTENT, 1f))
            r.addView(ui.text("+", 22, Ui.LIME, ui.display))
            val next = (left2.getOrNull(k + 1) ?: left2.getOrNull(k - 1))?.pkg
            r.setOnClickListener { if (chosen.size < 12) { chosen.add(e); save(chosen, next?.let { "add:$it" }) } }
            if (focusPkg == "add:${e.pkg}") focusView = r
            right.addView(r, ui.lp(MATCH_PARENT))
        }
        fun scroll(v: View) = ScrollView(this).apply { clipToPadding = false; clipChildren = false; setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(24)); addView(v) }
        cols.addView(scroll(left), ui.lp(0, MATCH_PARENT, 1f))
        cols.addView(scroll(right), ui.lp(0, MATCH_PARENT, 1f))
        p.addView(cols, ui.lp(MATCH_PARENT, 0, 1f).apply { topMargin = ui.dp(12) })
        show("dock", p, focusView ?: (left.getChildAt(1) as? LinearLayout)?.getChildAt(5) ?: right.getChildAt(1) ?: done, onBack)
    }

    // The assistant: one question per screen, a step bar on top, Back to the step before.
    private val STEPS = listOf(R.string.st_unlock, R.string.st_tv, R.string.st_clean, R.string.st_home, R.string.st_dock, R.string.st_go)

    /** The step bar: done steps ticked, the current one lit, the rest dim, with a progress line under them. */
    private fun stepper(current: Int): View {
        val box = ui.col(10)
        val row = ui.row(18)
        STEPS.forEachIndexed { i, id ->
            val done = i < current
            val now = i == current
            val dot = TextView(this).apply {
                text = if (done) "✓" else "${i + 1}"
                gravity = Gravity.CENTER
                setTextColor(if (done || now) Ui.INK else Ui.GREY)
                typeface = ui.display
                textSize = 13f
                background = when { done -> ui.rounded(Ui.LIME, 13); now -> ui.rounded(Color.WHITE, 13); else -> ui.rounded(Color.TRANSPARENT, 13, 2, Ui.DIM) }
            }
            row.addView(ui.row(8).apply {
                addView(dot, LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)))
                addView(ui.text(getString(id), 14, if (now) Ui.PAPER else if (done) Ui.LIME else Ui.DIM, bold = now))
            })
        }
        box.addView(row)
        val bar = FrameLayout(this).apply { background = ui.rounded(Color.argb(28, 255, 255, 255), 2) }
        bar.addView(View(this).apply { background = ui.rounded(Ui.LIME, 2); pivotX = 0f; scaleX = (current + 0.5f) / STEPS.size },
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        box.addView(bar, ui.lp(MATCH_PARENT, ui.dp(4)))
        return box
    }

    private fun wizPage(step: Int) = page().apply { addView(stepper(step), ui.lp(MATCH_PARENT).apply { bottomMargin = ui.dp(26) }) }

    /** Reloads what is on the TV off the main thread, then carries on. */
    private fun load2(then: () -> Unit) = thread { runCatching { load() }; main.post(then) }

    private fun counts(): Triple<Int, Int, Int> = Triple(
        items.count { it.kind == Kind.ADS && it.rec && !it.offNow }, items.count { it.kind == Kind.TRACKING && it.rec && !it.offNow },
        items.count { it.kind == Kind.EXTRAS && it.rec && !it.offNow })

    // Step 2: this TV.
    private fun wizTv() {
        val p = wizPage(1)
        val known = knownTv
        p.addView(ui.chip(getString(if (known != null) R.string.h_tv_known else R.string.h_tv_unknown), if (known != null) Ui.LIME else Ui.GREY))
        p.addView(ui.title(if (known != null) getString(R.string.z_tv_known) else getString(R.string.z_tv_unknown), 40), ui.lp().apply { topMargin = ui.dp(8) })
        val card = ui.col(10).apply { background = ui.rounded(Ui.CARD, 22); setPadding(ui.dp(28), ui.dp(24), ui.dp(28), ui.dp(24)) }
        card.addView(ui.text(known?.name ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", 26, bold = true))
        card.addView(ui.text("Android ${Build.VERSION.RELEASE} · ${Build.MODEL}", 15, Ui.GREY, ui.mono))
        val (a, t, e) = counts()
        card.addView(ui.text(getString(R.string.z_found, a, t, e), 18, Ui.LIME), ui.lp().apply { topMargin = ui.dp(10) })
        if (known == null) card.addView(ui.text(getString(R.string.z_tv_unknown_b), 15, Ui.GREY).apply { maxWidth = ui.dp(720) })
        p.addView(card, ui.lp(ui.dp(760)).apply { topMargin = ui.dp(24) })
        val next = ui.button(getString(R.string.z_next), true) { wizClean() }
        p.addView(next, ui.lp().apply { topMargin = ui.dp(30) })
        show("wiz", p, next)
    }

    // Step 3: recommended, or one list after the other.
    private fun wizClean() {
        val p = wizPage(2)
        p.addView(ui.title(getString(R.string.z_clean_title), 40))
        val (a, t, e) = counts()
        val r = ui.row(24).apply { clipChildren = false }
        fun choice(title: String, sub: String, lime: Boolean, go: () -> Unit) = ui.col(10).apply {
            setPadding(ui.dp(28), ui.dp(26), ui.dp(28), ui.dp(26))
            ui.focusable(this, if (lime) Color.argb(36, 198, 244, 50) else Ui.CARD, if (lime) Color.argb(70, 198, 244, 50) else Ui.CARD_ON, 22)
            addView(ui.text(title, 26, if (lime) Ui.LIME else Ui.PAPER, ui.display))
            addView(ui.text(sub, 16, Ui.GREY))
            setOnClickListener { go() }
        }
        val rec = choice(getString(R.string.z_rec), if (a + t + e == 0) getString(R.string.z_none) else getString(R.string.z_rec_b, a + t + e), true) {
            items.forEach { it.want = it.offNow || (it.rec && it.kind != Kind.APPS) }
            wantFast = true
            wizHome()
        }
        val kinds = listOf(Kind.ADS to R.string.h_ads, Kind.TRACKING to R.string.h_track, Kind.EXTRAS to R.string.h_extras, Kind.APPS to R.string.h_apps)
        fun walk(i: Int) {
            if (i >= kinds.size) { wantFast = true; wizHome(); return }
            category(kinds[i].first, getString(kinds[i].second), 2, onDone = { walk(i + 1) }, onBack = { if (i == 0) wizClean() else walk(i - 1) })
        }
        val custom = choice(getString(R.string.z_custom), getString(R.string.z_custom_b), false) { walk(0) }
        // Side by side at the height of the taller one, so the two choices line up.
        r.addView(rec, ui.lp(0, MATCH_PARENT, 1f)); r.addView(custom, ui.lp(0, MATCH_PARENT, 1f))
        p.addView(r, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(28) })
        show("wiz", p, rec) { wizTv() }
    }

    // Step 4: the home screen, with a picture of it.
    private fun wizHome() {
        val p = wizPage(3)
        val r = ui.row(36).apply { gravity = Gravity.CENTER_VERTICAL }
        val pic = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.preview_home); scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            clipToOutline = true; background = ui.rounded(Ui.CARD, 20)
        }
        r.addView(pic, ui.lp(ui.dp(456), ui.dp(256)))
        val c = ui.col(14)
        c.addView(ui.title(getString(R.string.z_home_title), 32))
        c.addView(ui.text(getString(R.string.z_home_b), 17, Ui.GREY))
        var first: View
        if (homeBlock != null) {
            wantHome = ourHome
            c.addView(ui.text(getString(R.string.z_home_blocked), 17, Ui.PAPER))
            first = ui.button(getString(R.string.z_next), true) { wizSummary() }
            c.addView(first)
        } else {
            if (wizardOwnsWifi && !ourHome) c.addView(ui.text(getString(R.string.z_home_wifi), 15, Ui.LIME))
            val btns = ui.col(12)
            first = ui.button(getString(R.string.z_home_yes), true) { wantHome = true; wizDock() }
            btns.addView(first, ui.lp(MATCH_PARENT))
            btns.addView(ui.button(getString(R.string.z_home_no), false) { wantHome = ourHome; wizSummary() }, ui.lp(MATCH_PARENT))
            c.addView(btns, ui.lp().apply { topMargin = ui.dp(8) })
        }
        r.addView(c, ui.lp(0, WRAP_CONTENT, 1f))
        p.addView(r, ui.lp(MATCH_PARENT))
        show("wiz", p, first) { wizClean() }
    }

    // Step 5: the dock.
    private fun wizDock() = dockScreen(step = 4, onDone = { wizSummary() }, onBack = { wizHome() })

    // Step 6: what is about to happen, then go.
    private fun wizSummary() {
        val p = wizPage(5)
        p.addView(ui.title(getString(R.string.z_sum_title), 44))
        val list = ui.col(14)
        fun line(t: String) = list.addView(ui.row(16).apply { addView(ui.check(true)); addView(ui.text(t, 20)) })
        val off = items.count { it.want && !it.offNow }
        if (off > 0) line(getString(R.string.z_sum_off, off))
        if (wantHome) {
            line(getString(R.string.z_sum_home))
            line(getString(R.string.z_sum_dock, app.ownyourtv.home.Dock.current(this).size))
        } else line(getString(R.string.z_sum_keep))
        if (wantFast) line(getString(R.string.z_sum_fast))
        p.addView(list, ui.lp().apply { topMargin = ui.dp(24) })
        p.addView(ui.text(getString(R.string.h_safe), 15, Ui.GREY), ui.lp().apply { topMargin = ui.dp(20) })
        val go = ui.button(getString(R.string.z_go), true) {
            prefs.edit().putBoolean(SETUP_DONE, true).apply()
            if (changes() > 0) apply() else finished(0, 0, 0)
        }
        p.addView(go, ui.lp().apply { topMargin = ui.dp(28) })
        show("wiz", p, go) { if (wantHome) wizDock() else wizHome() }
    }

    private fun kindName(k: Kind) = getString(when (k) {
        Kind.ADS -> R.string.k_ads; Kind.TRACKING -> R.string.k_tracking; Kind.EXTRAS -> R.string.k_extras; Kind.APPS -> R.string.k_apps
    })

    // 6. Applying, step by step, in plain words.
    private class Step(val label: String, val run: () -> Boolean) { var view: TextView? = null }

    private fun apply() {
        if (wantHome && !ourHome && wizardOwnsWifi) wifiStep() else runSteps(buildSteps())
    }

    /**
     * The Wi-Fi network saved while the TV was first set up belongs to the setup wizard, and Android deletes it when
     * the wizard is switched off, which the home step has to do. Joining the network again from Settings moves it
     * to the system, so the wizard can go without taking Internet with it.
     */
    private fun wifiStep(still: Boolean = false) {
        val p = page()
        p.addView(ui.title(getString(R.string.wf_title), 40))
        p.addView(ui.text(getString(R.string.wf_lead), 18, Ui.GREY).apply { maxWidth = ui.dp(760) }, ui.lp().apply { topMargin = ui.dp(12) })
        val steps = ui.col(12)
        listOf(R.string.wf_1, R.string.wf_2, R.string.wf_3).forEachIndexed { i, id ->
            steps.addView(ui.row(16).apply {
                addView(ui.text("${i + 1}", 24, Ui.LIME, ui.display))
                addView(ui.text(getString(id), 19))
            })
        }
        p.addView(steps, ui.lp().apply { topMargin = ui.dp(22) })
        if (still) p.addView(ui.text(getString(R.string.wf_still), 16, Ui.RED).apply { maxWidth = ui.dp(760) }, ui.lp().apply { topMargin = ui.dp(16) })
        val r = ui.row(16)
        val open = ui.button(getString(R.string.wf_open), false) { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        val done = ui.button(getString(R.string.wf_check), true) {
            thread {
                val owned = runCatching { tv!!.sh("dumpsys wifi | grep -o 'cname=[^ ]*'").out.contains("setupwraith") }
                    .getOrElse { tv = runCatching { Tv.connect(this) }.getOrNull(); tv?.sh("dumpsys wifi | grep -o 'cname=[^ ]*'")?.out?.contains("setupwraith") ?: true }
                main.post { wizardOwnsWifi = owned; if (owned) wifiStep(still = true) else runSteps(buildSteps()) }
            }
        }
        r.addView(open); r.addView(done)
        r.addView(ui.button(getString(R.string.wf_skip), false) { wantHome = false; runSteps(buildSteps()) })
        p.addView(r, ui.lp().apply { topMargin = ui.dp(30) })
        show("wifi", p, open)
    }

    /** The best settings in one press: verified ads, tracking and clutter off, the home screen on, faster animations. */
    private fun optimise() {
        items.forEach { it.want = it.offNow || (it.rec && it.kind != Kind.APPS) }
        wantHome = true
        wantFast = true
        if (changes() > 0) apply() else home()
    }

    private fun buildSteps(): List<Step> {
        val t = tv ?: return emptyList()
        val steps = mutableListOf<Step>()
        failedPkgs.clear()
        extraOff = 0
        homeResult = when { ourHome -> "already"; homeBlock != null -> "blocked"; else -> "kept" }
        items.filter { it.changes }.forEach { item ->
            if (item.want) steps += Step(getString(R.string.p_off, item.label)) {
                t.sh("pm disable-user --user 0 ${item.pkg}").out.contains("disabled").also { if (it) remember(OURS, item.pkg) else failedPkgs += item.pkg }
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
            // Earlier versions had a remote-key service; it is gone, so its entry in the TV's settings goes too.
            steps += Step(getString(R.string.p_perms_done)) {
                val s = t.sh("settings get secure enabled_accessibility_services").out.trim()
                val next = s.split(':').filter { it.isNotBlank() && it != "null" && it != KEY_SERVICE }.joinToString(":")
                if (next != s) t.sh(if (next.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $next")
                true
            }
            steps += Step(getString(R.string.p_home)) { makeHome(t).also { homeResult = if (it) "ok" else "refused" } }
        } else if (!wantHome && ourHome) {
            steps += Step(getString(R.string.p_restore_home)) { undoHome(t) }
        }
        if (wantFast && !fastNow) steps += Step(getString(R.string.opt_speed)) {
            // Half-length animations make menus feel quicker on a slow TV; the old values are kept to put back.
            val old = ANIM_KEYS.joinToString(",") { t.sh("settings get global $it").out.trim() }
            prefs.edit().putString(ANIM, old).apply()
            t.sh(ANIM_KEYS.joinToString("; ") { "settings put global $it 0.5" }).ok
        }
        return steps
    }

    /** the home screen's Home entry stays off until the owner picks the home screen, so the TV never asks which home to use. */
    private fun setOurHome(on: Boolean) {
        packageManager.setComponentEnabledSetting(android.content.ComponentName(this, app.ownyourtv.home.HomeActivity::class.java),
            if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP)
        getSharedPreferences(app.ownyourtv.home.HomeKeeper.PREFS, MODE_PRIVATE).edit()
            .putBoolean(app.ownyourtv.home.HomeKeeper.KEY_CHOSEN, on).apply()
        // OwnYourTV has already done what the home screen's own first-run steps would ask for.
        if (on) getSharedPreferences("horizon_onboarding", MODE_PRIVATE).edit().putBoolean("done", true).apply()
    }

    /** The original home comes back: what OwnYourTV switched off for the home screen comes back on, the home screen steps aside. */
    private fun undoHome(t: Tv): Boolean {
        prefs.getStringSet(OURS_HOME, emptySet())!!.sortedBy { if (it == WIZARD) 1 else 0 }
            .forEach { if (t.sh("pm enable $it").out.contains("enabled")) forget(OURS_HOME, it) }
        // The original home may have been switched off before OwnYourTV, by hand or by another tool: bring every
        // stock home back, so stepping aside never leaves the TV without one.
        val off = pkgs("-d")
        STOCK_HOMES.filter { it in off }.forEach { t.sh("pm enable $it") }
        setOurHome(false)
        val now = homeNow()
        if (now == HOME_ACTIVITY || now.isBlank() || now.contains("FallbackHome") || now.contains("ResolverActivity") && pkgs("-e").none { it in STOCK_HOMES }) {
            setOurHome(true)
            return false
        }
        val s = t.sh("settings get secure enabled_accessibility_services").out.trim()
        val next = s.split(':').filter { it.isNotBlank() && it != "null" && it != KEY_SERVICE }.joinToString(":")
        t.sh(if (next.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services $next")
        return true
    }

    private fun makeHome(t: Tv): Boolean {
        t.sh("cmd package set-home-activity --user 0 $HOME_ACTIVITY")
        if (homeOpens(t)) return true
        // Google TV answers Home with its own launcher, then the setup wizard's recovery screen, then a sideloaded
        // home. The wizard goes first: with the launcher off and the wizard on, the TV lands on that recovery
        // screen, which crash-loops on some sets and can even factory-reset one on reboot.
        val enabled = pkgs("-e")
        val offNow = mutableListOf<String>()
        if (WIZARD in enabled) {
            if (t.sh("dumpsys wifi | grep -o 'cname=[^ ]*'").out.contains("setupwraith")) return false
            if (t.sh("pm disable-user --user 0 $WIZARD").out.contains("disabled")) offNow += WIZARD
        }
        (STOCK_HOMES + listOfNotNull(knownTv?.home)).distinct().filter { it in enabled }
            .forEach { if (t.sh("pm disable-user --user 0 $it").out.contains("disabled")) offNow += it }
        t.sh("cmd package set-home-activity --user 0 $HOME_ACTIVITY")
        if (homeOpens(t)) {
            offNow.forEach { remember(OURS_HOME, it) }
            return true
        }
        // Not taken: the stock homes come back first, then the wizard, so the TV never sits on its recovery screen.
        offNow.sortedBy { if (it == WIZARD) 1 else 0 }.forEach { t.sh("pm enable $it") }
        setOurHome(false)
        homeRefused = true
        t.sh("input keyevent HOME")
        return false
    }

    /** The TV both picks the OwnYourTV home and really shows it when asked for the home. */
    private fun homeOpens(t: Tv): Boolean {
        if (homeNow() != HOME_ACTIVITY) return false
        val shown = t.sh("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME").out
        Thread.sleep(800)
        val top = t.sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").out
        // Back to the setup screens, which are still running the steps (the shell may start an activity at any time).
        t.sh("am start -n $packageName/.MainActivity -f 0x20000000")
        return shown.contains("HomeActivity") && top.contains("app.ownyourtv/.home.HomeActivity")
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
            val off = items.count { it.want && !it.offNow } + extraOff
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
        if (homeRefused) p.addView(ui.text(getString(R.string.f_home_refused), 16, Ui.GREY).apply { maxWidth = ui.dp(700) }, ui.lp().apply { topMargin = ui.dp(8) })
        p.addView(shareCard(off, on, failed), ui.lp().apply { topMargin = ui.dp(28) })
        val lock = ui.button(getString(R.string.f_close), true) {
            thread { runCatching { tv?.sh("settings put global adb_enabled 0") }; main.post { goHome() } }
        }
        p.addView(lock, ui.lp().apply { topMargin = ui.dp(28) })
        p.addView(ui.text(getString(R.string.f_close_b), 14, Ui.GREY), ui.lp().apply { topMargin = ui.dp(8) })
        p.addView(ui.button(getString(R.string.f_keep), false) { goHome() }, ui.lp().apply { topMargin = ui.dp(22) })
        show("done", p, lock)
    }

    /** Opt-in: the owner may send which TV this is and what worked, anonymously, to grow the TV database. */
    private fun shareCard(off: Int, on: Int, failed: Int): View {
        val r = Report.build(packageManager.getPackageInfo(packageName, 0).versionName ?: "?", knownTv?.id, homeResult, wizardOwnsWifi, off, on, failed, failedPkgs)
        val card = ui.row(24).apply {
            setPadding(ui.dp(24), ui.dp(18), ui.dp(24), ui.dp(18)); background = ui.rounded(Ui.CARD, 20)
        }
        val c = ui.col(6)
        c.addView(ui.text(getString(R.string.sh_title), 19, Ui.PAPER, bold = true))
        c.addView(ui.text(getString(R.string.sh_body), 15, Ui.GREY).apply { maxWidth = ui.dp(620) })
        c.addView(ui.text(Report.preview(r), 13, Ui.DIM, ui.mono))
        card.addView(c, ui.lp(0, WRAP_CONTENT, 1f))
        val btn = ui.button(getString(R.string.sh_go), false) {}
        var busy = false
        btn.setOnClickListener {
            if (busy || prefs.getBoolean(SHARED + r.getString("app"), false)) return@setOnClickListener
            busy = true
            btn.text = getString(R.string.sh_sending)
            thread {
                val ok = Report.send(r)
                if (ok) prefs.edit().putBoolean(SHARED + r.getString("app"), true).apply()
                main.post { busy = false; btn.text = getString(if (ok) R.string.sh_done else R.string.sh_fail) }
            }
        }
        if (prefs.getBoolean(SHARED + r.getString("app"), false)) btn.text = getString(R.string.sh_done)
        card.addView(btn)
        return card
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
            prefs.getString(ANIM, null)?.let { old -> steps += Step(getString(R.string.opt_speed_back)) {
                val cmds = ANIM_KEYS.zip(old.split(",")).joinToString("; ") { (k, v) ->
                    if (v.isEmpty() || v == "null") "settings delete global $k" else "settings put global $k $v" }
                t.sh(cmds).ok.also { if (it) prefs.edit().remove(ANIM).apply() }
            } }
            items.forEach { if (it.pkg in prefs.getStringSet(OURS, emptySet())!!) it.want = false }
            runSteps(steps)
        })
        p.addView(r, ui.lp().apply { topMargin = ui.dp(30) })
        show("restore", p, cancel)
    }

    companion object {
        const val HOME_ACTIVITY = "app.ownyourtv/.home.HomeActivity"
        /** The remote-key service of versions before 1.2, cleaned out of the TV's settings. */
        const val KEY_SERVICE = "app.ownyourtv/.home.HomeKeyService"
        const val OURS = "ours"
        const val OURS_HOME = "ours_home"
        const val ANIM = "anim_before"
        const val SHARED = "shared_"
        const val SETUP_DONE = "setup_done"
        const val EXTRA_HOME = "computer_home"
        /** From the profiles screen: connect once so the child guard can switch itself on. */
        const val EXTRA_GUARD = "guard"
        const val EXTRA_HOME_ON = "on"
        val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        const val WIZARD = "com.google.android.tungsten.setupwraith"
        val STOCK_HOMES = listOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher")
    }
}
