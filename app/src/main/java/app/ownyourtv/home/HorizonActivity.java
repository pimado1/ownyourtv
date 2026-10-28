package app.ownyourtv.home;

import app.ownyourtv.R;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Horizon": a home screen drawn as a living landscape. The dock apps float in a curved 3D carousel that lights the
 * scene in their colour; the other apps open in a popup. Laid out on the 1920 x 1080 grid of its design mockup and
 * scaled to the screen, so every position below is in mockup pixels ({@link #px}).
 */
public class HorizonActivity extends Activity {
    private static final float CARD_W = 400, CARD_H = 225, CARD_GAP = 410;
    private static final PathInterpolator SPRING = new PathInterpolator(.2f, .85f, .25f, 1f);
    private static final int ALL_APPS_COLOR = 0xFF9B8CFF;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread bgThread;
    private Handler bg;
    private float u;
    private Typeface display, displayLight, text, textBold;

    private HorizonScene scene;
    private TextView chipTitle;
    private TextView greeting, clock, dateLine, moonLine, chipLine, focusName, focusHint, toast;
    private View accentDot;
    private FrameLayout carousel;
    private final List<View> cards = new ArrayList<>();
    private final List<HorizonData.App> dock = new ArrayList<>();
    private int focus;
    private LinearLayout resumePill;
    private TextView resumeTitle, resumeLeft;
    private View resumeBar;
    private final Map<String, HorizonData.Resume> resumes = new HashMap<>();

    private View weatherCard;
    private HorizonGlyph weatherIcon, sunArc;
    private TextView weatherTemp, weatherSky, weatherPlace, weatherRise, weatherSet;
    private Weather weather;
    private Weather.Reading reading;
    private long weatherAt;

    private LinearLayout soirPill, speakerPill, castPill, sourcesPill, memPill;
    private TextView soirLabel, speakerValue, sourcesValue, memTitle, memAction;
    private HorizonGlyph memRing;
    private boolean soir;
    private String liveTvPkg = "";
    private String name = "";
    private Dialog popup;
    private ValueAnimator breathe;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            main.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };
    private final Runnable slowTick = new Runnable() {
        @Override
        public void run() {
            updateMemory();
            refreshSpeaker();
            if (weather != null && System.currentTimeMillis() - weatherAt > (reading == null ? Weather.RETRY_MS
                    : Weather.REFRESH_MS)) refreshWeather();
            main.postDelayed(this, 15_000);
        }
    };

    /** am start -n app.ownyourtv/.home.HorizonActivity --ez make_default true: kept for scripts that start the Home. */
    static final String EXTRA_MAKE_DEFAULT = "make_default";

    /** Holding OK on Réglages opens OwnYourTV itself, where the original home can be brought back. */
    private void backToClassic() {
        startActivity(new Intent(this, app.ownyourtv.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setBackgroundDrawable(new ColorDrawable(0xFF03050F));
        bgThread = new HandlerThread("horizon");
        bgThread.start();
        bg = new Handler(bgThread.getLooper());
        u = getResources().getDisplayMetrics().widthPixels / 1920f;
        display = font("fonts/bricolage.ttf", "'wght' 200, 'opsz' 96");
        displayLight = font("fonts/bricolage.ttf", "'wght' 320, 'opsz' 96");
        text = font("fonts/manrope.ttf", "'wght' 600");
        textBold = font("fonts/manrope.ttf", "'wght' 750");
        JSONObject json = HomePrefs.overlay(this);
        if (json != null) {
            liveTvPkg = json.optString("live_tv_package", "");
            name = json.optString("greeting_name", "").trim();
        }
        weather = Weather.from(this);
        setContentView(build());
        scene.setAlwaysNight(HomePrefs.alwaysNight(this));
        loadDock();
        refreshInputs();
        if (weather != null) refreshWeather();
        else showNoCity();
        if (getIntent().getBooleanExtra(HorizonPanelActivity.EXTRA_INPUTS, false)) main.post(this::showInputs);
        if (getIntent().getBooleanExtra(EXTRA_PICK_CITY, false)) main.post(this::showCityPicker);
    }

    // ---- first launch without a computer -----------------------------------------------------------

    private static final String ONBOARD_PREFS = "horizon_onboarding";
    private static final int PERMISSIONS_REQUEST = 7;
    /** Accessibility settings were opened from step 1, so a helper still off there means Android blocked it. */
    private boolean askedHelper;

    /** Installed from the TV (Downloader and the like): the stock home still owns Home, so the steps that ADB does
     *  OwnYourTV runs are walked through on screen instead. */
    private boolean needsOnboarding() {
        if (getSharedPreferences(ONBOARD_PREFS, MODE_PRIVATE).getBoolean("done", false)) return false;
        android.content.pm.ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        return home == null || !getPackageName().equals(home.activityInfo.packageName);
    }

    private boolean homeHelperOn() {
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(getPackageName() + "/") && on.contains("HomeKeyService");
    }

    private boolean permissionsGranted() {
        boolean bt = Build.VERSION.SDK_INT < 31
                || checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return bt && checkSelfPermission("android.permission.READ_TV_LISTINGS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private View onboardingStep(int n, int title, int detail, boolean done, View.OnClickListener click) {
        LinearLayout p = pill(done ? HorizonGlyph.CHECK : HorizonGlyph.SPARKLE,
                n + " · " + getString(title).toUpperCase(Locale.getDefault()), true);
        ((TextView) p.getTag()).setText(getString(detail));
        p.setOnClickListener(click);
        p.setLayoutParams(new LinearLayout.LayoutParams(-1, px(84)));
        return p;
    }

    /** The three steps, each showing a check once done; shown again after every step until "Done". */
    private void showOnboarding() {
        boolean helper = homeHelperOn(), perms = permissionsGranted(), city = weather != null;
        LinearLayout steps = column(14);
        View first = onboardingStep(1, R.string.o_home, R.string.o_home_d, helper, v -> {
            askedHelper = true;
            open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        steps.addView(first);
        if (askedHelper && !helper) {
            // Android 13+ greys nothing out: the switch just stays off for an app installed from a file.
            TextView blocked = label(text, 18, 0xFFFFC08A);
            blocked.setSingleLine(false);
            blocked.setText(getString(R.string.o_home_blocked));
            steps.addView(blocked, new LinearLayout.LayoutParams(-1, -2));
        }
        steps.addView(onboardingStep(2, R.string.o_perm, R.string.o_perm_d, perms, v -> requestPermissions(
                new String[]{"android.permission.BLUETOOTH_CONNECT", "android.permission.READ_TV_LISTINGS"},
                PERMISSIONS_REQUEST)));
        steps.addView(onboardingStep(3, R.string.o_city, R.string.o_city_d, city, v -> showCityPicker()));
        LinearLayout finish = pill(HorizonGlyph.CHECK, "", true);
        ((View) ((ViewGroup) ((TextView) finish.getTag()).getParent()).getChildAt(0)).setVisibility(View.GONE);
        ((TextView) finish.getTag()).setText(getString(helper && perms && city ? R.string.o_done : R.string.o_start));
        finish.setOnClickListener(v -> {
            getSharedPreferences(ONBOARD_PREFS, MODE_PRIVATE).edit().putBoolean("done", true).apply();
            dismissPopup();
            refreshResumes();
            refreshSpeaker();
            carousel.requestFocus();
        });
        steps.addView(finish, new LinearLayout.LayoutParams(-2, px(72)));
        dismissPopup();
        popup = sheet(getString(R.string.o_title), getString(R.string.o_sub), steps);
        popup.show();
        (!helper ? first : !perms ? steps.getChildAt(1) : !city ? steps.getChildAt(2) : finish).requestFocus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSIONS_REQUEST && needsOnboarding()) showOnboarding();
    }

    /** Réglages rapides ▸ Météo opens Home with this extra to choose the weather's city. */
    static final String EXTRA_PICK_CITY = "pick_city";

    private void showNoCity() {
        weatherTemp.setText("—");
        weatherSky.setText(getString(R.string.h_weather));
        weatherPlace.setText(getString(R.string.h_pick_city_hint));
    }

    /** The city search: type a name with the remote's keyboard, pick it, and it is kept on the TV. */
    private void showCityPicker() {
        LinearLayout body = column(22);
        LinearLayout search = row(16);
        android.widget.EditText field = new android.widget.EditText(this);
        field.setTypeface(text);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * u);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(0x80FFFFFF);
        field.setHint(getString(R.string.h_city_field_hint));
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        field.setPadding(px(26), 0, px(26), 0);
        StateListDrawable fieldBg = new StateListDrawable();
        fieldBg.addState(new int[]{android.R.attr.state_focused}, round(0x29FFFFFF, 28, 3, Color.WHITE));
        fieldBg.addState(new int[]{}, round(0x1AFFFFFF, 28, 1, 0x40FFFFFF));
        field.setBackground(fieldBg);
        search.addView(field, new LinearLayout.LayoutParams(px(820), px(72)));
        LinearLayout go = pill(HorizonGlyph.SPARKLE, getString(R.string.h_weather_upper), true);
        ((TextView) go.getTag()).setText(getString(R.string.h_search));
        search.addView(go, new LinearLayout.LayoutParams(-2, px(72)));
        body.addView(search, wrap());
        TextView status = label(text, 20, 0xB3FFFFFF);
        body.addView(status, wrap());
        LinearLayout results = column(12);
        body.addView(results, wrap());
        Runnable run = () -> {
            String q = field.getText().toString().trim();
            if (q.isEmpty()) return;
            status.setText(getString(R.string.h_searching, q));
            results.removeAllViews();
            bg.post(() -> {
                List<Weather.Place> found = Weather.search(q);
                main.post(() -> {
                    status.setText(found.isEmpty() ? getString(R.string.h_no_city)
                            : getString(R.string.h_choose_city));
                    View first = null;
                    for (Weather.Place p : found) {
                        LinearLayout r = pill(HorizonGlyph.SPARKLE, p.detail.toUpperCase(Locale.getDefault()), true);
                        ((TextView) r.getTag()).setText(p.name);
                        r.setOnClickListener(v -> {
                            Weather.save(this, p.latitude, p.longitude, p.name);
                            weather = Weather.from(this);
                            reading = null;
                            refreshWeather();
                            dismissPopup();
                            showToast(getString(R.string.h_weather_set, p.name));
                            if (needsOnboarding()) main.post(this::showOnboarding);
                        });
                        results.addView(r, new LinearLayout.LayoutParams(-2, px(72)));
                        if (first == null) first = r;
                    }
                    if (first != null) {
                        // The TV keyboard covers the lower half of the screen: put it away once there is a list.
                        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                                .hideSoftInputFromWindow(field.getWindowToken(), 0);
                        first.requestFocus();
                    }
                });
            });
        };
        go.setOnClickListener(v -> run.run());
        field.setOnEditorActionListener((v, actionId, e) -> {
            run.run();
            return true;
        });
        dismissPopup();
        popup = sheet(getString(R.string.h_city_title), weather != null && !weather.place.isEmpty()
                ? getString(R.string.h_city_current, weather.place) : getString(R.string.h_city_none), body);
        popup.show();
        field.requestFocus();
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

    // ---- layout -----------------------------------------------------------------------------------

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        scene = new HorizonScene(this, text);
        // Drawn at half resolution into its own layer and scaled up: soft gradients look the same, a quarter of the
        // pixels to fill on every frame.
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        scene.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        scene.setPivotX(0);
        scene.setPivotY(0);
        scene.setScaleX(2f);
        scene.setScaleY(2f);
        root.addView(scene, new FrameLayout.LayoutParams(sw / 2, sh / 2));

        greeting = label(textBold, 22, 0xD1FFFFFF);
        greeting.setLetterSpacing(0.27f);
        root.addView(greeting, at(112, 72));
        clock = label(display, 184, Color.WHITE);
        clock.setLetterSpacing(-0.033f);
        clock.setIncludeFontPadding(false);
        clock.setFontFeatureSettings("tnum");
        root.addView(clock, at(104, 104));
        LinearLayout date = row(18);
        dateLine = label(text, 26, 0xDBFFFFFF);
        accentDot = new View(this);
        accentDot.setBackground(round(0xFFFF2D55, 3, 0, 0));
        moonLine = label(text, 26, 0xDBFFFFFF);
        date.addView(dateLine);
        date.addView(accentDot, new LinearLayout.LayoutParams(px(6), px(6)));
        date.addView(moonLine);
        root.addView(date, at(112, 300));
        root.addView(skyChip(), at(112, 352));

        LinearLayout top = row(14);
        top.addView(iconButton(R.drawable.ic_input, Color.WHITE, getString(R.string.h_sources), v -> showInputs()));
        top.addView(iconButton(R.drawable.ic_wifi, 0xFF5CF2A8, getString(R.string.h_wifi), v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        View settings = iconButton(R.drawable.ic_settings, Color.WHITE, getString(R.string.h_settings),
                v -> open(new Intent(this, HorizonPanelActivity.class)));
        settings.setOnLongClickListener(v -> {
            backToClassic();
            return true;
        });
        top.addView(settings);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
        tl.rightMargin = px(112);
        tl.topMargin = px(52);
        root.addView(top, tl);

        weatherCard = weatherCard();
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(px(420), -2, Gravity.END | Gravity.TOP);
        wl.rightMargin = px(112);
        wl.topMargin = px(138);
        root.addView(weatherCard, wl);

        carousel = new FrameLayout(this);
        carousel.setClipChildren(false);
        carousel.setFocusable(true);
        carousel.setDefaultFocusHighlightEnabled(false);
        carousel.setOnKeyListener(this::carouselKey);
        carousel.setId(View.generateViewId());
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, px(360));
        cl.topMargin = px(450);
        root.addView(carousel, cl);

        focusName = label(displayLight, 64, Color.WHITE);
        focusName.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        nl.topMargin = px(820);
        root.addView(focusName, nl);
        focusHint = label(textBold, 20, 0xB3FFFFFF);
        focusHint.setLetterSpacing(0.2f);
        focusHint.setAllCaps(true);
        focusHint.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        hl.topMargin = px(906);
        root.addView(focusHint, hl);
        resumePill = resumePill();
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, px(56), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        rl.topMargin = px(898);
        root.addView(resumePill, rl);

        root.addView(bottomBar(), bottomParams());

        toast = label(text, 20, Color.WHITE);
        toast.setPadding(px(26), px(18), px(26), px(18));
        toast.setBackground(round(0xD10E1028, 22, 1, 0x735CF2A8));
        toast.setAlpha(0f);
        FrameLayout.LayoutParams ol = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        ol.rightMargin = px(112);
        ol.bottomMargin = px(128);
        root.addView(toast, ol);

        carousel.setNextFocusUpId(settings.getId());
        settings.setNextFocusDownId(carousel.getId());
        for (int i = 0; i < top.getChildCount(); i++) top.getChildAt(i).setNextFocusDownId(carousel.getId());
        return root;
    }

    private FrameLayout.LayoutParams bottomParams() {
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, px(64), Gravity.BOTTOM);
        bl.leftMargin = px(112);
        bl.rightMargin = px(112);
        bl.bottomMargin = px(44);
        return bl;
    }

    private FrameLayout.LayoutParams at(float x, float y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        return lp;
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

    private LinearLayout row(float gap) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        // Focus zooms its element a little: nothing may crop it, or its ring loses its edges.
        l.setClipChildren(false);
        l.setClipToPadding(false);
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        GradientDrawable spacer = new GradientDrawable();
        spacer.setSize(px(gap), 1);
        spacer.setColor(Color.TRANSPARENT);
        l.setDividerDrawable(spacer);
        return l;
    }

    private LinearLayout column(float gap) {
        LinearLayout l = row(0);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.START);
        GradientDrawable spacer = new GradientDrawable();
        spacer.setSize(1, px(gap));
        spacer.setColor(Color.TRANSPARENT);
        l.setDividerDrawable(spacer);
        return l;
    }

    private GradientDrawable round(int fill, float radius, float stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radius));
        if (stroke > 0) g.setStroke(Math.max(1, px(stroke)), strokeColor);
        return g;
    }

    /** Glass pill background, with a bright rim while focused. */
    private Drawable glass(float radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(0x52FFFFFF, radius, 3, Color.WHITE));
        s.addState(new int[]{}, round(0x850C0E22, radius, 1, 0x24FFFFFF));
        return s;
    }

    private void focusScale(View v) {
        v.setOnFocusChangeListener((view, has) -> view.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                .setDuration(220).setInterpolator(SPRING).start());
    }

    private View iconButton(int icon, int tint, String description, View.OnClickListener click) {
        ImageView b = new ImageView(this);
        b.setId(View.generateViewId());
        b.setImageResource(icon);
        b.setColorFilter(tint);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(px(19), px(19), px(19), px(19));
        b.setBackground(glass(32));
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(description);
        b.setOnClickListener(click);
        b.setLayoutParams(new LinearLayout.LayoutParams(px(64), px(64)));
        b.setDefaultFocusHighlightEnabled(false);
        focusScale(b);
        return b;
    }

    private View skyChip() {
        LinearLayout chip = row(14);
        chip.setPadding(px(14), px(12), px(20), px(12));
        chip.setBackground(round(0x6B0C0E22, 22, 1, 0x24FFFFFF));
        HorizonGlyph g = new HorizonGlyph(this, HorizonGlyph.SPARKLE);
        g.setColor(0xFFC9D6FF);
        chip.addView(g, new LinearLayout.LayoutParams(px(30), px(30)));
        LinearLayout col = column(2);
        TextView t = label(textBold, 15, 0xADFFFFFF);
        t.setLetterSpacing(0.13f);
        chipTitle = t;
        chipLine = label(text, 19, Color.WHITE);
        col.addView(t, wrap());
        col.addView(chipLine, wrap());
        chip.addView(col);
        return chip;
    }

    private View weatherCard() {
        LinearLayout card = column(14);
        card.setPadding(px(24), px(20), px(24), px(20));
        card.setBackground(round(0x750C0E22, 28, 1, 0x24FFFFFF));
        LinearLayout top = row(16);
        weatherIcon = new HorizonGlyph(this, HorizonGlyph.CLOUD);
        weatherIcon.setColor(0xFFDDE3FF);
        weatherIcon.setAnimated(true);
        top.addView(weatherIcon, new LinearLayout.LayoutParams(px(52), px(52)));
        weatherTemp = label(displayLight, 72, Color.WHITE);
        weatherTemp.setLetterSpacing(-0.03f);
        top.addView(weatherTemp);
        LinearLayout col = column(4);
        weatherSky = label(text, 22, Color.WHITE);
        weatherPlace = label(text, 17, 0xBFFFFFFF);
        col.addView(weatherSky, wrap());
        col.addView(weatherPlace, wrap());
        top.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(top);
        LinearLayout sun = row(10);
        weatherRise = label(text, 15, 0xB8FFFFFF);
        weatherSet = label(text, 15, 0xB8FFFFFF);
        sunArc = new HorizonGlyph(this, HorizonGlyph.ARC);
        sun.addView(weatherRise, wrap());
        sun.addView(sunArc, new LinearLayout.LayoutParams(0, px(30), 1));
        sun.addView(weatherSet, wrap());
        card.addView(sun, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        card.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, px(1))));
        LinearLayout week = row(0);
        for (int i = 0; i < Weather.Reading.DAYS; i++) {
            LinearLayout day = column(6);
            day.setGravity(Gravity.CENTER_HORIZONTAL);
            dayName[i] = label(textBold, 14, 0xB3FFFFFF);
            dayName[i].setLetterSpacing(0.08f);
            dayIcon[i] = new HorizonGlyph(this, HorizonGlyph.CLOUD);
            dayIcon[i].setColor(0xFFDDE3FF);
            dayTemp[i] = label(text, 15, Color.WHITE);
            day.addView(dayName[i], wrap());
            day.addView(dayIcon[i], new LinearLayout.LayoutParams(px(26), px(26)));
            day.addView(dayTemp[i], wrap());
            week.addView(day, new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.addView(week, new LinearLayout.LayoutParams(-1, -2));
        weatherTemp.setText("—");
        return card;
    }

    private final TextView[] dayName = new TextView[Weather.Reading.DAYS];
    private final TextView[] dayTemp = new TextView[Weather.Reading.DAYS];
    private final HorizonGlyph[] dayIcon = new HorizonGlyph[Weather.Reading.DAYS];

    private static int glyphFor(int code, boolean day) {
        if (Weather.isFog(code)) return HorizonGlyph.FOG;
        if (Weather.isRain(code)) return HorizonGlyph.RAIN;
        if (Weather.isSnow(code)) return HorizonGlyph.SNOW;
        return code <= 1 ? (day ? HorizonGlyph.SUN : HorizonGlyph.NIGHT) : HorizonGlyph.CLOUD;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private LinearLayout pill(int glyph, String title, boolean focusable) {
        LinearLayout p = row(14);
        p.setId(View.generateViewId());
        p.setPadding(px(20), 0, px(26), 0);
        p.setBackground(focusable ? glass(32) : round(0x850C0E22, 32, 1, 0x24FFFFFF));
        p.setFocusable(focusable);
        p.setClickable(focusable);
        p.setDefaultFocusHighlightEnabled(false);
        if (focusable) focusScale(p);
        HorizonGlyph g = new HorizonGlyph(this, glyph);
        p.addView(g, new LinearLayout.LayoutParams(px(28), px(28)));
        LinearLayout col = column(2);
        TextView t = label(textBold, 15, 0xADFFFFFF);
        t.setLetterSpacing(0.1f);
        t.setText(title);
        TextView v = label(text, 20, Color.WHITE);
        col.addView(t, wrap());
        col.addView(v, wrap());
        p.addView(col);
        p.setTag(v);
        return p;
    }

    private View bottomBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setClipChildren(false);
        LinearLayout left = row(16);
        left.setClipChildren(false);
        soirPill = pill(HorizonGlyph.MOON, getString(R.string.h_mode), true);
        soirLabel = (TextView) soirPill.getTag();
        soirLabel.setTypeface(textBold);
        soirLabel.setText(getString(R.string.h_tonight));
        soirPill.setOnClickListener(v -> toggleSoir());
        speakerPill = pill(HorizonGlyph.SOUNDBAR, getString(R.string.h_soundbar), true);
        speakerValue = (TextView) speakerPill.getTag();
        speakerValue.setText("…");
        speakerPill.setOnClickListener(v -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        castPill = pill(HorizonGlyph.CAST, "CHROMECAST", false);
        ((TextView) castPill.getTag()).setText(getString(R.string.h_cast_ready));
        castPill.setVisibility(HorizonData.enabled(this, "com.google.android.apps.mediashell") ? View.VISIBLE : View.GONE);
        sourcesPill = pill(HorizonGlyph.TV, getString(R.string.h_sources_upper), true);
        sourcesValue = (TextView) sourcesPill.getTag();
        sourcesValue.setText("HDMI");
        sourcesPill.setOnClickListener(v -> showInputs());
        left.addView(soirPill, new LinearLayout.LayoutParams(-2, -1));
        left.addView(speakerPill, new LinearLayout.LayoutParams(-2, -1));
        left.addView(castPill, new LinearLayout.LayoutParams(-2, -1));
        left.addView(sourcesPill, new LinearLayout.LayoutParams(-2, -1));
        bar.addView(left, new FrameLayout.LayoutParams(-2, -1, Gravity.START));

        memPill = row(14);
        memPill.setId(View.generateViewId());
        memPill.setPadding(px(18), 0, px(26), 0);
        memPill.setBackground(glass(32));
        memPill.setFocusable(true);
        memPill.setClickable(true);
        memPill.setDefaultFocusHighlightEnabled(false);
        focusScale(memPill);
        memRing = new HorizonGlyph(this, HorizonGlyph.RING);
        memPill.addView(memRing, new LinearLayout.LayoutParams(px(44), px(44)));
        LinearLayout col = column(2);
        memTitle = label(textBold, 15, 0xADFFFFFF);
        memTitle.setLetterSpacing(0.1f);
        memAction = label(text, 20, Color.WHITE);
        memAction.setText(getString(R.string.h_close_apps));
        col.addView(memTitle, wrap());
        col.addView(memAction, wrap());
        memPill.addView(col);
        HorizonGlyph refresh = new HorizonGlyph(this, HorizonGlyph.REFRESH);
        memPill.addView(refresh, new LinearLayout.LayoutParams(px(24), px(24)));
        memPill.setOnClickListener(v -> freeMemory());
        bar.addView(memPill, new FrameLayout.LayoutParams(-2, -1, Gravity.END));

        for (View v : new View[]{soirPill, speakerPill, sourcesPill, memPill}) v.setNextFocusUpId(carousel.getId());
        soirPill.setNextFocusLeftId(soirPill.getId());
        memPill.setNextFocusRightId(memPill.getId());
        memPill.setNextFocusLeftId(sourcesPill.getId());
        sourcesPill.setNextFocusRightId(memPill.getId());
        return bar;
    }

    private LinearLayout resumePill() {
        LinearLayout p = row(16);
        p.setId(View.generateViewId());
        p.setPadding(px(22), 0, px(26), 0);
        p.setBackground(glass(28));
        p.setFocusable(true);
        p.setClickable(true);
        p.setDefaultFocusHighlightEnabled(false);
        focusScale(p);
        HorizonGlyph play = new HorizonGlyph(this, HorizonGlyph.PLAY);
        p.addView(play, new LinearLayout.LayoutParams(px(22), px(22)));
        TextView t = label(textBold, 20, Color.WHITE);
        t.setText(getString(R.string.h_resume));
        p.addView(t);
        resumeTitle = label(text, 20, 0xC7FFFFFF);
        resumeTitle.setMaxWidth(px(620));
        p.addView(resumeTitle);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(round(0x2EFFFFFF, 3, 0, 0));
        resumeBar = new View(this);
        resumeBar.setBackground(round(0xFFFF2D55, 3, 0, 0));
        track.addView(resumeBar, new FrameLayout.LayoutParams(0, -1));
        p.addView(track, new LinearLayout.LayoutParams(px(160), px(6)));
        resumeLeft = label(text, 17, 0xA8FFFFFF);
        p.addView(resumeLeft);
        p.setVisibility(View.GONE);
        p.setOnClickListener(v -> {
            HorizonData.Resume r = resumes.get(currentPkg());
            if (r != null) open(r.intent);
        });
        p.setNextFocusUpId(carousel.getId());
        return p;
    }

    // ---- the carousel -----------------------------------------------------------------------------

    /** Shadow margin around a card face, and the height of its reflection on the floor (mockup px). */
    private static final float S = 56, REFL = 70;
    private Bitmap shadowBmp;

    /** One soft rounded shadow, blurred once in software and reused (tinted) under every card. */
    private Bitmap shadowBitmap() {
        if (shadowBmp != null) return shadowBmp;
        int w = px(CARD_W + 2 * S), h = px(CARD_H + 2 * S);
        shadowBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setMaskFilter(new BlurMaskFilter(px(30), BlurMaskFilter.Blur.NORMAL));
        new Canvas(shadowBmp).drawRoundRect(new RectF(px(S + 10), px(S + 8), w - px(S + 10), h - px(S - 6)),
                px(28), px(28), p);
        return shadowBmp;
    }

    /** The bottom of the card mirrored, fading out: drawn once per card, then just shown. */
    private Bitmap reflection(Drawable face) {
        int cw = px(CARD_W), ch = px(CARD_H), rh = px(REFL);
        Bitmap full = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(full);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, cw, ch), px(28), px(28), Path.Direction.CW);
        fc.clipPath(clip);
        face.setBounds(0, 0, cw, ch);
        face.draw(fc);
        Bitmap out = Bitmap.createBitmap(cw, rh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Matrix m = new Matrix();
        m.setScale(1, -1);
        m.postTranslate(0, ch);
        c.drawBitmap(full, m, null);
        Paint fade = new Paint();
        fade.setShader(new LinearGradient(0, 0, 0, rh, 0x80000000, 0x00000000, Shader.TileMode.CLAMP));
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(0, 0, cw, rh, fade);
        full.recycle();
        return out;
    }

    private void loadDock() {
        dock.clear();
        Set<String> hidden = HorizonData.hidden(this);
        for (String pkg : HorizonData.dock(this)) {
            if (hidden.contains(pkg)) continue;
            HorizonData.App a = HorizonData.app(this, pkg);
            if (a != null) dock.add(a);
        }
        carousel.removeAllViews();
        cards.clear();
        for (HorizonData.App a : dock) cards.add(card(a));
        cards.add(allAppsCard());
        for (View c : cards) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(CARD_W + 2 * S), px(CARD_H + 2 * S + REFL),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = px(40 - S);
            carousel.addView(c, lp);
        }
        focus = Math.min(focus, cards.size() - 1);
        layoutCards(false);
    }

    private View card(HorizonData.App a) {
        FrameLayout f = baseCard();
        ImageView img = new ImageView(this);
        img.setImageDrawable(a.banner);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(img, new FrameLayout.LayoutParams(-1, -1));
        addSheen(f);
        Drawable copy = a.banner.getConstantState() != null ? a.banner.getConstantState().newDrawable().mutate() : a.banner;
        return holder(f, reflection(copy), () -> open(a.launch));
    }

    private View allAppsCard() {
        FrameLayout f = baseCard();
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF5A4CC0, 0xFF221A66});
        f.setBackground(g);
        LinearLayout col = column(14);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        HorizonGlyph grid = new HorizonGlyph(this, HorizonGlyph.GRID);
        col.addView(grid, new LinearLayout.LayoutParams(px(60), px(60)));
        TextView t = label(displayLight, 30, Color.WHITE);
        t.setText(getString(R.string.h_all_apps));
        col.addView(t, wrap());
        f.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        addSheen(f);
        GradientDrawable copy = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF5A4CC0, 0xFF221A66});
        return holder(f, reflection(copy), this::showAllApps);
    }

    /** A card in the carousel: its floor shadow, its reflection and its face, moved and turned as one. */
    private View holder(FrameLayout face, Bitmap refl, Runnable click) {
        FrameLayout h = new FrameLayout(this);
        h.setClipChildren(false);
        ImageView shadow = new ImageView(this);
        shadow.setImageBitmap(shadowBitmap());
        shadow.setTag("shadow");
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(px(CARD_W + 2 * S), px(CARD_H + 2 * S));
        sl.topMargin = px(30);
        h.addView(shadow, sl);
        ImageView mirror = new ImageView(this);
        mirror.setImageBitmap(refl);
        mirror.setTag("mirror");
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(CARD_W), px(REFL));
        ml.leftMargin = px(S);
        ml.topMargin = px(S + CARD_H + 10);
        h.addView(mirror, ml);
        View dim = new View(this);
        dim.setBackgroundColor(0xFF05060F);
        dim.setAlpha(0f);
        dim.setTag("dim");
        face.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(px(CARD_W), px(CARD_H));
        fl.leftMargin = px(S);
        fl.topMargin = px(S);
        h.addView(face, fl);
        h.setTag(face);
        h.setOnClickListener(v -> click.run());
        h.setPivotX(px(CARD_W / 2 + S));
        h.setPivotY(px(S + CARD_H / 2));
        h.setCameraDistance(getResources().getDisplayMetrics().density * 1700 * u);
        return h;
    }

    private FrameLayout baseCard() {
        FrameLayout f = new FrameLayout(this);
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(28));
            }
        });
        f.setForeground(round(Color.TRANSPARENT, 28, 2, 0x8CFFFFFF));
        f.getForeground().setAlpha(0);
        return f;
    }

    private void addSheen(FrameLayout f) {
        View sheen = new View(this);
        sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}));
        sheen.setRotation(18);
        sheen.setAlpha(0f);
        sheen.setTag("sheen");
        f.addView(sheen, new FrameLayout.LayoutParams(px(140), px(CARD_H * 1.8f), Gravity.CENTER_VERTICAL));
    }

    /**
     * Cover flow: the focused card faces the viewer, the others turn away and stack behind one another, each
     * further one smaller, dimmer and partly hidden by the one in front. Shadows and reflections move with them.
     */
    private void layoutCards(boolean animate) {
        int accent = focus < dock.size() ? dock.get(focus).color : ALL_APPS_COLOR;
        for (int i = 0; i < cards.size(); i++) {
            View c = cards.get(i);
            int d = i - focus, ad = Math.abs(d);
            // A deck: every card tucked behind the one before it, deeper and smaller, a strip of it still showing,
            // so moving through the dock slides cards out of the deck and back into it.
            float sign = Math.signum(d);
            float z = d == 0 ? 120 : -260f * ad;
            float depth = 1700f / (1700f - z);
            float x = d == 0 ? 0 : sign * (255 + (ad - 1) * 115) * u;
            float rot = d == 0 ? 0 : -sign * 14;
            float scale = depth;
            // Opaque cards (a see-through deck mixes the logos of the cards behind); depth comes from the dimming.
            float alpha = ad <= 3 ? 1f : ad == 4 ? 0.5f : 0f;
            c.setZ(20 - ad);
            if (animate) {
                c.animate().translationX(x).rotationY(rot).scaleX(scale).scaleY(scale).alpha(alpha)
                        .setDuration(650).setInterpolator(SPRING).start();
            } else {
                c.setTranslationX(x);
                c.setRotationY(rot);
                c.setScaleX(scale);
                c.setScaleY(scale);
                c.setAlpha(alpha);
            }
            ImageView shadow = c.findViewWithTag("shadow");
            shadow.setColorFilter(d == 0 ? accent : Color.BLACK, PorterDuff.Mode.SRC_IN);
            shadow.animate().alpha(d == 0 ? 0.9f : 0.65f).translationY(d == 0 ? px(14) : 0).setDuration(450).start();
            c.findViewWithTag("mirror").animate().alpha(d == 0 ? 1f : 0.55f).setDuration(450).start();
            FrameLayout face = (FrameLayout) c.getTag();
            // Further cards sink into the night, as in the mockup.
            face.findViewWithTag("dim").animate().alpha(Math.min(0.7f, ad * 0.2f)).setDuration(500).start();
            Drawable fg = face.getForeground();
            if (fg != null) fg.setAlpha(d == 0 ? 255 : 0);
            View sheen = face.findViewWithTag("sheen");
            if (sheen != null) {
                sheen.animate().cancel();
                if (d == 0) runSheen(sheen);
                else sheen.setAlpha(0f);
            }
        }
        int color = accent;
        scene.setAccent(color);
        scene.setFocusIndex(focus);
        accentDot.setBackground(round(color, 3, 0, 0));
        memRing.setColor(color);
        resumeBar.setBackground(round(color, 3, 0, 0));
        focusName.setShadowLayer(px(8), 0, 0, (color & 0x00FFFFFF) | 0x99000000);
        focusName.setText(focus < dock.size() ? dock.get(focus).label : getString(R.string.h_all_apps));
        focusName.setAlpha(0f);
        focusName.setTranslationY(px(14));
        focusName.animate().alpha(1f).translationY(0).setDuration(500).setInterpolator(SPRING).start();
        updateResume();
    }

    private void runSheen(View sheen) {
        sheen.setAlpha(1f);
        sheen.setTranslationX(-px(220));
        sheen.animate().translationX(px(CARD_W + 120)).setStartDelay(500).setDuration(1500).setInterpolator(SPRING)
                .withEndAction(() -> {
                    if (sheen.getAlpha() > 0) main.postDelayed(() -> {
                        if (sheen.getAlpha() > 0) runSheen(sheen);
                    }, 1800);
                }).start();
    }

    /** OK held on a deck card: the dock menu opened, so the release must not also open the app. */
    private boolean okHeld;

    private boolean carouselKey(View v, int keyCode, KeyEvent e) {
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            // A short press opens the card on release; held past the repeat delay, it opens the dock menu.
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) okHeld = false;
                else if (!okHeld) {
                    okHeld = true;
                    if (focus < dock.size()) showDockMenu(focus);
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (!okHeld) cards.get(focus).performClick();
                okHeld = false;
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus > 0) {
                    focus--;
                    layoutCards(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus < cards.size() - 1) {
                    focus++;
                    layoutCards(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                (resumePill.getVisibility() == View.VISIBLE ? resumePill : soirPill).requestFocus();
                return true;
            default:
                return false;
        }
    }

    private String currentPkg() {
        return focus < dock.size() ? dock.get(focus).pkg : "";
    }

    private void updateResume() {
        HorizonData.Resume r = resumes.get(currentPkg());
        boolean show = r != null;
        boolean had = resumePill.hasFocus();
        resumePill.setVisibility(show ? View.VISIBLE : View.GONE);
        focusHint.setVisibility(show ? View.GONE : View.VISIBLE);
        for (View p : new View[]{soirPill, speakerPill, sourcesPill, memPill})
            p.setNextFocusUpId(show ? resumePill.getId() : carousel.getId());
        resumePill.setNextFocusDownId(soirPill.getId());
        if (show) {
            resumeTitle.setText(r.detail.isEmpty() ? r.title : r.title + " · " + r.detail);
            resumeLeft.setText(r.remainingMin > 0 ? getString(R.string.h_minutes, (int) r.remainingMin) : "");
            ViewGroup.LayoutParams lp = resumeBar.getLayoutParams();
            lp.width = Math.round(px(160) * r.progress);
            resumeBar.setLayoutParams(lp);
        } else {
            focusHint.setText(focus < dock.size() ? getString(R.string.h_ok_open) : getString(R.string.h_ok_all));
            if (had) carousel.requestFocus();
        }
    }

    private void refreshResumes() {
        bg.post(() -> {
            List<HorizonData.Resume> list = HorizonData.watchNext(this);
            main.post(() -> {
                resumes.clear();
                for (HorizonData.Resume r : list) resumes.put(r.pkg, r);
                updateResume();
            });
        });
    }

    // ---- header, weather, widgets -----------------------------------------------------------------

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        String hello = getString(h < 5 ? R.string.h_good_night : h < 12 ? R.string.h_good_morning
                : h < 18 ? R.string.h_good_afternoon : R.string.h_good_evening);
        greeting.setText((name.isEmpty() ? hello : hello + ", " + name).toUpperCase(Locale.getDefault()));
        String time = String.format(Locale.US, "%02d:%02d", h, now.get(Calendar.MINUTE));
        if (!time.contentEquals(clock.getText())) clock.setText(time);
        String date = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault()).format(now.getTime());
        dateLine.setText(Character.toUpperCase(date.charAt(0)) + date.substring(1));
        moonLine.setText(getString(HorizonData.moonPhase(now)));
        chipLine.setText(getString(R.string.h_next_shower, HorizonData.nextShower(this, now)));
        chipTitle.setText(weather != null && !weather.place.isEmpty()
                ? getString(R.string.h_real_sky_at, weather.place.toUpperCase()) : getString(R.string.h_real_sky));
        if (reading != null) {
            float hour = h + now.get(Calendar.MINUTE) / 60f;
            float f = (hour - reading.sunriseHour) / (reading.sunsetHour - reading.sunriseHour);
            sunArc.setValue(f);
        }
    }

    private void refreshWeather() {
        weatherAt = System.currentTimeMillis();
        Weather place = weather;
        bg.post(() -> {
            Weather.Reading r = place.read();
            main.post(() -> {
                if (r == null) return;
                reading = r;
                weatherTemp.setText(r.temp + "°");
                weatherSky.setText(getString(r.label()));
                weatherPlace.setText((place.place.isEmpty() ? "" : place.place + " · ") + getString(R.string.h_feels, (int) r.feels));
                weatherRise.setText(r.sunrise);
                weatherSet.setText(r.sunset);
                for (int i = 0; i < Weather.Reading.DAYS; i++) {
                    dayName[i].setText(r.nextDays[i].replace(".", "").toUpperCase(Locale.getDefault()));
                    dayIcon[i].setKind(glyphFor(r.nextCode[i], true));
                    dayTemp[i].setText(r.nextMax[i] + "° " + r.nextMin[i] + "°");
                }
                int kind = r.fog() ? HorizonScene.WEATHER_FOG : r.rain() ? HorizonScene.WEATHER_RAIN
                        : r.snow() ? HorizonScene.WEATHER_SNOW : HorizonScene.WEATHER_CLEAR;
                weatherIcon.setKind(glyphFor(r.code, r.day));
                scene.setWeather(kind, r.sunriseHour, r.sunsetHour);
                updateClock();
            });
        });
    }

    private void updateMemory() {
        long[] m = HorizonData.memory(this);
        memTitle.setText(getString(R.string.h_memory, HorizonData.gigabytes(m[0]), HorizonData.gigabytes(m[1])));
        memRing.setValue(m[0] / (float) m[1]);
    }

    private void refreshSpeaker() {
        HorizonData.connectedSpeaker(this, n -> main.post(() -> speakerValue.setText(n != null ? n : getString(R.string.h_not_connected))));
    }

    private void refreshInputs() {
        bg.post(() -> {
            List<HorizonData.Input> list = HorizonData.inputs(this);
            main.post(() -> sourcesValue.setText(list.isEmpty() ? getString(R.string.h_none) : list.get(0).label));
        });
    }

    private void freeMemory() {
        long before = HorizonData.memory(this)[0];
        memAction.setText(getString(R.string.h_closing));
        bg.post(() -> {
            int n = HorizonData.closeApps(this);
            main.postDelayed(() -> {
                long after = HorizonData.memory(this)[0];
                long freed = Math.max(0, (before - after) / 1048576);
                memAction.setText(getString(R.string.h_close_apps));
                updateMemory();
                showToast(getString(R.string.h_apps_closed, n, (int) freed));
            }, 1500);
        });
    }

    private void toggleSoir() {
        soir = !soir;
        scene.setDimmed(soir);
        soirLabel.setText(getString(soir ? R.string.h_tonight_on : R.string.h_tonight));
        soirPill.setBackground(soir ? activeGlass() : glass(32));
        if (!soir) {
            showToast(getString(R.string.h_tonight_off));
            return;
        }
        bg.post(() -> {
            HorizonData.closeApps(this);
            HorizonData.connectedSpeaker(this, n -> main.post(() ->
                    showToast(getString(R.string.h_tonight_done, n != null ? getString(R.string.h_tonight_speaker, n) : getString(R.string.h_tonight_ambience)))));
        });
    }

    private Drawable activeGlass() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(0x8C9B8CFF, 32, 3, Color.WHITE));
        s.addState(new int[]{}, round(0x619B8CFF, 32, 1, 0xB3C8BEFF));
        return s;
    }

    private void showToast(String msg) {
        toast.setText(msg);
        toast.animate().cancel();
        toast.setTranslationY(px(18));
        toast.animate().alpha(1f).translationY(0).setDuration(450).setInterpolator(SPRING)
                .withEndAction(() -> toast.animate().alpha(0f).setStartDelay(3200).setDuration(500).start()).start();
    }

    // ---- popups -----------------------------------------------------------------------------------

    private Dialog sheet(String title, String subtitle, View body) {
        Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xB303050F);
        LinearLayout panel = column(32);
        panel.setPadding(px(56), px(48), px(56), px(56));
        overlay.setClipChildren(false);
        panel.setBackground(round(0xF00E1028, 40, 1, 0x29FFFFFF));
        LinearLayout head = column(6);
        TextView t = label(displayLight, 52, Color.WHITE);
        t.setText(title);
        TextView s = label(textBold, 20, 0xA6FFFFFF);
        s.setLetterSpacing(0.15f);
        s.setText(subtitle.toUpperCase(Locale.getDefault()));
        head.addView(t, wrap());
        head.addView(s, wrap());
        panel.addView(head);
        panel.addView(body);
        overlay.addView(panel, new FrameLayout.LayoutParams(px(1380), -2, Gravity.CENTER));
        d.setContentView(overlay);
        overlay.setAlpha(0f);
        panel.setTranslationY(px(30));
        overlay.animate().alpha(1f).setDuration(300).start();
        panel.animate().translationY(0).setDuration(500).setInterpolator(SPRING).start();
        return d;
    }

    /**
     * A focus ring that never crops: the element sits inside a frame with a small gap, and the frame draws the
     * ring on its own edge, following the element's corner radius plus the gap.
     */
    private FrameLayout ringed(View content, float radius, float gap, int w, int h) {
        FrameLayout frame = new FrameLayout(this);
        int g = px(gap);
        frame.setPadding(g, g, g, g);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        StateListDrawable ring = new StateListDrawable();
        ring.addState(new int[]{android.R.attr.state_focused}, round(Color.TRANSPARENT, radius + gap, 3, Color.WHITE));
        frame.setForeground(ring);
        frame.setFocusable(true);
        frame.setClickable(true);
        frame.setDefaultFocusHighlightEnabled(false);
        frame.addView(content, new FrameLayout.LayoutParams(w, h));
        frame.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                .setDuration(260).setInterpolator(SPRING).start());
        return frame;
    }

    private List<String> dockPackages() {
        List<String> out = new ArrayList<>();
        for (HorizonData.App a : dock) out.add(a.pkg);
        return out;
    }

    /** Saves a new dock order, rebuilds the deck and keeps the focus on the app that moved. */
    private void applyDock(List<String> pkgs, int newFocus, String message) {
        // The OK key that triggered this may still be down: its repeats and release reach the deck next, and
        // must neither open the app nor the dock menu.
        okHeld = true;
        HorizonData.saveDock(this, pkgs);
        focus = Math.max(0, newFocus);
        loadDock();
        carousel.requestFocus();
        showToast(message);
    }

    /** Hold OK on a deck card: move it left or right, or take it off the deck. */
    private void showDockMenu(int index) {
        HorizonData.App app = dock.get(index);
        LinearLayout actions = row(18);
        List<String> pkgs = dockPackages();
        if (index > 0) actions.addView(dockAction(getString(R.string.h_move), getString(R.string.h_left), () -> {
            java.util.Collections.swap(pkgs, index, index - 1);
            dismissPopup();
            applyDock(pkgs, index - 1, getString(R.string.h_moved_left, app.label));
        }), new LinearLayout.LayoutParams(-2, px(72)));
        if (index < pkgs.size() - 1) actions.addView(dockAction(getString(R.string.h_move), getString(R.string.h_right), () -> {
            java.util.Collections.swap(pkgs, index, index + 1);
            dismissPopup();
            applyDock(pkgs, index + 1, getString(R.string.h_moved_right, app.label));
        }), new LinearLayout.LayoutParams(-2, px(72)));
        if (pkgs.size() > 1) actions.addView(dockAction(getString(R.string.h_dock), getString(R.string.h_remove), () -> {
            pkgs.remove(index);
            dismissPopup();
            applyDock(pkgs, Math.min(index, pkgs.size() - 1), getString(R.string.h_removed, app.label));
        }), new LinearLayout.LayoutParams(-2, px(72)));
        if (HorizonData.dockChosen(this)) actions.addView(dockAction(getString(R.string.h_dock), getString(R.string.h_theme_dock), () -> {
            dismissPopup();
            HorizonData.resetDock(this);
            okHeld = true;
            focus = 0;
            loadDock();
            carousel.requestFocus();
            showToast(getString(R.string.h_theme_dock_back));
        }), new LinearLayout.LayoutParams(-2, px(72)));
        dismissPopup();
        popup = sheet(app.label, getString(R.string.h_dock_menu_hint), actions);
        popup.show();
        if (actions.getChildCount() > 0) actions.getChildAt(0).requestFocus();
    }

    /** Set when Android's uninstall window is open, so Home rebuilds its lists when it comes back. */
    private boolean refreshAfterUninstall;

    /** Hold OK on an app in Toutes les apps: add it to the dock, uninstall it, or open its info screen. */
    private void showAppMenu(HorizonData.App app) {
        LinearLayout actions = row(18);
        if (!dockPackages().contains(app.pkg)) actions.addView(dockAction(getString(R.string.h_dock), getString(R.string.h_add_dock), () -> {
            List<String> pkgs = dockPackages();
            pkgs.add(app.pkg);
            dismissPopup();
            applyDock(pkgs, pkgs.size() - 1, getString(R.string.h_added, app.label));
        }), new LinearLayout.LayoutParams(-2, px(72)));
        if (!app.system) actions.addView(dockAction(getString(R.string.h_app), getString(R.string.h_uninstall), () -> {
            dismissPopup();
            refreshAfterUninstall = true;
            // Android's own window asks the viewer to confirm: nothing is removed without that answer.
            open(new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + app.pkg)));
        }), new LinearLayout.LayoutParams(-2, px(72)));
        actions.addView(dockAction(getString(R.string.h_app), getString(R.string.h_app_info), () -> {
            dismissPopup();
            open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + app.pkg)));
        }), new LinearLayout.LayoutParams(-2, px(72)));
        dismissPopup();
        popup = sheet(app.label, app.system
                ? getString(R.string.h_system_app)
                : getString(R.string.h_uninstall_confirm), actions);
        popup.show();
        actions.getChildAt(0).requestFocus();
    }

    private View dockAction(String title, String value, Runnable run) {
        LinearLayout p = pill(HorizonGlyph.GRID, title, true);
        ((TextView) p.getTag()).setText(value);
        p.setOnClickListener(v -> run.run());
        // The menu opens while OK is still held: only a press that starts here counts, never that release.
        boolean[] fresh = {false};
        p.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER
                    && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) fresh[0] = true;
                return !fresh[0];
            }
            if (e.getAction() == KeyEvent.ACTION_UP && !fresh[0]) return true;
            return false;
        });
        return p;
    }

    private View tile(Drawable banner, String label, int color, View.OnClickListener click,
                      View.OnLongClickListener hold) {
        LinearLayout col = column(10);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout f = new FrameLayout(this);
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(18));
            }
        });
        f.setBackgroundColor(0xFF1A1C3A);
        ImageView img = new ImageView(this);
        img.setImageDrawable(banner);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(img, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout frame = ringed(f, 18, 7, px(216), px(122));
        frame.setOnClickListener(click);
        // The hold is read from the remote's key repeats, as on the deck: Android's own long-press timer races
        // the release, and a press that lost the race opened the app instead of its menu.
        boolean[] held = {false};
        frame.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER
                    && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) held[0] = false;
                else if (!held[0]) {
                    held[0] = true;
                    hold.onLongClick(v);
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (!held[0]) v.performClick();
                held[0] = false;
            }
            return true;
        });
        col.addView(frame, wrap());
        TextView t = label(text, 18, 0xCCFFFFFF);
        t.setText(label);
        t.setMaxWidth(px(230));
        col.addView(t, wrap());
        return col;
    }

    private void showAllApps() {
        Set<String> skip = HorizonData.hidden(this);
        for (HorizonData.App a : dock) skip.add(a.pkg);
        List<HorizonData.App> others = new ArrayList<>();
        for (HorizonData.App a : HorizonData.allApps(this)) if (!skip.contains(a.pkg)) others.add(a);
        LinearLayout grid = column(28);
        LinearLayout row = null;
        for (int i = 0; i < others.size(); i++) {
            if (i % 5 == 0) {
                row = row(18);
                grid.addView(row);
            }
            HorizonData.App a = others.get(i);
            row.addView(tile(a.banner, a.label, a.color, v -> {
                dismissPopup();
                open(a.launch);
            }, v -> {
                showAppMenu(a);
                return true;
            }));
        }
        ScrollView scroll = new ScrollView(this);
        // The grid's own margin leaves room for the zoomed ring of the edge tiles; the scroll box still crops
        // whatever is scrolled out of view.
        grid.setPadding(px(10), px(12), px(10), px(44));
        scroll.setClipToPadding(true);
        // The fading edge doubles as the margin ScrollView keeps around the focused tile, so its ring and its
        // name stay on screen when the list scrolls.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(px(64));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid);
        int rows = (others.size() + 4) / 5;
        // A view is cropped to its bounds only when its parent clips children, and the panel does not (focus
        // rings): this frame does it for the scroll box.
        FrameLayout box = new FrameLayout(this);
        box.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        box.setLayoutParams(new LinearLayout.LayoutParams(-1, px(Math.min(3, rows) * 198 + 24)));
        dismissPopup();
        popup = sheet(getString(R.string.h_all_apps), getString(R.string.h_apps_count, others.size()), box);
        popup.show();
        if (!others.isEmpty()) ((ViewGroup) ((ViewGroup) grid.getChildAt(0)).getChildAt(0)).getChildAt(0).requestFocus();
    }

    private void showInputs() {
        List<HorizonData.Input> list = HorizonData.inputs(this);
        LinearLayout rowView = row(24);
        for (HorizonData.Input in : list) {
            LinearLayout p = pill(HorizonGlyph.TV, getString(R.string.h_input), true);
            ((TextView) p.getTag()).setText(in.label);
            p.setOnClickListener(v -> {
                dismissPopup();
                try {
                    startActivity(HorizonData.inputIntent(in.id, liveTvPkg));
                } catch (ActivityNotFoundException | SecurityException e) {
                    open(HorizonData.inputIntent(in.id, null));
                }
            });
            rowView.addView(p, new LinearLayout.LayoutParams(-2, px(72)));
        }
        dismissPopup();
        popup = sheet(getString(R.string.h_sources), list.isEmpty() ? getString(R.string.h_no_inputs) : getString(R.string.h_inputs_count, list.size()), rowView);
        popup.show();
    }

    private void dismissPopup() {
        if (popup != null && popup.isShowing()) popup.dismiss();
        popup = null;
    }

    private void open(Intent i) {
        if (i == null) return;
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            showToast(getString(R.string.h_cannot_open));
        }
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        open(i);
    }

    // ---- lifecycle --------------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        scene.setRunning(true);
        if (needsOnboarding() && (popup == null || !popup.isShowing())) main.post(this::showOnboarding);
        if (refreshAfterUninstall) {
            // An uninstalled dock app is skipped by loadDock; one still installed comes back unchanged.
            refreshAfterUninstall = false;
            loadDock();
        }
        main.post(tick);
        main.post(slowTick);
        refreshResumes();
        if (breathe == null) {
            // A slow breath on the clock's alpha: animating the font weight re-shaped 184 px glyphs every frame.
            breathe = ValueAnimator.ofFloat(1f, 0.86f);
            breathe.setDuration(6000);
            breathe.setRepeatMode(ValueAnimator.REVERSE);
            breathe.setRepeatCount(ValueAnimator.INFINITE);
            breathe.addUpdateListener(a -> clock.setAlpha((float) a.getAnimatedValue()));
        }
        breathe.start();
        if (!carousel.hasFocus() && getCurrentFocus() == null) carousel.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        scene.setRunning(false);
        main.removeCallbacks(tick);
        main.removeCallbacks(slowTick);
        if (breathe != null) breathe.cancel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        dismissPopup();
        if (intent.getBooleanExtra(HorizonPanelActivity.EXTRA_INPUTS, false)) {
            main.post(this::showInputs);
            return;
        }
        if (intent.getBooleanExtra(EXTRA_PICK_CITY, false)) {
            main.post(this::showCityPicker);
            return;
        }
        carousel.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (popup != null) dismissPopup();
        else carousel.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissPopup();
        bgThread.quitSafely();
    }
}
