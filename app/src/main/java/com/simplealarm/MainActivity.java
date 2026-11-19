package com.simplealarm;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String[] DAY_LETTERS = {"M", "T", "W", "T", "F", "S", "S"};

    private WakeWindow w;
    private DialView dial;
    private TextView startText, endText, nextText;
    private final TextView[] chips = new TextView[7];
    private LinearLayout warnings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        w = WakeWindow.load(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.BG_GLOW, Ui.BG, Ui.BG});
        scroll.setBackground(bg);
        Ui.applyInsets(scroll, dp(20));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        root.addView(header());

        nextText = Ui.text(this, "", 15, Ui.TEXT2, Ui.REGULAR);
        root.addView(nextText, Ui.lp(-1, -2, 0, dp(6)));

        dial = new DialView(this);
        dial.setWindow(w.startMin, w.endMin);
        dial.setListener((s, e, finished) -> {
            w.startMin = s;
            w.endMin = e;
            updateTimeTexts();
            if (finished) persist();
        });
        root.addView(dial, Ui.lp(-1, -2, 0, dp(12)));

        LinearLayout times = new LinearLayout(this);
        startText = Ui.text(this, "", 40, Ui.TEXT, Ui.THIN);
        endText = Ui.text(this, "", 40, Ui.TEXT, Ui.THIN);
        times.addView(timeCard("Earliest", startText, Ui.GENTLE, true), Ui.lp(0, -2, 1, 0));
        times.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        times.addView(timeCard("Latest", endText, Ui.HOT, false), Ui.lp(0, -2, 1, 0));
        root.addView(times, Ui.lp(-1, -2, 0, dp(16)));

        root.addView(daysCard(), Ui.lp(-1, -2, 0, dp(12)));
        root.addView(toggleCard(), Ui.lp(-1, -2, 0, dp(12)));

        warnings = new LinearLayout(this);
        warnings.setOrientation(LinearLayout.VERTICAL);
        root.addView(warnings);

        LinearLayout previews = new LinearLayout(this);
        previews.addView(previewPill("Preview gentle", 0), Ui.lp(0, -2, 1, 0));
        previews.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        previews.addView(previewPill("Preview intense", 1), Ui.lp(0, -2, 1, 0));
        root.addView(previews, Ui.lp(-1, -2, 0, dp(16)));

        setContentView(scroll);
        updateTimeTexts();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        w = WakeWindow.load(this);       // "I'm up" may have changed dismissedUntil
        refreshNext(AlarmScheduler.scheduleNext(this));
        refreshWarnings();
    }

    /** Save, reset any "I'm up" skip, and re-book the next ring. */
    private void persist() {
        w.dismissedUntil = 0;
        w.save(this);
        refreshNext(AlarmScheduler.scheduleNext(this));
    }

    // ---------- sections ----------

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "Wake", 30, Ui.TEXT, Ui.MEDIUM);
        title.setLetterSpacing(-0.01f);
        row.addView(title, Ui.lp(0, -2, 1, 0));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(15);
        clock.setTextColor(Ui.TEXT);
        clock.setTypeface(Ui.MEDIUM);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(14), dp(8), dp(14), dp(8));
        clock.setBackground(Ui.rounded(Ui.SURFACE2, dp(18), 0, 0));
        row.addView(clock);
        return row;
    }

    private View timeCard(String name, TextView value, int color, boolean isStart) {
        LinearLayout card = Ui.card(this);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(Ui.oval(color, 0, 0));
        head.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        head.addView(new Space(this), new LinearLayout.LayoutParams(dp(8), 1));
        head.addView(Ui.caps(this, name, Ui.TEXT2));
        card.addView(head);

        card.addView(value, Ui.lp(-2, -2, 0, dp(10)));

        LinearLayout steps = new LinearLayout(this);
        steps.addView(stepButton("−", isStart, -WakeWindow.STEP_MIN));
        steps.addView(new Space(this), new LinearLayout.LayoutParams(dp(8), 1));
        steps.addView(stepButton("+", isStart, WakeWindow.STEP_MIN));
        card.addView(steps, Ui.lp(-2, -2, 0, dp(14)));
        return card;
    }

    private View stepButton(String glyph, boolean isStart, int delta) {
        TextView b = Ui.roundButton(this, glyph, 40);
        b.setOnClickListener(v -> {
            if (isStart) w.startMin = Math.floorMod(w.startMin + delta, WakeWindow.DAY_MIN);
            else w.endMin = Math.floorMod(w.endMin + delta, WakeWindow.DAY_MIN);
            dial.setWindow(w.startMin, w.endMin);
            updateTimeTexts();
            persist();
        });
        return b;
    }

    private View daysCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.caps(this, "Repeat", Ui.TEXT2));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 7; i++) {
            if (i > 0) row.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
            final int bit = i;
            TextView c = Ui.text(this, DAY_LETTERS[i], 14, Ui.TEXT, Ui.MEDIUM);
            c.setGravity(Gravity.CENTER);
            c.setOnClickListener(v -> {
                w.days ^= 1 << bit;
                styleChips();
                persist();
            });
            row.addView(c, new LinearLayout.LayoutParams(dp(36), dp(36)));
            chips[i] = c;
        }
        card.addView(row, Ui.lp(-1, -2, 0, dp(12)));
        styleChips();
        return card;
    }

    private void styleChips() {
        for (int i = 0; i < 7; i++) {
            boolean on = (w.days & (1 << i)) != 0;
            chips[i].setBackground(Ui.ripple(on ? Ui.oval(Ui.ACCENT, 0, 0)
                    : Ui.oval(0, Ui.STROKE, dp(1))));
            chips[i].setTextColor(on ? Ui.BG : Ui.TEXT2);
        }
    }

    private View toggleCard() {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, "Alarms", 17, Ui.TEXT, Ui.MEDIUM));
        texts.addView(Ui.text(this, "Every " + WakeWindow.STEP_MIN + " min · gentle → intense",
                13, Ui.TEXT2, Ui.REGULAR), Ui.lp(-2, -2, 0, dp(4)));
        card.addView(texts, Ui.lp(0, -2, 1, 0));

        Switch sw = new Switch(this);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, 0xFF9AA1B2}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{Ui.ACCENT, 0x40FFFFFF}));
        sw.setChecked(w.enabled);
        sw.setOnCheckedChangeListener((b, on) -> {
            w.enabled = on;
            persist();
        });
        card.addView(sw);
        return card;
    }

    private View previewPill(String text, int index) {
        TextView p = Ui.pill(this, text, Ui.SURFACE, Ui.TEXT, Ui.STROKE);
        p.setOnClickListener(v -> startForegroundService(new Intent(this, AlarmService.class)
                .setAction(AlarmService.ACTION_RING)
                .putExtra(AlarmScheduler.EXTRA_INDEX, index)
                .putExtra(AlarmScheduler.EXTRA_COUNT, 2)
                .putExtra(AlarmScheduler.EXTRA_WINDOW_END, 0L)));
        return p;
    }

    private void updateTimeTexts() {
        startText.setText(WakeWindow.hhmm(w.startMin));
        endText.setText(WakeWindow.hhmm(w.endMin));
    }

    private void refreshNext(WakeWindow.Ring r) {
        if (!w.enabled) {
            nextText.setText("Alarms are off");
        } else if (w.days == 0) {
            nextText.setText("No days selected");
        } else if (r == null) {
            nextText.setText("Nothing scheduled");
        } else {
            nextText.setText("Next alarm in " + untilText(r.time - System.currentTimeMillis()));
        }
    }

    static String untilText(long ms) {
        long totalMin = (ms + 59_999) / 60_000;   // round up to whole minutes
        long d = totalMin / (24 * 60), h = (totalMin / 60) % 24, m = totalMin % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" d ");
        if (d > 0 || h > 0) sb.append(h).append(" h ");
        sb.append(m).append(" min");
        return sb.toString();
    }

    // ---------- warnings: settings that silently break alarms ----------

    private void refreshWarnings() {
        warnings.removeAllViews();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) {
            addWarning("Notifications are off", "Alarms can't show.",
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            addWarning("Full-screen alarms are off", "The ring screen won't appear on the lock screen.",
                    new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName())));
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            addWarning("Battery saving may delay alarms", "Tap → Battery → Unrestricted.",
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
        }
    }

    private void addWarning(String title, String body, Intent fix) {
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.ripple(Ui.rounded(0x1AFFC857, dp(22), 0x33FFC857, dp(1))));
        card.setClickable(true);
        card.addView(Ui.text(this, title, 15, Ui.MID, Ui.MEDIUM));
        card.addView(Ui.text(this, body, 13, Ui.TEXT2, Ui.REGULAR), Ui.lp(-2, -2, 0, dp(4)));
        card.setOnClickListener(v -> {
            try {
                startActivity(fix);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        warnings.addView(card, Ui.lp(-1, -2, 0, dp(12)));
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
