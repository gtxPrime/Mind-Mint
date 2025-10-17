package com.gxdevs.mindmint.Common;

import android.content.Context;

public class IntentActions {
    public static String getActionTimeUpdated(Context context) {
        return context.getPackageName() + ".MINDMINT_APP_USAGE_TIME_UPDATED_ACTION";
    }

    public static String getActionUpdatePackages(Context context) {
        return context.getPackageName() + ".MINDMINT_UPDATE_TRACKED_PACKAGES_ACTION";
    }

    public static String getActionResetAppTimes(Context context) {
        return context.getPackageName() + ".MINDMINT_RESET_APP_TIMES_ACTION";
    }

    public static String getActionPauseService(Context context) {
        return context.getPackageName() + ".MINDMINT_PAUSE_SERVICE_ACTION";
    }
} 