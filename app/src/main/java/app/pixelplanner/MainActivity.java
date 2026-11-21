package app.pixelplanner;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.provider.Settings;
import android.widget.Toast;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import java.util.ArrayList;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int BG = Color.parseColor("#1B1A2E");
    private static final String BACKUP_NAME = "pixel-planner-backup.json";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        web = new WebView(this);
        web.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Keep the page clear of the status bar, notch, gesture bar and keyboard.
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = insets.getInsets(
                            WindowInsets.Type.systemBars()
                                    | WindowInsets.Type.displayCutout()
                                    | WindowInsets.Type.ime());
                    v.setPadding(i.left, i.top, i.right, i.bottom);
                } else {
                    v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                }
                return insets;
            }
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        });

        handleIntent(getIntent());
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String k = i.getStringExtra("act_k");
        if (k != null) {
            Reminders.queue(this, k, 0, null, i.getStringExtra("act_b"));
            i.removeExtra("act_k");
            if (web != null) web.evaluateJavascript("window.__syncReminders && window.__syncReminders();", null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Make sure the last change is written to the phone before the app goes away.
        web.evaluateJavascript("window.__flushSave && window.__flushSave(); window.__syncReminders && window.__syncReminders(true);", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.evaluateJavascript("window.__syncReminders && window.__syncReminders();", null);
    }

    @Override
    public void onBackPressed() {
        // Send the app to the background instead of closing it, so a running timer is not lost.
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    private File backupFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        return new File(dir, BACKUP_NAME);
    }

    /** Called from the page (window.AndroidBridge). */
    private String pendingBackup = "";

    private void toastJs(String m) {
        final String q = m.replace("'", "");
        if (web != null) web.post(new Runnable() { public void run() { web.evaluateJavascript("window.__toast && window.__toast('" + q + "');", null); } });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if ((req != 7101 && req != 7102) || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            if (req == 7101) {
                java.io.OutputStream os = getContentResolver().openOutputStream(u, "wt");
                os.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                os.close();
                web.evaluateJavascript("window.__backupDone && window.__backupDone(true);", null);
            } else {
                InputStream in = getContentResolver().openInputStream(u);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0 && bo.size() < 8000000) bo.write(b, 0, n);
                in.close();
                String t = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                String js = "window.__restoreBackup && window.__restoreBackup(" + org.json.JSONObject.quote(t) + ");";
                web.evaluateJavascript(js, null);
            }
        } catch (Exception e) {
            web.evaluateJavascript("window.__backupDone && window.__backupDone(false);", null);
        }
    }

    private class Bridge {
        @JavascriptInterface
        public String load() {
            File[] candidates = new File[]{backupFile(), new File(getFilesDir(), BACKUP_NAME)};
            for (File f : candidates) {
                if (f.exists() && f.length() > 0) {
                    try (InputStream in = new FileInputStream(f)) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                        return new String(out.toByteArray(), StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                    }
                }
            }
            return "";
        }

        @JavascriptInterface
        public void save(String json) {
            if (json == null || json.length() < 2) {
                return;
            }
            writeAtomically(backupFile(), json);
            writeAtomically(new File(getFilesDir(), BACKUP_NAME), json);
        }

        @JavascriptInterface
        public String readBackup() {
            try {
                String name = "PixelPlanner-backup.json";
                InputStream in = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentResolver cr = getContentResolver();
                    Uri coll = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    android.database.Cursor c = cr.query(coll,
                            new String[]{android.provider.MediaStore.MediaColumns._ID},
                            android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                                    + android.provider.MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                            new String[]{name, "Download/%"}, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            in = cr.openInputStream(android.content.ContentUris.withAppendedId(coll, c.getLong(0)));
                        }
                        c.close();
                    }
                } else {
                    File f = new File(getExternalFilesDir(null), name);
                    if (f.exists()) {
                        in = new FileInputStream(f);
                    }
                }
                if (in == null) {
                    return "";
                }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bo.write(buf, 0, n);
                }
                in.close();
                return new String(bo.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean exportBackup(String json, String name) {
            if (json == null || json.length() < 2) return false;
            pendingBackup = json;
            final String fn = (name == null || name.length() == 0) ? "PixelPlanner-backup.json" : name.replaceAll("[^A-Za-z0-9._-]", "_");
            runOnUiThread(new Runnable() { public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, fn);
                    startActivityForResult(i, 7101);
                } catch (Exception e) { toastJs("NO FILE PICKER FOUND"); }
            } });
            return true;
        }

        @JavascriptInterface
        public boolean importBackup() {
            runOnUiThread(new Runnable() { public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, 7102);
                } catch (Exception e) { toastJs("NO FILE PICKER FOUND"); }
            } });
            return true;
        }

        @JavascriptInterface
        public boolean saveBackup(String json) {
            if (json == null || json.length() < 2) {
                return false;
            }
            try {
                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                String name = "PixelPlanner-backup.json";
                if (Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentResolver cr = getContentResolver();
                    Uri coll = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    Uri found = null;
                    android.database.Cursor c = cr.query(coll,
                            new String[]{android.provider.MediaStore.MediaColumns._ID},
                            android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                                    + android.provider.MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                            new String[]{name, "Download/%"}, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            found = android.content.ContentUris.withAppendedId(coll, c.getLong(0));
                        }
                        c.close();
                    }
                    if (found == null) {
                        android.content.ContentValues v = new android.content.ContentValues();
                        v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                        v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json");
                        v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                        found = cr.insert(coll, v);
                    }
                    if (found == null) {
                        return false;
                    }
                    java.io.OutputStream os = cr.openOutputStream(found, "wt");
                    if (os == null) {
                        return false;
                    }
                    os.write(data);
                    os.close();
                    return true;
                }
                File dir = getExternalFilesDir(null);
                if (dir == null) {
                    return false;
                }
                FileOutputStream fo = new FileOutputStream(new File(dir, name));
                fo.write(data);
                fo.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String takeActions() {
            return Reminders.take(MainActivity.this);
        }

        @JavascriptInterface
        public void setIcon(String base64Png) {
            if (base64Png == null || base64Png.length() < 20) {
                return;
            }
            try {
                byte[] data = android.util.Base64.decode(base64Png, android.util.Base64.DEFAULT);
                File f = new File(getFilesDir(), "pixel-icon.png");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void schedule(final String json) {
            try { Reminders.replaceAll(MainActivity.this, json == null ? "[]" : json); } catch (Exception ignored) { }
        }

        /** 0 = ready, 1 = notifications not allowed, 2 = exact alarms not allowed. */
        @JavascriptInterface
        public int notifStatus() {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (!nm.areNotificationsEnabled()) return 1;
                if (Build.VERSION.SDK_INT >= 26) {
                    TimerReceiver.channelsFor(MainActivity.this);
                    android.app.NotificationChannel c1 = nm.getNotificationChannel(TimerReceiver.CH_RUN);
                    android.app.NotificationChannel c2 = nm.getNotificationChannel(ReminderReceiver.CHANNEL);
                    if ((c1 != null && c1.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE)
                            || (c2 != null && c2.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE)) return 1;
                }
            } catch (Throwable ignored) { }
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return 1;
            }
            return Reminders.canExact(MainActivity.this) ? 0 : 2;
        }

        @JavascriptInterface
        public void askNotif() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ReminderReceiver.ensureChannel(MainActivity.this);
                    TimerReceiver.channelsFor(MainActivity.this);
                    android.content.SharedPreferences sp = getSharedPreferences("pp", MODE_PRIVATE);
                    boolean asked = sp.getBoolean("askedNotif", false);
                    if (Build.VERSION.SDK_INT >= 33 && !asked
                            && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        sp.edit().putBoolean("askedNotif", true).apply();
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                    } else if (notifStatus() == 1) {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
                        } catch (Exception ignored) { }
                    } else if (!Reminders.canExact(MainActivity.this) && Build.VERSION.SDK_INT >= 31) {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
                    }
                }
            });
        }

        /** Data for the home-screen widget (JSON: title, coins, streak, best, next, tasks[]). */
        @JavascriptInterface
        public void widgetData(String json) {
            try {
                if (json != null && json.length() < 20000) PlannerWidget.save(MainActivity.this, json);
            } catch (Exception ignored) {
            }
        }

        /** Background timer: live countdown notification now, ring at endAt. */
        @JavascriptInterface
        public void timerSet(long endAt, String label, String doneTitle, String doneBody) {
            try {
                TimerReceiver.start(MainActivity.this, endAt, label == null ? "Focus timer" : label,
                        doneTitle == null ? "Timer finished" : doneTitle, doneBody == null ? "" : doneBody);
            } catch (Throwable ignored) {
            }
        }

        @JavascriptInterface
        public void timerClear() {
            try {
                TimerReceiver.stop(MainActivity.this);
            } catch (Throwable ignored) {
            }
        }

        /** The installed app's versionName, used by the in-app update notice. */
        @JavascriptInterface
        public String appVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }

        /** Downloads the new APK inside the app and opens the installer when it finishes. */
        @JavascriptInterface
        public void installUpdate(final String url) {
            if (url == null || !url.startsWith("https://")) {
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                            Toast.makeText(MainActivity.this, "Allow Pixel Planner to install updates, then tap UPDATE again", Toast.LENGTH_LONG).show();
                            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(s);
                            return;
                        }
                        final DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                        File old = new File(getExternalFilesDir(null), "PixelPlanner-update.apk");
                        if (old.exists()) {
                            old.delete();
                        }
                        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                        r.setTitle("Pixel Planner update");
                        r.setMimeType("application/vnd.android.package-archive");
                        r.setDestinationInExternalFilesDir(MainActivity.this, null, "PixelPlanner-update.apk");
                        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        final long id = dm.enqueue(r);
                        Toast.makeText(MainActivity.this, "Downloading the update...", Toast.LENGTH_SHORT).show();
                        BroadcastReceiver rc = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context c, Intent i) {
                                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) {
                                    return;
                                }
                                try {
                                    unregisterReceiver(this);
                                } catch (Exception ignored) {
                                }
                                Uri u = dm.getUriForDownloadedFile(id);
                                if (u == null) {
                                    Toast.makeText(MainActivity.this, "Update download failed. Please try again.", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                Intent inst = new Intent(Intent.ACTION_VIEW);
                                inst.setDataAndType(u, "application/vnd.android.package-archive");
                                inst.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                                try {
                                    startActivity(inst);
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, "Open the downloaded update from your notifications", Toast.LENGTH_LONG).show();
                                }
                            }
                        };
                        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                        if (Build.VERSION.SDK_INT >= 33) {
                            registerReceiver(rc, f, Context.RECEIVER_EXPORTED);
                        } else {
                            registerReceiver(rc, f);
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not start the update download", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        /** Opens a web address (used to download the new APK). Only https links are allowed. */
        @JavascriptInterface
        public void openUrl(final String url) {
            if (url == null || !url.startsWith("https://")) {
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        /** Sets a real alarm in the phone's Clock app. days = comma list of Calendar day numbers (1=Sun..7=Sat), or empty for a one-time alarm. */
        @JavascriptInterface
        public boolean setAlarm(int hour, int minute, String label, String days) {
            try {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                i.putExtra(AlarmClock.EXTRA_HOUR, hour);
                i.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                i.putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "Pixel Planner" : label);
                i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                if (days != null && days.length() > 0) {
                    ArrayList<Integer> list = new ArrayList<Integer>();
                    for (String part : days.split(",")) {
                        try {
                            list.add(Integer.parseInt(part.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (!list.isEmpty()) {
                        i.putExtra(AlarmClock.EXTRA_DAYS, list);
                    }
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (on) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });
        }
    }

    private static void writeAtomically(File target, String data) {
        try {
            File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (!tmp.renameTo(target)) {
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                }
                tmp.delete();
            }
        } catch (Exception ignored) {
        }
    }
}
