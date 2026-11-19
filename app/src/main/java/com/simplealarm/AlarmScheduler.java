package com.simplealarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Uses AlarmManager.setAlarmClock(): exact, fires in Doze, and shows the
 * alarm icon in the status bar / next-alarm on the Samsung lock screen.
 */
public final class AlarmScheduler {
    static final String EXTRA_ID = "alarm_id";
    static final String EXTRA_SNOOZE = "snooze";
    private static final int SNOOZE_OFFSET = 1_000_000;

    private AlarmScheduler() {}

    private static PendingIntent firePi(Context ctx, int id, boolean snooze) {
        Intent i = new Intent(ctx, AlarmReceiver.class)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_SNOOZE, snooze);
        int req = snooze ? SNOOZE_OFFSET + id : id;
        return PendingIntent.getBroadcast(ctx, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAt(Context ctx, long when, PendingIntent op) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Should not happen with USE_EXACT_ALARM; inexact fallback rather than crash.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, op);
            return;
        }
        PendingIntent show = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), op);
    }

    /** Schedule (or cancel, if disabled) the next occurrence. Returns trigger millis or -1. */
    public static long schedule(Context ctx, Alarm a) {
        PendingIntent op = firePi(ctx, a.id, false);
        if (!a.enabled) {
            ctx.getSystemService(AlarmManager.class).cancel(op);
            return -1;
        }
        long when = a.nextTrigger(System.currentTimeMillis());
        setAt(ctx, when, op);
        return when;
    }

    public static void snooze(Context ctx, int id, int minutes) {
        long when = System.currentTimeMillis() + minutes * 60_000L;
        setAt(ctx, when, firePi(ctx, id, true));
    }

    public static void cancelAll(Context ctx, int id) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(firePi(ctx, id, false));
        am.cancel(firePi(ctx, id, true));
    }

    public static void rescheduleAll(Context ctx) {
        for (Alarm a : AlarmStore.load(ctx)) schedule(ctx, a);
    }
}
