package com.gxdevs.mindmint;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.perf.FirebasePerformance;
import com.gxdevs.mindmint.Receivers.FirebaseNotificationReceiver;

/**
 * Application class — the single entry point for all Firebase SDK initialization.
 * <p>
 * ════════════════════════════════════════════════════════════════════
 * HOW FCM TOPICS WORK IN MIND MINT
 * ════════════════════════════════════════════════════════════════════
 * <p>
 * Topic              Subscribed by           Use for
 * ─────────────────  ──────────────────────  ────────────────────────────────
 * all_users          Every install           Announcements, critical notices
 * app_v_<code>       Every install (current  UPDATE NOTIFICATIONS — send to
 * version only)           the OLD version code to reach
 * users who haven't updated yet
 * focus_users        Only after first focus tips, session nudges
 * session is started      (NOT everyone — only focus users)
 * <p>
 * ════════════════════════════════════════════════════════════════════
 * HOW VERSION-TARGETED UPDATES WORK
 * ════════════════════════════════════════════════════════════════════
 * <p>
 * 1. Pumpkin 11 (versionCode 16) installs → subscribes to "app_v_16"
 * 2. Pumpkin 12 (versionCode 17) releases
 * 3. Pumpkin 12 users open app → unsubscribe from "app_v_16", subscribe to "app_v_17"
 * 4. You send update notification to topic "app_v_16"
 * → Only Pumpkin 11 users who haven't updated receive it ✅
 * → Pumpkin 12 users are NOT on "app_v_16" anymore, so they DON'T get it ✅
 * <p>
 * ════════════════════════════════════════════════════════════════════
 */
public class MindMintApp extends Application {

    private static final String TAG = "MindMintApp";

    // ── FCM Topics ────────────────────────────────────────────────────────────

    /**
     * Every install. Use for announcements and critical notices only.
     */
    public static final String TOPIC_ALL_USERS = "all_users";

    /**
     * Version-specific topic prefix. The full topic name is "app_v_<versionCode>".
     * Subscribed automatically on every launch. Old version topics are unsubscribed
     * on update so you can target only non-updated users.
     * <p>
     * To send an update notification to users still on Pumpkin 11 (versionCode 16):
     * Firebase Console → Topic → "app_v_16"
     */
    public static final String TOPIC_VERSION_PREFIX = "app_v_";

    /**
     * Subscribed ONLY when the user actually starts their first focus session.
     * Do NOT subscribe everyone here — that defeats the purpose.
     * Call subscribeToFocusTopic() from FocusService when a session starts.
     * <p>
     * Use for: focus reminders, Pomodoro tips, "time to focus" nudges.
     */
    public static final String TOPIC_FOCUS_USERS = "focus_users";

    /**
     * Singleton accessor so any class can log events without re-obtaining the instance.
     */
    private static FirebaseAnalytics analyticsInstance;

    @Override
    public void onCreate() {
        super.onCreate();

        // Seed and migrate blocker database configs
        com.gxdevs.mindmint.Utils.BlockerMigrationManager.migrateAndSeed(this);

        // 1. Bootstrap Firebase
        FirebaseApp.initializeApp(this);

        // 2. Analytics
        analyticsInstance = FirebaseAnalytics.getInstance(this);
        analyticsInstance.setAnalyticsCollectionEnabled(true);
        Log.d(TAG, "Firebase Analytics initialised");

        // 3. Crashlytics
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(true);
        crashlytics.setCustomKey("app_flavour", "production");
        crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE);
        crashlytics.setCustomKey("version_name", BuildConfig.VERSION_NAME);
        Log.d(TAG, "Firebase Crashlytics initialised");

        // 4. Performance Monitoring
        FirebasePerformance performance = FirebasePerformance.getInstance();
        performance.setPerformanceCollectionEnabled(true);
        Log.d(TAG, "Firebase Performance initialised");

        // 5. FCM notification channels
        FirebaseNotificationReceiver.ensureChannelsCreated(this);
        Log.d(TAG, "FCM notification channels registered");

        // 6. FCM topics
        subscribeToTopics();

        // 7. DEBUG ONLY: Log the FCM device token so you can use it in Firebase Console
        //    → Firebase Console → Messaging → New campaign → Send test message → paste token
        //    This block is completely removed in release builds by the compiler.
        if (BuildConfig.DEBUG) {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            String token = task.getResult();
                            // ════════════════════════════════════════════════
                            //  Copy this token from Logcat filter: MindMintFCM_Token
                            // ════════════════════════════════════════════════
                            Log.i("MindMintFCM_Token", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            Log.i("MindMintFCM_Token", "  FCM Device Token (for test messages):");
                            Log.i("MindMintFCM_Token", "  " + token);
                            Log.i("MindMintFCM_Token", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        } else {
                            Log.w("MindMintFCM_Token", "Failed to get FCM token", task.getException());
                        }
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Topic subscriptions
    // ─────────────────────────────────────────────────────────────────────────

    private void subscribeToTopics() {
        FirebaseMessaging fcm = FirebaseMessaging.getInstance();
        int currentVersion = BuildConfig.VERSION_CODE;

        // ── Topic 1: all_users ────────────────────────────────────────────────
        // Every install. For announcements and notices — NOT for update nudges.
        fcm.subscribeToTopic(TOPIC_ALL_USERS)
                .addOnCompleteListener(t ->
                        Log.d(TAG, t.isSuccessful()
                                ? "✓ Subscribed: " + TOPIC_ALL_USERS
                                : "✗ Failed:     " + TOPIC_ALL_USERS));

        // ── Topic 2: app_v_<currentVersionCode> ──────────────────────────────
        // Subscribe to THIS version's topic.
        String currentVersionTopic = TOPIC_VERSION_PREFIX + currentVersion;
        fcm.subscribeToTopic(currentVersionTopic)
                .addOnCompleteListener(t ->
                        Log.d(TAG, t.isSuccessful()
                                ? "✓ Subscribed: " + currentVersionTopic
                                : "✗ Failed:     " + currentVersionTopic));

        // ── Unsubscribe from OLD version topics ───────────────────────────────
        // When a user updates the app, we remove them from the old version's topic
        // so they stop receiving "please update" notifications.
        // We clean up the last 10 previous versions to cover users who skipped updates.
        for (int i = 1; i <= 10; i++) {
            int oldVersion = currentVersion - i;
            String oldTopic = TOPIC_VERSION_PREFIX + oldVersion;
            fcm.unsubscribeFromTopic(oldTopic)
                    .addOnCompleteListener(t ->
                            Log.d(TAG, t.isSuccessful()
                                    ? "✓ Unsubscribed old version topic: " + oldTopic
                                    : "✗ Unsubscribe skipped: " + oldTopic));
        }

        // ── Topic 3: focus_users — NOT subscribed here ────────────────────────
        // focus_users is subscribed only when the user actually starts a focus
        // session. See subscribeToFocusTopic() below. This keeps the topic
        // meaningful — only real focus users receive focus-related nudges.
    }

    /**
     * Subscribes this device to the "focus_users" topic.
     * <p>
     * Call this ONCE after the user starts their very first focus session.
     * Subsequent calls are safe — Firebase no-ops if already subscribed.
     * <p>
     * Suggested call site: FocusService.startForeground() on first-ever session,
     * or anywhere you detect the user has engaged with Focus Mode.
     */
    public static void subscribeToFocusTopic() {
        FirebaseMessaging.getInstance()
                .subscribeToTopic(TOPIC_FOCUS_USERS)
                .addOnCompleteListener(t ->
                        Log.d(TAG, t.isSuccessful()
                                ? "✓ Subscribed: " + TOPIC_FOCUS_USERS
                                : "✗ Failed:     " + TOPIC_FOCUS_USERS));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public accessors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the singleton {@link FirebaseAnalytics} instance.
     * Use {@link com.gxdevs.mindmint.Utils.FirebaseAnalyticsHelper} for structured event logging.
     */
    public static FirebaseAnalytics getAnalytics() {
        return analyticsInstance;
    }
}
