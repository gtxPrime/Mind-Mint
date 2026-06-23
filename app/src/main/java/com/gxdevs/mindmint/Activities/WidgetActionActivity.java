package com.gxdevs.mindmint.Activities;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.FocusService;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.StreakManager;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Widgets.HabitListWidgetProvider;
import com.gxdevs.mindmint.db.MindMintRoomDatabase;
import com.gxdevs.mindmint.db.entities.FocusTopicEntity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class WidgetActionActivity extends AppCompatActivity {

    private HabitManager habitManager;
    private StreakManager streakManager;
    private MintCrystals mintCrystals;
    private Habit habit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setContentView(container);

        habitManager = new HabitManager(this);
        streakManager = new StreakManager(this);
        mintCrystals = new MintCrystals(this);

        String mode = getIntent().getStringExtra("mode");
        if (mode == null)
            mode = getIntent().getStringExtra("MODE");

        if ("FOCUS_NORMAL".equals(mode) || "FOCUS_POMODORO".equals(mode)) {
            showFocusBottomSheet(mode);
            return;
        }

        String habitId = getIntent().getStringExtra("habit_id");
        if (habitId == null) {
            finish();
            return;
        }

        habit = habitManager.getHabitById(habitId);
        if (habit == null) {
            finish();
            return;
        }

        if ("GOAL".equals(mode)) {
            showGoalBottomSheet();
        } else if ("MOOD".equals(mode)) {
            showMoodBottomSheet();
        } else {
            finish();
        }
    }

    private boolean isTransitioningToMood = false;

    // Focus Mode Logic
    private void showFocusBottomSheet(String mode) {
        BottomSheetDialog sheetDialog = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_widget_focus, null);
        sheetDialog.setContentView(view);

        TextView title = view.findViewById(R.id.sheetTitle);
        LinearLayout normalSection = view.findViewById(R.id.normalDurationSection);
        LinearLayout pomodoroSection = view.findViewById(R.id.pomodoroSection);
        TextView normalTimerText = view.findViewById(R.id.normalTimerText);

        SeekBar durationSeekBar = view.findViewById(R.id.durationSeekBar);
        SeekBar focusSeekBar = view.findViewById(R.id.focusSeekBar);
        TextView focusDurationLabel = view.findViewById(R.id.focusDurationLabel);
        SeekBar breakSeekBar = view.findViewById(R.id.breakSeekBar);
        TextView breakDurationLabel = view.findViewById(R.id.breakDurationLabel);
        MaterialSwitch autoResumeSwitch = view.findViewById(R.id.autoResumeSwitch);
        View breakSettingsContainer = view.findViewById(R.id.breakSettingsContainer);
        ChipGroup topicsChipGroup = view.findViewById(R.id.topicsChipGroup);
        Button startBtn = view.findViewById(R.id.startBtn);

        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isPomodoro = "FOCUS_POMODORO".equals(mode);
        if (isPomodoro) {
            title.setText(R.string.pomodoro);
            normalSection.setVisibility(View.GONE);
            pomodoroSection.setVisibility(View.VISIBLE);
            normalTimerText.setVisibility(View.GONE);

            // Setup Pomodoro
            int savedFocus = prefs.getInt("pref_focus_duration", 25);
            int savedBreak = prefs.getInt("pref_break_duration", 5);
            boolean savedAutoStart = prefs.getBoolean("pref_auto_start_break", true);

            // Focus: Min 20, Max 45
            int minFocus = 20;
            int maxFocus = 45;
            // Break: Min 3, Max 15
            int minBreak = 3;
            int maxBreak = 15;

            // Check Quick Start
            String quickStartKey = "pref_widget_quick_start_pomodoro";
            boolean forceConfig = getIntent().getBooleanExtra("FORCE_CONFIG", false);

            if (!forceConfig && prefs.getBoolean(quickStartKey, false)) {
                long focusInterval = savedFocus * 60 * 1000L;
                long breakInterval = savedBreak * 60 * 1000L;
                selectedTopicId = prefs.getInt("widget_last_topic_id", -1);
                loadTopicsForQuickStartAndLaunch(() -> {
                    startFocusService(Long.MAX_VALUE, true, focusInterval, breakInterval);
                    finish();
                });
                return; // Don't show sheet
            }

            focusSeekBar.setMax(maxFocus - minFocus); // 25
            if (savedFocus < minFocus)
                savedFocus = minFocus;
            if (savedFocus > maxFocus)
                savedFocus = maxFocus;
            focusSeekBar.setProgress(savedFocus - minFocus);
            focusDurationLabel.setText("Focus Interval: " + savedFocus + " min");

            breakSeekBar.setMax(maxBreak - minBreak); // 12
            if (savedBreak < minBreak)
                savedBreak = minBreak;
            if (savedBreak > maxBreak)
                savedBreak = maxBreak;
            breakSeekBar.setProgress(savedBreak - minBreak);
            breakDurationLabel.setText("Break Duration: " + savedBreak + " min");

            autoResumeSwitch.setChecked(savedAutoStart);

            // Visibility Logic for Break Settings
            Runnable updateBreakVisibility = () -> {
                int visibility = autoResumeSwitch.isChecked() ? View.VISIBLE : View.GONE;
                breakSettingsContainer.setVisibility(visibility);
            };
            updateBreakVisibility.run(); // Initial state

            autoResumeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateBreakVisibility.run());

            focusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress + minFocus;
                    focusDurationLabel.setText("Focus Interval: " + val + " min");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            breakSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress + minBreak;
                    breakDurationLabel.setText("Break Duration: " + val + " min");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

        } else {
            title.setText(R.string.set_your_focus_time);
            normalSection.setVisibility(View.VISIBLE);
            pomodoroSection.setVisibility(View.GONE);
            normalTimerText.setVisibility(View.VISIBLE);

            // Setup Normal
            int lastDuration = prefs.getInt("widget_last_normal_duration", 10);
            if (lastDuration < 10)
                lastDuration = 10;
            if (lastDuration > 180)
                lastDuration = 180;

            // Check Quick Start
            String quickStartKey = "pref_widget_quick_start_normal";
            boolean forceConfig = getIntent().getBooleanExtra("FORCE_CONFIG", false);

            if (!forceConfig && prefs.getBoolean(quickStartKey, false)) {
                selectedTopicId = prefs.getInt("widget_last_topic_id", -1);
                final long finalDuration = lastDuration * 60 * 1000L;
                loadTopicsForQuickStartAndLaunch(() -> {
                    startFocusService(finalDuration, false, 0, 0);
                    finish();
                });
                return;
            }

            durationSeekBar.setProgress(lastDuration - 10);
            normalTimerText.setText(String.format(java.util.Locale.US, "%d min", lastDuration));

            durationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = progress + 10;
                    normalTimerText.setText(String.format(java.util.Locale.US, "%d min", val));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        loadTopics(topicsChipGroup);
        MaterialSwitch saveForNextSwitch = view.findViewById(R.id.saveForNextSwitch);

        startBtn.setOnClickListener(v -> {
            boolean saveForNext = saveForNextSwitch.isChecked();
            int currentTopicId = selectedTopicId;

            if (isPomodoro) {
                int focusMin = focusSeekBar.getProgress() + 20;
                int breakMin = breakSeekBar.getProgress() + 3;
                boolean autoResume = autoResumeSwitch.isChecked();

                prefs.edit()
                        .putInt("pref_focus_duration", focusMin)
                        .putInt("pref_break_duration", breakMin)
                        .putBoolean("pref_auto_start_break", autoResume)
                        .putBoolean("pref_widget_quick_start_pomodoro", saveForNext)
                        .putInt("widget_last_topic_id", currentTopicId)
                        .apply();

                startFocusService(Long.MAX_VALUE, true, focusMin * 60 * 1000L, breakMin * 60 * 1000L);
            } else {
                int durationMinutes = durationSeekBar.getProgress() + 10;
                prefs.edit()
                        .putInt("widget_last_normal_duration", durationMinutes)
                        .putBoolean("pref_widget_quick_start_normal", saveForNext)
                        .putInt("widget_last_topic_id", currentTopicId)
                        .apply();
                startFocusService(durationMinutes * 60 * 1000L, false, 0, 0);
            }
            sheetDialog.dismiss();
            finish();
        });

        sheetDialog.setOnDismissListener(dialog -> finish());
        sheetDialog.show();
    }

    private void loadTopicsForQuickStartAndLaunch(Runnable onLoaded) {
        Executors.newSingleThreadExecutor().execute(() -> {
            MindMintRoomDatabase db = MindMintRoomDatabase.getInstance(this);
            currentTopics = db.focusTopicDao().getAllTopicsSync();
            runOnUiThread(onLoaded);
        });
    }

    private int selectedTopicId = -1;
    private List<FocusTopicEntity> currentTopics;

    private void loadTopics(ChipGroup chipGroup) {
        Executors.newSingleThreadExecutor().execute(() -> {
            MindMintRoomDatabase db = MindMintRoomDatabase.getInstance(this);
            currentTopics = db.focusTopicDao().getAllTopicsSync();
            runOnUiThread(() -> {
                chipGroup.removeAllViews();
                for (FocusTopicEntity topic : currentTopics) {
                    Chip chip = new Chip(this);
                    chip.setText(topic.name);
                    chip.setCheckable(true);
                    chip.setClickable(true);
                    chip.setCheckedIconVisible(true);

                    // Styling to match FocusMode.java
                    chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.transparent)));
                    chip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.glass_stroke)));
                    chip.setChipStrokeWidth(Utils.dpToPx(1, this));
                    chip.setChipCornerRadius(Utils.dpToPx(20, this));
                    chip.setChipBackgroundColorResource(R.color.white_10); // 10% white for glass effect
                    chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

                    if (selectedTopicId == topic.id) {
                        chip.setChecked(true);
                        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
                        chip.setTextColor(Color.WHITE);
                    }

                    chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            selectedTopicId = topic.id;
                            chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
                            chip.setTextColor(Color.WHITE);
                        } else if (selectedTopicId == topic.id) {
                            selectedTopicId = -1;
                            chip.setChipBackgroundColorResource(R.color.white_10);
                            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                        }
                    });
                    chipGroup.addView(chip);
                }
            });
        });
    }

    private void startFocusService(long duration, boolean isPomodoro, long pomodoroFocus, long pomodoroBreak) {
        Intent serviceIntent = new Intent(this, FocusService.class);
        serviceIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
        serviceIntent.putExtra("durationInMillis", duration);
        serviceIntent.putExtra("isPomodoroEnabled", isPomodoro);
        if (isPomodoro) {
            serviceIntent.putExtra("pomodoroFocusInterval", pomodoroFocus);
            serviceIntent.putExtra("pomodoroBreakInterval", pomodoroBreak);
        }

        String topicName = null;
        if (selectedTopicId != -1 && currentTopics != null) {
            for (FocusTopicEntity t : currentTopics) {
                if (t.id == selectedTopicId) {
                    topicName = t.name;
                    break;
                }
            }
        }
        serviceIntent.putExtra("topicName", topicName);

        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void showGoalBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_progress, null);

        TextView name = sheet.findViewById(R.id.habitName);
        ImageView icon = sheet.findViewById(R.id.habitIcon);
        TextView current = sheet.findViewById(R.id.tvCurrentProgress);
        TextView target = sheet.findViewById(R.id.tvTargetCount);
        TextView unit = sheet.findViewById(R.id.tvTargetUnit);
        ProgressBar pb = sheet.findViewById(R.id.pbGoal);
        ImageView btnMinus = sheet.findViewById(R.id.btnMinus);
        ImageView btnPlus = sheet.findViewById(R.id.btnPlus);
        ImageView cross = sheet.findViewById(R.id.crossBtn);
        TextView helperText = sheet.findViewById(R.id.helperText);

        habit.resetProgressIfNeeded();

        name.setText(habit.getName());
        int iconRes = habit.getIcon() != 0 ? habit.getIcon() : R.drawable.flame;
        icon.setImageResource(iconRes);
        // Apply tint
        icon.setColorFilter(habit.getIconTint());
        icon.setBackgroundResource(R.drawable.widget_circle_bg); // Ensuring background exists
        icon.setBackgroundTintList(ColorStateList.valueOf(habit.getIconBackgroundTint()));

        // Initial UI State
        updateGoalUI(current, target, unit, pb, helperText);

        int brandColor = getColor(R.color.brand_pink);
        btnPlus.setImageTintList(ColorStateList.valueOf(brandColor));
        btnMinus.setImageTintList(ColorStateList.valueOf(brandColor));
        pb.setProgressTintList(ColorStateList.valueOf(habit.getIconTint()));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(habit.getIconBackgroundTint()));

        btnPlus.setOnClickListener(v -> {
            if (habit.getCurrentProgress() >= habit.getTargetCount())
                return;

            int val = habit.getCurrentProgress() + habit.getOneTapValue();
            val = Math.min(val, habit.getTargetCount()); // Cap at target? HomeFragment does min(val, target)

            // Update DB immediately
            boolean justCompleted = habitManager.updateHabitProgress(habit, val);
            habit.setCurrentProgress(val); // Update local object

            if (justCompleted) {
                mintCrystals.addCoins(5);
                streakManager.updateStreakOnHabitCompletion();

                if (habit.isAskEmotion()) {
                    isTransitioningToMood = true; // Prevent activity finish
                    dialog.dismiss();
                    showMoodBottomSheet(); // Chain mood sheet
                    return;
                }
            }

            updateGoalUI(current, target, unit, pb, helperText);
            updateWidgets();
        });

        btnMinus.setOnClickListener(v -> {
            if (habit.getCurrentProgress() <= 0)
                return;

            boolean wasCompleted = habitManager.isCompletedToday(habit);
            int val = Math.max(0, habit.getCurrentProgress() - habit.getOneTapValue());

            // Update DB immediately
            habitManager.updateHabitProgress(habit, val);
            habit.setCurrentProgress(val);

            if (wasCompleted && val < habit.getTargetCount()) {
                mintCrystals.subtractCoins(5);
                streakManager.checkAndResetStreakIfNeeded(Collections.singletonList(habit));
            }

            updateGoalUI(current, target, unit, pb, helperText);
            updateWidgets();
        });

        cross.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            updateWidgets();
            if (!isTransitioningToMood) {
                finish();
            }
        });

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void updateGoalUI(TextView current, TextView target, TextView unit, ProgressBar pb, TextView helperText) {
        current.setText(String.valueOf(habit.getCurrentProgress()));
        current.setTextColor(habit.getIconTint());

        target.setText(String.valueOf(habit.getTargetCount()));
        unit.setText(habit.getTargetUnit() != null ? habit.getTargetUnit() : "");

        pb.setMax(habit.getTargetCount());
        pb.setProgress(habit.getCurrentProgress());

        if (habit.getCurrentProgress() >= habit.getTargetCount()) {
            if (helperText != null) {
                helperText.setText("Goal reached!");
                helperText.setTextColor(getColor(R.color.brainColor));
            }
        } else {
            if (helperText != null) {
                helperText.setText("Reach your goal to complete this habit");
                helperText.setTextColor(getColor(R.color.widget_text_tertiary));
            }
        }
    }

    private void showMoodBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_mood, null);

        View.OnClickListener emotionListener = v -> {
            String emotion = "";
            int id = v.getId();
            if (id == R.id.moodTired)
                emotion = "Tired";
            else if (id == R.id.moodMotivated)
                emotion = "Motivated";
            else if (id == R.id.moodRelieved)
                emotion = "Relieved";
            else if (id == R.id.moodSatisfied)
                emotion = "Satisfied";
            else if (id == R.id.moodProud)
                emotion = "Proud";
            else if (id == R.id.moodNeutral)
                emotion = "Neutral";

            completeHabit(emotion);
            dialog.dismiss();
        };

        sheet.findViewById(R.id.moodTired).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodMotivated).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodRelieved).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodSatisfied).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodProud).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodNeutral).setOnClickListener(emotionListener);

        View btnSkip = sheet.findViewById(R.id.btnSkip);
        btnSkip.setVisibility(View.VISIBLE);
        btnSkip.setAlpha(1f);
        btnSkip.setOnClickListener(v -> {
            completeHabit(null);
            dialog.dismiss();
        });

        sheet.findViewById(R.id.crossBtn).setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            finish(); // Finish activity when sheet closes
        });

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void completeHabit(String emotion) {
        habitManager.markHabit(habit, emotion);
        mintCrystals.addCoins(5);
        streakManager.updateStreakOnHabitCompletion();
        updateWidgets();
    }

    private void updateWidgets() {
        AppWidgetManager man = AppWidgetManager.getInstance(this);
        int[] listIds = man.getAppWidgetIds(new ComponentName(this, HabitListWidgetProvider.class));
        man.notifyAppWidgetViewDataChanged(listIds, R.id.widget_habit_list_view);
    }
}
