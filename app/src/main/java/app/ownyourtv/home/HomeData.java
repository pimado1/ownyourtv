package app.ownyourtv.home;

import app.ownyourtv.R;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** What the the home screen home shows besides the weather; every read here is cheap or runs off the main thread. */
final class HomeData {
    private static final String TAG = "HomeData";
    static final String[] DEFAULT_DOCK = {"com.google.android.youtube.tv", "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom"};

    private HomeData() {
    }

    static final class App {
        String pkg;
        String label;
        Drawable banner;
        int color;
        Intent launch;
        /** Part of the TV's system image: Android only lets it be switched off, not uninstalled. */
        boolean system;
    }

    /**
     * A phone app with no TV banner: its icon whole, centred on a card tinted with its own colour, instead of a
     * round icon cropped into a wide card.
     */
    static final class IconBanner extends Drawable {
        private final Drawable icon;
        private final int color;
        private final android.graphics.Paint fill = new android.graphics.Paint();

        IconBanner(Drawable icon, int color) {
            this.icon = icon;
            this.color = color;
            fill.setColor(SkyScene.lerpColor(0xFF16182E, color, 0.3f));
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.drawRect(b, fill);
            int side = Math.round(b.height() * 0.58f);
            icon.setBounds(b.centerX() - side / 2, b.centerY() - side / 2, b.centerX() + side / 2, b.centerY() + side / 2);
            icon.draw(c);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            icon.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter filter) {
            fill.setColorFilter(filter);
            icon.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }

        @Override
        public ConstantState getConstantState() {
            return new ConstantState() {
                @Override
                public Drawable newDrawable() {
                    ConstantState s = icon.getConstantState();
                    return new IconBanner(s != null ? s.newDrawable().mutate() : icon, color);
                }

                @Override
                public int getChangingConfigurations() {
                    return 0;
                }
            };
        }
    }

    /** The overlay's dock_apps, falling back to the three big streaming apps. */
    private static final String DOCK_PREFS = "horizon_dock";

    /** The dock chosen on the TV (hold OK on an app), which wins over the overlay's dock_apps. */
    static void saveDock(Context ctx, List<String> pkgs) {
        ctx.getSharedPreferences(DOCK_PREFS, Context.MODE_PRIVATE).edit()
                .putString("apps", android.text.TextUtils.join(",", pkgs)).apply();
    }

    /** Forgets the dock chosen on the TV: the default dock applies again. */
    static void resetDock(Context ctx) {
        ctx.getSharedPreferences(DOCK_PREFS, Context.MODE_PRIVATE).edit().remove("apps").apply();
    }

    static boolean dockChosen(Context ctx) {
        return ctx.getSharedPreferences(DOCK_PREFS, Context.MODE_PRIVATE).contains("apps");
    }

    static List<String> dock(Context ctx) {
        List<String> out = new ArrayList<>();
        String chosen = ctx.getSharedPreferences(DOCK_PREFS, Context.MODE_PRIVATE).getString("apps", null);
        if (chosen != null) {
            for (String p : chosen.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
            return out;
        }
        JSONObject json = HomePrefs.overlay(ctx);
        JSONArray arr = json == null ? null : json.optJSONArray("dock_apps");
        if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        if (out.isEmpty()) for (String p : DEFAULT_DOCK) out.add(p);
        return out;
    }

    static Set<String> hidden(Context ctx) {
        Set<String> out = new LinkedHashSet<>();
        JSONObject json = HomePrefs.overlay(ctx);
        JSONArray arr = json == null ? null : json.optJSONArray("hidden_apps");
        if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        return out;
    }

    /** Every app with a TV launcher entry, then phone-only ones; OwnYourTV itself is listed, to manage the TV. */
    static List<App> allApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<App> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                App a = load(ctx, pkg, ri, category);
                if (a != null) out.add(a);
            }
        }
        out.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    static App app(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> l = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg), 0);
            if (!l.isEmpty()) return load(ctx, pkg, l.get(0), category);
        }
        return null;
    }

    private static App load(Context ctx, String pkg, ResolveInfo ri, String category) {
        PackageManager pm = ctx.getPackageManager();
        try {
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm));
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            Drawable banner = null;
            try {
                banner = pm.getActivityBanner(cn);
                if (banner == null) banner = pm.getApplicationBanner(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            if (banner != null) {
                a.banner = banner;
                a.color = accent(banner);
            } else {
                Drawable icon = ri.loadIcon(pm);
                a.color = accent(icon);
                a.banner = new IconBanner(icon, a.color);
            }
            a.system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            a.launch = new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return a;
        } catch (RuntimeException e) {
            Log.w(TAG, "skipping " + pkg + ": " + e.getMessage());
            return null;
        }
    }

    /** The most vivid colour of the banner, for the glow: the saturated pixel nearest full brightness. */
    static int accent(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 32, 18);
            d.draw(c);
            float[] hsv = new float[3];
            float best = -1;
            int pick = 0xFF9B8CFF;
            for (int y = 0; y < 18; y++) {
                for (int x = 0; x < 32; x++) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 200) continue;
                    Color.colorToHSV(px, hsv);
                    float score = hsv[1] * hsv[2] * (hsv[2] > 0.35f ? 1f : 0.2f);
                    if (score > best) {
                        best = score;
                        pick = px;
                    }
                }
            }
            b.recycle();
            if (best < 0.18f) return 0xFF9B8CFF;
            Color.colorToHSV(pick, hsv);
            hsv[2] = Math.max(hsv[2], 0.85f);
            return Color.HSVToColor(hsv);
        } catch (RuntimeException e) {
            return 0xFF9B8CFF;
        }
    }

    // ---- memory -----------------------------------------------------------------------------------

    static long[] memory(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return new long[]{mi.totalMem - mi.availMem, mi.totalMem};
    }

    /** Asks Android to stop every app's background processes; returns how many apps were asked. */
    static int closeApps(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = ctx.getPackageManager();
        int n = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(ctx.getPackageName())) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && pm.getLeanbackLaunchIntentForPackage(ai.packageName) == null)
                continue;
            am.killBackgroundProcesses(ai.packageName);
            n++;
        }
        return n;
    }

    static String gigabytes(long bytes) {
        return String.format(java.util.Locale.getDefault(), "%.1f", bytes / 1073741824f);
    }

    // ---- Bluetooth --------------------------------------------------------------------------------

    interface Result<T> {
        void done(T value);
    }

    /** Name of the connected audio device, or null; the answer comes back on the main thread. */
    static void connectedSpeaker(Context ctx, Result<String> out) {
        try {
            if (Build.VERSION.SDK_INT >= 31
                    && ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                out.done(null);
                return;
            }
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                out.done(null);
                return;
            }
            adapter.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    String name = null;
                    try {
                        List<BluetoothDevice> devices = proxy.getConnectedDevices();
                        if (!devices.isEmpty()) name = devices.get(0).getName();
                    } catch (SecurityException ignored) {
                    }
                    adapter.closeProfileProxy(profile, proxy);
                    out.done(name);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                }
            }, BluetoothProfile.A2DP);
        } catch (SecurityException | IllegalStateException e) {
            out.done(null);
        }
    }

    static boolean enabled(Context ctx, String pkg) {
        try {
            return ctx.getPackageManager().getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ---- Continue watching ------------------------------------------------------------------------

    static final class Resume {
        String pkg;
        String title;
        String detail;
        float progress;
        long remainingMin;
        Intent intent;
    }

    /** The most recent Watch Next row of each app (needs READ_TV_LISTINGS). Off the main thread. */
    static List<Resume> watchNext(Context ctx) {
        List<Resume> out = new ArrayList<>();
        if (ctx.checkSelfPermission("android.permission.READ_TV_LISTINGS") != PackageManager.PERMISSION_GRANTED) return out;
        Set<String> seen = new LinkedHashSet<>();
        try (Cursor c = ctx.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            if (c == null) return out;
            int iPkg = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
            int iTitle = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE);
            int iSeason = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
            int iEpisode = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
            int iPos = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
            int iDur = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
            int iUri = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
            while (c.moveToNext()) {
                String pkg = c.getString(iPkg);
                if (pkg == null || !seen.add(pkg)) continue;
                Resume r = new Resume();
                r.pkg = pkg;
                r.title = c.isNull(iTitle) ? "" : c.getString(iTitle);
                String season = c.isNull(iSeason) ? null : c.getString(iSeason);
                String episode = c.isNull(iEpisode) ? null : c.getString(iEpisode);
                r.detail = season != null && episode != null ? ctx.getString(R.string.h_episode, season, episode) : "";
                long pos = c.isNull(iPos) ? 0 : c.getLong(iPos), dur = c.isNull(iDur) ? 0 : c.getLong(iDur);
                r.progress = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;
                r.remainingMin = dur > 0 ? Math.max(1, (dur - pos) / 60000) : 0;
                try {
                    String uri = c.isNull(iUri) ? null : c.getString(iUri);
                    if (uri != null) r.intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                } catch (Exception ignored) {
                }
                if (r.intent != null && !r.title.isEmpty()) out.add(r);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "watch next: " + e.getMessage());
        }
        return out;
    }

    // ---- inputs -----------------------------------------------------------------------------------

    static final class Input {
        String id;
        String label;
    }

    /** HDMI inputs, a device reported over HDMI-CEC first under its own name ("Fire TV Stick"). */
    static List<Input> inputs(Context ctx) {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<Input> ports = new ArrayList<>();
        for (TvInputInfo info : tim.getTvInputList()) {
            if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
            Input in = new Input();
            in.id = info.getId();
            CharSequence custom = info.loadCustomLabel(ctx);
            in.label = String.valueOf(custom != null && custom.length() > 0 ? custom : info.loadLabel(ctx));
            if (info.getParentId() != null) out.add(in);
            else ports.add(in);
        }
        out.addAll(ports);
        return out;
    }

    static Intent inputIntent(String inputId, String liveTvPkg) {
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelUriForPassthroughInput(inputId), "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (liveTvPkg != null && !liveTvPkg.isEmpty()) view.setPackage(liveTvPkg);
        return view;
    }

    // ---- sky --------------------------------------------------------------------------------------

}
