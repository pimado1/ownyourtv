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
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Recheck.afterBoot(ctx);
            Update.dueNow(ctx);
        }
    }
}
