package com.gxdevs.mindmint.Services;

import static com.gxdevs.mindmint.Utils.Utils.isKeepAlive;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.gxdevs.mindmint.Activities.BlockingOverlayDisplayActivity;
import com.gxdevs.mindmint.Common.IntentActions;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.db.MindMintRoomDatabase;
import com.gxdevs.mindmint.db.entities.BlockedAppEntity;
import com.gxdevs.mindmint.db.dao.BlockedAppDao;
import com.gxdevs.mindmint.Receivers.MidnightResetReceiver;
import com.gxdevs.mindmint.Receivers.NotificationDismissBroadcastReceiver;
import com.gxdevs.mindmint.Receivers.ServiceResumeReceiver;
import com.gxdevs.mindmint.Utils.AdultDomainListManager;
import com.gxdevs.mindmint.Utils.AlarmUtils;
import com.gxdevs.mindmint.Utils.BlockedSitesManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Utils.WarningUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressLint("AccessibilityPolicy")
public class AppUsageAccessibilityService extends AccessibilityService {

    private final List<BlockedAppEntity> cachedBlockedApps = new ArrayList<>();

    private void loadBlockedAppsFromDb() {
        try {
            MindMintRoomDatabase db = MindMintRoomDatabase.getInstance(this);
            List<BlockedAppEntity> apps = db.blockedAppDao().getAllSync();
            synchronized (cachedBlockedApps) {
                cachedBlockedApps.clear();
                cachedBlockedApps.addAll(apps);
            }
            Log.d(TAG, "Loaded " + apps.size() + " apps from database for blocking.");
        } catch (Exception e) {
            Log.e(TAG, "Error loading blocked apps from database", e);
        }
    }

    private static final String TAG = "AppUsageAccessibilityService";
    private static final String PREFS_NAME = "AppData";
    // Reuse the Guard service's notification so only one notification is shown
    private static final String FGS_CHANNEL_ID = "mindmint_guard_channel";
    private static final int FGS_NOTIFICATION_ID = 42;

    // --- Constants for Custom Blocking (Focus Mode) ---
    public static final String PREF_CUSTOM_BLOCKED_APPS = "custom_blocked_apps_set";
    public static final String PREF_BLOCKING_POPUP_DURATION_SEC = "blocking_popup_duration_seconds";
    public static final String BLOCKING_OVERLAY_ACTIVITY_CLASS_NAME = "com.gxdevs.mindmint.Activities.BlockingOverlayDisplayActivity";
    public static final String EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name";
    public static final String EXTRA_BLOCKED_PACKAGE_NAME = "extra_blocked_package_name";
    public static final String EXTRA_IS_REMINDER_ONLY = "extra_is_reminder_only";
    public static final String EXTRA_IS_FOCUS = "extra_is_focus";
    public static final String EXTRA_CUSTOM_SUBTITLE = "extra_custom_subtitle";

    public static final String PREF_REMIND_DOOM_SCROLLING_ENABLED = "pref_remind_doom_scrolling_enabled";
    public static final String PREF_REMIND_DOOM_SCROLLING_MINUTES = "pref_remind_doom_scrolling_minutes";
    public static final int DEFAULT_REMIND_DOOM_SCROLLING_MINUTES = 10;
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_ENABLED = "pref_block_after_wasted_time_enabled";
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_HOURS = "pref_block_after_wasted_time_hours";
    public static final float DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS = 1.0f;
    public static final String PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED = "pref_block_browsers_doom_enabled";
    public static final String PREF_BLOCK_ADULT_SITES_ENABLED = "pref_block_adult_sites_enabled";

    public static final String PREF_APP_VIEW_ACCUMULATED_TIME_MS_PREFIX = "app_view_accumulated_time_ms_";
    public static final String PREF_LAST_VIEW_REMINDER_TIMESTAMP_PREFIX = "last_view_reminder_timestamp_";
    public static final String PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX = "last_reminder_timestamp_app_tag_";
    public static final String PREF_REMINDER_VIEW_ACCUMULATED_TIME_CYCLE_MS_APP_TAG_PREFIX = "reminder_view_accumulated_time_cycle_ms_app_tag_";

    // Action for the overlay to tell this service to close the current app
    public static final String ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY = "com.gxdevs.mindmint.action.PERFORM_GLOBAL_HOME_FROM_OVERLAY";
    public static final String ACTION_PERFORM_GLOBAL_BACK_FROM_OVERLAY = "com.gxdevs.mindmint.action.PERFORM_GLOBAL_BACK_FROM_OVERLAY";

    // Broadcast Action for internal state refresh at midnight
    public static final String ACTION_REFRESH_DAILY_STATE_INTERNAL = "com.gxdevs.mindmint.action.REFRESH_DAILY_STATE_INTERNAL";
    public static final String ACTION_UPDATE_KEEP_ALIVE = "com.gxdevs.mindmint.action.UPDATE_KEEP_ALIVE";

    // Admin Guard — App Info page blocker
    public static final String PREF_ADMIN_GUARD_TRUSTED_TOKEN = "pref_admin_guard_trusted_token_ms";
    private static final long ADMIN_GUARD_TOKEN_VALIDITY_MS = 30_000L; // bypass window
    private static final long ADMIN_GUARD_COOLDOWN_MS = 1_500L; // HOME spam guard
    private volatile long lastAdminGuardHomeTimeMs = 0L;
    private static final String PREF_HOME_YT_SWITCH_STATE = "ytSwitchState";
    private static final String PREF_HOME_INSTA_SWITCH_STATE = "instaSwitchState";
    private static final String PREF_HOME_SNAP_SWITCH_STATE = "snapSwitchState";
    private static final String EXTRA_HOME_YT_SWITCH_ON = "home_yt_switch_on";
    private static final String EXTRA_HOME_INSTA_SWITCH_ON = "home_insta_switch_on";
    private static final String EXTRA_HOME_SNAP_SWITCH_ON = "home_snap_switch_on";

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

    // Exact-match set only for packages whose names contain no identifying
    // keywords.
    private static final Set<String> LOCKED_IN_ESSENTIAL_WHITELIST = new HashSet<>(java.util.Arrays.asList(
            "com.gxdevs.mindmint",
            "android",
            "com.android.server.telecom",
            "com.touchtype.swiftkey",
            "com.swiftkey.swiftkeyapp",
            "com.baidu.input_mi",
            "com.bbk.contacts",
            "com.samsung.rcs.eab",
            "com.google.android.apps.safetyhub",
            "com.google.android.marvin.talkback",
            "com.google.android.apps.jibe",
            "com.google.android.apps.nbu.files",
            "com.samsung.android.app.cocktailbarservice"));

    public static final String PREF_LOCKED_IN_EXTRA_WHITELIST = "pref_locked_in_extra_whitelist";
    private final Set<String> dynamicLauncherPackages = new HashSet<>();

    private final Map<String, Boolean> systemNavPackageCache = new HashMap<>();

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

    // saveReminderViewAccumulatedTime method.
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

    // --- Variables for old View Focus system (potentially needs review/cleanup)
    private final Map<String, Long> currentViewFocusSessionStartTimeMap = new HashMap<>();
    private final Map<String, Long> appViewFocusAccumulatedTimeTodayMap = new HashMap<>();
    private String currentViewIdPackage = null;

    // --- PeaceCoins Reminder Ignore Tracking ---
    private static final String PREF_REMINDER_IGNORED_COUNT_PREFIX = "reminder_ignored_count_";

    // --- Overlay variables for Scroll Counter Pill ---
    private WindowManager windowManager;
    private View scrollPillOverlayView;
    private TextView pillScrollCountText;
    private ImageView pillAppIconImage;
    private boolean isScrollCounterPillVisible = false;
    private boolean scrollCounterEnabled = false;
    private boolean scrollCounterPerApp = false;
    private String lastSeenPillAppTag = null;
    private String lastSeenPillPackageName = null;
    private final android.os.Handler scrollPillHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideScrollPillRunnable = () -> {
        if (scrollPillOverlayView != null) {
            // First, double check if it's actually gone by requesting the root node
            AccessibilityNodeInfo rootNode = null;
            try {
                rootNode = getRootInActiveWindow();
                if (rootNode != null && lastSeenPillAppTag != null && lastSeenPillPackageName != null) {
                    String viewId = getReminderViewIdForAppTag(lastSeenPillAppTag);
                    if (viewId != null && isReminderViewVisible(rootNode, lastSeenPillPackageName, viewId)) {
                        return; // It's still visible! Abort hide.
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (rootNode != null)
                    rootNode.recycle();
            }

            android.view.View pillContainer = scrollPillOverlayView.findViewById(R.id.pillContainer);
            if (pillContainer != null) {
                pillContainer.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    if (scrollPillOverlayView != null && windowManager != null) {
                        try {
                            windowManager.removeView(scrollPillOverlayView);
                        } catch (Exception ignored) {
                        }
                        scrollPillOverlayView = null;
                    }
                }).start();
            } else {
                if (windowManager != null) {
                    try {
                        windowManager.removeView(scrollPillOverlayView);
                    } catch (Exception ignored) {
                    }
                }
                scrollPillOverlayView = null;
            }
            isScrollCounterPillVisible = false;
        }
    };

    private long lastActionTime = 0L;
    private long lastGoHomeTimeMs = 0L;
    public static boolean serviceStatus = false;

    private final Map<String, Long> lastLockInOverlayTimeMs = new HashMap<>();
    private static final long LOCK_IN_OVERLAY_DEBOUNCE_MS = 5000L;
    
    public static final Map<String, Long> packageBypassExpiryTimeMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> lastTimeSectionVisibleMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> wasCommentSectionOpenMap = new java.util.concurrent.ConcurrentHashMap<>();
    
    public static void grantBypass(String packageName, int durationMinutes) {
        if (packageName != null) {
            packageBypassExpiryTimeMap.put(packageName, System.currentTimeMillis() + (long) durationMinutes * 60 * 1000);
            Log.d(TAG, "Static bypass granted for package: " + packageName + " for " + durationMinutes + " mins");
        }
    }
    
    private boolean isPackageBypassed(String packageName) {
        if (packageName == null) return false;
        Long expiry = packageBypassExpiryTimeMap.get(packageName);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            return true;
        }
        return false;
    }
    private BroadcastReceiver screenReceiver;
    private boolean isScreenReceiverRegistered = false;
    public static final String RESTORE_NOTIFICATION = "restore_notification";
    private BroadcastReceiver notificationRestoreReceiver;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        BlockedSitesManager.ensureSetsExist(getApplicationContext());
        loadLauncherPackages();

        notificationRestoreReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    if (RESTORE_NOTIFICATION.equals(intent.getAction())
                            || ACTION_UPDATE_KEEP_ALIVE.equals(intent.getAction())) {
                        if (isKeepAlive(context)) {
                            updateAddonsState(); // Force update
                        } else {
                            // Explicitly stop if toggle turned off and broadcast received
                            stopForeground(true);
                            if (isScreenReceiverRegistered) {
                                unregisterScreenReceiver();
                            }
                            AlarmUtils.cancelAlarm(AppUsageAccessibilityService.this);
                            WarningUtils.remove(AppUsageAccessibilityService.this);
                        }
                    }
                }
            }
        };

        IntentFilter notiFilter = new IntentFilter();
        notiFilter.addAction(RESTORE_NOTIFICATION);
        notiFilter.addAction(ACTION_UPDATE_KEEP_ALIVE);
        ContextCompat.registerReceiver(this, notificationRestoreReceiver, notiFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        restoreBlockingState();

        pauseServiceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long pauseDuration = intent.getLongExtra("pause_duration", 0);
                if (pauseDuration > 0) {
                    // Bug 3 fix: refuse pause if Lock In session is active at the receiver level
                    boolean lockedIn = sharedPreferences.getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
                    if (FocusService.isPublicFocusRun && lockedIn) {
                        Toast.makeText(context, "You are in focus mode right now.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long resumeTime = System.currentTimeMillis() + pauseDuration;
                    sharedPreferences.edit()
                            .putBoolean("isServicePaused", true)
                            .putLong("resumeTime", resumeTime)
                            .apply();
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    Intent resumeIntent = new Intent(context, ServiceResumeReceiver.class);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, resumeIntent,
                            PendingIntent.FLAG_IMMUTABLE);
                    try {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, resumeTime, pendingIntent);
                    } catch (SecurityException e) {
                        Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    sharedPreferences.edit()
                            .putBoolean("isServicePaused", false)
                            .putLong("resumeTime", 0)
                            .apply();
                }
            }
        };
        IntentFilter pauseFilter = new IntentFilter(IntentActions.getActionPauseService(this));
        ContextCompat.registerReceiver(this, pauseServiceReceiver, pauseFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        loadConfiguration();
        loadBlockedAppsFromDb();
        loadTodaysWastedTime();
        loadLastReminderTimestampsForAppTags();

        reminderHandler = new Handler(Looper.getMainLooper());

        preferenceChangeListener = (sharedPrefs, key) -> {
            if (key != null) {
                switch (key) {
                    case PREF_CUSTOM_BLOCKED_APPS:
                    case "keepServiceAlive":
                        updateAddonsState();
                        if ("keepServiceAlive".equals(key))
                            break; // Don't reload config for keepServiceAlive, just update addons
                    case PREF_BLOCKING_POPUP_DURATION_SEC:
                    case PREF_REMIND_DOOM_SCROLLING_ENABLED:
                    case PREF_REMIND_DOOM_SCROLLING_MINUTES:
                    case PREF_BLOCK_AFTER_WASTED_TIME_ENABLED:
                    case PREF_BLOCK_AFTER_WASTED_TIME_HOURS:
                    case PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED:
                    case PREF_BLOCK_ADULT_SITES_ENABLED:
                        loadConfiguration();
                        appViewFocusAccumulatedTimeTodayMap.clear(); // Part of old view focus
                        loadLastReminderTimestampsForAppTags();
                        break;
                    case FocusService.PREF_IS_LOCKED_IN:
                        lastLockInOverlayTimeMs.clear();
                        break;
                    case "pref_scroll_counter_enabled":
                    case "pref_scroll_counter_per_app":
                        scrollCounterEnabled = sharedPreferences.getBoolean("pref_scroll_counter_enabled", false);
                        scrollCounterPerApp = sharedPreferences.getBoolean("pref_scroll_counter_per_app", false);
                        if (!scrollCounterEnabled)
                            hideScrollCounterPill(true);
                        break;
                    case PREF_HOME_YT_SWITCH_STATE:
                    case PREF_HOME_INSTA_SWITCH_STATE:
                    case PREF_HOME_SNAP_SWITCH_STATE:
                        restoreBlockingState();
                        break;
                }
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadBlockedAppsFromDb();
                loadConfiguration();
            }
        };
        IntentFilter filter = new IntentFilter(IntentActions.getActionUpdatePackages(this));
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        overlayCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY.equals(intent.getAction())) {
                    lastGoHomeTimeMs = System.currentTimeMillis();
                    Log.d(TAG, "Received ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY. Setting lastGoHomeTimeMs = " + lastGoHomeTimeMs);
                    performThrottledGlobalAction(GLOBAL_ACTION_HOME);
                } else if (intent != null && ACTION_PERFORM_GLOBAL_BACK_FROM_OVERLAY.equals(intent.getAction())) {
                    performThrottledGlobalAction(GLOBAL_ACTION_BACK);
                } else if (intent != null && "com.gxdevs.mindmint.action.APP_BYPASS_GRANTED".equals(intent.getAction())) {
                    String pkg = intent.getStringExtra("package_name");
                    int durationMinutes = intent.getIntExtra("duration_minutes", 10);
                    if (pkg != null) {
                        packageBypassExpiryTimeMap.put(pkg, System.currentTimeMillis() + (long) durationMinutes * 60 * 1000);
                        Log.d(TAG, "Bypass granted for package: " + pkg + " for " + durationMinutes + " mins");
                    }
                }
            }
        };
        IntentFilter overlayIntentFilter = new IntentFilter(ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY);
        overlayIntentFilter.addAction(ACTION_PERFORM_GLOBAL_BACK_FROM_OVERLAY);
        overlayIntentFilter.addAction("com.gxdevs.mindmint.action.APP_BYPASS_GRANTED");
        ContextCompat.registerReceiver(this, overlayCommandReceiver, overlayIntentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        midnightStateRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_REFRESH_DAILY_STATE_INTERNAL.equals(intent.getAction())) {
                    refreshDailyServiceState();
                }
            }
        };
        IntentFilter midnightRefreshFilter = new IntentFilter(ACTION_REFRESH_DAILY_STATE_INTERNAL);
        ContextCompat.registerReceiver(this, midnightStateRefreshReceiver, midnightRefreshFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        scheduleMidnightReset();
    }

    private void loadLauncherPackages() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo != null && info.activityInfo.packageName != null) {
                dynamicLauncherPackages.add(info.activityInfo.packageName);
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        serviceStatus = true;
        super.onServiceConnected();

        restoreBlockingState();
        loadBlockedAppsFromDb();
        updateAddonsState();
        loadLastReminderTimestampsForAppTags();

        if (appTagToTimeLeftForNextSessionMs.isEmpty()) {
            long defaultUserReminderIntervalMs = (long) sharedPreferences.getInt(PREF_REMIND_DOOM_SCROLLING_MINUTES,
                    DEFAULT_REMIND_DOOM_SCROLLING_MINUTES) * 60 * 1000;
            for (String pkgAppTag : Utils.ALL_PACKAGES.values()) {
                if (pkgAppTag != null && !appTagToTimeLeftForNextSessionMs.containsKey(pkgAppTag)) {
                    appTagToTimeLeftForNextSessionMs.put(pkgAppTag, defaultUserReminderIntervalMs);
                }
            }
        }

        String foregroundPackageName = getForegroundPackageName();
        if (foregroundPackageName != null && Utils.ALL_PACKAGES.containsKey(foregroundPackageName)) {
            if (!foregroundPackageName.equals(this.currentPackage) || this.startTimeMillis == 0L) {
                this.currentPackage = foregroundPackageName;
                this.startTimeMillis = System.currentTimeMillis();
                if (blockAfterWastedTimeEnabled && !appTotalWastedTimeToday.containsKey(this.currentPackage)) {
                    appTotalWastedTimeToday.put(this.currentPackage,
                            (long) sharedPreferences.getInt(this.currentPackage + "_time", 0));
                }
            }
        } else {
            if (this.currentPackage != null || this.startTimeMillis != 0L) {
                this.currentPackage = null;
                this.startTimeMillis = 0L;
            }
        }
    }

    private void ensurePreferenceStores() {
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        }
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        }
    }

    private void restoreBlockingState() {
        ensurePreferenceStores();
        isYtHomeSwitchOn = sharedPreferences.getBoolean(PREF_HOME_YT_SWITCH_STATE, false);
        isInstaHomeSwitchOn = sharedPreferences.getBoolean(PREF_HOME_INSTA_SWITCH_STATE, false);
        isSnapHomeSwitchOn = sharedPreferences.getBoolean(PREF_HOME_SNAP_SWITCH_STATE, false);
    }

    /**
     * Returns true when the home-screen switch for the given appTag is ON (i.e. the
     * app is being blocked).
     */
    private boolean isAppTagBlocked(String appTag) {
        if (appTag == null)
            return false;
        return switch (appTag) {
            case "yt" -> isYtHomeSwitchOn;
            case "insta" -> isInstaHomeSwitchOn;
            case "snap" -> isSnapHomeSwitchOn;
            default -> false;
        };
    }

    private void updateBlockingStateFromIntent(Intent intent) {
        restoreBlockingState();
        if (intent == null) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }

        if (bundle.containsKey(EXTRA_HOME_YT_SWITCH_ON)) {
            isYtHomeSwitchOn = bundle.getBoolean(EXTRA_HOME_YT_SWITCH_ON, isYtHomeSwitchOn);
        }
        if (bundle.containsKey(EXTRA_HOME_INSTA_SWITCH_ON)) {
            isInstaHomeSwitchOn = bundle.getBoolean(EXTRA_HOME_INSTA_SWITCH_ON, isInstaHomeSwitchOn);
        }
        if (bundle.containsKey(EXTRA_HOME_SNAP_SWITCH_ON)) {
            isSnapHomeSwitchOn = bundle.getBoolean(EXTRA_HOME_SNAP_SWITCH_ON, isSnapHomeSwitchOn);
        }
    }

    private void updateAddonsState() {
        boolean shouldKeepAlive = isKeepAlive(this);
        if (shouldKeepAlive) {
            startForegroundProtection();
            if (!isScreenReceiverRegistered) {
                registerScreenReceiver();
            }
            AlarmUtils.scheduleAlarm(this);
        } else {
            stopForeground(true);
            if (isScreenReceiverRegistered) {
                unregisterScreenReceiver();
            }
            AlarmUtils.cancelAlarm(this);
            WarningUtils.remove(this);
        }
    }

    private void unregisterScreenReceiver() {
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception e) {
                isScreenReceiverRegistered = false;
            }
        }
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        if (screenReceiver != null) {
            return;
        }
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive: screenReceiver");
            }
        };
        registerReceiver(screenReceiver, filter);
        isScreenReceiverRegistered = true;
    }

    private void startForegroundProtection() {
        if (WarningUtils.isWarningVisible(this)) {
            WarningUtils.remove(this);
        }
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, FGS_CHANNEL_ID)
                .setContentTitle("MindMint Protected")
                .setContentText("Accessibility Service is Active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setDeleteIntent(PendingIntent.getBroadcast(this, 0,
                        new Intent(this, NotificationDismissBroadcastReceiver.class), PendingIntent.FLAG_IMMUTABLE))
                .build();

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(FGS_NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(FGS_NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
        }
    }

    @Override
    public void onInterrupt() {
        processViewFocusEndIfActive(currentViewIdPackage); // Old view focus system
    }

    @Override
    public void onDestroy() {
        serviceStatus = false;

        unregisterScreenReceiver();

        if (notificationRestoreReceiver != null) {
            try {
                unregisterReceiver(notificationRestoreReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering notificationRestoreReceiver", e);
            }
        }

        super.onDestroy();
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

        // Clear pending tasks
        hideScrollCounterPill(true);
        if (reminderHandler != null) {
            reminderHandler.removeCallbacksAndMessages(null);
            reminderHandler = null;
        }
        // Schedule a restart to keep the service alive
        AlarmUtils.scheduleAlarm(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = null;
        try {
            boolean isLockedInPref = sharedPreferences.getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
            boolean isFocusRunning = FocusService.isPublicFocusRun || isLockedInPref;
            boolean isLockedInActive = isFocusRunning && isLockedInPref;

            if (!isLockedInActive && sharedPreferences.getBoolean("isServicePaused", false)) {
                return;
            }
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive())
                return;
            String eventPackageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
            int eventType = event.getEventType();

            if (eventPackageName != null
                    && (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                            || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
                handleAdminProtectionGuard(eventPackageName);
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    manageFrictionSessions(eventPackageName);
                }
            }

            if (!isLockedInActive || isPackageAllowedInLockedIn(eventPackageName)) {
                handleScrollCounting(event);
            }
            boolean isBlockerEvent = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
                    || eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED);
            if (isBlockerEvent && eventPackageName != null && !eventPackageName.equals(getPackageName())) {
                if (isFocusRunning) {
                    rootNode = getRootInActiveWindow();
                    if (rootNode != null) {
                        CharSequence activePkgSequence = rootNode.getPackageName();
                        if (activePkgSequence != null) {
                            String activePkg = activePkgSequence.toString();
                            if (!activePkg.equals(eventPackageName) && !activePkg.equals(getPackageName())) {
                                rootNode.recycle();
                                rootNode = null;
                                return; // Ignore spurious background event
                            }
                        }
                    }

                    long now = System.currentTimeMillis();

                    if (isLockedInPref) {
                        boolean allowed = isPackageAllowedInLockedIn(eventPackageName);
                        if (!allowed) {
                            if (isPackageBypassed(eventPackageName)) {
                                return; // Bypassed
                            }
                            if (System.currentTimeMillis() - lastGoHomeTimeMs < 1500) {
                                Log.d(TAG, "Suppressed locked-in overlay for '" + eventPackageName + "' due to cancel/go home.");
                                return;
                            }
                            Long lastTime = lastLockInOverlayTimeMs.get(eventPackageName);
                            if (lastTime == null || (now - lastTime) > LOCK_IN_OVERLAY_DEBOUNCE_MS) {
                                lastLockInOverlayTimeMs.put(eventPackageName, now);
                                Intent intent = buildBlockingIntent(eventPackageName, "none", true);
                                startActivity(intent);
                                resetUsageAndTimersForPackage(eventPackageName);
                            }
                            return;
                        }
                    } else if (customBlockedApps != null && customBlockedApps.contains(eventPackageName)) {
                        if (isPackageBypassed(eventPackageName)) {
                            return; // Bypassed
                        }
                        if (System.currentTimeMillis() - lastGoHomeTimeMs < 1500) {
                            Log.d(TAG, "Suppressed custom blocked app overlay for '" + eventPackageName + "' due to cancel/go home.");
                            return;
                        }
                        Long lastTime = lastLockInOverlayTimeMs.get(eventPackageName);
                        if (lastTime == null || (now - lastTime) > LOCK_IN_OVERLAY_DEBOUNCE_MS) {
                            lastLockInOverlayTimeMs.put(eventPackageName, now);
                            Intent intent = buildBlockingIntent(eventPackageName, "none", true);
                            startActivity(intent);
                            resetUsageAndTimersForPackage(eventPackageName);
                        }
                        return;
                    }
                } else {
                    if (rootNode == null) {
                        rootNode = getRootInActiveWindow();
                    }
                    checkBlockerPipeline(eventPackageName, rootNode);
                }
            }

            rootNode = getRootInActiveWindow();
            if (eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (remindDoomScrollingEnabled || scrollCounterEnabled) {
                    String activeAppTagNow = null;
                    String activeReminderViewIdNow = null;
                    boolean isTrackedViewVisibleNow = false;

                    if (eventPackageName != null) {
                        String appTagForEvent = getAppTagFromAllPackages(eventPackageName);
                        if (appTagForEvent != null) {
                            // Scroll counter pill: show whenever a tracked app is in foreground,
                            // regardless of whether the app is "restricted" or the reminder view is visible.
                            if (scrollCounterEnabled) {
                                if (!isAppTagBlocked(appTagForEvent)) {
                                    showScrollCounterPill(appTagForEvent, eventPackageName);
                                } else {
                                    hideScrollCounterPill(false);
                                }
                            }

                            // Reminder view tracking: still requires restriction + view visible
                            BlockedAppEntity config = getBlockedAppConfig(eventPackageName);
                            if (config != null && config.isRestricted) {
                                String reminderViewId = getReminderViewIdForAppTag(appTagForEvent);
                                if (reminderViewId != null) {
                                    if (isReminderViewVisible(rootNode, eventPackageName, reminderViewId)) {
                                        activeAppTagNow = appTagForEvent;
                                        activeReminderViewIdNow = reminderViewId;
                                        isTrackedViewVisibleNow = true;
                                    }
                                }
                            }
                        }
                    } else if (scrollCounterEnabled) {
                        // App package is null or not tracked — hide pill
                        hideScrollCounterPill(false);
                    }

                    String previouslyTrackedAppTag = currentAppTagForReminderViewTracking;

                    if (isTrackedViewVisibleNow) {
                        if (remindDoomScrollingEnabled) {
                            if (!activeAppTagNow.equals(previouslyTrackedAppTag)) {
                                if (previouslyTrackedAppTag != null) {
                                    endReminderViewSession(previouslyTrackedAppTag);
                                }
                                startReminderViewSession(activeAppTagNow, activeReminderViewIdNow);
                            } else {
                                if (!activeRunnables.containsKey(activeAppTagNow)) {
                                    startReminderViewSession(activeAppTagNow, activeReminderViewIdNow);
                                }
                            }
                        }
                    } else {
                        if (remindDoomScrollingEnabled && previouslyTrackedAppTag != null) {
                            endReminderViewSession(previouslyTrackedAppTag);
                        }
                    }
                }
                if (eventPackageName == null) {
                    hideScrollCounterPill(false);
                    if (currentPackage != null && Utils.ALL_PACKAGES.containsKey(currentPackage)
                            && startTimeMillis != 0L) {
                        long endTimeMillis = System.currentTimeMillis();
                        int timeSpent = (int) Math.max(0L, (endTimeMillis - startTimeMillis) / 1000L);
                        updateAppTimeSpent(currentPackage, timeSpent);
                    }
                    if (currentAppTagForReminderViewTracking != null) {
                        endReminderViewSession(currentAppTagForReminderViewTracking);
                    }
                    clearCurrentPackageTracking();
                    processViewFocusEndIfActive(currentViewIdPackage); // Old view focus system
                    return;
                }
            }

            // --- Browser blocking (user list) and adult sites block ---
            if (blockBrowsersDoomEnabled || blockAdultSitesEnabled) {
                tryBlockBrowser(eventPackageName, rootNode);
            }

            // --- General App Usage Time Tracking & Window State Changes ---
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { // NEW CONDITION (Scroll is handled above)
                if (!eventPackageName.equals(currentPackage)) {
                    if (currentPackage != null && Utils.ALL_PACKAGES.containsKey(currentPackage)
                            && startTimeMillis != 0L) {
                        long endTimeMillis = System.currentTimeMillis();
                        int timeSpent = (int) Math.max(0L, (endTimeMillis - startTimeMillis) / 1000L);
                        updateAppTimeSpent(currentPackage, timeSpent);
                    }
                    processViewFocusEndIfActive(currentPackage); // Old view focus system

                    if (Utils.ALL_PACKAGES.containsKey(eventPackageName)) {
                        currentPackage = eventPackageName;
                        startTimeMillis = System.currentTimeMillis();
                        if (blockAfterWastedTimeEnabled && !appTotalWastedTimeToday.containsKey(currentPackage)) {
                            appTotalWastedTimeToday.put(currentPackage,
                                    (long) sharedPreferences.getInt(currentPackage + "_time", 0));
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

            if (currentAppTagForReminderViewTracking != null) {
                assert eventPackageName != null;
                if (!eventPackageName.equals(getPackageForAppTag(currentAppTagForReminderViewTracking))) {
                    String appTagToEnd = currentAppTagForReminderViewTracking;
                    if (reminderViewSessionStartTimeMs.containsKey(appTagToEnd)) {
                        Long sessionStartTimeValue = reminderViewSessionStartTimeMs.remove(appTagToEnd);
                        long sessionStartTime = sessionStartTimeValue != null ? sessionStartTimeValue : 0L;
                        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
                        if (sessionDuration > 0) {
                            Long previouslyAccumulatedValue = reminderViewAccumulatedTimeCurrentCycleMs
                                    .get(appTagToEnd);
                            long previouslyAccumulated = previouslyAccumulatedValue != null ? previouslyAccumulatedValue
                                    : 0L;
                            long newAccumulatedTime = previouslyAccumulated + sessionDuration;
                            reminderViewAccumulatedTimeCurrentCycleMs.put(appTagToEnd, newAccumulatedTime);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing accessibility event", e);
        } finally {
            // Recycle the root node once at the end
            if (rootNode != null) {
                rootNode.recycle();
            }
        }
    }

    private void handleScrollCounting(AccessibilityEvent event) {
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : null;
        if (packageName == null) {
            return;
        }

        String appTag = getAppTagFromAllPackages(packageName);
        if (appTag == null) {
            return;
        }

        // Scroll counting works regardless of restriction status — it is a tracking/awareness feature.
        if (!scrollCounterEnabled) {
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
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        
        boolean commentSectionOpen = isCommentSectionVisible(rootNode, packageName);
        if (commentSectionOpen) {
            wasCommentSectionOpenMap.put(packageName, true);
            if (rootNode != null) {
                rootNode.recycle();
            }
            return; // Ignore scroll counting when comment sheet is visible
        }
        
        Boolean wasOpen = wasCommentSectionOpenMap.get(packageName);
        if (wasOpen != null && wasOpen) {
            wasCommentSectionOpenMap.put(packageName, false);
            lastScrollEventTimestamp.put(appTag, System.currentTimeMillis()); // Debounce next immediate event
            if (rootNode != null) {
                rootNode.recycle();
            }
            Log.d(TAG, "Ignored scroll count on closing comment section for: " + packageName);
            return;
        }

        // If rootNode is null, we can't verify the view - but we still know the correct
        // event type fired for the correct package, so count it anyway as a fallback.
        boolean viewVisible = rootNode == null || isReminderViewVisible(rootNode, packageName, viewId);
        if (rootNode != null) {
            rootNode.recycle();
        }
        if (viewVisible) {
            long currentTime = System.currentTimeMillis();
            Long lastTimeValue = lastScrollEventTimestamp.get(appTag);
            long lastTime = lastTimeValue != null ? lastTimeValue : 0L;
            if (currentTime - lastTime > debounceMs) {
                lastScrollEventTimestamp.put(appTag, currentTime);
                incrementScrollCount(packageName);
            }
        }
    }

    private void incrementScrollCount(String packageName) {
        if (packageName == null)
            return;

        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        }

        long currentScrolls = sharedPreferences.getLong(packageName + "_scrolls", 0L);
        long newTotalScrolls = currentScrolls + 1;

        // Calculate random time spent on this scroll (reel) between 7 and 17 seconds
        int randomSeconds = (int) (Math.random() * (17 - 7 + 1) + 7);
        long currentEstimatedTime = sharedPreferences.getLong("total_estimated_wasted_time", 0);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(packageName + "_scrolls", newTotalScrolls);
        editor.putLong("total_estimated_wasted_time", currentEstimatedTime + randomSeconds);
        editor.apply();

        if (isScrollCounterPillVisible && scrollCounterEnabled && scrollPillOverlayView != null) {
            String appTag = getAppTagFromAllPackages(packageName);
            if (appTag != null) {
                new Handler(Looper.getMainLooper()).post(() -> updateScrollCounterPillInternal(appTag));
            }
        }

        Intent intent = new Intent(IntentActions.getActionTimeUpdated(this));
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private boolean isPackageAllowedInLockedIn(String packageName) {
        if (packageName == null)
            return true;
        if (LOCKED_IN_ESSENTIAL_WHITELIST.contains(packageName))
            return true;
        if (dynamicLauncherPackages.contains(packageName))
            return true;
        if (isEssentialPackage(packageName))
            return true;
        if (isSystemNavigationPackage(packageName))
            return true;
        Set<String> extraWhitelist = sharedPreferences.getStringSet(
                PREF_LOCKED_IN_EXTRA_WHITELIST, new HashSet<>());
        return extraWhitelist.contains(packageName);
    }

    /** Keyword-based check covering all OEM variants of essential system apps. */
    private static boolean isEssentialPackage(String pkg) {
        String p = pkg.toLowerCase();
        return p.contains("dialer")
                || p.contains("incallui")
                || p.contains("phone")
                || p.contains("telecom")
                || p.contains("contacts")
                || p.contains("messaging")
                || p.contains(".mms")
                || p.contains(".sms")
                || p.contains("inputmethod")
                || p.contains("honeyboard")
                || p.contains("keyboard")
                || p.contains("camera")
                || p.contains("launcher")
                || p.contains("systemui")
                || p.contains("packageinstaller")
                || p.contains("permissioncontroller")
                || p.contains("emergency")
                || p.contains(".sos")
                || p.contains("accessibility");
    }

    private boolean isSystemNavigationPackage(String pkg) {
        if (pkg == null)
            return false;
        Boolean cached = systemNavPackageCache.get(pkg);
        if (cached != null)
            return cached;

        boolean nameMatches = isNameMatches(pkg);

        if (!nameMatches) {
            systemNavPackageCache.put(pkg, false);
            return false;
        }

        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystem) {
                systemNavPackageCache.put(pkg, true);
                return true;
            }
            String installer = null;
            try {
                installer = getPackageManager().getInstallerPackageName(pkg);
            } catch (Exception ignored) {
            }
            boolean oemPreloaded = (installer == null);
            systemNavPackageCache.put(pkg, oemPreloaded);
            return oemPreloaded;
        } catch (PackageManager.NameNotFoundException e) {
            systemNavPackageCache.put(pkg, false);
            return false;
        }
    }

    private static boolean isNameMatches(String pkg) {
        String lower = pkg.toLowerCase();
        return lower.contains("gesture")
                || lower.contains("sidepad")
                || lower.contains("edgepad")
                || lower.contains("edgetouch")
                || lower.contains("edgepanel")
                || lower.contains("navigationbar")
                || lower.contains("navgesture")
                || lower.contains("navbar")
                || lower.contains("gesturepad")
                || lower.contains("quickstep");
    }

    // Admin Guard

    private static boolean isSettingsPackage(String pkg) {
        if (pkg == null)
            return false;
        switch (pkg) {
            case "com.android.settings", "com.samsung.android.settings", "com.miui.settings" -> {
                return true;
            }
            case "com.android.settings.applications" -> {
                return true; // AOSP/Samsung App Info
            }
        }
        if (pkg.startsWith("com.oneplus.settings") || pkg.startsWith("com.oplus.settings"))
            return true;
        if (pkg.startsWith("com.coloros.settings") || pkg.startsWith("com.realme.settings"))
            return true;
        if (pkg.startsWith("com.vivo.settings"))
            return true;
        if (pkg.startsWith("com.motorola.settings"))
            return true;
        if (pkg.startsWith("com.lge.settings"))
            return true;
        if (pkg.startsWith("com.huawei.settings") || pkg.startsWith("com.android.hwsettings"))
            return true;
        return pkg.startsWith("com.") && pkg.endsWith(".settings");
    }

    private boolean isOurAppInfoPageOpen(AccessibilityNodeInfo root, String pkg) {
        String appName = getString(R.string.app_name);
        if (viewIdMatchesAppName(root, pkg + ":id/entity_header_title", appName))
            return true;
        if (viewIdMatchesAppName(root, pkg + ":id/title", appName))
            return true;
        if (viewIdMatchesAppName(root, "com.android.settings:id/admin_name", appName))
            return true;
        return textScanFindsAppInfo(root, appName, 6);
    }

    private boolean viewIdMatchesAppName(AccessibilityNodeInfo root, String viewId, String appName) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes == null || nodes.isEmpty())
            return false;
        boolean found = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null) {
                CharSequence text = node.getText();
                if (!found && text != null && text.toString().trim().equals(appName))
                    found = true;
                node.recycle();
            }
        }
        return found;
    }

    private boolean textScanFindsAppInfo(AccessibilityNodeInfo node, String appName, int depth) {
        if (node == null || depth < 0)
            return false;
        CharSequence text = node.getText();
        if (text != null) {
            String t = text.toString().trim();
            if (t.equalsIgnoreCase(appName)
                    || t.equalsIgnoreCase("force stop")
                    || t.equalsIgnoreCase("uninstall")
                    || t.equalsIgnoreCase("device admin")
                    || t.equalsIgnoreCase("deactivate"))
                return true;
        }
        CharSequence cd = node.getContentDescription();
        if (cd != null && cd.toString().trim().equalsIgnoreCase(appName))
            return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (textScanFindsAppInfo(child, appName, depth - 1))
                    return true;
            } catch (Exception ignored) {
            } finally {
                if (child != null)
                    child.recycle();
            }
        }
        return false;
    }

    private void handleAdminProtectionGuard(String eventPackageName) {
        if (!isSettingsPackage(eventPackageName))
            return;

        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        if (dpm == null)
            return;
        android.content.ComponentName adminComponent = new android.content.ComponentName(this,
                com.gxdevs.mindmint.Receivers.MindMintDeviceAdminReceiver.class);
        if (!dpm.isAdminActive(adminComponent))
            return;

        long tokenTimestamp = sharedPreferences.getLong(PREF_ADMIN_GUARD_TRUSTED_TOKEN, 0L);
        if (tokenTimestamp > 0L
                && (System.currentTimeMillis() - tokenTimestamp) < ADMIN_GUARD_TOKEN_VALIDITY_MS) {
            return; // legit in-app navigation, let it through
        }

        long now = System.currentTimeMillis();
        if ((now - lastAdminGuardHomeTimeMs) < ADMIN_GUARD_COOLDOWN_MS)
            return;

        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null)
                return;
            if (isOurAppInfoPageOpen(root, eventPackageName)
                    || isDeviceAdminPageOpen(root)) {
                Log.d(TAG, "AdminGuard: blocked settings page in " + eventPackageName);
                lastAdminGuardHomeTimeMs = now;

                // Launch the same BlockingOverlayDisplayActivity used for reel/app blocking.
                // The Activity handles HOME itself via ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY
                // broadcast after its popup timer — no manual HOME call needed here.
                Intent overlay = new Intent(this, BlockingOverlayDisplayActivity.class);
                overlay.putExtra(EXTRA_BLOCKED_APP_NAME, getString(R.string.app_name));
                overlay.putExtra(EXTRA_CUSTOM_SUBTITLE,
                        "Access denied. Disable Prevent Uninstall from Mind Mint settings first.");
                overlay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                try {
                    startActivity(overlay);
                } catch (Exception e) {
                    Log.e(TAG, "AdminGuard overlay launch failed", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "AdminGuard error", e);
        } finally {
            if (root != null)
                root.recycle();
        }
    }

    /** True when the current screen looks like the Device Admin management page. */
    private boolean isDeviceAdminPageOpen(AccessibilityNodeInfo root) {
        if (root == null)
            return false;
        // The Device Admin page shows a button with text "Deactivate" — this is the
        // clearest unique signal that the user is trying to revoke admin rights.
        return nodeTreeContainsText(root, "Deactivate", 8)
                || nodeTreeContainsText(root, "Remove device admin app", 8);
    }

    private boolean nodeTreeContainsText(AccessibilityNodeInfo node, String target, int depth) {
        if (node == null || depth < 0)
            return false;
        CharSequence text = node.getText();
        if (text != null && text.toString().trim().equalsIgnoreCase(target))
            return true;
        CharSequence cd = node.getContentDescription();
        if (cd != null && cd.toString().trim().equalsIgnoreCase(target))
            return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (nodeTreeContainsText(child, target, depth - 1))
                    return true;
            } catch (Exception ignored) {
            } finally {
                if (child != null)
                    child.recycle();
            }
        }
        return false;
    }

    private void handleBackPress(String currentEventPackageName, AccessibilityNodeInfo rootNode,
            AccessibilityEvent event) {
        if (rootNode == null)
            return;

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
            findAndBlockView(rootNode, viewIdForBackPress, currentEventPackageName);
        }
    }

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
                Long originalDelayValue = appTagToOriginalDelayMs.get(appTag);
                long originalDelay = originalDelayValue != null ? originalDelayValue : 0L;
                Long postTimeValue = appTagToLastPostTimeMs.get(appTag);
                long postTime = postTimeValue != null ? postTimeValue : System.currentTimeMillis();
                long elapsed = System.currentTimeMillis() - postTime;
                long timeLeft = Math.max(0, originalDelay - elapsed);
                appTagToTimeLeftForNextSessionMs.put(appTag, timeLeft);
                return;
            }

            // --- PeaceCoins logic: track ignored reminders and subtract coins after 3rd
            // ---
            incrementReminderIgnoredCount(appTag);
            int ignoredCount = getReminderIgnoredCount(appTag);
            if (ignoredCount > 5) {
                try {
                    MintCrystals mintCrystals = new MintCrystals(getApplicationContext());
                    mintCrystals.subtractCoins(2);
                } catch (Exception e) {
                    Log.e(TAG, "PeaceCoins: Exception while subtracting coins for appTag " + appTag, e);
                }
            }
            // --- End PeaceCoins logic ---

            long userReminderIntervalMs = (long) remindDoomScrollingMinutes * 60 * 1000;
            Long lastActualReminderShownTimeValue = lastReminderTimestampForAppTag.get(appTag);
            long lastActualReminderShownTimeMs = lastActualReminderShownTimeValue != null
                    ? lastActualReminderShownTimeValue
                    : 0L;

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

        reminderViewAccumulatedTimeCurrentCycleMs.putIfAbsent(appTag, 0L);
        reminderViewSessionStartTimeMs.putIfAbsent(appTag, System.currentTimeMillis());

        ShowReminderRunnable existingRunnable = activeRunnables.get(appTag);
        if (existingRunnable != null) {
            currentAppTagForReminderViewTracking = appTag;
            return;
        }

        long userReminderIntervalMs = (long) remindDoomScrollingMinutes * 60 * 1000;
        Long timeToDelayValue = appTagToTimeLeftForNextSessionMs.get(appTag);
        long timeToDelayMs = timeToDelayValue != null ? timeToDelayValue : userReminderIntervalMs;

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
        if (appTagToEnd == null)
            return;
        ShowReminderRunnable activeRunnable = activeRunnables.remove(appTagToEnd);
        if (activeRunnable == null) {
            if (appTagToEnd.equals(currentAppTagForReminderViewTracking)) {
                currentAppTagForReminderViewTracking = null;
            }
            return;
        }

        reminderHandler.removeCallbacks(activeRunnable);

        Long sessionStartTimeValue = reminderViewSessionStartTimeMs.remove(appTagToEnd);
        if (sessionStartTimeValue != null) {
            long sessionDuration = System.currentTimeMillis() - sessionStartTimeValue;
            if (sessionDuration > 0) {
                Long accumulatedValue = reminderViewAccumulatedTimeCurrentCycleMs.get(appTagToEnd);
                long accumulatedTime = accumulatedValue != null ? accumulatedValue : 0L;
                reminderViewAccumulatedTimeCurrentCycleMs.put(appTagToEnd, accumulatedTime + sessionDuration);
            }
        }

        Long originalDelayValue = appTagToOriginalDelayMs.get(appTagToEnd);
        long originalDelay = originalDelayValue != null ? originalDelayValue : 0L;
        Long lastPostTimeValue = appTagToLastPostTimeMs.get(appTagToEnd);
        long lastPostTime = lastPostTimeValue != null ? lastPostTimeValue : System.currentTimeMillis();
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
        if (appTag == null)
            return;

        ShowReminderRunnable activeRunnable = activeRunnables.remove(appTag);
        if (activeRunnable != null) {
            reminderHandler.removeCallbacks(activeRunnable);
        }

        long userReminderIntervalMs = (long) sharedPreferences.getInt(PREF_REMIND_DOOM_SCROLLING_MINUTES,
                DEFAULT_REMIND_DOOM_SCROLLING_MINUTES) * 60 * 1000;
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
            return;
        }
        lastReminderTimestampForAppTag.put(appTag, timestamp);
        sharedPreferences.edit().putLong(PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX + appTag, timestamp).apply();
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
    }

    private String getReminderViewIdForAppTag(String appTag) {
        if (appTag == null)
            return null;
        return switch (appTag) {
            case "yt" -> Utils.YtViewId;
            case "insta" -> Utils.instaViewId;
            case "snap" -> Utils.snapViewId;
            default -> null;
        };
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
                if (node != null)
                    node.recycle();
            }
        }
        return visible;
    }

    private boolean isCommentSectionVisible(AccessibilityNodeInfo rootNode, String packageName) {
        if (rootNode == null || packageName == null) return false;
        
        String[] commonCommentIds = {
            "layout_comment_thread_edittext",
            "comment_input_edit_text",
            "comment_edittext",
            "comment_text",
            "layout_comment_thread_text",
            "comment_box"
        };
        for (String id : commonCommentIds) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(packageName + ":id/" + id);
            if (nodes != null && !nodes.isEmpty()) {
                boolean visible = false;
                for (AccessibilityNodeInfo node : nodes) {
                    if (node != null) {
                        if (node.isVisibleToUser()) {
                            visible = true;
                        }
                        node.recycle();
                    }
                }
                if (visible) return true;
            }
        }
        
        return scanForCommentInputRecursive(rootNode);
    }

    private boolean scanForCommentInputRecursive(AccessibilityNodeInfo node) {
        if (node == null) return false;
        
        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        boolean isInput = className.contains("EditText") || className.contains("AutoCompleteTextView");
        
        if (isInput && node.isVisibleToUser()) {
            CharSequence text = node.getText();
            if (text != null && text.toString().toLowerCase().contains("comment")) return true;
            
            CharSequence hint = node.getHintText();
            if (hint != null && hint.toString().toLowerCase().contains("comment")) return true;
            
            CharSequence contentDesc = node.getContentDescription();
            if (contentDesc != null && contentDesc.toString().toLowerCase().contains("comment")) return true;
            
            String viewId = node.getViewIdResourceName();
            if (viewId != null && viewId.toLowerCase().contains("comment")) return true;
        }
        
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean found = scanForCommentInputRecursive(child);
                child.recycle();
                if (found) return true;
            }
        }
        return false;
    }

    private void handleBlockers(String currentEventPackageName, AccessibilityEvent event) { // This handles
        // "blockAllFeatures" view
        // ID blocking
        boolean blockersGloballyEnabled = sharedPreferences.getBoolean("blockAllFeatures", false);
        AccessibilityNodeInfo src = event.getSource();
        boolean hasSource = src != null;
        if (src != null)
            src.recycle();
        if (!blockersGloballyEnabled || !hasSource) {
            return;
        }

        String appTag = getAppTagFromAllPackages(currentEventPackageName);
        String viewIdToBlock = appTag != null ? getReminderViewIdForAppTag(appTag) : null;

        if (viewIdToBlock != null) {
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
        if (isPackageBypassed(packageName)) {
            return;
        }
        Intent intent = buildBlockingIntent(packageName, false);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "EXCEPTION starting blocking screen for " + packageName, e);
        }
    }

    /**
     * Returns the correct blocking intent for a given package:
     *  - "none"   → standard BlockingOverlayDisplayActivity (auto-close + HOME)
     *  - "oneday" → BlockingOverlayDisplayActivity (24-hour countdown)
     *  - others   → LockChallengeActivity launched directly (math / scream / breath
     *               / text / shake / window10). On success the challenge broadcasts
     *               APP_BYPASS_GRANTED; on cancel it dispatches HOME.
     */
    private Intent buildBlockingIntent(String packageName, boolean isFocus) {
        String challengeType = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(com.gxdevs.mindmint.Utils.ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
        return buildBlockingIntent(packageName, challengeType, isFocus);
    }

    private Intent buildBlockingIntent(String packageName, String challengeType, boolean isFocus) {
        String appName = getAppNameFromPackageManager(packageName);
        BlockedAppEntity config = getBlockedAppConfig(packageName);
        String blockScope = (config != null) ? config.scope : "full";

        Intent intent;
        if ("none".equals(challengeType) || "oneday".equals(challengeType)) {
            // Overlay-based blocking (auto-close or countdown)
            intent = new Intent(this, BlockingOverlayDisplayActivity.class);
            intent.putExtra(EXTRA_IS_FOCUS, isFocus);
            intent.putExtra(EXTRA_BLOCKED_PACKAGE_NAME, packageName);
            intent.putExtra(EXTRA_BLOCKED_APP_NAME, appName != null ? appName : packageName);
            intent.putExtra("extra_challenge_type", challengeType);
            intent.putExtra("extra_block_scope", blockScope);
        } else {
            // Challenge-based: launch LockChallengeActivity directly so the user
            // sees the challenge screen immediately without an intermediate overlay.
            intent = new Intent(this, com.gxdevs.mindmint.Activities.LockChallengeActivity.class);
            intent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_LOCK_TYPE, challengeType);
            intent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_TARGET_PACKAGE, packageName);
            intent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_IS_SETTINGS_LOCK, false);
            intent.putExtra("extra_block_scope", blockScope);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private boolean isReminderEnabledForCurrentIntensity() {
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        }
        int intensity = sharedPreferences.getInt(com.gxdevs.mindmint.Utils.ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0);
        if (intensity == 2) {
            return true;
        }
        if (intensity == 3) {
            return sharedPreferences.getBoolean("pref_temp_lock_reminders_enabled", false);
        }
        return false;
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

        remindDoomScrollingEnabled = isReminderEnabledForCurrentIntensity();
        remindDoomScrollingMinutes = sharedPreferences.getInt(PREF_REMIND_DOOM_SCROLLING_MINUTES,
                DEFAULT_REMIND_DOOM_SCROLLING_MINUTES);
        blockAfterWastedTimeEnabled = sharedPreferences.getBoolean(PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
        blockAfterWastedTimeHours = sharedPreferences.getFloat(PREF_BLOCK_AFTER_WASTED_TIME_HOURS,
                DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS);
        blockBrowsersDoomEnabled = sharedPreferences.getBoolean(PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, false);
        blockAdultSitesEnabled = sharedPreferences.getBoolean(PREF_BLOCK_ADULT_SITES_ENABLED, false);

        scrollCounterEnabled = sharedPreferences.getBoolean("pref_scroll_counter_enabled", false);
        scrollCounterPerApp = sharedPreferences.getBoolean("pref_scroll_counter_per_app", false);

        long currentUserReminderIntervalMs = (long) remindDoomScrollingMinutes * 60 * 1000;
        boolean settingsChanged = oldRemindDoomScrollingEnabled != remindDoomScrollingEnabled
                || oldRemindDoomScrollingMinutes != remindDoomScrollingMinutes;

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
        }

        if (!remindDoomScrollingEnabled && !blockAfterWastedTimeEnabled) {
            currentViewFocusSessionStartTimeMap.clear();
            appViewFocusAccumulatedTimeTodayMap.clear();
            currentViewIdPackage = null;
        }
    }

    private void tryBlockBrowser(String packageName, AccessibilityNodeInfo rootNode) {
        if (packageName == null || rootNode == null)
            return;
        boolean isKnownBrowser = Utils.BROWSERS_PACKAGES.containsKey(packageName);
        if (!isKnownBrowser && !isBrowserPackage(packageName, this))
            return;

        if (isFirefox(packageName)) {
            String foundText = findUrlOrDomainFromNodeTreeLimited(rootNode);
            if (foundText == null || foundText.isEmpty()) {
                return;
            }
            String lowerText = foundText.toLowerCase();
            String host = AdultDomainListManager.extractHostFromUrlText(lowerText);
            if (host == null || !host.contains(".")) {
                return;
            }

            Set<String> candidateTexts = new HashSet<>();
            candidateTexts.add(lowerText);
            Set<String> candidateHosts = new HashSet<>();
            candidateHosts.add(host);

            if (blockBrowsersDoomEnabled) {
                if (isBlockedByUserLists(candidateTexts, candidateHosts)) {
                    performThrottledGlobalAction(GLOBAL_ACTION_BACK);
                    launchOverlay(packageName);
                    resetUsageAndTimersForPackage(packageName);
                    return;
                }
            }
            if (blockAdultSitesEnabled) {
                boolean isBlocked = AdultDomainListManager.isAdultHost(this, host);
                if (isBlocked) {
                    performThrottledGlobalAction(GLOBAL_ACTION_BACK);
                    launchOverlay(packageName);
                    resetUsageAndTimersForPackage(packageName);
                    return;
                }
            }
            // If not blocked, do nothing further for Firefox
            return;
        }

        Pair<String, Boolean> result = tryGetUrlBarTextWithRetry(packageName, rootNode);
        String urlText = result.first;

        if (urlText == null || urlText.isEmpty())
            return;

        String lowerText = urlText.toLowerCase();
        String host = AdultDomainListManager.extractHostFromUrlText(lowerText);
        Set<String> candidateTexts = new HashSet<>();
        candidateTexts.add(lowerText);
        Set<String> candidateHosts = new HashSet<>();
        if (host != null && host.contains("."))
            candidateHosts.add(host);

        // 1) User-defined blocking (no doom fallback)
        if (blockBrowsersDoomEnabled) {
            if (isEdgeOrSamsung(packageName)) {
                if (isBlockedByUserListsEdgeSamsung(candidateTexts, candidateHosts)) {
                    performThrottledGlobalAction(GLOBAL_ACTION_BACK);
                    launchOverlay(packageName);
                    resetUsageAndTimersForPackage(packageName);
                    return;
                }
            } else {
                if (isBlockedByUserLists(candidateTexts, candidateHosts)) {
                    performThrottledGlobalAction(GLOBAL_ACTION_BACK);
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
                    performThrottledGlobalAction(GLOBAL_ACTION_BACK);
                    launchOverlay(packageName);
                    resetUsageAndTimersForPackage(packageName);
                    return;
                }
            }
        }
    }

    private boolean isFirefox(String packageName) {
        if (packageName == null)
            return false;
        if (packageName.startsWith("org.mozilla.firefox"))
            return true;
        if ("org.mozilla.firefox_beta".equals(packageName))
            return true;
        return "org.mozilla.fenix".equals(packageName); // Fenix-based Firefox
    }

    @Nullable
    private String findUrlOrDomainFromNodeTreeLimited(AccessibilityNodeInfo root) {
        if (root == null)
            return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        int visited = 0;

        while (!queue.isEmpty() && visited < 50) {
            AccessibilityNodeInfo node = queue.pollFirst();
            if (node == null)
                continue;
            visited++;

            // Examine text-like fields
            CharSequence t = node.getText();
            if (t != null) {
                String s = t.toString();
                String host = AdultDomainListManager.extractHostFromUrlText(s.toLowerCase());
                if (host != null && host.contains(".")) {
                    node.recycle();
                    return s;
                }
            }
            CharSequence cd = node.getContentDescription();
            if (cd != null) {
                String s = cd.toString();
                String host = AdultDomainListManager.extractHostFromUrlText(s.toLowerCase());
                if (host != null && host.contains(".")) {
                    node.recycle();
                    return s;
                }
            }

            // Traverse children
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            }
            node.recycle();
        }

        return null;
    }

    private boolean isBrowserPackage(String packageName, Context context) {
        Boolean cached = browserCheckCache.get(packageName);
        if (cached != null)
            return cached;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        boolean isBrowser = false;
        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo.packageName.equals(packageName)) {
                isBrowser = true;
                break;
            }
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
                    if (n == null)
                        continue;
                    CharSequence t = n.getText();
                    if (t != null && !t.toString().isEmpty()) {
                        foundText = t.toString();
                        break;
                    }
                }
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null)
                        n.recycle();
                }
                if (foundText != null)
                    return new Pair<>(foundText, true);
            }
        }
        for (String candidateId : Utils.browserIds) {
            List<AccessibilityNodeInfo> nodes = rootNode
                    .findAccessibilityNodeInfosByViewId(packageName + ":id/" + candidateId);
            if (nodes == null || nodes.isEmpty())
                continue;
            nodeFound = true;
            String foundText = null;
            for (AccessibilityNodeInfo n : nodes) {
                if (n == null)
                    continue;
                CharSequence t = n.getText();
                if (t != null && !t.toString().isEmpty()) {
                    foundText = t.toString();
                    break;
                }
            }
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null)
                    n.recycle();
            }
            if (foundText != null)
                return new Pair<>(foundText, true);
        }
        if (nodeFound)
            return new Pair<>("", true);
        return new Pair<>(null, false);
    }

    private Pair<String, Boolean> tryGetUrlBarTextWithRetry(String packageName, AccessibilityNodeInfo rootNode) {
        final int maxRetries = 3;
        final int delayMs = 300;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return tryGetUrlBarText(packageName, rootNode);
        }

        // Only retry if we're on a background thread (shouldn't happen in normal flow)
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Pair<String, Boolean> result = tryGetUrlBarText(packageName, rootNode);
            if (result.second) {
                return result;
            }
            // Double-check we're not on main thread before sleeping
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return result; // Exit immediately if somehow on main thread
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                Log.d("Sleep", "Interrupted during retry", e);
                return result; // Return current result on interrupt
            }
        }

        // No node found after all retries
        return new Pair<>(null, false);
    }

    private boolean isEdgeOrSamsung(String packageName) {
        if (packageName == null)
            return false;
        if (packageName.startsWith("com.microsoft.emmx"))
            return true;
        return "com.sec.android.app.sbrowser".equals(packageName);
    }

    private boolean isBlockedByUserListsEdgeSamsung(Set<String> candidateTextsLower, Set<String> candidateHosts) {
        Set<String> domains = BlockedSitesManager.getBlockedDomains(this);
        Set<String> exacts = BlockedSitesManager.getBlockedExactUrls(this);

        for (String d : domains) {
            if (d == null)
                continue;
            String dl = d.toLowerCase();
            boolean domainLike = dl.contains(".");
            if (domainLike) {
                if (candidateHosts != null) {
                    for (String host : candidateHosts) {
                        if (host.equals(dl) || host.endsWith("." + dl)) {
                            return true;
                        }
                    }
                }
            } else {
                if (candidateTextsLower != null) {
                    for (String ct : candidateTextsLower) {
                        if (ct.contains(dl)) {
                            return true;
                        }
                    }
                }
            }
        }

        for (String e : exacts) {
            if (e == null)
                continue;
            HostPath exactHp = parseHostPath(e);
            if (exactHp == null || exactHp.host == null)
                continue;
            if (candidateHosts != null) {
                for (String host : candidateHosts) {
                    if (host.equals(exactHp.host) || host.endsWith("." + exactHp.host)) {
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
            if (d == null)
                continue;
            String dl = d.toLowerCase();
            boolean domainLike = dl.contains(".");
            if (domainLike) {
                // Require host and check suffix match
                if (candidateHosts != null) {
                    for (String host : candidateHosts) {
                        if (host.equals(dl) || host.endsWith("." + dl)) {
                            return true;
                        }
                    }
                }
            } else {
                // Brand keyword match limited to URL bar/title strings only
                if (candidateTextsLower != null) {
                    for (String ct : candidateTextsLower) {
                        if (ct.contains(dl)) {
                            return true;
                        }
                    }
                }
            }
        }

        // Exact URL entries (host + path prefix match)
        for (String e : exacts) {
            if (e == null)
                continue;
            HostPath exactHp = parseHostPath(e);
            if (exactHp == null || exactHp.host == null)
                continue;
            if (candidateTextsLower != null) {
                for (String ct : candidateTextsLower) {
                    HostPath candHp = parseHostPath(ct);
                    if (candHp == null || candHp.host == null)
                        continue;
                    if (candHp.host.equals(exactHp.host) || candHp.host.endsWith("." + exactHp.host)) {
                        if (candHp.path.startsWith(exactHp.path)) {
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
        if (text == null || text.isEmpty())
            return null;
        String host = AdultDomainListManager.extractHostFromUrlText(text);
        if (host == null || !host.contains("."))
            return null;
        String s = text.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        try {
            java.net.URL u = new java.net.URL(s);
            String h = u.getHost().toLowerCase();
            if (h.startsWith("www."))
                h = h.substring(4);
            String p = u.getPath();
            if (p == null || p.isEmpty())
                p = "/";
            if (p.length() > 1 && p.endsWith("/"))
                p = p.substring(0, p.length() - 1);
            HostPath hp = new HostPath();
            hp.host = h;
            hp.path = p;
            return hp;
        } catch (Exception ignore) {
            String p = "/";
            int slash = s.indexOf('/');
            if (slash >= 0)
                p = s.substring(slash);
            if (p.length() > 1 && p.endsWith("/"))
                p = p.substring(0, p.length() - 1);
            HostPath hp = new HostPath();
            hp.host = host;
            hp.path = p;
            return hp;
        }
    }

    private void logNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null)
            return;
        String indent = new String(new char[depth]).replace("\0", "-");
        Log.d("NodeDump", indent + node.getViewIdResourceName() + " | " + node.getText());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            logNodeTree(child, depth + 1);
            if (child != null)
                child.recycle();
        }
    }

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
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY,
                pendingIntent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private void clearCurrentPackageTracking() {
        if (currentPackage != null) {
            Log.d(TAG, "Clearing general app usage tracking for '" + currentPackage + "'.");
        }
        currentPackage = null;
        startTimeMillis = 0L;
    }

    private void resetUsageAndTimersForPackage(String packageName) {
        if (packageName == null)
            return;

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
        if (timeSpentSeconds <= 0)
            return;
        int currentTime = sharedPreferences.getInt(packageName + "_time", 0);
        int newTotalTime = currentTime + timeSpentSeconds;
        sharedPreferences.edit().putInt(packageName + "_time", newTotalTime).apply();
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
            if (key.startsWith(PREF_APP_VIEW_ACCUMULATED_TIME_MS_PREFIX) || // Old view focus
                    key.startsWith(PREF_LAST_VIEW_REMINDER_TIMESTAMP_PREFIX) || // Old view focus
                    key.startsWith(PREF_LAST_REMINDER_TIMESTAMP_APP_TAG_PREFIX) || // New reminder cooldowns
                    key.startsWith(PREF_REMINDER_VIEW_ACCUMULATED_TIME_CYCLE_MS_APP_TAG_PREFIX)) { // Old reminder
                // system
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
            appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA))
                    .toString();
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
        if (appTag == null)
            return null;
        for (Map.Entry<String, String> entry : Utils.ALL_PACKAGES.entrySet()) {
            if (appTag.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getAppNameForTag(String tag) { // Helper for reminder/overlay UI
        return switch (tag) {
            case "yt" -> "YouTube";
            case "insta" -> "Instagram";
            case "snap" -> "Snapchat";
            default -> "the app";
        };
    }

    private void findAndBlockView(AccessibilityNodeInfo rootNode, String targetViewIdName, String packageName) {
        if (rootNode == null || targetViewIdName == null || packageName == null) {
            if (rootNode != null)
                rootNode.recycle();
            return;
        }
        if (isPackageBypassed(packageName)) {
            rootNode.recycle();
            return;
        }
        List<AccessibilityNodeInfo> nodes = rootNode
                .findAccessibilityNodeInfosByViewId(packageName + ":id/" + targetViewIdName);
        if (nodes != null && !nodes.isEmpty()) {
            boolean actionPerformed = false;
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null && node.isVisibleToUser() && !actionPerformed) {
                    performThrottledGlobalAction(GLOBAL_ACTION_BACK);
                    actionPerformed = true;
                }
                if (node != null)
                    node.recycle();
            }
        }
    }

    private void performThrottledGlobalAction(int action) {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < 800)
            return;
        lastActionTime = now;
        performGlobalAction(action);
    }

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
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (String appTag : Utils.ALL_PACKAGES.values()) {
            editor.putInt(PREF_REMINDER_IGNORED_COUNT_PREFIX + appTag, 0);
        }
        editor.apply();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        serviceStatus = false;
        return super.onUnbind(intent);
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel serviceChannel = new NotificationChannel(
                FGS_CHANNEL_ID,
                "Mind Mint Service",
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(serviceChannel);
    }

    private void showScrollCounterPill(String appTag, String packageName) {
        if (!scrollCounterEnabled || !serviceStatus) {
            hideScrollCounterPill(true);
            return;
        }

        scrollPillHandler.removeCallbacks(hideScrollPillRunnable);

        if (windowManager == null) {
            windowManager = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }

        if (scrollPillOverlayView == null) {
            android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                    android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);

            params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;

            scrollPillOverlayView = android.view.LayoutInflater.from(this).inflate(R.layout.overlay_scroll_pill, null);
            pillScrollCountText = scrollPillOverlayView.findViewById(R.id.pillScrollCount);
            pillAppIconImage = scrollPillOverlayView.findViewById(R.id.pillAppIcon);

            windowManager.addView(scrollPillOverlayView, params);
        }

        isScrollCounterPillVisible = true;
        lastSeenPillAppTag = appTag;
        lastSeenPillPackageName = packageName;
        updateScrollCounterPillInternal(appTag);

        // animate alpha if invisible
        android.view.View pillContainer = scrollPillOverlayView.findViewById(R.id.pillContainer);
        if (pillContainer != null) {
            pillContainer.animate().cancel();
            if (pillContainer.getAlpha() < 1f) {
                pillContainer.animate().alpha(1f).setDuration(250).start();
            }
        }
    }

    private void updateScrollCounterPillInternal(String appTag) {
        if (scrollPillOverlayView == null || pillScrollCountText == null)
            return;

        long scrollCount = 0;
        if (scrollCounterPerApp) {
            scrollCount = Utils.calculateTotalUsageScrolls(sharedPreferences, appTag);
        } else {
            for (String pkg : Utils.ALL_PACKAGES.keySet()) {
                scrollCount += sharedPreferences.getLong(pkg + "_scrolls", 0L);
            }
        }

        pillScrollCountText.setText(scrollCount + " scrolls");

        if (pillAppIconImage != null) {
            pillAppIconImage.setImageResource(getDrawableId((int) scrollCount));
        }
    }

    private static int getDrawableId(int totalWastedScrolls) {
        if (totalWastedScrolls < 150)
            return R.drawable.brain1;
        if (totalWastedScrolls < 300)
            return R.drawable.brain2;
        if (totalWastedScrolls < 500)
            return R.drawable.brain3;
        if (totalWastedScrolls < 700)
            return R.drawable.brain4;
        if (totalWastedScrolls < 900)
            return R.drawable.brain5;
        if (totalWastedScrolls < 1100)
            return R.drawable.brain6;
        if (totalWastedScrolls < 1200)
            return R.drawable.brain7;
        if (totalWastedScrolls < 1400)
            return R.drawable.brain8;
        return R.drawable.brain9;
    }

    private void hideScrollCounterPill(boolean immediate) {
        scrollPillHandler.removeCallbacks(hideScrollPillRunnable);
        if (immediate) {
            hideScrollPillRunnable.run();
        } else {
            scrollPillHandler.postDelayed(hideScrollPillRunnable, 1000); // 1-second debounce
        }
    }

    private BlockedAppEntity getBlockedAppConfig(String packageName) {
        if (packageName == null) return null;
        synchronized (cachedBlockedApps) {
            for (BlockedAppEntity app : cachedBlockedApps) {
                if (app.packageName.equals(packageName)) {
                    return app;
                }
            }
        }
        return null;
    }

    private boolean isDailyLimitExceeded(String packageName) {
        String limitType = sharedPreferences.getString("pref_temp_lock_limit_type", "both");

        if ("time".equals(limitType) || "both".equals(limitType)) {
            if (blockAfterWastedTimeEnabled) {
                long currentGlobalDailyWastedTimeMs = 0;
                for (long timeInSeconds : appTotalWastedTimeToday.values()) {
                    currentGlobalDailyWastedTimeMs += (timeInSeconds * 1000);
                }
                long globalBlockThresholdMs = (long) (blockAfterWastedTimeHours * 3600 * 1000);
                if (currentGlobalDailyWastedTimeMs >= globalBlockThresholdMs) {
                    return true;
                }
            }
        }

        if ("scroll".equals(limitType) || "both".equals(limitType)) {
            long scrollLimit = sharedPreferences.getLong("pref_daily_scroll_limit", 100);
            long currentScrolls = sharedPreferences.getLong(packageName + "_scrolls", 0L);
            if (currentScrolls >= scrollLimit) {
                return true;
            }
        }

        return false;
    }

    private void triggerStrictLockout(String packageName) {
        long now = System.currentTimeMillis();
        Long lastTime = lastLockInOverlayTimeMs.get(packageName);
        if (lastTime == null || (now - lastTime) > LOCK_IN_OVERLAY_DEBOUNCE_MS) {
            lastLockInOverlayTimeMs.put(packageName, now);
            Intent intent = buildBlockingIntent(packageName, "oneday", false);
            startActivity(intent);
            resetUsageAndTimersForPackage(packageName);
        }
    }

    private void triggerFrictionChallenge(String packageName) {
        long now = System.currentTimeMillis();
        Long lastTime = lastLockInOverlayTimeMs.get(packageName);
        if (lastTime == null || (now - lastTime) > LOCK_IN_OVERLAY_DEBOUNCE_MS) {
            lastLockInOverlayTimeMs.put(packageName, now);
            String challengeType = sharedPreferences.getString(com.gxdevs.mindmint.Utils.ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math");
            if ("none".equals(challengeType) || "oneday".equals(challengeType)) {
                challengeType = "math";
            }
            Intent intent = buildBlockingIntent(packageName, challengeType, false);
            startActivity(intent);
            resetUsageAndTimersForPackage(packageName);
        }
    }

    private void manageFrictionSessions(String activePackageName) {
        if (activePackageName == null) return;
        
        boolean isLauncher = dynamicLauncherPackages.contains(activePackageName) || activePackageName.toLowerCase().contains("launcher");
        boolean isEssential = isEssentialPackage(activePackageName) || LOCKED_IN_ESSENTIAL_WHITELIST.contains(activePackageName);
        boolean isOurApp = activePackageName.equals("com.gxdevs.mindmint") || activePackageName.equals(getPackageName());
        
        if (isLauncher || (!isEssential && !isOurApp)) {
            synchronized (cachedBlockedApps) {
                for (BlockedAppEntity app : cachedBlockedApps) {
                    if (!app.packageName.equals(activePackageName)) {
                        packageBypassExpiryTimeMap.remove(app.packageName);
                    }
                }
            }
        }
    }

    private boolean isEssentialPackageOrLauncher(String packageName) {
        if (packageName == null) return false;
        if (dynamicLauncherPackages.contains(packageName)) return true;
        if (LOCKED_IN_ESSENTIAL_WHITELIST.contains(packageName)) return true;
        return isEssentialPackage(packageName);
    }

    private void checkBlockerPipeline(String eventPackageName, AccessibilityNodeInfo rootNode) {
        if (eventPackageName == null) return;

        if (System.currentTimeMillis() - lastGoHomeTimeMs < 1500) {
            Log.d(TAG, "checkBlockerPipeline suppressed for package '" + eventPackageName + "' due to recent cancel/go home transition.");
            return;
        }

        boolean isLockedInPref = sharedPreferences.getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
        boolean isFocusRunning = FocusService.isPublicFocusRun || isLockedInPref;
        boolean isLockedInActive = isFocusRunning && isLockedInPref;
        if (isLockedInActive) {
            return;
        }

        BlockedAppEntity config = getBlockedAppConfig(eventPackageName);
        if (config == null || !config.isRestricted) {
            return;
        }

        boolean isTriggered = false;
        if ("full".equals(config.scope)) {
            isTriggered = true;
        } else if ("section".equals(config.scope)) {
            if (config.sectionViewId != null && !config.sectionViewId.trim().isEmpty()) {
                isTriggered = isReminderViewVisible(rootNode, eventPackageName, config.sectionViewId);
            }
        }

        if ("section".equals(config.scope)) {
            if (isTriggered) {
                lastTimeSectionVisibleMap.put(eventPackageName, System.currentTimeMillis());
            } else {
                Long lastTimeVisible = lastTimeSectionVisibleMap.get(eventPackageName);
                if (lastTimeVisible != null && (System.currentTimeMillis() - lastTimeVisible > 3000)) {
                    if (isPackageBypassed(eventPackageName)) {
                        packageBypassExpiryTimeMap.remove(eventPackageName);
                        Log.d(TAG, "Section invisible for > 3s. Cleared bypass for: " + eventPackageName);
                    }
                    lastTimeSectionVisibleMap.remove(eventPackageName);
                }
            }
        }

        if (!isTriggered) {
            return;
        }

        int intensity = sharedPreferences.getInt(com.gxdevs.mindmint.Utils.ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0);
        if (intensity == 0) {
            return;
        }

        if (intensity == 4) {
            triggerStrictLockout(eventPackageName);
            return;
        }

        if (intensity == 3) {
            boolean limitExceeded = isDailyLimitExceeded(eventPackageName);
            if (limitExceeded) {
                triggerStrictLockout(eventPackageName);
            } else {
                boolean hybridEnabled = sharedPreferences.getBoolean("pref_temp_lock_friction_enabled", false);
                if (hybridEnabled) {
                    if (isPackageBypassed(eventPackageName)) {
                        return;
                    }
                    triggerFrictionChallenge(eventPackageName);
                }
            }
            return;
        }

        if (intensity == 2) {
            boolean frictionEnabled = sharedPreferences.getBoolean("pref_reminder_friction_enabled", false);
            if (frictionEnabled) {
                if (isPackageBypassed(eventPackageName)) {
                    return;
                }
                triggerFrictionChallenge(eventPackageName);
            }
            return;
        }

        if (intensity == 1) {
            if (isPackageBypassed(eventPackageName)) {
                return;
            }
            triggerFrictionChallenge(eventPackageName);
            return;
        }
    }
}
