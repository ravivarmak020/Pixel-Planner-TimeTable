package app.pixelplanner;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Keeps the running-timer card in the notification bar while a focus or break timer runs. */
public class TimerService extends Service {
    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        long endAt = i == null ? 0 : i.getLongExtra("endAt", 0);
        String label = i == null ? null : i.getStringExtra("label");
        Notification n;
        try {
            n = TimerReceiver.runNotif(this, endAt, label == null ? "FOCUS" : label);
        } catch (Throwable t) {
            n = new Notification.Builder(this, TimerReceiver.CH_RUN).setSmallIcon(R.drawable.ic_mono).setContentTitle(label == null ? "FOCUS" : label).setOngoing(true).build();
        }
        try {
            // always promote first, otherwise Android kills the app for not calling startForeground in time
            startForeground(TimerReceiver.ID_RUN, n);
        } catch (Throwable t) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if ((i != null && i.getBooleanExtra("stop", false)) || endAt <= System.currentTimeMillis()) {
            try { stopForeground(true); } catch (Throwable ignored) { }
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
