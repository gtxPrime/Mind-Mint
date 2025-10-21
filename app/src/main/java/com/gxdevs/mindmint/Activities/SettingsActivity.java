package com.gxdevs.mindmint.Activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;
import com.gxdevs.mindmint.Utils.Utils;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class SettingsActivity extends AppCompatActivity {

    MaterialSwitch remindDoomScrollingSwitch, blockAfterWastedTimeSwitch;
    TextView remindDoomSubtitle, blockViewTimeSubtitle;
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_ENABLED = "pref_block_after_wasted_time_enabled";
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_HOURS = "pref_block_after_wasted_time_hours";
    public static final float DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS = 1.0f;
    private SharedPreferences defaultSharedPreferences;
    private BottomSheetDialog timerPicker;
    private ActivityResultLauncher<Intent> batteryOptimizationLauncher;
    private BottomSheetDialog bottomSheetDialog;
    private boolean batteryOptimizationIgnored = false;
    private MaterialCardView permissionCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        Utils.setPad(findViewById(R.id.main), "bottom", this);
        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        remindDoomSubtitle = findViewById(R.id.remindDoomSubtitle);
        blockViewTimeSubtitle = findViewById(R.id.blockViewTimeSubtitle);

        bottomSheetDialog = new BottomSheetDialog(SettingsActivity.this);
        registerForPermission();

        // --- START: Initialize new UI elements and listeners ---
        ConstraintLayout customAppBlockingLayout = findViewById(R.id.customAppBlockingLayout);
        EditText etBlockingPopupDuration = findViewById(R.id.et_blocking_popup_duration);

        customAppBlockingLayout.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, CustomAppSelectionActivity.class);
            startActivity(intent);
        });

        int savedDuration = defaultSharedPreferences.getInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, 5);
        etBlockingPopupDuration.setText(String.valueOf(savedDuration));

        etBlockingPopupDuration.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int duration = Integer.parseInt(s.toString());
                    if (duration < 5) { // Min 1 second for popup
                        etBlockingPopupDuration.setError("Minimum 5 second");
                    } else {
                        etBlockingPopupDuration.setError(null);
                        SharedPreferences.Editor editor = defaultSharedPreferences.edit();
                        editor.putInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, duration);
                        editor.apply();
                    }
                } catch (NumberFormatException e) {
                    etBlockingPopupDuration.setError("Invalid number");
                }
            }
        });

        remindDoomScrollingSwitch = findViewById(R.id.remindDoomScrollingSwitch);
        blockAfterWastedTimeSwitch = findViewById(R.id.blockAfterWastedTimeSwitch);

        // Load saved values for reminder feature
        boolean isRemindDoomScrollingEnabled = defaultSharedPreferences.getBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
        remindDoomScrollingSwitch.setChecked(isRemindDoomScrollingEnabled);
        updateRemindDoomTimeDisplay();

        // Load saved values for blocking feature
        blockAfterWastedTimeSwitch.setChecked(defaultSharedPreferences.getBoolean(PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false));
        updateBlockAfterWastedTimeDisplay();


        // Listeners for reminder feature
        remindDoomScrollingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            defaultSharedPreferences.edit().putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, isChecked).apply();
            if (!isChecked) {
                remindDoomSubtitle.setVisibility(GONE);
            } else {
                remindDoomSubtitle.setVisibility(VISIBLE);
            }
        });

        remindDoomSubtitle.setOnClickListener(v -> showTimePickerBottomSheet(true));

        // Listeners for blocking feature
        blockAfterWastedTimeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            defaultSharedPreferences.edit().putBoolean(PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, isChecked).apply();
            if (!isChecked) {
                blockViewTimeSubtitle.setVisibility(GONE);
            } else {
                blockViewTimeSubtitle.setVisibility(VISIBLE);
            }
        });

        blockViewTimeSubtitle.setOnClickListener(v -> showTimePickerBottomSheet(false));

        BlurView timeBlur = findViewById(R.id.timeBlur);
        BlurView blockingBlur = findViewById(R.id.blockingBlur);
        BlurView permissionBlur = findViewById(R.id.permissionBlurX);
        BlurTarget blurTarget = findViewById(R.id.blurTarget);
        permissionCard = findViewById(R.id.permissionCard);

        timeBlur.setupWith(blurTarget).setBlurRadius(5f);
        blockingBlur.setupWith(blurTarget).setBlurRadius(5f);
        permissionBlur.setupWith(blurTarget).setBlurRadius(5f);

        permissionCard.setOnClickListener(v -> showBottomSheet(R.string.batter_permission, R.string.why_battery, R.string.click_on_proceed, R.string.step2allow));
        checkPermissionsAndMove();
        // --- END: NEW FEATURES INITIALIZATION ---
    }

    private void showTimePickerBottomSheet(boolean isRemindDoomScrolling) {
        timerPicker = new BottomSheetDialog(SettingsActivity.this);
        View bottomSheetView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.bottom_sheet_time_picker, findViewById(R.id.bottomSheetTimePickerLayout));

        NumberPicker hourPickerBottomSheet = bottomSheetView.findViewById(R.id.hours_selector_bottom_sheet);
        NumberPicker minutePickerBottomSheet = bottomSheetView.findViewById(R.id.minutes_selector_bottom_sheet);
        Button setLimitBtnBottomSheet = bottomSheetView.findViewById(R.id.setLimitBtnBottomSheet);
        TextView hoursLabel = bottomSheetView.findViewById(R.id.hours_textView_bottom_sheet);

        if (isRemindDoomScrolling) {
            // For remind doom scrolling, we only use minutes (0-59)
            hourPickerBottomSheet.setVisibility(View.GONE);
            hoursLabel.setVisibility(View.GONE);
            minutePickerBottomSheet.setMinValue(1);
            minutePickerBottomSheet.setMaxValue(59);

            // Set current value
            int currentMinutes = defaultSharedPreferences.getInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, AppUsageAccessibilityService.DEFAULT_REMIND_DOOM_SCROLLING_MINUTES);
            minutePickerBottomSheet.setValue(currentMinutes);

            setLimitBtnBottomSheet.setText(R.string.set_reminder_time);
        } else {
            // For block after wasted time, we use hours and minutes
            hourPickerBottomSheet.setVisibility(VISIBLE);
            hoursLabel.setVisibility(VISIBLE);
            hourPickerBottomSheet.setMinValue(0);
            hourPickerBottomSheet.setMaxValue(23);
            minutePickerBottomSheet.setMinValue(0);
            minutePickerBottomSheet.setMaxValue(59);

            // Set current value
            float currentHours = defaultSharedPreferences.getFloat(PREF_BLOCK_AFTER_WASTED_TIME_HOURS, DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS);
            int hours = (int) currentHours;
            int minutes = Math.round((currentHours - hours) * 60);
            hourPickerBottomSheet.setValue(hours);
            minutePickerBottomSheet.setValue(minutes);

            setLimitBtnBottomSheet.setText(R.string.set_block_time);
        }

        setLimitBtnBottomSheet.setOnClickListener(v -> {
            int hours = hourPickerBottomSheet.getValue();
            int minutes = minutePickerBottomSheet.getValue();

            if (isRemindDoomScrolling) {
                // Save minutes only
                defaultSharedPreferences.edit().putInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, minutes).apply();
                updateRemindDoomTimeDisplay();
                Toast.makeText(this, "Reminder time set to " + minutes + " minutes", Toast.LENGTH_SHORT).show();
            } else {
                // Save hours and minutes as decimal hours
                float totalHours = hours + (minutes / 60.0f);
                defaultSharedPreferences.edit().putFloat(PREF_BLOCK_AFTER_WASTED_TIME_HOURS, totalHours).apply();
                updateBlockAfterWastedTimeDisplay();
                Toast.makeText(this, "Block time set to " + formatTimeDisplay(hours, minutes), Toast.LENGTH_SHORT).show();
            }

            timerPicker.dismiss();
        });

        timerPicker.setContentView(bottomSheetView);
        timerPicker.show();
    }

    private void updateRemindDoomTimeDisplay() {
        int minutes = defaultSharedPreferences.getInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, AppUsageAccessibilityService.DEFAULT_REMIND_DOOM_SCROLLING_MINUTES);
        String reminderText = "Remind me to stop scroll at every " + minutes + " minutes ";
        SpannableString spannable = new SpannableString(reminderText);

        int start = reminderText.indexOf(String.valueOf(minutes));
        int end = start + String.valueOf(minutes).length() + " minutes".length();

        int cyan = ContextCompat.getColor(remindDoomSubtitle.getContext(), R.color.cyan);

        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(cyan), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Now add drawable at the end
        Drawable icon = ContextCompat.getDrawable(remindDoomSubtitle.getContext(), R.drawable.ic_edit);
        if (icon != null) {
            DrawableCompat.setTint(icon, cyan);
            int size = (int) remindDoomSubtitle.getTextSize();
            icon.setBounds(0, 0, size, size);

            ImageSpan imageSpan = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
            SpannableStringBuilder builder = new SpannableStringBuilder(spannable);
            int iconStart = builder.length();
            builder.append(" ");
            builder.setSpan(imageSpan, iconStart, iconStart + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            remindDoomSubtitle.setText(builder);
        } else {
            remindDoomSubtitle.setText(spannable);
        }
    }

    private void updateBlockAfterWastedTimeDisplay() {
        float hours = defaultSharedPreferences.getFloat(PREF_BLOCK_AFTER_WASTED_TIME_HOURS, DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS);

        int wholeHours = (int) hours;
        int minutes = Math.round((hours - wholeHours) * 60);

        String timeText = formatTimeDisplay(wholeHours, minutes);
        String displayText = "Block content after\n" + timeText + " ";
        SpannableString spannable = new SpannableString(displayText);

        int start = displayText.indexOf(timeText);
        int end = start + timeText.length();
        int cyan = ContextCompat.getColor(blockViewTimeSubtitle.getContext(), R.color.cyan);

        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(cyan), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Drawable icon = ContextCompat.getDrawable(blockViewTimeSubtitle.getContext(), R.drawable.ic_edit);
        if (icon != null) {
            DrawableCompat.setTint(icon, cyan);
            int size = (int) blockViewTimeSubtitle.getTextSize();
            icon.setBounds(0, 0, size, size);
            ImageSpan imageSpan = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
            SpannableStringBuilder builder = new SpannableStringBuilder(spannable);
            int iconStart = builder.length();
            builder.append(" ");
            builder.setSpan(imageSpan, iconStart, iconStart + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            blockViewTimeSubtitle.setText(builder);
        } else {
            blockViewTimeSubtitle.setText(spannable);
        }
    }

    private String formatTimeDisplay(int hours, int minutes) {
        if (hours == 0) {
            return minutes + " minutes";
        } else if (minutes == 0) {
            return hours + " hours";
        } else {
            return hours + "h " + minutes + "m";
        }
    }

    public void registerForPermission() {
        batteryOptimizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
                    if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                        Toast.makeText(this, "Battery optimization ignored", Toast.LENGTH_SHORT).show();
                        batteryOptimizationIgnored = true;
                        checkPermissionsAndMove();
                        if (bottomSheetDialog.isShowing()) {
                            bottomSheetDialog.dismiss();
                        }
                    } else {
                        Toast.makeText(this, "Battery optimization not ignored", Toast.LENGTH_SHORT).show();
                    }
                });

    }

    private void checkPermissionsAndMove() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        batteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(getPackageName());

        if (batteryOptimizationIgnored) {
            permissionCard.setVisibility(GONE);
        } else {
            permissionCard.setVisibility(VISIBLE);
        }
    }

    private void showBottomSheet(int heading, int desc, int step1, int step2) {
        View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.layout_bottomsheet, findViewById(R.id.sheetContainer));
        TextView permissionHead = view.findViewById(R.id.permissionHead);
        TextView permissionDesc = view.findViewById(R.id.permissionDesc);
        TextView permissionStep1 = view.findViewById(R.id.permissionStep1);
        TextView permissionStep2 = view.findViewById(R.id.permissionStep2);
        TextView permissionStep3 = view.findViewById(R.id.permissionStep3);
        TextView proceed = view.findViewById(R.id.proceed);
        TextView notNow = view.findViewById(R.id.notNow);

        permissionHead.setText(getString(heading));
        permissionDesc.setText(getString(desc));
        permissionStep1.setText(getString(step1));
        permissionStep2.setText(getString(step2));
        permissionStep3.setVisibility(GONE);

        proceed.setOnClickListener(v -> {
            if (!batteryOptimizationIgnored) {
                requestBattery();
            } else {
                if (bottomSheetDialog.isShowing()) {
                    bottomSheetDialog.dismiss();
                }
                Toast.makeText(this, "Already Granted", Toast.LENGTH_SHORT).show();
            }
        });


        notNow.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }

    @SuppressLint("BatteryLife")
    private void requestBattery() {
        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
        Intent i = new Intent();

        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            i.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            batteryOptimizationLauncher.launch(i);
        } else {
            Toast.makeText(this, "Already Granted", Toast.LENGTH_SHORT).show();
            if (bottomSheetDialog.isShowing()) {
                bottomSheetDialog.dismiss();
            }
            batteryOptimizationIgnored = true;
        }
    }

    @Override
    protected void onResume() {
        checkPermissionsAndMove();
        super.onResume();
    }
}

















