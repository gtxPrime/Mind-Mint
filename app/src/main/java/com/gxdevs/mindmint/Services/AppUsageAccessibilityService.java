package com.gxdevs.mindmint.Services;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.gxdevs.mindmint.Activities.BlockingOverlayDisplayActivity;
import com.gxdevs.mindmint.Common.IntentActions;
import com.gxdevs.mindmint.Receivers.MidnightResetReceiver;
import com.gxdevs.mindmint.Receivers.ServiceResumeReceiver;
import com.gxdevs.mindmint.Utils.AdultDomainListManager;
import com.gxdevs.mindmint.Utils.BlockedSitesManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.Utils;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppUsageAccessibilityService extends AccessibilityService {

    private static final String TAG = "AppUsageAccessibilityService";
    private static final String PREFS_NAME = "AppData";

    // --- Constants for Custom Blocking (Focus Mode) ---
    public static final String PREF_CUSTOM_BLOCKED_APPS = "custom_blocked_apps_set";
    public static final String PREF_BLOCKING_POPUP_DURATION_SEC = "blocking_popup_duration_seconds"; // Used in loadConfiguration, but value not directly used later.
    public static final String BLOCKING_OVERLAY_ACTIVITY_CLASS_NAME = "com.gxdevs.mindmint.Activities.BlockingOverlayDisplayActivity";
    public static final String EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name";
    public static final String EXTRA_BLOCKED_PACKAGE_NAME = "extra_blocked_package_name";
    public static final String EXTRA_IS_REMINDER_ONLY = "extra_is_reminder_only";
    public static final String EXTRA_IS_FOCUS = "extra_is_focus";

    // --- Constants for View-Specific Time Features (Reminder & Block After View Time) ---
    public static final String PREF_REMIND_DOOM_SCROLLING_ENABLED = "pref_remind_doom_scrolling_enabled";
    public static final String PREF_REMIND_DOOM_SCROLLING_MINUTES = "pref_remind_doom_scrolling_minutes";
    public static final int DEFAULT_REMIND_DOOM_SCROLLING_MINUTES = 10;
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_ENABLED = "pref_block_after_wasted_time_enabled";
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_HOURS = "pref_block_after_wasted_time_hours";
    public static final float DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS = 1.0f;
    public static final String PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED = "pref_block_browsers_doom_enabled";
    public static final String PREF_BLOCK_ADULT_SITES_ENABLED = "pref_block_adult_sites_enabled";

    // --- Constants for Persistent View Tracking (used by Reminder & View Block) ---
    public static final String PREF_APP_VIEW_ACCUMULATED_TIME_MS_PREFIX = "app_view_accumulated_time_ms_"; // Referenced in resetDailyViewTracking
    public static final String PREF_LAST_VIEW_REMINDER_TIMESTAMP_PREFIX = "last_view_reminder_timestamp_"; // Referenced in resetDailyViewTracking
    public static final String PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX = "last_reminder_timestamp_app_tag_";
    public static final String PREF_REMINDER_VIEW_ACCUMULATED_TIME_CYCLE_MS_APP_TAG_PREFIX = "reminder_view_accumulated_time_cycle_ms_app_tag_";  // Referenced in resetDailyViewTracking

    // Action for the overlay to tell this service to close the current app
    public static final String ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY = "com.gxdevs.mindmint.action.PERFORM_GLOBAL_HOME_FROM_OVERLAY";
    public static final String ACTION_PERFORM_GLOBAL_BACK_FROM_OVERLAY = "com.gxdevs.mindmint.action.PERFORM_GLOBAL_BACK_FROM_OVERLAY";
    // Broadcast Action for internal state refresh at midnight
    public static final String ACTION_REFRESH_DAILY_STATE_INTERNAL = "com.gxdevs.mindmint.action.REFRESH_DAILY_STATE_INTERNAL";
    // Preference keys for service notification (not used in original)

    private SharedPreferences sharedPreferences;
    private SharedPreferences prefs;
    private long startTimeMillis = 0L;
    private String currentPackage = null;
    private BroadcastReceiver packageReceiver;
    private BroadcastReceiver overlayCommandReceiver;
    private BroadcastReceiver midnightStateRefreshReceiver;
    private BroadcastReceiver pauseServiceReceiver;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    // --- Variables for Back Pressing ---
    private boolean isYtHomeSwitchOn = false;
    private boolean isInstaHomeSwitchOn = false;
    private boolean isSnapHomeSwitchOn = false;

    // --- Variables for Custom Blocking (Focus Mode) ---
    private Set<String> customBlockedApps = new HashSet<>();

    // --- Variables for Reminders ---
    private boolean remindDoomScrollingEnabled;
    private int remindDoomScrollingMinutes;
    private final Map<String, Long> lastReminderTimestampForAppTag = new HashMap<>();
    private final Map<String, Long> appTagToTimeLeftForNextSessionMs = new HashMap<>();
    private final Map<String, Long> appTagToOriginalDelayMs = new HashMap<>();
    private final Map<String, Long> appTagToLastPostTimeMs = new HashMap<>();
    private final Map<String, ShowReminderRunnable> activeRunnables = new HashMap<>();
    private String currentAppTagForReminderViewTracking = null;
    private Handler reminderHandler;
    private final Map<String, Long> lastScrollEventTimestamp = new HashMap<>();

    // Cache for browser package checks
    private final Map<String, Boolean> browserCheckCache = new HashMap<>();

    // Obsolete, but kept for context during transition for saveReminderViewAccumulatedTime method.
    private final Map<String, Long> reminderViewSessionStartTimeMs = new HashMap<>();
    private final Map<String, Long> reminderViewAccumulatedTimeCurrentCycleMs = new HashMap<>();


    // --- Variables for Blocking (Wasted Time) & General App Usage ---
    private boolean blockAfterWastedTimeEnabled;
    private float blockAfterWastedTimeHours;
    private final Map<String, Long> appTotalWastedTimeToday = new HashMap<>(); // For global wasted time blocking & UI
    private boolean blockBrowsersDoomEnabled = false;
    private boolean blockAdultSitesEnabled = false;

    // Cache the most recently checked adult domain and its result
    private String lastAdultCheckedDomain = null;
    private boolean lastAdultCheckedBlocked = false;

    // --- Variables for old View Focus system (potentially needs review/cleanup) ---
    private final Map<String, Long> currentViewFocusSessionStartTimeMap = new HashMap<>(); // Used in loadConfiguration to clear
    private final Map<String, Long> appViewFocusAccumulatedTimeTodayMap = new HashMap<>(); // Used in loadConfiguration to clear
    private String currentViewIdPackage = null; // Used in onServiceConnected, onAccessibilityEvent, onInterrupt, onDestroy, processViewFocusEndIfActive

    // --- PeaceCoins Reminder Ignore Tracking ---
    private static final String PREF_REMINDER_IGNORED_COUNT_PREFIX = "reminder_ignored_count_";

    // --- Lifecycle Methods ---
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate: Initializing service");
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Ensure storage sets exist (do not seed defaults here)
        BlockedSitesManager.ensureSetsExist(getApplicationContext());

        isYtHomeSwitchOn = false;
        isInstaHomeSwitchOn = false;
        isSnapHomeSwitchOn = false;

        pauseServiceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long pauseDuration = intent.getLongExtra("pause_duration", 0);
                if (pauseDuration > 0) {
                    sharedPreferences.edit().putBoolean("isServicePaused", true).apply();
                    long resumeTime = System.currentTimeMillis() + pauseDuration;
                    sharedPreferences.edit().putLong("resumeTime", resumeTime).apply();
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    Intent resumeIntent = new Intent(context, ServiceResumeReceiver.class);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE);
                    try {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, resumeTime, pendingIntent);
                    } catch (SecurityException e) {
                        Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    sharedPreferences.edit().putBoolean("isServicePaused", false).apply();
                    sharedPreferences.edit().putLong("resumeTime", 0).apply();
                }
            }
        };
        IntentFilter pauseFilter = new IntentFilter(IntentActions.getActionPauseService(this));
        ContextCompat.registerReceiver(this, pauseServiceReceiver, pauseFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        loadConfiguration();
        loadTodaysWastedTime();
        loadLastReminderTimestampsForAppTags();

        reminderHandler = new Handler(Looper.getMainLooper());

        preferenceChangeListener = (sharedPrefs, key) -> {
            if (key != null) {
                switch (key) {
                    case PREF_CUSTOM_BLOCKED_APPS:
                    case PREF_BLOCKING_POPUP_DURATION_SEC:
                    case PREF_REMIND_DOOM_SCROLLING_ENABLED:
                    case PREF_REMIND_DOOM_SCROLLING_MINUTES:
                    case PREF_BLOCK_AFTER_WASTED_TIME_ENABLED:
                    case PREF_BLOCK_AFTER_WASTED_TIME_HOURS:
                    case PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED:
                    case PREF_BLOCK_ADULT_SITES_ENABLED:
                        Log.d(TAG, "Configuration changed for key: " + key + ". Reloading.");
                        loadConfiguration();
                        appViewFocusAccumulatedTimeTodayMap.clear(); // Part of old view focus
                        loadLastReminderTimestampsForAppTags();
                        break;
                    // No foreground service preference handling in original
                }
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "BroadcastReceiver onReceive: Received package update broadcast from HomeActivity");
                if (intent != null) {
                    Bundle bundle = intent.getExtras();
                    if (bundle != null) {
                        isYtHomeSwitchOn = bundle.getBoolean("home_yt_switch_on", false);
                        isInstaHomeSwitchOn = bundle.getBoolean("home_insta_switch_on", false);
                        isSnapHomeSwitchOn = bundle.getBoolean("home_snap_switch_on", false);
                        Log.d(TAG, "Received Home switch states: YT_ON=" + isYtHomeSwitchOn +
                                ", INSTA_ON=" + isInstaHomeSwitchOn + ", SNAP_ON=" + isSnapHomeSwitchOn);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(IntentActions.getActionUpdatePackages(this));
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Service onCreate: BroadcastReceiver for package updates registered.");

        overlayCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY.equals(intent.getAction())) {
                    Log.i(TAG, "Received command from overlay: ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY. Performing GLOBAL_ACTION_HOME.");
                    performGlobalAction(GLOBAL_ACTION_HOME);
                } else if (intent != null && ACTION_PERFORM_GLOBAL_BACK_FROM_OVERLAY.equals(intent.getAction())) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
            }
        };
        IntentFilter overlayIntentFilter = new IntentFilter(ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY);
        ContextCompat.registerReceiver(this, overlayCommandReceiver, overlayIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Service onCreate: overlayCommandReceiver for overlay commands registered.");

        midnightStateRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_REFRESH_DAILY_STATE_INTERNAL.equals(intent.getAction())) {
                    Log.i(TAG, "Received ACTION_REFRESH_DAILY_STATE_INTERNAL. Refreshing daily data in running service instance.");
                    refreshDailyServiceState();
                }
            }
        };
        IntentFilter midnightRefreshFilter = new IntentFilter(ACTION_REFRESH_DAILY_STATE_INTERNAL);
        ContextCompat.registerReceiver(this, midnightStateRefreshReceiver, midnightRefreshFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Service onCreate: midnightStateRefreshReceiver for internal state refresh registered.");

        scheduleMidnightReset();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Service onServiceConnected: System connected. Determining initial foreground app.");
        // No foreground notification
        loadLastReminderTimestampsForAppTags();

        if (appTagToTimeLeftForNextSessionMs.isEmpty()) {
            long defaultUserReminderIntervalMs = (long) sharedPreferences.getInt(PREF_REMIND_DOOM_SCROLLING_MINUTES, DEFAULT_REMIND_DOOM_SCROLLING_MINUTES) * 60 * 1000;
            for (String pkgAppTag : Utils.ALL_PACKAGES.values()) {
                if (pkgAppTag != null && !appTagToTimeLeftForNextSessionMs.containsKey(pkgAppTag)) {
                    appTagToTimeLeftForNextSessionMs.put(pkgAppTag, defaultUserReminderIntervalMs);
                }
            }
            Log.d(TAG, "onServiceConnected: Initialized appTagToTimeLeftForNextSessionMs for " + appTagToTimeLeftForNextSessionMs.size() + " potential app tags.");
        }

        String foregroundPackageName = getForegroundPackageName();
        if (foregroundPackageName != null && Utils.ALL_PACKAGES.containsKey(foregroundPackageName)) {
            if (!foregroundPackageName.equals(this.currentPackage) || this.startTimeMillis == 0L) {
                Log.d(TAG, "onServiceConnected: Foreground app '" + foregroundPackageName + "' is tracked. Updating/starting timer for general usage.");
                this.currentPackage = foregroundPackageName;
                this.startTimeMillis = System.currentTimeMillis();
                if (blockAfterWastedTimeEnabled && !appTotalWastedTimeToday.containsKey(this.currentPackage)) {
                    appTotalWastedTimeToday.put(this.currentPackage, (long) sharedPreferences.getInt(this.currentPackage + "_time", 0));
                }
            } else {
                Log.d(TAG, "onServiceConnected: Already tracking general usage for '" + this.currentPackage + "'.");
            }
        } else {
            if (this.currentPackage != null || this.startTimeMillis != 0L) {
                Log.d(TAG, "onServiceConnected: Foreground app ('" + foregroundPackageName + "') is not tracked or unknown. Clearing previous general usage tracking.");
                this.currentPackage = null;
                this.startTimeMillis = 0L;
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted. Ending any active sessions.");
        processViewFocusEndIfActive(currentViewIdPackage); // Old view focus system
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy: Unregistering receivers and ending sessions.");
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
        }
        if (overlayCommandReceiver != null) {
            unregisterReceiver(overlayCommandReceiver);
        }
        if (midnightStateRefreshReceiver != null) {
            unregisterReceiver(midnightStateRefreshReceiver);
        }
        if (pauseServiceReceiver != null) {
            unregisterReceiver(pauseServiceReceiver);
        }
        if (sharedPreferences != null && preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        if (currentPackage != null && Utils.ALL_PACKAGES.containsKey(currentPackage) && startTimeMillis != 0L) {
            long endTimeMillis = System.currentTimeMillis();
            int timeSpent = (int) Math.max(0L, (endTimeMillis - startTimeMillis) / 1000L);
            updateAppTimeSpent(currentPackage, timeSpent);
        }
        clearCurrentPackageTracking();
        processViewFocusEndIfActive(currentViewIdPackage); // Old view focus system
        Log.d(TAG, "Service destroyed.");

        // No foreground notification to stop
    }

    // --- Core Logic Entry Point ---
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (sharedPreferences.getBoolean("isServicePaused", false)) {
            return;
        }
        String eventPackageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
        int eventType = event.getEventType();

        handleScrollCounting(event);

        if (FocusService.isPublicFocusRun && customBlockedApps != null && customBlockedApps.contains(eventPackageName)) {
            Log.i(TAG, "BLOCKING (Focus Mode via FocusService.isPublicFocusRun) for: " + eventPackageName);
            Intent intent = new Intent(this, BlockingOverlayDisplayActivity.class);
            intent.putExtra(EXTRA_IS_FOCUS, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            resetUsageAndTimersForPackage(eventPackageName); // Resets general usage and specific timers for the package
            return;
        }
        if (eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (remindDoomScrollingEnabled) {
                String activeAppTagNow = null;
                String activeReminderViewIdNow = null;
                boolean isTrackedViewVisibleNow = false;

                if (eventPackageName != null) {
                    String appTagForEvent = getAppTagFromAllPackages(eventPackageName);
                    if (appTagForEvent != null) {
                        String reminderViewId = getReminderViewIdForAppTag(appTagForEvent);
                        if (reminderViewId != null) {
                            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                            if (isReminderViewVisible(rootNode, eventPackageName, reminderViewId)) {
                                activeAppTagNow = appTagForEvent;
                                activeReminderViewIdNow = reminderViewId;
                                isTrackedViewVisibleNow = true;
                            }
                            if (rootNode != null) rootNode.recycle();
                        }
                    }
                }

                String previouslyTrackedAppTag = currentAppTagForReminderViewTracking;

                if (isTrackedViewVisibleNow) {
                    if (!activeAppTagNow.equals(previouslyTrackedAppTag)) {
                        if (previouslyTrackedAppTag != null) {
                            Log.i(TAG, "onAccessibilityEvent: Reminder Flow - Switching. Attempting to PAUSE: " + previouslyTrackedAppTag);
                            endReminderViewSession(previouslyTrackedAppTag);
                        }
                        Log.i(TAG, "onAccessibilityEvent: Reminder Flow - STARTING/RESUMING: " + activeAppTagNow + " on view " + activeReminderViewIdNow);
                        startReminderViewSession(activeAppTagNow, activeReminderViewIdNow);
                    } else {
                        if (!activeRunnables.containsKey(activeAppTagNow)) {
                            Log.i(TAG, "onAccessibilityEvent: ReminderView Session - Still on " + activeAppTagNow + " but NO runnable active. Attempting to start/resume session.");
                            startReminderViewSession(activeAppTagNow, activeReminderViewIdNow);
                        } else {
                            Log.v(TAG, "onAccessibilityEvent: ReminderView Session - Still on " + activeAppTagNow + ". Timer already running. No action needed for scroll/minor event.");
                        }
                    }
                } else {
                    if (previouslyTrackedAppTag != null) {
                        Log.i(TAG, "onAccessibilityEvent: Reminder Flow - View for " + previouslyTrackedAppTag + " no longer visible. Attempting to PAUSE. Event Pkg: " + eventPackageName);
                        endReminderViewSession(previouslyTrackedAppTag);
                    }
                }
            }
            if (eventPackageName == null) {
                Log.w(TAG, "onAccessibilityEvent: eventPackageName is null. Current tracked app for reminder: " + currentAppTagForReminderViewTracking);
                if (currentPackage != null && Utils.ALL_PACKAGES.containsKey(currentPackage) && startTimeMillis != 0L) {
                    long endTimeMillis = System.currentTimeMillis();
                    int timeSpent = (int) Math.max(0L, (endTimeMillis - startTimeMillis) / 1000L);
                    updateAppTimeSpent(currentPackage, timeSpent);
                }
                if (currentAppTagForReminderViewTracking != null) {
                    Log.i(TAG, "onAccessibilityEvent: eventPackageName is null, pausing active reminder for " + currentAppTagForReminderViewTracking);
                    endReminderViewSession(currentAppTagForReminderViewTracking);
                }
                clearCurrentPackageTracking();
                processViewFocusEndIfActive(currentViewIdPackage); // Old view focus system
                return;
            }
            handleBlockers(eventPackageName, event);
        }

        handleBackPress(eventPackageName, event); // Handles back press modification

        // --- Browser blocking (user list) and adult sites block ---
        if (blockBrowsersDoomEnabled || blockAdultSitesEnabled) {
            Log.d(TAG, "Browser dispatch: pkg=" + eventPackageName +
                    ", browsersOn=" + blockBrowsersDoomEnabled +
                    ", adultOn=" + blockAdultSitesEnabled +
                    ", evt=" + AccessibilityEvent.eventTypeToString(event.getEventType()));
            tryBlockBrowser(eventPackageName);
        }

        // --- General App Usage Time Tracking & Window State Changes ---
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { // NEW CONDITION (Scroll is handled above)
            if (!eventPackageName.equals(currentPackage)) {
                if (currentPackage != null && Utils.ALL_PACKAGES.containsKey(currentPackage) && startTimeMillis != 0L) {
                    long endTimeMillis = System.currentTimeMillis();
                    int timeSpent = (int) Math.max(0L, (endTimeMillis - startTimeMillis) / 1000L);
                    updateAppTimeSpent(currentPackage, timeSpent);
                }
                processViewFocusEndIfActive(currentPackage); // Old view focus system

                if (Utils.ALL_PACKAGES.containsKey(eventPackageName)) {
                    currentPackage = eventPackageName;
                    startTimeMillis = System.currentTimeMillis();
                    if (blockAfterWastedTimeEnabled && !appTotalWastedTimeToday.containsKey(currentPackage)) {
                        appTotalWastedTimeToday.put(currentPackage, (long) sharedPreferences.getInt(currentPackage + "_time", 0));
                    }
                } else {
                    clearCurrentPackageTracking();
                }
            } else { // Same package
                if (Utils.ALL_PACKAGES.containsKey(currentPackage)) {
                    if (startTimeMillis == 0L) { // If it was cleared, restart
                        startTimeMillis = System.currentTimeMillis();
                    }
                } else { // Should not happen if currentPackage is set, but good for safety
                    clearCurrentPackageTracking();
                    processViewFocusEndIfActive(currentViewIdPackage); // Old view focus system
                }
            }
        }

        // --- Global Wasted Time Blocking ---
        if (blockAfterWastedTimeEnabled) {
            long currentGlobalDailyWastedTimeMs = 0;
            for (long timeInSeconds : appTotalWastedTimeToday.values()) {
                currentGlobalDailyWastedTimeMs += (timeInSeconds * 1000);
            }
            long globalBlockThresholdMs = (long) (blockAfterWastedTimeHours * 3600 * 1000);
            boolean globalTimeLimitExceededToday = currentGlobalDailyWastedTimeMs >= globalBlockThresholdMs;

            Log.d(TAG, "GLOBAL BLOCK CHECK: GlobalWastedTime: " + currentGlobalDailyWastedTimeMs + "ms, GlobalThreshold: " + globalBlockThresholdMs + "ms, LimitExceededToday: " + globalTimeLimitExceededToday);

            if (globalTimeLimitExceededToday) {
                String appTagForGlobalBlock = getAppTagFromAllPackages(eventPackageName);
                if (appTagForGlobalBlock != null) {
                    String viewIdToLookFor = getReminderViewIdForAppTag(appTagForGlobalBlock);

                    if (viewIdToLookFor != null) {
                        AccessibilityNodeInfo rootNodeForGlobalBlockCheck = getRootInActiveWindow();
                        if (rootNodeForGlobalBlockCheck != null) {
                            List<AccessibilityNodeInfo> nodes = rootNodeForGlobalBlockCheck.findAccessibilityNodeInfosByViewId(eventPackageName + ":id/" + viewIdToLookFor);
                            boolean specificViewPresent = nodes != null && !nodes.isEmpty();
                            if (nodes != null) {
                                for (AccessibilityNodeInfo node : nodes) node.recycle();
                            }
                            rootNodeForGlobalBlockCheck.recycle();

                            if (specificViewPresent) {
                                Log.i(TAG, "GLOBAL APP BLOCK (Total Wasted Time Limit Exceeded & View ID " + viewIdToLookFor + " Accessed): Blocking app " + eventPackageName);
                                launchOverlay(eventPackageName); // Generic overlay, might need specific "reason" if UI differs
                                resetUsageAndTimersForPackage(eventPackageName);
                                return; // Important to return after blocking
                            }
                        }
                    }
                }
            }
        }

        if (currentAppTagForReminderViewTracking != null && !eventPackageName.equals(getPackageForAppTag(currentAppTagForReminderViewTracking))) {
            String appTagToEnd = currentAppTagForReminderViewTracking;
            if (reminderViewSessionStartTimeMs.containsKey(appTagToEnd)) {
                Log.d(TAG, "ReminderViewTracking (Old System): App changed. Ending session for previously tracked appTag: " + appTagToEnd);
                long sessionStartTime = reminderViewSessionStartTimeMs.remove(appTagToEnd);
                long sessionDuration = System.currentTimeMillis() - sessionStartTime;
                if (sessionDuration > 0) {
                    long previouslyAccumulated = reminderViewAccumulatedTimeCurrentCycleMs.getOrDefault(appTagToEnd, 0L);
                    long newAccumulatedTime = previouslyAccumulated + sessionDuration;
                    Log.d(TAG, "ReminderViewTracking (Old System): Session ENDED (app changed) for appTag: " + appTagToEnd + ". Duration: " + (sessionDuration / 1000) + "s. New Accumulated: " + (newAccumulatedTime / 1000) + "s.");
                }
            }
        }
    }

    // ===== SCROLL COUNTING =====
    private void handleScrollCounting(AccessibilityEvent event) {
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
        if (packageName == null) {
            return;
        }

        String appTag = getAppTagFromAllPackages(packageName);
        if (appTag == null) {
            return;
        }

        String viewId;
        int requiredEventType;
        long debounceMs;

        switch (appTag) {
            case "insta":
                viewId = Utils.instaViewId;
                requiredEventType = AccessibilityEvent.TYPE_VIEW_SCROLLED;
                debounceMs = 1000; // 1 sec
                break;
            case "yt":
                viewId = Utils.YtViewId;
                requiredEventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                debounceMs = 2500; // 2.5 sec
                break;
            case "snap":
                viewId = Utils.snapViewId;
                requiredEventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                debounceMs = 2500; // 2.5 sec
                break;
            default:
                return;
        }

        if (event.getEventType() != requiredEventType) {
            return;
        }

        Log.d(TAG, "ScrollCounting: Relevant event type " + AccessibilityEvent.eventTypeToString(event.getEventType()) + " for " + appTag + ". Checking view visibility.");

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (isReminderViewVisible(rootNode, packageName, viewId)) {
            rootNode.recycle();

            long currentTime = System.currentTimeMillis();
            long lastTime = lastScrollEventTimestamp.getOrDefault(appTag, 0L);
            if (currentTime - lastTime > debounceMs) {
                lastScrollEventTimestamp.put(appTag, currentTime);
                incrementScrollCount(packageName);
            } else {
                Log.d(TAG, "ScrollCounting: Scroll event for " + appTag + " debounced. Time since last: " + (currentTime - lastTime) + "ms.");
            }
        } else {
            Log.d(TAG, "ScrollCounting: View '" + viewId + "' not visible for " + appTag + ". No scroll counted.");
            if (rootNode != null) rootNode.recycle();
        }
    }

    private void incrementScrollCount(String packageName) {
        if (packageName == null) return;

        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        }

        long currentScrolls = sharedPreferences.getLong(packageName + "_scrolls", 0L);
        long newTotalScrolls = currentScrolls + 1;
        sharedPreferences.edit().putLong(packageName + "_scrolls", newTotalScrolls).apply();
        Log.i(TAG, "Scroll detected for " + packageName + ". New total: " + newTotalScrolls);

        Intent intent = new Intent(IntentActions.getActionTimeUpdated(this));
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    // ===== BACK PRESS HANDLING =====
    private void handleBackPress(String currentEventPackageName, AccessibilityEvent event) {
        AccessibilityNodeInfo eventSource = event.getSource();
        boolean isEventSourceNull = eventSource == null;
        if (!isEventSourceNull) {
            eventSource.recycle();
        }
        if (isEventSourceNull) {
            return;
        }

        String appTag = getAppTagFromAllPackages(currentEventPackageName);
        if (appTag == null) {
            return;
        }

        boolean homeSwitchForAppIsOn;
        boolean modSwitchForAppIsOn;
        String viewIdForBackPress;

        switch (appTag) {
            case "yt":
                homeSwitchForAppIsOn = isYtHomeSwitchOn;
                modSwitchForAppIsOn = prefs.getBoolean("YtMod", false);
                viewIdForBackPress = Utils.YtViewId;
                break;
            case "insta":
                homeSwitchForAppIsOn = isInstaHomeSwitchOn;
                modSwitchForAppIsOn = prefs.getBoolean("InstaMod", false);
                viewIdForBackPress = Utils.instaViewId;
                break;
            case "snap":
                homeSwitchForAppIsOn = isSnapHomeSwitchOn;
                modSwitchForAppIsOn = prefs.getBoolean("SnapMod", false);
                viewIdForBackPress = Utils.snapViewId;
                break;
            default:
                return;
        }

        if (!homeSwitchForAppIsOn) {
            return;
        }

        // Home switch is ON, proceed with Mod switch logic
        Map<String, String> packagesToTarget = modSwitchForAppIsOn ? Utils.ALL_PACKAGES : Utils.ORIGINAL_PACKAGES;

        if (appTag.equals(packagesToTarget.get(currentEventPackageName))) {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                findAndBlockView(rootNode, viewIdForBackPress, currentEventPackageName);
                rootNode.recycle(); // Essential: recycle the rootNode after use
            }
        }
    }

    // ===== REMINDER FLOW (Doom Scrolling) =====
    private class ShowReminderRunnable implements Runnable {
        private final String appTag;

        ShowReminderRunnable(String appTag) {
            this.appTag = appTag;
        }

        @Override
        public void run() {
            activeRunnables.remove(appTag);

            if (!remindDoomScrollingEnabled) {
                resetTimerStateForApp(appTag);
                return;
            }

            String currentForegroundAppTag = getAppTagFromAllPackages(getForegroundPackageName());
            if (!appTag.equals(currentForegroundAppTag)) {
                long originalDelay = appTagToOriginalDelayMs.getOrDefault(appTag, 0L);
                long postTime = appTagToLastPostTimeMs.getOrDefault(appTag, System.currentTimeMillis());
                long elapsed = System.currentTimeMillis() - postTime;
                long timeLeft = Math.max(0, originalDelay - elapsed);
                appTagToTimeLeftForNextSessionMs.put(appTag, timeLeft);
                Log.i(TAG, "ShowReminderRunnable: Aborted for " + appTag + " as it's not foreground. Effective Time Left: " + (timeLeft / 1000) + "s. Will pause.");
                return;
            }

            // --- PeaceCoins logic: track ignored reminders and subtract coins after 3rd ---
            incrementReminderIgnoredCount(appTag);
            int ignoredCount = getReminderIgnoredCount(appTag);
            if (ignoredCount > 5) {
                try {
                    MintCrystals mintCrystals = new MintCrystals(getApplicationContext());
                    mintCrystals.subtractCoins(2);
                    Log.i(TAG, "PeaceCoins: Subtracted 2 coins for appTag " + appTag + " after " + ignoredCount + " ignored reminders today.");
                } catch (Exception e) {
                    Log.e(TAG, "PeaceCoins: Exception while subtracting coins for appTag " + appTag, e);
                }
            }
            // --- End PeaceCoins logic ---

            long userReminderIntervalMs = (long) remindDoomScrollingMinutes * 60 * 1000;
            long lastActualReminderShownTimeMs = lastReminderTimestampForAppTag.getOrDefault(appTag, 0L);

            if ((System.currentTimeMillis() - lastActualReminderShownTimeMs) < userReminderIntervalMs) {
                appTagToTimeLeftForNextSessionMs.put(appTag, userReminderIntervalMs);
                return;
            }

            String currentPackageName = getPackageForAppTag(appTag);

            if (currentPackageName != null) {
                Intent reminderIntent = getIntent(currentPackageName, appTag);
                try {
                    startActivity(reminderIntent);
                    saveLastReminderTimestampForAppTag(appTag, System.currentTimeMillis());
                    appTagToTimeLeftForNextSessionMs.put(appTag, userReminderIntervalMs);

                    if (appTag.equals(currentAppTagForReminderViewTracking)) {
                        currentAppTagForReminderViewTracking = null;
                    }
                    appTagToOriginalDelayMs.remove(appTag);
                    appTagToLastPostTimeMs.remove(appTag);

                } catch (Exception e) {
                    Log.e(TAG, "ShowReminderRunnable: EXCEPTION starting Reminder activity for " + appTag, e);
                }
            } else {
                appTagToTimeLeftForNextSessionMs.put(appTag, userReminderIntervalMs);
            }
        }
    }

    private void startReminderViewSession(String appTag, String reminderViewId) {
        if (appTag == null || !remindDoomScrollingEnabled) {
            return;
        }

        ShowReminderRunnable existingRunnable = activeRunnables.get(appTag);
        if (existingRunnable != null) {
            currentAppTagForReminderViewTracking = appTag;
            return;
        }

        long userReminderIntervalMs = (long) remindDoomScrollingMinutes * 60 * 1000;
        long timeToDelayMs = appTagToTimeLeftForNextSessionMs.getOrDefault(appTag, userReminderIntervalMs);

        if (timeToDelayMs <= 0) {
            timeToDelayMs = userReminderIntervalMs;
            appTagToTimeLeftForNextSessionMs.put(appTag, timeToDelayMs);
        }

        ShowReminderRunnable newRunnable = new ShowReminderRunnable(appTag);
        activeRunnables.put(appTag, newRunnable);
        reminderHandler.postDelayed(newRunnable, timeToDelayMs);
        appTagToOriginalDelayMs.put(appTag, timeToDelayMs);
        appTagToLastPostTimeMs.put(appTag, System.currentTimeMillis());
        currentAppTagForReminderViewTracking = appTag;
    }

    private void endReminderViewSession(String appTagToEnd) {
        if (appTagToEnd == null) return;
        ShowReminderRunnable activeRunnable = activeRunnables.remove(appTagToEnd);
        if (activeRunnable == null) {
            if (appTagToEnd.equals(currentAppTagForReminderViewTracking)) {
                currentAppTagForReminderViewTracking = null;
            }
            return;
        }

        reminderHandler.removeCallbacks(activeRunnable);

        long originalDelay = appTagToOriginalDelayMs.getOrDefault(appTagToEnd, 0L);
        long lastPostTime = appTagToLastPostTimeMs.getOrDefault(appTagToEnd, System.currentTimeMillis());
        long elapsedOnThisPost = System.currentTimeMillis() - lastPostTime;
        long timeLeftMs = Math.max(0, originalDelay - elapsedOnThisPost);

        appTagToTimeLeftForNextSessionMs.put(appTagToEnd, timeLeftMs);
        appTagToOriginalDelayMs.remove(appTagToEnd);
        appTagToLastPostTimeMs.remove(appTagToEnd);

        if (appTagToEnd.equals(currentAppTagForReminderViewTracking)) {
            currentAppTagForReminderViewTracking = null;
        }
    }

    private void resetTimerStateForApp(String appTag) {
        if (appTag == null) return;

        ShowReminderRunnable activeRunnable = activeRunnables.remove(appTag);
        if (activeRunnable != null) {
            reminderHandler.removeCallbacks(activeRunnable);
        }

        long userReminderIntervalMs = (long) sharedPreferences.getInt(PREF_REMIND_DOOM_SCROLLING_MINUTES, DEFAULT_REMIND_DOOM_SCROLLING_MINUTES) * 60 * 1000;
        appTagToTimeLeftForNextSessionMs.put(appTag, userReminderIntervalMs);
        appTagToOriginalDelayMs.remove(appTag);
        appTagToLastPostTimeMs.remove(appTag);

        if (appTag.equals(currentAppTagForReminderViewTracking)) {
            currentAppTagForReminderViewTracking = null;
        }
    }

    @NonNull
    private Intent getIntent(String currentEventPackageName, String appTag) { // Primarily used for Reminders
        Intent reminderIntent = new Intent(this, BlockingOverlayDisplayActivity.class);
        reminderIntent.putExtra(EXTRA_IS_REMINDER_ONLY, true);
        reminderIntent.putExtra(EXTRA_BLOCKED_APP_NAME, getAppNameForTag(appTag));
        reminderIntent.putExtra(EXTRA_BLOCKED_PACKAGE_NAME, currentEventPackageName);
        reminderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return reminderIntent;
    }

    private void saveLastReminderTimestampForAppTag(String appTag, long timestamp) {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (appTag == null || appTag.isEmpty()) {
            Log.w(TAG, "Attempted to save reminder timestamp for null or empty appTag.");
            return;
        }
        lastReminderTimestampForAppTag.put(appTag, timestamp);
        sharedPreferences.edit().putLong(PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX + appTag, timestamp).apply();
        Log.i(TAG, "Saved last reminder timestamp for appTag " + appTag + ": " + timestamp);
    }

    private void loadLastReminderTimestampsForAppTags() {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        lastReminderTimestampForAppTag.clear();
        Map<String, ?> allEntries = sharedPreferences.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith(PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX)) {
                try {
                    String appTag = entry.getKey().substring(PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX.length());
                    long timestamp = (Long) entry.getValue();
                    if (timestamp > 0L && !appTag.isEmpty()) {
                        lastReminderTimestampForAppTag.put(appTag, timestamp);
                    }
                } catch (ClassCastException e) {
                    Log.e(TAG, "Error casting preference for " + entry.getKey(), e);
                }
            }
        }
        Log.d(TAG, "Loaded lastReminderTimestampForAppTag for " + lastReminderTimestampForAppTag.size() + " app tags: " + lastReminderTimestampForAppTag.toString());
    }

    private String getReminderViewIdForAppTag(String appTag) {
        if (appTag == null) return null;
        switch (appTag) {
            case "yt":
                return Utils.YtViewId;
            case "insta":
                return Utils.instaViewId;
            case "snap":
                return Utils.snapViewId;
            default:
                return null;
        }
    }

    private boolean isReminderViewVisible(AccessibilityNodeInfo rootNode, String packageName, String viewId) {
        if (rootNode == null || packageName == null || viewId == null) {
            return false;
        }
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(packageName + ":id/" + viewId);
        boolean visible = false;
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (!visible && node != null && node.isVisibleToUser()) {
                    visible = true;
                }
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) node.recycle();
            }
        }
        return visible;
    }

    // ===== VIEW BLOCKERS (View ID-based) =====
    private void handleBlockers(String currentEventPackageName, AccessibilityEvent event) { // This handles "blockAllFeatures" view ID blocking
        boolean blockersGloballyEnabled = sharedPreferences.getBoolean("blockAllFeatures", false);
        AccessibilityNodeInfo src = event.getSource();
        boolean hasSource = src != null;
        if (src != null) src.recycle();
        if (!blockersGloballyEnabled || !hasSource) {
            return;
        }

        String appTag = getAppTagFromAllPackages(currentEventPackageName);
        String viewIdToBlock = appTag != null ? getReminderViewIdForAppTag(appTag) : null;

        if (viewIdToBlock != null) {
            Log.d(TAG, "Blocker: Checking for view ID " + viewIdToBlock + " in " + currentEventPackageName);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    findAndBlockView(root, viewIdToBlock, currentEventPackageName); // Uses helper
                } finally {
                    root.recycle();
                }
            }
        }
    }

    private void launchOverlay(String packageName) { // Used by Global Wasted Time Blocker
        Log.i(TAG, "Overlay Launch (Full Block): Pkg=" + packageName);
        Intent overlayIntent = new Intent();
        overlayIntent.setClassName(this, BLOCKING_OVERLAY_ACTIVITY_CLASS_NAME);
        String appName = getAppNameFromPackageManager(packageName);
        overlayIntent.putExtra(EXTRA_BLOCKED_APP_NAME, appName != null ? appName : packageName);
        overlayIntent.putExtra(EXTRA_BLOCKED_PACKAGE_NAME, packageName);
        overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            startActivity(overlayIntent);
        } catch (Exception e) {
            Log.e(TAG, "EXCEPTION starting OverlayActivity for " + packageName, e);
        }
    }

    private void loadConfiguration() {
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        }
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        }
        customBlockedApps = sharedPreferences.getStringSet(PREF_CUSTOM_BLOCKED_APPS, new HashSet<>());

        boolean oldRemindDoomScrollingEnabled = remindDoomScrollingEnabled;
        int oldRemindDoomScrollingMinutes = remindDoomScrollingMinutes;

        remindDoomScrollingEnabled = sharedPreferences.getBoolean(PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
        remindDoomScrollingMinutes = sharedPreferences.getInt(PREF_REMIND_DOOM_SCROLLING_MINUTES, DEFAULT_REMIND_DOOM_SCROLLING_MINUTES);
        blockAfterWastedTimeEnabled = sharedPreferences.getBoolean(PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
        blockAfterWastedTimeHours = sharedPreferences.getFloat(PREF_BLOCK_AFTER_WASTED_TIME_HOURS, DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS);
        blockBrowsersDoomEnabled = sharedPreferences.getBoolean(PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, false);
        blockAdultSitesEnabled = sharedPreferences.getBoolean(PREF_BLOCK_ADULT_SITES_ENABLED, false);

        long currentUserReminderIntervalMs = (long) remindDoomScrollingMinutes * 60 * 1000;
        boolean settingsChanged = oldRemindDoomScrollingEnabled != remindDoomScrollingEnabled || oldRemindDoomScrollingMinutes != remindDoomScrollingMinutes;

        if (settingsChanged || appTagToTimeLeftForNextSessionMs.isEmpty()) {
            appTagToTimeLeftForNextSessionMs.clear();
            appTagToOriginalDelayMs.clear();
            appTagToLastPostTimeMs.clear();
            for (ShowReminderRunnable activeRunnable : activeRunnables.values()) {
                reminderHandler.removeCallbacks(activeRunnable);
            }
            activeRunnables.clear();

            if (remindDoomScrollingEnabled) {
                for (String appTag : Utils.ALL_PACKAGES.values()) {
                    if (appTag != null) {
                        appTagToTimeLeftForNextSessionMs.put(appTag, currentUserReminderIntervalMs);
                    }
                }
            }
        }

        if (!remindDoomScrollingEnabled) {
            if (currentAppTagForReminderViewTracking != null) {
                resetTimerStateForApp(currentAppTagForReminderViewTracking);
            }
            appTagToTimeLeftForNextSessionMs.clear();
            appTagToOriginalDelayMs.clear();
            appTagToLastPostTimeMs.clear();
            for (ShowReminderRunnable activeRunnable : activeRunnables.values()) {
                reminderHandler.removeCallbacks(activeRunnable);
            }
            activeRunnables.clear();
            Log.i(TAG, "loadConfiguration: Reminders globally disabled. All reminder timer states cleared.");
        }

        if (!remindDoomScrollingEnabled && !blockAfterWastedTimeEnabled) { // This seems to be for the old view focus system cleanup
            currentViewFocusSessionStartTimeMap.clear();
            appViewFocusAccumulatedTimeTodayMap.clear();
            currentViewIdPackage = null;
        }
    }

    // ===== BROWSER / URL BLOCKING =====
    private void tryBlockBrowser(String packageName) {
        if (packageName == null) return;
        boolean isKnownBrowser = Utils.BROWSERS_PACKAGES.containsKey(packageName);
        if (!isKnownBrowser && !isBrowserPackage(packageName, this)) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        logNodeTree(rootNode, 0);
        if (rootNode == null) return;
        try {
            Pair<String, Boolean> result = tryGetUrlBarTextWithRetry(packageName, rootNode);
            String urlText = result.first;

            if (urlText == null || urlText.isEmpty())
                return;

            String lowerText = urlText.toLowerCase();
            String host = AdultDomainListManager.extractHostFromUrlText(lowerText);
            Log.d(TAG, "Browser check: pkg=" + packageName + ", urlText=" + urlText + ", host=" + host +
                    ", browsersOn=" + blockBrowsersDoomEnabled + ", adultOn=" + blockAdultSitesEnabled);
            Set<String> candidateTexts = new HashSet<>();
            candidateTexts.add(lowerText);
            Set<String> candidateHosts = new HashSet<>();
            if (host != null && host.contains(".")) candidateHosts.add(host);

            // 1) User-defined blocking (no doom fallback)
            if (blockBrowsersDoomEnabled) {
                if (isEdgeOrSamsung(packageName)) {
                    if (isBlockedByUserListsEdgeSamsung(candidateTexts, candidateHosts)) {
                        Log.i(TAG, "Blocker: Decision=USER_LIST_EDGE_SAMSUNG host-only for exacts");
                        launchOverlay(packageName);
                        resetUsageAndTimersForPackage(packageName);
                        return;
                    }
                } else {
                    if (isBlockedByUserLists(candidateTexts, candidateHosts)) {
                        Log.i(TAG, "Blocker: Decision=USER_LIST (domains/exacts)");
                        launchOverlay(packageName);
                        resetUsageAndTimersForPackage(packageName);
                        return;
                    }
                }
            }

            // 2) Adult sites blocking using URL text only
            if (blockAdultSitesEnabled && !candidateHosts.isEmpty()) {
                for (String h : candidateHosts) {
                    boolean shouldCheck = (lastAdultCheckedDomain == null) || !lastAdultCheckedDomain.equals(h);
                    boolean isBlocked = lastAdultCheckedBlocked;
                    if (shouldCheck) {
                        isBlocked = AdultDomainListManager.isAdultHost(this, h);
                        lastAdultCheckedDomain = h;
                        lastAdultCheckedBlocked = isBlocked;
                    }
                    if (isBlocked) {
                        Log.i(TAG, "Blocker: Decision=ADULT_LIST host=" + h);
                        launchOverlay(packageName);
                        resetUsageAndTimersForPackage(packageName);
                        return;
                    }
                }
            }

        } finally {
            rootNode.recycle();
        }
    }

    private boolean isBrowserPackage(String packageName, Context context) {
        Boolean cached = browserCheckCache.get(packageName);
        if (cached != null) return cached;

        Log.e("isBrowser: ", packageName);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        boolean isBrowser = false;
        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo.packageName.equals(packageName)) {
                Log.e("isBrowser: ", packageName + " true");
                isBrowser = true;
                break;
            }
        }
        if (!isBrowser) {
            Log.e("isBrowser: ", packageName + " false");
        }
        browserCheckCache.put(packageName, isBrowser);
        return isBrowser;
    }

    private Pair<String, Boolean> tryGetUrlBarText(String packageName, AccessibilityNodeInfo rootNode) {
        boolean nodeFound = false;
        String id = Utils.BROWSERS_PACKAGES.get(packageName);
        if (id != null) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(packageName + ":id/" + id);
            if (nodes != null && !nodes.isEmpty()) {
                nodeFound = true;
                String foundText = null;
                for (AccessibilityNodeInfo n : nodes) {
                    if (n == null) continue;
                    CharSequence t = n.getText();
                    if (t != null && t.length() > 0) {
                        foundText = t.toString();
                        break;
                    }
                }
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null) n.recycle();
                }
                if (foundText != null) return new Pair<>(foundText, true);
            }
        }
        for (String candidateId : Utils.browserIds) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(packageName + ":id/" + candidateId);
            if (nodes == null || nodes.isEmpty()) continue;
            nodeFound = true;
            String foundText = null;
            for (AccessibilityNodeInfo n : nodes) {
                if (n == null) continue;
                CharSequence t = n.getText();
                if (t != null && t.length() > 0) {
                    foundText = t.toString();
                    break;
                }
            }
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null) n.recycle();
            }
            if (foundText != null) return new Pair<>(foundText, true);
        }
        if (nodeFound) return new Pair<>("", true);
        return new Pair<>(null, false);
    }

    private Pair<String, Boolean> tryGetUrlBarTextWithRetry(String packageName, AccessibilityNodeInfo rootNode) {
        final int maxRetries = 3;
        final int delayMs = 300;

        // Avoid blocking the main thread; fall back to single attempt if on main looper
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return tryGetUrlBarText(packageName, rootNode);
        }

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Pair<String, Boolean> result = tryGetUrlBarText(packageName, rootNode);
            if (result.second) {
                return result;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Log.d("Sleep", String.valueOf(e));
            }
        }

        // No node found after all retries
        return new Pair<>(null, false);
    }

    private boolean isEdgeOrSamsung(String packageName) {
        if (packageName == null) return false;
        if (packageName.startsWith("com.microsoft.emmx")) return true;
        return "com.sec.android.app.sbrowser".equals(packageName);
    }

    private boolean isBlockedByUserListsEdgeSamsung(Set<String> candidateTextsLower, Set<String> candidateHosts) {
        Set<String> domains = BlockedSitesManager.getBlockedDomains(this);
        Set<String> exacts = BlockedSitesManager.getBlockedExactUrls(this);

        for (String d : domains) {
            if (d == null) continue;
            String dl = d.toLowerCase();
            boolean domainLike = dl.contains(".");
            if (domainLike) {
                if (candidateHosts != null) {
                    for (String host : candidateHosts) {
                        if (host.equals(dl) || host.endsWith("." + dl)) {
                            Log.d(TAG, "Blocker: ES DOMAIN match entry=" + dl + ", host=" + host);
                            return true;
                        }
                    }
                }
            } else {
                if (candidateTextsLower != null) {
                    for (String ct : candidateTextsLower) {
                        if (ct.contains(dl)) {
                            Log.d(TAG, "Blocker: ES KEYWORD match entry=" + dl + ", textSource=" + ct);
                            return true;
                        }
                    }
                }
            }
        }

        for (String e : exacts) {
            if (e == null) continue;
            HostPath exactHp = parseHostPath(e);
            if (exactHp == null || exactHp.host == null) continue;
            if (candidateHosts != null) {
                for (String host : candidateHosts) {
                    if (host.equals(exactHp.host) || host.endsWith("." + exactHp.host)) {
                        Log.d(TAG, "Blocker: ES EXACT-HOST match host=" + exactHp.host + ", candidateHost=" + host);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isBlockedByUserLists(Set<String> candidateTextsLower, Set<String> candidateHosts) {
        Set<String> domains = BlockedSitesManager.getBlockedDomains(this);
        Set<String> exacts = BlockedSitesManager.getBlockedExactUrls(this);

        // Domain entries
        for (String d : domains) {
            if (d == null) continue;
            String dl = d.toLowerCase();
            boolean domainLike = dl.contains(".");
            if (domainLike) {
                // Require host and check suffix match
                if (candidateHosts != null) {
                    for (String host : candidateHosts) {
                        if (host.equals(dl) || host.endsWith("." + dl)) {
                            Log.d(TAG, "Blocker: User list DOMAIN match entry=" + dl + ", host=" + host);
                            return true;
                        }
                    }
                }
            } else {
                // Brand keyword match limited to URL bar/title strings only
                if (candidateTextsLower != null) {
                    for (String ct : candidateTextsLower) {
                        if (ct.contains(dl)) {
                            Log.d(TAG, "Blocker: User list KEYWORD match entry=" + dl + ", textSource=" + ct);
                            return true;
                        }
                    }
                }
            }
        }

        // Exact URL entries (host + path prefix match)
        for (String e : exacts) {
            if (e == null) continue;
            HostPath exactHp = parseHostPath(e);
            if (exactHp == null || exactHp.host == null) continue;
            if (candidateTextsLower != null) {
                for (String ct : candidateTextsLower) {
                    HostPath candHp = parseHostPath(ct);
                    if (candHp == null || candHp.host == null) continue;
                    if (candHp.host.equals(exactHp.host) || candHp.host.endsWith("." + exactHp.host)) {
                        if (candHp.path.startsWith(exactHp.path)) {
                            Log.d(TAG, "Blocker: User list EXACT match host=" + exactHp.host + ", pathPrefix=" + exactHp.path + ", matchedPath=" + candHp.path);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static class HostPath {
        String host;
        String path;
    }

    @Nullable
    private HostPath parseHostPath(String text) {
        if (text == null || text.isEmpty()) return null;
        String host = AdultDomainListManager.extractHostFromUrlText(text);
        if (host == null || !host.contains(".")) return null;
        String s = text.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        try {
            java.net.URL u = new java.net.URL(s);
            String h = u.getHost().toLowerCase();
            if (h.startsWith("www.")) h = h.substring(4);
            String p = u.getPath();
            if (p == null || p.isEmpty()) p = "/";
            if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
            HostPath hp = new HostPath();
            hp.host = h;
            hp.path = p;
            return hp;
        } catch (Exception ignore) {
            String p = "/";
            int slash = s.indexOf('/');
            if (slash >= 0) p = s.substring(slash);
            if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
            HostPath hp = new HostPath();
            hp.host = host;
            hp.path = p;
            return hp;
        }
    }

    // ===== DEBUG / NODE TREE LOGGING =====
    private void logNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;
        String indent = new String(new char[depth]).replace("\0", "-");
        Log.d("NodeDump", indent + node.getViewIdResourceName() + " | " + node.getText());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            logNodeTree(child, depth + 1);
            if (child != null) child.recycle();
        }
    }

    // ===== FOREGROUND APP HELPERS =====
    @Nullable
    private String getForegroundPackageName() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        String foregroundPackageName = null;
        if (rootNode != null) {
            CharSequence packageNameChars = rootNode.getPackageName();
            if (packageNameChars != null) {
                foregroundPackageName = packageNameChars.toString();
            }
            rootNode.recycle();
        }
        return foregroundPackageName;
    }

    // ===== SCHEDULING / ALARMS =====
    private void scheduleMidnightReset() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, MidnightResetReceiver.class);
        intent.setAction(IntentActions.getActionResetAppTimes(this));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        Log.d(TAG, "Midnight reset alarm scheduled for: " + calendar.getTime());
    }

    private void clearCurrentPackageTracking() {
        if (currentPackage != null) {
            Log.d(TAG, "Clearing general app usage tracking for '" + currentPackage + "'.");
        }
        currentPackage = null;
        startTimeMillis = 0L;
    }

    private void resetUsageAndTimersForPackage(String packageName) {
        if (packageName == null) return;

        if (packageName.equals(this.currentPackage)) {
            clearCurrentPackageTracking();
        }
        String appTag = getAppTagFromAllPackages(packageName);
        if (appTag != null) {
            resetTimerStateForApp(appTag);
        }

        processViewFocusEndIfActive(packageName); // Old view focus system
    }

    private void updateAppTimeSpent(String packageName, int timeSpentSeconds) {
        if (timeSpentSeconds <= 0) return;
        int currentTime = sharedPreferences.getInt(packageName + "_time", 0);
        int newTotalTime = currentTime + timeSpentSeconds;
        sharedPreferences.edit().putInt(packageName + "_time", newTotalTime).apply();
        Log.d(TAG, "Updated SharedPreferences general app time for " + packageName + " to " + newTotalTime + "s");
        appTotalWastedTimeToday.put(packageName, (long) newTotalTime);

        Intent intent = new Intent(IntentActions.getActionTimeUpdated(this));
        intent.putExtra("packageName", packageName);
        intent.putExtra("timeSpent", timeSpentSeconds);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void processViewFocusEndIfActive(String packageName) { // Part of the old view focus system
        if (packageName != null && packageName.equals(currentViewIdPackage)) {
            currentViewIdPackage = null;
        }
    }

    public static void resetDailyViewTracking(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> keysToRemove = new HashSet<>();
        SharedPreferences focusPref = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor1 = focusPref.edit();
        editor1.remove("TotalFocusedTime");
        editor1.apply();

        for (String pkgName : Utils.ALL_PACKAGES.keySet()) {
            String timeKeyToClear = pkgName + "_time";
            if (prefs.contains(timeKeyToClear)) {
                keysToRemove.add(timeKeyToClear);
            }
            String scrollsKeyToClear = pkgName + "_scrolls";
            if (prefs.contains(scrollsKeyToClear)) {
                keysToRemove.add(scrollsKeyToClear);
            }
            // Also clear reminder ignored count for each appTag
            String appTag = Utils.ALL_PACKAGES.get(pkgName);
            if (appTag != null) {
                String ignoreKey = PREF_REMINDER_IGNORED_COUNT_PREFIX + appTag;
                if (prefs.contains(ignoreKey)) {
                    keysToRemove.add(ignoreKey);
                }
            }
        }

        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PREF_APP_VIEW_ACCUMULATED_TIME_MS_PREFIX) ||        // Old view focus
                    key.startsWith(PREF_LAST_VIEW_REMINDER_TIMESTAMP_PREFIX) ||   // Old view focus
                    key.startsWith(PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX) || // New reminder cooldowns
                    key.startsWith(PREF_REMINDER_VIEW_ACCUMULATED_TIME_CYCLE_MS_APP_TAG_PREFIX)) { // Old reminder system
                keysToRemove.add(key);
            }
        }

        if (!keysToRemove.isEmpty()) {
            for (String key : keysToRemove) {
                editor.remove(key);
            }
            editor.apply();
        }

        Intent intent = new Intent(ACTION_REFRESH_DAILY_STATE_INTERNAL);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void loadTodaysWastedTime() {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        appTotalWastedTimeToday.clear();
        for (String pkgName : Utils.ALL_PACKAGES.keySet()) {
            int time = sharedPreferences.getInt(pkgName + "_time", 0);
            if (time > 0) {
                appTotalWastedTimeToday.put(pkgName, (long) time);
            }
        }
    }

    private void refreshDailyServiceState() {
        loadTodaysWastedTime();
        loadLastReminderTimestampsForAppTags();
        resetAllReminderIgnoredCounts();

        long currentUserReminderIntervalMs = (long) remindDoomScrollingMinutes * 60 * 1000;
        appTagToTimeLeftForNextSessionMs.clear();
        appTagToOriginalDelayMs.clear();
        appTagToLastPostTimeMs.clear();
        for (ShowReminderRunnable activeRunnable : activeRunnables.values()) {
            reminderHandler.removeCallbacks(activeRunnable);
        }
        activeRunnables.clear();

        if (remindDoomScrollingEnabled) {
            for (String appTag : Utils.ALL_PACKAGES.values()) {
                if (appTag != null) {
                    appTagToTimeLeftForNextSessionMs.put(appTag, currentUserReminderIntervalMs);
                }
            }
        }
    }

    private String getAppNameFromPackageManager(String packageName) {
        PackageManager pm = getApplicationContext().getPackageManager();
        String appName;
        try {
            appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            appName = packageName;
        }
        return appName;
    }

    private String getAppTagFromAllPackages(String packageName) {
        if (Utils.ALL_PACKAGES.containsKey(packageName)) {
            return Utils.ALL_PACKAGES.get(packageName);
        }
        return null;
    }

    private String getPackageForAppTag(String appTag) { // Helper for reminders
        if (appTag == null) return null;
        for (Map.Entry<String, String> entry : Utils.ALL_PACKAGES.entrySet()) {
            if (appTag.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getAppNameForTag(String tag) { // Helper for reminder/overlay UI
        switch (tag) {
            case "yt":
                return "YouTube";
            case "insta":
                return "Instagram";
            case "snap":
                return "Snapchat";
            default:
                return "the app";
        }
    }

    private void findAndBlockView(AccessibilityNodeInfo rootNode, String targetViewIdName, String packageName) {
        if (rootNode == null || targetViewIdName == null || packageName == null) {
            if (rootNode != null) rootNode.recycle();
            return;
        }
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(packageName + ":id/" + targetViewIdName);
        if (nodes != null && !nodes.isEmpty()) {
            boolean actionPerformed = false;
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null && node.isVisibleToUser() && !actionPerformed) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    actionPerformed = true;
                }
                if (node != null) node.recycle();
            }
        }
    }

    // --- PeaceCoins Reminder Ignore Tracking ---
    private int getReminderIgnoredCount(String appTag) {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getInt(PREF_REMINDER_IGNORED_COUNT_PREFIX + appTag, 0);
    }

    private void incrementReminderIgnoredCount(String appTag) {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int count = getReminderIgnoredCount(appTag) + 1;
        sharedPreferences.edit().putInt(PREF_REMINDER_IGNORED_COUNT_PREFIX + appTag, count).apply();
    }

    private void resetAllReminderIgnoredCounts() {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        for (String appTag : Utils.ALL_PACKAGES.values()) {
            sharedPreferences.edit().putInt(PREF_REMINDER_IGNORED_COUNT_PREFIX + appTag, 0).apply();
        }
    }
}

