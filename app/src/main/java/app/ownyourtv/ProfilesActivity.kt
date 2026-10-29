package app.ownyourtv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import app.ownyourtv.home.Dock
import app.ownyourtv.home.PinPad
import app.ownyourtv.home.Profiles

/**
 * The people who share the TV: add one, give them a colour, and for a child choose their apps, their bedtime,
 * their time per day and their volume ceiling. Behind the parent code once one is set.
 */
class ProfilesActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        root = FrameLayout(this).apply { setBackgroundColor(Ui.INK) }
        setContentView(root)
        if (Profiles.all(this).isEmpty()) {
            Profiles.save(this, listOf(Profiles.Profile().apply { id = Profiles.MAIN; name = getString(R.string.pf_me); color = Profiles.COLORS[0] }))
        }
        // No code by default: one is asked for with the first child's profile, and dropped with the last one.
        Profiles.dropPinWithoutKids(this)
        if (Profiles.hasPin(this)) {
            PinPad.show(this, PinPad.title(this, false)) { code ->
                if (Profiles.pinIs(this, code)) list() else { Toast.makeText(this, R.string.pf_pin_wrong, Toast.LENGTH_SHORT).show(); finish() }
            }.setOnCancelListener { finish() }
        } else list()
    }

    private fun page() = ui.col().apply { setPadding(ui.dp(64), ui.dp(36), ui.dp(64), ui.dp(36)); clipChildren = false; clipToPadding = false }

    private fun show(v: View, focus: View?) {
        root.removeAllViews()
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(v) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        focus?.post { focus.requestFocus() }
    }

    /** A round badge with the first letter of the name, in the profile's colour. */
    private fun bubble(name: String, color: Int, size: Int) = ui.text(name.trim().take(1).uppercase().ifEmpty { "?" }, size / 2.6f, Ui.INK, ui.display).apply {
        gravity = Gravity.CENTER
        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color) }
        layoutParams = android.widget.LinearLayout.LayoutParams(ui.dp(size), ui.dp(size))
    }

    private fun list() {
        val p = page()
        p.addView(ui.title(getString(R.string.pf_title), 40))
        p.addView(ui.text(getString(R.string.pf_lead), 18, Ui.GREY).apply { maxWidth = ui.dp(900) }, ui.lp().apply { topMargin = ui.dp(8) })
        val r = ui.row(20).apply { clipChildren = false; gravity = Gravity.TOP }
        var first: View? = null
        val inUse = Profiles.shown(this)?.id
        Profiles.all(this).forEach { pr ->
            // OK on the card: this person is watching now. Editing is the button under it.
            val holder = ui.col(10).apply { gravity = Gravity.CENTER_HORIZONTAL }
            val c = ui.col(10).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(20)) }
            ui.focusable(c, if (pr.id == inUse) Ui.CARD_ON else Ui.CARD)
            c.addView(bubble(pr.name, pr.color, 84))
            c.addView(ui.ellipsize(ui.text(pr.name, 20, bold = true)).apply { maxWidth = ui.dp(170); gravity = Gravity.CENTER })
            c.addView(ui.chip(if (pr.id == inUse) getString(R.string.pf_in_use) else getString(if (pr.kid) R.string.pf_kid else R.string.pf_adult),
                if (pr.id == inUse || pr.kid) Ui.LIME else Ui.GREY).apply { gravity = Gravity.CENTER })
            c.setOnClickListener { Profiles.use(this, pr.id); finish() }
            holder.addView(c, ui.lp(ui.dp(210), ui.dp(210)))
            holder.addView(ui.button(getString(R.string.pf_edit_short), false) { edit(pr, false) }, ui.lp(ui.dp(210)))
            r.addView(holder)
            if (first == null || pr.id == inUse) first = c
        }
        if (Profiles.all(this).size < 6) {
            val add = ui.col(10).apply { gravity = Gravity.CENTER; setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(20)) }
            ui.focusable(add)
            add.addView(ui.text("+", 52, Ui.LIME, ui.display).apply { gravity = Gravity.CENTER })
            add.addView(ui.text(getString(R.string.pf_add), 18, bold = true).apply { gravity = Gravity.CENTER })
            add.setOnClickListener {
                val used = Profiles.all(this).map { it.color }
                edit(Profiles.Profile().apply { id = Profiles.newId(); name = ""; color = Profiles.COLORS.firstOrNull { it !in used } ?: Profiles.COLORS[1] }, true)
            }
            r.addView(add, ui.lp(ui.dp(210), ui.dp(210)).apply { gravity = Gravity.TOP })
        }
        p.addView(r, ui.lp().apply { topMargin = ui.dp(30) })
        val actions = ui.row(16)
        if (Profiles.hasPin(this)) actions.addView(ui.button(getString(R.string.pf_pin_change), false) { setPin { list() } })
        actions.addView(ui.button(getString(R.string.pf_back_home), false) { finish() })
        p.addView(actions, ui.lp().apply { topMargin = ui.dp(34) })
        p.addView(ui.text(getString(R.string.pf_private), 14, Ui.GREY).apply { maxWidth = ui.dp(900) }, ui.lp().apply { topMargin = ui.dp(22) })
        show(p, first)
    }

    private fun setPin(then: () -> Unit) {
        PinPad.show(this, PinPad.title(this, true)) { a ->
            PinPad.show(this, getString(R.string.pf_pin_again)) { b ->
                if (a == b) { Profiles.setPin(this, a); Toast.makeText(this, R.string.pf_pin_saved, Toast.LENGTH_SHORT).show(); then() }
                else Toast.makeText(this, R.string.pf_pin_mismatch, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun edit(pr: Profiles.Profile, isNew: Boolean) {
        val p = page()
        p.addView(ui.title(getString(if (isNew) R.string.pf_add else R.string.pf_edit), 36))
        val name = EditText(this).apply {
            setText(pr.name); setSelection(text.length)
            hint = getString(R.string.pf_name_hint); setHintTextColor(Ui.DIM); setTextColor(Ui.PAPER); textSize = 22f
            isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14))
            ui.focusable(this, Ui.CARD, Ui.CARD_ON, 14)
            filters = arrayOf(android.text.InputFilter.LengthFilter(16))
        }
        p.addView(name, ui.lp(ui.dp(520)).apply { topMargin = ui.dp(22) })

        // Colour.
        p.addView(ui.chip(getString(R.string.pf_color), Ui.GREY), ui.lp().apply { topMargin = ui.dp(24) })
        val colors = ui.row(14).apply { clipChildren = false }
        val dots = Profiles.COLORS.map { col ->
            FrameLayout(this).apply {
                isFocusable = true; isClickable = true
                val ring = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(ui.dp(3), Color.WHITE) })
                    addState(intArrayOf(), android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.TRANSPARENT) })
                }
                background = ring
                val d = View(context).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(col) } }
                addView(d, FrameLayout.LayoutParams(ui.dp(40), ui.dp(40), Gravity.CENTER))
                tag = col
            }
        }
        fun paintDots() = dots.forEach { (it.getChildAt(0)).alpha = if (it.tag == pr.color) 1f else 0.35f }
        dots.forEach { d -> d.setOnClickListener { pr.color = d.tag as Int; paintDots() }; colors.addView(d, ui.lp(ui.dp(56), ui.dp(56))) }
        paintDots()
        p.addView(colors, ui.lp().apply { topMargin = ui.dp(10) })

        // Adult or child.
        p.addView(ui.chip(getString(R.string.pf_who), Ui.GREY), ui.lp().apply { topMargin = ui.dp(24) })
        val kind = ui.row(14)
        kind.addView(ui.button(getString(R.string.pf_adult_long), !pr.kid) { pr.name = name.text.toString(); pr.kid = false; edit(pr, isNew) })
        val kidBtn = ui.button(getString(R.string.pf_kid_long), pr.kid) {
            pr.name = name.text.toString(); pr.kid = true
            if (pr.apps.isEmpty()) pr.apps.addAll(Dock.current(this).map { it.pkg })
            edit(pr, isNew)
        }
        kind.addView(kidBtn)
        p.addView(kind, ui.lp().apply { topMargin = ui.dp(10) })

        if (pr.kid) {
            fun choices(label: Int, options: List<Pair<Int, String>>, now: () -> Int, set: (Int) -> Unit) {
                p.addView(ui.chip(getString(label), Ui.GREY), ui.lp().apply { topMargin = ui.dp(24) })
                val r = ui.row(10)
                val buttons = mutableListOf<android.widget.TextView>()
                fun paint() = buttons.forEachIndexed { i, b -> b.setTextColor(if (options[i].first == now()) Ui.INK else Ui.PAPER); b.isSelected = options[i].first == now()
                    b.background = android.graphics.drawable.StateListDrawable().apply {
                        val on = options[i].first == now()
                        addState(intArrayOf(android.R.attr.state_focused), ui.rounded(if (on) Color.WHITE else Ui.CARD_ON, 12, 3, Ui.LIME))
                        addState(intArrayOf(), ui.rounded(if (on) Ui.LIME else Ui.CARD, 12))
                    } }
                options.forEach { (v, t) ->
                    val b = ui.text(t, 16, bold = true).apply { gravity = Gravity.CENTER; setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(10)); isFocusable = true; isClickable = true }
                    b.setOnClickListener { set(v); paint() }
                    buttons += b; r.addView(b)
                }
                paint()
                p.addView(r, ui.lp().apply { topMargin = ui.dp(10) })
            }
            fun hm(m: Int) = "%d:%02d".format(m / 60, m % 60)
            choices(R.string.pf_bedtime, listOf(-1 to getString(R.string.pf_none)) + listOf(19 * 60 + 30, 20 * 60, 20 * 60 + 30, 21 * 60, 21 * 60 + 30, 22 * 60).map { it to hm(it) },
                { pr.bedtime }, { pr.bedtime = it })
            choices(R.string.pf_daily, listOf(0 to getString(R.string.pf_unlimited)) + listOf(30, 60, 90, 120, 180).map { it to (if (it < 60) "$it min" else if (it % 60 == 0) "${it / 60} h" else "${it / 60} h ${it % 60}") },
                { pr.daily }, { pr.daily = it })
            choices(R.string.pf_volume, listOf(0 to getString(R.string.pf_no_limit)) + listOf(30, 40, 50, 60).map { it to "$it %" },
                { pr.maxVolume }, { pr.maxVolume = it })

            p.addView(ui.chip(getString(R.string.pf_apps), Ui.GREY), ui.lp().apply { topMargin = ui.dp(24) })
            val all = Dock.available(this).filter { it.pkg != packageName }
            all.chunked(3).forEach { line ->
                val r = ui.row(12)
                line.forEach { e ->
                    val c = ui.row(12).apply { setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10)) }
                    ui.focusable(c, Ui.CARD, Ui.CARD_ON, 14)
                    val box = ui.check(e.pkg in pr.apps)
                    c.addView(box)
                    c.addView(android.widget.ImageView(this).apply { setImageDrawable(e.art); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }, ui.lp(ui.dp(56), ui.dp(32)))
                    c.addView(ui.ellipsize(ui.text(e.label, 16, bold = true)), ui.lp(0, WRAP_CONTENT, 1f))
                    c.setOnClickListener {
                        if (!pr.apps.remove(e.pkg)) pr.apps.add(e.pkg)
                        val on = e.pkg in pr.apps
                        box.text = if (on) "✓" else ""
                        box.background = if (on) ui.rounded(Ui.LIME, 8) else ui.rounded(Color.TRANSPARENT, 8, 2, Ui.DIM)
                    }
                    r.addView(c, ui.lp(0, WRAP_CONTENT, 1f))
                }
                repeat(3 - line.size) { r.addView(View(this), ui.lp(0, 1, 1f)) }
                p.addView(r, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(10) })
            }
        }

        val actions = ui.row(16)
        val save = ui.button(getString(R.string.pf_save), true) {
            pr.name = name.text.toString().trim().replaceFirstChar { it.titlecase() }.ifEmpty { getString(if (pr.kid) R.string.pf_kid else R.string.pf_me) }
            store(pr)
        }
        actions.addView(save)
        if (!isNew && pr.id != Profiles.MAIN) actions.addView(ui.button(getString(R.string.pf_delete), false) {
            Profiles.save(this, Profiles.all(this).filter { it.id != pr.id })
            Profiles.dropPinWithoutKids(this)
            list()
        })
        actions.addView(ui.button(getString(R.string.cancel), false) { list() })
        p.addView(actions, ui.lp().apply { topMargin = ui.dp(34) })
        show(p, if (isNew) name else save)
    }

    /** A child's profile needs the parent code and the guard: both are asked for before it is kept. */
    private fun store(pr: Profiles.Profile) {
        if (pr.kid && !Profiles.hasPin(this)) { setPin { store(pr) }; return }
        val list = Profiles.all(this).toMutableList()
        val i = list.indexOfFirst { it.id == pr.id }
        if (i >= 0) list[i] = pr else list += pr
        Profiles.save(this, list)
        if (pr.kid && !app.ownyourtv.home.GuardService.allowed(this)) { guardNeeded(); return }
        list()
    }

    // The guard reads which app is in front with usage access, granted over ADB once: the assistant grants it.
    private fun guardNeeded() {
        val p = page().apply { gravity = Gravity.CENTER_VERTICAL }
        p.addView(ui.title(getString(R.string.pf_guard_title), 40))
        p.addView(ui.text(getString(R.string.pf_guard_b), 18, Ui.GREY).apply { maxWidth = ui.dp(900) }, ui.lp().apply { topMargin = ui.dp(12) })
        val r = ui.row(16)
        val go = ui.button(getString(R.string.pf_guard_go), true) {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_GUARD, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        r.addView(go)
        r.addView(ui.button(getString(R.string.rc_later), false) { list() })
        p.addView(r, ui.lp().apply { topMargin = ui.dp(30) })
        show(p, go)
    }
}
