package app.ownyourtv.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Horizon's settings, kept on the TV: an optional JSON overlay (dock, hidden apps, weather place) and the sky. */
final class HomePrefs {
    private static final String PREFS = "horizon_settings";

    private HomePrefs() {
    }

    /** The overlay OwnYourTV may write for Horizon, or null when there is none. */
    static JSONObject overlay(Context ctx) {
        String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("overlay", null);
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /** The night sky at any hour (the default), or the sky that follows the sun. */
    static boolean alwaysNight(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("always_night", true);
    }

    static void setAlwaysNight(Context ctx, boolean on) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putBoolean("always_night", on).apply();
    }
}
