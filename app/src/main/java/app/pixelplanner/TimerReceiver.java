package app.pixelplanner;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * Background focus timer: a live countdown notification while it runs and an alarm-ringtone
 * notification when it ends, even if the app was closed.
 */
public class TimerReceiver extends BroadcastReceiver {
    static final String CH_RUN = "pixel_timer_run";
    static final String CH_DONE = "pixel_timer_done";
    static final int ID_RUN = 7001;
    static final int ID_DONE = 7002;

    private static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CH_RUN) == null) {
            NotificationChannel ch = new NotificationChannel(CH_RUN, "Running timer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows the focus or break countdown while it runs");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CH_DONE) == null) {
            NotificationChannel ch = new NotificationChannel(CH_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Rings when a focus or break timer is finished");
            Uri tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (tone == null) tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ch.setSound(tone, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 600});
            nm.createNotificationChannel(ch);
        }
    }

    private static PendingIntent openApp(Context c, int rc) {
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, rc, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent alarmIntent(Context c, String title, String body) {
        Intent i = new Intent(c, TimerReceiver.class);
        i.setAction("app.pixelplanner.TIMER_DONE");
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, ID_DONE, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Start (or replace) the running timer. */
    static void start(Context c, long endAt, String label, String doneTitle, String doneBody) {
        channels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(ID_DONE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH_RUN) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_mono)
                .setContentTitle(label)
                .setContentText("Time left - tap to open Pixel Planner")
                .setContentIntent(openApp(c, ID_RUN))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(endAt)
                .setUsesChronometer(true)
                .setColor(0xFF6B5BD6);
        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        nm.notify(ID_RUN, b.build());
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c, doneTitle, doneBody);
        try {
            if (Reminders.canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi);
        }
    }

    /** Stop the timer notification and cancel the ring. */
    static void stop(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(ID_RUN);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(alarmIntent(c, "", ""));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        channels(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(ID_RUN);
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CH_DONE) : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_mono)
                .setContentTitle(title == null ? "Timer finished" : title)
                .setContentText(body == null ? "" : body)
                .setContentIntent(openApp(ctx, ID_DONE))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setColor(0xFF6B5BD6);
        if (Build.VERSION.SDK_INT < 26) {
            b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).setVibrate(new long[]{0, 400, 200, 400});
        }
        nm.notify(ID_DONE, b.build());
    }
}
