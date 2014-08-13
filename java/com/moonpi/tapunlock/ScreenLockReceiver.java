package com.moonpi.tapunlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class ScreenLockReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        //if SCREEN_OFF event detected, start lockscreen if enabled
        //as new task and with no animation
        if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            Intent intent1 = new Intent(context, LockActivity.class);
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                             | Intent.FLAG_ACTIVITY_NO_ANIMATION);

            context.startActivity(intent1);
        }

        //if BOOT_COMPLETED event detected, start service and lockscreen if enabled
        //as new task and with no animation
        else if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            context.startService(new Intent(context, ScreenLockService.class));

            Intent intent1 = new Intent(context, LockActivity.class);
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);

            context.startActivity(intent1);
        }
    }
}
