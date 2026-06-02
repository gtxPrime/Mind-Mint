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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;
import com.gxdevs.mindmint.Services.FocusService;

public class BlockingOverlayDisplayActivity extends AppCompatActivity {

    private static final String TAG = "BlockingOverlayDisplay";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentBlockedAppName = "Unknown";
    private String currentBlockedPackageName;
    private ImageView ivBlockedAppIcon;
    private boolean isReminderOnly = false;
    private boolean homeActionDispatched = false;

    private Button btnUnlockApp;
    private Button btnGoBack;
    private com.gxdevs.mindmint.Utils.ChallengeLockManager challengeLockMgr;
    private androidx.activity.result.ActivityResultLauncher<Intent> challengeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: Activity CREATING. Intent: " + getIntent());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_blocking_overlay_display);
        Log.d(TAG, "onCreate: ContentView SET");

        ivBlockedAppIcon = findViewById(R.id.iv_blocked_app_icon);
        btnUnlockApp = findViewById(R.id.btn_unlock_app);
        btnGoBack = findViewById(R.id.btn_go_back);
        challengeLockMgr = new com.gxdevs.mindmint.Utils.ChallengeLockManager(this);

        challengeLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        finish();
                    } else {
                        handleBackPress();
                    }
                });

        processIntent(getIntent());
        setupTimer();
        Log.i(TAG, "onCreate: Activity CREATED and timer scheduled.");
    }

    @SuppressLint("SetTextI18n")
    private void processIntent(Intent intent) {
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
        String customSubtitle = intent != null ? intent.getStringExtra(AppUsageAccessibilityService.EXTRA_CUSTOM_SUBTITLE) : null;
        if (tvBlockingMessage != null) {
            if (customSubtitle != null) {
                tvBlockingMessage.setText(currentBlockedAppName + " is Protected");
                tv_blocking_subtitle.setText(customSubtitle);
            } else if (isReminderOnly) {
                tvBlockingMessage.setText("Quick Reminder for: " + currentBlockedAppName);
                tv_blocking_subtitle.setText("Time's up! This is your reminder to close " + currentBlockedAppName + ".");
            } else {
                tvBlockingMessage.setText(currentBlockedAppName + " is Blocked");
                if (!isFocus) {
                    tv_blocking_subtitle.setText("Access blocked - you've restricted " + currentBlockedAppName + ".");
                } else {
                    boolean locked = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
                    if (locked) {
                        tv_blocking_subtitle.setText("You're Locked In - you've restricted " + currentBlockedAppName + ".");
                    } else {
                        tv_blocking_subtitle.setText("You're in Focus Mode - you've restricted " + currentBlockedAppName + ".");
                    }
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
        TranslateAnimation moveZigZag = new TranslateAnimation(TranslateAnimation.RELATIVE_TO_SELF, -0.05f, Animation.RELATIVE_TO_SELF, 0.05f, Animation.RELATIVE_TO_SELF, -0.05f, Animation.RELATIVE_TO_SELF, 0.05f);
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

        // Reset the HOME-dispatch guard for every new blocking event.
        homeActionDispatched = false;

        btnUnlockApp.setVisibility(View.GONE);
        btnGoBack.setVisibility(View.GONE);

        if (isReminderOnly) {
            setupStandardAutoCloseTimer();
            return;
        }

        String challengeType = null;
        if (getIntent() != null) {
            challengeType = getIntent().getStringExtra("extra_challenge_type");
        }
        if (challengeType == null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            challengeType = sp.getString(com.gxdevs.mindmint.Utils.ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
        }

        if ("oneday".equals(challengeType)) {
            // 1-Day lockout: show countdown + Go Back (HOME) button.
            btnGoBack.setVisibility(View.VISIBLE);
            btnGoBack.setOnClickListener(v -> handleBackPress());

            TextView tv_blocking_subtitle = findViewById(R.id.tv_blocking_subtitle);

            if (!challengeLockMgr.isBlockerOneDayLockActive(currentBlockedPackageName)) {
                challengeLockMgr.startBlockerOneDayLock(currentBlockedPackageName);
            }

            handler.post(new Runnable() {
                @Override
                public void run() {
                    long remainingMs = challengeLockMgr.getBlockerOneDayLockRemainingMs(currentBlockedPackageName);
                    if (remainingMs <= 0) {
                        dispatchHomeAction();
                        finish();
                        return;
                    }
                    long hours = remainingMs / (60 * 60 * 1000L);
                    long minutes = (remainingMs / (60 * 1000L)) % 60;
                    long seconds = (remainingMs / 1000L) % 60;
                    if (tv_blocking_subtitle != null) {
                        tv_blocking_subtitle.setText(String.format(java.util.Locale.US,
                            "Strict lockout active.\nTime remaining: %02dh %02dm %02ds",
                            hours, minutes, seconds));
                    }
                    handler.postDelayed(this, 1000);
                }
            });
        } else {
            // "none" or any fallback → standard auto-close + HOME after popup duration.
            setupStandardAutoCloseTimer();
        }
    }

    private void setupStandardAutoCloseTimer() {
        homeActionDispatched = false;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int popupDurationSeconds = sharedPreferences.getInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, 3);
        
        if (popupDurationSeconds < 1) {
            popupDurationSeconds = 1;
        }

        handler.postDelayed(() -> {
            if (!isReminderOnly && !homeActionDispatched) {
                homeActionDispatched = true;
                Intent closeAppIntent = new Intent(AppUsageAccessibilityService.ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY);
                closeAppIntent.setPackage(getPackageName());
                sendBroadcast(closeAppIntent);
            }
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, popupDurationSeconds * 1000L);
    }


    /**
     * Dispatch a global HOME action via the accessibility service so the user lands
     * on the launcher instead of being dropped back into the blocked app.
     */
    private void dispatchHomeAction() {
        if (!homeActionDispatched) {
            homeActionDispatched = true;
            Intent homeIntent = new Intent(AppUsageAccessibilityService.ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY);
            homeIntent.setPackage(getPackageName());
            sendBroadcast(homeIntent);
            Log.d(TAG, "dispatchHomeAction: HOME broadcast sent.");
        }
    }

    private void handleBackPress() {
        if (isReminderOnly) {
            Log.d(TAG, "onBackPressed: Back press allowed for reminder. Finishing activity.");
            finish();
        } else {
            // Blocking mode: always go home so the user is NOT dropped back into the
            // blocked app when they press Go Back / hardware back.
            Log.d(TAG, "onBackPressed: Blocking mode — dispatching HOME before finishing.");
            dispatchHomeAction();
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
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