package app.ownyourtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/**
 * The home screen's safety net. Each start is counted and cleared once the home screen is really on screen; three
 * starts in a row that never got there within two minutes mean it is crashing, and a plain backup home takes over, so the
 * TV is never stuck on a black screen. A new version of the app gives the home screen another chance.
 */
public final class SafeMode {
    private static final String PREFS = "safe_mode";
    private static final long WINDOW = 120_000;
    private static final int LIMIT = 3;

    private SafeMode() {}

    public static boolean on(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.getLong("version", -1) != version(ctx)) {
            p.edit().clear().putLong("version", version(ctx)).apply();
            return false;
        }
        return p.getBoolean("on", false);
    }

    public static void starting(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        boolean fresh = now - p.getLong("since", 0) > WINDOW;
        int starts = (fresh ? 0 : p.getInt("starts", 0)) + 1;
        SharedPreferences.Editor e = p.edit().putInt("starts", starts);
        if (fresh) e.putLong("since", now);
        if (starts >= LIMIT) e.putBoolean("on", true);
        e.commit();
    }

    public static void shown(Context ctx) {
        prefs(ctx).edit().putInt("starts", 0).putBoolean("on", false).apply();
    }

    /** "Try the new home again" on the backup home. */
    public static void reset(Context ctx) {
        prefs(ctx).edit().putBoolean("on", false).putInt("starts", 0).apply();
    }

    private static long version(Context ctx) {
        try {
            android.content.pm.PackageInfo i = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28 ? i.getLongVersionCode() : i.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
