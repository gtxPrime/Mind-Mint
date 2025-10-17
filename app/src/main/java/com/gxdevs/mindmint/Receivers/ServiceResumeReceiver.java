package com.gxdevs.mindmint.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

public class ServiceResumeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putBoolean("isServicePaused", false).apply();
        sharedPreferences.edit().putLong("resumeTime", 0).apply();
        Toast.makeText(context, "Blocking resumed", Toast.LENGTH_SHORT).show();
    }
}
