package app.ownyourtv.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import app.ownyourtv.Report;

/**
 * The anonymous TV report, offered once more on the home screen the day after setup when the owner did not press
 * Share at the end of the assistant. Asked a single time: "No thanks" and "Later" both close it for good.
 */
public final class ShareReminder {
    private static final long DAY = 20 * 3600_000L;

    private ShareReminder() {}

    public static boolean shouldAsk(Context ctx) {
        SharedPreferences own = own(ctx);
        if (own.getBoolean("shared_any", false) || own.getBoolean("share_asked", false)) return false;
        if (own.getString("pending_report", null) == null) return false;
        return System.currentTimeMillis() - own.getLong("pending_report_at", System.currentTimeMillis()) > DAY;
    }

    public static void asked(Context ctx) {
        own(ctx).edit().putBoolean("share_asked", true).apply();
    }

    /** Sends the stored report; never on the main thread. */
    public static boolean send(Context ctx) {
        SharedPreferences own = own(ctx);
        try {
            JSONObject r = new JSONObject(own.getString("pending_report", "{}"));
            boolean ok = Report.INSTANCE.send(r);
            if (ok) own.edit().putBoolean("shared_any", true).putBoolean("shared_" + r.optString("app"), true).apply();
            return ok;
        } catch (Exception broken) {
            return false;
        }
    }

    /** The line that would be sent, shown before the owner says yes. */
    public static String preview(Context ctx) {
        try {
            return Report.INSTANCE.preview(new JSONObject(own(ctx).getString("pending_report", "{}")));
        } catch (Exception broken) {
            return "";
        }
    }

    private static SharedPreferences own(Context ctx) {
        return ctx.getSharedPreferences("own", Context.MODE_PRIVATE);
    }
}
