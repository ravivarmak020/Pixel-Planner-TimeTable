package app.pixelplanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try { Reminders.restore(ctx); } catch (Exception ignored) { }
    }
}
