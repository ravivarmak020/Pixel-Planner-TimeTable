package app.pixelplanner;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Handles a button tapped inside a notification (for example "+250 ml" or "DONE"), even when the app is closed. */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String k = intent.getStringExtra("k");
        int n = intent.getIntExtra("n", 0);
        String t = intent.getStringExtra("t");
        String b = intent.getStringExtra("b");
        Reminders.queue(ctx, k, n, t, b);
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(intent.getIntExtra("id", 0));
        } catch (Exception ignored) { }
        String msg = "Saved";
        if ("water".equals(k)) msg = "Saved +" + n + " ml";
        else if ("protein".equals(k)) msg = "Saved +" + n + " g protein";
        else if ("done".equals(k)) msg = "Task marked done";
        try { Toast.makeText(ctx, msg + ". It updates when you open Pixel Planner.", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) { }
    }
}
