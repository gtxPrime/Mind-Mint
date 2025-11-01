package com.gxdevs.mindmint.Services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.gxdevs.mindmint.Activities.FocusMode;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.MintCrystals;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FocusService extends Service {

    private static final String TAG = "FocusService";
    private static final String CHANNEL_ID = "TimerChannel";
    private static final String COMPLETION_CHANNEL_ID = "FocusCompletionChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int COMPLETION_NOTIFICATION_ID = 2;
    public static final String PREFS_NAME = "AppData";
    public static final String TOTAL_FOCUSED_TIME_KEY = "TotalFocusedTime";
    public static final String ACTION_START_FOREGROUND_SERVICE = "com.gxdevs.mindmint.Services.action.START_FOREGROUND";
    public static final String ACTION_STOP_TIMER = "com.gxdevs.mindmint.Services.action.STOP_TIMER";

    // Persistent state for robust background handling
    private static final String STATE_PREFS = "FOCUS_TIMER_STATE";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_END_ELAPSED = "end_elapsed";
    private static final String KEY_DURATION = "duration";

    private final IBinder binder = new TimerBinder();
    private long startTimeMillis = 0L;
    private long currentDurationInMillis = Long.MAX_VALUE;
    public boolean isRunning = false;
    public static boolean isPublicFocusRun = false;
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private Handler durationHandler;
    private boolean completedNaturally = false;
    private int lastCompletedDurationMinutes = 0;
    private AlarmManager alarmManager;

    public class TimerBinder extends Binder {
        public FocusService getService() {
            return FocusService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "Service created");
        isPublicFocusRun = false;
        durationHandler = new Handler(Looper.getMainLooper());
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // Restore running timer if the process was killed/recreated
        SharedPreferences sp = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        boolean active = sp.getBoolean(KEY_ACTIVE, false);
        if (active) {
            long endElapsed = sp.getLong(KEY_END_ELAPSED, 0L);
            long duration = sp.getLong(KEY_DURATION, 0L);
            long now = SystemClock.elapsedRealtime();
            if (endElapsed > 0L && duration > 0L) {
                isRunning = true;
                currentDurationInMillis = duration;
                if (endElapsed <= now) {
                    // Treat as a naturally completed session and finalize immediately
                    startTimeMillis = now - duration;
                    startForeground(NOTIFICATION_ID, createNotification(currentDurationInMillis));
                    stopTimer();
                } else {
                    startTimeMillis = endElapsed - duration;
                    startForeground(NOTIFICATION_ID, createNotification(getElapsedMillis()));
                    notificationHandler.removeCallbacks(updateNotificationTask);
                    notificationHandler.post(updateNotificationTask);
                    scheduleStopAlarm(endElapsed);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received action: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_FOREGROUND_SERVICE:
                    currentDurationInMillis = intent.getLongExtra("durationInMillis", Long.MAX_VALUE);
                    Log.d(TAG, "ACTION_START_FOREGROUND_SERVICE: duration = " + currentDurationInMillis);
                    if (!isRunning) {
                        startTimer(currentDurationInMillis);
                    } else {
                        startForeground(NOTIFICATION_ID, createNotification(getElapsedMillis()));
                        notificationHandler.post(updateNotificationTask);
                    }
                    break;
                case ACTION_STOP_TIMER:
                    Log.d(TAG, "ACTION_STOP_TIMER received");
                    stopTimer();
                    return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service onBind");
        return binder;
    }

    public void startTimer(long durationInMillis) {
        if (!isRunning) {
            currentDurationInMillis = durationInMillis;
            startTimeMillis = SystemClock.elapsedRealtime();
            isRunning = true;
            isPublicFocusRun = true;
            completedNaturally = false;
            Log.d(TAG, "Timer logic started. Duration: " + (currentDurationInMillis == Long.MAX_VALUE ? "Infinite" : currentDurationInMillis + "ms"));

            startForeground(NOTIFICATION_ID, createNotification(0));
            notificationHandler.removeCallbacks(updateNotificationTask);
            notificationHandler.post(updateNotificationTask);

            durationHandler.removeCallbacksAndMessages(null);

            if (currentDurationInMillis != Long.MAX_VALUE) {
                long endElapsed = startTimeMillis + currentDurationInMillis;
                // Backup in-process handler
                durationHandler.postDelayed(() -> {
                    if (isRunning) {
                        Log.d(TAG, "Timer duration reached (handler). Stopping timer.");
                        stopTimer();
                    }
                }, currentDurationInMillis);

                // Persist state for robustness
                SharedPreferences sp = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
                sp.edit()
                        .putBoolean(KEY_ACTIVE, true)
                        .putLong(KEY_END_ELAPSED, endElapsed)
                        .putLong(KEY_DURATION, currentDurationInMillis)
                        .apply();

                // Schedule an exact alarm so the timer stops even in doze/background
                scheduleStopAlarm(endElapsed);
            }
        } else {
            Log.d(TAG, "Timer is already running.");
        }
    }

    private void saveDailyFocusStat(long elapsedSeconds) {
        SharedPreferences statsPrefs = getSharedPreferences("FOCUS_STATS_PREFS", MODE_PRIVATE);
        SharedPreferences.Editor editor = statsPrefs.edit();
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String dailyKey = "focus_time_" + today;
        long todaySeconds = statsPrefs.getLong(dailyKey, 0);
        editor.putLong(dailyKey, todaySeconds + elapsedSeconds);
        editor.apply();
    }

    public void stopTimer() {
        if (isRunning) {
            long elapsedMillis = getElapsedMillis();
            boolean wasTimeLimited = currentDurationInMillis != Long.MAX_VALUE;
            boolean stoppedEarly = wasTimeLimited && elapsedMillis < currentDurationInMillis;
            lastCompletedDurationMinutes = (int) (currentDurationInMillis / (60_000L));
            completedNaturally = wasTimeLimited && !stoppedEarly;
            isRunning = false;
            isPublicFocusRun = false;
            startTimeMillis = 0L;
            durationHandler.removeCallbacksAndMessages(null);
            notificationHandler.removeCallbacks(updateNotificationTask);
            cancelStopAlarm();

            // Clear persisted state
            SharedPreferences sp = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
            sp.edit().putBoolean(KEY_ACTIVE, false).remove(KEY_END_ELAPSED).remove(KEY_DURATION).apply();

            Log.d(TAG, "Stopping timer. Elapsed: " + elapsedMillis + "ms");

            // Save daily focus stats
            long elapsedSeconds = elapsedMillis / 1000;
            saveDailyFocusStat(elapsedSeconds);

            // Save to total focused time for HomeActivity display
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            long currentTotal = prefs.getLong(TOTAL_FOCUSED_TIME_KEY, 0);
            prefs.edit().putLong(TOTAL_FOCUSED_TIME_KEY, currentTotal + elapsedSeconds).apply();

            // Award Mint Crystals only when the user completes the full time-limited session
            MintCrystals mintCrystals = new MintCrystals(this);
            int coinsAwarded = 0;
            if (completedNaturally) {
                coinsAwarded = mapCoinsForMinutes(lastCompletedDurationMinutes);
                if (coinsAwarded > 0) {
                    mintCrystals.addCoins(coinsAwarded);
                    Log.i(TAG, "MintCrystals: Awarded " + coinsAwarded + " coins for completing " + lastCompletedDurationMinutes + " minutes.");
                }
            }

            // Deduct 3 coins if user stopped early in a time-limited session
            if (stoppedEarly) {
                mintCrystals.subtractCoins(3);
                Toast.makeText(this, "3 MintCrystals deducted.", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "PeaceCoins: Deducted 3 coins for stopping early in time-limited Focus Mode.");
            }

            Toast.makeText(this, "You have focused for " + formatTime(elapsedMillis), Toast.LENGTH_SHORT).show();

            // Show a high-priority completion notification if finished naturally (background/closed cases)
            if (completedNaturally) {
                showCompletionNotification(lastCompletedDurationMinutes, coinsAwarded);
            }

            stopForeground(true);
        }
    }

    public void stopService() {
        Log.d(TAG, "stopService called from Activity.");
        stopTimer();
        stopSelf();
    }

    public boolean isTimerRunning() {
        return isRunning;
    }

    public long getElapsedMillis() {
        if (!isRunning || startTimeMillis == 0L) {
            return 0L;
        }
        return SystemClock.elapsedRealtime() - startTimeMillis;
    }

    public long getCurrentDuration() {
        return currentDurationInMillis;
    }

    public boolean consumeCompletedNaturally() {
        boolean value = completedNaturally;
        completedNaturally = false;
        return value;
    }

    public int getLastCompletedDurationMinutes() {
        return lastCompletedDurationMinutes;
    }

    private final Runnable updateNotificationTask = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                long elapsedMillis = getElapsedMillis();
                Notification notification = createNotification(elapsedMillis);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, notification);
                }
                notificationHandler.postDelayed(this, 1000);
            } else {
                notificationHandler.removeCallbacks(this);
            }
        }
    };

    private PendingIntent getStopPendingIntent() {
        Intent stopIntent = new Intent(this, FocusService.class);
        stopIntent.setAction(ACTION_STOP_TIMER);
        return PendingIntent.getService(this, 1001, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void scheduleStopAlarm(long triggerElapsedRealtime) {
        if (alarmManager == null) return;
        PendingIntent pi = getStopPendingIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, pi);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to schedule exact stop alarm: " + t.getMessage());
        }
    }

    private void cancelStopAlarm() {
        if (alarmManager == null) return;
        try {
            alarmManager.cancel(getStopPendingIntent());
        } catch (Throwable t) {
            Log.w(TAG, "Failed to cancel stop alarm: " + t.getMessage());
        }
    }

    private Notification createNotification(long elapsedMillis) {
        Intent stopIntent = new Intent(this, FocusService.class);
        stopIntent.setAction(ACTION_STOP_TIMER);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent notificationIntent = new Intent(this, FocusMode.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String timeString;
        if (currentDurationInMillis == Long.MAX_VALUE) {
            timeString = "Focusing: " + formatTime(elapsedMillis);
        } else {
            long remainingMillis = currentDurationInMillis - elapsedMillis;
            if (remainingMillis < 0) remainingMillis = 0;
            timeString = "Time left: " + formatTime(remainingMillis);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Focus Mode Active")
                .setContentText(timeString)
                .setSmallIcon(R.drawable.focus)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.stop_circle, "Stop Focus", stopPendingIntent)
                .build();
    }

    private void showCompletionNotification(int minutes, int coins) {
        Intent openIntent = new Intent(this, FocusMode.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String content = "Completed: " + minutes + " min." + (coins > 0 ? "   +" + coins + " Mint Crystals" : "");
        Bitmap large = null;
        try {
            large = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        } catch (Throwable ignored) {
        }

        Notification notification = new NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
                .setContentTitle("Focus Complete")
                .setContentText(content)
                .setSmallIcon(R.drawable.focus)
                .setLargeIcon(large)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(COMPLETION_NOTIFICATION_ID, notification);
        }
    }

    private String formatTime(long millis) {
        int seconds = (int) (millis / 1000) % 60;
        int minutes = (int) ((millis / (1000 * 60)) % 60);
        int hours = (int) ((millis / (1000 * 60 * 60)) % 24);
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Focus Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("Channel for Focus Timer foreground service notification");

        NotificationChannel completionChannel = new NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Focus Completion Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        completionChannel.setDescription("Alerts when a focus session completes");
        completionChannel.enableVibration(true);
        completionChannel.setVibrationPattern(new long[]{0, 300, 200, 300});

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(completionChannel);
        }
    }

    private int mapCoinsForMinutes(int minutes) {
        if (minutes <= 0) return 0;
        if (minutes <= 30) return 2;            // Ruby
        if (minutes <= 60) return 5;            // Emerald
        if (minutes <= 90) return 7;            // Amethyst
        if (minutes <= 120) return 10;          // Moonstone
        if (minutes <= 150) return 15;          // Aquamarine
        return 20;                               // Amber
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        isPublicFocusRun = false;
        isRunning = false;
        notificationHandler.removeCallbacksAndMessages(null);
        durationHandler.removeCallbacksAndMessages(null);
    }
}



