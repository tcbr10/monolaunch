package com.monobogdan.monolaunch;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

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
        public RemoteInput[] remoteInputs;

        public NotificationAction(String title, PendingIntent intent, Icon icon, RemoteInput[] remoteInputs) {
            this.title = title;
            this.intent = intent;
            this.icon = icon;
            this.remoteInputs = remoteInputs;
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
            CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
            this.text = textSeq != null ? textSeq.toString() : "";
        }

        // Extract actions
        this.actions = new ArrayList<>();
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                RemoteInput[] remoteInputs = action.getRemoteInputs();
                if (remoteInputs != null && remoteInputs.length > 0) {
                    this.canReply = true;
                    this.replyIntent = action.actionIntent;
                }
                actions.add(new NotificationAction(
                    action.title != null ? action.title.toString() : "",
                    action.actionIntent,
                    action.getIcon(),
                    remoteInputs
                ));
            }
        }
    }
}
