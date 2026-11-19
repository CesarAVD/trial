package com.simplealarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Books only the NEXT ring via AlarmManager.setAlarmClock() (exact, Doze-exempt,
 * shown on the lock screen). Each ring books the one after it in AlarmReceiver.
 */
public final class AlarmScheduler {
    static final String EXTRA_TIME = "time";
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_COUNT = "count";
    static final String EXTRA_WINDOW_END = "window_end";

    private AlarmScheduler() {}

    private static PendingIntent firePi(Context ctx, WakeWindow.Ring r) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        if (r != null) {
            i.putExtra(EXTRA_TIME, r.time)
                    .putExtra(EXTRA_INDEX, r.index)
                    .putExtra(EXTRA_COUNT, r.count)
                    .putExtra(EXTRA_WINDOW_END, r.windowEnd);
        }
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Schedule the first ring after fromMillis; cancels if none. Returns it or null. */
    public static WakeWindow.Ring scheduleNext(Context ctx, long fromMillis) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        WakeWindow.Ring r = WakeWindow.load(ctx).nextRing(fromMillis);
        if (r == null) {
            am.cancel(firePi(ctx, null));
            return null;
        }
        PendingIntent op = firePi(ctx, r);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.time, op);   // fallback only
        } else {
            PendingIntent show = PendingIntent.getActivity(ctx, 0,
                    new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(r.time, show), op);
        }
        return r;
    }

    public static WakeWindow.Ring scheduleNext(Context ctx) {
        return scheduleNext(ctx, System.currentTimeMillis());
    }
}
