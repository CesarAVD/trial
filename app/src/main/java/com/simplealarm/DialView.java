package com.simplealarm;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/**
 * 24-hour dial. 00:00 at top, 12:00 at bottom, clockwise. Two draggable thumbs:
 * earliest (x) and latest (y). The arc x→y is colored gentle→aggressive.
 * Values snap to WakeWindow.STEP_MIN.
 */
public class DialView extends View {

    public interface Listener {
        /** finished = finger lifted (persist/schedule then). */
        void onWindowChanged(int startMin, int endMin, boolean finished);
    }

    static final int GENTLE = Color.rgb(80, 200, 140);
    static final int MID = Color.rgb(250, 190, 60);
    static final int HOT = Color.rgb(235, 60, 60);

    private int startMin = 390, endMin = 435;
    private Listener listener;
    private int dragging = -1;          // -1 none, 0 start, 1 end

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix gradMatrix = new Matrix();

    private float cx, cy, radius;
    private final float strokeW, thumbR;

    public DialView(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        strokeW = 18 * d;
        thumbR = 16 * d;
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int fg = night ? Color.rgb(235, 235, 240) : Color.rgb(30, 30, 40);
        int faint = night ? Color.argb(60, 255, 255, 255) : Color.argb(40, 0, 0, 0);

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(strokeW);
        track.setColor(faint);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(strokeW);
        arc.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(fg);
        tick.setStrokeWidth(1.5f * d);
        tick.setAlpha(140);
        label.setColor(fg);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(13 * d);
        label.setAlpha(170);
        thumb.setStyle(Paint.Style.FILL);
        thumbRing.setStyle(Paint.Style.STROKE);
        thumbRing.setStrokeWidth(3 * d);
        thumbRing.setColor(Color.WHITE);
        big.setColor(fg);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(34 * d);
        big.setFakeBoldText(true);
        small.setColor(fg);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(15 * d);
        small.setAlpha(180);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setWindow(int start, int end) {
        startMin = norm(start);
        endMin = norm(end);
        invalidate();
    }

    private static int norm(int m) {
        return ((m % WakeWindow.DAY_MIN) + WakeWindow.DAY_MIN) % WakeWindow.DAY_MIN;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w);   // square
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) / 2f - thumbR - 4;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    /** Canvas angle in degrees (0 = 3 o'clock) for a minute of day; 00:00 at top. */
    private static float angleOf(int min) {
        return min / (float) WakeWindow.DAY_MIN * 360f - 90f;
    }

    private float px(int min, float r) {
        return cx + r * (float) Math.cos(Math.toRadians(angleOf(min)));
    }

    private float py(int min, float r) {
        return cy + r * (float) Math.sin(Math.toRadians(angleOf(min)));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawCircle(cx, cy, radius, track);

        // hour ticks + labels every 3 h
        float inner = radius - strokeW / 2f - 4;
        for (int h = 0; h < 24; h++) {
            int m = h * 60;
            float len = (h % 3 == 0 ? 14 : 7) * getResources().getDisplayMetrics().density;
            c.drawLine(px(m, inner), py(m, inner), px(m, inner - len), py(m, inner - len), tick);
            if (h % 3 == 0) {
                float lr = inner - len - label.getTextSize();
                c.drawText(String.format("%02d", h), px(m, lr), py(m, lr) + label.getTextSize() / 3f, label);
            }
        }

        // window arc, gradient gentle -> hot along the sweep
        int dur = norm(endMin - startMin);
        float sweep = Math.max(dur / (float) WakeWindow.DAY_MIN * 360f, 0.5f);
        float frac = sweep / 360f;
        SweepGradient g = new SweepGradient(cx, cy,
                new int[]{GENTLE, MID, HOT, HOT},
                new float[]{0f, frac * 0.55f, frac, 1f});
        gradMatrix.setRotate(angleOf(startMin), cx, cy);
        g.setLocalMatrix(gradMatrix);
        arc.setShader(g);
        c.drawArc(oval, angleOf(startMin), sweep, false, arc);

        // thumbs
        drawThumb(c, startMin, GENTLE, dragging == 0);
        drawThumb(c, endMin, HOT, dragging == 1);

        // center text
        c.drawText(WakeWindow.hhmm(startMin) + " – " + WakeWindow.hhmm(endMin),
                cx, cy + big.getTextSize() / 3f, big);
        int n = WakeWindow.offsets(dur).length;
        String sub = n + (n == 1 ? " alarm" : " alarms") + " · "
                + (dur >= 60 ? (dur / 60) + " h " : "") + (dur % 60) + " min";
        c.drawText(sub, cx, cy + big.getTextSize() / 3f + small.getTextSize() * 1.8f, small);
    }

    private void drawThumb(Canvas c, int min, int color, boolean active) {
        float r = active ? thumbR * 1.2f : thumbR;
        thumb.setColor(color);
        c.drawCircle(px(min, radius), py(min, radius), r, thumb);
        c.drawCircle(px(min, radius), py(min, radius), r, thumbRing);
    }

    /** Minute of day under (x,y), snapped to STEP_MIN. */
    private int minuteAt(float x, float y) {
        double deg = Math.toDegrees(Math.atan2(y - cy, x - cx)) + 90.0;
        if (deg < 0) deg += 360;
        int m = (int) Math.round(deg / 360.0 * WakeWindow.DAY_MIN / WakeWindow.STEP_MIN)
                * WakeWindow.STEP_MIN;
        return norm(m);
    }

    private static int circDist(int a, int b) {
        int d = Math.abs(a - b) % WakeWindow.DAY_MIN;
        return Math.min(d, WakeWindow.DAY_MIN - d);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float dist = (float) Math.hypot(x - cx, y - cy);
                if (dist < radius * 0.55f) return false;    // center: let parent scroll
                int m = minuteAt(x, y);
                dragging = circDist(m, startMin) <= circDist(m, endMin) ? 0 : 1;
                getParent().requestDisallowInterceptTouchEvent(true);
                update(m, false);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) update(minuteAt(x, y), false);
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                // fall through
            case MotionEvent.ACTION_CANCEL:
                if (dragging >= 0) {
                    dragging = -1;
                    invalidate();
                    if (listener != null) listener.onWindowChanged(startMin, endMin, true);
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void update(int m, boolean finished) {
        if (dragging == 0) startMin = m; else endMin = m;
        invalidate();
        if (listener != null) listener.onWindowChanged(startMin, endMin, finished);
    }
}
