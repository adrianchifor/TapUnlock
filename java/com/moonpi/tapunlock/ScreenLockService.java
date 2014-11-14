package com.moonpi.tapunlock;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;


public class ScreenLockService extends Service {
    private BroadcastReceiver mReceiver; //SCREEN_OFF intent receiver

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);

        // Start receiver for SCREEN_OFF event
        mReceiver = new ScreenLockReceiver();
        registerReceiver(mReceiver, filter);

        // Create intent to start MainActivity when users presses persistent notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

        // Create persistent notification when service is on (lockscreen is enabled)
        Notification notification = new Notification.Builder(this)
                .setLargeIcon(largeIcon)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.notification_text))
                .setContentIntent(pIntent)
                .setPriority(Notification.PRIORITY_MIN)
                .build();

        // Start service in foreground with leet ID and show persistent notification
        int FOREGROUND_ID = 1337;
        startForeground(FOREGROUND_ID, notification);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        // When service stopped, unregister the receiver
        try {
            unregisterReceiver(mReceiver);

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        // When service stopped, stop it from being in the foreground
        // And remove persistent notification
        stopForeground(true);
    }
}
