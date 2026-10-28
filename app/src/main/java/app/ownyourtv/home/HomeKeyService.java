package app.ownyourtv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The remote's buttons for Horizon: the gear button opens the quick settings, and when the TV brings its own
 * home back (Google TV keeps it on the Home button unless it is switched off) Horizon comes back in front.
 */
public class HomeKeyService extends AccessibilityService {
    /** Set by OwnYourTV once Horizon is the owner's choice of home. */
    public static final String PREFS = "horizon_settings";
    public static final String KEY_CHOSEN = "home_chosen";

    private static final Set<String> STOCK_HOMES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher", "com.amazon.tv.launcher"));

    private boolean chosen() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CHOSEN, false)
                || HorizonPanelActivity.horizonIsHome(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_SETTINGS && code != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (!chosen()) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            Class<?> target = code == KeyEvent.KEYCODE_SETTINGS ? HorizonPanelActivity.class : HorizonActivity.class;
            startActivity(new Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        if (!STOCK_HOMES.contains(event.getPackageName().toString()) || !chosen()) return;
        startActivity(new Intent(this, HorizonActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override
    public void onInterrupt() {
    }
}
