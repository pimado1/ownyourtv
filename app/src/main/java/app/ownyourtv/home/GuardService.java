package app.ownyourtv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

import app.ownyourtv.R;

/**
 * Keeps a child to their profile: an app that is not theirs, opened from the settings or by voice, sends them back to
 * their home screen; it also counts their minutes, puts the TV to bed at bedtime and holds the volume under the
 * ceiling. It reads which app is on screen, never what is in it. Idle while an adult profile is in use.
 */
public final class GuardService extends AccessibilityService {
    private static GuardService running;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> always = new HashSet<>();
    private String lastApp = "";

    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            tick();
            main.postDelayed(this, 60_000);
        }
    };

    private final BroadcastReceiver volume = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
            capVolume();
        }
    };

    @Override
    protected void onServiceConnected() {
        running = this;
        always.add(getPackageName());
        always.add("android");
        always.add("com.android.systemui");
        // System windows an allowed app opens on its own: permission requests and Google sign-in.
        always.add("com.google.android.permissioncontroller");
        always.add("com.android.permissioncontroller");
        always.add("com.google.android.gms");
        // The TV's home, in case it is not OwnYourTV's: sending the child back there must not count as leaving.
        android.content.pm.ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        if (home != null && home.activityInfo != null) always.add(home.activityInfo.packageName);
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null) for (InputMethodInfo i : im.getEnabledInputMethodList()) always.add(i.getPackageName());
        registerReceiver(volume, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        main.postDelayed(minute, 60_000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        Profiles.Profile p = Profiles.current(this);
        if (p == null || !p.kid || pkg.equals(lastApp)) {
            lastApp = pkg;
            return;
        }
        lastApp = pkg;
        if (always.contains(pkg) || p.apps.contains(pkg)) {
            if (Profiles.bedtimeNow(this, p) || Profiles.timeUp(this, p)) rest();
            return;
        }
        Toast.makeText(this, getString(R.string.pf_not_yours), Toast.LENGTH_SHORT).show();
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void tick() {
        Profiles.Profile p = Profiles.current(this);
        PowerManager pm = getSystemService(PowerManager.class);
        if (p == null || !p.kid || pm == null || !pm.isInteractive()) return;
        Profiles.addMinute(this, p);
        capVolume();
        if (p.daily > 0 && Profiles.usedToday(this, p) == p.daily - 5)
            Toast.makeText(this, getString(R.string.pf_five_left), Toast.LENGTH_LONG).show();
        if (Profiles.bedtimeNow(this, p) || Profiles.timeUp(this, p)) rest();
    }

    private void rest() {
        startActivity(new Intent(this, RestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void capVolume() {
        Profiles.Profile p = Profiles.current(this);
        if (p == null || !p.kid || p.maxVolume <= 0) return;
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        int cap = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * p.maxVolume / 100);
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
    }

    /** Puts the TV to sleep at bedtime; false when the guard is off or the TV ignores it. */
    static boolean sleep() {
        GuardService g = running;
        return g != null && android.os.Build.VERSION.SDK_INT >= 28 && g.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        running = null;
        main.removeCallbacks(minute);
        try {
            unregisterReceiver(volume);
        } catch (IllegalArgumentException alreadyGone) {
            // Not registered: the service never connected.
        }
        super.onDestroy();
    }
}
