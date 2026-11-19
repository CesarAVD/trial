package com.simplealarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persists alarms as a JSON array in SharedPreferences. */
public final class AlarmStore {
    private static final String PREFS = "alarms";
    private static final String KEY = "list";
    private static final String KEY_NEXT_ID = "next_id";

    private AlarmStore() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Alarm> load(Context ctx) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(Alarm.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        Collections.sort(out, (a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
        return out;
    }

    public static synchronized void saveAll(Context ctx, List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : alarms) arr.put(a.toJson());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).commit();
    }

    public static synchronized Alarm get(Context ctx, int id) {
        for (Alarm a : load(ctx)) if (a.id == id) return a;
        return null;
    }

    /** Insert or replace by id. Assigns an id if a.id == 0. */
    public static synchronized void put(Context ctx, Alarm a) {
        if (a.id == 0) {
            int next = prefs(ctx).getInt(KEY_NEXT_ID, 1);
            a.id = next;
            prefs(ctx).edit().putInt(KEY_NEXT_ID, next + 1).commit();
        }
        List<Alarm> all = load(ctx);
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == a.id) {
                all.set(i, a);
                replaced = true;
            }
        }
        if (!replaced) all.add(a);
        saveAll(ctx, all);
    }

    public static synchronized void delete(Context ctx, int id) {
        List<Alarm> all = load(ctx);
        all.removeIf(a -> a.id == id);
        saveAll(ctx, all);
    }
}
