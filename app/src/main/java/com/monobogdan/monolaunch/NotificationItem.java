package com.monobogdan.monolaunch;

import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

public class NotificationItem {
    public String key;
    public String packageName;
    public String appName;
    public String title;
    public String text;
    public long timestamp;
    public Icon icon;
    public PendingIntent contentIntent;
    public List<NotificationAction> actions;
    public boolean canReply;
    public PendingIntent replyIntent;

    public static class NotificationAction {
        public String title;
        public PendingIntent intent;
        public Icon icon;

        public NotificationAction(String title, PendingIntent intent, Icon icon) {
            this.title = title;
            this.intent = intent;
            this.icon = icon;
        }
    }

    public NotificationItem(StatusBarNotification sbn) {
        this.key = sbn.getKey();
        this.packageName = sbn.getPackageName();
        this.timestamp = sbn.getPostTime();

        Notification notification = sbn.getNotification();
        this.icon = notification.getSmallIcon();
        this.contentIntent = notification.contentIntent;
        
        // Extract title and text
        Bundle extras = notification.extras;
        if (extras != null) {
            this.title = extras.getString(Notification.EXTRA_TITLE, "");
            this.text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString();
        }

        // Extract actions
        this.actions = new ArrayList<>();
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    this.canReply = true;
                    this.replyIntent = action.actionIntent;
                }
                actions.add(new NotificationAction(
                    action.title != null ? action.title.toString() : "",
                    action.actionIntent,
                    action.getIcon()
                ));
            }
        }
    }
}
