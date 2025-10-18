package com.monobogdan.monolaunch;

import android.util.TypedValue;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.AppOpsManager;
import android.provider.Settings;

import com.monobogdan.monolaunch.widgets.ClockWidget;
import com.monobogdan.monolaunch.widgets.PlayerWidget;
import com.monobogdan.monolaunch.widgets.StatusWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Launcher extends Activity {

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String TAG = "Launcher";
    
    // SharedPreferences keys for shortcuts
    private static final String PREFS_NAME = "LauncherShortcuts";
    private static final String KEY_DPAD_LEFT = "shortcut_dpad_left";
    private static final String KEY_DPAD_RIGHT = "shortcut_dpad_right";
    private static final String KEY_DPAD_UP = "shortcut_dpad_up";
    private static final String KEY_MENU = "shortcut_menu";
    private static final String KEY_BACK = "shortcut_back";

    public class LauncherView extends View {
        final String TAG = "LauncherView";

        private Paint defaultPaint;
        private Paint fontPaint;
        private BitmapDrawable iconMenu;

        private ClockWidget clockWidget;
        private StatusWidget statusWidget;
        private long timeSinceStart;

        private PlayerWidget playerView;
        
        // Store shortcuts
        private SharedPreferences shortcuts;

        private float dpToPx(float dp) {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
            );
        }

        private float spToPx(float sp) {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                getResources().getDisplayMetrics()
            );
        }

        public LauncherView(Context ctx) {
            super(ctx);

            clockWidget = new ClockWidget(this);
            playerView = new PlayerWidget(this);

            defaultPaint = new Paint();
            defaultPaint.setColor(Color.WHITE);

            fontPaint = new Paint();
            fontPaint.setColor(Color.WHITE);
            fontPaint.setAntiAlias(true);
            fontPaint.setTextSize(spToPx(16));

            statusWidget = new StatusWidget(this);

            iconMenu = (BitmapDrawable) ctx.getResources().getDrawable(R.drawable.list);
            
            // Initialize SharedPreferences
            shortcuts = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Set default shortcuts if not already set
            setDefaultShortcuts();
        }
        
        private void setDefaultShortcuts() {
            SharedPreferences.Editor editor = shortcuts.edit();
            
            if (!shortcuts.contains(KEY_DPAD_LEFT)) {
                editor.putString(KEY_DPAD_LEFT, "com.android.mms");
            }
            if (!shortcuts.contains(KEY_DPAD_RIGHT)) {
                editor.putString(KEY_DPAD_RIGHT, "com.android.calendar");
            }
            if (!shortcuts.contains(KEY_DPAD_UP)) {
                editor.putString(KEY_DPAD_UP, "tasks"); // Special value for tasks view
            }
            if (!shortcuts.contains(KEY_MENU)) {
                editor.putString(KEY_MENU, "com.sprd.fileexplorer");
            }
            if (!shortcuts.contains(KEY_BACK)) {
                editor.putString(KEY_BACK, "com.android.contacts");
            }
            
            editor.apply();
        }
        
        private void showAppPicker(final String keyName, final String keyLabel) {
            PackageManager pm = getContext().getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            // Filter to show only launchable apps
            final List<ApplicationInfo> launchableApps = new ArrayList<>();
            for (ApplicationInfo app : apps) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    launchableApps.add(app);
                }
            }
            
            // Sort apps by name
            Collections.sort(launchableApps, new Comparator<ApplicationInfo>() {
                @Override
                public int compare(ApplicationInfo a1, ApplicationInfo a2) {
                    return a1.loadLabel(pm).toString().compareToIgnoreCase(
                           a2.loadLabel(pm).toString());
                }
            });
            
            // Create app names array for dialog
            final String[] appNames = new String[launchableApps.size() + 1];
            appNames[0] = "Tasks View (Built-in)";
            for (int i = 0; i < launchableApps.size(); i++) {
                appNames[i + 1] = launchableApps.get(i).loadLabel(pm).toString();
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setTitle("Assign app to " + keyLabel);
            builder.setItems(appNames, (dialog, which) -> {
                SharedPreferences.Editor editor = shortcuts.edit();
                
                if (which == 0) {
                    // Tasks view
                    editor.putString(keyName, "tasks");
                } else {
                    // Selected app
                    String packageName = launchableApps.get(which - 1).packageName;
                    editor.putString(keyName, packageName);
                }
                
                editor.apply();
                Toast.makeText(getContext(), 
                    "Shortcut assigned to " + keyLabel, 
                    Toast.LENGTH_SHORT).show();
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }
        
        private void launchShortcut(String shortcutKey) {
            String packageName = shortcuts.getString(shortcutKey, null);
            
            if (packageName == null) {
                return;
            }
            
            if (packageName.equals("tasks")) {
                switchToTasks();
            } else {
                Intent intent = getContext().getPackageManager()
                    .getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(getContext(), 
                        "App not found", 
                        Toast.LENGTH_SHORT).show();
                }
            }
        }

        public long getTimeSinceStart() {
            return timeSinceStart;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            event.startTracking();
            return super.onKeyDown(keyCode, event);
        }
        
        @Override
        public boolean onKeyLongPress(int keyCode, KeyEvent event) {
            Log.i(TAG, "onKeyLongPress: " + keyCode);
            
            // Long press to configure shortcuts
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    showAppPicker(KEY_DPAD_LEFT, "D-Pad Left");
                    return true;
                    
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    showAppPicker(KEY_DPAD_RIGHT, "D-Pad Right");
                    return true;
                    
                case KeyEvent.KEYCODE_DPAD_UP:
                    showAppPicker(KEY_DPAD_UP, "D-Pad Up");
                    return true;
                    
                case KeyEvent.KEYCODE_MENU:
                    showAppPicker(KEY_MENU, "Menu");
                    return true;
                    
                case KeyEvent.KEYCODE_BACK:
                    showAppPicker(KEY_BACK, "Back");
                    return true;
            }
            
            return super.onKeyLongPress(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            Log.i(TAG, "onKeyUp: " + keyCode);
            
            // Center button for main menu
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                switchToMainMenu();
                return true;
            }

            // Call button for dialer
            if (keyCode == KeyEvent.KEYCODE_CALL) {
                startActivity(getContext().getPackageManager()
                    .getLaunchIntentForPackage("com.android.dialer"));
                return true;
            }

            // Dial shortcuts (0-9, *, #)
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + (keyCode - KeyEvent.KEYCODE_0)));
                startActivity(intent);
                return true;
            }
            
            if (keyCode == KeyEvent.KEYCODE_POUND) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:#"));
                startActivity(intent);
                return true;
            }
            
            if (keyCode == KeyEvent.KEYCODE_STAR) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:*"));
                startActivity(intent);
                return true;
            }
            
            // Dynamic shortcuts for D-pad and other keys
            if (keyCode == KeyEvent.KEYCODE_BACK && 
                !event.isCanceled() && 
                (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0) {
                launchShortcut(KEY_BACK);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_MENU && 
                (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0) {
                launchShortcut(KEY_MENU);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && 
                (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0) {
                launchShortcut(KEY_DPAD_LEFT);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                try {
                    StatusBarManager barMan = (StatusBarManager) 
                        getContext().getSystemService("statusbar");
                    barMan.getClass().getMethod("expandNotificationsPanel").invoke(barMan);
                } catch (Exception e) {
                    Log.i(TAG, "onKeyUp: Failed to bring status");
                }
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && 
                (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0) {
                launchShortcut(KEY_DPAD_RIGHT);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && 
                (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0) {
                launchShortcut(KEY_DPAD_UP);
                return true;
            }

            return super.onKeyUp(keyCode, event);
        }

        private void drawBottomBar(Canvas canvas) {
            float metrics = fontPaint.getFontMetrics().bottom;
            float bottomLine = getHeight() - metrics - dpToPx(3);
            
            // Get app names for shortcuts
            String leftLabel = getShortcutLabel(KEY_MENU);
            String rightLabel = getShortcutLabel(KEY_BACK);
            
            float rightLine = getWidth() - fontPaint.measureText(rightLabel) - dpToPx(5);

            canvas.drawText(leftLabel, dpToPx(5), bottomLine, fontPaint);
            canvas.drawText(rightLabel, rightLine, bottomLine, fontPaint);

            float centerLine = getWidth() / 2 - (iconMenu.getMinimumWidth() / 2);

            canvas.drawBitmap(iconMenu.getBitmap(), centerLine, 
                getHeight() - iconMenu.getMinimumHeight() - dpToPx(3), defaultPaint);
        }
        
        private String getShortcutLabel(String key) {
            String packageName = shortcuts.getString(key, null);
            
            if (packageName == null) {
                return "Not Set";
            }
            
            if (packageName.equals("tasks")) {
                return "Tasks";
            }
            
            try {
                PackageManager pm = getContext().getPackageManager();
                ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
                String label = pm.getApplicationLabel(app).toString();
                
                // Shorten long app names
                if (label.length() > 10) {
                    return label.substring(0, 9) + "...";
                }
                return label;
            } catch (PackageManager.NameNotFoundException e) {
                return "Unknown";
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float baseline = dpToPx(15);

            baseline += clockWidget.draw(canvas, baseline);
            baseline += statusWidget.draw(canvas, baseline);
            baseline += playerView.draw(canvas, baseline);

            clientWidth = getWindow().getDecorView().getWidth();
            clientHeight = getWindow().getDecorView().getHeight();

            drawBottomBar(canvas);
        }
    }

    private Drawable cachedBackground;
    private LauncherView launcherView;
    private AppListView appList;
    private DialerView dialerView;
    private Tasks tasks;

    private int clientHeight;
    private int clientWidth;

    public Drawable getCachedBackground() {
        return cachedBackground;
    }

    public void switchToHome() {
        setContentView(launcherView);
        launcherView.requestFocus();

        launcherView.setAlpha(0);
        launcherView.animate().
                alpha(1.0f).
                setDuration(350);
    }

    private void switchToDialer() {
        setContentView(dialerView);
        dialerView.requestFocus();
        dialerView.setTranslationY(clientHeight);
        dialerView.animate().
                setDuration(250).
                translationY(0);
    }

    private void switchToMainMenu() {
        setContentView(appList);
        appList.requestFocus();
        appList.setTranslationY(clientHeight);
        appList.animate().
                setDuration(250).
                translationY(0);
    }

    private void switchToTasks() {
        tasks.updateTaskList();
        setContentView(tasks);
        tasks.requestFocus();
        tasks.setTranslationX(clientWidth);
        tasks.animate().setDuration(250).translationX(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prompt user to grant Usage Access if needed
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());

        if (mode != AppOpsManager.MODE_ALLOWED) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        }

        // Initialize views
        dialerView = new DialerView(getApplicationContext());
        tasks = new Tasks(this);

        tasks.setFocusable(true);
        dialerView.setFocusable(true);

        launcherView = new LauncherView(getApplicationContext());
        appList = new AppListView(this);
        appList.setFocusable(true);
        launcherView.setFocusable(true);
        launcherView.requestFocus();

        cachedBackground = getWindow().getDecorView().getBackground();

        // Check and request permission for wallpaper access on Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_EXTERNAL_STORAGE);
                getWindow().setBackgroundDrawableResource(android.R.color.black);
            } else {
                loadWallpaper();
            }
        } else {
            loadWallpaper();
        }

        switchToHome();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Storage permission granted, loading wallpaper");
                loadWallpaper();
            } else {
                Log.w(TAG, "Storage permission denied, using default background");
                getWindow().setBackgroundDrawableResource(android.R.color.black);
            }
        }
    }

    private void loadWallpaper() {
        try {
            Drawable wallpaper = getWallpaper();
            if (wallpaper != null) {
                getWindow().setBackgroundDrawable(wallpaper);
            } else {
                getWindow().setBackgroundDrawableResource(android.R.color.black);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to load wallpaper: " + e.getMessage());
            getWindow().setBackgroundDrawableResource(android.R.color.black);
        } catch (Exception e) {
            Log.e(TAG, "Error loading wallpaper: " + e.getMessage());
            getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
    }
}
