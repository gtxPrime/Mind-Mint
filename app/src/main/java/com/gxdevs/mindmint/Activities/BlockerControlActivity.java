package com.gxdevs.mindmint.Activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.slider.Slider;
import com.gxdevs.mindmint.Common.IntentActions;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;
import com.gxdevs.mindmint.Utils.ChallengeLockManager;
import com.gxdevs.mindmint.Utils.SettingsLockManager;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.db.MindMintRoomDatabase;
import com.gxdevs.mindmint.db.dao.BlockedAppDao;
import com.gxdevs.mindmint.db.entities.BlockedAppEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BlockerControlActivity extends AppCompatActivity {

    private SharedPreferences sharedPrefs;
    private MindMintRoomDatabase database;
    private BlockedAppDao blockedAppDao;
    private SettingsLockManager settingsLockMgr;
    private ChallengeLockManager challengeLockMgr;

    private MaterialSwitch switchServiceStatus;
    private Slider sliderIntensity;
    private TextView tvLevelDesc;
    private MaterialCardView cardLevelSettings;
    private LinearLayout layoutLevelSettings;
    private RecyclerView rvRestrictedApps;
    private AppAdapter appAdapter;

    private MaterialSwitch switchWebBlocker;
    private MaterialSwitch switchAdultBlocker;
    private MaterialSwitch switchSettingsLock;
    private MaterialSwitch switchDeviceAdmin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocker_control);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        database = MindMintRoomDatabase.getInstance(this);
        blockedAppDao = database.blockedAppDao();
        settingsLockMgr = new SettingsLockManager(this);
        challengeLockMgr = new ChallengeLockManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // Authenticate immediately if lock is enabled
        if (settingsLockMgr.isLockEnabled()) {
            settingsLockMgr.authenticate(this, "Access Blocker settings", new SettingsLockManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    initViews();
                    setupListeners();
                    loadSettings();
                }

                @Override
                public void onFailure(@Nullable String reason) {
                    finish();
                }
            });
        } else {
            initViews();
            setupListeners();
            loadSettings();
        }
    }

    private void initViews() {
        switchServiceStatus = findViewById(R.id.switchServiceStatus);
        sliderIntensity = findViewById(R.id.sliderIntensity);
        tvLevelDesc = findViewById(R.id.tvLevelDesc);
        cardLevelSettings = findViewById(R.id.cardLevelSettings);
        layoutLevelSettings = findViewById(R.id.layoutLevelSettings);
        rvRestrictedApps = findViewById(R.id.rvRestrictedApps);
        
        switchWebBlocker = findViewById(R.id.switchWebBlocker);
        switchAdultBlocker = findViewById(R.id.switchAdultBlocker);
        switchSettingsLock = findViewById(R.id.switchSettingsLock);
        switchDeviceAdmin = findViewById(R.id.switchDeviceAdmin);

        rvRestrictedApps.setLayoutManager(new LinearLayoutManager(this));
        appAdapter = new AppAdapter();
        rvRestrictedApps.setAdapter(appAdapter);
    }

    private void setupListeners() {
        switchServiceStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Note: 0 pause duration means resume, positive duration means paused
            boolean isPaused = !isChecked;
            if (isPaused) {
                // Confirm with user by showing pause options
                showPausePicker();
            } else {
                setServicePauseState(false, 0);
            }
        });

        sliderIntensity.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {}

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                int progress = (int) slider.getValue();
                if (progress == 4) {
                    // Strict lockout warning
                    showOneDayLockWarning(() -> saveIntensity(4), () -> {
                        // Revert
                        int current = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0);
                        sliderIntensity.setValue((float) current);
                    });
                } else {
                    saveIntensity(progress);
                }
            }
        });

        sliderIntensity.addOnChangeListener((slider, value, fromUser) -> {
            int progress = (int) value;
            updateLevelDescription(progress);
            updateDynamicLevelSettings(progress);
        });

        findViewById(R.id.rowWebBlocker).setOnClickListener(v -> {
            if (!Utils.isAccessibilityPermissionGranted(this)) {
                Toast.makeText(this, "Accessibility permission is required.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, SiteBlockerActivity.class));
        });

        switchWebBlocker.setOnCheckedChangeListener((btn, isChecked) -> {
            sharedPrefs.edit().putBoolean(AppUsageAccessibilityService.PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, isChecked).apply();
            notifyServiceConfigChanged();
        });

        switchAdultBlocker.setOnCheckedChangeListener((btn, isChecked) -> {
            sharedPrefs.edit().putBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, isChecked).apply();
            notifyServiceConfigChanged();
        });

        switchSettingsLock.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                settingsLockMgr.setLockEnabled(true);
                String currentType = sharedPrefs.getString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "device");
                if ("custom".equals(currentType) && !settingsLockMgr.hasCustomPin()) {
                    settingsLockMgr.showSetCustomPinDialog(this, false, () -> loadSettings());
                }
            } else {
                btn.setChecked(true);
                settingsLockMgr.authenticate(this, "Disable Settings Lock", new SettingsLockManager.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        settingsLockMgr.setLockEnabled(false);
                        btn.setChecked(false);
                    }

                    @Override
                    public void onFailure(@Nullable String reason) {}
                });
            }
        });

        switchDeviceAdmin.setOnCheckedChangeListener((btn, isChecked) -> {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName component = new android.content.ComponentName(this, com.gxdevs.mindmint.Receivers.MindMintDeviceAdminReceiver.class);
            boolean active = dpm != null && dpm.isAdminActive(component);

            if (isChecked) {
                if (!active) {
                    Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component);
                    intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Grants Mind Mint Device Admin rights to prevent uninstall.");
                    startActivity(intent);
                }
            } else {
                if (active) {
                    btn.setChecked(true);
                    settingsLockMgr.authenticate(this, "Disable Device Admin Protection", new SettingsLockManager.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            sharedPrefs.edit().putLong(AppUsageAccessibilityService.PREF_ADMIN_GUARD_TRUSTED_TOKEN, System.currentTimeMillis()).apply();
                            Toast.makeText(BlockerControlActivity.this, "Disable it from security settings.", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
                        }

                        @Override
                        public void onFailure(@Nullable String reason) {}
                    });
                }
            }
        });
    }

    private void loadSettings() {
        boolean isPaused = sharedPrefs.getBoolean("isServicePaused", false);
        switchServiceStatus.setChecked(!isPaused);

        int intensity = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0);
        sliderIntensity.setValue((float) intensity);
        updateLevelDescription(intensity);
        updateDynamicLevelSettings(intensity);

        switchWebBlocker.setChecked(sharedPrefs.getBoolean(AppUsageAccessibilityService.PREF_BLOCK_BROWSERS_DOOMSCROLLING_ENABLED, false));
        switchAdultBlocker.setChecked(sharedPrefs.getBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, false));
        switchSettingsLock.setChecked(settingsLockMgr.isLockEnabled());

        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName component = new android.content.ComponentName(this, com.gxdevs.mindmint.Receivers.MindMintDeviceAdminReceiver.class);
        switchDeviceAdmin.setChecked(dpm != null && dpm.isAdminActive(component));

        // Load apps list (excluding mods from UI view)
        List<BlockedAppEntity> allApps = blockedAppDao.getAllSync();
        List<BlockedAppEntity> displayApps = new ArrayList<>();
        for (BlockedAppEntity app : allApps) {
            if (isModPackage(app.packageName)) {
                continue;
            }
            displayApps.add(app);
        }
        appAdapter.setApps(displayApps);
    }

    private void updateLevelDescription(int level) {
        String desc;
        switch (level) {
            case 0:
                desc = "NONE: All blocker features are completely turned off.";
                break;
            case 1:
                desc = "FRICTION: Shows a challenge when opening. Once completed, stays open until you leave/close it.";
                break;
            case 2:
                desc = "REMINDER: Periodically shows a self-dismissing warning message while scrolling.";
                break;
            case 3:
                desc = "TEMP LOCK: Lock apps for the rest of the day once you reach daily scrolling or time limits.";
                break;
            case 4:
                desc = "PERMANENT: Complete block of restricted apps. No bypasses, no challenges allowed.";
                break;
            default:
                desc = "";
        }
        tvLevelDesc.setText(desc);
    }

    private void saveIntensity(int level) {
        sharedPrefs.edit().putInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, level).apply();
        
        // Sync older trigger fields for accessibility service compatibility
        SharedPreferences.Editor editor = sharedPrefs.edit();
        if (level == 2) {
            editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, true);
            editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
        } else if (level == 3) {
            editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, true);
            editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, true);
        } else if (level == 4) {
            editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
            editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
            editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "oneday");
        } else {
            editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
            editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
        }
        editor.apply();

        notifyServiceConfigChanged();
    }

    private void updateDynamicLevelSettings(int level) {
        layoutLevelSettings.removeAllViews();
        if (level == 0) {
            cardLevelSettings.setVisibility(View.GONE);
            return;
        }

        cardLevelSettings.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);

        if (level == 1) { // FRICTION
            View view = inflater.inflate(R.layout.item_settings_block_trigger_tab, layoutLevelSettings, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (params != null) {
                params.leftMargin = 0;
                params.rightMargin = 0;
                view.setLayoutParams(params);
            }
            view.setElevation(0f);
            
            TextView tabLabel = view.findViewById(R.id.tabLabel);
            tabLabel.setText("Challenge Type");
            
            LinearLayout tabContainer = view.findViewById(R.id.tabContainer);
            tabContainer.removeAllViews();

            String currentChallenge = sharedPrefs.getString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math");
            if ("none".equals(currentChallenge) || "oneday".equals(currentChallenge)) {
                currentChallenge = "math";
                sharedPrefs.edit().putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math").apply();
            }

            // Create simple pill buttons
            String[] types = {"Math", "Shake", "Scream"};
            String[] typeKeys = {"math", "shake", "scream"};
            for (int i = 0; i < types.length; i++) {
                final int index = i;
                TextView tv = new TextView(this);
                tv.setText(types[index]);
                tv.setTextSize(11);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setPadding(Utils.dpToPx(12, this), 0, Utils.dpToPx(12, this), 0);
                tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

                if (typeKeys[index].equals(currentChallenge)) {
                    tv.setBackgroundResource(R.drawable.bg_segment_selected);
                    tv.setTextColor(ContextCompat.getColor(this, R.color.white));
                } else {
                    tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                }

                tv.setOnClickListener(v -> {
                    sharedPrefs.edit().putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, typeKeys[index]).apply();
                    updateDynamicLevelSettings(1);
                    notifyServiceConfigChanged();
                });
                tabContainer.addView(tv);
            }
            layoutLevelSettings.addView(view);

        } else if (level == 2) { // REMINDER
            // 1. Seekbar for Remind Interval
            LinearLayout row1 = createSeekBarRow("Reminder Interval", "Popup warning interval", 1, 60, 
                sharedPrefs.getInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, 5), "m",
                val -> {
                    sharedPrefs.edit().putInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, val).apply();
                    notifyServiceConfigChanged();
                });
            layoutLevelSettings.addView(row1);

            // Divider
            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            div.setBackgroundColor(ContextCompat.getColor(this, R.color.glass_stroke));
            layoutLevelSettings.addView(div);

            // 2. Seekbar for Popup Duration
            LinearLayout row2 = createSeekBarRow("Popup Warning Duration", "Display length before auto-dismiss", 3, 15, 
                sharedPrefs.getInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, 5), "s",
                val -> {
                    sharedPrefs.edit().putInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, val).apply();
                    notifyServiceConfigChanged();
                });
            layoutLevelSettings.addView(row2);

        } else if (level == 3) { // TEMP LOCK
            // 1. Scroll Limit Selector
            LinearLayout row1 = createSeekBarRow("Daily Scroll Limit", "Scroll limit before strict lockout", 10, 500,
                (int) sharedPrefs.getLong("pref_daily_scroll_limit", 100), " scrolls",
                val -> sharedPrefs.edit().putLong("pref_daily_scroll_limit", val).apply());
            layoutLevelSettings.addView(row1);

            // Divider
            View div1 = new View(this);
            div1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            div1.setBackgroundColor(ContextCompat.getColor(this, R.color.glass_stroke));
            layoutLevelSettings.addView(div1);

            // 2. Daily Time Limit (minutes)
            int currentHoursVal = (int) (sharedPrefs.getFloat(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_HOURS, 1f) * 60);
            LinearLayout row2 = createSeekBarRow("Daily Time Limit", "Time usage allowed per day", 5, 240,
                currentHoursVal, "m",
                val -> {
                    sharedPrefs.edit().putFloat(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_HOURS, (float) val / 60).apply();
                    notifyServiceConfigChanged();
                });
            layoutLevelSettings.addView(row2);

            // Divider
            View div2 = new View(this);
            div2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            div2.setBackgroundColor(ContextCompat.getColor(this, R.color.glass_stroke));
            layoutLevelSettings.addView(div2);

            // 3. Optional Hybrid Toggle
            LinearLayout hybridRow = new LinearLayout(this);
            hybridRow.setOrientation(LinearLayout.HORIZONTAL);
            hybridRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            hybridRow.setPadding(Utils.dpToPx(16, this), Utils.dpToPx(8, this), Utils.dpToPx(16, this), Utils.dpToPx(8, this));

            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            infoLayout.setLayoutParams(lp);

            TextView title = new TextView(this);
            title.setText("Require Friction on Open");
            title.setTextSize(14);
            title.setTextColor(ContextCompat.getColor(this, R.color.white));
            infoLayout.addView(title);

            TextView desc = new TextView(this);
            desc.setText("Force a challenge when opening under the daily limit");
            desc.setTextSize(11);
            desc.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            infoLayout.addView(desc);

            hybridRow.addView(infoLayout);

            MaterialSwitch hybridSwitch = new MaterialSwitch(this);
            hybridSwitch.setChecked(sharedPrefs.getBoolean("pref_temp_lock_friction_enabled", false));
            hybridSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                sharedPrefs.edit().putBoolean("pref_temp_lock_friction_enabled", isChecked).apply();
                notifyServiceConfigChanged();
                updateDynamicLevelSettings(3); // Re-inflate to show/hide challenge selection if needed
            });
            hybridRow.addView(hybridSwitch);
            layoutLevelSettings.addView(hybridRow);

            if (sharedPrefs.getBoolean("pref_temp_lock_friction_enabled", false)) {
                // Add challenge selection if hybrid is ON
                View challView = inflater.inflate(R.layout.item_settings_block_trigger_tab, layoutLevelSettings, false);
                ViewGroup.MarginLayoutParams challParams = (ViewGroup.MarginLayoutParams) challView.getLayoutParams();
                if (challParams != null) {
                    challParams.leftMargin = 0;
                    challParams.rightMargin = 0;
                    challView.setLayoutParams(challParams);
                }
                challView.setElevation(0f);

                TextView tabLabel = challView.findViewById(R.id.tabLabel);
                tabLabel.setText("Challenge Type");
                
                LinearLayout tabContainer = challView.findViewById(R.id.tabContainer);
                tabContainer.removeAllViews();

                String currentChallenge = sharedPrefs.getString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math");
                if ("none".equals(currentChallenge) || "oneday".equals(currentChallenge)) {
                    currentChallenge = "math";
                    sharedPrefs.edit().putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math").apply();
                }

                String[] types = {"Math", "Shake", "Scream"};
                String[] typeKeys = {"math", "shake", "scream"};
                for (int i = 0; i < types.length; i++) {
                    final int index = i;
                    TextView tv = new TextView(this);
                    tv.setText(types[index]);
                    tv.setTextSize(11);
                    tv.setGravity(android.view.Gravity.CENTER);
                    tv.setPadding(Utils.dpToPx(12, this), 0, Utils.dpToPx(12, this), 0);
                    tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    if (typeKeys[index].equals(currentChallenge)) {
                        tv.setBackgroundResource(R.drawable.bg_segment_selected);
                        tv.setTextColor(ContextCompat.getColor(this, R.color.white));
                    } else {
                        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    }

                    tv.setOnClickListener(v -> {
                        sharedPrefs.edit().putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, typeKeys[index]).apply();
                        updateDynamicLevelSettings(3);
                        notifyServiceConfigChanged();
                    });
                    tabContainer.addView(tv);
                }
                layoutLevelSettings.addView(challView);
            }

        } else if (level == 4) { // PERMANENT
            TextView warning = new TextView(this);
            warning.setText("⚠️ WARNING: Predefined apps will be strictly blocked with no challenge bypasses possible.");
            warning.setTextColor(Color.parseColor("#F77381")); // brand_pink/red color
            warning.setTextSize(13);
            warning.setGravity(android.view.Gravity.CENTER);
            warning.setPadding(Utils.dpToPx(16, this), Utils.dpToPx(8, this), Utils.dpToPx(16, this), Utils.dpToPx(8, this));
            layoutLevelSettings.addView(warning);
        }
    }

    private LinearLayout createSeekBarRow(String titleStr, String subStr, int min, int max, int current, String unit, OnValueSelectedListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Utils.dpToPx(16, this), Utils.dpToPx(8, this), Utils.dpToPx(16, this), Utils.dpToPx(8, this));

        // Header: Title and value
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView title = new TextView(this);
        title.setText(titleStr);
        title.setTextSize(14);
        title.setTextColor(ContextCompat.getColor(this, R.color.white));
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(title);

        TextView value = new TextView(this);
        value.setText(current + unit);
        value.setTextSize(13);
        value.setTextColor(ContextCompat.getColor(this, R.color.brainColor));
        header.addView(value);

        row.addView(header);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText(subStr);
        subtitle.setTextSize(11);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        row.addView(subtitle);

        // SeekBar
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(current - min);
        seekBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
        seekBar.setThumbTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brainColor)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = progress + min;
                value.setText(val + unit);
                if (listener != null) listener.onSelected(val);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        row.addView(seekBar);

        return row;
    }

    interface OnValueSelectedListener {
        void onSelected(int val);
    }

    private void showOneDayLockWarning(Runnable onConfirm, Runnable onCancel) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Nuclear Mode")
                .setMessage("Are you sure? Once Permanent Mode is active, you CANNOT bypass any locks or undo this setting for 24 hours.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    if (onCancel != null) onCancel.run();
                })
                .setOnCancelListener(dialog -> {
                    if (onCancel != null) onCancel.run();
                })
                .show();
    }

    private void showPausePicker() {
        Dialog timerDialog = new Dialog(this);
        timerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        timerDialog.setContentView(R.layout.bottom_sheet_time);
        if (timerDialog.getWindow() != null) {
            timerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        android.widget.NumberPicker hourPicker = timerDialog.findViewById(R.id.hours_selector_bottom_sheet);
        android.widget.NumberPicker minutePicker = timerDialog.findViewById(R.id.minutes_selector_bottom_sheet);
        android.widget.Button pauseBtn = timerDialog.findViewById(R.id.setLimitBtnBottomSheet);

        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        pauseBtn.setText("Pause Blocker");

        pauseBtn.setOnClickListener(v -> {
            int h = hourPicker.getValue();
            int m = minutePicker.getValue();
            long pauseDuration = (h * 3600L + m * 60L) * 1000L;

            if (pauseDuration > 0) {
                setServicePauseState(true, pauseDuration);
                Toast.makeText(this, "Blocker paused for " + h + "h " + m + "m", Toast.LENGTH_SHORT).show();
            } else {
                switchServiceStatus.setChecked(true);
            }
            timerDialog.dismiss();
        });

        timerDialog.findViewById(R.id.crossBtn).setOnClickListener(v -> {
            switchServiceStatus.setChecked(true);
            timerDialog.dismiss();
        });

        timerDialog.setOnCancelListener(dialog -> switchServiceStatus.setChecked(true));
        timerDialog.show();
    }

    private void setServicePauseState(boolean isPaused, long pauseDuration) {
        Intent intent = new Intent(IntentActions.getActionPauseService(this));
        intent.putExtra("pause_duration", pauseDuration);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        sharedPrefs.edit()
                .putBoolean("isServicePaused", isPaused)
                .putLong("resumeTime", isPaused ? System.currentTimeMillis() + pauseDuration : 0)
                .apply();
    }

    private void notifyServiceConfigChanged() {
        Intent intent = new Intent(IntentActions.getActionUpdatePackages(this));
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    // --- RECYCLER VIEW ADAPTER ---

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {
        private final List<BlockedAppEntity> apps = new ArrayList<>();

        public void setApps(List<BlockedAppEntity> newApps) {
            apps.clear();
            apps.addAll(newApps);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restrictable_app, parent, false);
            return new AppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            holder.bind(apps.get(position), position);
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class AppViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAppIcon;
            TextView tvAppName;
            MaterialSwitch switchRestricted;
            LinearLayout layoutAppConfig;
            
            View layoutScope;
            TextView btnScopeSection;
            TextView btnScopeFull;

            View layoutMod;
            MaterialCheckBox cbUseMod;
            View divider;

            public AppViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
                tvAppName = itemView.findViewById(R.id.tvAppName);
                switchRestricted = itemView.findViewById(R.id.switchRestricted);
                layoutAppConfig = itemView.findViewById(R.id.layoutAppConfig);
                
                layoutScope = itemView.findViewById(R.id.layoutScope);
                btnScopeSection = itemView.findViewById(R.id.btnScopeSection);
                btnScopeFull = itemView.findViewById(R.id.btnScopeFull);

                layoutMod = itemView.findViewById(R.id.layoutMod);
                cbUseMod = itemView.findViewById(R.id.cbUseMod);
                divider = itemView.findViewById(R.id.divider);
            }

            public void bind(BlockedAppEntity app, int position) {
                tvAppName.setText(app.appName);

                if (divider != null) {
                    divider.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                }

                // Try to load launcher icon
                PackageManager pm = itemView.getContext().getPackageManager();
                try {
                    Drawable icon = pm.getApplicationIcon(app.packageName);
                    ivAppIcon.setImageDrawable(icon);
                    ivAppIcon.setColorFilter(null);
                    ivAppIcon.setBackground(null);
                    ivAppIcon.setPadding(0, 0, 0, 0);
                } catch (Exception e) {
                    // Fallback to default with glass colored backgrounds matching Settings
                    int iconColor;
                    int bgTint;
                    ivAppIcon.setPadding(Utils.dpToPx(10, itemView.getContext()), Utils.dpToPx(10, itemView.getContext()), Utils.dpToPx(10, itemView.getContext()), Utils.dpToPx(10, itemView.getContext()));
                    ivAppIcon.setBackgroundResource(R.drawable.shape_circle);

                    if (app.packageName.contains("youtube")) {
                        ivAppIcon.setImageResource(R.drawable.youtube);
                        iconColor = ContextCompat.getColor(itemView.getContext(), R.color.sexyYt);
                        bgTint = Color.parseColor("#33FF0000"); // 20% transparent red
                    } else if (app.packageName.contains("insta")) {
                        ivAppIcon.setImageResource(R.drawable.instagram);
                        iconColor = ContextCompat.getColor(itemView.getContext(), R.color.sexyInsta);
                        bgTint = Color.parseColor("#33E1306C"); // 20% transparent pink
                    } else if (app.packageName.contains("snap")) {
                        ivAppIcon.setImageResource(R.drawable.snapchat);
                        iconColor = ContextCompat.getColor(itemView.getContext(), R.color.sexySnap);
                        bgTint = Color.parseColor("#33FFFC00"); // 20% transparent yellow
                    } else {
                        ivAppIcon.setImageResource(R.drawable.shield);
                        iconColor = ContextCompat.getColor(itemView.getContext(), R.color.text_secondary);
                        bgTint = Color.parseColor("#1AFFFFFF"); // 10% transparent white
                    }
                    ivAppIcon.setColorFilter(iconColor);
                    ivAppIcon.setBackgroundTintList(ColorStateList.valueOf(bgTint));
                }

                switchRestricted.setOnCheckedChangeListener(null);
                switchRestricted.setChecked(app.isRestricted);
                layoutAppConfig.setVisibility(app.isRestricted ? View.VISIBLE : View.GONE);

                switchRestricted.setOnCheckedChangeListener((btn, isChecked) -> {
                    app.isRestricted = isChecked;
                    blockedAppDao.update(app);
                    syncModPackages(app);
                    layoutAppConfig.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    notifyServiceConfigChanged();
                });

                // Scope setting: Section vs Full
                boolean isSection = "section".equals(app.scope);
                updateScopeUI(isSection);

                // Predefined apps like YouTube/Instagram/Snapchat have section/doom-scrolling view IDs
                boolean hasViewId = app.sectionViewId != null && !app.sectionViewId.trim().isEmpty();
                layoutScope.setVisibility(hasViewId ? View.VISIBLE : View.GONE);
                layoutMod.setVisibility(hasViewId ? View.VISIBLE : View.GONE);

                btnScopeSection.setOnClickListener(v -> {
                    app.scope = "section";
                    blockedAppDao.update(app);
                    syncModPackages(app);
                    updateScopeUI(true);
                    notifyServiceConfigChanged();
                });

                btnScopeFull.setOnClickListener(v -> {
                    app.scope = "full";
                    blockedAppDao.update(app);
                    syncModPackages(app);
                    updateScopeUI(false);
                    notifyServiceConfigChanged();
                });

                // Mod checkbox setting
                cbUseMod.setOnCheckedChangeListener(null);
                cbUseMod.setChecked(app.useMod);
                cbUseMod.setOnCheckedChangeListener((btn, isChecked) -> {
                    app.useMod = isChecked;
                    blockedAppDao.update(app);
                    syncModPackages(app);
                    notifyServiceConfigChanged();
                });
            }

            private void syncModPackages(BlockedAppEntity parentApp) {
                List<BlockedAppEntity> allApps = blockedAppDao.getAllSync();
                for (BlockedAppEntity app : allApps) {
                    if (isModPackage(app.packageName)) {
                        boolean match = false;
                        if (parentApp.packageName.equals("com.google.android.youtube") && app.packageName.contains("youtube")) {
                            match = true;
                        } else if (parentApp.packageName.equals("com.instagram.android") && (app.packageName.contains("insta") || app.packageName.contains("honista"))) {
                            match = true;
                        }
                        
                        if (match) {
                            app.isRestricted = parentApp.isRestricted && parentApp.useMod;
                            app.scope = parentApp.scope;
                            app.useMod = parentApp.useMod;
                            blockedAppDao.update(app);
                        }
                    }
                }
            }

            private void updateScopeUI(boolean isSection) {
                int selectedBg = R.drawable.bg_segment_selected;
                int selectedTxt = ContextCompat.getColor(itemView.getContext(), R.color.white);
                int normalTxt = ContextCompat.getColor(itemView.getContext(), R.color.text_secondary);

                if (isSection) {
                    btnScopeSection.setBackgroundResource(selectedBg);
                    btnScopeSection.setTextColor(selectedTxt);
                    btnScopeFull.setBackground(null);
                    btnScopeFull.setTextColor(normalTxt);
                } else {
                    btnScopeSection.setBackground(null);
                    btnScopeSection.setTextColor(normalTxt);
                    btnScopeFull.setBackgroundResource(selectedBg);
                    btnScopeFull.setTextColor(selectedTxt);
                }
            }
        }
    }

    private static boolean isModPackage(String packageName) {
        if (packageName == null) return false;
        // YouTube mods
        if (packageName.equals("com.rvx.android.youtube") || packageName.equals("com.revance.android.youtube")) {
            return true;
        }
        // Instagram mods
        if (packageName.equals("com.myinsta.android") || 
            packageName.equals("com.instafel.android") || 
            packageName.equals("com.instander.android") || 
            packageName.equals("com.instagold.android") || 
            packageName.equals("com.instapro2.android") || 
            packageName.equals("com.instaflow.android") || 
            packageName.equals("cc.honista.app") || 
            packageName.equals("com.instaprime.android")) {
            return true;
        }
        return false;
    }
}
