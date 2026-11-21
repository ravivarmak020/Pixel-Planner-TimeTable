package app.pixelplanner;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** "Upcoming" widget: tasks and time-table blocks for the next days, with a coloured day chip. */
public class UpcomingWidget extends AppWidgetProvider {
    static void refresh(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(c, UpcomingWidget.class));
            if (ids != null && ids.length > 0) render(c, mgr, ids);
        } catch (Throwable ignored) { }
    }

    private static int chipColor(String label, boolean late) {
        if (late) return Color.parseColor("#FFFF6B6B");
        if ("TODAY".equals(label)) return Color.parseColor("#FF39D353");
        if ("TMRW".equals(label)) return Color.parseColor("#FFFFD23F");
        return Color.parseColor("#FF8FE0FF");
    }

    private static void render(Context c, AppWidgetManager mgr, int[] ids) {
        JSONObject o = PlannerWidget.data(c);
        JSONArray up = o.optJSONArray("upcoming");
        int total = up == null ? 0 : up.length();
        int[] rows = {R.id.u_row1, R.id.u_row2, R.id.u_row3, R.id.u_row4, R.id.u_row5, R.id.u_row6, R.id.u_row7};
        int[] ds = {R.id.u_d1, R.id.u_d2, R.id.u_d3, R.id.u_d4, R.id.u_d5, R.id.u_d6, R.id.u_d7};
        int[] ts = {R.id.u_t1, R.id.u_t2, R.id.u_t3, R.id.u_t4, R.id.u_t5, R.id.u_t6, R.id.u_t7};
        int[] hs = {R.id.u_h1, R.id.u_h2, R.id.u_h3, R.id.u_h4, R.id.u_h5, R.id.u_h6, R.id.u_h7};
        Bitmap av = PlannerWidget.avatar(c);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_upcoming);
            if (av != null) rv.setImageViewBitmap(R.id.u_icon, av);
            else rv.setImageViewResource(R.id.u_icon, R.drawable.w_star);
            rv.setTextViewText(R.id.u_count, String.valueOf(total));
            int n = Math.min(7, total);
            for (int i = 0; i < 7; i++) {
                if (i < n) {
                    JSONObject it = up.optJSONObject(i);
                    boolean late = it != null && it.optInt("od") == 1;
                    String d = it == null ? "" : it.optString("d");
                    rv.setViewVisibility(rows[i], View.VISIBLE);
                    rv.setTextViewText(ds[i], d);
                    rv.setInt(ds[i], "setBackgroundColor", chipColor(d, late));
                    rv.setTextViewText(ts[i], it == null ? "" : it.optString("t"));
                    rv.setTextViewText(hs[i], it == null ? "" : it.optString("h"));
                } else rv.setViewVisibility(rows[i], View.GONE);
            }
            rv.setViewVisibility(R.id.u_empty, n == 0 ? View.VISIBLE : View.GONE);
            rv.setTextViewText(R.id.u_empty, o.has("coins") ? "Nothing coming up. Enjoy the free time!" : "Open the app once to fill this widget");
            rv.setOnClickPendingIntent(R.id.u_root, PlannerWidget.openIntent(c));
            mgr.updateAppWidget(id, rv);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        render(context, mgr, ids);
    }
}
