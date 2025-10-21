package com.gxdevs.mindmint.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;

import java.util.Map;

public class MidnightResetReceiver extends BroadcastReceiver {
    private static final String TAG = "MidnightResetReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Map<String, ?> allEntries = sharedPreferences.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().endsWith("_scrolls")) {
                editor.remove(entry.getKey());
                Log.d(TAG, "Removed app scrolls: " + entry.getKey());
            }
        }
        editor.apply();
        AppUsageAccessibilityService.resetDailyViewTracking(context);
        Intent internalRefreshIntent = new Intent(AppUsageAccessibilityService.ACTION_REFRESH_DAILY_STATE_INTERNAL);
        internalRefreshIntent.setPackage(context.getPackageName());
        context.sendBroadcast(internalRefreshIntent);
    }
} 