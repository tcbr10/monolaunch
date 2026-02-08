package com.monobogdan.monolaunch;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationCenterView extends View implements NotificationService.NotificationUpdateListener {
    private static final String TAG = "NotificationCenter";
    
    private Paint titlePaint;
    private Paint textPaint;
    private Paint separatorPaint;
    private Paint selectedPaint;
    private Paint timePaint;
    
    private List<NotificationItem> notifications = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private int actionMode = 0; // 0 = notification selection, 1 = action selection
    private int selectedActionIndex = 0;
    
    private boolean isRTL;
    private Launcher launcher;

    public NotificationCenterView(Context context) {
        super(context);
        
        if (context instanceof Launcher) {
            launcher = (Launcher) context;
        }
        
        // Check if Hebrew is the system language
        isRTL = isRTLLanguage();
        
        setupPaints();
        
        // Register for notification updates
        NotificationService service = NotificationService.getInstance();
        if (service != null) {
            service.addListener(this);
            service.refreshNotifications();
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
        textPaint.setTextSize(spToPx(14));
        
        timePaint = new Paint();
        timePaint.setColor(Color.GRAY);
        timePaint.setAntiAlias(true);
        timePaint.setTextSize(spToPx(12));
        
        separatorPaint = new Paint();
        separatorPaint.setColor(Color.DKGRAY);
        separatorPaint.setStrokeWidth(1);
        
        selectedPaint = new Paint();
        selectedPaint.setColor(Color.argb(100, 255, 255, 255));
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
    public void onNotificationsUpdated() {
        NotificationService service = NotificationService.getInstance();
        if (service != null) {
            notifications = service.getNotifications();
            if (selectedIndex >= notifications.size() && notifications.size() > 0) {
                selectedIndex = notifications.size() - 1;
            }
            postInvalidate();
        }
    }

    @Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    float yPos = dpToPx(20);
    
    // Draw header
    String header = isRTL ? "התראות" : "Notifications";
    String commandCenterHint = isRTL ? "[0] מרכז פקודה" : "[0] Command Center";
    
    canvas.drawText(header, dpToPx(10), yPos, titlePaint);
    canvas.drawText(commandCenterHint, getWidth() - textPaint.measureText(commandCenterHint) - dpToPx(10), yPos, timePaint);
    
    yPos += dpToPx(10);
    canvas.drawLine(0, yPos, getWidth(), yPos, separatorPaint);
    yPos += dpToPx(15);

    if (notifications.isEmpty()) {
        String emptyMsg = isRTL ? "אין התראות" : "No notifications";
        canvas.drawText(emptyMsg, dpToPx(10), yPos, textPaint);
        return;
    }

    // Draw notifications
    int visibleCount = 0;
    for (int i = scrollOffset; i < notifications.size(); i++) {
        NotificationItem item = notifications.get(i);
        
        // Highlight selected
        if (i == selectedIndex) {
            canvas.drawRect(0, yPos - dpToPx(15), getWidth(), yPos + dpToPx(70), selectedPaint);
        }

        float xPos = isRTL ? getWidth() - dpToPx(10) : dpToPx(10);
        
        // Draw app name and time
        String timeStr = formatTime(item.timestamp);
        if (isRTL) {
            canvas.drawText(item.appName, xPos, yPos, titlePaint);
            canvas.drawText(timeStr, dpToPx(10), yPos, timePaint);
        } else {
            canvas.drawText(item.appName, xPos, yPos, titlePaint);
            canvas.drawText(timeStr, getWidth() - timePaint.measureText(timeStr) - dpToPx(10), yPos, timePaint);
        }
        
        yPos += dpToPx(20);

        // Draw title
        if (item.title != null && !item.title.isEmpty()) {
            String displayTitle = truncateText(item.title, titlePaint, getWidth() - dpToPx(20));
            if (isRTL) {
                canvas.drawText(displayTitle, getWidth() - titlePaint.measureText(displayTitle) - dpToPx(10), yPos, textPaint);
            } else {
                canvas.drawText(displayTitle, xPos, yPos, textPaint);
            }
            yPos += dpToPx(18);
        }

        // Draw text
        if (item.text != null && !item.text.isEmpty()) {
            String displayText = truncateText(item.text, textPaint, getWidth() - dpToPx(20));
            if (isRTL) {
                canvas.drawText(displayText, getWidth() - textPaint.measureText(displayText) - dpToPx(10), yPos, textPaint);
            } else {
                canvas.drawText(displayText, xPos, yPos, textPaint);
            }
            yPos += dpToPx(18);
        }

        // Draw action hints if selected
        if (i == selectedIndex && actionMode == 0) {
            String actionHint = isRTL ? 
                "[1-9] פתח מהר | [*] פעולות | [#] סגור" : 
                "[1-9] Quick Open | [*] Actions | [#] Dismiss";
            canvas.drawText(actionHint, dpToPx(10), yPos, timePaint);
            yPos += dpToPx(18);
        } else if (i == selectedIndex && actionMode == 1) {
            // Draw available actions
            yPos = drawActions(canvas, item, yPos);
        }

        yPos += dpToPx(10);
        canvas.drawLine(0, yPos, getWidth(), yPos, separatorPaint);
        yPos += dpToPx(15);

        visibleCount++;
        if (yPos > getHeight()) break;
    }
}

private float drawActions(Canvas canvas, NotificationItem item, float yPos) {
    String actionHeader = isRTL ? "פעולות זמינות:" : "Available actions:";
    canvas.drawText(actionHeader, dpToPx(10), yPos, textPaint);
    yPos += dpToPx(20);

    for (int i = 0; i < item.actions.size(); i++) {
        NotificationItem.NotificationAction action = item.actions.get(i);
        
        if (i == selectedActionIndex) {
            canvas.drawRect(dpToPx(5), yPos - dpToPx(15), getWidth() - dpToPx(5), yPos + dpToPx(5), selectedPaint);
        }

        String actionText = "[" + (i + 1) + "] " + action.title;
        canvas.drawText(actionText, dpToPx(15), yPos, textPaint);
        yPos += dpToPx(22);
    }

    String backHint = isRTL ? "[#] חזור" : "[#] Back";
    canvas.drawText(backHint, dpToPx(15), yPos, timePaint);
    yPos += dpToPx(18);

    return yPos;
}


    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 60000) {
            return isRTL ? "כעת" : "now";
        } else if (diff < 3600000) {
            int minutes = (int) (diff / 60000);
            return isRTL ? minutes + " דק'" : minutes + "m";
        } else if (diff < 86400000) {
            int hours = (int) (diff / 3600000);
            return isRTL ? hours + " שע'" : hours + "h";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    private String truncateText(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        
        String ellipsis = "...";
        float ellipsisWidth = paint.measureText(ellipsis);
        
        int len = text.length();
        while (len > 0 && paint.measureText(text.substring(0, len)) + ellipsisWidth > maxWidth) {
            len--;
        }
        
        return text.substring(0, len) + ellipsis;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        event.startTracking();
        return super.onKeyDown(keyCode, event);
    }

    @Override
public boolean onKeyUp(int keyCode, KeyEvent event) {
    Log.i(TAG, "Key: " + keyCode + ", actionMode: " + actionMode);

    // Switch to command center with 0 key
    if (keyCode == KeyEvent.KEYCODE_0) {
        if (launcher != null) {
            launcher.switchToCommandCenter();
        }
        return true;
    }

    // Navigation in notification list
    if (actionMode == 0) {
        // DPAD navigation only
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedIndex > 0) {
                selectedIndex--;
                if (selectedIndex < scrollOffset) {
                    scrollOffset = selectedIndex;
                }
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (notifications.size() > 0 && selectedIndex < notifications.size() - 1) {
                selectedIndex++;
                invalidate();
            }
            return true;
        }

        // Open notification with CENTER
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            openNotification();
            return true;
        }

        // Quick open by number (1-9) - direct selection
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
            int index = keyCode - KeyEvent.KEYCODE_1;
            if (index < notifications.size()) {
                selectedIndex = index;
                openNotification();
            }
            return true;
        }

        // Dismiss notification with #
        if (keyCode == KeyEvent.KEYCODE_POUND) {
            dismissCurrentNotification();
            return true;
        }

        // Show actions menu with *
        if (keyCode == KeyEvent.KEYCODE_STAR && !notifications.isEmpty()) {
            NotificationItem item = notifications.get(selectedIndex);
            if (!item.actions.isEmpty()) {
                actionMode = 1;
                selectedActionIndex = 0;
                invalidate();
            }
            return true;
        }

        // Back to home
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ENDCALL) {
            if (launcher != null) {
                launcher.switchToHome();
            }
            return true;
        }
    }
    // Action selection mode
    else if (actionMode == 1) {
        NotificationItem item = notifications.get(selectedIndex);

        // Navigate actions with DPAD
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedActionIndex > 0) {
                selectedActionIndex--;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (selectedActionIndex < item.actions.size() - 1) {
                selectedActionIndex++;
                invalidate();
            }
            return true;
        }

        // Execute action by number key (1-9) - direct execution
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
            int actionIndex = keyCode - KeyEvent.KEYCODE_1;
            if (actionIndex < item.actions.size()) {
                executeAction(item.actions.get(actionIndex));
            }
            return true;
        }

        // Execute selected action with CENTER
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (selectedActionIndex < item.actions.size()) {
                executeAction(item.actions.get(selectedActionIndex));
            }
            return true;
        }

        // Back to notification list with POUND or BACK
        if (keyCode == KeyEvent.KEYCODE_POUND || keyCode == KeyEvent.KEYCODE_BACK) {
            actionMode = 0;
            invalidate();
            return true;
        }
    }

    return super.onKeyUp(keyCode, event);
}


    private void openNotification() {
        if (notifications.isEmpty() || selectedIndex >= notifications.size()) {
            return;
        }

        NotificationItem item = notifications.get(selectedIndex);
        if (item.contentIntent != null) {
            try {
                item.contentIntent.send();
                if (launcher != null) {
                    launcher.switchToHome();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to open notification", e);
            }
        }
    }

    private void executeAction(NotificationItem.NotificationAction action) {
        if (action.intent != null) {
            try {
                action.intent.send();
                actionMode = 0;
                if (launcher != null) {
                    launcher.switchToHome();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to execute action", e);
            }
        }
    }

    private void dismissCurrentNotification() {
        if (notifications.isEmpty() || selectedIndex >= notifications.size()) {
            return;
        }

        NotificationItem item = notifications.get(selectedIndex);
        NotificationService service = NotificationService.getInstance();
        if (service != null) {
            service.dismissNotification(item.key);
        }
        
        if (selectedIndex >= notifications.size() && notifications.size() > 0) {
            selectedIndex = notifications.size() - 1;
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationService service = NotificationService.getInstance();
        if (service != null) {
            service.removeListener(this);
        }
    }
}
