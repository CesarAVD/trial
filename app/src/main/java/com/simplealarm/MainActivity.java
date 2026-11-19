package com.simplealarm;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {

    private final List<Alarm> alarms = new ArrayList<>();
    private AlarmAdapter adapter;
    private TextView nextText;
    private LinearLayout warnings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        applyInsets(root, pad);

        TextClock clock = new TextClock(this);
        clock.setTextSize(64);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setGravity(Gravity.CENTER);
        date.setTextSize(16);
        root.addView(date);

        nextText = new TextView(this);
        nextText.setGravity(Gravity.CENTER);
        nextText.setPadding(0, dp(8), 0, dp(8));
        root.addView(nextText);

        warnings = new LinearLayout(this);
        warnings.setOrientation(LinearLayout.VERTICAL);
        root.addView(warnings);

        ListView list = new ListView(this);
        adapter = new AlarmAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> editAlarm(alarms.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            confirmDelete(alarms.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = new Button(this);
        add.setText("+ Add alarm");
        add.setTextSize(18);
        add.setOnClickListener(v -> editAlarm(null));
        root.addView(add, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        setContentView(root);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        refreshWarnings();
    }

    private void refresh() {
        alarms.clear();
        alarms.addAll(AlarmStore.load(this));
        adapter.notifyDataSetChanged();

        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (Alarm a : alarms) if (a.enabled) next = Math.min(next, a.nextTrigger(now));
        if (next == Long.MAX_VALUE) {
            nextText.setText(alarms.isEmpty() ? "No alarms. Tap + to add one." : "All alarms off");
        } else {
            nextText.setText("Next alarm in " + untilText(next - now));
        }
    }

    // ---------- Warnings: settings that silently break alarms ----------

    private void refreshWarnings() {
        warnings.removeAllViews();
        NotificationManager nm = getSystemService(NotificationManager.class);

        if (!nm.areNotificationsEnabled()) {
            addWarning("Notifications are off — alarms can't show.", openAppNotificationSettings());
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

    private Intent openAppNotificationSettings() {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
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

    // ---------- Add / edit / delete ----------

    private void editAlarm(Alarm existing) {
        final Alarm a = existing != null ? existing : new Alarm();
        if (existing == null) {
            Calendar c = Calendar.getInstance();
            a.hour = c.get(Calendar.HOUR_OF_DAY);
            a.minute = c.get(Calendar.MINUTE);
        }
        new TimePickerDialog(this, (tp, h, m) -> {
            a.hour = h;
            a.minute = m;
            pickDays(a);
        }, a.hour, a.minute, DateFormat.is24HourFormat(this)).show();
    }

    private void pickDays(Alarm a) {
        boolean[] checked = new boolean[7];
        for (int i = 0; i < 7; i++) checked[i] = (a.days & (1 << i)) != 0;
        String[] names = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        new AlertDialog.Builder(this)
                .setTitle("Repeat (none = once)")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Next", (d, w) -> {
                    int days = 0;
                    for (int i = 0; i < 7; i++) if (checked[i]) days |= 1 << i;
                    a.days = days;
                    pickLabel(a);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickLabel(Alarm a) {
        EditText input = new EditText(this);
        input.setHint("Label (optional)");
        input.setText(a.label);
        new AlertDialog.Builder(this)
                .setTitle("Label")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    a.label = input.getText().toString().trim();
                    a.enabled = true;
                    save(a);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void save(Alarm a) {
        AlarmStore.put(this, a);
        long when = AlarmScheduler.schedule(this, a);
        if (when > 0) {
            Toast.makeText(this, "Alarm set for " + untilText(when - System.currentTimeMillis())
                    + " from now", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void confirmDelete(Alarm a) {
        new AlertDialog.Builder(this)
                .setTitle("Delete alarm " + formatTime(a) + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    AlarmScheduler.cancelAll(this, a.id);
                    AlarmStore.delete(this, a.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Helpers ----------

    private String formatTime(Alarm a) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        return DateFormat.getTimeFormat(this).format(c.getTime());
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class AlarmAdapter extends BaseAdapter {
        @Override public int getCount() { return alarms.size(); }
        @Override public Object getItem(int pos) { return alarms.get(pos); }
        @Override public long getItemId(int pos) { return alarms.get(pos).id; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            Alarm a = alarms.get(pos);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(12), dp(8), dp(12));

            LinearLayout texts = new LinearLayout(MainActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView time = new TextView(MainActivity.this);
            time.setText(formatTime(a));
            time.setTextSize(32);
            time.setAlpha(a.enabled ? 1f : 0.4f);
            TextView sub = new TextView(MainActivity.this);
            sub.setText(a.label.isEmpty() ? a.daysText() : a.daysText() + " · " + a.label);
            texts.addView(time);
            texts.addView(sub);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Switch sw = new Switch(MainActivity.this);
            sw.setChecked(a.enabled);
            sw.setFocusable(false);   // keep row clickable inside ListView
            sw.setOnCheckedChangeListener((b, on) -> {
                a.enabled = on;
                if (!on) AlarmScheduler.cancelAll(MainActivity.this, a.id);
                save(a);
            });
            row.addView(sw);
            return row;
        }
    }
}
