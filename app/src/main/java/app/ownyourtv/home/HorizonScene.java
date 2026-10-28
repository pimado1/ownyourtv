package app.ownyourtv.home;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * The Horizon home's living backdrop: sky by the time of day, twinkling stars, sparkles, shooting stars, an aurora,
 * three hills in parallax, the current weather (fog, rain, snow) and a pool of light in the
 * focused app's colour. Everything is drawn from shaders and paths, no bitmaps and no blur, at about 30 frames a
 * second while Home is on screen; {@link #setRunning} stops the loop when Home is paused.
 */
final class HorizonScene extends View {
    static final int WEATHER_CLEAR = 0, WEATHER_FOG = 1, WEATHER_RAIN = 2, WEATHER_SNOW = 3;
    private static final long FRAME_MS = 33;
    private static final float W = 1920f, H = 1080f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final float[] starX = new float[110], starY = new float[110], starR = new float[110];
    private final float[] starSpeed = new float[110], starPhase = new float[110];
    private final float[] dropX = new float[90], dropSpeed = new float[90], dropPhase = new float[90];
    private final float[] flakeX = new float[80], flakeSpeed = new float[80], flakePhase = new float[80], flakeR = new float[80];
    private static final float[][] GLINTS = {{312, 96, 5f, 1.2f}, {884, 58, 6.5f, 3.4f}, {1180, 330, 4.2f, .4f},
            {1560, 46, 7f, 2.6f}, {640, 300, 5.6f, 4.1f}, {1010, 190, 8f, 1.9f}};
    /** x, y, angle in degrees, period s, delay s. */
    private static final float[][] METEORS = {{520, 70, 24, 13, 2}, {1150, 40, 32, 17, 6.5f}, {260, 210, 18, 21, 10},
            {1420, 150, 150, 19, 14}};
    private final Path hillFar = new Path(), hillMid = new Path(), hillNear = new Path();
    private final RadialGradient soft;
    private final long start = SystemClock.uptimeMillis();

    private int accent = 0xFFFF2D55, accentFrom = accent, accentTo = accent;
    private long accentAt;
    private float parallax, parallaxTarget;
    private float dim, dimTarget;
    private int weather = WEATHER_CLEAR;
    private float sunrise = 8f, sunset = 17.5f;
    private boolean running;
    private int skyKey = -1;
    private LinearGradient sky;
    private int[] hills = new int[3];
    private float auroraLevel = 1f, starLevel = 1f;

    HorizonScene(Context ctx, Typeface text) {
        super(ctx);
        Random r = new Random(7);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextFloat() * W;
            starY[i] = r.nextFloat() * 560;
            starR[i] = 0.6f + r.nextFloat() * 1.3f;
            starSpeed[i] = (float) (2 * Math.PI / (2.5 + r.nextFloat() * 4));
            starPhase[i] = r.nextFloat() * 6.3f;
        }
        for (int i = 0; i < dropX.length; i++) {
            dropX[i] = r.nextFloat() * 2000;
            dropSpeed[i] = 0.6f + r.nextFloat() * 0.5f;
            dropPhase[i] = r.nextFloat();
        }
        for (int i = 0; i < flakeX.length; i++) {
            flakeX[i] = r.nextFloat() * W;
            flakeSpeed[i] = 7 + r.nextFloat() * 6;
            flakePhase[i] = r.nextFloat();
            flakeR[i] = 1.5f + r.nextFloat() * 2.5f;
        }
        hill(hillFar, 520, new float[]{0, 300, 180, 230, 330, 250, 470, 210, 620, 170, 760, 240, 900, 220, 1060, 196,
                1180, 140, 1340, 170, 1500, 200, 1640, 150, 1800, 190, 1930, 220, 2020, 200, 2100, 210});
        hill(hillMid, 420, new float[]{0, 260, 160, 220, 300, 200, 440, 230, 600, 264, 720, 190, 880, 200, 1040, 210,
                1150, 260, 1320, 236, 1480, 214, 1600, 170, 1760, 206, 1900, 238, 2000, 220, 2100, 236});
        hill(hillNear, 300, new float[]{0, 170, 220, 130, 380, 160, 560, 140, 760, 118, 900, 168, 1080, 156, 1260, 144,
                1400, 110, 1600, 136, 1800, 162, 1960, 140, 2100, 150});
        soft = new RadialGradient(0, 0, 1, new int[]{Color.WHITE, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        line.setStyle(Paint.Style.STROKE);
        label.setTypeface(text);
        label.setLetterSpacing(0.2f);
    }

    /** Smooth cubic path through the points (x, y pairs in a 2100 x height box), closed along the bottom. */
    private static void hill(Path p, float height, float[] pts) {
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) {
            float mx = (pts[i - 2] + pts[i]) / 2f;
            p.cubicTo(mx, pts[i - 1], mx, pts[i + 1], pts[i], pts[i + 1]);
        }
        p.lineTo(2100, height);
        p.lineTo(0, height);
        p.close();
    }

    void setRunning(boolean on) {
        if (running == on) return;
        running = on;
        if (on) postInvalidateOnAnimation();
    }

    void setAccent(int color) {
        if (color == accentTo) return;
        accentFrom = accent;
        accentTo = color;
        accentAt = SystemClock.uptimeMillis();
    }

    void setFocusIndex(int index) {
        parallaxTarget = index;
    }

    void setDimmed(boolean on) {
        dimTarget = on ? 1f : 0f;
    }

    void setWeather(int kind, float sunriseHour, float sunsetHour) {
        weather = kind;
        sunrise = sunriseHour;
        sunset = sunsetHour;
        skyKey = -1;
    }

    private boolean alwaysNight = true;

    /** true (the default): the night sky at any hour; false ("auto"): follow the sun. */
    void setAlwaysNight(boolean on) {
        alwaysNight = on;
        skyKey = -1;
    }

    /** 0 night, 1 dawn, 2 day, 3 dusk, from the clock and today's sun times. */
    private int moment() {
        if (alwaysNight) return 0;
        Calendar c = Calendar.getInstance();
        float h = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f;
        if (h >= sunrise - 0.75f && h < sunrise + 0.75f) return 1;
        if (h >= sunrise + 0.75f && h < sunset - 0.75f) return 2;
        if (h >= sunset - 0.75f && h < sunset + 0.75f) return 3;
        return 0;
    }

    private void ensureSky() {
        int m = moment();
        if (m == skyKey && sky != null) return;
        skyKey = m;
        int[][] colors = {
                {0xFF03050F, 0xFF0B1033, 0xFF2A1850, 0xFF4A2160},
                {0xFF141A44, 0xFF5B3A78, 0xFFE0787A, 0xFFFFC08A},
                {0xFF123F8C, 0xFF2F72CC, 0xFF7FB1E8, 0xFFCFE4FA},
                {0xFF0D0B2B, 0xFF3B1E5C, 0xFFB0457A, 0xFFFF9A5C}};
        int[][] hillColors = {
                {0xFF151033, 0xFF0D0A24, 0xFF060412}, {0xFF3A2550, 0xFF24183A, 0xFF140D22},
                {0xFF2D5F78, 0xFF1F4459, 0xFF122A38}, {0xFF2A1640, 0xFF1A0E2C, 0xFF0D0718}};
        float[] stars = {1f, .25f, 0f, .5f};
        float[] aurora = {.95f, .2f, 0f, .35f};
        sky = new LinearGradient(0, 0, 0, H, colors[m], new float[]{0f, .42f, .76f, 1f}, Shader.TileMode.CLAMP);
        hills = hillColors[m];
        float wx = weather == WEATHER_FOG ? .45f : weather == WEATHER_CLEAR ? 1f : .2f;
        starLevel = stars[m] * wx;
        auroraLevel = aurora[m] * (weather == WEATHER_RAIN || weather == WEATHER_SNOW ? .35f : 1f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float t = (now - start) / 1000f;
        float u = getWidth() / W;
        canvas.save();
        canvas.scale(u, getHeight() / H);
        ensureSky();

        float a = Math.min(1f, (now - accentAt) / 900f);
        accent = lerpColor(accentFrom, accentTo, ease(a));
        parallax += (parallaxTarget - parallax) * 0.08f;
        dim += (dimTarget - dim) * 0.05f;

        paint.setShader(sky);
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
        paint.setAlpha(255);

        drawStars(canvas, t);
        drawAurora(canvas, t);
        drawSpill(canvas);
        drawHills(canvas);
        if (weather == WEATHER_FOG) drawFog(canvas, t);
        else if (weather == WEATHER_RAIN) drawRain(canvas, t);
        else if (weather == WEATHER_SNOW) drawSnow(canvas, t);
        drawScrims(canvas);
        canvas.restore();

        if (running) postInvalidateDelayed(FRAME_MS);
    }

    private void drawStars(Canvas c, float t) {
        if (starLevel <= 0.01f) return;
        paint.setColor(Color.WHITE);
        for (int i = 0; i < starX.length; i++) {
            float tw = 0.15f + 0.85f * (0.5f + 0.5f * (float) Math.sin(t * starSpeed[i] + starPhase[i]));
            paint.setAlpha((int) (255 * tw * starLevel));
            c.drawCircle(starX[i], starY[i], starR[i], paint);
        }
        for (float[] g : GLINTS) {
            float k = (float) (0.5 + 0.5 * Math.sin((t + g[3]) * 2 * Math.PI / g[2]));
            float alpha = (0.25f + 0.75f * k) * starLevel;
            c.save();
            c.translate(g[0], g[1]);
            c.rotate(25 * k);
            c.scale(0.6f + 0.55f * k, 0.6f + 0.55f * k);
            drawSoft(c, 0, 0, 10, 10, 0xFFC8DCFF, alpha * .8f);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setAlpha((int) (255 * alpha));
            c.drawCircle(0, 0, 2.2f, paint);
            line.setPathEffect(null);
            line.setStrokeWidth(1f);
            line.setShader(rayH);
            line.setAlpha((int) (255 * alpha));
            c.drawLine(-17, 0, 17, 0, line);
            line.setShader(rayV);
            c.drawLine(0, -17, 0, 17, line);
            line.setShader(null);
            c.restore();
        }
        for (float[] m : METEORS) drawMeteor(c, t, m);
    }

    private void drawMeteor(Canvas c, float t, float[] m) {
        float phase = ((t - m[4]) % m[3] + m[3]) % m[3] / m[3];
        if (t < m[4] || phase > 0.08f) return;
        float p = phase / 0.06f;
        float x = -260 + Math.min(p, 1f) * 880 + Math.max(0, p - 1) * 140 / 0.33f;
        float fade = phase < 0.01f ? phase / 0.01f : phase > 0.06f ? 1 - (phase - 0.06f) / 0.02f : 1f;
        float len = 260 * (0.3f + 0.7f * Math.min(p, 1f));
        c.save();
        c.translate(m[0], m[1]);
        c.rotate(m[2]);
        line.setStrokeWidth(2f);
        line.setShader(new LinearGradient(x - len, 0, x, 0, new int[]{0x00FFFFFF, 0x80BED7FF, Color.WHITE},
                new float[]{0, .6f, 1}, Shader.TileMode.CLAMP));
        line.setAlpha((int) (255 * fade * starLevel));
        c.drawLine(x - len, 0, x, 0, line);
        line.setShader(null);
        c.restore();
    }

    private void drawAurora(Canvas c, float t) {
        if (auroraLevel <= 0.01f) return;
        setScreen(paint, true);
        float s1 = (float) Math.sin(t * 2 * Math.PI / 44), s2 = (float) Math.sin(t * 2 * Math.PI / 56 + 1),
                s3 = (float) Math.sin(t * 2 * Math.PI / 68 + 2);
        // Every band leans toward the focused app's colour, so the whole aurora changes with the dock.
        drawSoft(c, 730 + 140 * s1, 210 + 15 * s1, 560, 150 * (1 + .15f * s1), lerpColor(0xFF28FFBE, accent, .45f),
                .6f * auroraLevel);
        drawSoft(c, 1360 - 160 * s2, 270 - 20 * s2, 620 * (1 + .1f * s2), 130, lerpColor(0xFF785AFF, accent, .55f),
                .55f * auroraLevel);
        drawSoft(c, 870 + 45 * s3, 130 - 20 * s3, 450 * (1 + .12f * s3), 110, accent, .7f * auroraLevel);
        drawSoft(c, 1250 - 60 * s1, 200, 380, 90, lerpColor(0xFF28DCFF, accent, .5f), .45f * auroraLevel);
        drawSoft(c, 520 + 90 * s2, 300, 420, 100, accent, .35f * auroraLevel);
        setScreen(paint, false);
    }

    private void drawSpill(Canvas c) {
        drawSoft(c, W / 2, H * .78f, 1100, 520, accent, .5f);
    }

    private void drawHills(Canvas c) {
        paint.setShader(null);
        float[] speed = {10, 24, 44};
        float[] top = {H - 520, H - 420, H - 300};
        Path[] p = {hillFar, hillMid, hillNear};
        for (int i = 0; i < 3; i++) {
            c.save();
            c.translate(-90 - parallax * speed[i], top[i]);
            paint.setColor(hills[i]);
            c.drawPath(p[i], paint);
            c.restore();
        }
    }

    private void drawFog(Canvas c, float t) {
        float[][] bands = {{770, 130, 40, 1f}, {910, 150, 60, .8f}, {630, 110, 75, .55f}};
        for (int i = 0; i < bands.length; i++) {
            float[] b = bands[i];
            float x = W / 2 + (float) Math.sin(t * 2 * Math.PI / b[2] * (i == 1 ? -1 : 1)) * 0.08f * W * 1.6f;
            drawSoft(c, x, b[0], W * 0.8f, b[1], 0xFFDCE2FF, .30f * b[3]);
        }
    }

    private void drawRain(Canvas c, float t) {
        line.setStrokeWidth(2f);
        line.setColor(Color.rgb(190, 215, 255));
        for (int i = 0; i < dropX.length; i++) {
            float p = ((t / dropSpeed[i]) + dropPhase[i]) % 1f;
            float y = -60 + p * 1180, x = dropX[i] - p * 60;
            line.setAlpha(110);
            c.drawLine(x, y, x - 2.4f, y + 46, line);
        }
    }

    private void drawSnow(Canvas c, float t) {
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < flakeX.length; i++) {
            float p = ((t / flakeSpeed[i]) + flakePhase[i]) % 1f;
            float y = -20 + p * 1120, x = flakeX[i] + (float) Math.sin(p * Math.PI * 2) * 40;
            paint.setAlpha(200);
            c.drawCircle(x, y, flakeR[i], paint);
        }
    }

    private void drawScrims(Canvas c) {
        paint.setAlpha(255);
        paint.setShader(scrimTop);
        c.drawRect(0, 0, W, 420, paint);
        paint.setShader(scrimBottom);
        c.drawRect(0, H - 360, W, H, paint);
        paint.setShader(null);
        if (dim > 0.01f) {
            paint.setShader(dimShade);
            paint.setAlpha((int) (255 * dim));
            c.drawRect(0, 0, W, H, paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }
    }

    private final LinearGradient rayH = new LinearGradient(-17, 0, 17, 0,
            new int[]{0x00FFFFFF, Color.WHITE, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    private final LinearGradient rayV = new LinearGradient(0, -17, 0, 17,
            new int[]{0x00FFFFFF, Color.WHITE, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    private final LinearGradient scrimTop = new LinearGradient(0, 0, 0, 420, 0x8C03050F, 0x0003050F,
            Shader.TileMode.CLAMP);
    private final LinearGradient scrimBottom = new LinearGradient(0, H, 0, H - 360, 0xC703050F, 0x0003050F,
            Shader.TileMode.CLAMP);
    private final RadialGradient dimShade = new RadialGradient(W / 2, H * .7f, W * .75f,
            new int[]{0x26000000, 0x99000000}, null, Shader.TileMode.CLAMP);
    private final java.util.HashMap<Integer, android.graphics.PorterDuffColorFilter> tints = new java.util.HashMap<>();

    private android.graphics.ColorFilter tint(int color) {
        android.graphics.PorterDuffColorFilter f = tints.get(color);
        if (f == null) {
            if (tints.size() > 48) tints.clear();
            f = new android.graphics.PorterDuffColorFilter(color | 0xFF000000, PorterDuff.Mode.MULTIPLY);
            tints.put(color, f);
        }
        return f;
    }

    /** A soft ellipse: the shared white-to-clear radial gradient, stretched and tinted through the paint colour. */
    private void drawSoft(Canvas c, float cx, float cy, float rx, float ry, int color, float alpha) {
        matrix.setScale(rx, ry);
        matrix.postTranslate(cx, cy);
        soft.setLocalMatrix(matrix);
        paint.setShader(soft);
        paint.setColorFilter(tint(color));
        paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
        c.drawRect(cx - rx, cy - ry, cx + rx, cy + ry, paint);
        paint.setColorFilter(null);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private static void setScreen(Paint p, boolean on) {
        if (Build.VERSION.SDK_INT >= 29) p.setBlendMode(on ? BlendMode.SCREEN : null);
        else p.setXfermode(on ? new PorterDuffXfermode(PorterDuff.Mode.SCREEN) : null);
    }

    private static float ease(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    static int lerpColor(int a, int b, float f) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
