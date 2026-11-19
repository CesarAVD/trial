package com.simplealarm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextClock;
import android.widget.TextView;

/** Full-screen ringing UI over the lock screen. Look scales with intensity. */
public class RingActivity extends Activity {

    private final BroadcastReceiver stopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Manifest attrs cover API 27+; flags cover API 26.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        Ui.edgeToEdge(this);

        int index = getIntent().getIntExtra(AlarmScheduler.EXTRA_INDEX, 0);
        int count = getIntent().getIntExtra(AlarmScheduler.EXTRA_COUNT, 1);
        boolean last = index >= count - 1;
        float f = WakeWindow.intensity(index, count);
        int accent = Ui.intensityColor(f);
        WakeWindow w = WakeWindow.load(this);

        // background: deep night blue -> tinted by intensity
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.mix(0xFF0E1830, 0xFF2A0C14, f), Ui.BG,
                        Ui.mix(0xFF0B1F24, 0xFF3A0E16, f)});

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.applyInsets(root, dp(24));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // top: status chip + progress
        TextView chip = Ui.caps(this, last ? "Last alarm" : "Alarm " + (index + 1) + " of " + count, accent);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackground(Ui.rounded(Ui.withAlpha(accent, 0x22), dp(16), Ui.withAlpha(accent, 0x44), dp(1)));
        root.addView(chip, Ui.lp(-2, -2, 0, dp(12)));
        root.addView(new ProgressDots(this, index, count, accent), Ui.lp(-1, dp(10), 0, dp(16)));

        // center: pulse + clock
        FrameLayout center = new FrameLayout(this);
        center.addView(new PulseView(this, f), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);
        clockCol.setGravity(Gravity.CENTER);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(88);
        clock.setTypeface(Ui.THIN);
        clock.setTextColor(Ui.TEXT);
        clock.setLetterSpacing(-0.02f);
        clock.setIncludeFontPadding(false);
        clockCol.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(16);
        date.setTypeface(Ui.REGULAR);
        date.setTextColor(Ui.TEXT2);
        date.setGravity(Gravity.CENTER);
        clockCol.addView(date, Ui.lp(-2, -2, 0, dp(6)));
        center.addView(clockCol, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(center, Ui.lp(-1, 0, 1, dp(8)));

        TextView window = Ui.text(this, "Window " + WakeWindow.hhmm(w.startMin) + " – "
                + WakeWindow.hhmm(w.endMin), 14, Ui.TEXT2, Ui.REGULAR);
        root.addView(window, Ui.lp(-2, -2, 0, dp(8)));

        // actions
        if (!last) {
            TextView stop = Ui.pill(this, "Stop · next in " + WakeWindow.STEP_MIN + " min",
                    0x14FFFFFF, Ui.TEXT, 0x22FFFFFF);
            stop.setOnClickListener(v -> send(AlarmService.ACTION_STOP));
            root.addView(stop, Ui.lp(-1, -2, 0, dp(24)));
        }
        SlideToConfirm slide = new SlideToConfirm(this, "Slide — I'm up", accent);
        slide.setOnConfirm(() -> send(AlarmService.ACTION_AWAKE));
        root.addView(slide, Ui.lp(-1, -2, 0, dp(last ? 24 : 12)));
        root.addView(new Space(this), new LinearLayout.LayoutParams(1, dp(8)));

        setContentView(frame);

        IntentFilter filter = new IntentFilter(AlarmService.ACTION_STOPPED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopped, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopped, filter);
        }
    }

    private void send(String action) {
        startService(new Intent(this, AlarmService.class).setAction(action));
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(stopped);
        super.onDestroy();
    }

    /** Position in the window: dots (≤ 24 rings) or a thin bar. */
    static class ProgressDots extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int index, count, accent;

        ProgressDots(Context c, int index, int count, int accent) {
            super(c);
            this.index = index;
            this.count = count;
            this.accent = accent;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float h = getHeight(), cy = h / 2f, d = getResources().getDisplayMetrics().density;
            if (count <= 24) {
                float r = 3 * d, gap = 8 * d;
                float total = count * 2 * r + (count - 1) * gap;
                float x = (getWidth() - total) / 2f + r;
                for (int i = 0; i < count; i++) {
                    p.setColor(i < index ? Ui.withAlpha(accent, 0x99) : i == index ? accent : 0x33FFFFFF);
                    c.drawCircle(x, cy, i == index ? r * 1.4f : r, p);
                    x += 2 * r + gap;
                }
            } else {
                float w = getWidth() * 0.6f, x0 = (getWidth() - w) / 2f, th = 3 * d;
                p.setColor(0x33FFFFFF);
                c.drawRoundRect(x0, cy - th / 2, x0 + w, cy + th / 2, th, th, p);
                p.setColor(accent);
                float fx = x0 + w * (index + 1f) / count;
                c.drawRoundRect(x0, cy - th / 2, fx, cy + th / 2, th, th, p);
            }
        }
    }
}
