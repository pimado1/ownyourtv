package app.ownyourtv.home;

import app.ownyourtv.R;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * the home screen's quick settings: a glass panel on the right, over Home or over whatever app is playing. Volume is set
 * here; picture and sound modes open the TV's own screens (TCL does not let an app change them).
 */
public class QuickPanelActivity extends Activity {
    private static final PathInterpolator SPRING = new PathInterpolator(.2f, .85f, .25f, 1f);
    static final String EXTRA_INPUTS = "show_inputs";

    private float u;
    private Typeface display, text, textBold;
    private AudioManager audio;
    private TextView volumeValue;
    private View volumeFill;
    private LinearLayout panel;

    /** Whether the home screen is the Home the TV opens. */
    static boolean isOurHome(Context ctx) {
        android.content.pm.ResolveInfo r = ctx.getPackageManager().resolveActivity(
                new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && ctx.getPackageName().equals(r.activityInfo.packageName);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        u = getResources().getDisplayMetrics().widthPixels / 1920f;
        display = font("fonts/bricolage.ttf", "'wght' 320, 'opsz' 96");
        text = font("fonts/manrope.ttf", "'wght' 600");
        textBold = font("fonts/manrope.ttf", "'wght' 750");
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setContentView(build());
    }

    private Typeface font(String asset, String axes) {
        try {
            return new Typeface.Builder(getAssets(), asset).setFontVariationSettings(axes).build();
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    private int px(float v) {
        return Math.round(v * u);
    }

    private TextView label(Typeface tf, float size, int color) {
        TextView t = new TextView(this);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable round(int fill, float radius, float stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radius));
        if (stroke > 0) g.setStroke(Math.max(1, px(stroke)), strokeColor);
        return g;
    }

    private Drawable rowBackground() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(0x33FFFFFF, 22, 3, Color.WHITE));
        s.addState(new int[]{}, round(0x14FFFFFF, 22, 0, 0));
        return s;
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0xCC03050F, 0x0003050F}));
        root.addView(shade, new FrameLayout.LayoutParams(px(1100), -1, Gravity.END));
        shade.setOnClickListener(v -> finish());

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(px(40), px(48), px(40), px(40));
        panel.setBackground(round(0xF20E1028, 36, 1, 0x29FFFFFF));
        TextView title = label(display, 46, Color.WHITE);
        title.setText(getString(R.string.h_quick_settings));
        panel.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView sub = label(textBold, 16, 0xA6FFFFFF);
        sub.setLetterSpacing(0.15f);
        sub.setText(getString(R.string.h_panel_hint));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = px(8);
        sl.bottomMargin = px(26);
        panel.addView(sub, sl);

        View volume = volumeRow();
        panel.addView(volume, rowParams());
        Profiles.Profile who = Profiles.current(this);
        if (who != null && who.kid) {
            // A child: the volume and who is watching, nothing that changes the TV.
            addRow(Glyph.GRID, getString(R.string.pf_title), getString(R.string.pf_row_on, who.name),
                    () -> open(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_PROFILES, true)));
        } else {
            addRow(Glyph.SPARKLE, getString(R.string.h_picture), getString(R.string.h_picture_d),
                    () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
            addRow(Glyph.SOUNDBAR, getString(R.string.h_sound), getString(R.string.h_sound_d),
                    () -> open(new Intent("com.android.tv.action.VIEW_SOUND_SETTINGS")));
            Weather w = Weather.from(this);
            addRow(Glyph.SPARKLE, getString(R.string.h_weather), w != null && !w.place.isEmpty() ? getString(R.string.h_city_is, w.place)
                    : getString(R.string.h_city_pick), () -> open(new Intent(this, HomeActivity.class)
                    .putExtra(HomeActivity.EXTRA_PICK_CITY, true)));
            addRow(Glyph.TV, getString(R.string.h_sources), getString(R.string.h_sources_d),
                    () -> open(new Intent(this, HomeActivity.class).putExtra(EXTRA_INPUTS, true)));
            addRow(Glyph.CAST, getString(R.string.h_wifi), getString(R.string.h_wifi_d), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            addRow(Glyph.SOUNDBAR, getString(R.string.h_bluetooth), getString(R.string.h_bluetooth_d),
                    () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
            // Sleep is a Google TV screen; on other TVs the row is left out rather than opening the wrong screen.
            Intent sleep = new Intent("com.google.android.pano.action.SLEEP");
            if (sleep.resolveActivity(getPackageManager()) != null)
                addRow(Glyph.MOON, getString(R.string.h_saver), getString(R.string.h_saver_d), () -> open(sleep));
            addRow(Glyph.SPARKLE, getString(R.string.app_name), getString(R.string.up_row, Update.current(this)),
                    () -> open(new Intent(this, app.ownyourtv.MainActivity.class).putExtra(Update.EXTRA_CHECK, true)));
            addRow(Glyph.GRID, getString(R.string.h_all_settings), getString(R.string.h_all_settings_d),
                    () -> open(new Intent(Settings.ACTION_SETTINGS)));
        }

        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(px(600), -2, Gravity.END | Gravity.CENTER_VERTICAL);
        pl.rightMargin = px(56);
        root.addView(panel, pl);
        panel.setTranslationX(px(120));
        panel.setAlpha(0f);
        panel.animate().translationX(0).alpha(1f).setDuration(420).setInterpolator(SPRING).start();
        volume.requestFocus();
        return root;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(76));
        lp.bottomMargin = px(10);
        return lp;
    }

    private LinearLayout row(int glyph, String name, String detail) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(22), 0, px(24), 0);
        r.setBackground(rowBackground());
        r.setFocusable(true);
        r.setClickable(true);
        r.setDefaultFocusHighlightEnabled(false);
        Glyph g = new Glyph(this, glyph);
        r.addView(g, new LinearLayout.LayoutParams(px(30), px(30)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView n = label(textBold, 22, Color.WHITE);
        n.setText(name);
        TextView d = label(text, 16, 0xA6FFFFFF);
        d.setText(detail);
        col.addView(n, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = px(4);
        col.addView(d, dl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1);
        cl.leftMargin = px(20);
        r.addView(col, cl);
        return r;
    }

    private void addRow(int glyph, String name, String detail, Runnable action) {
        LinearLayout r = row(glyph, name, detail);
        r.setOnClickListener(v -> action.run());
        panel.addView(r, rowParams());
    }

    private View volumeRow() {
        LinearLayout r = row(Glyph.SOUNDBAR, getString(R.string.h_volume), "");
        LinearLayout col = (LinearLayout) r.getChildAt(1);
        col.removeViewAt(1);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(round(0x2EFFFFFF, 4, 0, 0));
        volumeFill = new View(this);
        volumeFill.setBackground(round(0xFF9B8CFF, 4, 0, 0));
        track.addView(volumeFill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(300), px(8));
        tl.topMargin = px(12);
        col.addView(track, tl);
        volumeValue = label(textBold, 22, Color.WHITE);
        r.addView(volumeValue, new LinearLayout.LayoutParams(-2, -2));
        r.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                step(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                return true;
            }
            return false;
        });
        r.setOnClickListener(v -> {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            showVolume();
        });
        showVolume();
        return r;
    }

    private void step(int dir) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int top = max;
        Profiles.Profile kid = Profiles.current(this);
        // A child's volume stops at the ceiling the parent chose.
        if (kid != null && kid.kid && kid.maxVolume > 0) top = Math.max(1, max * kid.maxVolume / 100);
        int next = Math.max(0, Math.min(top, cur + dir * Math.max(1, Math.round(max / 20f))));
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
        } catch (SecurityException ignored) {
        }
        showVolume();
    }

    private void showVolume() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        int pct = Math.round(100f * cur / max);
        volumeValue.setText(muted ? getString(R.string.h_muted) : pct + " %");
        volumeFill.post(() -> {
            View track = (View) volumeFill.getParent();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) volumeFill.getLayoutParams();
            lp.width = muted ? 0 : Math.round(track.getWidth() * cur / (float) max);
            volumeFill.setLayoutParams(lp);
        });
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        } catch (ActivityNotFoundException | SecurityException e) {
            if (!Settings.ACTION_SETTINGS.equals(i.getAction())) open(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    /** The gear again while the panel is open, sent back here by the buttons module: the same button closes it. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** The gear again on a TV where it reaches the panel itself. */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, e);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }
}
