package com.simplealarm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Full-screen ringing UI shown over the lock screen. */
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

        int id = getIntent().getIntExtra(AlarmScheduler.EXTRA_ID, 0);
        Alarm a = AlarmStore.get(this, id);
        String label = a != null && !a.label.isEmpty() ? a.label : "Alarm";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(16, 24, 48));
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextClock clock = new TextClock(this);
        clock.setTextSize(72);
        clock.setTextColor(Color.WHITE);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextSize(24);
        lbl.setTextColor(Color.WHITE);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPadding(0, dp(8), 0, dp(48));
        root.addView(lbl);

        Button snooze = new Button(this);
        snooze.setText("Snooze " + AlarmService.SNOOZE_MINUTES + " min");
        snooze.setTextSize(20);
        snooze.setOnClickListener(v -> send(AlarmService.ACTION_SNOOZE));
        root.addView(snooze, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)));

        Button dismiss = new Button(this);
        dismiss.setText("Dismiss");
        dismiss.setTextSize(20);
        dismiss.setOnClickListener(v -> send(AlarmService.ACTION_DISMISS));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        lp.topMargin = dp(16);
        root.addView(dismiss, lp);

        setContentView(root);

        IntentFilter f = new IntentFilter(AlarmService.ACTION_STOPPED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopped, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopped, f);
        }
    }

    private void send(String action) {
        startService(new Intent(this, AlarmService.class).setAction(action));
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(stopped);
        super.onDestroy();
    }
}
