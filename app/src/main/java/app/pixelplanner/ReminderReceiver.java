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
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(0xFF6B5BD6);
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
