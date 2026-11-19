package com.simplealarm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Pill track with a knob; drag to the end to confirm. Prevents half-asleep taps. */
public class SlideToConfirm extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path chevron = new Path();
    private final String text;
    private final float d;
    private float progress;          // 0..1
    private float grabOffset;
    private boolean dragging, done;
    private Runnable onConfirm;

    public SlideToConfirm(Context c, String text, int accent) {
        super(c);
        this.text = text;
        d = getResources().getDisplayMetrics().density;
        track.setColor(0x1FFFFFFF);
        fill.setColor(Ui.withAlpha(accent, 0x55));
        knob.setColor(0xFFFFFFFF);
        arrow.setColor(Ui.BG);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeWidth(2.5f * d);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        label.setColor(Ui.TEXT);
        label.setTextSize(16 * d);
        label.setTypeface(Ui.MEDIUM);
        label.setTextAlign(Paint.Align.CENTER);
        label.setLetterSpacing(0.02f);
    }

    public void setOnConfirm(Runnable r) {
        onConfirm = r;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(68 * d));
    }

    private float pad() { return 6 * d; }
    private float knobR() { return getHeight() / 2f - pad(); }
    private float travel() { return getWidth() - 2 * (pad() + knobR()); }
    private float knobX() { return pad() + knobR() + travel() * progress; }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), r = h / 2f;
        rect.set(0, 0, getWidth(), h);
        c.drawRoundRect(rect, r, r, track);
        rect.set(0, 0, knobX() + knobR() + pad(), h);
        c.drawRoundRect(rect, r, r, fill);

        label.setAlpha((int) (255 * Math.max(0f, 1f - progress * 1.8f)));
        c.drawText(text, getWidth() / 2f + knobR() / 2f, h / 2f + label.getTextSize() / 3f, label);

        float kx = knobX(), ky = h / 2f, kr = knobR();
        c.drawCircle(kx, ky, kr, knob);
        float s = kr * 0.28f;
        chevron.reset();
        chevron.moveTo(kx - s * 0.6f, ky - s);
        chevron.lineTo(kx + s * 0.6f, ky);
        chevron.lineTo(kx - s * 0.6f, ky + s);
        c.drawPath(chevron, arrow);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (done) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.abs(e.getX() - knobX()) > knobR() * 1.6f) return false;
                dragging = true;
                grabOffset = e.getX() - knobX();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                float x = e.getX() - grabOffset - pad() - knobR();
                progress = Math.max(0f, Math.min(1f, x / travel()));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                // fall through
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (progress > 0.85f) {
                    done = true;
                    performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                            ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                    animateTo(1f, () -> { if (onConfirm != null) onConfirm.run(); });
                } else {
                    animateTo(0f, null);
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void animateTo(float target, Runnable end) {
        ValueAnimator a = ValueAnimator.ofFloat(progress, target);
        a.setDuration(220);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v -> {
            progress = (float) v.getAnimatedValue();
            invalidate();
        });
        if (end != null) {
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    end.run();
                }
            });
        }
        a.start();
    }
}
