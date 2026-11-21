package app.pixelplanner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** Home-screen widget: today's tasks, the next time-table block, coins and streak. */
public class PlannerWidget extends AppWidgetProvider {
    private static final String PREFS = "pixel_widget";
    private static final String KEY = "data";

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
        refresh(c);
    }

    static void refresh(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(c, PlannerWidget.class));
            if (ids != null && ids.length > 0) render(c, mgr, ids);
        } catch (Exception ignored) {
        }
    }

    private static void render(Context c, AppWidgetManager mgr, int[] ids) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String title = "PIXEL PLANNER", stats = "Open the app once to fill this widget", next = "", tasks = "";
        try {
            JSONObject o = new JSONObject(sp.getString(KEY, "{}"));
            if (o.has("title")) title = o.optString("title", title);
            if (o.has("coins")) {
                stats = "R " + o.optInt("coins") + "   STREAK " + o.optInt("streak") + "   BEST " + o.optInt("best");
            }
            next = o.optString("next", "");
            JSONArray arr = o.optJSONArray("tasks");
            StringBuilder sb = new StringBuilder();
            if (arr != null) {
                for (int i = 0; i < arr.length() && i < 5; i++) {
                    if (i > 0) sb.append("\n");
                    sb.append("[ ] ").append(arr.optString(i));
                }
            }
            tasks = sb.length() == 0 && o.has("coins") ? "No open tasks today. Nice!" : sb.toString();
        } catch (Exception ignored) {
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 9100, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.w_title, title);
            rv.setTextViewText(R.id.w_stats, stats);
            rv.setTextViewText(R.id.w_next, next);
            rv.setTextViewText(R.id.w_tasks, tasks);
            rv.setOnClickPendingIntent(R.id.w_root, pi);
            mgr.updateAppWidget(id, rv);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        render(context, mgr, ids);
    }
}
