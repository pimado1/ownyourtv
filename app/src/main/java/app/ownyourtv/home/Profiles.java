package app.ownyourtv.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The people who share the TV: each one has a name, a colour and a dock of their own. A child's profile also lists
 * the apps it may open, a bedtime, a time per day and a volume ceiling. Everything stays on the TV.
 */
public final class Profiles {
    private static final String PREFS = "profiles";
    /** The first profile, which keeps the dock chosen before profiles existed. */
    public static final String MAIN = "main";
    /** Bedtime ends in the morning: until then the TV stays asleep for a child. */
    private static final int WAKE_MINUTES = 7 * 60;

    public static final int[] COLORS = {0xFF7C6CFF, 0xFFFF5C8A, 0xFF34C6A0, 0xFFFFB547, 0xFF4AA8FF, 0xFFC6F432};

    private Profiles() {}

    public static final class Profile {
        public String id;
        public String name;
        public int color;
        public boolean kid;
        public Set<String> apps = new LinkedHashSet<>();
        /** Minutes after midnight, or -1 for none. */
        public int bedtime = -1;
        /** Minutes a day, or 0 for no limit. */
        public int daily;
        /** Percent of the volume, or 0 for no ceiling. */
        public int maxVolume;

        JSONObject json() throws JSONException {
            JSONObject o = new JSONObject().put("id", id).put("name", name).put("color", color).put("kid", kid)
                    .put("bedtime", bedtime).put("daily", daily).put("maxVolume", maxVolume);
            o.put("apps", new JSONArray(apps));
            return o;
        }

        static Profile from(JSONObject o) {
            Profile p = new Profile();
            p.id = o.optString("id", MAIN);
            p.name = o.optString("name", "");
            p.color = o.optInt("color", COLORS[0]);
            p.kid = o.optBoolean("kid", false);
            JSONArray a = o.optJSONArray("apps");
            for (int i = 0; a != null && i < a.length(); i++) p.apps.add(a.optString(i));
            p.bedtime = o.optInt("bedtime", -1);
            p.daily = o.optInt("daily", 0);
            p.maxVolume = o.optInt("maxVolume", 0);
            return p;
        }
    }

    public static List<Profile> all(Context ctx) {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Profile.from(a.getJSONObject(i)));
        } catch (JSONException broken) {
            // A damaged list is read as no profiles: the TV works as before.
        }
        return out;
    }

    /** Profiles are on once a second person was added; with one profile the TV works as it always did. */
    public static boolean on(Context ctx) {
        return all(ctx).size() > 1;
    }

    public static void save(Context ctx, List<Profile> list) {
        JSONArray a = new JSONArray();
        try {
            for (Profile p : list) a.put(p.json());
        } catch (JSONException impossible) {
            return;
        }
        prefs(ctx).edit().putString("list", a.toString()).apply();
    }

    /** The profile in use, or null when profiles are off. */
    public static Profile current(Context ctx) {
        List<Profile> list = all(ctx);
        if (list.size() < 2) return null;
        String id = prefs(ctx).getString("current", MAIN);
        for (Profile p : list) if (p.id.equals(id)) return p;
        return list.get(0);
    }

    /** The profile to show on the home screen's pill: the one in use, even when it is the only one. */
    public static Profile shown(Context ctx) {
        List<Profile> list = all(ctx);
        if (list.isEmpty()) return null;
        String id = prefs(ctx).getString("current", MAIN);
        for (Profile p : list) if (p.id.equals(id)) return p;
        return list.get(0);
    }

    public static void use(Context ctx, String id) {
        prefs(ctx).edit().putString("current", id).apply();
    }

    public static boolean kidNow(Context ctx) {
        Profile p = current(ctx);
        return p != null && p.kid;
    }

    /** The key the current profile's dock is kept under: the first profile keeps the dock from before. */
    static String dockKey(Context ctx) {
        Profile p = current(ctx);
        return p == null || MAIN.equals(p.id) ? "apps" : "apps_" + p.id;
    }

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36);
    }

    // ---- the parent code -------------------------------------------------------------------------

    public static boolean hasPin(Context ctx) {
        return !prefs(ctx).getString("pin", "").isEmpty();
    }

    public static boolean pinIs(Context ctx, String pin) {
        return hasPin(ctx) && prefs(ctx).getString("pin", "").equals(pin);
    }

    public static void setPin(Context ctx, String pin) {
        prefs(ctx).edit().putString("pin", pin).apply();
    }

    /** The code only guards children: with no child profile left, there is no code at all. */
    public static void dropPinWithoutKids(Context ctx) {
        for (Profile p : all(ctx)) if (p.kid) return;
        prefs(ctx).edit().remove("pin").apply();
    }

    // ---- a child's day ---------------------------------------------------------------------------

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    public static int usedToday(Context ctx, Profile p) {
        return prefs(ctx).getInt("used_" + p.id + "_" + today(), 0);
    }

    static void addMinute(Context ctx, Profile p) {
        String key = "used_" + p.id + "_" + today();
        SharedPreferences.Editor e = prefs(ctx).edit().putInt(key, prefs(ctx).getInt(key, 0) + 1);
        // Yesterday's counts are no longer needed.
        for (String k : prefs(ctx).getAll().keySet()) if (k.startsWith("used_") && !k.endsWith(today())) e.remove(k);
        e.apply();
    }

    /** "Ten more minutes" from the parent: given back for today only. */
    public static void extend(Context ctx, Profile p, int minutes) {
        String key = "used_" + p.id + "_" + today();
        prefs(ctx).edit().putInt(key, Math.max(0, prefs(ctx).getInt(key, 0) - minutes)).apply();
    }

    public static boolean timeUp(Context ctx, Profile p) {
        return p.daily > 0 && usedToday(ctx, p) >= p.daily;
    }

    /** From bedtime until seven in the morning, unless the parent opened the evening up. */
    public static boolean bedtimeNow(Context ctx, Profile p) {
        if (p.bedtime < 0) return false;
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        boolean late = p.bedtime > WAKE_MINUTES ? now >= p.bedtime || now < WAKE_MINUTES : now >= p.bedtime && now < WAKE_MINUTES;
        return late && !prefs(ctx).getString("awake_" + p.id, "").equals(today() + (now < WAKE_MINUTES ? "n" : ""));
    }

    /** The parent let the child stay up tonight. */
    public static void stayUp(Context ctx, Profile p) {
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        prefs(ctx).edit().putString("awake_" + p.id, today() + (now < WAKE_MINUTES ? "n" : "")).apply();
    }

    // ---- the guard ---------------------------------------------------------------------------------

    /** The accessibility service that keeps a child to their apps. */
    public static ComponentName guard(Context ctx) {
        return new ComponentName(ctx, GuardService.class);
    }

    public static boolean guardOn(Context ctx) {
        String on = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(guard(ctx).flattenToString());
    }

    /** Switches the guard on by itself: OwnYourTV was given the right to change secure settings over ADB. */
    public static boolean enableGuard(Context ctx) {
        if (guardOn(ctx)) return true;
        try {
            String on = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String me = guard(ctx).flattenToString();
            String next = on == null || on.isEmpty() || "null".equals(on) ? me : on + ":" + me;
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return guardOn(ctx);
        } catch (SecurityException notGranted) {
            return false;
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
