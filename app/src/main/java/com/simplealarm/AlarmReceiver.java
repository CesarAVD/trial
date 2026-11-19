package com.simplealarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fired by AlarmManager: book the following ring, then start ringing this one. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long now = System.currentTimeMillis();
        long t = intent.getLongExtra(AlarmScheduler.EXTRA_TIME, now);
        // +1 s guards against the alarm firing a few ms early and re-booking itself.
        AlarmScheduler.scheduleNext(ctx, Math.max(now, t) + 1000);

        WakeWindow w = WakeWindow.load(ctx);
        if (!w.enabled || t <= w.dismissedUntil) return;   // stale booking

        Intent svc = new Intent(ctx, AlarmService.class)
                .setAction(AlarmService.ACTION_RING)
                .putExtra(AlarmScheduler.EXTRA_INDEX, intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0))
                .putExtra(AlarmScheduler.EXTRA_COUNT, intent.getIntExtra(AlarmScheduler.EXTRA_COUNT, 1))
                .putExtra(AlarmScheduler.EXTRA_WINDOW_END,
                        intent.getLongExtra(AlarmScheduler.EXTRA_WINDOW_END, now));
        // setAlarmClock broadcasts are exempt from background FGS-start restrictions.
        ctx.startForegroundService(svc);
    }
}
