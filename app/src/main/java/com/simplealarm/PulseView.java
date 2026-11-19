package com.simplealarm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Concentric rings expanding from the center; faster + brighter with intensity. */
public class PulseView extends View {
    private static final int RINGS = 3;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator anim;
    private final int color;
    private float phase;

    public PulseView(Context c, float intensity) {
        super(c);
        color = Ui.intensityColor(intensity);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
        ring.setColor(color);
        core.setColor(color);
        long period = (long) (3200 - 2200 * intensity);     // 3.2 s -> 1.0 s
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(period);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float max = Math.min(getWidth(), getHeight()) / 2f;
        float min = max * 0.42f;
        for (int i = 0; i < RINGS; i++) {
            float p = (phase + i / (float) RINGS) % 1f;
            float eased = 1f - (1f - p) * (1f - p);          // ease-out
            ring.setAlpha((int) (120 * (1f - p)));
            c.drawCircle(cx, cy, min + (max - min) * eased, ring);
        }
        core.setAlpha(22);
        c.drawCircle(cx, cy, min, core);
    }
}
