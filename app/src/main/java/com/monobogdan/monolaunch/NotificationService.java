package com.monobogdan.monolaunch;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationService extends NotificationListenerService {
    private static final String TAG = "NotificationService";
    private static NotificationService instance;
    private List<NotificationItem> notifications = new CopyOnWriteArrayList<>();
    private List<NotificationUpdateListener> listeners = new CopyOnWriteArrayList<>();

    public interface NotificationUpdateListener {
        void onNotificationsUpdated();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "NotificationService created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "Notification posted: " + sbn.getPackageName());
        
        // Filter out system notifications if needed
        if (shouldShowNotification(sbn)) {
            NotificationItem item = new NotificationItem(sbn);
            item.appName = getAppName(sbn.getPackageName());
            notifications.add(item);
            notifyListeners();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG, "Notification removed: " + sbn.getKey());
        notifications.removeIf(item -> item.key.equals(sbn.getKey()));
        notifyListeners();
    }

    private boolean shouldShowNotification(StatusBarNotification sbn) {
        // Filter out our own notifications and system UI
        String pkg = sbn.getPackageName();
        return !pkg.equals(getPackageName()) && 
               !pkg.equals("android") &&
               !pkg.equals("com.android.systemui");
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager()
                .getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0))
                .toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    public void dismissNotification(String key) {
        try {
            cancelNotification(key);
            notifications.removeIf(item -> item.key.equals(key));
            notifyListeners();
        } catch (Exception e) {
            Log.e(TAG, "Failed to dismiss notification", e);
        }
    }

    public void addListener(NotificationUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(NotificationUpdateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (NotificationUpdateListener listener : listeners) {
            listener.onNotificationsUpdated();
        }
    }

    public static NotificationService getInstance() {
        return instance;
    }

    public List<NotificationItem> getNotifications() {
        return new ArrayList<>(notifications);
    }

    public void refreshNotifications() {
        notifications.clear();
        try {
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            if (activeNotifications != null) {
                for (StatusBarNotification sbn : activeNotifications) {
                    if (shouldShowNotification(sbn)) {
                        NotificationItem item = new NotificationItem(sbn);
                        item.appName = getAppName(sbn.getPackageName());
                        notifications.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh notifications", e);
        }
        notifyListeners();
    }
}
