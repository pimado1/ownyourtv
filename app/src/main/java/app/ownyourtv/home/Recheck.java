package app.ownyourtv.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import app.ownyourtv.R;

/**
 * A TV update often switches the maker's apps back on. This finds the apps OwnYourTV switched off that are on
 * again, so the home screen and the app can offer to switch them off once more.
 */
public final class Recheck {
    /** MainActivity's own preferences, where it records what it switched off. */
    private static final String OWN = "own";
    private static final String OURS = "ours";
    private static final String OURS_HOME = "ours_home";
    private static final String STATE = "recheck";
    private static final String CHANNEL = "recheck";
    public static final String EXTRA = "recheck";

    private Recheck() {}

    public static List<String> cameBack(Context ctx) {
        SharedPreferences own = ctx.getSharedPreferences(OWN, Context.MODE_PRIVATE);
        Set<String> all = new TreeSet<>(own.getStringSet(OURS, Collections.emptySet()));
        all.addAll(own.getStringSet(OURS_HOME, Collections.emptySet()));
        PackageManager pm = ctx.getPackageManager();
        List<String> out = new ArrayList<>();
        for (String pkg : all) {
            try {
                if (pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled) out.add(pkg);
            } catch (PackageManager.NameNotFoundException gone) {
                // Uninstalled by the update: nothing to switch off.
            }
        }
        return out;
    }

    /** Asked once for a given set of apps: "later" is not asked again until something else comes back. */
    public static boolean shouldAsk(Context ctx, List<String> back) {
        return !back.isEmpty() && !String.join(",", back).equals(state(ctx).getString("later", ""));
    }

    public static void later(Context ctx, List<String> back) {
        state(ctx).edit().putString("later", String.join(",", back)).apply();
    }

    /** The owner keeps them on: OwnYourTV forgets it ever switched them off. */
    public static void keep(Context ctx, List<String> back) {
        SharedPreferences own = ctx.getSharedPreferences(OWN, Context.MODE_PRIVATE);
        Set<String> ours = new HashSet<>(own.getStringSet(OURS, Collections.emptySet()));
        Set<String> home = new HashSet<>(own.getStringSet(OURS_HOME, Collections.emptySet()));
        ours.removeAll(back);
        home.removeAll(back);
        own.edit().putStringSet(OURS, ours).putStringSet(OURS_HOME, home).apply();
    }

    public static String labels(Context ctx, List<String> pkgs, int max) {
        PackageManager pm = ctx.getPackageManager();
        List<String> names = new ArrayList<>();
        for (String p : pkgs) {
            if (names.size() == max) break;
            try {
                names.add(pm.getApplicationLabel(pm.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS)).toString());
            } catch (PackageManager.NameNotFoundException e) {
                names.add(p);
            }
        }
        String s = String.join(", ", names);
        return pkgs.size() > max ? s + "…" : s;
    }

    /** The TV's software as OwnYourTV last saw it, so the next boot can tell whether it was updated. */
    public static void remember(Context ctx) {
        SharedPreferences st = state(ctx);
        if (!st.contains("build")) st.edit().putString("build", Build.FINGERPRINT).apply();
    }

    /** At boot: the TV's software changed since last time, and some apps came back. A notice says so. */
    public static void afterBoot(Context ctx) {
        SharedPreferences st = state(ctx);
        String before = st.getString("build", null);
        st.edit().putString("build", Build.FINGERPRINT).apply();
        if (before == null || before.equals(Build.FINGERPRINT)) return;
        List<String> back = cameBack(ctx);
        if (!shouldAsk(ctx, back)) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.rc_channel), NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(ctx, app.ownyourtv.MainActivity.class).putExtra(EXTRA, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_settings)
                .setContentTitle(ctx.getString(R.string.rc_title))
                .setContentText(ctx.getResources().getQuantityString(R.plurals.rc_count, back.size(), back.size()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(1, n);
        } catch (SecurityException noPermission) {
            // Without the notification permission, the home screen and the app still ask.
        }
    }

    private static SharedPreferences state(Context ctx) {
        return ctx.getSharedPreferences(STATE, Context.MODE_PRIVATE);
    }
}
