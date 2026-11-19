package com.simplealarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * 24-hour dial. 00:00 at top, clockwise. Two draggable thumbs: earliest (x) and
 * latest (y). Glowing arc x→y graded gentle→hot, with a dot per ring.
 * Values snap to WakeWindow.STEP_MIN.
 */
public class DialView extends View {

    public interface Listener {
        /** finished = finger lifted (persist/schedule then). */
        void onWindowChanged(int startMin, int endMin, boolean finished);
    }

    private int startMin = 390, endMin = 435;
    private Listener listener;
    private int dragging = -1;          // -1 none, 0 start, 1 end

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caps = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix gradMatrix = new Matrix();

    private float cx, cy, radius;
    private final float d, strokeW, thumbR;

    public DialView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        strokeW = 14 * d;
        thumbR = 13 * d;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(strokeW);
        track.setColor(0x10FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(strokeW);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(strokeW * 2.6f);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setAlpha(38);
        dot.setColor(0x40FFFFFF);
        label.setColor(Ui.TEXT2);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(12 * d);
        label.setTypeface(Ui.MEDIUM);
        ringDot.setColor(0x8C0B0D12);
        thumbFill.setColor(0xFFFFFFFF);
        caps.setColor(Ui.TEXT2);
        caps.setTextAlign(Paint.Align.CENTER);
        caps.setTextSize(11 * d);
        caps.setTypeface(Ui.MEDIUM);
        caps.setLetterSpacing(0.16f);
        big.setColor(Ui.TEXT);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(38 * d);
        big.setTypeface(Ui.THIN);
        small.setColor(Ui.TEXT2);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(14 * d);
        small.setTypeface(Ui.REGULAR);
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
        return Math.floorMod(m, WakeWindow.DAY_MIN);
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
        radius = Math.min(w, h) / 2f - strokeW * 1.4f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    /** Canvas angle in degrees (0 = 3 o'clock) for a minute of day; 00:00 at top. */
    private static float angleOf(float min) {
        return min / WakeWindow.DAY_MIN * 360f - 90f;
    }

    private float px(float min, float r) {
        return cx + r * (float) Math.cos(Math.toRadians(angleOf(min)));
    }

    private float py(float min, float r) {
        return cy + r * (float) Math.sin(Math.toRadians(angleOf(min)));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawCircle(cx, cy, radius, track);

        // hour dots inside the track; labels at 00/06/12/18
        float inner = radius - strokeW - 6 * d;
        for (int h = 0; h < 24; h++) {
            int m = h * 60;
            boolean major = h % 6 == 0;
            c.drawCircle(px(m, inner), py(m, inner), (major ? 2.2f : 1.3f) * d, dot);
            if (major) {
                float lr = inner - 16 * d;
                c.drawText(String.format("%02d", h), px(m, lr), py(m, lr) + label.getTextSize() / 3f, label);
            }
        }

        // window arc + glow, gradient gentle -> hot along the sweep
        int dur = norm(endMin - startMin);
        float sweep = Math.max(dur / (float) WakeWindow.DAY_MIN * 360f, 0.5f);
        float frac = sweep / 360f;
        SweepGradient g = new SweepGradient(cx, cy,
                new int[]{Ui.GENTLE, Ui.GENTLE, Ui.MID, Ui.HOT, Ui.HOT},
                new float[]{0f, frac * 0.05f, frac * 0.55f, frac, 1f});
        gradMatrix.setRotate(angleOf(startMin), cx, cy);
        g.setLocalMatrix(gradMatrix);
        glow.setShader(g);
        arc.setShader(g);
        c.drawArc(oval, angleOf(startMin), sweep, false, glow);
        c.drawArc(oval, angleOf(startMin), sweep, false, arc);

        // a small dot for every ring (skip when they'd merge)
        int[] offs = WakeWindow.offsets(dur);
        float spacingPx = (float) (radius * Math.toRadians(360.0 * WakeWindow.STEP_MIN / WakeWindow.DAY_MIN));
        if (spacingPx > 5 * d) {
            for (int i = 1; i < offs.length - 1; i++) {
                float m = startMin + offs[i];
                c.drawCircle(px(m, radius), py(m, radius), 1.8f * d, ringDot);
            }
        }

        drawThumb(c, startMin, Ui.GENTLE, dragging == 0);
        drawThumb(c, endMin, Ui.HOT, dragging == 1);

        // center
        float base = cy + big.getTextSize() / 3f;
        c.drawText("WAKE WINDOW", cx, base - big.getTextSize() - 4 * d, caps);
        c.drawText(WakeWindow.hhmm(startMin) + " – " + WakeWindow.hhmm(endMin), cx, base, big);
        int n = offs.length;
        String sub = n + (n == 1 ? " alarm" : " alarms") + "  ·  "
                + (dur >= 60 ? (dur / 60) + " h " : "") + (dur % 60) + " min";
        c.drawText(sub, cx, base + small.getTextSize() * 2.2f, small);
    }

    private void drawThumb(Canvas c, int min, int color, boolean active) {
        float x = px(min, radius), y = py(min, radius);
        float r = active ? thumbR * 1.25f : thumbR;
        thumbHalo.setColor(Ui.withAlpha(color, active ? 90 : 55));
        c.drawCircle(x, y, r * 1.7f, thumbHalo);
        c.drawCircle(x, y, r, thumbFill);
        thumbHalo.setColor(color);
        c.drawCircle(x, y, r * 0.42f, thumbHalo);
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
        int dd = Math.abs(a - b) % WakeWindow.DAY_MIN;
        return Math.min(dd, WakeWindow.DAY_MIN - dd);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float dist = (float) Math.hypot(x - cx, y - cy);
                if (dist < radius * 0.6f) return false;     // center: let parent scroll
                int m = minuteAt(x, y);
                dragging = circDist(m, startMin) <= circDist(m, endMin) ? 0 : 1;
                getParent().requestDisallowInterceptTouchEvent(true);
                update(m);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) update(minuteAt(x, y));
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

    private void update(int m) {
        int old = dragging == 0 ? startMin : endMin;
        if (dragging == 0) startMin = m; else endMin = m;
        if (m != old) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
        if (listener != null) listener.onWindowChanged(startMin, endMin, false);
    }
}
