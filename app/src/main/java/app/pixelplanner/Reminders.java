package app.pixelplanner;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps the reminder list on the phone and (re)schedules the alarms for it. */
final class Reminders {
    private static final String PREFS = "pixel_reminders";
    private static final String KEY = "list";

    static String read(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
    }

    static void write(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
    }

    private static PendingIntent pending(Context c, int id, String title, String body) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("app.pixelplanner.REMIND." + id);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canExact(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    /** Replace everything scheduled before with the given list: [{id, at, title, body}]. */
    static void replaceAll(Context c, String json) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray old = new JSONArray(read(c));
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                am.cancel(pending(c, o.optInt("id"), o.optString("title"), o.optString("body")));
            }
        } catch (Exception ignored) { }
        long now = System.currentTimeMillis();
        JSONArray keep = new JSONArray();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length() && i < 100; i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.optLong("at");
                if (at <= now + 1000) continue;
                int id = o.optInt("id");
                String title = o.optString("title"), body = o.optString("body");
                PendingIntent pi = pending(c, id, title, body);
                try {
                    if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } catch (SecurityException e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
                keep.put(o);
            }
        } catch (Exception ignored) { }
        write(c, keep.toString());
    }

    /** After a reboot: put the saved alarms back. */
    static void restore(Context c) {
        replaceAll(c, read(c));
    }
}
