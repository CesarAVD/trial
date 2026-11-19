package com.simplealarm;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/** One alarm. days: bit 0 = Monday ... bit 6 = Sunday; 0 = ring once. */
public class Alarm {
    public int id;
    public int hour;
    public int minute;
    public int days;
    public boolean enabled = true;
    public String label = "";

    static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    /** Calendar.DAY_OF_WEEK (SUNDAY=1..SATURDAY=7) -> bit index (Mon=0..Sun=6). */
    static int dayBit(int calendarDow) {
        return (calendarDow + 5) % 7;
    }

    /** Next trigger time in epoch millis strictly after nowMillis. */
    public long nextTrigger(long nowMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMillis);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            boolean dayOk = days == 0 || (days & (1 << dayBit(c.get(Calendar.DAY_OF_WEEK)))) != 0;
            if (dayOk && c.getTimeInMillis() > nowMillis) return c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 1);
            // re-apply time in case of DST shifts
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
        }
        throw new IllegalStateException("no trigger found");
    }

    public String timeText() {
        return String.format("%02d:%02d", hour, minute);
    }

    public String daysText() {
        if (days == 0) return "Once";
        if (days == 0x7F) return "Every day";
        if (days == 0x1F) return "Weekdays";
        if (days == 0x60) return "Weekends";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(DAY_NAMES[i]);
            }
        }
        return sb.toString();
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("hour", hour);
        o.put("minute", minute);
        o.put("days", days);
        o.put("enabled", enabled);
        o.put("label", label);
        return o;
    }

    static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optInt("id");
        a.hour = o.optInt("hour");
        a.minute = o.optInt("minute");
        a.days = o.optInt("days");
        a.enabled = o.optBoolean("enabled", true);
        a.label = o.optString("label", "");
        return a;
    }
}
