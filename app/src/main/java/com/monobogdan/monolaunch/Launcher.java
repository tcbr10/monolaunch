package com.monobogdan.monolaunch;

import android.util.TypedValue;
import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.AppOpsManager;
import android.provider.Settings;

import com.monobogdan.monolaunch.widgets.ClockWidget;
import com.monobogdan.monolaunch.widgets.PlayerWidget;
import com.monobogdan.monolaunch.widgets.StatusWidget;

public class Launcher extends Activity {

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String TAG = "Launcher";

    public class LauncherView extends View {
        final String TAG = "LauncherView";

        private Paint defaultPaint;
        private Paint fontPaint;
        private BitmapDrawable iconMenu;

        private ClockWidget clockWidget;
        private StatusWidget statusWidget;
        private long timeSinceStart;

        private PlayerWidget playerView;

        private float dpToPx(float dp) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp,
                    getResources().getDisplayMetrics());
        }

        private float spToPx(float sp) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    sp,
                    getResources().getDisplayMetrics());
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
        public boolean onKeyUp(int keyCode, KeyEvent event) {

            Log.i(TAG, "onKeyUp: " + keyCode);
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                switchToMainMenu();

                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_CALL) {
                startActivity(getContext().getPackageManager().getLaunchIntentForPackage("com.android.dialer"));

                return true;
            }

            /// dial. copied from
            /// [https://github.com/Barracuda72/minilaunch](https://github.com/Barracuda72/minilaunch)
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
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                startActivity(getContext().getPackageManager().getLaunchIntentForPackage("com.android.contacts"));
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_MENU) {
                startActivity(getContext().getPackageManager().getLaunchIntentForPackage("com.sprd.fileexplorer"));
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
                startActivity(getContext().getPackageManager().getLaunchIntentForPackage("com.android.mms"));

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                try {
                    StatusBarManager barMan = (StatusBarManager) getContext().getSystemService("statusbar");
                    barMan.getClass().getMethod("expandNotificationsPanel").invoke(barMan);
                } catch (Exception e) {
                    Log.i(TAG, "onKeyUp: Failed to bring status");
                }

                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                startActivity(getContext().getPackageManager().getLaunchIntentForPackage("com.android.calendar"));

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                switchToTasks();

                return true;
            }

            // Short press of physical "End Call" key -> send lock screen intent
            if (keyCode == KeyEvent.KEYCODE_ENDCALL) {
                if (keyCode == KeyEvent.KEYCODE_ENDCALL) {
        Log.d(TAG, "ENDCALL detected — about to send broadcast");
        Toast.makeText(this, "ENDCALL detected", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent("com.monobogdan.monolaunch.POWEROFF_SCREEN");
        // target MacroDroid explicitly (helps around Android broadcast restrictions)
        intent.setPackage("com.arlosoft.macrodroid");
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);

        Log.d(TAG, "Broadcast sent");
        return true;
            }

            return super.onKeyUp(keyCode, event);
        }

        private void drawBottomBar(Canvas canvas) {
            float metrics = fontPaint.getFontMetrics().bottom;
            float bottomLine = getHeight() - metrics - dpToPx(3);
            float rightLine = getWidth()
                    - fontPaint.measureText((getApplicationContext().getResources().getString(R.string.contacts)))
                    - dpToPx(5);

            canvas.drawText(getApplicationContext().getResources().getString(R.string.files), dpToPx(5), bottomLine,
                    fontPaint);
            canvas.drawText(getApplicationContext().getResources().getString(R.string.contacts), rightLine, bottomLine,
                    fontPaint);

            float centerLine = getWidth() / 2 - (iconMenu.getMinimumWidth() / 2);

            canvas.drawBitmap(iconMenu.getBitmap(), centerLine, getHeight() - iconMenu.getMinimumHeight() - dpToPx(3),
                    defaultPaint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float baseline = dpToPx(15);

            baseline += clockWidget.draw(canvas, baseline);
            baseline += statusWidget.draw(canvas, baseline);
            baseline += playerView.draw(canvas, baseline);

            clientWidth = getWindow().getDecorView().getWidth();
            clientHeight = getWindow().getDecorView().getHeight(); // HACK!!!

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
        launcherView.animate().alpha(1.0f).setDuration(350);
    }

    private void switchToDialer() {
        setContentView(dialerView);
        dialerView.requestFocus();
        dialerView.setTranslationY(clientHeight);
        dialerView.animate().setDuration(250).translationY(0);
    }

    private void switchToMainMenu() {
        setContentView(appList);
        appList.requestFocus();
        appList.setTranslationY(clientHeight);
        appList.animate().setDuration(250).translationY(0);
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
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Request the permission
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                        REQUEST_EXTERNAL_STORAGE);
                // Set default background for now
                getWindow().setBackgroundDrawableResource(android.R.color.black);
            } else {
                // Permission already granted, load wallpaper
                loadWallpaper();
            }
        } else {
            // Android 5.x and below, no runtime permission needed
            loadWallpaper();
        }

        switchToHome();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, load wallpaper
                Log.i(TAG, "Storage permission granted, loading wallpaper");
                loadWallpaper();
            } else {
                // Permission denied, keep default background
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
            // Permission denied or wallpaper not accessible
            Log.e(TAG, "Failed to load wallpaper: " + e.getMessage());
            getWindow().setBackgroundDrawableResource(android.R.color.black);
        } catch (Exception e) {
            // Any other error loading wallpaper
            Log.e(TAG, "Error loading wallpaper: " + e.getMessage());
            getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
    }
}
