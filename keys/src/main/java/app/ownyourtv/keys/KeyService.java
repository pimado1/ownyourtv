package app.ownyourtv.keys;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The remote's app and gear buttons. On Google TV the system hands them to Google's own home screen; once that
 * is switched off for OwnYourTV's, nothing answers them. This service takes only those buttons, never the
 * volume, the arrows or anything else, and opens what the button says.
 */
public class KeyService extends AccessibilityService {
    private static final String TAG = "OwnYourTVKeys";

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        String target = target(e.getKeyCode());
        Log.i(TAG, "key " + e.getKeyCode() + " scan " + e.getScanCode() + " action " + e.getAction() + (target == null ? " passed" : " -> " + target));
        if (target == null) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) open(target);
        return true;
    }

    /** What a button opens: a package, or "settings" for the gear. Null leaves the key to Android. */
    static String target(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_NOTIFICATION: // the gear on TCL remotes (BT_RC933)
                return "settings";
            case KeyEvent.KEYCODE_BUTTON_3: // YouTube on TCL remotes
                return "com.google.android.youtube.tv";
            case 4062: // Netflix on TCL remotes
                return "com.netflix.ninja";
            case 4099: // Prime Video on TCL remotes
                return "com.amazon.amazonvideo.livingroom";
            default:
                return null;
        }
    }

    private void open(String target) {
        Intent i;
        if ("settings".equals(target)) {
            i = new Intent().setComponent(new ComponentName("app.ownyourtv", "app.ownyourtv.home.QuickPanelActivity"));
            if (getPackageManager().resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_SETTINGS);
        } else {
            i = getPackageManager().getLeanbackLaunchIntentForPackage(target);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(target);
            if (i == null) return;
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ex) {
            Log.w(TAG, "could not open " + target, ex);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
