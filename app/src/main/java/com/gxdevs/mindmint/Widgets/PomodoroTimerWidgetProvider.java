package com.gxdevs.mindmint.Widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.gxdevs.mindmint.Activities.WidgetActionActivity;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.FocusService;
import androidx.preference.PreferenceManager;

public class PomodoroTimerWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_pomodoro_timer);

        Intent intent = new Intent(context, WidgetActionActivity.class);
        intent.putExtra("MODE", "FOCUS_POMODORO");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        // Edit/Settings Button Intent
        Intent settingsIntent = new Intent(context, WidgetActionActivity.class);
        settingsIntent.putExtra("MODE", "FOCUS_POMODORO");
        settingsIntent.putExtra("FORCE_CONFIG", true);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent settingsPendingIntent = PendingIntent.getActivity(context, appWidgetId + 6000, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_settings_btn, settingsPendingIntent);

        views.setTextViewText(R.id.widget_status, context.getString(R.string.tap_to_start));

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateWidgetStatus(Context context, boolean isRunning, String statusText) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, PomodoroTimerWidgetProvider.class));

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_pomodoro_timer);

            views.setTextViewText(R.id.widget_status, statusText);

            boolean isLockedIn = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(FocusService.PREF_IS_LOCKED_IN, false);

            if (isRunning) {
                if (isLockedIn) {
                    Intent openIntent = new Intent(context, com.gxdevs.mindmint.Activities.FocusMode.class);
                    openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent openPendingIntent = PendingIntent.getActivity(context, 200 + appWidgetId, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent);
                } else {
                    Intent stopIntent = new Intent(context, FocusService.class);
                    stopIntent.setAction(FocusService.ACTION_STOP_TIMER);
                    PendingIntent stopPendingIntent = PendingIntent.getService(context, 200 + appWidgetId, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.widget_root, stopPendingIntent);
                }
            } else {
                Intent startIntent = new Intent(context, WidgetActionActivity.class);
                startIntent.putExtra("MODE", "FOCUS_POMODORO");
                startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent startPendingIntent = PendingIntent.getActivity(context, appWidgetId, startIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_root, startPendingIntent);
            }

            // Always enable settings button
            Intent settingsIntent = new Intent(context, WidgetActionActivity.class);
            settingsIntent.putExtra("MODE", "FOCUS_POMODORO");
            settingsIntent.putExtra("FORCE_CONFIG", true);
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent settingsPendingIntent = PendingIntent.getActivity(context, appWidgetId + 6000, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_settings_btn, settingsPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
