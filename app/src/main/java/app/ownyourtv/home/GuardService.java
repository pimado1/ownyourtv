package app.ownyourtv.home;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

import app.ownyourtv.R;

/**
 * Keeps a child to their profile while it is in use: every second and a half it reads which app is in front (the
 * usage access Android lets OwnYourTV have), sends the child back to their home screen from an app that is not
 * theirs, counts their minutes, shows the goodnight screen at bedtime and holds the volume under the ceiling. It
 * never sees what is inside an app. Runs only while a child's profile is in use.
 */
public final class GuardService extends Service {
    private static final String CHANNEL = "guard";
    private static final long EVERY_MS = 1500;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> always = new HashSet<>();
    private long lastMinute = System.currentTimeMillis();
    private String lastApp = "";

    private final Runnable look = new Runnable() {
        @Override
        public void run() {
            if (!check()) {
                stopSelf();
                return;
            }
            main.postDelayed(this, EVERY_MS);
        }
    };

    /** Starts the guard when a child's profile is in use, stops it otherwise. Called from the home screen. */
    public static void sync(Context ctx) {
        Intent i = new Intent(ctx, GuardService.class);
        if (Profiles.kidNow(ctx) && allowed(ctx)) {
            try {
                ctx.startForegroundService(i);
            } catch (RuntimeException notNow) {
                // Android refuses a start while the home screen is not in front; the next resume starts it.
            }
        } else {
            ctx.stopService(i);
        }
    }

    /** OwnYourTV may read which app is in front: granted once over ADB by the assistant. */
    public static boolean allowed(Context ctx) {
        AppOpsManager ops = ctx.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        always.add(getPackageName());
        always.add("android");
        always.add("com.android.systemui");
        // System windows an allowed app opens on its own: permission requests and Google sign-in.
        always.add("com.google.android.permissioncontroller");
        always.add("com.android.permissioncontroller");
        always.add("com.google.android.gms");
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null) for (InputMethodInfo i : im.getEnabledInputMethodList()) always.add(i.getPackageName());
        android.content.pm.ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        if (home != null && home.activityInfo != null) always.add(home.activityInfo.packageName);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.pf_guard_name), NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_settings)
                .setContentTitle(getString(R.string.pf_guard_name)).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, n);
        main.removeCallbacks(look);
        main.post(look);
        return START_STICKY;
    }

    /** One look at the TV; false once the child's profile is no longer in use. */
    private boolean check() {
        Profiles.Profile p = Profiles.current(this);
        if (p == null || !p.kid) return false;
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null || !pm.isInteractive()) return true;
        capVolume(p);
        long now = System.currentTimeMillis();
        if (now - lastMinute >= 60_000) {
            lastMinute = now;
            Profiles.addMinute(this, p);
            if (p.daily > 0 && Profiles.usedToday(this, p) == p.daily - 5)
                Toast.makeText(this, getString(R.string.pf_five_left), Toast.LENGTH_LONG).show();
        }
        String front = frontApp(now);
        if (front != null) lastApp = front;
        if (Profiles.bedtimeNow(this, p) || Profiles.timeUp(this, p)) {
            if (!getPackageName().equals(lastApp)) rest();
            return true;
        }
        if (front != null && !always.contains(front) && !p.apps.contains(front)) {
            Toast.makeText(this, getString(R.string.pf_not_yours), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    /** The app that last came to the front, from Android's usage events of the last few seconds. */
    private String frontApp(long now) {
        UsageStatsManager us = getSystemService(UsageStatsManager.class);
        if (us == null) return null;
        UsageEvents events = us.queryEvents(now - 10_000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String last = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) last = e.getPackageName();
        }
        return last;
    }

    private void rest() {
        startActivity(new Intent(this, RestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void capVolume(Profiles.Profile p) {
        if (p.maxVolume <= 0) return;
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        int cap = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * p.maxVolume / 100);
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(look);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
