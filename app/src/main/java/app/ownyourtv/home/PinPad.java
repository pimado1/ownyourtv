package app.ownyourtv.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.ownyourtv.R;

/**
 * The parent code: four digits typed with the remote's arrows and OK on a keypad, or with its number keys. Used to
 * set the code and to ask for it before a child's limits are lifted.
 */
public final class PinPad {
    public interface Done {
        void pin(String code);
    }

    private PinPad() {}

    public static Dialog show(Activity a, String title, Done done) {
        float u = a.getResources().getDisplayMetrics().widthPixels / 1920f;
        Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        FrameLayout overlay = new FrameLayout(a);
        overlay.setBackgroundColor(0xCC03050F);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (48 * u);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(round(0xFF0E1028, 36 * u, 1, 0x29FFFFFF));

        TextView t = text(a, title, 40 * u, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        panel.addView(t, new LinearLayout.LayoutParams(-2, -2));
        TextView dots = text(a, "", 52 * u, 0xFFC6F432, true);
        dots.setLetterSpacing(0.4f);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = (int) (20 * u);
        dl.bottomMargin = (int) (24 * u);
        panel.addView(dots, dl);
        StringBuilder code = new StringBuilder();
        Runnable paint = () -> {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 4; i++) s.append(i < code.length() ? "●" : "○");
            dots.setText(s);
        };
        paint.run();

        Runnable finish = () -> {
            if (code.length() < 4) return;
            String c = code.toString();
            d.dismiss();
            done.pin(c);
        };
        java.util.function.IntConsumer press = digit -> {
            if (digit < 0) {
                if (code.length() > 0) code.setLength(code.length() - 1);
            } else if (code.length() < 4) {
                code.append(digit);
            }
            paint.run();
            if (code.length() == 4) dots.postDelayed(finish, 180);
        };
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        View first = null;
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                String k = keys[r * 3 + c];
                TextView b = text(a, k, 34 * u, Color.WHITE, true);
                b.setGravity(Gravity.CENTER);
                b.setFocusable(true);
                b.setClickable(true);
                b.setDefaultFocusHighlightEnabled(false);
                StateListDrawable bg = new StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_focused}, round(0x52FFFFFF, 24 * u, 3, Color.WHITE));
                bg.addState(new int[]{}, round(0x1AFFFFFF, 24 * u, 0, 0));
                b.setBackground(bg);
                b.setOnClickListener(v -> {
                    if ("OK".equals(k)) finish.run();
                    else press.accept("⌫".equals(k) ? -1 : Integer.parseInt(k));
                });
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams((int) (120 * u), (int) (88 * u));
                bl.setMargins((int) (8 * u), (int) (8 * u), (int) (8 * u), (int) (8 * u));
                row.addView(b, bl);
                if (first == null) first = b;
            }
            // Sized by its keys, not stretched to the title: a short title must not crop the third column.
            panel.addView(row, new LinearLayout.LayoutParams(-2, -2));
        }
        overlay.addView(panel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        d.setContentView(overlay);
        // The remote's number keys type straight into the code.
        d.setOnKeyListener((dialog, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                press.accept(keyCode - KeyEvent.KEYCODE_0);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                press.accept(-1);
                return true;
            }
            return false;
        });
        d.show();
        View f = first;
        f.post(f::requestFocus);
        return d;
    }

    static GradientDrawable round(int fill, float radius, float stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke > 0) g.setStroke((int) Math.max(1, stroke), strokeColor);
        return g;
    }

    static TextView text(Activity a, String s, float px, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(font(a, bold));
        return t;
    }

    /** The home screen's own type: Bricolage for headings, Manrope for the rest. */
    static Typeface font(Activity a, boolean display) {
        try {
            return new Typeface.Builder(a.getAssets(), display ? "fonts/bricolage.ttf" : "fonts/manrope.ttf")
                    .setFontVariationSettings(display ? "'wght' 320, 'opsz' 96" : "'wght' 600").build();
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    /** The heading a code pad shows: set a new code, or type it. */
    public static String title(Activity a, boolean set) {
        return a.getString(set ? R.string.pf_pin_new : R.string.pf_pin_ask);
    }
}
