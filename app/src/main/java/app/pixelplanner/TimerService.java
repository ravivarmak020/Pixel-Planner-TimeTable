package app.pixelplanner;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the running-timer card in the notification bar while a focus or break timer runs. */
public class TimerService extends Service {
    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        long endAt = i == null ? 0 : i.getLongExtra("endAt", 0);
        String label = i == null ? null : i.getStringExtra("label");
        if (endAt <= System.currentTimeMillis()) { stopSelf(); return START_NOT_STICKY; }
        Notification n = TimerReceiver.runNotif(this, endAt, label == null ? "FOCUS" : label);
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(TimerReceiver.ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(TimerReceiver.ID_RUN, n);
        } catch (Exception e) { stopSelf(); }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
