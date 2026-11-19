package com.simplealarm;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String[] DAY_LETTERS = {"M", "T", "W", "T", "F", "S", "S"};

    private WakeWindow w;
    private DialView dial;
    private TextView startText, endText, nextText;
    private Switch enabledSwitch;
    private final TextView[] chips = new TextView[7];
    private LinearLayout warnings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        w = WakeWindow.load(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        int pad = dp(16);
        applyInsets(scroll, pad);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(28);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);

        dial = new DialView(this);
        dial.setWindow(w.startMin, w.endMin);
        dial.setListener((s, e, finished) -> {
            w.startMin = s;
            w.endMin = e;
            updateTimeTexts();
            if (finished) persist();
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(8);
        root.addView(dial, dlp);

        startText = new TextView(this);
        endText = new TextView(this);
        root.addView(timeRow("Earliest", startText, DialView.GENTLE, true));
        root.addView(timeRow("Latest", endText, DialView.HOT, false));

        root.addView(dayChips());

        LinearLayout swRow = new LinearLayout(this);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(12), 0, 0);
        TextView swLabel = new TextView(this);
        swLabel.setText("Alarms on");
        swLabel.setTextSize(18);
        swRow.addView(swLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(w.enabled);
        enabledSwitch.setOnCheckedChangeListener((b, on) -> {
            w.enabled = on;
            persist();
        });
        swRow.addView(enabledSwitch);
        root.addView(swRow);

        nextText = new TextView(this);
        nextText.setGravity(Gravity.CENTER);
        nextText.setTextSize(16);
        nextText.setPadding(0, dp(12), 0, dp(12));
        root.addView(nextText);

        warnings = new LinearLayout(this);
        warnings.setOrientation(LinearLayout.VERTICAL);
        root.addView(warnings);

        LinearLayout previews = new LinearLayout(this);
        previews.addView(previewButton("Preview first", 0, 2), new LinearLayout.LayoutParams(0, -2, 1f));
        previews.addView(previewButton("Preview last", 1, 2), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(previews);

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

    // ---------- UI pieces ----------

    private View timeRow(String name, TextView value, int color, boolean isStart) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        View dot = new View(this);
        GradientDrawable dd = new GradientDrawable();
        dd.setShape(GradientDrawable.OVAL);
        dd.setColor(color);
        dot.setBackground(dd);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotLp.rightMargin = dp(10);
        row.addView(dot, dotLp);

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(18);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        row.addView(stepButton("−", isStart, -WakeWindow.STEP_MIN));
        value.setTextSize(24);
        value.setGravity(Gravity.CENTER);
        row.addView(value, new LinearLayout.LayoutParams(dp(96), -2));
        row.addView(stepButton("+", isStart, WakeWindow.STEP_MIN));
        return row;
    }

    private Button stepButton(String text, boolean isStart, int delta) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setOnClickListener(v -> {
            if (isStart) w.startMin = Math.floorMod(w.startMin + delta, WakeWindow.DAY_MIN);
            else w.endMin = Math.floorMod(w.endMin + delta, WakeWindow.DAY_MIN);
            dial.setWindow(w.startMin, w.endMin);
            updateTimeTexts();
            persist();
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(48)));
        return b;
    }

    private View dayChips() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(12), 0, 0);
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView c = new TextView(this);
            c.setText(DAY_LETTERS[i]);
            c.setGravity(Gravity.CENTER);
            c.setTextSize(16);
            c.setOnClickListener(v -> {
                w.days ^= 1 << bit;
                styleChips();
                persist();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.leftMargin = lp.rightMargin = dp(3);
            row.addView(c, lp);
            chips[i] = c;
        }
        styleChips();
        return row;
    }

    private void styleChips() {
        for (int i = 0; i < 7; i++) {
            boolean on = (w.days & (1 << i)) != 0;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if (on) {
                bg.setColor(Color.rgb(61, 123, 247));
                chips[i].setTextColor(Color.WHITE);
            } else {
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke(dp(1), Color.GRAY);
                chips[i].setTextColor(Color.GRAY);
            }
            chips[i].setBackground(bg);
        }
    }

    private Button previewButton(String text, int index, int count) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(v -> startForegroundService(new Intent(this, AlarmService.class)
                .setAction(AlarmService.ACTION_RING)
                .putExtra(AlarmScheduler.EXTRA_INDEX, index)
                .putExtra(AlarmScheduler.EXTRA_COUNT, count)
                .putExtra(AlarmScheduler.EXTRA_WINDOW_END, 0L)));
        return b;
    }

    private void updateTimeTexts() {
        startText.setText(WakeWindow.hhmm(w.startMin));
        endText.setText(WakeWindow.hhmm(w.endMin));
    }

    private void refreshNext(WakeWindow.Ring r) {
        if (!w.enabled) {
            nextText.setText("Alarms off");
        } else if (w.days == 0) {
            nextText.setText("No days selected");
        } else if (r == null) {
            nextText.setText("Nothing scheduled");
        } else {
            nextText.setText("Next: alarm " + (r.index + 1) + " of " + r.count + " in "
                    + untilText(r.time - System.currentTimeMillis()));
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

    // ---------- Warnings: settings that silently break alarms ----------

    private void refreshWarnings() {
        warnings.removeAllViews();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) {
            addWarning("Notifications are off — alarms can't show.",
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            addWarning("Full-screen alarm permission is off — the ring screen won't appear on the lock screen.",
                    new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName())));
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            addWarning("Samsung battery saving may delay alarms. Tap → Battery → Unrestricted.",
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
        }
    }

    private void addWarning(String text, Intent fix) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText("⚠ " + text);
        b.setOnClickListener(v -> {
            try {
                startActivity(fix);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        warnings.addView(b);
    }

    // ---------- Helpers ----------

    /** targetSdk 35 forces edge-to-edge on Android 15+; pad content away from system bars. */
    private void applyInsets(View root, int basePad) {
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
