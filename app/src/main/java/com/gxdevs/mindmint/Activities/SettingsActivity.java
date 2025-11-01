package com.gxdevs.mindmint.Activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;
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
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import com.gxdevs.mindmint.Utils.AdultDomainListManager;
import com.gxdevs.mindmint.Utils.BlockedSitesManager;
import com.gxdevs.mindmint.Utils.Utils;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class SettingsActivity extends AppCompatActivity {

    MaterialSwitch remindDoomScrollingSwitch, blockAfterWastedTimeSwitch, blockBrowsersDoomSwitch, blockAdultSitesSwitch;
    TextView remindDoomSubtitle, blockViewTimeSubtitle, blockBrowsersSubtitle;
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_ENABLED = "pref_block_after_wasted_time_enabled";
    public static final String PREF_BLOCK_AFTER_WASTED_TIME_HOURS = "pref_block_after_wasted_time_hours";
    public static final float DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS = 1.0f;
    public static final String PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED = "pref_block_browsers_doom_enabled";
    public static final String PREF_BLOCK_ADULT_SITES_ENABLED = "pref_block_adult_sites_enabled";
    public static final String PREF_THEME_MODE = "pref_theme_mode";
    public static final String PREF_THEME_ADVANCED_ENABLED = "pref_theme_advanced_enabled";
    public static final String PREF_THEME_COLOR_SECONDARY = "pref_theme_color_secondary";
    public static final String PREF_THEME_COLOR_1 = "pref_theme_color_1";
    public static final String PREF_THEME_COLOR_2 = "pref_theme_color_2";
    public static final String PREF_THEME_COLOR_3 = "pref_theme_color_3";
    private SharedPreferences defaultSharedPreferences;
    private BottomSheetDialog timerPicker;
    private ActivityResultLauncher<Intent> batteryOptimizationLauncher;
    private BottomSheetDialog bottomSheetDialog;
    private ActivityResultLauncher<Intent> accessibilityLauncher;
    private boolean batteryOptimizationIgnored = false;
    // Cached views for permission card and battery UI
    private MaterialCardView permissionCard;
    private ImageView assArrow;
    private ImageView batteryArrow;
    private TextView batteryTxt;
    private TextView assTxt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.applyAppThemeFromPrefs(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        Utils.setPad(findViewById(R.id.main), "bottom", this);
        // Smooth fade-in to mask theme (re)applies
        View rootFade = findViewById(R.id.main);
        if (rootFade != null) {
            rootFade.setAlpha(0f);
            rootFade.animate().alpha(1f).setDuration(180).start();
        }
        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        remindDoomSubtitle = findViewById(R.id.remindDoomSubtitle);
        blockViewTimeSubtitle = findViewById(R.id.blockViewTimeSubtitle);
        blockBrowsersSubtitle = findViewById(R.id.blockBrowsersSubtitle);

        bottomSheetDialog = new BottomSheetDialog(SettingsActivity.this);
        registerForPermission();
        registerAccessibilityLauncher();

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
        blockBrowsersDoomSwitch = findViewById(R.id.blockBrowsersDoomSwitch);
        blockAdultSitesSwitch = findViewById(R.id.blockAdultSitesSwitch);
        TextView adultListUpdate = findViewById(R.id.adultListUpdate);


        // Strict mode views
        // Load saved values for reminder feature
        boolean isRemindDoomScrollingEnabled = defaultSharedPreferences.getBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
        remindDoomScrollingSwitch.setChecked(isRemindDoomScrollingEnabled);
        updateRemindDoomTimeDisplay();

        // Load saved values for blocking feature
        blockAfterWastedTimeSwitch.setChecked(defaultSharedPreferences.getBoolean(PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false));
        updateBlockAfterWastedTimeDisplay();

        // Load saved value for blocking browsers doomscrolling
        boolean isBlockBrowsersEnabled = defaultSharedPreferences.getBoolean(PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, false);
        blockBrowsersDoomSwitch.setChecked(isBlockBrowsersEnabled);
        blockBrowsersSubtitle.setVisibility(isBlockBrowsersEnabled ? VISIBLE : GONE);

        boolean isBlockAdultEnabled = defaultSharedPreferences.getBoolean(PREF_BLOCK_ADULT_SITES_ENABLED, false);
        if (blockAdultSitesSwitch != null) blockAdultSitesSwitch.setChecked(isBlockAdultEnabled);
        if (adultListUpdate != null)
            adultListUpdate.setVisibility(isBlockAdultEnabled ? VISIBLE : GONE);


        // Listeners for reminder feature
        remindDoomScrollingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !isAccessibilityPermissionGranted()) {
                Toast.makeText(this, "Requires Accessibility permission", Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                showAccessibilityCard();
                return;
            }
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
            if (isChecked && !isAccessibilityPermissionGranted()) {
                Toast.makeText(this, "Requires Accessibility permission", Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                showAccessibilityCard();
                return;
            }
            defaultSharedPreferences.edit().putBoolean(PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, isChecked).apply();
            if (!isChecked) {
                blockViewTimeSubtitle.setVisibility(GONE);
            } else {
                blockViewTimeSubtitle.setVisibility(VISIBLE);
            }
        });

        blockViewTimeSubtitle.setOnClickListener(v -> showTimePickerBottomSheet(false));

        // Listener for browsers doomscrolling switch
        blockBrowsersDoomSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !isAccessibilityPermissionGranted()) {
                Toast.makeText(this, "Requires Accessibility permission", Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                showAccessibilityCard();
                return;
            }
            defaultSharedPreferences.edit().putBoolean(PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, isChecked).apply();
            blockBrowsersSubtitle.setVisibility(isChecked ? VISIBLE : GONE);
            if (isChecked) {
                // One-time seeding only if empty and never seeded before
                BlockedSitesManager.seedDefaultsIfFirstTimeAndEmpty(this);
            }
        });

        if (blockAdultSitesSwitch != null) {
            blockAdultSitesSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !isAccessibilityPermissionGranted()) {
                    Toast.makeText(this, "Requires Accessibility permission", Toast.LENGTH_SHORT).show();
                    buttonView.setChecked(false);
                    showAccessibilityCard();
                    return;
                }
                if (isChecked) {
                    showAdultListDownloadDialogAndEnsure();
                } else {
                    defaultSharedPreferences.edit().putBoolean(PREF_BLOCK_ADULT_SITES_ENABLED, false).apply();
                }
                if (adultListUpdate != null)
                    adultListUpdate.setVisibility(isChecked ? VISIBLE : GONE);
            });
        }

        if (adultListUpdate != null) {
            adultListUpdate.setOnClickListener(v -> showAdultListDownloadDialogAndEnsure());
        }

        ConstraintLayout blockBrowsersLayout = findViewById(R.id.blockBrowsersLayout);
        if (blockBrowsersLayout != null) {
            blockBrowsersLayout.setOnClickListener(v -> {
                if (!isAccessibilityPermissionGranted()) {
                    Toast.makeText(this, "Requires Accessibility permission", Toast.LENGTH_SHORT).show();
                    showAccessibilityCard();
                    return;
                }
                Intent intent = new Intent(SettingsActivity.this, SiteBlockerActivity.class);
                startActivity(intent);
            });
        }

        // Initialize Strict mode states
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        batteryOptimizationIgnored = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());

        BlurView timeBlur = findViewById(R.id.timeBlur);
        BlurView blockingBlur = findViewById(R.id.blockingBlur);
        BlurView permissionBlurAcc = findViewById(R.id.permissionBlurAcc);
        BlurView themingBlur = findViewById(R.id.themingBlur);
        BlurTarget blurTarget = findViewById(R.id.blurTarget);
        // Cache once to avoid multiple local assignments
        permissionCard = findViewById(R.id.PermissionCard);
        batteryTxt = findViewById(R.id.batteryTxt);
        assTxt = findViewById(R.id.assTxt);
        assArrow = findViewById(R.id.assArrow);
        batteryArrow = findViewById(R.id.batteryArrow);

        timeBlur.setupWith(blurTarget).setBlurRadius(5f);
        blockingBlur.setupWith(blurTarget).setBlurRadius(5f);
        permissionBlurAcc.setupWith(blurTarget).setBlurRadius(5f);
        themingBlur.setupWith(blurTarget).setBlurRadius(5f);

        batteryTxt.setOnClickListener(v -> showBottomSheet(R.string.batter_permission, R.string.why_battery, R.string.click_on_proceed, R.string.step2allow));
        batteryArrow.setOnClickListener(v -> showBottomSheet(R.string.batter_permission, R.string.why_battery, R.string.click_on_proceed, R.string.step2allow));
        assTxt.setOnClickListener(v -> showAccessibilityBottomSheet());
        assArrow.setOnClickListener(v -> showAccessibilityBottomSheet());
        // Theming: Spinner and Color Pickers
        Spinner themeSpinner = findViewById(R.id.themeSpinner);
        TextView advancedOptionsToggle = findViewById(R.id.advancedOptionsToggle);
        TextView reset = findViewById(R.id.reset);
        ConstraintLayout secondaryRow = findViewById(R.id.secondaryRow);
        ImageView secondaryPreview = findViewById(R.id.secondaryPreview);
        ImageView colorPreview1 = findViewById(R.id.colorPreview1);
        ImageView colorPreview2 = findViewById(R.id.colorPreview2);
        ImageView colorPreview3 = findViewById(R.id.colorPreview3);
        ConstraintLayout colorRow1 = findViewById(R.id.colorRow1);
        ConstraintLayout colorRow2 = findViewById(R.id.colorRow2);
        ConstraintLayout colorRow3 = findViewById(R.id.colorRow3);

        if (themeSpinner != null) {
            String[] themeOptions = new String[]{"Dark Theme", "Light Theme", "System"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, themeOptions);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            themeSpinner.setAdapter(adapter);

            String savedMode = defaultSharedPreferences.getString(PREF_THEME_MODE, "Dark Theme");
            int selectedIndex; // Dark by default
            if ("Dark Theme".equals(savedMode) || "dark".equalsIgnoreCase(savedMode))
                selectedIndex = 0;
            else if ("Light Theme".equals(savedMode) || "light".equalsIgnoreCase(savedMode))
                selectedIndex = 1;
            else selectedIndex = 2; // System
            themeSpinner.setSelection(selectedIndex, false);

            themeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String value = themeOptions[position];
                    String current = defaultSharedPreferences.getString(PREF_THEME_MODE, "Dark Theme");
                    if (!value.equals(current)) {
                        defaultSharedPreferences.edit().putString(PREF_THEME_MODE, value).apply();
                        Utils.applyAppThemeFromPrefs(SettingsActivity.this);
                        View root = findViewById(R.id.main);
                        Runnable doRecreate = () -> {
                            applyColors();
                            recreate();
                        };
                        if (root != null) {
                            root.animate().alpha(0f).setDuration(180).withEndAction(doRecreate).start();
                        } else {
                            themeSpinner.post(doRecreate);
                        }
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }

        // Defaults for advanced colors
        int defaultSecondary = ContextCompat.getColor(this, R.color.cardColor);
        int arc1Initial = android.graphics.Color.parseColor("#59FD7D81");
        int arc2Initial = android.graphics.Color.parseColor("#2614B8A6");
        int arc3Initial = android.graphics.Color.parseColor("#33A78BFA");

        boolean isAdvancedEnabled = defaultSharedPreferences.getBoolean(PREF_THEME_ADVANCED_ENABLED, false);
        if (secondaryRow != null) secondaryRow.setVisibility(isAdvancedEnabled ? VISIBLE : GONE);
        if (colorRow1 != null) colorRow1.setVisibility(isAdvancedEnabled ? VISIBLE : GONE);
        if (colorRow2 != null) colorRow2.setVisibility(isAdvancedEnabled ? VISIBLE : GONE);
        if (colorRow3 != null) colorRow3.setVisibility(isAdvancedEnabled ? VISIBLE : GONE);
        if (reset != null) reset.setVisibility(isAdvancedEnabled ? VISIBLE : GONE);
        if (advancedOptionsToggle != null) {
            updateAdvancedToggleIndicator(advancedOptionsToggle, isAdvancedEnabled);
        }

        if (advancedOptionsToggle != null) {
            advancedOptionsToggle.setOnClickListener(v -> {
                boolean now = !(secondaryRow != null && secondaryRow.getVisibility() == VISIBLE);
                if (secondaryRow != null) secondaryRow.setVisibility(now ? VISIBLE : GONE);
                if (colorRow1 != null) colorRow1.setVisibility(now ? VISIBLE : GONE);
                if (colorRow2 != null) colorRow2.setVisibility(now ? VISIBLE : GONE);
                if (colorRow3 != null) colorRow3.setVisibility(now ? VISIBLE : GONE);
                if (reset != null) reset.setVisibility(now ? VISIBLE : GONE);
                updateAdvancedToggleIndicator(advancedOptionsToggle, now);
            });
        }

        if (reset != null) {
            reset.setOnClickListener(v -> {
                defaultSharedPreferences.edit()
                        .remove(PREF_THEME_COLOR_SECONDARY)
                        .remove(PREF_THEME_COLOR_1)
                        .remove(PREF_THEME_COLOR_2)
                        .remove(PREF_THEME_COLOR_3)
                        .putBoolean(PREF_THEME_ADVANCED_ENABLED, false)
                        .apply();
                int sec = Utils.getEffectiveSecondaryColor(SettingsActivity.this);
                if (secondaryPreview != null)
                    secondaryPreview.setImageDrawable(new ColorDrawable(sec));
                applyColors();
            });
        }

        int savedSecondary = defaultSharedPreferences.getInt(PREF_THEME_COLOR_SECONDARY, defaultSecondary);
        int savedColor1 = defaultSharedPreferences.getInt(PREF_THEME_COLOR_1, arc1Initial);
        int savedColor2 = defaultSharedPreferences.getInt(PREF_THEME_COLOR_2, arc2Initial);
        int savedColor3 = defaultSharedPreferences.getInt(PREF_THEME_COLOR_3, arc3Initial);

        if (secondaryPreview != null)
            secondaryPreview.setImageDrawable(new ColorDrawable(savedSecondary));
        if (colorPreview1 != null) colorPreview1.setImageDrawable(new ColorDrawable(savedColor1));
        if (colorPreview2 != null) colorPreview2.setImageDrawable(new ColorDrawable(savedColor2));
        if (colorPreview3 != null) colorPreview3.setImageDrawable(new ColorDrawable(savedColor3));

        View.OnClickListener secondaryPicker = v -> showColorPickerDialog("Secondary color", defaultSharedPreferences.getInt(PREF_THEME_COLOR_SECONDARY, defaultSecondary), color -> {
            if (secondaryPreview != null)
                secondaryPreview.setImageDrawable(new ColorDrawable(color));
            defaultSharedPreferences.edit()
                    .putInt(PREF_THEME_COLOR_SECONDARY, color)
                    .putBoolean(PREF_THEME_ADVANCED_ENABLED, true)
                    .apply();
            applyColors();
        });
        View.OnClickListener colorPickerLauncher1 = v -> showColorPickerDialog("Aura Arc 1", defaultSharedPreferences.getInt(PREF_THEME_COLOR_1, arc1Initial), color -> {
            if (colorPreview1 != null) colorPreview1.setImageDrawable(new ColorDrawable(color));
            defaultSharedPreferences.edit()
                    .putInt(PREF_THEME_COLOR_1, color)
                    .putBoolean(PREF_THEME_ADVANCED_ENABLED, true)
                    .apply();
            applyColors();
        });
        View.OnClickListener colorPickerLauncher2 = v -> showColorPickerDialog("Aura Arc 2", defaultSharedPreferences.getInt(PREF_THEME_COLOR_2, arc2Initial), color -> {
            if (colorPreview2 != null) colorPreview2.setImageDrawable(new ColorDrawable(color));
            defaultSharedPreferences.edit()
                    .putInt(PREF_THEME_COLOR_2, color)
                    .putBoolean(PREF_THEME_ADVANCED_ENABLED, true)
                    .apply();
            applyColors();
        });
        View.OnClickListener colorPickerLauncher3 = v -> showColorPickerDialog("Aura Arc 3", defaultSharedPreferences.getInt(PREF_THEME_COLOR_3, arc3Initial), color -> {
            if (colorPreview3 != null) colorPreview3.setImageDrawable(new ColorDrawable(color));
            defaultSharedPreferences.edit()
                    .putInt(PREF_THEME_COLOR_3, color)
                    .putBoolean(PREF_THEME_ADVANCED_ENABLED, true)
                    .apply();
            applyColors();
        });

        if (secondaryRow != null) secondaryRow.setOnClickListener(secondaryPicker);
        if (secondaryPreview != null) secondaryPreview.setOnClickListener(secondaryPicker);
        if (colorRow1 != null) colorRow1.setOnClickListener(colorPickerLauncher1);
        if (colorRow2 != null) colorRow2.setOnClickListener(colorPickerLauncher2);
        if (colorRow3 != null) colorRow3.setOnClickListener(colorPickerLauncher3);
        if (colorPreview1 != null) colorPreview1.setOnClickListener(colorPickerLauncher1);
        if (colorPreview2 != null) colorPreview2.setOnClickListener(colorPickerLauncher2);
        if (colorPreview3 != null) colorPreview3.setOnClickListener(colorPickerLauncher3);
        checkPermissionsAndMove();
        applyColors();
        findViewById(R.id.backButton).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        // --- END: NEW FEATURES INITIALIZATION ---
    }

    private interface OnColorSelected {
        void onSelected(int color);
    }

    private void showColorPickerDialog(String title, int initialColor, OnColorSelected callback) {
        new ColorPickerDialog.Builder(this)
                .setTitle(title)
                .setPositiveButton(getString(R.string.ok), (ColorEnvelopeListener) (ColorEnvelope envelope, boolean fromUser) -> {
                    callback.onSelected(envelope.getColor());
                })
                .setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> dialogInterface.dismiss())
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .show();
    }

    private void showTimePickerBottomSheet(boolean isRemindDoomScrolling) {
        if (!isAccessibilityPermissionGranted()) {
            Toast.makeText(this, "Requires Accessibility permission", Toast.LENGTH_SHORT).show();
            showAccessibilityCard();
            return;
        }
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

    private void updateAdvancedToggleIndicator(TextView toggle, boolean expanded) {
        if (toggle == null) return;
        Drawable base = ContextCompat.getDrawable(this, R.drawable.ic_chevron_right);
        if (base == null) return;
        RotateDrawable rotated = new RotateDrawable();
        rotated.setDrawable(base);
        rotated.setTint(resolveAttrColor(this, R.attr.tintGlobalColor));
        float degrees = expanded ? 270f : 90f; // up when expanded, down when collapsed
        rotated.setFromDegrees(degrees);
        rotated.setToDegrees(degrees);
        rotated.setPivotX(0.5f);
        rotated.setPivotY(0.5f);
        rotated.setLevel(10000);
        toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, rotated, null);
    }

    private static int resolveAttrColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
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
        batteryOptimizationIgnored = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        // Battery row: control heading text and its arrow (top arrow)
        batteryTxt.setVisibility(batteryOptimizationIgnored ? GONE : VISIBLE);
        batteryArrow.setVisibility(batteryOptimizationIgnored ? GONE : VISIBLE);
        updatePermissionCardVisibility();
    }

    private void updatePermissionCardVisibility() {
        boolean hideCard = batteryOptimizationIgnored && isAccessibilityPermissionGranted();
        if (permissionCard != null) permissionCard.setVisibility(hideCard ? GONE : VISIBLE);
    }

    private void showBottomSheet(int heading, int desc, int step1, int step2) {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_bottomsheet, findViewById(R.id.sheetContainer));
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

    private void showAccessibilityBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(SettingsActivity.this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_bottomsheet, findViewById(R.id.sheetContainer));
        TextView permissionHead = view.findViewById(R.id.permissionHead);
        TextView permissionDesc = view.findViewById(R.id.permissionDesc);
        TextView permissionStep1 = view.findViewById(R.id.permissionStep1);
        TextView permissionStep2 = view.findViewById(R.id.permissionStep2);
        TextView permissionStep3 = view.findViewById(R.id.permissionStep3);
        TextView proceed = view.findViewById(R.id.proceed);
        TextView notNow = view.findViewById(R.id.notNow);

        permissionHead.setText(getString(R.string.ass_permission));
        permissionDesc.setText(getString(R.string.why_accessibility));
        permissionStep1.setText(getString(R.string.click_on_proceed));
        permissionStep2.setText(getString(R.string.select_installed_apps));
        permissionStep3.setText(getString(R.string.enableApp));

        proceed.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            if (accessibilityLauncher != null) accessibilityLauncher.launch(intent);
            dialog.dismiss();
        });
        notNow.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.show();
    }

    private void registerAccessibilityLauncher() {
        accessibilityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> updateAccessibilityPermissionUi());
    }

    private boolean isAccessibilityPermissionGranted() {
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String packageName = getPackageName();
        return enabledServices != null && enabledServices.contains(packageName);
    }

    private void showAccessibilityCard() {
        if (assTxt != null) assTxt.setVisibility(VISIBLE);
        if (assArrow != null) assArrow.setVisibility(VISIBLE);
    }

    private void updateAccessibilityPermissionUi() {
        boolean granted = isAccessibilityPermissionGranted();
        // Accessibility row: control notice text and its arrow (bottom arrow)
        if (assTxt != null) assTxt.setVisibility(granted ? GONE : VISIBLE);
        if (assArrow != null) assArrow.setVisibility(granted ? GONE : VISIBLE);
        updatePermissionCardVisibility();

        if (!granted) {
            if (remindDoomScrollingSwitch != null) {
                remindDoomScrollingSwitch.setChecked(false);
                defaultSharedPreferences.edit().putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false).apply();
            }
            if (blockAfterWastedTimeSwitch != null) {
                blockAfterWastedTimeSwitch.setChecked(false);
                defaultSharedPreferences.edit().putBoolean(PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false).apply();
            }
            if (blockBrowsersDoomSwitch != null) {
                blockBrowsersDoomSwitch.setChecked(false);
                defaultSharedPreferences.edit().putBoolean(PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, false).apply();
            }
            if (blockBrowsersSubtitle != null) blockBrowsersSubtitle.setVisibility(GONE);
            TextView adultListUpdateView = findViewById(R.id.adultListUpdate);
            if (adultListUpdateView != null) adultListUpdateView.setVisibility(GONE);
            if (blockAdultSitesSwitch != null) {
                blockAdultSitesSwitch.setChecked(false);
                defaultSharedPreferences.edit().putBoolean(PREF_BLOCK_ADULT_SITES_ENABLED, false).apply();
            }
        }
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
        updateAccessibilityPermissionUi();
        applyColors();
    }

    private void applyColors() {
        View arc1 = findViewById(R.id.arcTopLeft);
        View arc2 = findViewById(R.id.arcBottomRight);
        View arc3 = findViewById(R.id.arcBottomLeft);
        MaterialCardView timeLimits = findViewById(R.id.timeLimits);
        MaterialCardView blockingHolder = findViewById(R.id.blockingHolder);
        MaterialCardView themingCard = findViewById(R.id.themingCard);

        if (permissionCard != null) Utils.applySecondaryColor(permissionCard, this);
        Utils.applySecondaryColor(timeLimits, this);
        Utils.applySecondaryColor(blockingHolder, this);
        Utils.applySecondaryColor(themingCard, this);
        Utils.applyAccentColors(arc1, arc2, arc3, this);
    }

    private void showAdultListDownloadDialogAndEnsure() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_adult_list_progress, null);
        builder.setView(view);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        AdultDomainListManager.downloadAndBuildList(this, new AdultDomainListManager.OnDownloadCompleteListener() {
            @Override
            public void onSuccess(long mergedFileBytes, @Nullable String sha256Hex, boolean deduped) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this)
                            .edit().putBoolean(PREF_BLOCK_ADULT_SITES_ENABLED, true).apply();
                    Toast.makeText(SettingsActivity.this, "Adult sites list ready", Toast.LENGTH_SHORT).show();
                    TextView adultListUpdateView = findViewById(R.id.adultListUpdate);
                    if (adultListUpdateView != null) adultListUpdateView.setVisibility(VISIBLE);
                    if (blockAdultSitesSwitch != null) blockAdultSitesSwitch.setChecked(true);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).edit().putBoolean(PREF_BLOCK_ADULT_SITES_ENABLED, false).apply();
                    if (blockAdultSitesSwitch != null) blockAdultSitesSwitch.setChecked(false);
                    Toast.makeText(SettingsActivity.this, "Failed to prepare list", Toast.LENGTH_SHORT).show();
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Crash error", Log.getStackTraceString(e));
                    clipboard.setPrimaryClip(clip);
                });
            }
        }, true);
    }
}

















