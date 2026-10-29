package app.ownyourtv.home;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Updates from the TV itself: once a day the latest GitHub release is looked up, and when it is newer the home screen
 * offers it. The APK is only installed when it is OwnYourTV, newer, and signed with the same key as the one installed.
 */
public final class Update {
    static final String LATEST = "https://api.github.com/repos/pimado1/ownyourtv/releases/latest";
    private static final String PREFS = "update";
    private static final long DAY = 20 * 3600_000L;
    private static final long MAX_SIZE = 50L << 20;
    public static final String EXTRA = "update";
    /** Opens OwnYourTV on "check for an update now". */
    public static final String EXTRA_CHECK = "check_update";

    public enum Result { OK, OFFLINE, NOT_OURS }

    private Update() {}

    /** Asks GitHub at most once a day; never on the main thread. */
    public static void checkIfDue(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        if (now - p.getLong("checked", 0) < DAY) return;
        // Offline: tried again in an hour, not at every return to the home screen.
        p.edit().putLong("checked", now - DAY + 3600_000L).apply();
        check(ctx);
    }

    /** The next return to the home screen asks GitHub again: after a reboot, a day may well have gone by. */
    public static void dueNow(Context ctx) {
        prefs(ctx).edit().remove("checked").apply();
    }

    /** Asks GitHub now (the owner opened the app or pressed "check"); false when GitHub could not be reached. */
    public static boolean check(Context ctx) {
        SharedPreferences p = prefs(ctx);
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(LATEST).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            if (c.getResponseCode() != 200) return false;
            JSONObject r;
            try (InputStream in = c.getInputStream()) {
                r = new JSONObject(new String(readAll(in), "UTF-8"));
            }
            String apk = null;
            JSONArray assets = r.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url");
            }
            if (apk == null || !apk.startsWith("https://github.com/pimado1/ownyourtv/")) return false;
            p.edit().putLong("checked", System.currentTimeMillis())
                    .putString("tag", r.optString("tag_name").replaceFirst("^v", "")).putString("apk", apk).apply();
            return true;
        } catch (Exception offline) {
            return false;
        }
    }

    /** The newer version's name, or null when this one is the latest. */
    public static String available(Context ctx) {
        String tag = prefs(ctx).getString("tag", null);
        return tag != null && newer(tag, current(ctx)) ? tag : null;
    }

    /** Offered once per version on the home screen; the app's own screen still shows it. */
    public static boolean shouldAsk(Context ctx) {
        String tag = available(ctx);
        return tag != null && !tag.equals(prefs(ctx).getString("later", ""));
    }

    public static void later(Context ctx) {
        prefs(ctx).edit().putString("later", available(ctx)).apply();
    }

    /** Without it, Android asks the owner to allow OwnYourTV to install apps; the app grants it over ADB instead. */
    public static boolean canInstall(Context ctx) {
        return ctx.getPackageManager().canRequestPackageInstalls();
    }

    /** Downloads, checks and hands the APK to Android, which asks the owner to confirm. Never on the main thread. */
    public static Result install(Context ctx) {
        String url = prefs(ctx).getString("apk", null);
        if (url == null) return Result.OFFLINE;
        File apk = new File(ctx.getCacheDir(), "update.apk");
        try {
            download(url, apk);
        } catch (Exception e) {
            apk.delete();
            return Result.OFFLINE;
        }
        if (!ours(ctx, apk)) {
            apk.delete();
            return Result.NOT_OURS;
        }
        try {
            PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(ctx.getPackageName());
            int id = pi.createSession(params);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (OutputStream out = s.openWrite("OwnYourTV.apk", 0, apk.length()); InputStream in = new FileInputStream(apk)) {
                    copy(in, out);
                    s.fsync(out);
                }
                Intent done = new Intent(ctx, UpdateReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                s.commit(PendingIntent.getBroadcast(ctx, id, done, flags).getIntentSender());
            }
            return Result.OK;
        } catch (Exception e) {
            return Result.OFFLINE;
        } finally {
            apk.delete();
        }
    }

    /** Same package, a newer version, and the very key the installed OwnYourTV is signed with. */
    static boolean ours(Context ctx, File apk) {
        PackageManager pm = ctx.getPackageManager();
        int flag = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo got = pm.getPackageArchiveInfo(apk.getPath(), flag);
        try {
            PackageInfo mine = pm.getPackageInfo(ctx.getPackageName(), flag);
            return got != null && ctx.getPackageName().equals(got.packageName) && code(got) > code(mine)
                    && !signers(mine).isEmpty() && signers(mine).equals(signers(got));
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean newer(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int[] parts(String v) {
        String[] s = v.replaceAll("[^0-9.]", "").split("\\.");
        int[] out = new int[s.length];
        for (int i = 0; i < s.length; i++) out[i] = s[i].isEmpty() ? 0 : Integer.parseInt(s[i]);
        return out;
    }

    public static String current(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    private static long code(PackageInfo i) {
        return Build.VERSION.SDK_INT >= 28 ? i.getLongVersionCode() : i.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo i) {
        Signature[] sigs = Build.VERSION.SDK_INT >= 28
                ? (i.signingInfo == null ? null : i.signingInfo.getApkContentsSigners())
                : i.signatures;
        Set<String> out = new HashSet<>();
        if (sigs == null) return out;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : sigs) out.add(Arrays.toString(md.digest(s.toByteArray())));
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200 || c.getContentLengthLong() > MAX_SIZE) throw new Exception("http " + c.getResponseCode());
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            long n = copy(in, out);
            if (n > MAX_SIZE || n == 0) throw new Exception("size " + n);
        }
    }

    private static long copy(InputStream in, OutputStream out) throws Exception {
        byte[] b = new byte[64 * 1024];
        long total = 0;
        for (int n; (n = in.read(b)) > 0; ) {
            out.write(b, 0, n);
            total += n;
            if (total > MAX_SIZE) throw new Exception("too big");
        }
        return total;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
