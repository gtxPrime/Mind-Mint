package com.gxdevs.mindmint.Activities;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.gxdevs.mindmint.Adapters.FocusScheduleAdapter;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.Utils.CustomDialogUtils;
import com.gxdevs.mindmint.Utils.FocusScheduleManager;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.StreakManager;
import com.gxdevs.mindmint.Utils.TaskNotificationManager;
import com.gxdevs.mindmint.Views.NebulaStarfieldView;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.FocusService;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Views.RevealMaskImageView;
import com.gxdevs.mindmint.db.MindMintRoomDatabase;
import com.gxdevs.mindmint.db.entities.FocusScheduleEntity;
import com.gxdevs.mindmint.db.entities.FocusTopicEntity;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.Utils.TaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.tankery.lib.circularseekbar.CircularSeekBar;

public class FocusMode extends AppCompatActivity {

    private static final String TAG = "FocusMode";
    private static final float REVEAL_COMPLETE_AT = 0.95f;
    public static final String EXTRA_SHAKE_PERM_CARDS = "extra_shake_perm_cards";
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;
    private TextView timerText, instructionText;
    private Button startButton, focusStop;
    private View btnDivider;
    private FocusService focusService;
    private boolean isBound = false;
    private final Handler handler = new Handler();
    private int selectedMinutes = 25;
    private CircularSeekBar circularSeekBar;
    private ImageView crystalBase;
    private RevealMaskImageView crystalColor;
    private LottieAnimationView lottieAnimation;
    private ValueAnimator revealAnimator;
    private MaterialTextView mintCrystalsTxt;
    private MintCrystals mintCrystals;
    private ChipGroup topicsChipGroup;
    private MindMintRoomDatabase db;
    private List<FocusTopicEntity> currentTopics = new ArrayList<>();
    private int selectedTopicId = -1;
    private ImageView settingsBtn;
    private TextView pomodoroIndicator;
    private View topicsContainer;
    private final boolean isTestMode = false;
    private boolean wasLockedInSession = false;
    private boolean revealCompleteNotified = false;
    private android.widget.SeekBar breakDurationSeekBar;
    private TextView breakSeekbarLabel;
    private View breakSeekbarCard;
    private View notificationPermCard;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private View taskCardOverlay;
    private TextView linkedTaskName;
    private Button markTaskCompleteBtn;
    private String linkedTaskIdExtra;
    private String linkedTopicNameExtra;
    private boolean isOpenEndedExtra;

    // Pomodoro Preferences
    private static final String PREF_POMODORO_ENABLED = "pref_pomodoro_enabled";
    private static final String PREF_FOCUS_DURATION = "pref_focus_duration";
    private static final String PREF_BREAK_DURATION = "pref_break_duration";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefsInit = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefsInit.contains("nebula_theme_override")) {
            boolean dark = prefsInit.getBoolean("nebula_theme_override", false);
            getDelegate().setLocalNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_focus_mode);
        Utils.setPad(findViewById(R.id.main), "bottom", this);

        db = MindMintRoomDatabase.getInstance(this);

        setupPermissionLauncher();
        initViews();
        setupOnUI();
        setupReveal();
        setupCircularSeekBar();
        loadTopics();

        linkedTaskIdExtra = getIntent().getStringExtra(FocusService.EXTRA_TASK_ID);
        linkedTopicNameExtra = getIntent().getStringExtra("topicName");
        isOpenEndedExtra = getIntent().getBooleanExtra(FocusService.EXTRA_IS_OPEN_ENDED, false);

        setupTaskCardIfLinked();
        checkPermissionAndMoveOn();
    }

    private void restoreStateFromService() {
        if (focusService == null) return;
        if (linkedTaskIdExtra == null || linkedTaskIdExtra.isEmpty()) {
            linkedTaskIdExtra = focusService.getLinkedTaskId();
        }
        if (!isOpenEndedExtra) {
            isOpenEndedExtra = focusService.isOpenEnded();
        }
        if (linkedTopicNameExtra == null || linkedTopicNameExtra.isEmpty()) {
            if (linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty()) {
                try {
                    Task t = new TaskManager(this).getTaskById(linkedTaskIdExtra);
                    if (t != null) linkedTopicNameExtra = t.getName();
                } catch (Throwable ignored) {
                }
            }
        }
        setupTaskCardIfLinked();
    }

    private void setupTaskCardIfLinked() {
        if (linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty()) {
            taskCardOverlay.setVisibility(View.VISIBLE);
            linkedTaskName.setText(linkedTopicNameExtra != null ? linkedTopicNameExtra : "Focus Task");

            topicsContainer.setVisibility(View.GONE);
            settingsBtn.setVisibility(View.GONE);

            findViewById(R.id.coinImg).setVisibility(View.GONE);
            mintCrystalsTxt.setVisibility(View.GONE);

            if (isOpenEndedExtra) {
                circularSeekBar.setVisibility(View.GONE);
                lottieAnimation.setVisibility(View.GONE);
                crystalBase.setVisibility(View.GONE);
                crystalColor.setVisibility(View.GONE);
                View progressContainer = findViewById(R.id.progressContainer);
                if (progressContainer != null) progressContainer.setVisibility(View.GONE);
                instructionText.setText("Open Ended Focus");

                ConstraintLayout mainLayout = findViewById(R.id.main);
                ConstraintSet set = new ConstraintSet();
                set.clone(mainLayout);
                set.connect(R.id.timerText, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                set.connect(R.id.timerText, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                set.applyTo(mainLayout);
                timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 65f);
            }

            markTaskCompleteBtn.setOnClickListener(v -> {
                completeLinkedTask();
                finish();
            });
        } else {
            boolean isLockedInExt = getIntent().getBooleanExtra(FocusService.EXTRA_IS_LOCKED_IN, false) ||
                    PreferenceManager.getDefaultSharedPreferences(this).getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
            if (isLockedInExt) {
                findViewById(R.id.coinImg).setVisibility(View.GONE);
                mintCrystalsTxt.setVisibility(View.GONE);
            }
        }
    }

    private void showStopTaskDialog() {
        String activeTaskId = (linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty())
                ? linkedTaskIdExtra
                : (focusService != null ? focusService.getLinkedTaskId() : null);
        if (activeTaskId == null || activeTaskId.isEmpty()) {
            if (isBound && focusService != null) focusService.stopService();
            finish();
            return;
        }
        CustomDialogUtils.showCustomDialog(this,
                "Stop Focus",
                "Did you complete the task?",
                "Yes, mark complete",
                "No, keep it pending",
                () -> {
                    completeLinkedTask();
                    finish();
                },
                () -> {
                    if (isBound && focusService != null) {
                        focusService.stopService();
                    }
                    sendBroadcast(new Intent(FocusService.ACTION_TASK_FOCUS_UPDATE));
                    finish();
                });
    }

    private void completeLinkedTask() {
        if (isBound && focusService != null) {
            focusService.stopService(); // This saves time and stops timer
        }

        if (linkedTaskIdExtra == null || linkedTaskIdExtra.isEmpty()) return;

        TaskManager tm = new TaskManager(this);
        List<Task> tasks = tm.loadTasks();
        boolean updated = false;
        Task completedTask = null;
        for (Task t : tasks) {
            if (t.getId().equals(linkedTaskIdExtra)) {
                t.setCompleted(true);
                t.setCompletedDate(new java.util.Date());
                t.setFocusStatus("IDLE");
                updated = true;
                completedTask = t;
                break;
            }
        }
        if (updated) {
            tm.saveTasks(tasks);

            if (!completedTask.isHabit()) {
                new MintCrystals(this).addCoins(2);
            }
            new TaskNotificationManager(this).cancelTaskReminder(completedTask);

            if (completedTask.isHabit() && completedTask.getHabitId() != null) {
                HabitManager hm = new HabitManager(this);
                boolean allComplete = true;
                for (Task t : tasks) {
                    if (completedTask.getHabitId().equals(t.getHabitId()) && !t.isCompleted()) {
                        allComplete = false;
                        break;
                    }
                }
                if (allComplete) {
                    List<Habit> habits = hm.loadHabits();
                    for (Habit h : habits) {
                        if (h.getId().equals(completedTask.getHabitId())) {
                            h.markDoneToday();
                            break;
                        }
                    }
                    hm.saveHabits(habits);
                    new StreakManager(this).updateStreakOnHabitCompletion();
                }
            }

            String msg = completedTask.isRecurring()
                    ? "Task completed for today!"
                    : "Task completed: " + completedTask.getName();
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            sendBroadcast(new Intent(FocusService.ACTION_TASK_FOCUS_UPDATE));
        }
    }

    private void setupOnUI() {
        findViewById(R.id.coinImg).setOnClickListener(v -> showPeaceCoinsDialog());
        findViewById(R.id.mintCrystals).setOnClickListener(v -> showPeaceCoinsDialog());
        settingsBtn.setOnClickListener(v -> showSettingsBottomSheet());

        focusStop.setOnClickListener(v -> {
            if (isBound && focusService != null) {
                String activeTaskId = (linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty())
                        ? linkedTaskIdExtra : focusService.getLinkedTaskId();
                if (activeTaskId != null && !activeTaskId.isEmpty()) {
                    // Task-linked session: ask complete or pending
                    showStopTaskDialog();
                } else {
                    // Normal standalone session: just stop
                    focusService.stopService();
                    finish();
                }
            } else {
                // Service unbound (e.g. died unexpectedly) — just close the screen
                finish();
            }
        });

        startButton.setOnLongClickListener(v -> {
            // Long-press: show timer picker bottom sheet when timer is NOT running
            if (isBound && focusService != null && focusService.isTimerRunning()) {
                Toast.makeText(this, "Stop the session first to change the timer.", Toast.LENGTH_SHORT).show();
                return true;
            }
            showTimerPickerSheet();
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        });

        startButton.setOnClickListener(v -> {
            // Require exact alarm permission on Android 12+ before any timer action
            if (!hasExactAlarmPermission()) {
                checkPermissionAndMoveOn();
                askForExactAlarmPermission();
                Toast.makeText(this, "Allow exact alarm permission to use Focus Mode timer.", Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            if (checkNotification()) {
                if (isBound && focusService != null) {
                    if (focusService.isTimerRunning()) {
                        if (focusService.isPaused() && !focusService.isBreak()) {
                            // WAITING_USER state - Resume the timer
                            focusService.resumeTimer();
                            updateButtonStates(true);
                            handler.post(updateUITask);
                        } else {
                            // Running or in break — need to stop
                            String activeTaskId = (linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty())
                                    ? linkedTaskIdExtra
                                    : focusService.getLinkedTaskId();
                            if (activeTaskId != null && !activeTaskId.isEmpty()) {
                                // Task-linked: ask complete or pending before stopping
                                showStopTaskDialog();
                            } else {
                                focusService.stopService();
                                finish();
                            }
                        }
                    } else {
                        if (selectedMinutes < 10) {
                            Toast.makeText(this, "Minimum timer is 10 minutes", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        hideCrystalForRunningInit();
                        Intent serviceIntent = new Intent(this, FocusService.class);
                        serviceIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
                        long duration = isTestMode ? 60000L : getDuration();
                        serviceIntent.putExtra("durationInMillis", duration);

                        // Pass configurations
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        boolean isPomodoro = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
                        long focusInterval = isTestMode ? 30000L : prefs.getInt(PREF_FOCUS_DURATION, 25) * 60 * 1000L;
                        long breakInterval = isTestMode ? 15000L : prefs.getInt(PREF_BREAK_DURATION, 5) * 60 * 1000L;
                        String topicName = null;
                        if (selectedTopicId != -1) {
                            for (FocusTopicEntity t : currentTopics) {
                                if (t.id == selectedTopicId) {
                                    topicName = t.name;
                                    break;
                                }
                            }
                        }

                        serviceIntent.putExtra("isPomodoroEnabled", isPomodoro || isTestMode);
                        serviceIntent.putExtra("pomodoroFocusInterval", focusInterval);
                        serviceIntent.putExtra("pomodoroBreakInterval", breakInterval);
                        serviceIntent.putExtra("topicName", topicName);

                        ContextCompat.startForegroundService(this, serviceIntent);
                        wasLockedInSession = false; // Reset for fresh standalone session
                        // Clear any stale service pause — blocking must be active during focus
                        PreferenceManager.getDefaultSharedPreferences(this).edit()
                                .putBoolean("isServicePaused", false)
                                .putLong("resumeTime", 0)
                                .apply();
                        circularSeekBar.setProgress(0f);
                        if (!isBound) {
                            bindService();
                        } else {
                            updateButtonStates(true);
                            handler.post(updateUITask);
                        }
                    }
                } else {
                    hideCrystalForRunningInit();
                    Intent serviceIntent = new Intent(this, FocusService.class);
                    serviceIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
                    long duration = isTestMode ? 60000L : getDuration();
                    serviceIntent.putExtra("durationInMillis", duration);

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    boolean isPomodoro = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
                    long focusInterval = isTestMode ? 30000L : prefs.getInt(PREF_FOCUS_DURATION, 25) * 60 * 1000L;
                    long breakInterval = isTestMode ? 15000L : prefs.getInt(PREF_BREAK_DURATION, 5) * 60 * 1000L;
                    String topicName = null;
                    if (selectedTopicId != -1) {
                        for (FocusTopicEntity t : currentTopics) {
                            if (t.id == selectedTopicId) {
                                topicName = t.name;
                                break;
                            }
                        }
                    }

                    serviceIntent.putExtra("isPomodoroEnabled", isPomodoro || isTestMode);
                    serviceIntent.putExtra("pomodoroFocusInterval", focusInterval);
                    serviceIntent.putExtra("pomodoroBreakInterval", breakInterval);
                    serviceIntent.putExtra("topicName", topicName);

                    ContextCompat.startForegroundService(this, serviceIntent);
                    wasLockedInSession = false; // Reset for fresh standalone session
                    bindService();
                }
            }
        });

        NebulaStarfieldView galaxy = findViewById(R.id.starFieldView);
        galaxy.setContext(this);
        galaxy.setStarCount(200);
        galaxy.setDriftRange(2f);

        // Initialize galaxy theme on start
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean savedOverride = prefs.contains("nebula_theme_override")
                ? prefs.getBoolean("nebula_theme_override", false)
                : null;

        boolean isDark;
        if (savedOverride != null) {
            isDark = savedOverride;
        } else {
            String mode = prefs.getString("pref_theme_mode", "Dark Theme");
            if ("Dark Theme".equalsIgnoreCase(mode) || "dark".equalsIgnoreCase(mode)) {
                isDark = true;
            } else if ("Light Theme".equalsIgnoreCase(mode) || "light".equalsIgnoreCase(mode)) {
                isDark = false;
            } else {
                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                isDark = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
            }
        }
        galaxy.setThemeOverrideDark(isDark);

        mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
    }

    private void initViews() {
        startButton = findViewById(R.id.focusStart);

        timerText = findViewById(R.id.timerText);
        pomodoroIndicator = findViewById(R.id.pomodoroIndicator);
        instructionText = findViewById(R.id.instructionText);
        focusStop = findViewById(R.id.focusStop);
        btnDivider = findViewById(R.id.btnDivider);
        topicsContainer = findViewById(R.id.topicsContainer);

        taskCardOverlay = findViewById(R.id.taskCardOverlay);
        linkedTaskName = findViewById(R.id.linkedTaskName);
        markTaskCompleteBtn = findViewById(R.id.markTaskCompleteBtn);

        circularSeekBar = findViewById(R.id.circularProgress);
        crystalBase = findViewById(R.id.crystalBase);
        crystalColor = findViewById(R.id.crystalColor);
        lottieAnimation = findViewById(R.id.lottie);
        mintCrystalsTxt = findViewById(R.id.mintCrystals);
        mintCrystals = new MintCrystals(this);
        topicsChipGroup = findViewById(R.id.topicsChipGroup);
        settingsBtn = findViewById(R.id.settingsBtn);

        // Break seekbar on main screen
        breakDurationSeekBar = findViewById(R.id.breakDurationSeekBar);
        breakSeekbarLabel = findViewById(R.id.breakSeekbarLabel);
        breakSeekbarCard = findViewById(R.id.breakSeekbarCard);

        // Notification permission card
        notificationPermCard = findViewById(R.id.notificationPermCard);
    }

    private void setupReveal() {
        if (crystalColor != null) {
            crystalColor.setRevealFraction(0f);
            crystalColor.invalidate();
        }
        if (lottieAnimation != null) {
            lottieAnimation.setVisibility(INVISIBLE);
        }
    }

    private void setupCircularSeekBar() {
        try {
            circularSeekBar.setMax(180f);
        } catch (Throwable t) {
            Log.e(TAG, String.valueOf(t));
        }
        // Sync seekbar thumb to the current selectedMinutes
        circularSeekBar.setProgress((float) selectedMinutes);

        circularSeekBar.setOnSeekBarChangeListener(new CircularSeekBar.OnCircularSeekBarChangeListener() {
            @Override
            public void onProgressChanged(CircularSeekBar circularSeekBar, float progress, boolean fromUser) {
                if (fromUser) {
                    int val = (int) Math.round(progress);
                    if (val < 1) val = 1;
                    // Enforce Pomodoro minimum
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(FocusMode.this);
                    boolean isPomodoro = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
                    if (isPomodoro) {
                        int minDuration = prefs.getInt(PREF_FOCUS_DURATION, 25);
                        if (val < minDuration) {
                            val = minDuration;
                            circularSeekBar.setProgress((float) val);
                        }
                    }

                    selectedMinutes = val;
                    timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
                    updateThemeForDuration(selectedMinutes);
                }
            }

            @Override
            public void onStopTrackingTouch(CircularSeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(CircularSeekBar seekBar) {
            }
        });

        updateThemeForDuration(selectedMinutes);
        timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
        setupBreakDurationSeekBar();
    }

    /** Wires up the inline break-duration seekbar (3-30 min, range 0-27 offset by +3). */
    private void setupBreakDurationSeekBar() {
        if (breakDurationSeekBar == null || breakSeekbarLabel == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int savedBreak = prefs.getInt(PREF_BREAK_DURATION, 5);
        // clamp to 3–30
        savedBreak = Math.max(3, Math.min(30, savedBreak));
        breakDurationSeekBar.setMax(27); // 0..27 → 3..30 min
        breakDurationSeekBar.setProgress(savedBreak - 3);
        breakSeekbarLabel.setText("Break: " + savedBreak + " min");

        breakDurationSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int minutes = progress + 3;
                breakSeekbarLabel.setText("Break: " + minutes + " min");
                if (fromUser) {
                    PreferenceManager.getDefaultSharedPreferences(FocusMode.this)
                            .edit().putInt(PREF_BREAK_DURATION, minutes).apply();
                    updatePomodoroIndicator();
                }
            }

            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        // Show only when Pomodoro is enabled
        boolean isPomo = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
        if (breakSeekbarCard != null) {
            breakSeekbarCard.setVisibility(isPomo ? View.VISIBLE : View.GONE);
        }
    }

    /** Refreshes break seekbar visibility based on current Pomodoro preference. */
    private void refreshBreakSeekbarVisibility() {
        if (breakSeekbarCard == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isPomo = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
        boolean isRunning = isBound && focusService != null && focusService.isTimerRunning();
        boolean isOE = isOpenEndedExtra || (focusService != null && focusService.isOpenEnded());
        // Only show when: pomodoro enabled, not running, not open-ended, not task-linked
        boolean shouldShow = isPomo && !isRunning && !isOE
                && (linkedTaskIdExtra == null || linkedTaskIdExtra.isEmpty());
        breakSeekbarCard.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    private void updateThemeForDuration(int minutes) {
        int crystalResource;
        String lottieResource;
        int progressColor;

        if (minutes <= 30) {
            crystalResource = R.drawable.ruby;
            lottieResource = "ruby.json";
            progressColor = ContextCompat.getColor(this, R.color.ruby);
        } else if (minutes <= 60) {
            crystalResource = R.drawable.emerald;
            lottieResource = "emerald.json";
            progressColor = ContextCompat.getColor(this, R.color.emerald);
        } else if (minutes <= 90) {
            crystalResource = R.drawable.amethyst;
            lottieResource = "amethyst.json";
            progressColor = ContextCompat.getColor(this, R.color.amethyst);
        } else if (minutes <= 120) {
            crystalResource = R.drawable.moonstone;
            lottieResource = "moonstone.json";
            progressColor = ContextCompat.getColor(this, R.color.moonstone);
        } else if (minutes <= 150) {
            crystalResource = R.drawable.aquamarine;
            lottieResource = "aquamarine.json";
            progressColor = ContextCompat.getColor(this, R.color.aquamarine);
        } else {
            crystalResource = R.drawable.amber;
            lottieResource = "amber.json";
            progressColor = ContextCompat.getColor(this, R.color.amber);
        }

        if (crystalBase != null)
            crystalBase.setImageResource(crystalResource);
        if (crystalColor != null)
            crystalColor.setImageResource(crystalResource);
        if (lottieAnimation != null) {
            lottieAnimation.setAnimation(lottieResource);
            lottieAnimation.playAnimation();
        }
        if (circularSeekBar != null) {
            circularSeekBar.setPointerColor(progressColor);
            circularSeekBar.setCircleProgressColor(progressColor);
        }
    }

    private void applyCrystalEffectBase() {
        if (crystalBase != null) {
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            crystalBase.setColorFilter(filter);
        }
    }

    private void fadeIn(View view) {
        if (view == null)
            return;
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(400);
        fadeIn.setFillAfter(true);
        view.startAnimation(fadeIn);
        view.setVisibility(VISIBLE);
    }

    private void fadeOut(View view) {
        if (view == null)
            return;
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(500);
        fadeOut.setFillAfter(true);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        view.startAnimation(fadeOut);
    }

    private void showTimerRunningState() {
        boolean isLinked = linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty();
        boolean isOE = isOpenEndedExtra || (focusService != null && focusService.isOpenEnded());

        if (isOE) {
            // Open-ended: show elapsed-time label, hide everything else
            instructionText.setText("Time Elapsed");
        } else if (isLinked && linkedTopicNameExtra != null) {
            instructionText.setText("Focusing: " + linkedTopicNameExtra);
        } else {
            String topicName = "Deep Focus";
            if (selectedTopicId != -1) {
                for (FocusTopicEntity t : currentTopics) {
                    if (t.id == selectedTopicId) {
                        topicName = t.name;
                        break;
                    }
                }
            }
            instructionText.setText("Focusing: " + topicName);
        }

        // Hide settings and topics to prevent changes during run
        // Only touch these if not in task-linked mode (they are already GONE there)
        if (!isLinked) {
            if (settingsBtn != null)
                settingsBtn.setVisibility(INVISIBLE);
            if (topicsContainer != null)
                topicsContainer.setVisibility(INVISIBLE);
        }

        // BUG-7 fix: do NOT show lottie/seekbar in open-ended or task-linked modes
        if (!isOE) {
            fadeIn(lottieAnimation);
        }
        circularSeekBar.setEnabled(false);
        // Use GONE for open-ended so layout does not reserve space
        circularSeekBar.setVisibility(isOE ? GONE : INVISIBLE);
    }

    private void showTimerStoppedState() {
        boolean isLinked = linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty();
        boolean isOE = isOpenEndedExtra || (focusService != null && focusService.isOpenEnded());

        if (isOE) {
            instructionText.setText("Open Ended Focus");
        } else {
            instructionText.setText(getString(R.string.set_your_focus_time));
        }

        // restore settings and topics — only if not in task-linked mode
        if (!isLinked) {
            if (settingsBtn != null)
                settingsBtn.setVisibility(VISIBLE);
            if (topicsContainer != null)
                topicsContainer.setVisibility(VISIBLE);
        }

        fadeOut(lottieAnimation);
        circularSeekBar.setEnabled(!isOE); // Seekbar useless in open-ended
        // BUG-6 fix: do NOT flash seekbar visible in task-linked or open-ended modes
        circularSeekBar.setVisibility(isOE ? GONE : (isLinked ? INVISIBLE : VISIBLE));
    }

    private void showCrystalForSelection() {
        if (crystalColor == null)
            return;

        if (revealAnimator != null && revealAnimator.isRunning()) {
            revealAnimator.cancel();
        }

        float currentFraction = crystalColor.getRevealFraction();

        revealAnimator = ValueAnimator.ofFloat(currentFraction, 1f);
        revealAnimator.setDuration(600);
        revealAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            crystalColor.setRevealFraction(value);
            // lastRevealFraction = value;
        });
        revealAnimator.start();
    }

    private void hideCrystalForRunningInit() {
        if (crystalColor == null)
            return;

        if (revealAnimator != null && revealAnimator.isRunning()) {
            revealAnimator.cancel();
        }

        float currentFraction = crystalColor.getRevealFraction();

        revealAnimator = ValueAnimator.ofFloat(currentFraction, 0f);
        revealAnimator.setDuration(600);
        revealAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            crystalColor.setRevealFraction(value);
            // lastRevealFraction = value;
        });
        revealAnimator.start();
        revealCompleteNotified = false;
        applyCrystalEffectBase();
    }

    private void updateCrystalVisualsForProgress(float progress) {
        if (crystalColor == null)
            return;
        float targetFraction = progress <= 0f ? 0f : Math.min(1f, progress / REVEAL_COMPLETE_AT);
        crystalColor.setRevealFraction(targetFraction);

        if (targetFraction >= 1f && !revealCompleteNotified) {
            revealCompleteNotified = true;
        } else if (targetFraction < 1f) {
            revealCompleteNotified = false;
        }
    }

    private boolean checkNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                // Show notification card and shake it
                checkPermissionAndMoveOn();
                shakePermCard(notificationPermCard);
                Toast.makeText(this, "Notification permission is required for Focus Mode.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void showNotificationPermissionBottomSheet() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "Notification permission not required on this Android version", Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        if (isNotificationPermissionGranted()) {
            Toast.makeText(this, "Notification permission already granted", Toast.LENGTH_SHORT).show();
            return;
        }

        Utils.showPermissionSheet(this, Utils.PermissionType.NOTIFICATION,
                new Utils.PermissionLauncher() {
                    @Override
                    public void launchAccessibility(Intent intent) {
                        // Not used for notification
                    }

                    @Override
                    public void launchBattery(Intent intent) {
                        // Not used for notification
                    }

                    @Override
                    public void launchNotification(String permission) {
                        if (requestNotificationPermissionLauncher != null) {
                            requestNotificationPermissionLauncher.launch(permission);
                        }
                    }
                },
                () -> Toast.makeText(this, "Notification permission not granted", Toast.LENGTH_SHORT).show());
    }

    private boolean isNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT < 33)
            return true;
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void setupPermissionLauncher() {
        requestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Notification permission granted");
                        Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w(TAG, "Notification permission denied");
                        Toast.makeText(this, "Notification permission is required to show focus mode notifications.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkPermissionAndMoveOn() {
        ConstraintLayout permissionCard = findViewById(R.id.permissionCard);
        if (permissionCard == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            boolean allowed = alarmManager != null && alarmManager.canScheduleExactAlarms();
            permissionCard.setVisibility(allowed ? GONE : VISIBLE);
            if (!allowed) {
                permissionCard.setOnClickListener(v -> askForExactAlarmPermission());
            } else {
                permissionCard.setOnClickListener(null);
            }
        } else {
            permissionCard.setVisibility(GONE);
            permissionCard.setOnClickListener(null);
        }

        // Also update notification permission card
        if (notificationPermCard != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
                notificationPermCard.setVisibility(VISIBLE);
                notificationPermCard.setOnClickListener(v -> showNotificationPermissionBottomSheet());
            } else {
                notificationPermCard.setVisibility(GONE);
                notificationPermCard.setOnClickListener(null);
            }
        }
    }

    /**
     * Shake + scale animation on a permission card to draw attention.
     * Mirrors SettingsFragment.shakeCard() behaviour.
     */
    private void shakePermCard(View card) {
        if (card == null || card.getVisibility() != View.VISIBLE) return;

        card.setScaleX(1f);
        card.setScaleY(1f);
        card.setTranslationX(0f);

        android.animation.ObjectAnimator scaleUpX = android.animation.ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.05f);
        android.animation.ObjectAnimator scaleUpY = android.animation.ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.05f);
        scaleUpX.setDuration(200);
        scaleUpY.setDuration(200);

        android.animation.ObjectAnimator shake = android.animation.ObjectAnimator.ofFloat(
                card, "translationX",
                0, 22, -22, 16, -16, 10, -10, 5, -5, 0);
        shake.setDuration(650);

        android.animation.ObjectAnimator scaleDownX = android.animation.ObjectAnimator.ofFloat(card, "scaleX", 1.05f, 1f);
        android.animation.ObjectAnimator scaleDownY = android.animation.ObjectAnimator.ofFloat(card, "scaleY", 1.05f, 1f);
        scaleDownX.setDuration(200);
        scaleDownY.setDuration(200);

        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.play(scaleUpX).with(scaleUpY);
        set.play(shake).after(scaleUpX);
        set.play(scaleDownX).with(scaleDownY).after(shake);
        set.start();

        // Haptic feedback
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null)
                    v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.EFFECT_HEAVY_CLICK));
            }
        } catch (Throwable ignored) {}
    }

    private boolean hasExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    private void askForExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Utils.showPermissionSheet(this, Utils.PermissionType.ALARM,
                    null,
                    () -> {
                        Toast.makeText(FocusMode.this, "Alarm permission is required for Focus Mode timer.",
                                Toast.LENGTH_SHORT).show();
                        checkPermissionAndMoveOn();
                    });
        }
    }

    /**
     * Returns true if both notification (Android 13+) and exact-alarm (Android 12+) permissions
     * are granted — required for Focus Schedules to fire and show heads-up alerts.
     * When a permission is missing the caller sheet is dismissed, the main screen's permission
     * cards are made visible via {@link #checkPermissionAndMoveOn()}, and shaken for attention.
     *
     * @param callerSheet the BottomSheetDialog currently shown (will be dismissed on failure)
     */
    private boolean hasSchedulePermissions(com.google.android.material.bottomsheet.BottomSheetDialog callerSheet) {
        boolean alarmOk = hasExactAlarmPermission();
        boolean notifOk = isNotificationPermissionGranted();

        if (alarmOk && notifOk) return true;

        // Dismiss the settings sheet so the permission cards on the main screen are visible
        if (callerSheet != null && callerSheet.isShowing()) callerSheet.dismiss();

        // Re-evaluate which cards to show
        checkPermissionAndMoveOn();

        // Shake missing-permission cards after a brief delay for layout to settle
        View alarmCard = findViewById(R.id.permissionCard);
        handler.postDelayed(() -> {
            if (!alarmOk) shakePermCard(alarmCard);
            if (!notifOk) shakePermCard(notificationPermCard);
        }, 250);

        String msg = !alarmOk
                ? "Exact Alarm permission is required to use Focus Schedules."
                : "Notification permission is required to use Focus Schedules.";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
        handler.removeCallbacks(updateUITask);
        applyCrystalEffectBase();
        updatePomodoroIndicator();
        // Re-check exact alarm and notification permissions when returning to screen
        checkPermissionAndMoveOn();
        // If redirected from HomeActivity due to missing permissions, shake the visible cards
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_SHAKE_PERM_CARDS, false)) {
            getIntent().removeExtra(EXTRA_SHAKE_PERM_CARDS);
            View alarmCard = findViewById(R.id.permissionCard);
            handler.postDelayed(() -> {
                shakePermCard(alarmCard);
                shakePermCard(notificationPermCard);
            }, 350);
        }
        // Refresh break seekbar from saved preferences (user may have changed in settings)
        setupBreakDurationSeekBar();
        refreshBreakSeekbarVisibility();

        if (isBound && focusService != null) {
            boolean isRunning = focusService.isTimerRunning();

            if (isRunning) {
                // Catch-up: stop overdue timer before setting up running-state UI
                if (checkAndStopOverdueTimer()) return;

                if (revealAnimator != null && revealAnimator.isRunning()) {
                    revealAnimator.cancel();
                }

                updateButtonStates(true);

                // Handle pause state - restore frozen crystal position
                if (focusService.isPaused()) {
                    // Restore saved crystal reveal fraction
                    float savedFraction = focusService.getCrystalRevealFraction();
                    if (crystalColor != null && savedFraction > 0) {
                        crystalColor.setRevealFraction(savedFraction);
                    }
                } else {
                    // Normal running state - calculate reveal from progress
                    long totalDuration = focusService.getCurrentDuration();
                    long elapsedMillis = focusService.getElapsedMillis();

                    if (totalDuration <= 0)
                        totalDuration = 1;
                    if (elapsedMillis < 0)
                        elapsedMillis = 0;
                    if (elapsedMillis > totalDuration)
                        elapsedMillis = totalDuration;

                    float progress = elapsedMillis / (float) totalDuration;
                    float targetReveal = Math.min(1f, progress / REVEAL_COMPLETE_AT);

                    if (crystalColor != null) {
                        crystalColor.setRevealFraction(targetReveal);
                    }
                }

                handler.post(updateUITask);
            } else {
                updateButtonStates(false);
                // Only reset to "X min" text in standalone (non task-linked) mode
                if (linkedTaskIdExtra == null || linkedTaskIdExtra.isEmpty()) {
                    timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateUITask);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(updateUITask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateUITask);
        dbExecutor.shutdownNow();
        if (isBound) {
            try {
                unbindService(connection);
                isBound = false;
            } catch (IllegalArgumentException e) {
                // Service was not bound
            }
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FocusService.TimerBinder binder = (FocusService.TimerBinder) service;
            focusService = binder.getService();
            isBound = true;

            // Restore session context from service if missing from intent extras
            restoreStateFromService();

            boolean isRunning = focusService.isTimerRunning();

            if (isRunning) {
                if (revealAnimator != null && revealAnimator.isRunning()) {
                    revealAnimator.cancel();
                }
                long totalDuration = focusService.getCurrentDuration();

                // For open-ended sessions, selectedMinutes is irrelevant (no seekbar shown)
                // For timed sessions, sync selectedMinutes from service duration
                if (!focusService.isOpenEnded()) {
                    selectedMinutes = (int) (totalDuration / (1000 * 60));
                }

                // Catch-up: stop overdue timer before setting up running-state UI
                if (checkAndStopOverdueTimer()) return;
            } else {
                // Only reset to "X min" for standalone (non task-linked) sessions
                boolean isLinked = linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty();
                boolean isOE = isOpenEndedExtra || (focusService != null && focusService.isOpenEnded());
                if (!isLinked && !isOE) {
                    timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
                }
            }

            updateButtonStates(isRunning);

            // If timer is NOT running, animate crystal to full reveal for selection
            // (but not in task-linked or open-ended mode where crystals are hidden)
            if (!isRunning) {
                boolean isLinked = linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty();
                boolean isOE = isOpenEndedExtra || focusService.isOpenEnded();
                if (!isLinked && !isOE) {
                    showCrystalForSelection();
                }
            }

            if (isRunning) {
                updateTimerUI();

                // Handle pause state - restore frozen crystal position
                if (focusService.isPaused()) {
                    float savedFraction = focusService.getCrystalRevealFraction();
                    if (crystalColor != null && savedFraction > 0) {
                        crystalColor.setRevealFraction(savedFraction);
                    }
                } else {
                    // Normal running - sync crystal from progress (only for timed sessions)
                    if (!focusService.isOpenEnded()) {
                        long elapsedMillis = focusService.getElapsedMillis();
                        long totalDuration = focusService.getCurrentDuration();
                        if (totalDuration > 0 && crystalColor != null) {
                            float progress = elapsedMillis / (float) totalDuration;
                            float targetReveal = Math.min(1f, progress / REVEAL_COMPLETE_AT);
                            crystalColor.setRevealFraction(targetReveal);
                        }
                    }
                }

                handler.removeCallbacks(updateUITask);
                handler.post(updateUITask);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            focusService = null;
            updateButtonStates(false);
        }
    };

    private void updateTimerUI() {
        if (isBound && focusService != null && focusService.isTimerRunning()) {
            if (focusService.isPaused()) {
                updatePomodoroIndicator();
                updateButtonStates(true);

                if (focusService.isBreak()) {
                    // BREAK_PAUSED state - show break countdown, freeze crystal
                    long breakRemaining = focusService.getBreakRemainingMillis();
                    long minutes = breakRemaining / (1000 * 60);
                    long seconds = (breakRemaining / 1000) % 60;
                    timerText.setText(String.format(Locale.US, "Break %02d:%02d", minutes, seconds));
                } else {
                    // WAITING_USER state - show tap to resume
                    timerText.setText("Tap Resume");
                }
                return;
            }

            // FOCUS_RUNNING state - normal logic
            long elapsedMillis = focusService.getElapsedMillis();

            if (isOpenEndedExtra || (focusService != null && focusService.isOpenEnded())) {
                // Open Ended Mode: Timer counts UP
                long minutes = elapsedMillis / (1000 * 60);
                long seconds = (elapsedMillis / 1000) % 60;
                String timeStr = String.format(Locale.US, "%02d:%02d", minutes, seconds);
                timerText.setText(timeStr);
                instructionText.setText("Time Elapsed");
            } else {
                // Timed Mode: Timer counts DOWN
                long totalDuration = focusService.getCurrentDuration();

                if (totalDuration <= 0) {
                    totalDuration = 1;
                }
                if (elapsedMillis < 0) {
                    elapsedMillis = 0;
                }
                if (elapsedMillis > totalDuration) {
                    elapsedMillis = totalDuration;
                }

                long remainingMillis = totalDuration - elapsedMillis;

                // Update timer text
                long minutes = remainingMillis / (1000 * 60);
                long seconds = (remainingMillis / 1000) % 60;
                String timeStr = String.format(Locale.US, "%02d:%02d", minutes, seconds);
                timerText.setText(timeStr);
            }

            updatePomodoroIndicator();

            if (!isOpenEndedExtra && focusService != null && !focusService.isOpenEnded()) {
                long totalDuration = focusService.getCurrentDuration();
                float progress = elapsedMillis / (float) (totalDuration > 0 ? totalDuration : 1);
                float seekbarProgress = progress * 180f;
                seekbarProgress = Float.isNaN(seekbarProgress) || Float.isInfinite(seekbarProgress)
                        ? 0f : Math.max(0f, Math.min(seekbarProgress, 180f));
                circularSeekBar.setProgress(seekbarProgress);

                updateCrystalVisualsForProgress(progress);

                // Store reveal fraction for pause recovery
                if (crystalColor != null) {
                    focusService.setCrystalRevealFraction(crystalColor.getRevealFraction());
                }
            }
        } else if (focusService != null && !focusService.isTimerRunning()) {
            // BUG-3 fix: don't overwrite timer text with "X min" in task-linked/open-ended mode
            boolean isLinkedUI = linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty();
            boolean isOEUI = isOpenEndedExtra || focusService.isOpenEnded();
            if (!isLinkedUI && !isOEUI) {
                timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
            }
        }
    }

    /**
     * Checks if the timer has exceeded its duration (Doze may have delayed the stop).
     * If overdue, stops the timer and handles UI + completion dialog.
     *
     * @return true if the timer was stopped; caller should skip normal running-state logic.
     */
    private boolean checkAndStopOverdueTimer() {
        if (focusService == null || !focusService.isTimerRunning()) return false;
        long dur = focusService.getCurrentDuration();
        if (dur != Long.MAX_VALUE && !focusService.isPaused() && !focusService.isBreak()
                && focusService.getElapsedMillis() >= dur) {
            focusService.stopTimer();
            updateButtonStates(false);
            // BUG-4 fix: only reveal crystal for standalone (non task-linked, non open-ended) sessions
            boolean isLinked = linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty();
            boolean isOE = isOpenEndedExtra || focusService.isOpenEnded();
            if (!isLinked && !isOE) {
                showCrystalForSelection();
                mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
            }
            if (focusService.consumeCompletedNaturally()) {
                if (linkedTaskIdExtra != null) {
                    showTaskFinishedDialog(focusService.getLastCompletedDurationMinutes());
                } else {
                    showFocusCompleteDialog(focusService.getLastCompletedDurationMinutes());
                    mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
                }
            }
            return true;
        }
        return false;
    }

    private void updateButtonStates(boolean timerIsRunning) {
        boolean isTaskLinked = (linkedTaskIdExtra != null && !linkedTaskIdExtra.isEmpty())
                || (focusService != null && focusService.getLinkedTaskId() != null && !focusService.getLinkedTaskId().isEmpty());
        boolean isOpenEnded = isOpenEndedExtra || (focusService != null && focusService.isOpenEnded());
        // BUG-11 FIX: use activity-cached wasLockedInSession so the coin UI stays hidden even after
        // stopTimer() clears PREF_IS_LOCKED_IN. When running, also refresh the cache from service.
        if (timerIsRunning && focusService != null) {
            wasLockedInSession = focusService.isLockedIn()
                    || PreferenceManager.getDefaultSharedPreferences(this).getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
        }
        boolean isLockedInSession = wasLockedInSession
                || (focusService != null && focusService.isLockedIn())
                || PreferenceManager.getDefaultSharedPreferences(this).getBoolean(FocusService.PREF_IS_LOCKED_IN, false);

        if (timerIsRunning && focusService != null) {
            if (revealAnimator != null && revealAnimator.isRunning()) {
                revealAnimator.cancel();
            }
            showTimerRunningState();

            ConstraintLayout mainLayout = findViewById(R.id.main);
            ConstraintSet set = new ConstraintSet();
            set.clone(mainLayout);

            if (isOpenEnded) {
                // OpenEnded overrides: hide crystals, coins, seekbar; center timer
                circularSeekBar.setVisibility(View.GONE);
                lottieAnimation.setVisibility(View.GONE);
                crystalBase.setVisibility(View.GONE);
                crystalColor.setVisibility(View.GONE);
                View progressContainer = findViewById(R.id.progressContainer);
                if (progressContainer != null) progressContainer.setVisibility(View.GONE);
                // Coins always hidden for open-ended (always task-linked)
                findViewById(R.id.coinImg).setVisibility(View.GONE);
                mintCrystalsTxt.setVisibility(View.GONE);

                // Center timer text
                set.connect(R.id.timerText, ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP);
                set.connect(R.id.timerText, ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM);
                timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 65f);
            } else {
                updateThemeForDuration(selectedMinutes);

                // Restore timer text below progressContainer
                set.clear(R.id.timerText, ConstraintSet.BOTTOM);
                set.connect(R.id.timerText, ConstraintSet.TOP, R.id.progressContainer, ConstraintSet.BOTTOM);
                timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50f);

                // Hide coins for task-linked timed sessions or locked-in state
                if (isTaskLinked || isLockedInSession) {
                    findViewById(R.id.coinImg).setVisibility(View.GONE);
                    mintCrystalsTxt.setVisibility(View.GONE);
                } else {
                    mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
                }
            }
            set.applyTo(mainLayout);

            boolean isPausedWaitingUser = focusService.isPaused() && !focusService.isBreak();
            if (isLockedInSession) {
                focusStop.setVisibility(View.GONE);
                btnDivider.setVisibility(View.GONE);
                if (isPausedWaitingUser) {
                    findViewById(R.id.buttonCard).setVisibility(View.VISIBLE);
                    startButton.setVisibility(View.VISIBLE);
                    startButton.setText("Resume");
                } else {
                    findViewById(R.id.buttonCard).setVisibility(View.GONE);
                }
            } else {
                findViewById(R.id.buttonCard).setVisibility(View.VISIBLE);
                startButton.setVisibility(View.VISIBLE);
                if (isPausedWaitingUser) {
                    startButton.setText("Resume");
                    focusStop.setVisibility(View.VISIBLE);
                    btnDivider.setVisibility(View.VISIBLE);
                } else {
                    startButton.setText(getString(R.string.stop));
                    focusStop.setVisibility(View.GONE);
                    btnDivider.setVisibility(View.GONE);
                }
            }
            refreshBreakSeekbarVisibility();
        } else {
            // Timer stopped state
            showTimerStoppedState();

            ConstraintLayout mainLayout = findViewById(R.id.main);
            ConstraintSet set = new ConstraintSet();
            set.clone(mainLayout);

            if (isOpenEnded) {
                // For open-ended sessions that have stopped, keep timer centered and crystals gone
                set.connect(R.id.timerText, ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP);
                set.connect(R.id.timerText, ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM);
                timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 65f);
            } else {
                set.clear(R.id.timerText, ConstraintSet.BOTTOM);
                set.connect(R.id.timerText, ConstraintSet.TOP, R.id.progressContainer, ConstraintSet.BOTTOM);
                timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50f);

                // Only update seekbar/timer text for timed non-task mode
                if (!isTaskLinked) {
                    updateThemeForDuration(selectedMinutes);
                    // Sync circular seekbar thumb to selectedMinutes (direct minute value)
                    circularSeekBar.setProgress((float) selectedMinutes);
                    timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
                }
            }
            set.applyTo(mainLayout);

            startButton.setText(getString(R.string.start));
            focusStop.setVisibility(View.GONE);
            btnDivider.setVisibility(View.GONE);

            // Restore coin display only for non-task-linked, non-locked-in sessions
            if (!isTaskLinked && !isLockedInSession) {
                mintCrystalsTxt.setVisibility(View.VISIBLE);
                mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
                findViewById(R.id.coinImg).setVisibility(View.VISIBLE);
            }

            // Refresh break seekbar visibility now timer has stopped
            refreshBreakSeekbarVisibility();
            handler.removeCallbacks(updateUITask);
        }
    }

    private final Runnable updateUITask = new Runnable() {
        @Override
        public void run() {
            if (isBound && focusService != null) {
                if (focusService.isTimerRunning()) {
                    // Catch-up: stop overdue timer instead of updating UI
                    if (checkAndStopOverdueTimer()) return;

                    updateTimerUI();
                    handler.postDelayed(this, 900);
                } else {
                    updateButtonStates(false);
                    // Timer stopped - animate crystal to full reveal for selection (only if not linked task)
                    if (linkedTaskIdExtra == null) {
                        showCrystalForSelection();
                    }
                    // If completed naturally, show appropriate dialog
                    if (focusService.consumeCompletedNaturally()) {
                        int minutes = focusService.getLastCompletedDurationMinutes();
                        if (linkedTaskIdExtra != null) {
                            // Task-linked: ask if complete or extend
                            showTaskFinishedDialog(minutes);
                        } else {
                            showFocusCompleteDialog(minutes);
                            mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
                        }
                    }
                }
            }
        }
    };

    private void bindService() {
        if (!isBound) {
            Intent serviceIntent = new Intent(this, FocusService.class);
            bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        }
    }

    private long getDuration() {
        return selectedMinutes * 60L * 1000L;
    }

    private void showPeaceCoinsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_peace_coins);
        dialog.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        setupTimelineNode(dialog, R.id.node30, "30m", "+2");
        setupTimelineNode(dialog, R.id.node60, "60m", "+5"); // Active
        setupTimelineNode(dialog, R.id.node90, "90m", "+7");
        setupTimelineNode(dialog, R.id.node120, "120m", "+10");
        setupTimelineNode(dialog, R.id.node150, "150m", "+15");
        setupTimelineNode(dialog, R.id.node180, "180m", "+20"); // Goal

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
            dialog.getWindow().setLayout(MATCH_PARENT, WRAP_CONTENT);
        }

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void setupTimelineNode(Dialog dialog, int includeId, String time, String reward) {
        View nodeView = dialog.findViewById(includeId);
        if (nodeView == null)
            return;

        TextView tvReward = nodeView.findViewById(R.id.tvReward);
        TextView tvTime = nodeView.findViewById(R.id.tvTime);
        View viewDot = nodeView.findViewById(R.id.viewDot);
        tvTime.setText(time);
        tvReward.setText(reward);

        int colorPink = ContextCompat.getColor(this, R.color.brand_pink);

        tvReward.setTextColor(colorPink);
        viewDot.setBackgroundResource(R.drawable.glass_circle);
        viewDot.setBackgroundTintList(ColorStateList.valueOf(colorPink));

        viewDot.setScaleX(1.3f);
        viewDot.setScaleY(1.3f);

    }

    private void showTaskFinishedDialog(int minutes) {
        com.gxdevs.mindmint.Utils.CustomDialogUtils.showCustomDialog(this,
                "Time's Up!",
                "Is the task complete, or do you want to extend the time?",
                "Task Complete",
                "Extend Time",
                "Keep Pending",
                () -> {
                    completeLinkedTask();
                    // BUG FIX: don't call finish() with a 200ms timer while the dialog is still
                    // open — let the user dismiss the completion dialog naturally via its OK button.
                    // The completion dialog's OK button calls dialog.dismiss(); the activity is
                    // finished from the okBtn listener we inject here.
                    showFocusCompleteDialogThenFinish(minutes);
                },
                this::showExtendTimeDialog,
                () -> {
                    // Time ran out — save time but keep task incomplete
                    if (isBound && focusService != null) {
                        focusService.stopService();
                    }
                    sendBroadcast(new Intent(FocusService.ACTION_TASK_FOCUS_UPDATE));
                    finish();
                });
    }

    private void showExtendTimeDialog() {
        CharSequence[] options = {"5 minutes", "10 minutes", "15 minutes", "30 minutes"};
        final int[] durations = {5, 10, 15, 30};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("Extend Time")
                .setItems(options, (dialog, which) -> {
                    int extendMins = durations[which];
                    // Start a new timer for the extended duration
                    Intent serviceIntent = new Intent(this, FocusService.class);
                    serviceIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
                    serviceIntent.putExtra("durationInMillis", extendMins * 60000L);

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    boolean isPomodoro = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
                    long focusInterval = isTestMode ? 30000L : prefs.getInt(PREF_FOCUS_DURATION, 25) * 60 * 1000L;
                    long breakInterval = isTestMode ? 15000L : prefs.getInt(PREF_BREAK_DURATION, 5) * 60 * 1000L;

                    serviceIntent.putExtra("isPomodoroEnabled", isPomodoro || isTestMode);
                    serviceIntent.putExtra("pomodoroFocusInterval", focusInterval);
                    serviceIntent.putExtra("pomodoroBreakInterval", breakInterval);
                    serviceIntent.putExtra("topicName", linkedTopicNameExtra != null ? linkedTopicNameExtra : "Extended Task");
                    serviceIntent.putExtra(FocusService.EXTRA_TASK_ID, linkedTaskIdExtra);
                    serviceIntent.putExtra(FocusService.EXTRA_IS_OPEN_ENDED, false);

                    ContextCompat.startForegroundService(this, serviceIntent);
                    // BUG-5 FIX: rebind to the newly-started service so focusService stays valid
                    isBound = false;
                    focusService = null;
                    bindService();
                    // NOTE: do NOT call updateButtonStates(true) here — focusService is null until
                    // onServiceConnected fires. The binding callback will update UI correctly.
                    // Restart the UI tick loop so the extended countdown is displayed
                    handler.removeCallbacks(updateUITask);
                    handler.postDelayed(updateUITask, 500);
                    Toast.makeText(this, "Extended by " + extendMins + " min", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    sendBroadcast(new Intent(FocusService.ACTION_TASK_FOCUS_UPDATE));
                    finish();
                })
                .show();
    }

    /**
     * Wrapper: show the completion dialog and finish the activity when the user taps OK.
     * For task-linked completions: always show exactly 2 coins (the actual award from
     * completeLinkedTask), not the duration-based amount used for standalone sessions.
     */
    private void showFocusCompleteDialogThenFinish(int minutes) {
        showFocusCompleteDialog(minutes, 2, this::finish);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showFocusCompleteDialog(int minutes) {
        showFocusCompleteDialog(minutes, -1, null);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showFocusCompleteDialog(int minutes, int overrideCoins, Runnable onDismiss) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_focus_complete);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.6f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.getWindow().setBackgroundBlurRadius(10);
            }
            dialog.getWindow().setLayout(MATCH_PARENT, WRAP_CONTENT);
        }

        ImageView crystalImg = dialog.findViewById(R.id.crystalImage);
        LottieAnimationView sparkle = dialog.findViewById(R.id.sparkleAnim);
        TextView title = dialog.findViewById(R.id.titleText);
        TextView desc = dialog.findViewById(R.id.descText);
        TextView coinsText = dialog.findViewById(R.id.coinsText);
        MaterialButton okBtn = dialog.findViewById(R.id.okBtn);

        int coins;
        int crystalRes;

        int progressColor;
        String lottieResource;
        if (minutes <= 30) {
            coins = 2;
            crystalRes = R.drawable.ruby;
            lottieResource = "ruby.json";
            progressColor = ContextCompat.getColor(this, R.color.ruby);
        } else if (minutes <= 60) {
            coins = 5;
            crystalRes = R.drawable.emerald;
            lottieResource = "emerald.json";
            progressColor = ContextCompat.getColor(this, R.color.emerald);
        } else if (minutes <= 90) {
            coins = 7;
            crystalRes = R.drawable.amethyst;
            lottieResource = "amethyst.json";
            progressColor = ContextCompat.getColor(this, R.color.amethyst);
        } else if (minutes <= 120) {
            coins = 10;
            crystalRes = R.drawable.moonstone;
            lottieResource = "moonstone.json";
            progressColor = ContextCompat.getColor(this, R.color.moonstone);
        } else if (minutes <= 150) {
            coins = 15;
            crystalRes = R.drawable.aquamarine;
            lottieResource = "aquamarine.json";
            progressColor = ContextCompat.getColor(this, R.color.aquamarine);
        } else {
            coins = 20;
            crystalRes = R.drawable.amber;
            lottieResource = "amber.json";
            progressColor = ContextCompat.getColor(this, R.color.amber);
        }

        if (crystalImg != null)
            crystalImg.setImageResource(crystalRes);
        if (title != null)
            title.setText(R.string.congratulations);
        if (desc != null) {
            String compText = "Focus complete: " + minutes + " min";
            desc.setText(compText);
        }
        if (coinsText != null) {
            coinsText.setBackgroundTintList(android.content.res.ColorStateList.valueOf(progressColor));
        }
        // NOTE: okBtn click listener is set below (after touch listener) — don't set it here

        View root = dialog.findViewById(android.R.id.content);
        if (root != null) {
            root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS);
        }

        // Crystal scale-in and float micro-motion
        if (crystalImg != null) {
            crystalImg.setScaleX(0.9f);
            crystalImg.setScaleY(0.9f);
            crystalImg.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(450)
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .withEndAction(() -> crystalImg.animate()
                            .translationYBy(-8f)
                            .setDuration(900)
                            .setInterpolator(new DecelerateInterpolator())
                            .start())
                    .start();
        }

        // Sparkle effect around crystal (best-effort if animation present)
        if (sparkle != null) {
            try {
                sparkle.setAnimation(lottieResource);
                sparkle.playAnimation();
            } catch (Throwable ignored) {
                sparkle.setVisibility(View.GONE);
            }
        }

        // Count-up reward text — use overrideCoins if provided (e.g. task-linked = always 2)
        if (coinsText != null) {
            int displayCoins = (overrideCoins >= 0) ? overrideCoins : coins;
            ValueAnimator va = ValueAnimator.ofInt(0, displayCoins);
            va.setDuration(900);
            va.addUpdateListener(a -> {
                int val = (int) a.getAnimatedValue();
                String crystalTxt = "+" + val + " Mint Crystals";
                coinsText.setText(crystalTxt);
            });
            va.start();
        }

        if (okBtn != null) {
            okBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (onDismiss != null) onDismiss.run();
            });
        }

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        vibrateCompletion();

    }

    private void vibrateCompletion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    long[] pattern = new long[]{0, 220, 120, 220};
                    VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                    vm.getDefaultVibrator().vibrate(effect);
                }
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(VibrationEffect.createOneShot(260, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadTopics() {
        db.focusTopicDao().getAllTopics().observe(this, topics -> {
            currentTopics = topics;
            refreshChipGroup();
        });
    }

    private void refreshChipGroup() {
        topicsChipGroup.removeAllViews();

        for (FocusTopicEntity topic : currentTopics) {
            Chip chip = new Chip(this, null);
            chip.setText(topic.name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.transparent)));
            chip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.glass_stroke)));
            chip.setChipStrokeWidth(Utils.dpToPx(1, this));
            chip.setChipCornerRadius(Utils.dpToPx(20, this));
            chip.setChipBackgroundColorResource(R.color.white_10); // 10% white for glass effect
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            if (topic.id == selectedTopicId) {
                chip.setChecked(true);
                chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
                chip.setTextColor(Color.WHITE);
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedTopicId = topic.id;
                    chip.setChipBackgroundColor(
                            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
                    chip.setTextColor(Color.WHITE);
                } else if (selectedTopicId == topic.id) {
                    selectedTopicId = -1;
                    chip.setChipBackgroundColorResource(R.color.white_10);
                    chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                }
            });

            chip.setOnLongClickListener(v -> {
                showDeleteTopicDialog(topic);
                return true;
            });

            topicsChipGroup.addView(chip);
        }

        Chip newChip = new Chip(this);
        newChip.setText("New");
        newChip.setChipIcon(ContextCompat.getDrawable(this, R.drawable.ic_add));
        newChip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
        newChip.setTextColor(ContextCompat.getColor(this, R.color.brainColor));
        newChip.setChipBackgroundColorResource(R.color.white_10);
        newChip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
        newChip.setChipStrokeWidth(Utils.dpToPx(1, this));
        newChip.setChipCornerRadius(Utils.dpToPx(20, this));
        newChip.setOnClickListener(v -> showAddTopicDialog());

        topicsChipGroup.addView(newChip);
    }

    private void showAddTopicDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("New Focus Topic");
        final TextInputEditText input = new TextInputEditText(this);
        input.setHint("Topic Name");
        ConstraintLayout container = new ConstraintLayout(this);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(Utils.dpToPx(20, this), 0, Utils.dpToPx(20, this), 0);
        input.setLayoutParams(params);
        container.addView(input);
        container.setPadding(Utils.dpToPx(20, this), Utils.dpToPx(10, this), Utils.dpToPx(20, this), 0);

        builder.setView(container);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = input.getText() != null ? input.getText().toString().trim() : "";
            if (!name.isEmpty()) {
                dbExecutor.execute(() -> db.focusTopicDao().insert(new FocusTopicEntity(name)));
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showDeleteTopicDialog(FocusTopicEntity topic) {
        com.gxdevs.mindmint.Utils.CustomDialogUtils.showCustomDialog(this,
                "Delete Topic",
                "Are you sure you want to delete '" + topic.name + "'?",
                "Delete",
                "Cancel",
                () -> dbExecutor.execute(() -> {
                    db.focusTopicDao().delete(topic);
                    if (selectedTopicId == topic.id) {
                        runOnUiThread(() -> selectedTopicId = -1);
                    }
                }),
                null);
    }

    private void showSettingsBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        // BUG-8 FIX: must call setContentView before any findViewById calls
        sheet.setContentView(R.layout.bottom_sheet_focus);
        MaterialSwitch bSwitch = sheet.findViewById(R.id.breakSwitch);
        View bSettings = sheet.findViewById(R.id.breakSettings);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        MaterialSwitch themeSwitchSheet = sheet.findViewById(R.id.themeSwitch);
        NebulaStarfieldView galaxy = findViewById(R.id.starFieldView);

        if (themeSwitchSheet != null && galaxy != null) {
            Boolean savedOverride = prefs.contains("nebula_theme_override")
                    ? prefs.getBoolean("nebula_theme_override", false)
                    : null;

            boolean isDark;
            if (savedOverride != null) {
                isDark = savedOverride;
            } else {
                String mode = prefs.getString("pref_theme_mode", "Dark Theme");
                if ("Dark Theme".equalsIgnoreCase(mode) || "dark".equalsIgnoreCase(mode)) {
                    isDark = true;
                } else if ("Light Theme".equalsIgnoreCase(mode) || "light".equalsIgnoreCase(mode)) {
                    isDark = false;
                } else {
                    int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    isDark = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
                }
            }

            themeSwitchSheet.setChecked(isDark);
            galaxy.setThemeOverrideDark(isDark);

            themeSwitchSheet.setOnCheckedChangeListener((buttonView, checked) -> {
                prefs.edit().putBoolean("nebula_theme_override", checked).apply();
                galaxy.setThemeOverrideDark(checked);
                getDelegate().setLocalNightMode(
                        checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }

        MaterialSwitch pomodoroSwitch = sheet.findViewById(R.id.pomodoroSwitch);
        View pomodoroSettings = sheet.findViewById(R.id.pomodoroSettings);
        View breakCard = sheet.findViewById(R.id.breakCard);

        if (pomodoroSwitch != null && pomodoroSettings != null) {
            boolean enabled = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
            pomodoroSwitch.setChecked(enabled);
            pomodoroSettings.setVisibility(enabled ? VISIBLE : GONE);
            if (breakCard != null)
                breakCard.setVisibility(enabled ? VISIBLE : GONE);

            pomodoroSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_POMODORO_ENABLED, isChecked).apply();
                pomodoroSettings.setVisibility(isChecked ? VISIBLE : GONE);
                if (breakCard != null)
                    breakCard.setVisibility(isChecked ? VISIBLE : GONE);

                // Auto-enable Auto Resume when Pomodoro is turned ON
                if (isChecked) {

                    if (bSwitch != null) {
                        bSwitch.setChecked(true);
                        prefs.edit().putBoolean("pref_auto_start_break", true).apply();
                        if (bSettings != null)
                            bSettings.setVisibility(VISIBLE);
                    }
                    showPomodoroFirstTimeDialogIfNeeded();
                }
            });
        }

        SeekBar focusSeekBar = sheet.findViewById(R.id.focusSeekBar);
        TextView focusLabel = sheet.findViewById(R.id.focusDurationLabel);
        if (focusSeekBar != null && focusLabel != null) {
            // BUG-3 FIX: set max (20-120 min range → progress 0-100) and clamp saved value
            focusSeekBar.setMax(100); // 0..100 → 20..120 min
            int savedDuration = Math.max(20, Math.min(120, prefs.getInt(PREF_FOCUS_DURATION, 30)));
            focusSeekBar.setProgress(savedDuration - 20);
            focusLabel.setText("Focus Interval: " + savedDuration + " min");
            focusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress + 20;
                    focusLabel.setText("Focus Interval: " + val + " min");
                    prefs.edit().putInt(PREF_FOCUS_DURATION, val).apply();
                    updatePomodoroIndicator();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }


        if (bSwitch != null && bSettings != null) {
            // Auto Resume toggle - controls whether focus auto-resumes after break
            boolean autoResumeEnabled = prefs.getBoolean("pref_auto_start_break", true);
            bSwitch.setChecked(autoResumeEnabled);
            bSettings.setVisibility(autoResumeEnabled ? VISIBLE : GONE); // Hide duration if Auto Resume is OFF

            bSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("pref_auto_start_break", isChecked).apply();
                bSettings.setVisibility(isChecked ? VISIBLE : GONE);

                if (!isChecked) {
                    showAutoKillWarning();
                }
            });
        }

        MaterialSwitch alwaysLockInSwitch = sheet.findViewById(R.id.alwaysLockInSwitch);
        View alwaysLockInCard = sheet.findViewById(R.id.alwaysLockInCard);
        if (alwaysLockInSwitch != null && alwaysLockInCard != null) {
            if (!Utils.isAccessibilityPermissionGranted(this)) {
                alwaysLockInCard.setVisibility(View.GONE);
            } else {
                boolean isAlwaysLockIn = prefs.getBoolean("pref_always_lock_in", false);
                alwaysLockInSwitch.setChecked(isAlwaysLockIn);
                alwaysLockInSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked && !Utils.isAccessibilityPermissionGranted(this)) {
                        alwaysLockInSwitch.setChecked(false);
                        prefs.edit().putBoolean("pref_always_lock_in", false).apply();
                        Toast.makeText(this, "Accessibility Permission Required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.edit().putBoolean("pref_always_lock_in", isChecked).apply();
                });
            }
        }

        View customAppBlockCard = sheet.findViewById(R.id.customAppBlockCard);
        if (customAppBlockCard != null) {
            customAppBlockCard.setOnClickListener(v -> {
                Intent i = new Intent(this, CustomAppSelectionActivity.class);
                i.putExtra(CustomAppSelectionActivity.EXTRA_MODE, CustomAppSelectionActivity.MODE_BLACKLIST);
                startActivity(i);
            });
        }

        View strictWhitelistCard = sheet.findViewById(R.id.strictWhitelistCard);
        if (strictWhitelistCard != null) {
            strictWhitelistCard.setOnClickListener(v -> {
                Intent i = new Intent(this, CustomAppSelectionActivity.class);
                i.putExtra(CustomAppSelectionActivity.EXTRA_MODE, CustomAppSelectionActivity.MODE_WHITELIST);
                startActivity(i);
            });
        }

        SeekBar breakSeekBar = sheet.findViewById(R.id.breakSeekBar);
        TextView breakLabel = sheet.findViewById(R.id.breakDurationLabel);
        if (breakSeekBar != null && breakLabel != null) {
            // BUG-2 FIX: set max (3-30 min range → progress 0-27) and clamp saved value
            breakSeekBar.setMax(27); // 0..27 → 3..30 min
            int savedDuration = Math.max(3, Math.min(30, prefs.getInt(PREF_BREAK_DURATION, 5)));
            breakSeekBar.setProgress(savedDuration - 3);
            breakLabel.setText("Break Duration: " + savedDuration + " min");
            breakSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress + 3;
                    breakLabel.setText("Break Duration: " + val + " min");
                    prefs.edit().putInt(PREF_BREAK_DURATION, val).apply();
                    updatePomodoroIndicator();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        View crossBtn = sheet.findViewById(R.id.crossBtn);
        if (crossBtn != null) {
            crossBtn.setOnClickListener(v -> sheet.dismiss());
        }

        // --- Scheduled Focus UI ---
        RecyclerView scheduleRv = sheet.findViewById(R.id.scheduleRecyclerView);
        View addScheduleBtn = sheet.findViewById(R.id.addScheduleBtn);
        View schedulesContainer = sheet.findViewById(R.id.schedulesContainer);

        if (scheduleRv != null && addScheduleBtn != null) {
            scheduleRv.setLayoutManager(new LinearLayoutManager(this));
            Runnable reloadSchedules = getRunnable(scheduleRv, schedulesContainer);

            addScheduleBtn.setOnClickListener(v -> {
                // Require both permissions before creating a schedule
                if (!hasSchedulePermissions(sheet)) return;
                showAddScheduleDialog(null, reloadSchedules);
            });
            reloadSchedules.run();
        }

        sheet.setOnDismissListener(dialog -> {
            // Re-validate duration when settings close
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isPomo = p.getBoolean(PREF_POMODORO_ENABLED, false);
            if (isPomo) {
                int minDur = p.getInt(PREF_FOCUS_DURATION, 25);
                if (selectedMinutes < minDur) {
                    selectedMinutes = minDur;
                    circularSeekBar.setProgress((float) minDur);
                    timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
                    updateThemeForDuration(selectedMinutes);
                }
            }
            updatePomodoroIndicator();
            // Sync inline break seekbar to any changes made in settings sheet
            setupBreakDurationSeekBar();
            refreshBreakSeekbarVisibility();
        });

        sheet.show();
    }

    private Runnable getRunnable(RecyclerView scheduleRv, View schedulesContainer) {
        return () -> dbExecutor.execute(() -> {
            List<FocusScheduleEntity> schedules = db.focusScheduleDao().getAll();
            runOnUiThread(() -> {
                if (schedulesContainer != null) {
                    schedulesContainer.setVisibility(schedules.isEmpty() ? View.GONE : View.VISIBLE);
                }
                FocusScheduleAdapter scheduleAdapter = new FocusScheduleAdapter(
                        this, schedules,
                        new FocusScheduleAdapter.OnScheduleActionListener() {
                            @Override
                            public void onToggle(FocusScheduleEntity schedule, boolean isChecked) {
                                dbExecutor.execute(() -> {
                                    schedule.isEnabled = isChecked ? 1 : 0;
                                    db.focusScheduleDao().update(schedule);
                                    FocusScheduleManager mgr = new FocusScheduleManager(FocusMode.this);
                                    if (isChecked) {
                                        mgr.setSchedule(schedule);
                                    } else {
                                        mgr.cancelSchedule(schedule.id);
                                    }
                                });
                            }

                            @Override
                            public void onDelete(FocusScheduleEntity schedule) {
                                CustomDialogUtils.showCustomDialog(FocusMode.this,
                                        "Delete Schedule",
                                        "Are you sure you want to delete this schedule?",
                                        "Delete",
                                        "Cancel",
                                        () -> dbExecutor.execute(() -> {
                                            db.focusScheduleDao().delete(schedule);
                                            new FocusScheduleManager(FocusMode.this).cancelSchedule(schedule.id);
                                            List<FocusScheduleEntity> fresh = db.focusScheduleDao().getAll();
                                            runOnUiThread(() -> scheduleRv.setAdapter(
                                                    new FocusScheduleAdapter(FocusMode.this, fresh, this)));
                                        }),
                                        null);
                            }

                            @Override
                            public void onClick(FocusScheduleEntity schedule) {
                                showAddScheduleDialog(schedule, () -> dbExecutor.execute(() -> {
                                    List<FocusScheduleEntity> fresh = db.focusScheduleDao().getAll();
                                    runOnUiThread(() -> scheduleRv.setAdapter(
                                            new FocusScheduleAdapter(FocusMode.this, fresh, this)));
                                }));
                            }
                        });
                scheduleRv.setAdapter(scheduleAdapter);
            });
        });
    }

    private void updatePomodoroIndicator() {
        if (pomodoroIndicator == null)
            return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isPomoPref = prefs.getBoolean(PREF_POMODORO_ENABLED, false);

        if (isBound && focusService != null && focusService.isTimerRunning()) {
            if (focusService.isPomodoroEnabled()) {
                pomodoroIndicator.setVisibility(View.VISIBLE);
                if (focusService.isPaused() && focusService.isBreak()) {
                    // BREAK_PAUSED state
                    pomodoroIndicator.setText(isTestMode ? "TEST: Break" : "Pomodoro: Break Time");
                    pomodoroIndicator.setTextColor(ContextCompat.getColor(this, R.color.greenIcon));
                } else if (focusService.isPaused()) {
                    // WAITING_USER state
                    pomodoroIndicator.setText(isTestMode ? "TEST: Paused" : "Pomodoro: Tap Resume");
                    pomodoroIndicator.setTextColor(ContextCompat.getColor(this, R.color.status_amber));
                } else {
                    // FOCUS_RUNNING state
                    pomodoroIndicator.setText(isTestMode ? "TEST: Focus" : "Pomodoro: Focus");
                    pomodoroIndicator.setTextColor(ContextCompat.getColor(this, R.color.redIcon));
                }
            } else {
                pomodoroIndicator.setVisibility(View.GONE);
            }
        } else {
            // Not running
            if (isTestMode) {
                pomodoroIndicator.setVisibility(View.VISIBLE);
                pomodoroIndicator.setText("TEST MODE ACTIVE");
                pomodoroIndicator.setTextColor(ContextCompat.getColor(this, R.color.status_amber));
            } else if (isPomoPref) {
                pomodoroIndicator.setVisibility(View.VISIBLE);
                long focus = prefs.getInt(PREF_FOCUS_DURATION, 25);
                long brk = prefs.getInt(PREF_BREAK_DURATION, 5);
                pomodoroIndicator.setText("Pomodoro Enabled (" + focus + "/" + brk + ")");
                pomodoroIndicator.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
            } else {
                pomodoroIndicator.setVisibility(View.GONE);
            }
        }
    }

    private void showPomodoroFirstTimeDialogIfNeeded() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("pref_pomodoro_first_time_shown", false)) {
            return; // Already shown
        }
        showAutoKillWarning(); // Use the same message
        prefs.edit().putBoolean("pref_pomodoro_first_time_shown", true).apply();
    }

    private void showAutoKillWarning() {
        com.gxdevs.mindmint.Utils.CustomDialogUtils.showCustomDialog(this,
                "Important Info",
                """
                        If you don't resume the timer within 20 minutes after a break, \
                        the session will automatically end.
                        
                        This won't deduct any coins.""",
                "Got it",
                "",
                null,
                null);
    }

    private void showTimerPickerSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_timer_picker, null);
        sheet.setContentView(view);

        SeekBar timerSeekBar = view.findViewById(R.id.timerPickerSeekBar);
        TextView timerPickerLabel = view.findViewById(R.id.timerPickerLabel);
        com.google.android.material.button.MaterialButton confirmBtn = view.findViewById(R.id.timerPickerConfirmBtn);
        View closeBtn = view.findViewById(R.id.timerPickerCloseBtn);

        // BUG-9 FIX: enforce Pomodoro minimum in the timer picker
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isPomoSheet = prefs.getBoolean(PREF_POMODORO_ENABLED, false);
        int pomodoroMin = isPomoSheet ? Math.max(10, prefs.getInt(PREF_FOCUS_DURATION, 25)) : 10;

        // Range pomodoroMin–180 min; seekbar goes 0–(180-pomodoroMin)
        int seekbarMax = 180 - pomodoroMin;
        int initialProgress = Math.max(0, Math.min(selectedMinutes - pomodoroMin, seekbarMax));
        if (timerSeekBar != null) {
            timerSeekBar.setMax(seekbarMax);
            timerSeekBar.setProgress(initialProgress);
        }
        int displayMinutes = Math.max(selectedMinutes, pomodoroMin);
        if (timerPickerLabel != null) {
            timerPickerLabel.setText(displayMinutes + " min");
        }

        final int[] pickedMinutes = {displayMinutes};

        if (timerSeekBar != null) {
            timerSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    pickedMinutes[0] = progress + pomodoroMin;
                    if (timerPickerLabel != null) {
                        timerPickerLabel.setText(pickedMinutes[0] + " min");
                    }
                }

                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                }
            });
        }

        if (confirmBtn != null) {
            confirmBtn.setOnClickListener(v -> {
                selectedMinutes = pickedMinutes[0];
                circularSeekBar.setProgress(selectedMinutes);
                timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
                updateThemeForDuration(selectedMinutes);
                sheet.dismiss();
            });
        }
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> sheet.dismiss());
        }

        sheet.show();
    }

    private void showAddScheduleDialog(FocusScheduleEntity scheduleToEdit, Runnable onSaved) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        dialog.setContentView(R.layout.bottom_sheet_add_schedule);

        MaterialButton btnTime = dialog.findViewById(R.id.btnSelectTime);
        LinearLayout daysChipGroup = dialog.findViewById(R.id.daysChipGroup);
        SeekBar durBar = dialog.findViewById(R.id.durationSeekBar);
        TextView durLabel = dialog.findViewById(R.id.durationLabel);
        MaterialSwitch lockSwitch = dialog.findViewById(R.id.lockInSwitch);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSaveSchedule);

        View.OnClickListener dayClickListener = getClickListener();

        if (daysChipGroup == null) {
            Log.e(TAG, "showAddScheduleDialog: daysChipGroup not found, aborting");
            dialog.dismiss();
            return;
        }
        for (int i = 0; i < daysChipGroup.getChildCount(); i++) {
            daysChipGroup.getChildAt(i).setOnClickListener(dayClickListener);
        }

        if (!Utils.isAccessibilityPermissionGranted(this)) {
            if (lockSwitch != null) lockSwitch.setVisibility(View.GONE);
        }

        final int[] hour = {12};
        final int[] min = {0};

        if (scheduleToEdit != null) {
            hour[0] = scheduleToEdit.startHour;
            min[0] = scheduleToEdit.startMinute;
            if (durBar != null) durBar.setProgress(scheduleToEdit.durationMinutes);
            if (lockSwitch != null) lockSwitch.setChecked(scheduleToEdit.isLockedIn == 1);
            if (scheduleToEdit.daysOfWeek != null) {
                String[] d = scheduleToEdit.daysOfWeek.split(",");
                for (String day : d) {
                    for (int i = 0; i < daysChipGroup.getChildCount(); i++) {
                        TextView c = (TextView) daysChipGroup.getChildAt(i);
                        if (c.getText().toString().equalsIgnoreCase(day.trim())) {
                            c.setTag(false); // Force toggle
                            c.callOnClick();
                        }
                    }
                }
            }
        }
        btnTime.setText(String.format(java.util.Locale.US, "Start Time %02d:%02d %s",
                hour[0] % 12 == 0 ? 12 : hour[0] % 12, min[0], hour[0] >= 12 ? "PM" : "AM"));
        durLabel.setText("Duration (Minutes): " + durBar.getProgress());

        btnTime.setOnClickListener(v -> {
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTheme(R.style.ThemeOverlay_MindMint_TimePicker)
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(hour[0])
                    .setMinute(min[0])
                    .setTitleText("Select Schedule Time")
                    .build();
            timePicker.addOnPositiveButtonClickListener(v1 -> {
                hour[0] = timePicker.getHour();
                min[0] = timePicker.getMinute();
                btnTime.setText(String.format(java.util.Locale.US, "Start Time %02d:%02d %s",
                        hour[0] % 12 == 0 ? 12 : hour[0] % 12, min[0], hour[0] >= 12 ? "PM" : "AM"));
            });
            timePicker.show(getSupportFragmentManager(), "schedule_time");
        });

        durBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean b) {
                durLabel.setText("Duration (Minutes): " + p);
            }

            public void onStartTrackingTouch(SeekBar sb) {
            }

            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        // Use a proper null-guard instead of assert (asserts are disabled in release builds)
        if (btnSave == null) {
            Log.e(TAG, "showAddScheduleDialog: btnSaveSchedule not found in layout");
            dialog.dismiss();
            return;
        }
        btnSave.setOnClickListener(v -> {
            java.util.List<String> days = new java.util.ArrayList<>();
            for (int i = 0; i < daysChipGroup.getChildCount(); i++) {
                TextView c = (TextView) daysChipGroup.getChildAt(i);
                boolean isSelected = c.getTag() != null && (boolean) c.getTag();
                if (isSelected) days.add(c.getText().toString());
            }

            if (days.isEmpty()) {
                Toast.makeText(this, "Select at least one day", Toast.LENGTH_SHORT).show();
                return;
            }

            // BUG-1 FIX: ensure the user picked a non-zero duration
            if (durBar.getProgress() <= 0) {
                Toast.makeText(this, "Please set a duration of at least 1 minute", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Please enable Notification permission in App Settings", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                    return;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (!am.canScheduleExactAlarms()) {
                    Toast.makeText(this, "Please enable Exact Alarms permission in App Settings", Toast.LENGTH_LONG).show();
                    Intent pIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    pIntent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(pIntent);
                    return;
                }
            }

            dbExecutor.execute(() -> {
                FocusScheduleEntity entity = scheduleToEdit != null ? scheduleToEdit : new FocusScheduleEntity();
                entity.startHour = hour[0];
                entity.startMinute = min[0];
                entity.daysOfWeek = android.text.TextUtils.join(", ", days);
                entity.durationMinutes = durBar.getProgress();
                entity.isLockedIn = lockSwitch != null && lockSwitch.isChecked() ? 1 : 0;
                entity.isEnabled = 1;

                if (scheduleToEdit == null) {
                    long id = db.focusScheduleDao().insert(entity);
                    entity.id = (int) id;
                } else {
                    db.focusScheduleDao().update(entity);
                }

                FocusScheduleManager mgr = new FocusScheduleManager(this);
                mgr.setSchedule(entity);

                runOnUiThread(() -> {
                    dialog.dismiss();
                    if (onSaved != null) onSaved.run();
                });
            });
        });

        dialog.show();
    }

    private View.OnClickListener getClickListener() {
        TypedValue tvPrimary = new TypedValue();
        getTheme().resolveAttribute(R.attr.text_primary, tvPrimary, true);
        final int colorPrimary = tvPrimary.data;

        TypedValue tvTertiary = new TypedValue();
        getTheme().resolveAttribute(R.attr.text_tertiary, tvTertiary, true);
        final int colorTertiary = tvTertiary.data;

        return v -> {
            boolean isSelected = v.getTag() != null && (boolean) v.getTag();
            isSelected = !isSelected;
            v.setTag(isSelected);
            if (isSelected) {
                v.setBackgroundResource(R.drawable.bg_segment_selected);
                ((TextView) v).setTextColor(colorPrimary);
            } else {
                v.setBackgroundResource(R.drawable.bg_chip_unselected);
                ((TextView) v).setTextColor(colorTertiary);
            }
        };
    }


}

