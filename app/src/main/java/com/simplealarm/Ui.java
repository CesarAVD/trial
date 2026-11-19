package com.simplealarm;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Design tokens + small view factories. Dark-only palette. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0B0D12;
    static final int BG_GLOW = 0xFF1A2444;
    static final int SURFACE = 0x12FFFFFF;
    static final int SURFACE2 = 0x1FFFFFFF;
    static final int STROKE = 0x17FFFFFF;
    static final int TEXT = 0xFFF2F4F8;
    static final int TEXT2 = 0xFF8A93A6;
    static final int ACCENT = 0xFF7C9CFF;
    static final int GENTLE = 0xFF5EE6B0;
    static final int MID = 0xFFFFC857;
    static final int HOT = 0xFFFF5A5F;

    static final Typeface THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** gentle (mint) -> mid (amber) -> hot (coral) as f goes 0 -> 1. */
    static int intensityColor(float f) {
        return f < 0.55f ? mix(GENTLE, MID, f / 0.55f) : mix(MID, HOT, (f - 0.55f) / 0.45f);
    }

    static GradientDrawable rounded(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    static GradientDrawable oval(int fill, int stroke, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null);
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small all-caps tracking label. */
    static TextView caps(Context c, String s, int color) {
        TextView t = text(c, s.toUpperCase(), 11.5f, color, MEDIUM);
        t.setLetterSpacing(0.14f);
        return t;
    }

    /** Pill-shaped tappable label. */
    static TextView pill(Context c, String s, int fill, int textColor, int stroke) {
        TextView t = text(c, s, 15, textColor, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 52));
        t.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        t.setBackground(ripple(rounded(fill, dp(c, 26), stroke, stroke != 0 ? dp(c, 1) : 0)));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Circular icon-ish button (text glyph). */
    static TextView roundButton(Context c, String glyph, int sizeDp) {
        TextView t = text(c, glyph, 22, TEXT, LIGHT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(oval(SURFACE2, 0, 0)));
        t.setClickable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    /** Glass card container. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        l.setBackground(rounded(SURFACE, dp(c, 26), STROKE, dp(c, 1)));
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight, int topMarginPx) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.topMargin = topMarginPx;
        return p;
    }

    /** Draw behind system bars on all versions (forced anyway on Android 15+). */
    @SuppressWarnings("deprecation")
    static void edgeToEdge(Activity a) {
        Window w = a.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    /** Pad a root view by system-bar + cutout insets plus basePad. */
    @SuppressWarnings("deprecation")
    static void applyInsets(View root, int basePad) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(basePad + l, basePad + t, basePad + r, basePad + b);
            return insets;
        });
    }
}
