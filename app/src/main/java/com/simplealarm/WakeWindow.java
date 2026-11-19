package com.simplealarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The wake-up window [start, end] in minutes since midnight. May cross midnight.
 * Rings at start, start+5, start+10 ... and always at end (forced, even if the
 * last gap is < 5 min).
 */
public class WakeWindow {
    public static final int STEP_MIN = 5;
    public static final int DAY_MIN = 24 * 60;

    public int startMin = 6 * 60 + 30;
    public int endMin = 7 * 60 + 15;
    /** bit 0 = Monday ... bit 6 = Sunday. */
    public int days = 0x7F;
    public boolean enabled = true;
    /** Rings at or before this epoch millis are skipped ("I'm up" pressed). */
    public long dismissedUntil = 0;

    /** One scheduled ring. index is 0-based; count = rings in its window. */
    public static class Ring {
        public long time;
        public int index;
        public int count;
        public long windowEnd;
    }

    /** Calendar.DAY_OF_WEEK (SUNDAY=1..SATURDAY=7) -> bit index (Mon=0..Sun=6). */
    static int dayBit(int calendarDow) {
        return (calendarDow + 5) % 7;
    }

    /** Window length in minutes, 0..1439. start == end means a single ring. */
    public int durationMin() {
        return ((endMin - startMin) % DAY_MIN + DAY_MIN) % DAY_MIN;
    }

    /** Ring offsets in minutes from start: 0, 5, 10, ..., < dur, then dur. */
    public static int[] offsets(int dur) {
        List<Integer> out = new ArrayList<>();
        for (int o = 0; o < dur; o += STEP_MIN) out.add(o);
        out.add(dur);
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    public int ringCount() {
        return offsets(durationMin()).length;
    }

    /** 0 = gentlest (first ring), 1 = most aggressive (last ring). */
    public static float intensity(int index, int count) {
        return count <= 1 ? 1f : (float) index / (count - 1);
    }

    /** First ring strictly after nowMillis (and after dismissedUntil), or null. */
    public Ring nextRing(long nowMillis) {
        if (!enabled || days == 0) return null;
        int dur = durationMin();
        int[] offs = offsets(dur);
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(nowMillis);
        // d = -1 catches a window that started yesterday and crosses midnight.
        for (int d = -1; d <= 8; d++) {
            Calendar c = (Calendar) base.clone();
            c.add(Calendar.DAY_OF_YEAR, d);
            c.set(Calendar.HOUR_OF_DAY, startMin / 60);
            c.set(Calendar.MINUTE, startMin % 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if ((days & (1 << dayBit(c.get(Calendar.DAY_OF_WEEK)))) == 0) continue;
            long start = c.getTimeInMillis();
            for (int i = 0; i < offs.length; i++) {
                long t = start + offs[i] * 60_000L;
                if (t > nowMillis && t > dismissedUntil) {
                    Ring r = new Ring();
                    r.time = t;
                    r.index = i;
                    r.count = offs.length;
                    r.windowEnd = start + dur * 60_000L;
                    return r;
                }
            }
        }
        return null;
    }

    public static String hhmm(int min) {
        min = ((min % DAY_MIN) + DAY_MIN) % DAY_MIN;
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    // ---------- persistence ----------

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("wake_window", Context.MODE_PRIVATE);
    }

    public static synchronized WakeWindow load(Context ctx) {
        SharedPreferences p = prefs(ctx);
        WakeWindow w = new WakeWindow();
        w.startMin = p.getInt("start", w.startMin);
        w.endMin = p.getInt("end", w.endMin);
        w.days = p.getInt("days", w.days);
        w.enabled = p.getBoolean("enabled", w.enabled);
        w.dismissedUntil = p.getLong("dismissed_until", 0);
        return w;
    }

    public synchronized void save(Context ctx) {
        prefs(ctx).edit()
                .putInt("start", startMin)
                .putInt("end", endMin)
                .putInt("days", days)
                .putBoolean("enabled", enabled)
                .putLong("dismissed_until", dismissedUntil)
                .commit();
    }
}
