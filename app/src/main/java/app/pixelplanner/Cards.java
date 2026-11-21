package app.pixelplanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;
import java.util.Locale;

/** Builds the pixel-style card used by Pixel Planner notifications. */
final class Cards {
    static int accentFor(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (t.contains("water")) return 0xFF37D6FF;
        if (t.contains("protein")) return 0xFFFF6FA8;
        if (t.contains("break")) return 0xFF8FE0FF;
        if (t.contains("focus") || t.contains("study") || t.contains("timer")) return 0xFF39D353;
        if (t.contains("due") || t.contains("task")) return 0xFFFFA24A;
        if (t.contains("plan") || t.contains("evening") || t.contains("check")) return 0xFFB58CFF;
        return 0xFFFFD23F;
    }

    private static Bitmap avatar(Context c) {
        try {
            File f = new File(c.getFilesDir(), "pixel-icon.png");
            if (f.exists()) return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Small copy of the character icon for the notification's large icon (a huge bitmap can make Android drop the notification). */
    static Bitmap largeIcon(Context ctx) {
        try {
            Bitmap b = avatar(ctx);
            if (b == null) return null;
            return Bitmap.createScaledBitmap(b, 96, 96, false);
        } catch (Throwable t) {
            return null;
        }
    }

    /** A normal card (title + message). */
    static RemoteViews card(Context c, String title, String body, int accent) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.notif_card);
        rv.setTextViewText(R.id.n_title, title == null ? "Pixel Planner" : title);
        rv.setTextViewText(R.id.n_body, body == null ? "" : body);
        rv.setInt(R.id.n_bar, "setBackgroundColor", accent);
        rv.setTextColor(R.id.n_app, accent);
        Bitmap b = avatar(c);
        if (b != null) rv.setImageViewBitmap(R.id.n_icon, b);
        else rv.setImageViewResource(R.id.n_icon, R.drawable.ic_mono);
        return rv;
    }

    /** A card with a big live countdown that ends at endAt. */
    static RemoteViews timerCard(Context c, String title, long endAt, int accent) {
        RemoteViews rv = card(c, title, "Tap to open Pixel Planner", accent);
        rv.setViewVisibility(R.id.n_chrono, View.VISIBLE);
        rv.setChronometer(R.id.n_chrono, SystemClock.elapsedRealtime() + Math.max(0, endAt - System.currentTimeMillis()), "%s", true);
        rv.setChronometerCountDown(R.id.n_chrono, true);
        rv.setTextColor(R.id.n_chrono, accent);
        return rv;
    }
}
