package app.pixelplanner;

import android.app.Activity;
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
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
                    } else if (!Reminders.canExact(MainActivity.this) && Build.VERSION.SDK_INT >= 31) {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
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
