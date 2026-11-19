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

        int index = getIntent().getIntExtra(AlarmScheduler.EXTRA_INDEX, 0);
        int count = getIntent().getIntExtra(AlarmScheduler.EXTRA_COUNT, 1);
        boolean last = index >= count - 1;
        WakeWindow w = WakeWindow.load(this);
        String label = (last ? "Last alarm" : "Alarm " + (index + 1) + " of " + count)
                + "\nwindow " + WakeWindow.hhmm(w.startMin) + " – " + WakeWindow.hhmm(w.endMin);

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

        if (!last) {
            Button stop = new Button(this);
            stop.setText("Stop (next in " + WakeWindow.STEP_MIN + " min)");
            stop.setTextSize(20);
            stop.setOnClickListener(v -> send(AlarmService.ACTION_STOP));
            root.addView(stop, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(72)));
        }

        Button awake = new Button(this);
        awake.setText("I'm up — cancel the rest");
        awake.setTextSize(20);
        awake.setOnClickListener(v -> send(AlarmService.ACTION_AWAKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        lp.topMargin = dp(16);
        root.addView(awake, lp);

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
