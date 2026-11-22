package app.pixelplanner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** Home-screen widget: "To Buy" list. */
public class BuyWidget extends AppWidgetProvider {
    static final String PREFS = "pixel_widget";
    private static final String KEY = "data";

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply();
        refresh(c);
    }

    static JSONObject data(Context c) {
        try {
            return new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static Bitmap avatar(Context c) {
        try {
            File f = new File(c.getFilesDir(), "pixel-icon.png");
            if (f.exists()) return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable ignored) { }
        return null;
    }

    static PendingIntent openIntent(Context c) { return openIntent(c, "buy"); }

    static PendingIntent openIntent(Context c, String view) {
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("act_k", "view");
        open.putExtra("act_b", view);
        return PendingIntent.getActivity(c, "buy".equals(view) ? 9100 : 9101, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static String textOf(JSONArray a, int i) {
        Object o = a == null ? null : a.opt(i);
        if (o instanceof JSONObject) return ((JSONObject) o).optString("t");
        return o == null ? "" : String.valueOf(o);
    }

    static String idOf(JSONArray a, int i) {
        Object o = a == null ? null : a.opt(i);
        if (o instanceof JSONObject) return ((JSONObject) o).optString("i", textOf(a, i));
        return textOf(a, i);
    }

    /** Drop a ticked item from the saved widget data so it disappears at once (the app syncs later). */
    static void removeItem(Context c, String kind, String id) {
        try {
            JSONObject o = data(c);
            String key = "buy".equals(kind) ? "buy" : "tasks";
            JSONArray a = o.optJSONArray(key), out = new JSONArray();
            if (a != null) for (int i = 0; i < a.length(); i++) if (!idOf(a, i).equals(id)) out.put(a.get(i));
            o.put(key, out);
            if ("buy".equals(kind)) o.put("n", Math.max(0, o.optInt("n") - 1));
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply();
        } catch (Exception ignored) { }
    }

    static void refresh(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(c, BuyWidget.class));
            if (ids != null && ids.length > 0) render(c, mgr, ids);
        } catch (Throwable ignored) { }
        PlannerWidget.refresh(c);
    }

    private static void render(Context c, AppWidgetManager mgr, int[] ids) {
        JSONObject o = data(c);
        boolean has = o.has("ok");
        JSONArray arr = o.optJSONArray("buy");
        int total = o.optInt("n");
        Bitmap av = avatar(c);
        int[] rows = {R.id.b_row1, R.id.b_row2, R.id.b_row3, R.id.b_row4, R.id.b_row5, R.id.b_row6, R.id.b_row7};
        int[] txt = {R.id.b_t1, R.id.b_t2, R.id.b_t3, R.id.b_t4, R.id.b_t5, R.id.b_t6, R.id.b_t7};
        int[] circ = {R.id.b_c1, R.id.b_c2, R.id.b_c3, R.id.b_c4, R.id.b_c5, R.id.b_c6, R.id.b_c7};
        for (int id : ids) {
            Bundle opt = mgr.getAppWidgetOptions(id);
            int minH = opt == null ? 0 : opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            // card padding 32 + header 38 + gap 8 = 78dp, each row 36dp, the "+N more" line 26dp
            int byH = minH <= 0 ? 4 : Math.max(3, Math.min(7, (minH - 80) / 36));
            int n = arr == null ? 0 : Math.min(byH, arr.length());
            if (total > n && n == byH && n > 1 && (minH - 80) - n * 36 < 26) n--;
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_buy);
            if (av != null) rv.setImageViewBitmap(R.id.b_icon, av);
            else rv.setImageViewResource(R.id.b_icon, R.drawable.w_coin);
            rv.setTextViewText(R.id.b_count, String.valueOf(total));
            rv.setTextViewText(R.id.b_sub, !has ? "Open the app once" : total == 0 ? "All done" : total == 1 ? "1 item left" : total + " items left");
            for (int i = 0; i < 7; i++) {
                if (i < n) {
                    rv.setViewVisibility(rows[i], View.VISIBLE);
                    String tid = idOf(arr, i);
                    WidgetTick.style(c, rv, "buy", tid, circ[i], txt[i], textOf(arr, i));
                    rv.setOnClickPendingIntent(rows[i], WidgetTick.intent(c, "buy", tid));
                } else rv.setViewVisibility(rows[i], View.GONE);
            }
            int extra = total - n;
            rv.setViewVisibility(R.id.b_more, extra > 0 ? View.VISIBLE : View.GONE);
            rv.setTextViewText(R.id.b_more, "+" + extra + " more");
            rv.setViewVisibility(R.id.b_empty, n == 0 ? View.VISIBLE : View.GONE);
            rv.setTextViewText(R.id.b_empty, has ? "Nothing to buy. All done!" : "Open the app once to fill this widget");
            rv.setOnClickPendingIntent(R.id.b_root, openIntent(c));
            mgr.updateAppWidget(id, rv);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, Bundle newOptions) {
        render(context, mgr, new int[]{id});
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        render(context, mgr, ids);
    }
}
