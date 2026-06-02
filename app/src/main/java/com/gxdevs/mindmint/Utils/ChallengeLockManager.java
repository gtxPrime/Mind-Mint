package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

public class ChallengeLockManager {

    private static final String PREFS_NAME = "AppData";

    // Keys
    public static final String PREF_SETTINGS_LOCK_TYPE = "pref_settings_lock_type";
    public static final String PREF_BLOCKER_CHALLENGE_TYPE = "pref_blocker_challenge_type";
    public static final String PREF_BLOCKER_BYPASS_DURATION_MIN = "pref_blocker_bypass_duration_minutes";
    public static final String PREF_BLOCKER_INTENSITY = "pref_blocker_intensity";
    public static final String PREF_BLOCKER_TRIGGER_TYPE = "pref_blocker_trigger_type";
    
    // Cooldown and lockout keys
    public static final String PREF_MAX_SEEN_TIMESTAMP = "pref_max_seen_timestamp";
    public static final String PREF_PIN_RESET_COOLDOWN_START_MILLIS = "pref_pin_reset_cooldown_start_millis";
    public static final String PREF_PIN_RESET_COOLDOWN_START_ELAPSED = "pref_pin_reset_cooldown_start_elapsed";
    
    public static final String PREF_SETTINGS_ONEDAY_LOCK_START_MILLIS = "pref_settings_oneday_lock_start_millis";
    public static final String PREF_SETTINGS_ONEDAY_LOCK_START_ELAPSED = "pref_settings_oneday_lock_start_elapsed";
    
    public static final String PREF_BLOCKER_ONEDAY_LOCK_START_MILLIS_PREFIX = "pref_blocker_oneday_lock_start_millis_";
    public static final String PREF_BLOCKER_ONEDAY_LOCK_START_ELAPSED_PREFIX = "pref_blocker_oneday_lock_start_elapsed_";

    public static final String PREF_BLOCKER_10MIN_WINDOW_DATE_PREFIX = "pref_blocker_10min_window_date_";

    private final SharedPreferences prefs;
    private final SharedPreferences appDataPrefs;
    private final Context context;

    public ChallengeLockManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.appDataPrefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Time & Anti-Tampering ---

    /**
     * Checks if the device clock has been tampered with (rewound or advanced forward).
     * @param savedWallTime The wall clock time saved when the lock was activated.
     * @param savedElapsedRealtime The elapsed realtime saved when the lock was activated.
     * @return true if tampering is detected.
     */
    public boolean isClockTampered(long savedWallTime, long savedElapsedRealtime) {
        long currentWallTime = System.currentTimeMillis();
        long currentElapsedRealtime = SystemClock.elapsedRealtime();

        // 1. Max Seen Timestamp Check (Detect rewinds)
        long maxSeen = prefs.getLong(PREF_MAX_SEEN_TIMESTAMP, 0L);
        if (currentWallTime < maxSeen) {
            return true; // Clock was wound back
        }
        
        // Update max seen time
        prefs.edit().putLong(PREF_MAX_SEEN_TIMESTAMP, currentWallTime).apply();

        if (savedWallTime <= 0 || savedElapsedRealtime <= 0) {
            return false;
        }

        // 2. Boot Drift / Forward Tamper Check
        // If current wall time minus saved wall time is significantly larger than
        // the elapsed realtime since activation, they moved the clock forward.
        long wallDelta = currentWallTime - savedWallTime;
        long elapsedDelta = currentElapsedRealtime - savedElapsedRealtime;

        // Allow up to 1 minute of drift / sleep cycles
        long threshold = 60 * 1000L; 
        
        if (elapsedDelta >= 0) {
            if (wallDelta > elapsedDelta + threshold) {
                return true; // Clock was wound forward
            }
            if (wallDelta < -threshold) {
                return true; // Clock was wound back
            }
        }
        
        return false;
    }

    public void updateMaxSeenTime() {
        long currentWallTime = System.currentTimeMillis();
        long maxSeen = prefs.getLong(PREF_MAX_SEEN_TIMESTAMP, 0L);
        if (currentWallTime > maxSeen) {
            prefs.edit().putLong(PREF_MAX_SEEN_TIMESTAMP, currentWallTime).apply();
        }
    }

    // --- Blocker Bypass Duration ---

    public int getBypassDurationMinutes() {
        return prefs.getInt(PREF_BLOCKER_BYPASS_DURATION_MIN, 10);
    }

    public void setBypassDurationMinutes(int minutes) {
        prefs.edit().putInt(PREF_BLOCKER_BYPASS_DURATION_MIN, minutes).apply();
    }

    // --- PIN Reset Cooldown ---

    public void startPinResetCooldown() {
        updateMaxSeenTime();
        prefs.edit()
                .putLong(PREF_PIN_RESET_COOLDOWN_START_MILLIS, System.currentTimeMillis())
                .putLong(PREF_PIN_RESET_COOLDOWN_START_ELAPSED, SystemClock.elapsedRealtime())
                .apply();
    }

    public boolean isPinResetCooldownActive() {
        long startWall = prefs.getLong(PREF_PIN_RESET_COOLDOWN_START_MILLIS, 0L);
        long startElapsed = prefs.getLong(PREF_PIN_RESET_COOLDOWN_START_ELAPSED, 0L);
        
        if (startWall <= 0) return false;

        // Check tampering
        if (isClockTampered(startWall, startElapsed)) {
            return true; // Tampered = keep cooldown active to prevent bypass
        }

        long elapsedMs = System.currentTimeMillis() - startWall;
        long targetMs = 24 * 60 * 60 * 1000L; // 24 hours
        
        return elapsedMs < targetMs;
    }

    public long getPinResetRemainingMs() {
        long startWall = prefs.getLong(PREF_PIN_RESET_COOLDOWN_START_MILLIS, 0L);
        if (startWall <= 0) return 0;
        
        long elapsedMs = System.currentTimeMillis() - startWall;
        long targetMs = 24 * 60 * 60 * 1000L;
        
        return Math.max(0, targetMs - elapsedMs);
    }

    public void clearPinResetCooldown() {
        prefs.edit()
                .remove(PREF_PIN_RESET_COOLDOWN_START_MILLIS)
                .remove(PREF_PIN_RESET_COOLDOWN_START_ELAPSED)
                .apply();
    }

    // --- 1-Day Lock Settings ---

    public void startSettingsOneDayLock() {
        updateMaxSeenTime();
        prefs.edit()
                .putLong(PREF_SETTINGS_ONEDAY_LOCK_START_MILLIS, System.currentTimeMillis())
                .putLong(PREF_SETTINGS_ONEDAY_LOCK_START_ELAPSED, SystemClock.elapsedRealtime())
                .apply();
    }

    public boolean isSettingsOneDayLockActive() {
        long startWall = prefs.getLong(PREF_SETTINGS_ONEDAY_LOCK_START_MILLIS, 0L);
        long startElapsed = prefs.getLong(PREF_SETTINGS_ONEDAY_LOCK_START_ELAPSED, 0L);
        
        if (startWall <= 0) return false;

        // Check tampering
        if (isClockTampered(startWall, startElapsed)) {
            return true; // Lock active if tampered
        }

        long elapsedMs = System.currentTimeMillis() - startWall;
        long targetMs = 24 * 60 * 60 * 1000L;
        
        return elapsedMs < targetMs;
    }

    public long getSettingsOneDayLockRemainingMs() {
        long startWall = prefs.getLong(PREF_SETTINGS_ONEDAY_LOCK_START_MILLIS, 0L);
        if (startWall <= 0) return 0;
        
        long elapsedMs = System.currentTimeMillis() - startWall;
        long targetMs = 24 * 60 * 60 * 1000L;
        
        return Math.max(0, targetMs - elapsedMs);
    }

    // --- 1-Day Lock Blocker ---

    public void startBlockerOneDayLock(String packageName) {
        updateMaxSeenTime();
        prefs.edit()
                .putLong(PREF_BLOCKER_ONEDAY_LOCK_START_MILLIS_PREFIX + packageName, System.currentTimeMillis())
                .putLong(PREF_BLOCKER_ONEDAY_LOCK_START_ELAPSED_PREFIX + packageName, SystemClock.elapsedRealtime())
                .apply();
    }

    public boolean isBlockerOneDayLockActive(String packageName) {
        long startWall = prefs.getLong(PREF_BLOCKER_ONEDAY_LOCK_START_MILLIS_PREFIX + packageName, 0L);
        long startElapsed = prefs.getLong(PREF_BLOCKER_ONEDAY_LOCK_START_ELAPSED_PREFIX + packageName, 0L);
        
        if (startWall <= 0) return false;

        if (isClockTampered(startWall, startElapsed)) {
            return true;
        }

        long elapsedMs = System.currentTimeMillis() - startWall;
        long targetMs = 24 * 60 * 60 * 1000L;
        
        return elapsedMs < targetMs;
    }

    public long getBlockerOneDayLockRemainingMs(String packageName) {
        long startWall = prefs.getLong(PREF_BLOCKER_ONEDAY_LOCK_START_MILLIS_PREFIX + packageName, 0L);
        if (startWall <= 0) return 0;
        
        long elapsedMs = System.currentTimeMillis() - startWall;
        long targetMs = 24 * 60 * 60 * 1000L;
        
        return Math.max(0, targetMs - elapsedMs);
    }

    // --- 10-Min Window blocker tracker ---

    public boolean hasUsed10MinWindowToday(String packageName, String todayDateString) {
        String lastUsedDate = prefs.getString(PREF_BLOCKER_10MIN_WINDOW_DATE_PREFIX + packageName, "");
        return todayDateString.equals(lastUsedDate);
    }

    public void mark10MinWindowUsed(String packageName, String todayDateString) {
        prefs.edit().putString(PREF_BLOCKER_10MIN_WINDOW_DATE_PREFIX + packageName, todayDateString).apply();
    }
}
