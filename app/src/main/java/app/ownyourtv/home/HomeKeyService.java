package app.ownyourtv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The remote's buttons for the home screen: the gear button opens the quick settings, and when the TV brings its own
 * home back (Google TV keeps it on the Home button unless it is switched off) the home screen comes back in front.
 */
public class HomeKeyService extends AccessibilityService {
    /** Set by OwnYourTV once the home screen is the owner's choice of home. */
    public static final String PREFS = "horizon_settings";
    public static final String KEY_CHOSEN = "home_chosen";

    private static final Set<String> STOCK_HOMES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher", "com.amazon.tv.launcher"));

    private boolean chosen() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CHOSEN, false)
                || QuickPanelActivity.isOurHome(this);
    }

    /**
     * The home screen's Home entry is switched on by the app itself (the shell may not change another app's
     * components), so it is switched back on here whenever the owner chose it: after an update that renamed it,
     * or anything else that left it off, the TV keeps a home.
     */
    public static void keepHomeOn(android.content.Context ctx) {
        if (!ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CHOSEN, false)) return;
        android.content.ComponentName home = new android.content.ComponentName(ctx, HomeActivity.class);
        android.content.pm.PackageManager pm = ctx.getPackageManager();
        if (pm.getComponentEnabledSetting(home) != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(home, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
        }
    }

    @Override
    protected void onServiceConnected() {
        keepHomeOn(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_SETTINGS && code != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (!chosen()) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            Class<?> target = code == KeyEvent.KEYCODE_SETTINGS ? QuickPanelActivity.class : HomeActivity.class;
            startActivity(new Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        if (!STOCK_HOMES.contains(event.getPackageName().toString()) || !chosen()) return;
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override
    public void onInterrupt() {
    }
}
