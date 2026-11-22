package app.pixelplanner;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.widget.RemoteViews;

/** Handles a tap on a widget check circle: animates the tick, plays a sound, removes the item, tells the app. */
public class WidgetTick extends BroadcastReceiver {
    static final String ACT = "app.pixelplanner.WIDGET_TICK";
    static volatile String kind = "";
    static volatile String id = "";
    static volatile int stage = 0;

    static PendingIntent intent(Context c, String k, String itemId) {
        Intent i = new Intent(c, WidgetTick.class).setAction(ACT).setData(Uri.parse("pp://tick/" + k + "/" + Uri.encode(itemId)));
        i.putExtra("kind", k);
        i.putExtra("id", itemId);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sets the circle and text of one row, showing the tick animation frame if this row is being ticked. */
    static void style(Context c, RemoteViews rv, String k, String itemId, int circle, int txt, String text) {
        boolean me = stage > 0 && kind.equals(k) && id.equals(itemId);
        if (!me) {
            rv.setImageViewResource(circle, R.drawable.w_circle);
            rv.setTextViewText(txt, text);
            rv.setTextColor(txt, c.getColor(R.color.w_text));
        } else if (stage == 1) {
            rv.setImageViewResource(circle, R.drawable.w_circle_fill);
            rv.setTextViewText(txt, text);
            rv.setTextColor(txt, c.getColor(R.color.w_text));
        } else {
            rv.setImageViewResource(circle, R.drawable.w_circle_done);
            SpannableString ss = new SpannableString(text);
            ss.setSpan(new StrikethroughSpan(), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            rv.setTextViewText(txt, ss);
            rv.setTextColor(txt, c.getColor(R.color.w_text_dim));
        }
    }

    @Override
    public void onReceive(final Context ctx, Intent in) {
        final String k = in.getStringExtra("kind"), i = in.getStringExtra("id");
        if (k == null || i == null) return;
        final PendingResult pr = goAsync();
        if (stage > 0) { pr.finish(); return; }
        final Context c = ctx.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        kind = k; id = i; stage = 1;
        BuyWidget.refresh(c);
        h.postDelayed(new Runnable() { @Override public void run() { stage = 2; TickSound.play(); BuyWidget.refresh(c); } }, 170);
        h.postDelayed(new Runnable() { @Override public void run() {
            try {
                BuyWidget.removeItem(c, k, i);
                Reminders.queue(c, "buy".equals(k) ? "bdone" : "done", 0, i, null);
            } finally {
                stage = 0; kind = ""; id = "";
                BuyWidget.refresh(c);
                pr.finish();
            }
        } }, 650);
    }
}
