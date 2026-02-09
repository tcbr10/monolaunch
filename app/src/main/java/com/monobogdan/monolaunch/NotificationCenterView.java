package com.monobogdan.monolaunch;

import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

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
    private Paint headerPaint;
    private Paint iconBgPaint;
    
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
        
        // Disable focus highlight
        setFocusableInTouchMode(false);
        setDefaultFocusHighlightEnabled(false);
        
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
        headerPaint = new Paint();
        headerPaint.setColor(Color.WHITE);
        headerPaint.setAntiAlias(true);
        headerPaint.setTextSize(spToPx(20));
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        titlePaint = new Paint();
        titlePaint.setColor(Color.WHITE);
        titlePaint.setAntiAlias(true);
        titlePaint.setTextSize(spToPx(16));
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        textPaint = new Paint();
        textPaint.setColor(Color.rgb(220, 220, 220));
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(spToPx(14));
        
        timePaint = new Paint();
        timePaint.setColor(Color.rgb(150, 150, 150));
        timePaint.setAntiAlias(true);
        timePaint.setTextSize(spToPx(12));
        
        separatorPaint = new Paint();
        separatorPaint.setColor(Color.rgb(60, 60, 60));
        separatorPaint.setStrokeWidth(2);
        
        selectedPaint = new Paint();
        selectedPaint.setColor(Color.argb(120, 0, 150, 200));
        selectedPaint.setStyle(Paint.Style.FILL);
        
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

        float yPos = dpToPx(15);
        
        // Draw header with background
        canvas.drawRect(0, 0, getWidth(), dpToPx(50), iconBgPaint);
        
        String header = isRTL ? "התראות" : "Notifications";
        String commandCenterHint = isRTL ? "[0] מרכז" : "[0] Control";
        
        float headerX = isRTL ? getWidth() - headerPaint.measureText(header) - dpToPx(10) : dpToPx(10);
        canvas.drawText(header, headerX, yPos + dpToPx(20), headerPaint);
        
        float hintX = isRTL ? dpToPx(10) : getWidth() - timePaint.measureText(commandCenterHint) - dpToPx(10);
        canvas.drawText(commandCenterHint, hintX, yPos + dpToPx(20), timePaint);
        
        yPos += dpToPx(40);
        canvas.drawLine(0, yPos, getWidth(), yPos, separatorPaint);
        yPos += dpToPx(15);

        if (notifications.isEmpty()) {
            String emptyMsg = isRTL ? "אין התראות" : "No notifications";
            float emptyX = (getWidth() - textPaint.measureText(emptyMsg)) / 2;
            canvas.drawText(emptyMsg, emptyX, getHeight() / 2, textPaint);
            return;
        }

        // Draw notifications
        for (int i = scrollOffset; i < notifications.size(); i++) {
            NotificationItem item = notifications.get(i);
            
            float startY = yPos - dpToPx(10);
            float itemHeight = dpToPx(100);
            
            // Highlight selected with rounded rect effect
            if (i == selectedIndex) {
                canvas.drawRect(dpToPx(5), startY, getWidth() - dpToPx(5), startY + itemHeight, selectedPaint);
            }

            float xPos = dpToPx(15);
            
            // Draw app icon if available
            if (item.icon != null) {
                try {
                    Drawable iconDrawable = item.icon.loadDrawable(getContext());
                    if (iconDrawable != null) {
                        Bitmap iconBitmap = drawableToBitmap(iconDrawable);
                        int iconSize = (int) dpToPx(32);
                        Bitmap scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true);
                        
                        if (isRTL) {
                            canvas.drawBitmap(scaledIcon, getWidth() - dpToPx(15) - iconSize, yPos, null);
                        } else {
                            canvas.drawBitmap(scaledIcon, xPos, yPos, null);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to draw icon", e);
                }
            }
            
            float textStartX = isRTL ? dpToPx(15) : xPos + dpToPx(40);
            float textEndX = isRTL ? getWidth() - dpToPx(55) : getWidth() - dpToPx(15);
            float textWidth = Math.abs(textEndX - textStartX);
            
            // Draw app name and time on same line
            String timeStr = formatTime(item.timestamp);
            if (isRTL) {
                canvas.drawText(item.appName, textEndX - titlePaint.measureText(item.appName), yPos + dpToPx(10), titlePaint);
                canvas.drawText(timeStr, textStartX, yPos + dpToPx(10), timePaint);
            } else {
                canvas.drawText(item.appName, textStartX, yPos + dpToPx(10), titlePaint);
                canvas.drawText(timeStr, textEndX - timePaint.measureText(timeStr), yPos + dpToPx(10), timePaint);
            }
            
            yPos += dpToPx(25);

            // Draw title (bold)
            if (item.title != null && !item.title.isEmpty()) {
                String displayTitle = truncateText(item.title, titlePaint, textWidth);
                if (isRTL) {
                    canvas.drawText(displayTitle, textEndX - titlePaint.measureText(displayTitle), yPos, titlePaint);
                } else {
                    canvas.drawText(displayTitle, textStartX, yPos, titlePaint);
                }
                yPos += dpToPx(20);
            }

            // Draw text content
            if (item.text != null && !item.text.isEmpty()) {
                String displayText = truncateText(item.text, textPaint, textWidth);
                if (isRTL) {
                    canvas.drawText(displayText, textEndX - textPaint.measureText(displayText), yPos, textPaint);
                } else {
                    canvas.drawText(displayText, textStartX, yPos, textPaint);
                }
                yPos += dpToPx(20);
            }

            // Draw action hints if selected
            if (i == selectedIndex && actionMode == 0) {
                String actionHint = isRTL ? 
                    "[1-9] פתח | [*] פעולות | [#] סגור" : 
                    "[1-9] Open | [*] Actions | [#] Dismiss";
                canvas.drawText(actionHint, textStartX, yPos, timePaint);
                yPos += dpToPx(20);
            } else if (i == selectedIndex && actionMode == 1) {
                yPos = drawActions(canvas, item, yPos);
            }

            yPos += dpToPx(15);
            canvas.drawLine(dpToPx(10), yPos, getWidth() - dpToPx(10), yPos, separatorPaint);
            yPos += dpToPx(15);

            if (yPos > getHeight()) break;
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        
        if (width <= 0) width = 48;
        if (height <= 0) height = 48;
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private float drawActions(Canvas canvas, NotificationItem item, float yPos) {
        String actionHeader = isRTL ? "פעולות:" : "Actions:";
        canvas.drawText(actionHeader, dpToPx(20), yPos, titlePaint);
        yPos += dpToPx(25);

        for (int i = 0; i < item.actions.size() && i < 9; i++) {
            NotificationItem.NotificationAction action = item.actions.get(i);
            
            if (i == selectedActionIndex) {
                canvas.drawRect(dpToPx(15), yPos - dpToPx(18), getWidth() - dpToPx(15), yPos + dpToPx(5), selectedPaint);
            }

            String actionText = "[" + (i + 1) + "] " + action.title;
            canvas.drawText(actionText, dpToPx(25), yPos, textPaint);
            yPos += dpToPx(25);
        }

        String backHint = isRTL ? "[#] חזור" : "[#] Back";
        canvas.drawText(backHint, dpToPx(25), yPos, timePaint);
        yPos += dpToPx(20);

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
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
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
        
        return text.substring(0, Math.max(0, len)) + ellipsis;
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

            // Quick open by number (1-9)
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

            // Execute action by number key (1-9)
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
                // Check if this is a reply action
                if (action.remoteInputs != null && action.remoteInputs.length > 0) {
                    // For quick reply, we'd need to show an input dialog
                    // For now, just trigger the action
                    action.intent.send();
                } else {
                    action.intent.send();
                }
                
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
