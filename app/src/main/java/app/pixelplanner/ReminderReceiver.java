package app.pixelplanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shows one reminder notification when its alarm fires, even if the app is closed. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "pixel_reminders";

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Pixel Planner reminders", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Focus timers, time-table blocks, tasks and daily nudges");
                nm.createNotificationChannel(ch);
            }
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int id = intent.getIntExtra("id", 0);
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        ensureChannel(ctx);
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL) : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_mono)
                .setContentTitle(title == null ? "Pixel Planner" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(Cards.card(ctx, title, body, Cards.accentFor(title)))
                .setCustomBigContentView(Cards.card(ctx, title, body, Cards.accentFor(title)))
                .setSubText("Pixel Planner")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(Cards.accentFor(title));
        try {
            String acts = intent.getStringExtra("actions");
            if (acts != null && acts.length() > 2) {
                JSONArray aa = new JSONArray(acts);
                for (int i = 0; i < aa.length() && i < 3; i++) {
                    JSONObject a = aa.getJSONObject(i);
                    String k = a.optString("k");
                    PendingIntent api;
                    int rc = (id * 31 + i + 1) & 0x7fffffff;
                    if ("focus".equals(k)) {
                        Intent fi = new Intent(ctx, MainActivity.class);
                        fi.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        fi.setAction("app.pixelplanner.ACT." + rc);
                        fi.putExtra("act_k", k);
                        fi.putExtra("act_b", a.optString("b"));
                        api = PendingIntent.getActivity(ctx, rc, fi, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        Intent ai = new Intent(ctx, ActionReceiver.class);
                        ai.setAction("app.pixelplanner.ACT." + rc);
                        ai.putExtra("id", id);
                        ai.putExtra("k", k);
                        ai.putExtra("n", a.optInt("n"));
                        ai.putExtra("t", a.optString("t", null));
                        ai.putExtra("b", a.optString("b", null));
                        api = PendingIntent.getBroadcast(ctx, rc, ai, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    }
                    b.addAction(new Notification.Action.Builder(
                            android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_mono),
                            a.optString("l", "OK"), api).build());
                }
            }
        } catch (Exception ignored) { }
        try {
            java.io.File icon = new java.io.File(ctx.getFilesDir(), "pixel-icon.png");
            if (icon.exists()) {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(icon.getAbsolutePath());
                if (bmp != null) b.setLargeIcon(bmp);
            }
        } catch (Exception ignored) { }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        try { nm.notify(id, b.build()); } catch (Exception ignored) { }
        // forget it so a reboot does not fire it again
        try {
            JSONArray arr = new JSONArray(Reminders.read(ctx));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optInt("id") != id) keep.put(o);
            }
            Reminders.write(ctx, keep.toString());
        } catch (Exception ignored) { }
    }
}
