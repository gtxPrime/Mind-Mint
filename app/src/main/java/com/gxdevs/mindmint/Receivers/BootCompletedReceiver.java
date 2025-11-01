package com.gxdevs.mindmint.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Accessibility services are auto-started by the system after boot if enabled by the user.
            // Send an internal refresh broadcast so that when the service starts it can refresh state quickly.
            try {
                Intent refresh = new Intent(AppUsageAccessibilityService.ACTION_REFRESH_DAILY_STATE_INTERNAL);
                refresh.setPackage(context.getPackageName());
                context.sendBroadcast(refresh);
            } catch (Exception e) {
                Log.e("BootCompletedReceiver", "Failed to broadcast refresh after boot", e);
            }
        }
    }
}


