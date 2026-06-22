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
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.gxdevs.mindmint.Activities.FocusMode;
import com.gxdevs.mindmint.MindMintApp;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.TaskManager;
import com.gxdevs.mindmint.Widgets.FocusTimerWidgetProvider;
import com.gxdevs.mindmint.Widgets.PomodoroTimerWidgetProvider;
import com.gxdevs.mindmint.db.MindMintRoomDatabase;
import com.gxdevs.mindmint.db.dao.FocusDao;
import com.gxdevs.mindmint.db.entities.FocusDailyStatEntity;
import com.gxdevs.mindmint.db.entities.FocusSessionEntity;
import com.gxdevs.mindmint.db.entities.FocusStateEntity;

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
    /** Separate accumulator for total time spent in Lock In sessions (milliseconds). */
    public static final String PREF_LOCK_IN_TOTAL_MS = "pref_lock_in_total_ms";
    public static final String ACTION_START_FOREGROUND_SERVICE = "com.gxdevs.mindmint.Services.action.START_FOREGROUND";
    public static final String ACTION_STOP_TIMER = "com.gxdevs.mindmint.Services.action.STOP_TIMER";
    public static final String ACTION_PAUSE_TIMER = "com.gxdevs.mindmint.Services.action.PAUSE_TIMER";
    public static final String ACTION_RESUME_TIMER = "com.gxdevs.mindmint.Services.action.RESUME_TIMER";

    // Persistent state for robust background handling
    private static final String STATE_PREFS = "FOCUS_TIMER_STATE";
    // Legacy keys used for migration only
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_END_ELAPSED = "end_elapsed";
    private static final String KEY_DURATION = "duration";

    private final IBinder binder = new TimerBinder();

    // Core Timer State
    private long wallStartTimeMillis = 0L; // Wall clock start time for history
    private long sessionStartRealtime = 0L; // elapsedRealtime() for duration logic
    private long currentSegmentStartMillis = 0L; // Start of the current Focus or Break
    private long accumulatedFocusTime = 0L; // Banked time from previous focus segments
    private long currentDurationInMillis = Long.MAX_VALUE; // Total Goal

    public boolean isRunning = false;
    public boolean isBreak = false;
    public boolean isPaused = false;

    // Pause State Tracking
    private long breakStartTime = 0L;
    private long idleTimeoutStart = 0L;
    private float crystalRevealFraction = 0f;
    private static final long IDLE_TIMEOUT_MS = 20 * 60 * 1000L; // 20 minutes

    // Config
    private boolean isPomodoroEnabled = false;
    private long pomodoroFocusInterval = 25 * 60 * 1000L;
    private long pomodoroBreakInterval = 5 * 60 * 1000L;
    private String currentTopicName = null;
    // Task-linked focus extras
    public static final String EXTRA_TASK_ID = "taskId";
    public static final String EXTRA_IS_LOCKED_IN = "isLockedIn";
    public static final String EXTRA_IS_OPEN_ENDED = "isOpenEnded";
    public static final String ACTION_TASK_FOCUS_UPDATE = "com.gxdevs.mindmint.action.TASK_FOCUS_UPDATE";
    public static final String PREF_IS_LOCKED_IN = "pref_focus_is_locked_in";
    public static final String PREF_LINKED_TASK_ID = "pref_focus_linked_task_id";
    public static final String PREF_IS_OPEN_ENDED = "pref_focus_is_open_ended";

    private String linkedTaskId = null;  // null if not task-linked
    private boolean isLockedIn = false;  // true = block ALL non-essentials
    private boolean isOpenEnded = false; // true = count-up mode (no auto-stop)

    public static boolean isPublicFocusRun = false;
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private Handler durationHandler;
    private boolean completedNaturally = false;
    private int lastCompletedDurationMinutes = 0;
    private AlarmManager alarmManager;
    private FocusDao focusDao;

    private void startForegroundWithType(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FocusService.NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FocusService.NOTIFICATION_ID, notification);
        }
    }

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
        focusDao = MindMintRoomDatabase.getInstance(this).focusDao();

        // One-time migration from SharedPreferences to Room
        migrateFocusPrefsIfNeeded();

        // Restore running timer if the process was killed/recreated (from Room)
        FocusStateEntity existing = focusDao.getState();
        if (existing != null && existing.active) {
            long now = SystemClock.elapsedRealtime();

            // Reload State
            this.currentDurationInMillis = existing.duration;
            this.wallStartTimeMillis = existing.start_time;
            // Since we don't persist sessionStartRealtime (it's volatile),
            // we reconstruct duration logic relative to currentSegmentStartMillis
            this.currentSegmentStartMillis = existing.current_segment_start;
            this.accumulatedFocusTime = existing.accumulated_focus;
            this.isBreak = existing.is_break;
            this.isPomodoroEnabled = existing.is_pomodoro;
            this.pomodoroFocusInterval = existing.pomodoro_focus_interval;
            this.pomodoroBreakInterval = existing.pomodoro_break_interval;
            this.currentTopicName = existing.topic_name;
            // Restore new pause state fields
            this.isPaused = existing.is_paused;
            this.breakStartTime = existing.break_start_time;
            this.idleTimeoutStart = existing.idle_timeout_start;
            this.crystalRevealFraction = existing.crystal_reveal_fraction;
            this.isRunning = true;
            isPublicFocusRun = true; // Restore public flag so AccessibilityService knows focus is active

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            this.linkedTaskId = prefs.getString(PREF_LINKED_TASK_ID, null);
            this.isLockedIn = prefs.getBoolean(PREF_IS_LOCKED_IN, false);
            this.isOpenEnded = prefs.getBoolean(PREF_IS_OPEN_ENDED, false);

            boolean isInfinite = currentDurationInMillis == Long.MAX_VALUE || isOpenEnded;

            // Handle paused states
            if (isPaused && isBreak) {
                // BREAK_PAUSED state - check if break ended while killed
                long breakElapsed = now - breakStartTime;
                if (breakElapsed >= pomodoroBreakInterval) {
                    // Break ended while killed - transition
                    transitionPomodoroState();
                } else {
                    // Still in break - schedule remaining break time
                    long remainingBreak = pomodoroBreakInterval - breakElapsed;
                    durationHandler.postDelayed(() -> {
                        if (isRunning && isPaused && isBreak) {
                            transitionPomodoroState();
                        }
                    }, remainingBreak);
                }
            } else if (isPaused) {
                // WAITING_USER state - check if idle timeout passed while killed
                long idleElapsed = now - idleTimeoutStart;
                if (idleElapsed >= IDLE_TIMEOUT_MS) {
                    // Idle timeout passed - auto-kill
                    autoKillSession();
                    return;
                } else {
                    // Still waiting for user - schedule remaining idle timeout
                    long remainingIdle = IDLE_TIMEOUT_MS - idleElapsed;
                    durationHandler.postDelayed(() -> {
                        if (isRunning && isPaused && !isBreak) {
                            autoKillSession();
                        }
                    }, remainingIdle);
                }
            } else {
                // FOCUS_RUNNING state - check progress and schedule events
                long timeInSegment = now - currentSegmentStartMillis;
                long totalFocusIfStillRunning = accumulatedFocusTime + timeInSegment;

                // Check if we exceeded total duration while killed
                if (!isInfinite && totalFocusIfStillRunning >= currentDurationInMillis) {
                    stopTimer();
                    return;
                }

                // Check Pomodoro segment transition
                if (isPomodoroEnabled && timeInSegment >= pomodoroFocusInterval) {
                    transitionPomodoroState();
                } else {
                    scheduleNextEvent();
                }
            }

            startForegroundWithType(createNotification(getElapsedMillis()));
            notificationHandler.removeCallbacks(updateNotificationTask);
            notificationHandler.post(updateNotificationTask);
            updateWidgets();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received action: " + (intent != null ? intent.getAction() : "null intent"));
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_FOREGROUND_SERVICE:
                    currentDurationInMillis = intent.getLongExtra("durationInMillis", Long.MAX_VALUE);

                    // Config Extras
                    isPomodoroEnabled = intent.getBooleanExtra("isPomodoroEnabled", false);
                    pomodoroFocusInterval = intent.getLongExtra("pomodoroFocusInterval", 25 * 60 * 1000L);
                    pomodoroBreakInterval = intent.getLongExtra("pomodoroBreakInterval", 5 * 60 * 1000L);
                    currentTopicName = intent.getStringExtra("topicName");

                    // Task-linked & Locked-In extras
                    linkedTaskId = intent.getStringExtra(EXTRA_TASK_ID);
                    boolean alwaysLockIn = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_always_lock_in", false);
                    isLockedIn = intent.getBooleanExtra(EXTRA_IS_LOCKED_IN, false) || alwaysLockIn;
                    isOpenEnded = intent.getBooleanExtra(EXTRA_IS_OPEN_ENDED, false);

                    // If open-ended, cap duration to 12 hours (auto-stop)
                    if (isOpenEnded) {
                        currentDurationInMillis = 12 * 60 * 60 * 1000L;
                    }

                    // Persist state for service recreation
                    PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean(PREF_IS_LOCKED_IN, isLockedIn)
                            .putBoolean(PREF_IS_OPEN_ENDED, isOpenEnded)
                            .putString(PREF_LINKED_TASK_ID, linkedTaskId)
                            .apply();

                    // If task is linked, mark it IN_PROGRESS
                    if (linkedTaskId != null) {
                        updateLinkedTaskStatus(linkedTaskId, "IN_PROGRESS", 0);
                    }

                    Log.d(TAG, "ACTION_START_FOREGROUND_SERVICE: duration = " + currentDurationInMillis +
                            ", pomodoro=" + isPomodoroEnabled + ", topic=" + currentTopicName);

                    if (!isRunning) {
                        startTimer(currentDurationInMillis);
                    } else {
                        // Update params if needed? Usually we don't change config mid-run
                        startForegroundWithType(createNotification(getElapsedMillis()));
                        notificationHandler.post(updateNotificationTask);
                    }
                    break;
                case ACTION_STOP_TIMER:
                    Log.d(TAG, "ACTION_STOP_TIMER received");
                    if (isLockedIn) {
                        Log.w(TAG, "Ignoring ACTION_STOP_TIMER because Locked-In Mode is active.");
                        break;
                    }
                    stopTimer();
                    return START_NOT_STICKY;
                case ACTION_PAUSE_TIMER:
                    Log.d(TAG, "ACTION_PAUSE_TIMER received");
                    if (isLockedIn) {
                        Log.w(TAG, "Ignoring ACTION_PAUSE_TIMER because Locked-In Mode is active.");
                        break;
                    }
                    pauseTimer();
                    break;
                case ACTION_RESUME_TIMER:
                    Log.d(TAG, "ACTION_RESUME_TIMER received");
                    if (isLockedIn) {
                        Log.w(TAG, "Ignoring ACTION_RESUME_TIMER because Locked-In Mode is active.");
                        break;
                    }
                    resumeTimer();
                    break;
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
            wallStartTimeMillis = System.currentTimeMillis();
            sessionStartRealtime = SystemClock.elapsedRealtime();
            currentSegmentStartMillis = sessionStartRealtime;
            accumulatedFocusTime = 0L;
            isBreak = false;
            isRunning = true;
            isPublicFocusRun = true;
            completedNaturally = false;

            MindMintApp.subscribeToFocusTopic();

            Log.d(TAG, "Timer logic started. Duration: "
                    + (currentDurationInMillis == Long.MAX_VALUE ? "Infinite" : currentDurationInMillis + "ms"));

            startForegroundWithType(createNotification(0));
            notificationHandler.removeCallbacks(updateNotificationTask);
            notificationHandler.post(updateNotificationTask);

            durationHandler.removeCallbacksAndMessages(null);

            persistState();
            scheduleNextEvent(); // Schedules break or stop
            updateWidgets();
        } else {
            Log.d(TAG, "Timer is already running.");
        }
    }


    private void scheduleNextEvent() {
        if (!isRunning)
            return;

        long now = SystemClock.elapsedRealtime();
        long nextEventDelay = Long.MAX_VALUE;
        Runnable nextEventRunnable = null;

        // 1. Check Total Goal
        if (currentDurationInMillis != Long.MAX_VALUE) {
            long remainingTotal = currentDurationInMillis - accumulatedFocusTime;

            if (!isBreak) {
                long finishTime = currentSegmentStartMillis + remainingTotal;
                long delay = finishTime - now;

                if (delay <= 0) {
                    stopTimer();
                    return;
                }

                nextEventDelay = delay;
                nextEventRunnable = this::stopTimer;
            }
        }

        // 2. Check Pomodoro Transition
        if (isPomodoroEnabled) {
            long segmentDuration = isBreak ? pomodoroBreakInterval : pomodoroFocusInterval;
            long transitionTime = currentSegmentStartMillis + segmentDuration;
            long delay = transitionTime - now;

            // If a transition happens BEFORE the total goal, prioritize it.
            if (delay < nextEventDelay) {
                nextEventDelay = delay;
                nextEventRunnable = this::transitionPomodoroState;
            }
        }

        if (nextEventDelay != Long.MAX_VALUE) {
            // Cap delay to avoid issues, though alarms handle long delays
            durationHandler.removeCallbacksAndMessages(null); // Remove old
            Runnable finalNextEventRunnable = nextEventRunnable;
            durationHandler.postDelayed(() -> {
                if (isRunning)
                    finalNextEventRunnable.run();
            }, nextEventDelay);
        }

        // Schedule AlarmManager backup for total duration stop (survives Doze mode).
        // Handler.postDelayed() alone is unreliable for long timers (>30 min screen-off).
        if (currentDurationInMillis != Long.MAX_VALUE && !isBreak && !isPaused) {
            long remainingTotal = currentDurationInMillis - accumulatedFocusTime;
            long stopDelay = (currentSegmentStartMillis + remainingTotal) - SystemClock.elapsedRealtime();
            if (stopDelay > 0) {
                scheduleStopAlarm(stopDelay);
            }
        } else {
            cancelStopAlarm();
        }
    }

    public boolean isPomodoroEnabled() {
        return isPomodoroEnabled;
    }

    public boolean isBreak() {
        return isBreak;
    }

    private void transitionPomodoroState() {
        if (!isRunning)
            return;

        long now = SystemClock.elapsedRealtime();

        if (!isBreak) {
            // FOCUS -> BREAK (Enter PAUSE state)
            long segmentTime = now - currentSegmentStartMillis;
            accumulatedFocusTime += segmentTime;

            isBreak = true;
            isPaused = true;
            breakStartTime = now;
            currentSegmentStartMillis = now;

            Toast.makeText(this, "Break Time! Timer Paused.", Toast.LENGTH_SHORT).show();

            persistState();
            scheduleBreakEnd();
            updateWidgets();
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean autoStart = prefs.getBoolean("pref_auto_start_break", true);

            isBreak = false;
            if (autoStart) {
                isPaused = false;
                currentSegmentStartMillis = now;
                Toast.makeText(this, "Focus Resumed!", Toast.LENGTH_SHORT).show();
                persistState();
                scheduleNextEvent();
            } else {
                isPaused = true;
                idleTimeoutStart = now;
                Toast.makeText(this, "Break ended. Tap Resume to continue.", Toast.LENGTH_SHORT).show();
                persistState();
                scheduleIdleTimeout();
            }
            updateWidgets();
        }

        startForegroundWithType(createNotification(getElapsedMillis()));
    }

    private void scheduleBreakEnd() {
        durationHandler.removeCallbacksAndMessages(null);
        cancelStopAlarm(); // Cancel any pending stop alarm while on break
        durationHandler.postDelayed(() -> {
            if (isRunning && isPaused && isBreak) {
                transitionPomodoroState(); // Break -> Focus or WAITING_USER
            }
        }, pomodoroBreakInterval);
    }

    private void scheduleIdleTimeout() {
        durationHandler.removeCallbacksAndMessages(null);
        boolean isTestMode = pomodoroFocusInterval <= 60000L; // 1 minute or less = test mode
        long idleTimeout = isTestMode ? 30000L : IDLE_TIMEOUT_MS;
        durationHandler.postDelayed(() -> {
            if (isRunning && isPaused && !isBreak) {
                // Auto-kill after idle timeout in WAITING_USER state
                autoKillSession();
            }
        }, idleTimeout);
    }

    private void autoKillSession() {
        long elapsedFocusMillis = accumulatedFocusTime;
        saveDailyFocusStat(elapsedFocusMillis / 1000);

        // If this was a Lock In session, also credit its separate accumulator
        if (isLockedIn && elapsedFocusMillis > 0) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            long prev = sp.getLong(PREF_LOCK_IN_TOTAL_MS, 0L);
            sp.edit().putLong(PREF_LOCK_IN_TOTAL_MS, prev + elapsedFocusMillis).apply();
        }

        try {
            FocusSessionEntity session = new FocusSessionEntity();
            session.start_time_ms = wallStartTimeMillis;
            session.end_time_ms = System.currentTimeMillis();
            session.duration_ms = elapsedFocusMillis;
            session.date_str = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            session.topic_name = currentTopicName;
            focusDao.insertSession(session);
        } catch (Exception e) {
            Log.e(TAG, "Error saving focus session on auto-kill", e);
        }

        // Reset state
        isRunning = false;
        isPaused = false;
        isBreak = false;
        isPublicFocusRun = false;
        sessionStartRealtime = 0L;
        wallStartTimeMillis = 0L;

        // Clear persisted state (Room)
        FocusStateEntity state = new FocusStateEntity();
        state.id = 1;
        state.active = false;
        focusDao.insertOrReplaceState(state);

        // Clear ALL session prefs — prevents stale Lock In / task-link state
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(PREF_IS_LOCKED_IN, false)
                .putBoolean(PREF_IS_OPEN_ENDED, false)
                .remove(PREF_LINKED_TASK_ID)
                .apply();

        durationHandler.removeCallbacksAndMessages(null);
        notificationHandler.removeCallbacks(updateNotificationTask);
        cancelStopAlarm();

        Toast.makeText(this, "Session saved due to inactivity.", Toast.LENGTH_SHORT).show();

        // Notify UI that focus session has ended
        Intent ended = new Intent(com.gxdevs.mindmint.Common.IntentActions.getActionFocusSessionEnded(this));
        ended.setPackage(getPackageName());
        sendBroadcast(ended);

        stopForeground(true);
        stopSelf();
    }

    public void resumeTimer() {
        if (!isRunning || !isPaused)
            return;

        long now = SystemClock.elapsedRealtime();
        isPaused = false;
        isBreak = false;
        currentSegmentStartMillis = now;
        idleTimeoutStart = 0L;
        breakStartTime = 0L;

        persistState();
        scheduleNextEvent();
        startForegroundWithType(createNotification(getElapsedMillis()));
        updateWidgets();

        notificationHandler.removeCallbacks(updateNotificationTask);
        notificationHandler.post(updateNotificationTask);

        Toast.makeText(this, "Focus Resumed!", Toast.LENGTH_SHORT).show();
    }

    public void pauseTimer() {
        if (!isRunning || isPaused)
            return;

        long now = SystemClock.elapsedRealtime();

        // Accumulate focus time spent in current segment
        if (!isBreak) {
            accumulatedFocusTime += (now - currentSegmentStartMillis);
        }

        isPaused = true;
        idleTimeoutStart = now;
        currentSegmentStartMillis = now; // Reset segment start for duration math after resume

        // Cancel stop alarms and timers
        durationHandler.removeCallbacksAndMessages(null);
        cancelStopAlarm();

        persistState();
        scheduleIdleTimeout(); // Auto-kills session after 20 minutes of inactivity if idle
        startForegroundWithType(createNotification(getElapsedMillis()));
        updateWidgets();

        notificationHandler.removeCallbacks(updateNotificationTask);
        notificationHandler.post(updateNotificationTask);

        Toast.makeText(this, "Focus Paused!", Toast.LENGTH_SHORT).show();
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isOpenEnded() {
        return isOpenEnded;
    }

    public String getLinkedTaskId() {
        return linkedTaskId;
    }

    public long getBreakRemainingMillis() {
        if (!isBreak || !isPaused)
            return 0L;
        long now = SystemClock.elapsedRealtime();
        long breakElapsed = now - breakStartTime;
        long remaining = pomodoroBreakInterval - breakElapsed;
        return Math.max(0, remaining);
    }

    public float getCrystalRevealFraction() {
        return crystalRevealFraction;
    }

    public void setCrystalRevealFraction(float fraction) {
        this.crystalRevealFraction = fraction;
        // Also persist to DB for app kill recovery
        if (isRunning) {
            persistState();
        }
    }

    private void persistState() {
        FocusStateEntity state = new FocusStateEntity();
        state.id = 1;
        state.active = isRunning;
        state.duration = currentDurationInMillis;
        state.start_time = wallStartTimeMillis;
        state.current_segment_start = currentSegmentStartMillis;
        state.accumulated_focus = accumulatedFocusTime;
        state.is_break = isBreak;
        state.is_pomodoro = isPomodoroEnabled;
        state.pomodoro_focus_interval = pomodoroFocusInterval;
        state.pomodoro_break_interval = pomodoroBreakInterval;
        state.topic_name = currentTopicName;
        state.is_paused = isPaused;
        state.break_start_time = breakStartTime;
        state.idle_timeout_start = idleTimeoutStart;
        state.crystal_reveal_fraction = crystalRevealFraction;
        focusDao.insertOrReplaceState(state);
    }

    private void saveDailyFocusStat(long elapsedSeconds) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Long cur = focusDao.getSecondsForDate(today);
        long newVal = (cur != null ? cur : 0L) + elapsedSeconds;
        FocusDailyStatEntity e = new FocusDailyStatEntity();
        e.date = today;
        e.seconds = newVal;
        focusDao.insertOrReplaceDaily(e);
    }

    public void stopTimer() {
        if (isRunning) {
            // Capture session type BEFORE any state is cleared
            final boolean wasTaskSession = (linkedTaskId != null) || isLockedIn;
            final boolean wasLockedIn = isLockedIn;

            // Calculate FOCUS time only (not break time)
            long elapsedFocusMillis = accumulatedFocusTime;
            if (!isPaused && !isBreak) {
                // Add current segment if actively focusing
                elapsedFocusMillis += (SystemClock.elapsedRealtime() - currentSegmentStartMillis);
            }

            // Capture start time before reset
            final long actualStartTime = wallStartTimeMillis;

            boolean wasTimeLimited = currentDurationInMillis != Long.MAX_VALUE;
            boolean completedNaturally = wasTimeLimited && elapsedFocusMillis >= currentDurationInMillis;
            lastCompletedDurationMinutes = (int) (currentDurationInMillis / (60_000L));
            this.completedNaturally = completedNaturally;

            // Cap recorded focus time to the goal duration if timer completed naturally.
            // Doze may have delayed the stop callback, causing elapsed > goal.
            if (completedNaturally) {
                elapsedFocusMillis = currentDurationInMillis;
            }

            // Reset state
            isRunning = false;
            isPaused = false;
            isBreak = false;
            isPublicFocusRun = false;
            sessionStartRealtime = 0L;
            wallStartTimeMillis = 0L;
            durationHandler.removeCallbacksAndMessages(null);
            notificationHandler.removeCallbacks(updateNotificationTask);
            cancelStopAlarm();

            // Clear persisted state (Room)
            FocusStateEntity state = new FocusStateEntity();
            state.id = 1;
            state.active = false;
            state.end_elapsed = 0L;
            state.duration = 0L;
            focusDao.insertOrReplaceState(state);
            updateWidgets();

            Log.d(TAG, "Stopping timer. Focus time: " + elapsedFocusMillis + "ms");

            // Save daily focus stats (FOCUS TIME ONLY - no break time)
            long elapsedSeconds = elapsedFocusMillis / 1000;
            saveDailyFocusStat(elapsedSeconds);

            // If this was a Lock In session, also credit its dedicated accumulator
            if (wasLockedIn && elapsedFocusMillis > 0) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                long prev = sp.getLong(PREF_LOCK_IN_TOTAL_MS, 0L);
                sp.edit().putLong(PREF_LOCK_IN_TOTAL_MS, prev + elapsedFocusMillis).apply();
            }

            // --- Save elapsed time to linked task ---
            if (linkedTaskId != null) {
                updateLinkedTaskStatus(linkedTaskId, "IDLE", elapsedFocusMillis);
                // Broadcast so TasksFragment can refresh the list
                Intent taskUpdate = new Intent(ACTION_TASK_FOCUS_UPDATE);
                taskUpdate.putExtra(EXTRA_TASK_ID, linkedTaskId);
                sendBroadcast(taskUpdate);
                linkedTaskId = null;
            }

            // Clear prefs
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(PREF_IS_LOCKED_IN, false)
                    .putBoolean(PREF_IS_OPEN_ENDED, false)
                    .remove(PREF_LINKED_TASK_ID)
                    .apply();

            // Save detailed session history (FOCUS TIME ONLY)
            try {
                FocusSessionEntity session = new FocusSessionEntity();
                session.start_time_ms = actualStartTime;
                session.end_time_ms = System.currentTimeMillis();
                session.duration_ms = elapsedFocusMillis; // Focus time only
                session.date_str = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                session.topic_name = (currentTopicName != null && !currentTopicName.isEmpty()) ? currentTopicName
                        : "Untitled";

                focusDao.insertSession(session);
            } catch (Exception e) {
                Log.e(TAG, "Error saving focus session", e);
            }


            // Coin logic — only for standalone (non-task-linked, non-locked-in) sessions
            MintCrystals mintCrystals = new MintCrystals(this);
            int coinsAwarded = 0;

            if (!wasTaskSession) {
                if (completedNaturally) {
                    coinsAwarded = mapCoinsForMinutes(lastCompletedDurationMinutes);
                    if (coinsAwarded > 0) {
                        mintCrystals.addCoins(coinsAwarded);
                        Log.i(TAG, "MintCrystals: Awarded " + coinsAwarded + " coins for completing " + lastCompletedDurationMinutes + " minutes.");
                    }
                } else if (wasTimeLimited) {
                    // User manually stopped a timed standalone session early — deduct penalty
                    mintCrystals.subtractCoins(3);
                    Toast.makeText(this, "3 MintCrystals deducted for stopping early.", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "MintCrystals: Deducted 3 coins for stopping early.");
                }
            }

            Toast.makeText(this, "You have focused for " + formatTime(elapsedFocusMillis), Toast.LENGTH_SHORT).show();

            // Show a high-priority completion notification if finished naturally (only for non-task sessions)
            if (completedNaturally && !wasTaskSession)
                showCompletionNotification(lastCompletedDurationMinutes, coinsAwarded);

            stopForeground(true);
            stopSelf();

            // Notify UI that focus session ended (alarm-triggered stop won't call onResume)
            Intent ended = new Intent(com.gxdevs.mindmint.Common.IntentActions.getActionFocusSessionEnded(this));
            ended.setPackage(getPackageName());
            sendBroadcast(ended);
        }

    }

    /**
     * Update the linked task's focus status and accumulate spent time.
     * Called on-thread (allowMainThreadQueries is enabled for this db).
     */
    private void updateLinkedTaskStatus(String taskId, String newStatus, long additionalMs) {
        try {
            TaskManager tm = new TaskManager(this);
            Task task = tm.getTaskById(taskId);
            if (task != null) {
                task.setFocusStatus(newStatus);
                if (additionalMs > 0) {
                    task.setFocusTimeSpentMs(task.getFocusTimeSpentMs() + additionalMs);
                }
                tm.updateTask(task);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating linked task focus status", e);
        }
    }

    public boolean isLockedIn() {
        return isLockedIn;
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
        if (!isRunning)
            return 0L;

        long now = SystemClock.elapsedRealtime();
        if (isBreak) {
            // In break, elapsed focus time is just what we accumulated
            return accumulatedFocusTime;
        } else {
            // In focus, it's accumulated + current segment
            return accumulatedFocusTime + (now - currentSegmentStartMillis);
        }
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

                // Catch-up: stop if total duration exceeded (Doze may have delayed Handler)
                if (currentDurationInMillis != Long.MAX_VALUE && !isPaused && !isBreak
                        && elapsedMillis >= currentDurationInMillis) {
                    stopTimer();
                    return;
                }

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

    private void cancelStopAlarm() {
        if (alarmManager == null)
            return;
        try {
            alarmManager.cancel(getStopPendingIntent());
        } catch (Throwable t) {
            Log.w(TAG, "Failed to cancel stop alarm: " + t.getMessage());
        }
    }

    /**
     * Schedule an exact alarm as a Doze-proof backup to stop the timer.
     * Handler.postDelayed() is unreliable for long delays when the device enters
     * Doze mode, so we use AlarmManager to guarantee the timer stops on time.
     */
    private void scheduleStopAlarm(long delayMs) {
        if (alarmManager == null) return;
        cancelStopAlarm();
        long triggerAt = SystemClock.elapsedRealtime() + delayMs;
        PendingIntent pi = getStopPendingIntent();
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            Log.d(TAG, "Scheduled stop alarm in " + delayMs + "ms");
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot schedule exact alarm: " + e.getMessage());
        }
    }

    private Notification createNotification(long elapsedMillis) {
        Intent stopIntent = new Intent(this, FocusService.class);
        stopIntent.setAction(ACTION_STOP_TIMER);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1001, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent pauseIntent = new Intent(this, FocusService.class);
        pauseIntent.setAction(ACTION_PAUSE_TIMER);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1002, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent resumeIntent = new Intent(this, FocusService.class);
        resumeIntent.setAction(ACTION_RESUME_TIMER);
        PendingIntent resumePendingIntent = PendingIntent.getService(this, 1003, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent notificationIntent = new Intent(this, FocusMode.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "Focus Mode Active";
        String contentText = "";
        String subText = "Focus";
        boolean isInfinite = currentDurationInMillis == Long.MAX_VALUE || isOpenEnded;
        long remainingMillis = currentDurationInMillis - elapsedMillis;
        if (remainingMillis < 0) remainingMillis = 0;
        String timeStr = isInfinite ? formatTime(elapsedMillis) : formatTime(remainingMillis);

        if (isPaused && isBreak) {
            title = "Focus Paused - Take a Break";
            long breakRemaining = getBreakRemainingMillis();
            contentText = "Break: " + formatTime(breakRemaining);
            subText = "Break Paused";
        } else if (isPaused) {
            title = "Focus Paused";
            contentText = "Tap Resume to continue";
            subText = "Paused";
        } else if (isBreak) {
            title = "Take a Break";
            long breakRemaining = getBreakRemainingMillis();
            contentText = "Break: " + formatTime(breakRemaining);
            subText = "On Break";
        } else {
            if (isLockedIn) {
                title = "Locked In Mode Active";
                contentText = timeStr + " • Eliminating distractions";
                subText = "Locked In";
            } else if (linkedTaskId != null) {
                title = "Focusing on Task";
                subText = "Task Focus";
                if (currentTopicName != null && !currentTopicName.trim().isEmpty()) {
                    contentText = timeStr + " • Task: " + currentTopicName;
                } else {
                    contentText = timeStr + " • Focusing on Task";
                }
            } else {
                title = "Focus Mode Active";
                subText = "Deep Focus";
                if (currentTopicName != null && !currentTopicName.trim().isEmpty()) {
                    contentText = timeStr + " • Tag: " + currentTopicName;
                } else {
                    contentText = timeStr + " • Focus Active";
                }
            }
        }

        // --- Android 16 (API 36) Native Live Update Branch ---
        if (android.os.Build.VERSION.SDK_INT >= 37 || 
                (Build.VERSION.SDK_INT == 36 &&
                 android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.BAKLAVA) >= 1)) {
            Notification.Builder nativeBuilder = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setSubText(subText)
                    .setSmallIcon(R.drawable.brain)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);

            try {
                if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                    nativeBuilder.setRequestPromotedOngoing(true);
                }
            } catch (NoSuchMethodError e) {
                Log.w(TAG, "Notification.Builder.setRequestPromotedOngoing not available at runtime on this device", e);
            } catch (Exception e) {
                Log.w(TAG, "Failed calling setRequestPromotedOngoing", e);
            }

            if (!isPaused) {
                nativeBuilder.setUsesChronometer(true);
                if (isInfinite) {
                    long startTime = System.currentTimeMillis() - elapsedMillis;
                    nativeBuilder.setWhen(startTime);
                    nativeBuilder.setChronometerCountDown(false);
                } else {
                    long targetTime = System.currentTimeMillis() + remainingMillis;
                    nativeBuilder.setWhen(targetTime);
                    nativeBuilder.setChronometerCountDown(true);
                }
            } else {
                nativeBuilder.setUsesChronometer(false);
            }

            // Set progress style for Android 16
            Notification.ProgressStyle progressStyle = new Notification.ProgressStyle();
            if (currentDurationInMillis != Long.MAX_VALUE) {
                progressStyle.setProgress((int) elapsedMillis);
                progressStyle.addProgressSegment(new Notification.ProgressStyle.Segment((int) currentDurationInMillis));
            }
            nativeBuilder.setStyle(progressStyle);

            // Add native actions
            if (!isLockedIn) {
                Notification.Action stopAction = new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_minus), "Stop", stopPendingIntent).build();
                nativeBuilder.addAction(stopAction);

                if (isPaused) {
                    Notification.Action resumeAction = new Notification.Action.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_play), "Resume", resumePendingIntent).build();
                    nativeBuilder.addAction(resumeAction);
                } else {
                    Notification.Action pauseAction = new Notification.Action.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pause), "Pause", pausePendingIntent).build();
                    nativeBuilder.addAction(pauseAction);
                }
            } else {
                Notification.Action viewAction = new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.brain), "View", pendingIntent).build();
                nativeBuilder.addAction(viewAction);
            }

            return nativeBuilder.build();
        }

        // --- Fallback Branch (API < 36) ---
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(subText)
                .setSmallIcon(R.drawable.brain)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Configure Live Ticking Chronometer and Progress for OEM Now Bar / Live Alerts
        if (!isPaused) {
            builder.setUsesChronometer(true);
            if (isInfinite) {
                long startTime = System.currentTimeMillis() - elapsedMillis;
                builder.setWhen(startTime);
                builder.setChronometerCountDown(false);
            } else {
                long targetTime = System.currentTimeMillis() + remainingMillis;
                builder.setWhen(targetTime);
                builder.setChronometerCountDown(true);
                // Set progress so Samsung/OnePlus can draw progress circles
                builder.setProgress((int) currentDurationInMillis, (int) elapsedMillis, false);
            }
        } else {
            builder.setUsesChronometer(false);
            if (!isInfinite) {
                builder.setProgress((int) currentDurationInMillis, (int) elapsedMillis, false);
            }
        }

        // Action buttons
        // Action buttons
        if (!isLockedIn) {
            builder.addAction(R.drawable.ic_minus, "Stop", stopPendingIntent);
            if (isPaused) {
                builder.addAction(R.drawable.ic_play, "Resume", resumePendingIntent);
            } else {
                builder.addAction(R.drawable.ic_pause, "Pause", pausePendingIntent);
            }
        } else {
            builder.addAction(R.drawable.brain, "View", pendingIntent);
        }

        return builder.build();
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
                .setSmallIcon(R.drawable.brain)
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

    private void migrateFocusPrefsIfNeeded() {
        SharedPreferences migration = getSharedPreferences("focus_room_migration", MODE_PRIVATE);
        if (migration.getBoolean("done_v1", false))
            return;

        // Migrate running state
        SharedPreferences sp = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        boolean active = sp.getBoolean(KEY_ACTIVE, false);
        long endElapsed = sp.getLong(KEY_END_ELAPSED, 0L);
        long duration = sp.getLong(KEY_DURATION, 0L);
        FocusStateEntity state = new FocusStateEntity();
        state.id = 1;
        state.active = active;
        state.end_elapsed = endElapsed;
        state.duration = duration;
        focusDao.insertOrReplaceState(state);
        sp.edit().clear().apply();

        // Migrate daily stats
        SharedPreferences statsPrefs = getSharedPreferences("FOCUS_STATS_PREFS", MODE_PRIVATE);
        long sum = 0L;
        for (String key : statsPrefs.getAll().keySet()) {
            if (key.startsWith("focus_time_")) {
                Object val = statsPrefs.getAll().get(key);
                if (val instanceof Long) {
                    long seconds = (Long) val;
                    String date = key.substring("focus_time_".length());
                    FocusDailyStatEntity e = new FocusDailyStatEntity();
                    e.date = date;
                    e.seconds = seconds;
                    focusDao.insertOrReplaceDaily(e);
                    sum += seconds;
                }
            }
        }
        statsPrefs.edit().clear().apply();

        // Migrate total if higher than sum of daily
        SharedPreferences appPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long total = appPrefs.getLong(TOTAL_FOCUSED_TIME_KEY, 0);
        if (total > sum) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            Long cur = focusDao.getSecondsForDate(today);
            FocusDailyStatEntity e = new FocusDailyStatEntity();
            e.date = today;
            e.seconds = (cur != null ? cur : 0L) + (total - sum);
            focusDao.insertOrReplaceDaily(e);
        }
        appPrefs.edit().remove(TOTAL_FOCUSED_TIME_KEY).apply();

        migration.edit().putBoolean("done_v1", true).apply();
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Focus Timer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT);
        serviceChannel.setDescription("Channel for Focus Timer foreground service notification");

        NotificationChannel completionChannel = new NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Focus Completion Alerts",
                NotificationManager.IMPORTANCE_HIGH);
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
        if (minutes <= 0)
            return 0;
        if (minutes <= 30)
            return 2; // Ruby
        if (minutes <= 60)
            return 5; // Emerald
        if (minutes <= 90)
            return 7; // Amethyst
        if (minutes <= 120)
            return 10; // Moonstone
        if (minutes <= 150)
            return 15; // Aquamarine
        return 20; // Amber
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        isPublicFocusRun = false;
        isRunning = false;
        // Ensure foreground notification is removed if still present
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        notificationHandler.removeCallbacksAndMessages(null);
        durationHandler.removeCallbacksAndMessages(null);
    }

    private void updateWidgets() {
        String statusText;
        if (!isRunning) {
            statusText = getString(R.string.tap_to_start);
        } else if (isPaused) {
            statusText = isBreak ? getString(R.string.break_time) : getString(R.string.paused);
        } else {
            statusText = isBreak ? getString(R.string.break_time) : getString(R.string.running);
        }

        FocusTimerWidgetProvider.updateWidgetStatus(this, isRunning, statusText);
        PomodoroTimerWidgetProvider.updateWidgetStatus(this, isRunning, statusText);
    }
}
