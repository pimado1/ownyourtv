package app.ownyourtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** The Horizon home's line icons, drawn in a 24 x 24 box scaled to the view, plus the memory ring and the sun arc. */
final class HorizonGlyph extends View {
    static final int MOON = 1, SOUNDBAR = 2, CAST = 3, TV = 4, PLAY = 5, CHECK = 6, GRID = 7, FOG = 8, RAIN = 9,
            SUN = 10, NIGHT = 11, SNOW = 12, CLOUD = 13, RING = 14, ARC = 15, SPARKLE = 16, REFRESH = 17;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private int kind;
    private int color = Color.WHITE;
    private float value;
    private boolean animated;

    HorizonGlyph(Context ctx, int kind) {
        super(ctx);
        this.kind = kind;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setKind(int k) {
        if (kind == k) return;
        kind = k;
        invalidate();
    }

    void setColor(int c) {
        color = c;
        invalidate();
    }

    /** RING: used fraction 0-1. ARC: the sun's position along the day, 0-1. */
    void setValue(float v) {
        value = v;
        invalidate();
    }

    void setAnimated(boolean on) {
        animated = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (kind == RING) {
            drawRing(c, w, h);
            return;
        }
        if (kind == ARC) {
            drawArc(c, w, h);
            return;
        }
        float s = Math.min(w, h) / 24f;
        c.save();
        c.translate((w - 24 * s) / 2, (h - 24 * s) / 2);
        c.scale(s, s);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        p.setColor(color);
        float t = (SystemClock.uptimeMillis() % 100000) / 1000f;
        path.reset();
        switch (kind) {
            case MOON:
                path.moveTo(20, 14.5f);
                path.cubicTo(18.6f, 19.2f, 13.4f, 21.8f, 8.8f, 20.2f);
                path.cubicTo(4.2f, 18.6f, 2f, 13.4f, 3.8f, 8.9f);
                path.cubicTo(4.8f, 6.5f, 6.9f, 4.7f, 9.5f, 4f);
                path.cubicTo(8.1f, 8.7f, 11f, 13.6f, 15.8f, 14.8f);
                path.cubicTo(17.2f, 15.2f, 18.7f, 15.1f, 20f, 14.5f);
                c.drawPath(path, p);
                break;
            case SOUNDBAR:
                oval.set(2, 9, 22, 15);
                c.drawRoundRect(oval, 3, 3, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(7, 12, 1, p);
                c.drawCircle(12, 12, 1, p);
                c.drawCircle(17, 12, 1, p);
                break;
            case CAST:
                path.moveTo(2, 16);
                path.cubicTo(5.3f, 16, 8, 18.7f, 8, 22);
                path.moveTo(2, 12);
                path.cubicTo(7.5f, 12, 12, 16.5f, 12, 22);
                path.moveTo(6, 4);
                path.lineTo(20, 4);
                path.cubicTo(21.1f, 4, 22, 4.9f, 22, 6);
                path.lineTo(22, 18);
                path.cubicTo(22, 19.1f, 21.1f, 20, 20, 20);
                path.lineTo(16, 20);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(2.5f, 20.5f, 1, p);
                break;
            case TV:
                oval.set(3, 6, 21, 18);
                c.drawRoundRect(oval, 2, 2, p);
                c.drawLine(7, 10, 7, 14, p);
                c.drawLine(11, 10, 11, 14, p);
                break;
            case PLAY:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(7, 4.5f);
                path.lineTo(19, 12);
                path.lineTo(7, 19.5f);
                path.close();
                c.drawPath(path, p);
                break;
            case CHECK:
                p.setStrokeWidth(2.2f);
                path.moveTo(5, 12);
                path.lineTo(10, 17);
                path.lineTo(20, 7);
                c.drawPath(path, p);
                break;
            case GRID:
                p.setStrokeWidth(1.6f);
                for (int i = 0; i < 4; i++) {
                    float x = i % 2 == 0 ? 3 : 14, y = i < 2 ? 3 : 14;
                    oval.set(x, y, x + 7, y + 7);
                    c.drawRoundRect(oval, 2, 2, p);
                }
                break;
            case REFRESH:
                oval.set(3, 3, 21, 21);
                c.drawArc(oval, 200, 290, false, p);
                path.moveTo(3, 4);
                path.lineTo(3, 9);
                path.lineTo(8, 9);
                c.drawPath(path, p);
                break;
            case SPARKLE:
                p.setStrokeWidth(1.7f);
                path.moveTo(12, 3);
                path.lineTo(13.8f, 7.6f);
                path.lineTo(18.5f, 9);
                path.lineTo(13.8f, 10.4f);
                path.lineTo(12, 15);
                path.lineTo(10.2f, 10.4f);
                path.lineTo(5.5f, 9);
                path.lineTo(10.2f, 7.6f);
                path.close();
                c.drawPath(path, p);
                break;
            case FOG:
                p.setStrokeWidth(1.6f);
                float[][] bars = {{4, 8, 17}, {7, 12, 21}, {3, 16, 16}, {8, 20, 18}};
                for (int i = 0; i < bars.length; i++) {
                    float dx = (float) Math.sin(t * 2 * Math.PI / 3.2 + i) * 1.4f;
                    c.drawLine(bars[i][0] + dx, bars[i][1], bars[i][2] + dx, bars[i][1], p);
                }
                break;
            case CLOUD:
            case RAIN:
                path.moveTo(7, 15.5f);
                path.cubicTo(4.5f, 15.5f, 3, 13.6f, 3.3f, 11.4f);
                path.cubicTo(3.6f, 9.4f, 5.4f, 8.2f, 7.4f, 8.5f);
                path.cubicTo(8.3f, 5.6f, 11.6f, 4.3f, 14.4f, 5.6f);
                path.cubicTo(16.2f, 6.5f, 17.2f, 8.3f, 17.2f, 10.2f);
                path.cubicTo(19.4f, 10.2f, 21, 11.9f, 20.8f, 13.9f);
                path.cubicTo(20.6f, 15.3f, 19.4f, 15.5f, 18, 15.5f);
                path.close();
                c.drawPath(path, p);
                if (kind == RAIN) {
                    float k = (t * 1.4f) % 1f;
                    p.setAlpha((int) (255 * (1 - k)));
                    for (int i = 0; i < 3; i++) c.drawLine(8 + i * 4, 18 + k * 2, 7 + i * 4, 21 + k * 2, p);
                    p.setAlpha(255);
                }
                break;
            case SNOW:
                c.drawLine(12, 3, 12, 21, p);
                c.drawLine(4.2f, 7.5f, 19.8f, 16.5f, p);
                c.drawLine(4.2f, 16.5f, 19.8f, 7.5f, p);
                break;
            case SUN:
            case NIGHT: {
                float pulse = 1 + 0.06f * (float) Math.sin(t * 2 * Math.PI / 3);
                int core = kind == SUN ? 0xFFFFB52E : 0xFFC9D3FF;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(12, 12, 11 * pulse, new int[]{core & 0x88FFFFFF, core & 0x00FFFFFF}, null,
                        Shader.TileMode.CLAMP));
                c.drawCircle(12, 12, 11 * pulse, p);
                p.setShader(new RadialGradient(10, 10, 7, new int[]{0xFFFFFFFF, core}, null, Shader.TileMode.CLAMP));
                c.drawCircle(12, 12, 6.5f * pulse, p);
                p.setShader(null);
                break;
            }
            default:
                break;
        }
        c.restore();
        if (animated && (kind == FOG || kind == RAIN || kind == SUN || kind == NIGHT)) postInvalidateDelayed(50);
    }

    private void drawRing(Canvas c, float w, float h) {
        float stroke = w * 0.11f;
        oval.set(stroke, stroke, w - stroke, h - stroke);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setColor(0x29FFFFFF);
        c.drawArc(oval, 0, 360, false, p);
        p.setColor(color);
        c.drawArc(oval, -90, 360 * Math.max(0.02f, Math.min(1f, value)), false, p);
    }

    private void drawArc(Canvas c, float w, float h) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.05f);
        p.setColor(0x47FFFFFF);
        p.setPathEffect(new DashPathEffect(new float[]{h * .1f, h * .2f}, 0));
        path.reset();
        path.moveTo(h * .2f, h * .9f);
        path.quadTo(w / 2, -h * .55f, w - h * .2f, h * .9f);
        c.drawPath(path, p);
        p.setPathEffect(null);
        float f = Math.max(0f, Math.min(1f, value));
        float x0 = h * .2f, x2 = w - h * .2f, y0 = h * .9f, cy = -h * .55f;
        float x = (1 - f) * (1 - f) * x0 + 2 * (1 - f) * f * (w / 2) + f * f * x2;
        float y = (1 - f) * (1 - f) * y0 + 2 * (1 - f) * f * cy + f * f * y0;
        boolean night = value < 0 || value > 1;
        int core = night ? 0xFFDFE6FF : 0xFFFFD36B;
        if (night) {
            x = w / 2;
            y = h * .5f;
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y, h * .45f, new int[]{core & 0xAAFFFFFF, core & 0x00FFFFFF}, null,
                Shader.TileMode.CLAMP));
        c.drawCircle(x, y, h * .45f, p);
        p.setShader(null);
        p.setColor(core);
        c.drawCircle(x, y, h * .2f, p);
    }
}
