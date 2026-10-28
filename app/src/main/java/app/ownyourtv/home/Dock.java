package app.ownyourtv.home;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/** The home screen's dock, for OwnYourTV's own screens: read it, choose its apps and their order, or reset it. */
public final class Dock {
    private Dock() {
    }

    public static final class Entry {
        public final String pkg;
        public final String label;
        public final Drawable art;

        Entry(HomeData.App a) {
            pkg = a.pkg;
            label = a.label;
            art = a.banner;
        }
    }

    /** The apps in the dock, in order, leaving out any that is no longer installed. */
    public static List<Entry> current(Context ctx) {
        List<Entry> out = new ArrayList<>();
        for (String pkg : HomeData.dock(ctx)) {
            HomeData.App a = HomeData.app(ctx, pkg);
            if (a != null) out.add(new Entry(a));
        }
        return out;
    }

    /** Every app that can go in the dock. */
    public static List<Entry> available(Context ctx) {
        List<Entry> out = new ArrayList<>();
        for (HomeData.App a : HomeData.allApps(ctx)) if (!a.pkg.equals(ctx.getPackageName())) out.add(new Entry(a));
        return out;
    }

    public static void save(Context ctx, List<String> pkgs) {
        HomeData.saveDock(ctx, pkgs);
    }

    public static void reset(Context ctx) {
        HomeData.resetDock(ctx);
    }
}
