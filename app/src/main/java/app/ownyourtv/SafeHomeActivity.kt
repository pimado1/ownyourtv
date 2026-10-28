package app.ownyourtv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ScrollView
import app.ownyourtv.home.SafeMode

/**
 * The backup home: when the OwnYourTV home keeps crashing, the TV lands here instead of on a black screen. Plain
 * on purpose, so nothing in it can fail: the apps, a way to try the home again and a way back to the original one.
 */
class SafeHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        val p = ui.col().apply { setPadding(ui.dp(64), ui.dp(40), ui.dp(64), ui.dp(40)); setBackgroundColor(Ui.INK) }
        p.addView(ui.title(getString(R.string.sh_home_title), 40))
        p.addView(ui.text(getString(R.string.sh_home_lead), 18, Ui.GREY).apply { maxWidth = ui.dp(900) }, ui.lp().apply { topMargin = ui.dp(10) })
        val actions = ui.row(16)
        val retry = ui.button(getString(R.string.sh_home_retry), true) {
            SafeMode.reset(this)
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        actions.addView(retry)
        actions.addView(ui.button(getString(R.string.sh_home_restore), false) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        actions.addView(ui.button(getString(R.string.sh_home_settings), false) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        p.addView(actions, ui.lp().apply { topMargin = ui.dp(28) })

        // TV apps first; apps with only a phone launcher entry still count, so none is missing.
        val apps = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        p.addView(ui.chip(getString(R.string.sh_home_apps), Ui.GREY), ui.lp().apply { topMargin = ui.dp(36) })
        apps.chunked(4).forEach { line ->
            val r = ui.row(14)
            line.forEach { app ->
                val label = app.loadLabel(packageManager).toString()
                r.addView(ui.ellipsize(ui.button(label, false) {
                    val pkg = app.activityInfo.packageName
                    (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
                        ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }), ui.lp(0, weight = 1f))
            }
            repeat(4 - line.size) { r.addView(android.view.View(this), ui.lp(0, weight = 1f)) }
            p.addView(r, ui.lp(MATCH_PARENT).apply { topMargin = ui.dp(14) })
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.INK); addView(p) })
        retry.post { retry.requestFocus() }
    }

    // This is the home: Back has nowhere to go.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}
