package app.ownyourtv.home;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Keeps the home screen's Home entry on once the owner chose it. Android lets only the app itself switch the
 * entry on, so it is switched back on after every update and every boot: a TV whose stock home is off is never
 * left without one. At boot it also looks for apps a TV update switched back on.
 */
public class HomeKeeper extends BroadcastReceiver {
    public static final String PREFS = "horizon_settings";
    public static final String KEY_CHOSEN = "home_chosen";

    private static void goHome(Context ctx) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException refused) {
            // Android may refuse a start from the background; the TV then shows what it chooses.
        }
    }

        private static boolean isTvHome(Context ctx) {
        android.content.pm.ResolveInfo r = ctx.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && ctx.getPackageName().equals(r.activityInfo.packageName);
    }

        public static void keepHomeOn(Context ctx) {
        if (!ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CHOSEN, false)) return;
        ComponentName home = new ComponentName(ctx, HomeActivity.class);
        PackageManager pm = ctx.getPackageManager();
        if (pm.getComponentEnabledSetting(home) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(home, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        keepHomeOn(ctx);
        // An update closes the home screen with the rest of the app, and some TVs then show live TV or the last app
        // used. When the home screen is the TV's home, it is brought straight back.
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) && isTvHome(ctx)) {
            goHome(ctx);
            // On TCL sets, the AirPlay service opens live TV a couple of seconds after the home screen went away:
            // the home screen comes back once more after it.
            PendingResult pending = goAsync();
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(() -> goHome(ctx), 2500);
            h.postDelayed(() -> {
                goHome(ctx);
                pending.finish();
            }, 5000);
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Recheck.afterBoot(ctx);
            Update.dueNow(ctx);
        }
    }
}
