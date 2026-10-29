package app.ownyourtv.home;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import app.ownyourtv.R;

/**
 * Bedtime, or a child's time used up for the day: a calm night sky that says so, then the TV goes to sleep. The
 * parent code lets the child stay up, or gives back half an hour.
 */
public final class RestActivity extends Activity {
    /** Seconds the goodnight stays on screen before the TV is put to sleep. */
    private static final int GOODNIGHT_S = 12;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sleep = GuardService::sleep;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        float u = getResources().getDisplayMetrics().widthPixels / 1920f;
        Profiles.Profile p = Profiles.current(this);
        if (p == null || !p.kid) {
            finish();
            return;
        }
        boolean night = Profiles.bedtimeNow(this, p);
        FrameLayout root = new FrameLayout(this);
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        SkyScene sky = new SkyScene(this, Typeface.DEFAULT);
        sky.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        sky.setPivotX(0);
        sky.setPivotY(0);
        sky.setScaleX(2f);
        sky.setScaleY(2f);
        sky.setAccent(p.color);
        sky.setDimmed(true);
        root.addView(sky, new FrameLayout.LayoutParams(sw / 2, sh / 2));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = PinPad.text(this, night ? getString(R.string.pf_goodnight, p.name) : getString(R.string.pf_time_up),
                84 * u, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView sub = PinPad.text(this, getString(night ? R.string.pf_goodnight_b : R.string.pf_time_up_b), 30 * u,
                0xCCFFFFFF, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = (int) (20 * u);
        col.addView(sub, sl);
        TextView parent = PinPad.text(this, getString(R.string.pf_parent), 24 * u, Color.WHITE, true);
        parent.setGravity(Gravity.CENTER);
        int px = (int) (32 * u), py = (int) (18 * u);
        parent.setPadding(px, py, px, py);
        parent.setFocusable(true);
        parent.setClickable(true);
        parent.setDefaultFocusHighlightEnabled(false);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, PinPad.round(0x52FFFFFF, 30 * u, 3, Color.WHITE));
        bg.addState(new int[]{}, PinPad.round(0x1AFFFFFF, 30 * u, 1, 0x33FFFFFF));
        parent.setBackground(bg);
        parent.setOnClickListener(v -> {
            main.removeCallbacks(sleep);
            PinPad.show(this, PinPad.title(this, false), code -> {
                if (!Profiles.pinIs(this, code)) {
                    Toast.makeText(this, R.string.pf_pin_wrong, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (night) {
                    Profiles.stayUp(this, p);
                } else {
                    Profiles.extend(this, p, 30);
                    Toast.makeText(this, R.string.pf_more_time, Toast.LENGTH_LONG).show();
                }
                finish();
            });
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.topMargin = (int) (56 * u);
        col.addView(parent, pl);
        root.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        parent.post(parent::requestFocus);
        col.setAlpha(0f);
        col.animate().alpha(1f).setDuration(1200).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Profiles.Profile p = Profiles.current(this);
        if (p == null || !p.kid || (!Profiles.bedtimeNow(this, p) && !Profiles.timeUp(this, p))) {
            finish();
            return;
        }
        main.removeCallbacks(sleep);
        main.postDelayed(sleep, GOODNIGHT_S * 1000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(sleep);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    // Back leads nowhere: only the parent code lifts the limit.
    @Deprecated
    @Override
    public void onBackPressed() {}
}
