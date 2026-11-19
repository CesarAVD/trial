package com.simplealarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fired by AlarmManager. Starts the ringing service and books the next occurrence. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0);
        boolean snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false);
        Alarm a = AlarmStore.get(ctx, id);
        if (a == null) return;                       // deleted meanwhile
        if (!snooze && !a.enabled) return;

        if (!snooze) {
            if (a.days == 0) {
                a.enabled = false;                   // one-shot: turn off
                AlarmStore.put(ctx, a);
            } else {
                AlarmScheduler.schedule(ctx, a);     // repeating: next day
            }
        }

        Intent svc = new Intent(ctx, AlarmService.class)
                .setAction(AlarmService.ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ID, id);
        // setAlarmClock broadcasts are exempt from background FGS-start restrictions.
        ctx.startForegroundService(svc);
    }
}
