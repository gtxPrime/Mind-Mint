package com.gxdevs.mindmint.Activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
 
import androidx.preference.PreferenceManager;

import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;

public class BlockingOverlayDisplayActivity extends AppCompatActivity {

    private static final String TAG = "BlockingOverlayDisplay";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentBlockedAppName = "Unknown";
    private ImageView ivBlockedAppIcon;
    private boolean isReminderOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: Activity CREATING. Intent: " + getIntent());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_blocking_overlay_display);
        Log.d(TAG, "onCreate: ContentView SET");

        ivBlockedAppIcon = findViewById(R.id.iv_blocked_app_icon);

        processIntent(getIntent());
        setupTimer();
        Log.i(TAG, "onCreate: Activity CREATED and timer scheduled.");
    }

    private void processIntent(Intent intent) {
        // To store package name
        String currentBlockedPackageName;
        boolean isFocus = false;
        if (intent == null) {
            Log.w(TAG, "processIntent: Intent is null.");
            currentBlockedAppName = "App (Error)";
            isReminderOnly = false;
        } else {
            currentBlockedAppName = intent.getStringExtra(AppUsageAccessibilityService.EXTRA_BLOCKED_APP_NAME);
            currentBlockedPackageName = intent.getStringExtra(AppUsageAccessibilityService.EXTRA_BLOCKED_PACKAGE_NAME);
            isReminderOnly = intent.getBooleanExtra(AppUsageAccessibilityService.EXTRA_IS_REMINDER_ONLY, false);
            isFocus = intent.getBooleanExtra(AppUsageAccessibilityService.EXTRA_IS_FOCUS, false);
            Log.i(TAG, "processIntent: Received blocked app name: " + currentBlockedAppName + ", package: " + currentBlockedPackageName + ", isReminder: " + isReminderOnly);

            if (TextUtils.isEmpty(currentBlockedAppName)) {
                currentBlockedAppName = "This app";
                Log.w(TAG, "processIntent: Blocked app name was empty, defaulted to 'This app'.");
            }
        }

        TextView tvBlockingMessage = findViewById(R.id.tv_blocking_message);
        TextView tv_blocking_subtitle = findViewById(R.id.tv_blocking_subtitle);
        if (tvBlockingMessage != null) {
            if (isReminderOnly) {
                tvBlockingMessage.setText("Quick Reminder for: " + currentBlockedAppName);
                tv_blocking_subtitle.setText("Time's up! This is your reminder to close " + currentBlockedAppName + ".");
            } else {
                tvBlockingMessage.setText(currentBlockedAppName + " is Blocked");
                if (!isFocus) {
                    tv_blocking_subtitle.setText("Access blocked - you've restricted " + currentBlockedAppName + ".");
                } else {
                    tv_blocking_subtitle.setText("You're in Focus Mode - you've restricted " + currentBlockedAppName + ".");
                }
            }
        } else {
            Log.e(TAG, "processIntent: tvBlockingMessage is NULL!");
        }

        // Load and set the icon
        if (ivBlockedAppIcon != null) {
            AnimationSet angryShake = getAnimationSet();
            ivBlockedAppIcon.startAnimation(angryShake);
        } else {
            Log.e(TAG, "processIntent: ivBlockedAppIcon is NULL!");
        }
    }

    @NonNull
    private static AnimationSet getAnimationSet() {
        AnimationSet angryShake = new AnimationSet(true);
        TranslateAnimation moveZigZag = new TranslateAnimation(TranslateAnimation.RELATIVE_TO_SELF, -0.05f,
                Animation.RELATIVE_TO_SELF, 0.05f,
                Animation.RELATIVE_TO_SELF, -0.05f,
                Animation.RELATIVE_TO_SELF, 0.05f);
        moveZigZag.setDuration(80);
        moveZigZag.setRepeatCount(Animation.INFINITE);
        moveZigZag.setRepeatMode(Animation.REVERSE);
        RotateAnimation tilt = new RotateAnimation(-5, 5, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        tilt.setDuration(80);
        tilt.setRepeatCount(Animation.INFINITE);
        tilt.setRepeatMode(Animation.REVERSE);
        angryShake.addAnimation(moveZigZag);
        angryShake.addAnimation(tilt);
        return angryShake;
    }

    private void setupTimer() {
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "setupTimer: Removed any existing handler callbacks.");

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int popupDurationSeconds = sharedPreferences.getInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, 3);
        Log.i(TAG, "setupTimer: Loaded popupDurationSeconds from Prefs: " + popupDurationSeconds + "s");

        if (popupDurationSeconds < 1) {
            Log.w(TAG, "setupTimer: popupDurationSeconds was < 1 (" + popupDurationSeconds + "s), forcing to 1s.");
            popupDurationSeconds = 1;
        }

        Log.i(TAG, "setupTimer: Scheduling finish() in " + popupDurationSeconds + " seconds for app: " + currentBlockedAppName + ". Is Reminder: " + isReminderOnly);
        handler.postDelayed(() -> {
            Log.i(TAG, "Handler postDelayed: Time elapsed for app: " + currentBlockedAppName + ". Is Reminder: " + isReminderOnly);

            if (!isReminderOnly) {
                Log.d(TAG, "Sending broadcast to AppUsageAccessibilityService to perform GLOBAL_ACTION_HOME for blocking.");
                Intent closeAppIntent = new Intent(AppUsageAccessibilityService.ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY);
                closeAppIntent.setPackage(getPackageName());
                sendBroadcast(closeAppIntent);
                Log.d(TAG, "Broadcast ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY sent.");
            } else {
                Log.d(TAG, "Reminder time elapsed. Not sending GLOBAL_ACTION_HOME.");
            }

            if (!isFinishing() && !isDestroyed()) {
                Log.i(TAG, "Handler postDelayed: Finishing BlockingOverlayDisplayActivity now.");
                finish();
            } else {
                Log.w(TAG, "Handler postDelayed: Activity was already finishing or destroyed before explicit finish call.");
            }
        }, popupDurationSeconds * 1000L);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (isReminderOnly) {
            Log.d(TAG, "onBackPressed: Back press allowed for reminder. Finishing activity.");
            finish();
        } else {
            Log.d(TAG, "onBackPressed: Back press ignored for blocking mode.");
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent: Activity received NEW INTENT: " + intent);
        setIntent(intent);
        processIntent(intent);
        setupTimer();
        Log.i(TAG, "onNewIntent: Processed new intent and rescheduled timer.");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart: Activity STARTED.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume: Activity RESUMED.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: Activity PAUSED.");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop: Activity STOPPED.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy: Activity DESTROYED for app: " + currentBlockedAppName + ". Removing handler callbacks.");
        handler.removeCallbacksAndMessages(null);
    }
} 