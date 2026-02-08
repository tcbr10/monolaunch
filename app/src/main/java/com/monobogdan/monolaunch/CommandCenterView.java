package com.monobogdan.monolaunch;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;

import java.util.Locale;

public class CommandCenterView extends View {
    private static final String TAG = "CommandCenter";
    
    private Paint titlePaint;
    private Paint textPaint;
    private Paint selectedPaint;
    private Paint statusPaint;
    
    private int selectedIndex = 0;
    private boolean isRTL;
    private Launcher launcher;
    
    private AudioManager audioManager;
    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    
    private enum QuickSetting {
        SOUND_PROFILE,
        WIFI,
        BLUETOOTH,
        HOTSPOT,
        AIRPLANE_MODE,
        BRIGHTNESS,
        MOBILE_DATA
    }
    
    private QuickSetting[] settings = QuickSetting.values();

    public CommandCenterView(Context context) {
        super(context);
        
        if (context instanceof Launcher) {
            launcher = (Launcher) context;
        }
        
        isRTL = isRTLLanguage();
        setupPaints();
        
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth not available", e);
        }
    }

    private boolean isRTLLanguage() {
        Locale locale = getResources().getConfiguration().locale;
        String language = locale.getLanguage();
        return language.equals("he") || language.equals("ar") || language.equals("iw");
    }

    private void setupPaints() {
        titlePaint = new Paint();
        titlePaint.setColor(Color.WHITE);
        titlePaint.setAntiAlias(true);
        titlePaint.setTextSize(spToPx(18));
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        textPaint = new Paint();
        textPaint.setColor(Color.LTGRAY);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(spToPx(16));
        
        selectedPaint = new Paint();
        selectedPaint.setColor(Color.argb(100, 255, 255, 255));
        
        statusPaint = new Paint();
        statusPaint.setAntiAlias(true);
        statusPaint.setTextSize(spToPx(14));
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

        float yPos = dpToPx(20);
        
        // Draw header
        String header = isRTL ? "מרכז פקודה" : "Command Center";
        String notificationHint = isRTL ? "[0] התראות" : "[0] Notifications";
        
        canvas.drawText(header, dpToPx(10), yPos, titlePaint);
        canvas.drawText(notificationHint, getWidth() - textPaint.measureText(notificationHint) - dpToPx(10), yPos, statusPaint);
        
        yPos += dpToPx(40);

        // Draw quick settings
        for (int i = 0; i < settings.length; i++) {
            QuickSetting setting = settings[i];
            
            if (i == selectedIndex) {
                canvas.drawRect(0, yPos - dpToPx(20), getWidth(), yPos + dpToPx(10), selectedPaint);
            }

            String settingName = getSettingName(setting);
            String status = getSettingStatus(setting);
            
            // Number key hint
            String numberHint = "[" + (i + 1) + "] ";
            
            if (isRTL) {
                float nameX = getWidth() - textPaint.measureText(settingName) - dpToPx(10);
                canvas.drawText(settingName, nameX, yPos, textPaint);
                
                Paint paint = status.equals(isRTL ? "פעיל" : "ON") ? 
                    getOnPaint() : getOffPaint();
                canvas.drawText(status, dpToPx(10), yPos, paint);
                canvas.drawText(numberHint, nameX - textPaint.measureText(numberHint) - dpToPx(5), yPos, statusPaint);
            } else {
                canvas.drawText(numberHint, dpToPx(10), yPos, statusPaint);
                canvas.drawText(settingName, dpToPx(10) + textPaint.measureText(numberHint), yPos, textPaint);
                
                Paint paint = status.equals("ON") ? getOnPaint() : getOffPaint();
                canvas.drawText(status, getWidth() - paint.measureText(status) - dpToPx(10), yPos, paint);
            }
            
            yPos += dpToPx(45);
        }

        // // Draw instructions at bottom
        // yPos = getHeight() - dpToPx(20);
        // String instructions = isRTL ? 
        //     "[1-7] בחר | [5] החלף | [*] חזור" : 
        //     "[1-7] Select | [5] Toggle | [*] Back";
        // canvas.drawText(instructions, dpToPx(10), yPos, statusPaint);
        // Draw instructions at bottom
yPos = getHeight() - dpToPx(20);
String instructions = isRTL ? 
    "[1-7] החלף מהר | [D-pad] נווט | [חזור] סגור" : 
    "[1-7] Quick Toggle | [D-pad] Navigate | [Back] Close";
canvas.drawText(instructions, dpToPx(10), yPos, statusPaint);

    }
    

    private Paint getOnPaint() {
        Paint paint = new Paint(statusPaint);
        paint.setColor(Color.GREEN);
        return paint;
    }

    private Paint getOffPaint() {
        Paint paint = new Paint(statusPaint);
        paint.setColor(Color.GRAY);
        return paint;
    }

    private String getSettingName(QuickSetting setting) {
        if (isRTL) {
            switch (setting) {
                case SOUND_PROFILE: return "פרופיל קול";
                case WIFI: return "WiFi";
                case BLUETOOTH: return "Bluetooth";
                case HOTSPOT: return "נקודה חמה";
                case AIRPLANE_MODE: return "מצב טיסה";
                case BRIGHTNESS: return "בהירות";
                case MOBILE_DATA: return "נתונים סלולריים";
            }
        } else {
            switch (setting) {
                case SOUND_PROFILE: return "Sound Profile";
                case WIFI: return "WiFi";
                case BLUETOOTH: return "Bluetooth";
                case HOTSPOT: return "Hotspot";
                case AIRPLANE_MODE: return "Airplane Mode";
                case BRIGHTNESS: return "Brightness";
                case MOBILE_DATA: return "Mobile Data";
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
                    
                case AIRPLANE_MODE:
                    int airplaneMode = Settings.Global.getInt(getContext().getContentResolver(), 
                        Settings.Global.AIRPLANE_MODE_ON, 0);
                    return isRTL ? (airplaneMode == 1 ? "פעיל" : "כבוי") : (airplaneMode == 1 ? "ON" : "OFF");
                    
                case BRIGHTNESS:
                    return isRTL ? "התאם" : "Adjust";
                    
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
                    if (bluetoothAdapter != null) {
                        if (bluetoothAdapter.isEnabled()) {
                            bluetoothAdapter.disable();
                        } else {
                            bluetoothAdapter.enable();
                        }
                    }
                    break;
                    
                case AIRPLANE_MODE:
                    // Note: This requires WRITE_SECURE_SETTINGS permission
                    // User needs to grant via ADB: adb shell pm grant com.monobogdan.monolaunch android.permission.WRITE_SECURE_SETTINGS
                    Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
                    getContext().startActivity(intent);
                    break;
                    
                case BRIGHTNESS:
                    Intent brightnessIntent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                    getContext().startActivity(brightnessIntent);
                    break;
                    
                case HOTSPOT:
                    Intent hotspotIntent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                    getContext().startActivity(hotspotIntent);
                    break;
                    
                case MOBILE_DATA:
                    Intent dataIntent = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
                    getContext().startActivity(dataIntent);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle setting: " + setting, e);
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
}
