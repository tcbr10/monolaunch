package com.monobogdan.monolaunch;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.lang.reflect.Method;
import java.util.Locale;

public class CommandCenterView extends View {
    private static final String TAG = "CommandCenter";
    
    private Paint titlePaint;
    private Paint textPaint;
    private Paint selectedPaint;
    private Paint statusPaint;
    private Paint headerPaint;
    private Paint iconBgPaint;
    
    private int selectedIndex = 0;
    private boolean isRTL;
    private Launcher launcher;
    
    private AudioManager audioManager;
    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    
    // Brightness control
    private int currentBrightnessLevel = 0; // 0=Low, 1=Medium, 2=High
    
    private enum QuickSetting {
        SOUND_PROFILE,
        WIFI,
        BLUETOOTH,
        BRIGHTNESS,
        AIRPLANE_MODE,
        MOBILE_DATA,
        HOTSPOT
    }
    
    private QuickSetting[] settings = QuickSetting.values();

    public CommandCenterView(Context context) {
        super(context);
        
        if (context instanceof Launcher) {
            launcher = (Launcher) context;
        }
        
        // Disable focus highlight
        setFocusableInTouchMode(false);
        setDefaultFocusHighlightEnabled(false);
        
        isRTL = isRTLLanguage();
        setupPaints();
        
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth not available", e);
        }
        
        // Get current brightness level
        try {
            int brightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            if (brightness < 85) currentBrightnessLevel = 0;
            else if (brightness < 170) currentBrightnessLevel = 1;
            else currentBrightnessLevel = 2;
        } catch (Exception e) {
            currentBrightnessLevel = 1;
        }
    }

    private boolean isRTLLanguage() {
        Locale locale = getResources().getConfiguration().locale;
        String language = locale.getLanguage();
        return language.equals("he") || language.equals("ar") || language.equals("iw");
    }

    private void setupPaints() {
        headerPaint = new Paint();
        headerPaint.setColor(Color.WHITE);
        headerPaint.setAntiAlias(true);
        headerPaint.setTextSize(spToPx(20));
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        titlePaint = new Paint();
        titlePaint.setColor(Color.WHITE);
        titlePaint.setAntiAlias(true);
        titlePaint.setTextSize(spToPx(18));
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        textPaint = new Paint();
        textPaint.setColor(Color.rgb(220, 220, 220));
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(spToPx(16));
        
        selectedPaint = new Paint();
        selectedPaint.setColor(Color.argb(120, 0, 150, 200));
        selectedPaint.setStyle(Paint.Style.FILL);
        
        statusPaint = new Paint();
        statusPaint.setAntiAlias(true);
        statusPaint.setTextSize(spToPx(14));
        
        iconBgPaint = new Paint();
        iconBgPaint.setColor(Color.argb(100, 255, 255, 255));
        iconBgPaint.setStyle(Paint.Style.FILL);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp,
            getResources().getDisplayMetrics());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float yPos = dpToPx(15);
        
        // Draw header with background
        canvas.drawRect(0, 0, getWidth(), dpToPx(50), iconBgPaint);
        
        String header = isRTL ? "מרכז פקודה" : "Command Center";
        String notificationHint = isRTL ? "[0] התראות" : "[0] Notifications";
        
        float headerX = isRTL ? getWidth() - headerPaint.measureText(header) - dpToPx(10) : dpToPx(10);
        canvas.drawText(header, headerX, yPos + dpToPx(20), headerPaint);
        
        float hintX = isRTL ? dpToPx(10) : getWidth() - timePaint.measureText(notificationHint) - dpToPx(10);
        canvas.drawText(notificationHint, hintX, yPos + dpToPx(20), statusPaint);
        
        yPos += dpToPx(55);

        // Draw quick settings
        for (int i = 0; i < settings.length; i++) {
            QuickSetting setting = settings[i];
            
            float itemStartY = yPos - dpToPx(18);
            
            if (i == selectedIndex) {
                canvas.drawRect(dpToPx(5), itemStartY, getWidth() - dpToPx(5), itemStartY + dpToPx(45), selectedPaint);
            }

            String settingName = getSettingName(setting);
            String status = getSettingStatus(setting);
            
            // Number key hint
            String numberHint = "[" + (i + 1) + "] ";
            
            float nameStartX = dpToPx(15);
            
            if (isRTL) {
                float nameX = getWidth() - textPaint.measureText(settingName) - dpToPx(15);
                canvas.drawText(settingName, nameX, yPos, textPaint);
                
                Paint paint = isStatusOn(status) ? getOnPaint() : getOffPaint();
                canvas.drawText(status, nameStartX, yPos, paint);
                canvas.drawText(numberHint, nameX - textPaint.measureText(numberHint) - dpToPx(5), yPos, statusPaint);
            } else {
                canvas.drawText(numberHint, nameStartX, yPos, statusPaint);
                float nameX = nameStartX + statusPaint.measureText(numberHint);
                canvas.drawText(settingName, nameX, yPos, textPaint);
                
                Paint paint = isStatusOn(status) ? getOnPaint() : getOffPaint();
                float statusX = getWidth() - paint.measureText(status) - dpToPx(15);
                canvas.drawText(status, statusX, yPos, paint);
            }
            
            yPos += dpToPx(50);
        }

        // Draw instructions at bottom
        yPos = getHeight() - dpToPx(25);
        String instructions = isRTL ? 
            "[1-7] החלף מהר | [חזור] סגור" : 
            "[1-7] Quick Toggle | [Back] Close";
        float instrX = (getWidth() - statusPaint.measureText(instructions)) / 2;
        canvas.drawText(instructions, instrX, yPos, statusPaint);
    }

    private boolean isStatusOn(String status) {
        return status.equals("ON") || status.equals("פעיל") || 
               status.equals("Ring") || status.equals("צלצול") ||
               status.equals("Low") || status.equals("Medium") || status.equals("High") ||
               status.equals("נמוך") || status.equals("בינוני") || status.equals("גבוה");
    }

    private Paint getOnPaint() {
        Paint paint = new Paint(statusPaint);
        paint.setColor(Color.rgb(0, 200, 100));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return paint;
    }

    private Paint getOffPaint() {
        Paint paint = new Paint(statusPaint);
        paint.setColor(Color.GRAY);
        return paint;
    }
    
    private Paint timePaint = statusPaint;

    private String getSettingName(QuickSetting setting) {
        if (isRTL) {
            switch (setting) {
                case SOUND_PROFILE: return "פרופיל קול";
                case WIFI: return "WiFi";
                case BLUETOOTH: return "Bluetooth";
                case BRIGHTNESS: return "בהירות";
                case AIRPLANE_MODE: return "מצב טיסה";
                case MOBILE_DATA: return "נתונים";
                case HOTSPOT: return "נקודה חמה";
            }
        } else {
            switch (setting) {
                case SOUND_PROFILE: return "Sound";
                case WIFI: return "WiFi";
                case BLUETOOTH: return "Bluetooth";
                case BRIGHTNESS: return "Brightness";
                case AIRPLANE_MODE: return "Airplane";
                case MOBILE_DATA: return "Data";
                case HOTSPOT: return "Hotspot";
            }
        }
        return "";
    }

    private String getSettingStatus(QuickSetting setting) {
        try {
            switch (setting) {
                case SOUND_PROFILE:
                    int ringerMode = audioManager.getRingerMode();
                    if (isRTL) {
                        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) return "צלצול";
                        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) return "רטט";
                        return "שקט";
                    } else {
                        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) return "Ring";
                        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) return "Vibrate";
                        return "Silent";
                    }
                    
                case WIFI:
                    boolean wifiEnabled = wifiManager != null && wifiManager.isWifiEnabled();
                    return isRTL ? (wifiEnabled ? "פעיל" : "כבוי") : (wifiEnabled ? "ON" : "OFF");
                    
                case BLUETOOTH:
                    if (bluetoothAdapter == null) return isRTL ? "לא זמין" : "N/A";
                    boolean btEnabled = bluetoothAdapter.isEnabled();
                    return isRTL ? (btEnabled ? "פעיל" : "כבוי") : (btEnabled ? "ON" : "OFF");
                    
                case BRIGHTNESS:
                    if (isRTL) {
                        if (currentBrightnessLevel == 0) return "נמוך";
                        if (currentBrightnessLevel == 1) return "בינוני";
                        return "גבוה";
                    } else {
                        if (currentBrightnessLevel == 0) return "Low";
                        if (currentBrightnessLevel == 1) return "Medium";
                        return "High";
                    }
                    
                case AIRPLANE_MODE:
                    int airplaneMode = Settings.Global.getInt(getContext().getContentResolver(), 
                        Settings.Global.AIRPLANE_MODE_ON, 0);
                    return isRTL ? (airplaneMode == 1 ? "פעיל" : "כבוי") : (airplaneMode == 1 ? "ON" : "OFF");
                    
                default:
                    return isRTL ? "כבוי" : "OFF";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting setting status", e);
            return isRTL ? "שגיאה" : "Error";
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        event.startTracking();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, "Key: " + keyCode);

        // Switch to notifications with 0
        if (keyCode == KeyEvent.KEYCODE_0) {
            if (launcher != null) {
                launcher.switchToNotificationCenter();
            }
            return true;
        }

        // DPAD navigation only
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedIndex > 0) {
                selectedIndex--;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (selectedIndex < settings.length - 1) {
                selectedIndex++;
                invalidate();
            }
            return true;
        }

        // Quick select and toggle by number (1-7)
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_7) {
            int index = keyCode - KeyEvent.KEYCODE_1;
            if (index < settings.length) {
                selectedIndex = index;
                toggleSetting(settings[selectedIndex]);
                invalidate();
            }
            return true;
        }

        // Toggle selected with CENTER
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            toggleSetting(settings[selectedIndex]);
            invalidate();
            return true;
        }

        // Back to home
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ENDCALL) {
            if (launcher != null) {
                launcher.switchToHome();
            }
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    private void toggleSetting(QuickSetting setting) {
        try {
            switch (setting) {
                case SOUND_PROFILE:
                    toggleSoundProfile();
                    break;
                    
                case WIFI:
                    if (wifiManager != null) {
                        wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
                    }
                    break;
                    
                case BLUETOOTH:
                    toggleBluetooth();
                    break;
                    
                case BRIGHTNESS:
                    toggleBrightness();
                    break;
                    
                case AIRPLANE_MODE:
                    toggleAirplaneMode();
                    break;
                    
                case HOTSPOT:
                    toggleHotspot();
                    break;
                    
                case MOBILE_DATA:
                    toggleMobileData();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle setting: " + setting, e);
            Toast.makeText(getContext(), "Failed to toggle " + setting, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleSoundProfile() {
        int currentMode = audioManager.getRingerMode();
        
        // Cycle: Normal -> Vibrate -> Silent -> Normal
        switch (currentMode) {
            case AudioManager.RINGER_MODE_NORMAL:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                break;
            case AudioManager.RINGER_MODE_SILENT:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                break;
        }
    }

    private void toggleBluetooth() {
        if (bluetoothAdapter == null) return;
        
        // Check permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                if (launcher != null) {
                    ActivityCompat.requestPermissions(launcher, 
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                }
                return;
            }
        }
        
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
        } else {
            bluetoothAdapter.enable();
        }
    }

    private void toggleBrightness() {
        // Check permission
        if (!Settings.System.canWrite(getContext())) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            getContext().startActivity(intent);
            return;
        }
        
        // Cycle through brightness levels: Low (30) -> Medium (128) -> High (255)
        currentBrightnessLevel = (currentBrightnessLevel + 1) % 3;
        
        int brightness;
        switch (currentBrightnessLevel) {
            case 0: brightness = 30; break;   // Low
            case 1: brightness = 128; break;  // Medium
            case 2: brightness = 255; break;  // High
            default: brightness = 128;
        }
        
        try {
            Settings.System.putInt(getContext().getContentResolver(), 
                Settings.System.SCREEN_BRIGHTNESS, brightness);
            
            // Apply immediately to current window
            if (launcher != null) {
                android.view.WindowManager.LayoutParams layoutParams = launcher.getWindow().getAttributes();
                layoutParams.screenBrightness = brightness / 255f;
                launcher.getWindow().setAttributes(layoutParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set brightness", e);
        }
    }

    private void toggleAirplaneMode() {
        try {
            int currentState = Settings.Global.getInt(getContext().getContentResolver(), 
                Settings.Global.AIRPLANE_MODE_ON, 0);
            
            // Toggle
            Settings.Global.putInt(getContext().getContentResolver(), 
                Settings.Global.AIRPLANE_MODE_ON, currentState == 0 ? 1 : 0);
            
            // Broadcast the change
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", currentState == 0);
            getContext().sendBroadcast(intent);
        } catch (SecurityException e) {
            // Need WRITE_SECURE_SETTINGS permission
            // Grant via ADB: adb shell pm grant com.monobogdan.monolaunch android.permission.WRITE_SECURE_SETTINGS
            Toast.makeText(getContext(), "Need system permission for airplane mode", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleHotspot() {
        try {
            WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            method.setAccessible(true);
            int apState = (Integer) method.invoke(wifiManager);
            
            Method setMethod = wifiManager.getClass().getDeclaredMethod("setWifiApEnabled", null, boolean.class);
            setMethod.setAccessible(true);
            
            // Toggle
            setMethod.invoke(wifiManager, null, apState != 13); // 13 = WIFI_AP_STATE_ENABLED
        } catch (Exception e) {
            Log.e(TAG, "Hotspot toggle failed, opening settings", e);
            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            getContext().startActivity(intent);
        }
    }

    private void toggleMobileData() {
        try {
            // This requires system permissions or root
            Toast.makeText(getContext(), "Mobile data requires system access", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle mobile data", e);
        }
    }
}
