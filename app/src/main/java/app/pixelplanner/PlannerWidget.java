package app.pixelplanner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** "Planner" (today) widget: coins, streak, next time-table block and open tasks. Data is shared with BuyWidget. */
public class PlannerWidget extends AppWidgetProvider {
    static void refresh(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(c, PlannerWidget.class));
            if (ids != null && ids.length > 0) render(c, mgr, ids);
        } catch (Throwable ignored) { }
    }

    private static void render(Context c, AppWidgetManager mgr, int[] ids) {
        JSONObject o = BuyWidget.data(c);
        boolean has = o.has("coins");
        String next = o.optString("next", has ? "" : "Open the app once to fill this widget");
        JSONArray arr = o.optJSONArray("tasks");
        Bitmap av = BuyWidget.avatar(c);
        PendingIntent pi = BuyWidget.openIntent(c, "planner");
        int[] rows = {R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4};
        int[] txt = {R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4};
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.w_title, o.optString("title", "PIXEL PLANNER"));
            rv.setTextViewText(R.id.w_coins, String.valueOf(o.optInt("coins")));
            rv.setTextViewText(R.id.w_streak, String.valueOf(o.optInt("streak")));
            rv.setTextViewText(R.id.w_best, String.valueOf(o.optInt("best")));
            rv.setTextViewText(R.id.w_next, next);
            rv.setViewVisibility(R.id.w_next, next.length() > 0 ? View.VISIBLE : View.GONE);
            if (av != null) rv.setImageViewBitmap(R.id.w_icon, av);
            else rv.setImageViewResource(R.id.w_icon, R.drawable.w_coin);
            int n = arr == null ? 0 : Math.min(4, arr.length());
            for (int i = 0; i < 4; i++) {
                if (i < n) {
                    rv.setViewVisibility(rows[i], View.VISIBLE);
                    rv.setTextViewText(txt[i], arr.optString(i));
                } else rv.setViewVisibility(rows[i], View.GONE);
            }
            rv.setViewVisibility(R.id.w_empty, n == 0 ? View.VISIBLE : View.GONE);
            rv.setTextViewText(R.id.w_empty, has ? "All clear today. Nice work!" : "");
            rv.setOnClickPendingIntent(R.id.w_root, pi);
            mgr.updateAppWidget(id, rv);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        render(context, mgr, ids);
    }
}
