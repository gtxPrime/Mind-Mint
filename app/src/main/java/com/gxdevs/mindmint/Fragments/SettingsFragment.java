package com.gxdevs.mindmint.Fragments;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.POWER_SERVICE;
import static com.gxdevs.mindmint.Utils.Utils.isAccessibilityPermissionGranted;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.gxdevs.mindmint.Activities.CustomAppSelectionActivity;
import com.gxdevs.mindmint.Activities.HomeActivity;
import com.gxdevs.mindmint.Activities.SiteBlockerActivity;
import com.gxdevs.mindmint.Adapters.SettingsAdapter;
import com.gxdevs.mindmint.Models.SettingsItem;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Receivers.MindMintDeviceAdminReceiver;
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;
import com.gxdevs.mindmint.Utils.CustomDialogUtils;
import com.gxdevs.mindmint.Utils.AdultDomainListManager;
import com.gxdevs.mindmint.Utils.AlarmUtils;
import com.gxdevs.mindmint.Utils.BackupManager;
import com.gxdevs.mindmint.Utils.BlockedSitesManager;
import com.gxdevs.mindmint.Utils.SettingsLockManager;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Utils.AnimUtils;
import com.gxdevs.mindmint.Utils.WarningUtils;
import com.gxdevs.mindmint.Utils.ChallengeLockManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;

import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;

public class SettingsFragment extends Fragment {

    private static final int ID_REMIND_DOOM = 1;
    private static final int ID_BLOCK_CONTENT = 2;
    private static final int ID_KEEP_ALIVE = 3;
    private static final int ID_CUSTOM_APP = 4;
    private static final int ID_BROWSER_BLOCKER = 5;
    private static final int ID_ADULT_BLOCK = 7;
    private static final int ID_POPUP_DURATION = 8;
    private static final int ID_THEME = 9;
    private static final int ID_SCROLL_COUNTER = 10;
    private static final int ID_SCROLL_TAB = 11;
    private static final int ID_SETTINGS_LOCK = 12;
    private static final int ID_LOCK_TYPE_TAB = 13;
    private static final int ID_ALWAYS_LOCK_IN = 14;
    private static final int ID_ROUTINES = 15;
    private static final int ID_PREVENT_UNINSTALL = 16;
    private static final int ID_LOCK_TYPES = 17;
    private static final int ID_BLOCKER_SLIDER = 20;
    private static final int ID_BLOCK_TRIGGER_TAB = 21;
    private static final int ID_BLOCKER_BYPASS_DURATION = 18;
    private static final int ID_PERM_ACCESSIBILITY = 100;
    private static final int ID_PERM_NOTIFICATION = 101;
    private static final int ID_PERM_ALARM = 102;
    private static final int ID_PERM_BATTERY = 103;
    private static final int ID_BACKUP = 104;
    public static final String PREF_THEME_MODE = "pref_theme_mode";
    private static final String PREF_BROWSER_BLOCK_TUTORIAL_SHOWN = "pref_browser_block_tutorial_shown";

    private SharedPreferences defaultSharedPreferences;
    private RecyclerView recyclerView;
    private SettingsAdapter adapter;
    private List<SettingsItem> settingsItems;

    private ActivityResultLauncher<Intent> batteryOptimizationLauncher;
    private ActivityResultLauncher<Intent> accessibilityLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;
    private ActivityResultLauncher<Intent> deviceAdminLauncher;
    private ActivityResultLauncher<Intent> challengeLauncher;
    private Runnable pendingAuthCallback;
    private Runnable pendingCancelCallback;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;

    private BottomSheetDialog timerPicker;
    private boolean batteryOptimizationIgnored = false;
    private boolean isImportOverride = false;
    /** Guards entrance animation — only plays on first tab visit. */
    private boolean firstResume = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        devicePolicyManager = (DevicePolicyManager) requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(requireContext(), MindMintDeviceAdminReceiver.class);

        recyclerView = view.findViewById(R.id.settingsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        settingsItems = new ArrayList<>();
        adapter = new SettingsAdapter(requireContext(), settingsItems);
        recyclerView.setAdapter(adapter);

        registerForPermission();
        registerAccessibilityLauncher();
        registerNotificationPermissionLauncher();
        registerBackupLaunchers();
        refreshList();

        // Pre-hide views that onResume will animate in — prevents flash on initial render
        View headerContainer = view.findViewById(R.id.headerContainer);
        if (headerContainer != null) { headerContainer.setAlpha(0f); headerContainer.setTranslationY(80f); }
        if (recyclerView != null) recyclerView.setAlpha(0f);

        return view;
    }

    private void registerBackupLaunchers() {
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        android.net.Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                BackupManager.exportData(requireContext(), uri);
                                Toast.makeText(requireContext(), "Backup exported successfully", Toast.LENGTH_SHORT)
                                        .show();
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        }
                    }
                });

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        android.net.Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                BackupManager.importData(requireContext(), uri, isImportOverride);
                                Toast.makeText(requireContext(), "Data imported successfully", Toast.LENGTH_SHORT)
                                        .show();
                                refreshList();
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        }
                    }
                });
    }

    /** Returns true only when this fragment is the page currently shown by the host ViewPager2. */
    private boolean isCurrentPage(int expectedPageIndex) {
        if (getActivity() instanceof com.gxdevs.mindmint.Activities.HomeActivity) {
            androidx.viewpager2.widget.ViewPager2 vp =
                    getActivity().findViewById(com.gxdevs.mindmint.R.id.nav_host_container);
            if (vp != null) return vp.getCurrentItem() == expectedPageIndex;
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Run entrance animation only the first time this tab is actually VISIBLE.
        // ViewPager2 with offscreenPageLimit calls onResume for ALL pre-loaded fragments
        // when the Activity resumes — guard against that.
        if (firstResume && isCurrentPage(com.gxdevs.mindmint.Adapters.HomePagerAdapter.PAGE_SETTINGS)) {
            firstResume = false;
            View headerContainer = getView() != null ? getView().findViewById(R.id.headerContainer) : null;
            if (headerContainer != null) headerContainer.post(() -> AnimUtils.enterSlideUp(headerContainer, 0));
            if (recyclerView != null) recyclerView.post(() -> AnimUtils.fadeIn(recyclerView, 60, 280));
        }
        refreshList();
    }

    private void refreshList() {
        if (!defaultSharedPreferences.contains(ChallengeLockManager.PREF_BLOCKER_INTENSITY)) {
            boolean isRemindEnabled = defaultSharedPreferences.getBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
            boolean isBlockEnabled = defaultSharedPreferences.getBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
            String currentChallenge = defaultSharedPreferences.getString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
            
            int initialIntensity = 0;
            if ("oneday".equals(currentChallenge)) {
                initialIntensity = 4;
            } else if (isBlockEnabled) {
                initialIntensity = 3;
            } else if (isRemindEnabled) {
                initialIntensity = 2;
            } else if (!"none".equals(currentChallenge)) {
                initialIntensity = 1;
            }
            
            defaultSharedPreferences.edit()
                .putInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, initialIntensity)
                .putString(ChallengeLockManager.PREF_BLOCKER_TRIGGER_TYPE, isRemindEnabled ? "scroll" : "time")
                .apply();
        }

        settingsItems.clear();
        buildSettingsList();
        adapter.setCurrentTheme(defaultSharedPreferences.getString(PREF_THEME_MODE, "Dark Theme"));
        adapter.setOnSeekbarChangeListener((itemId, progress) -> {
            if (itemId == ID_POPUP_DURATION) {
                int minSeconds = 3;
                int seconds = minSeconds + progress;
                defaultSharedPreferences.edit()
                        .putInt(AppUsageAccessibilityService.PREF_BLOCKING_POPUP_DURATION_SEC, seconds).apply();
            } else if (itemId == ID_BLOCKER_BYPASS_DURATION) {
                int minutes = progress + 5;
                defaultSharedPreferences.edit()
                        .putInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, minutes).apply();
            }
        });

        adapter.setOnBlockerIntensityChangeListener((itemId, intensity) -> {
            applyBlockerIntensity(intensity);
        });

        adapter.setOnBlockTriggerChangeListener((itemId, newTrigger) -> {
            defaultSharedPreferences.edit().putString(ChallengeLockManager.PREF_BLOCKER_TRIGGER_TYPE, newTrigger).apply();
            int intensity = defaultSharedPreferences.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0);
            saveBlockerIntensity(intensity);
            refreshList();
        });

        adapter.setOnThemeChangeListener(this::applyTheme);
        adapter.setOnLockTabActionListener(new SettingsAdapter.OnLockTabActionListener() {
            @Override
            public void onRequestLockTypeChange(String newLockType, Runnable onSuccess) {
                authenticateToChangeSetting("Change lock type", () -> {
                    SettingsLockManager lockMgr = new SettingsLockManager(requireContext());
                    if (SettingsLockManager.LOCK_TYPE_DEVICE.equals(newLockType)) {
                        if (!lockMgr.isDeviceLockAvailable()) {
                            Toast.makeText(requireContext(), "Device lock not found. Please set a custom PIN.", Toast.LENGTH_LONG).show();
                            refreshList();
                            return;
                        }
                        lockMgr.clearCustomPin();
                    }
                    onSuccess.run();
                    if (SettingsLockManager.LOCK_TYPE_CUSTOM.equals(newLockType) && !lockMgr.hasCustomPin()) {
                        lockMgr.showSetCustomPinDialog(requireContext(), false, null);
                    }
                });
            }

            @Override
            public void onEditCustomPin() {
                SettingsLockManager lockMgr = new SettingsLockManager(requireContext());
                if (lockMgr.hasCustomPin()) {
                    lockMgr.showVerifyPinDialog(requireContext(), "Enter current PIN to continue", verified -> {
                        if (verified) lockMgr.showSetCustomPinDialog(requireContext(), true, null);
                    });
                } else {
                    lockMgr.showSetCustomPinDialog(requireContext(), false, null);
                }
            }
        });
        adapter.setOnBackupActionListener(new SettingsAdapter.OnBackupActionListener() {
            @Override
            public void onExport() {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "mindmint_backup_" + System.currentTimeMillis() + ".brain");
                exportLauncher.launch(intent);
            }

            @Override
            public void onImport(boolean override) {
                isImportOverride = override;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                importLauncher.launch(intent);
            }
        });

        adapter.notifyDataSetChanged();
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getContext() != null && getContext().getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return 0;
    }

    private void buildSettingsList() {
        int eyeBg = getThemeColor(R.attr.eye_bg);
        int mobileBg = getThemeColor(R.attr.mobile_bg);
        int browserBg = getThemeColor(R.attr.browser_bg);
        int blockBg = getThemeColor(R.attr.block_bg);
        int popupBg = getThemeColor(R.attr.popup_bg);
        int textSecondary = getThemeColor(R.attr.text_secondary);

        int redIcon = Color.parseColor("#F77381");
        int blueIcon = Color.parseColor("#61A2F2");
        int greenIcon = Color.parseColor("#3DD7A5");
        int purpleIcon = Color.parseColor("#BF83FB");
        int grayIcon = Color.parseColor("#ABABAB");
        int tealIcon = Color.parseColor("#009688");

        settingsItems.add(new SettingsItem(SettingsItem.TYPE_HEADER, "FOCUS CONTROLS"));

        boolean isKeepAlive = defaultSharedPreferences.getBoolean("keepServiceAlive", false);
        settingsItems.add(new SettingsItem(ID_KEEP_ALIVE, SettingsItem.TYPE_SWITCH, "Keep service alive",
                "Prevent OS from killing app", R.drawable.zap, grayIcon).setSwitch(true, isKeepAlive, (buttonView, isChecked) -> {
            lockedSwitchAction("Change Keep service alive", buttonView, !isChecked, isChecked, () -> handleKeepAliveToggle(buttonView, isChecked));
        }));

        boolean isScrollCounterOn = defaultSharedPreferences.getBoolean("pref_scroll_counter_enabled", false);
        settingsItems.add(new SettingsItem(ID_SCROLL_COUNTER, SettingsItem.TYPE_SWITCH, "Show scroll counter",
                isScrollCounterOn ? "Pill shown on blocked app screens" : "Show daily scroll count on blocking screen",
                R.drawable.scroll_text, tealIcon)
                .setSwitch(true, isScrollCounterOn, (btn, isChecked) -> {
                    lockedSwitchAction("Change Scroll counter", btn, !isChecked, isChecked, () -> {
                        if (isChecked && !isAccessibilityPermissionGranted(requireContext())) {
                            btn.setChecked(false);
                            defaultSharedPreferences.edit().putBoolean("pref_scroll_counter_enabled", false).apply();
                            shakeCard(ID_PERM_ACCESSIBILITY);
                            return;
                        }
                        defaultSharedPreferences.edit().putBoolean("pref_scroll_counter_enabled", isChecked).apply();
                    });
                }));

        if (isScrollCounterOn) {
            boolean perApp = defaultSharedPreferences.getBoolean("pref_scroll_counter_per_app", false);
            settingsItems.add(new SettingsItem(ID_SCROLL_TAB, SettingsItem.TYPE_SCROLL_TAB,
                    "", "", 0, 0)
                    .setScrollTabPerApp(perApp));
        }

        settingsItems.add(new SettingsItem(SettingsItem.TYPE_HEADER, "BLOCKING RULES"));

        settingsItems.add(new SettingsItem(999, SettingsItem.TYPE_SWITCH, "Blocker & Lock Center",
                "Configure app blockers, challenges, limits, and settings locks",
                R.drawable.shield, purpleIcon)
                .setIconValues(R.drawable.shape_circle, popupBg)
                .setSwitch(false, false, null)
                .setArrow(true)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), com.gxdevs.mindmint.Activities.BlockerControlActivity.class);
                    startActivity(intent);
                }));

        settingsItems.add(new SettingsItem(SettingsItem.TYPE_HEADER, "UPCOMING FEATURES"));

        int orangeIcon = Color.parseColor("#FF9800");
        settingsItems.add(new SettingsItem(ID_ROUTINES, SettingsItem.TYPE_SWITCH,
                "Routines", "Coming Soon \uD83D\uDE80",
                R.drawable.alarm, orangeIcon)
                .setIconValues(R.drawable.shape_circle, Color.parseColor("#33FF9800"))
                .setOnClickListener(v ->
                        Toast.makeText(requireContext(), "Coming Soon \uD83D\uDE80", Toast.LENGTH_SHORT).show()));

        // App Blocker Challenge shifted to BLOCKING RULES

        settingsItems.add(new SettingsItem(SettingsItem.TYPE_HEADER, "APPEARANCE"));
        settingsItems.add(new SettingsItem(ID_THEME, SettingsItem.TYPE_THEME, "Theme", "", 0, 0));

        settingsItems.add(new SettingsItem(SettingsItem.TYPE_HEADER, "DATA MANAGEMENT"));
        settingsItems.add(new SettingsItem(ID_BACKUP, SettingsItem.TYPE_BACKUP, "Data Backup",
                "Import or export your data", R.drawable.backup, tealIcon).setIconValues(R.drawable.shape_circle, mobileBg));

        addPermissionCards();
    }

    private void showBrowserBlockingTutorial() {
        if (defaultSharedPreferences.getBoolean(PREF_BROWSER_BLOCK_TUTORIAL_SHOWN, false)) {
            return;
        }

        int pos = -1;
        for (int i = 0; i < settingsItems.size(); i++) {
            if (settingsItems.get(i).getId() == ID_BROWSER_BLOCKER) {
                pos = i;
                break;
            }
        }

        if (pos != -1) {
            final int finalPos = pos;
            recyclerView.post(() -> {
                RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(finalPos);
                if (vh != null) {
                    Balloon balloon = new Balloon.Builder(requireContext())
                            .setArrowSize(10)
                            .setArrowOrientation(ArrowOrientation.BOTTOM)
                            .setArrowPosition(0.5f)
                            .setWidthRatio(0.7f)
                            .setHeight(BalloonSizeSpec.WRAP)
                            .setTextSize(14f)
                            .setCornerRadius(10f)
                            .setAlpha(0.9f)
                            .setPadding(8)
                            .setText("Tap to add more sites")
                            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            .setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brainColor))
                            .setBalloonAnimation(BalloonAnimation.ELASTIC)
                            .setDismissWhenClicked(true)
                            .setLifecycleOwner(getViewLifecycleOwner())
                            .setOnBalloonDismissListener(() -> defaultSharedPreferences.edit()
                                    .putBoolean(PREF_BROWSER_BLOCK_TUTORIAL_SHOWN, true).apply())
                            .build();
                    balloon.showAlignTop(vh.itemView);
                }
            });
        }
    }

    private void addPermissionCards() {
        PowerManager pm = (PowerManager) requireContext().getSystemService(POWER_SERVICE);
        batteryOptimizationIgnored = pm != null && pm.isIgnoringBatteryOptimizations(requireContext().getPackageName());

        int statusErrorBg = getThemeColor(R.attr.status_error_bg);
        int statusErrorIcon = getThemeColor(R.attr.status_error_icon);
        int statusErrorText = getThemeColor(R.attr.status_error_text);
        int statusInfoIcon = getThemeColor(R.attr.status_info_icon);
        int statusWarningBg = getThemeColor(R.attr.status_warning_bg);
        int statusWarningIcon = getThemeColor(R.attr.status_warning_icon);
        int textPrimary = getThemeColor(R.attr.text_primary);
        int textTertiary = getThemeColor(R.attr.text_tertiary);

        if (!isAccessibilityPermissionGranted(requireContext())) {
            settingsItems.add(new SettingsItem(ID_PERM_ACCESSIBILITY, SettingsItem.TYPE_PERMISSION,
                    "Permission Required",
                    "Accessibility permission is required for scroll count, blocking and other stats.",
                    android.R.drawable.ic_dialog_alert, statusErrorIcon)
                    .setPermissionColors(statusErrorBg, statusErrorIcon, statusErrorText, statusErrorIcon,
                            statusErrorIcon)
                    .setOnClickListener(v -> showAccessibilityBottomSheet()));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted()) {
                settingsItems.add(new SettingsItem(ID_PERM_NOTIFICATION, SettingsItem.TYPE_PERMISSION,
                        "Permission Required",
                        "Provide notification permission to show notifications on time",
                        R.drawable.bell, statusInfoIcon)
                        .setPermissionColors(0, statusInfoIcon, textTertiary, textTertiary, textPrimary)
                        .setOnClickListener(v -> showNotificationPermissionBottomSheet()));
            }
        }

        // 3. Alarm (Warning) - title uses text_primary, icon uses warning color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!isAlarmPermissionGranted()) {
                settingsItems.add(new SettingsItem(ID_PERM_ALARM, SettingsItem.TYPE_PERMISSION,
                        "Alarm Permission",
                        getString(R.string.accurate),
                        R.drawable.alarm, statusWarningIcon)
                        .setPermissionColors(0, statusWarningIcon, textTertiary, textTertiary, textPrimary)
                        .setOnClickListener(v -> askForExactAlarmPermission()));
            }
        }

        // 4. Battery (Warning/Amber) - card root has NO bg tint, icon bg has warning
        // tint
        if (!batteryOptimizationIgnored) {
            settingsItems.add(new SettingsItem(ID_PERM_BATTERY, SettingsItem.TYPE_PERMISSION,
                    "Battery Optimization",
                    "Turn off to ensure app runs in background without interruption.",
                    R.drawable.zap, statusWarningIcon)
                    .setIconValues(0, statusWarningBg) // Icon background gets the warning tint
                    .setPermissionColors(0, statusWarningIcon, textTertiary, textTertiary, textPrimary)
                    .setOnClickListener(v -> showBatteryBottomSheet()));
        }
    }

    private void handleKeepAliveToggle(android.widget.CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            // 1. Notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
                shakeCard(ID_PERM_NOTIFICATION);
                buttonView.setChecked(false);
                defaultSharedPreferences.edit().putBoolean("keepServiceAlive", false).apply();
                return;
            }

            // 2. Alarms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isAlarmPermissionGranted()) {
                shakeCard(ID_PERM_ALARM);
                buttonView.setChecked(false);
                defaultSharedPreferences.edit().putBoolean("keepServiceAlive", false).apply();
                return;
            }

            // 3. Battery
            if (!batteryOptimizationIgnored) {
                shakeCard(ID_PERM_BATTERY);
                buttonView.setChecked(false);
                defaultSharedPreferences.edit().putBoolean("keepServiceAlive", false).apply();
                return;
            }

            // 4. Accessibility (Critical)
            if (!isAccessibilityPermissionGranted(requireContext())) {
                shakeCard(ID_PERM_ACCESSIBILITY);
                buttonView.setChecked(false);
                defaultSharedPreferences.edit().putBoolean("keepServiceAlive", false).apply();
                return;
            }

            // All granted
            defaultSharedPreferences.edit().putBoolean("keepServiceAlive", true).apply();
            AlarmUtils.scheduleAlarm(requireContext());
            // Force immediate update
            requireContext().sendBroadcast(new Intent(AppUsageAccessibilityService.ACTION_UPDATE_KEEP_ALIVE));
        } else {
            defaultSharedPreferences.edit().putBoolean("keepServiceAlive", false).apply();
            AlarmUtils.cancelAlarm(requireContext());
            WarningUtils.remove(requireContext());
        }
    }

    private void shakeCard(int itemId) {
        if (!isAdded())
            return;

        // Message determining logic
        String message = "Permission required";
        if (itemId == ID_PERM_ACCESSIBILITY)
            message = "Accessibility permission needed";
        else if (itemId == ID_PERM_NOTIFICATION)
            message = "Notification permission needed";
        else if (itemId == ID_PERM_ALARM)
            message = "Alarm permission needed";
        else if (itemId == ID_PERM_BATTERY)
            message = "Battery optimization permission needed";

        String finalMessage = message;

        // Try to find current position
        int initialPos = -1;
        for (int i = 0; i < settingsItems.size(); i++) {
            if (settingsItems.get(i).getId() == itemId) {
                initialPos = i;
                break;
            }
        }

        if (initialPos != -1) {
            recyclerView.smoothScrollToPosition(initialPos);
        }

        // Delay to allow scroll and potential refresh to settle
        recyclerView.postDelayed(() -> {
            if (!isAdded())
                return;

            int currentPos = -1;
            for (int i = 0; i < settingsItems.size(); i++) {
                if (settingsItems.get(i).getId() == itemId) {
                    currentPos = i;
                    break;
                }
            }

            if (currentPos != -1) {
                RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(currentPos);
                if (vh != null) {
                    shakeView(vh.itemView);
                }
            }
            Toast.makeText(requireContext(), finalMessage, Toast.LENGTH_SHORT).show();
        }, 500);
    }

    private void shakeView(View view) {
        if (view == null)
            return;

        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationX(0f);

        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f);
        scaleUpX.setDuration(200);
        scaleUpY.setDuration(200);

        ObjectAnimator shake = ObjectAnimator.ofFloat(
                view,
                "translationX",
                0, 20, -20, 15, -15, 10, -10, 5, -5, 0);
        shake.setDuration(650);

        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1.05f,
                1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1.05f,
                1f);
        scaleDownX.setDuration(200);
        scaleDownY.setDuration(200);

        AnimatorSet set = new AnimatorSet();
        set.play(scaleUpX).with(scaleUpY);
        set.play(shake).after(scaleUpX);
        set.play(scaleDownX).with(scaleDownY).after(shake);
        set.start();

        // Haptic
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.os.Vibrator v = (android.os.Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (v != null)
                v.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.EFFECT_HEAVY_CLICK));
        }
    }


    private CharSequence getRemindDoomFormattedSubtitle() {
        int minutes = defaultSharedPreferences.getInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES, AppUsageAccessibilityService.DEFAULT_REMIND_DOOM_SCROLLING_MINUTES);
        String reminderText = "Remind me to stop scroll at every " + minutes + " minutes ";
        SpannableString spannable = new SpannableString(reminderText);
        int start = reminderText.indexOf(String.valueOf(minutes));
        int end = start + String.valueOf(minutes).length() + " minutes".length();
        int cyan = ContextCompat.getColor(requireContext(), R.color.cyan);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(cyan), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit);
        if (icon != null) {
            DrawableCompat.setTint(icon, cyan);
            int size = (int) (13 * getResources().getDisplayMetrics().scaledDensity);
            icon.setBounds(0, 0, size, size);
            ImageSpan imageSpan = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
            SpannableStringBuilder builder = new SpannableStringBuilder(spannable);
            builder.append(" ");
            builder.setSpan(imageSpan, builder.length() - 1, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }
        return spannable;
    }

    private CharSequence getBlockTimeFormattedSubtitle() {
        float hours = defaultSharedPreferences.getFloat(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_HOURS, AppUsageAccessibilityService.DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS);
        int wholeHours = (int) hours;
        int minutes = Math.round((hours - wholeHours) * 60);
        String timeText = formatTimeDisplay(wholeHours, minutes);
        String displayText = "Block content after " + timeText + " ";

        SpannableString spannable = new SpannableString(displayText);
        int start = displayText.indexOf(timeText);
        int end = start + timeText.length();
        int cyan = ContextCompat.getColor(requireContext(), R.color.cyan);

        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(cyan), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit);
        if (icon != null) {
            DrawableCompat.setTint(icon, cyan);
            int size = (int) (13 * getResources().getDisplayMetrics().scaledDensity);
            icon.setBounds(0, 0, size, size);
            ImageSpan imageSpan = new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM);
            SpannableStringBuilder builder = new SpannableStringBuilder(spannable);
            builder.append(" ");
            builder.setSpan(imageSpan, builder.length() - 1, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }
        return spannable;
    }

    private String formatTimeDisplay(int hours, int minutes) {
        if (hours == 0)
            return minutes + " minutes";
        else if (minutes == 0)
            return hours + " hours";
        else
            return hours + "h " + minutes + "m";
    }

    private void registerForPermission() {
        batteryOptimizationLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    refreshList();
                });

        challengeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (pendingAuthCallback != null) {
                            pendingAuthCallback.run();
                        }
                    } else {
                        if (pendingCancelCallback != null) {
                            pendingCancelCallback.run();
                        }
                    }
                    pendingAuthCallback = null;
                    pendingCancelCallback = null;
                    refreshList();
                });

        deviceAdminLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    boolean active = devicePolicyManager != null
                            && devicePolicyManager.isAdminActive(deviceAdminComponent);
                    android.util.Log.d("PA_LAUNCHER", "deviceAdminLauncher result — resultCode=" + result.getResultCode() + "  isAdminActive=" + active);
                    refreshList();
                    android.util.Log.d("PA_LAUNCHER", "refreshList() done");
                    if (active) {
                        android.util.Log.d("PA_LAUNCHER", "showing enabled toast");
                        Toast.makeText(requireContext(),
                                "Prevent Uninstall enabled — app is now protected.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void registerAccessibilityLauncher() {
        accessibilityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    refreshList();
                });
    }

    private void registerNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Toast.makeText(requireContext(), "Granted", Toast.LENGTH_SHORT).show();
                    }
                    refreshList();
                });
    }

    private boolean isNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT < 33)
            return true;
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isAlarmPermissionGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true;
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private void askForExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isAlarmPermissionGranted()) {
                Toast.makeText(requireContext(), "Permission already granted", Toast.LENGTH_SHORT).show();
                refreshList();
                return;
            }
            Utils.showPermissionSheet(requireContext(), Utils.PermissionType.ALARM, null,
                    () -> Toast.makeText(requireContext(), "Permission required", Toast.LENGTH_SHORT).show());
        }
    }

    private void showBatteryBottomSheet() {
        Utils.showPermissionSheet(requireContext(), Utils.PermissionType.BATTERY,
                new Utils.PermissionLauncher() {
                    @Override
                    public void launchAccessibility(Intent intent) {
                    }

                    @Override
                    public void launchBattery(Intent intent) {
                        batteryOptimizationLauncher.launch(intent);
                    }

                    @Override
                    public void launchNotification(String permission) {
                    }
                },
                () -> Toast.makeText(requireContext(), "Not ignored", Toast.LENGTH_SHORT).show());
    }

    private void showAccessibilityBottomSheet() {
        Utils.showPermissionSheet(requireContext(), Utils.PermissionType.ACCESSIBILITY,
                new Utils.PermissionLauncher() {
                    @Override
                    public void launchAccessibility(Intent intent) {
                        accessibilityLauncher.launch(intent);
                    }

                    @Override
                    public void launchBattery(Intent intent) {
                    }

                    @Override
                    public void launchNotification(String permission) {
                    }
                }, null);
    }

    private void showNotificationPermissionBottomSheet() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void showTimePickerBottomSheet(boolean isRemindDoomScrolling) {
        if (!isAccessibilityPermissionGranted(requireContext())) {
            shakeCard(ID_PERM_ACCESSIBILITY);
            return;
        }
        timerPicker = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_time, null);

        NumberPicker hourPicker = bottomSheetView.findViewById(R.id.hours_selector_bottom_sheet);
        NumberPicker minutePicker = bottomSheetView.findViewById(R.id.minutes_selector_bottom_sheet);
        Button setLimitBtn = bottomSheetView.findViewById(R.id.setLimitBtnBottomSheet);
        TextView hoursLabel = bottomSheetView.findViewById(R.id.hours_textView_bottom_sheet);

        if (isRemindDoomScrolling) {
            hourPicker.setVisibility(View.GONE);
            hoursLabel.setVisibility(View.GONE);
            minutePicker.setMinValue(1);
            minutePicker.setMaxValue(59);
            int current = defaultSharedPreferences.getInt(
                    AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES,
                    AppUsageAccessibilityService.DEFAULT_REMIND_DOOM_SCROLLING_MINUTES);
            minutePicker.setValue(current);
            setLimitBtn.setText(R.string.set_reminder_time);
        } else {
            hourPicker.setVisibility(View.VISIBLE);
            hoursLabel.setVisibility(View.VISIBLE);
            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            float current = defaultSharedPreferences.getFloat(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_HOURS,
                    AppUsageAccessibilityService.DEFAULT_BLOCK_AFTER_WASTED_TIME_HOURS);
            int h = (int) current;
            int m = Math.round((current - h) * 60);
            hourPicker.setValue(h);
            minutePicker.setValue(m);
            setLimitBtn.setText(R.string.set_block_time);
        }

        setLimitBtn.setOnClickListener(v -> {
            if (isRemindDoomScrolling) {
                defaultSharedPreferences.edit().putInt(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_MINUTES,
                        minutePicker.getValue()).apply();
                Toast.makeText(requireContext(), "Reminder set", Toast.LENGTH_SHORT).show();
            } else {
                float val = hourPicker.getValue() + (minutePicker.getValue() / 60.0f);
                defaultSharedPreferences.edit().putFloat(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_HOURS, val).apply();
                Toast.makeText(requireContext(), "Block time set", Toast.LENGTH_SHORT).show();
            }
            timerPicker.dismiss();
            refreshList();
        });

        bottomSheetView.findViewById(R.id.crossBtn).setOnClickListener(v -> timerPicker.dismiss());
        timerPicker.setContentView(bottomSheetView);
        timerPicker.show();
    }

    private void showAdultListDownloadDialogAndEnsure() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        // Inflate custom layout
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_adult_list_progress, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog progressDialog = builder.create();
        // Set background transparent for the CardView radius to work
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
        progressDialog.show();

        AdultDomainListManager.downloadAndBuildList(requireContext(),
                new AdultDomainListManager.OnDownloadCompleteListener() {
                    @Override
                    public void onSuccess(long mergedFileBytes, String sha256Hex, boolean deduped) {
                        if (getActivity() == null)
                            return;
                        getActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(requireContext(), "List updated successfully", Toast.LENGTH_SHORT).show();
                            defaultSharedPreferences.edit().putBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, true).apply();
                            refreshList();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        if (getActivity() == null)
                            return;
                        getActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(requireContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT)
                                    .show();
                            defaultSharedPreferences.edit().putBoolean(AppUsageAccessibilityService.PREF_BLOCK_ADULT_SITES_ENABLED, false).apply();
                            refreshList();
                        });
                    }
                }, false);
    }

    private void applyTheme(String theme) {
        String current = defaultSharedPreferences.getString(PREF_THEME_MODE, "Dark Theme");
        if (!theme.equals(current)) {
            defaultSharedPreferences.edit().putString(PREF_THEME_MODE, theme).apply();
            String existingStartFragment = defaultSharedPreferences.getString(HomeActivity.PREF_START_FRAGMENT, null);
            if (!HomeActivity.START_FRAGMENT_SETTINGS.equals(existingStartFragment)) {
                defaultSharedPreferences.edit()
                        .putString(HomeActivity.PREF_START_FRAGMENT, HomeActivity.START_FRAGMENT_SETTINGS).apply();
            }
            Utils.applyAppThemeFromPrefs(requireContext());
            View root = getView().findViewById(R.id.headerContainer);
            Runnable doRecreate = () -> {
                if (isAdded())
                    requireActivity().recreate();
            };
            if (root != null)
                root.animate().alpha(0f).setDuration(180).withEndAction(doRecreate).start();
            else
                doRecreate.run();
        }
    }

    // ─── Settings Lock PIN helpers ────────────────────────────────────────────

    /**
     * Authenticate via device lock, custom PIN, or challenge locks (depending on current setting)
     * before allowing a sensitive change. Calls onAuthenticated when verified.
     */
    private void authenticateToChangeSetting(String reason, Runnable onAuthenticated, Runnable onCancelled) {
        SettingsLockManager lm = new SettingsLockManager(requireContext());
        if (!lm.isLockEnabled()) {
            onAuthenticated.run();
            return;
        }

        String settingsLockType = defaultSharedPreferences.getString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "device");

        if ("device".equals(settingsLockType) || "custom".equals(settingsLockType)) {
            lm.authenticate((AppCompatActivity) requireActivity(), reason, new SettingsLockManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    onAuthenticated.run();
                }

                @Override
                public void onFailure(String reason2) {
                    if (onCancelled != null) onCancelled.run();
                    if (!"Cancelled".equals(reason2)) {
                        Toast.makeText(requireContext(), reason2, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            // It's a challenge lock type!
            if ("oneday".equals(settingsLockType)) {
                ChallengeLockManager clm = new ChallengeLockManager(requireContext());
                if (clm.isSettingsOneDayLockActive()) {
                    long remainingMs = clm.getSettingsOneDayLockRemainingMs();
                    long hours = remainingMs / (60 * 60 * 1000L);
                    long minutes = (remainingMs / (60 * 1000L)) % 60;
                    long seconds = (remainingMs / 1000L) % 60;
                    Toast.makeText(requireContext(),
                            String.format(Locale.US, "Settings are locked under 1-Day lockout. Remaining: %02dh %02dm %02ds", hours, minutes, seconds),
                            Toast.LENGTH_LONG).show();
                    if (onCancelled != null) onCancelled.run();
                    return;
                } else {
                    onAuthenticated.run();
                    return;
                }
            }

            Intent challengeIntent = new Intent(requireContext(), com.gxdevs.mindmint.Activities.LockChallengeActivity.class);
            challengeIntent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_LOCK_TYPE, settingsLockType);
            challengeIntent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_IS_SETTINGS_LOCK, true);
            this.pendingAuthCallback = onAuthenticated;
            this.pendingCancelCallback = onCancelled;
            challengeLauncher.launch(challengeIntent);
        }
    }

    private void authenticateToChangeSetting(String reason, Runnable onAuthenticated) {
        authenticateToChangeSetting(reason, onAuthenticated, null);
    }

    private void lockedSwitchAction(String reason, android.widget.CompoundButton buttonView, boolean originalState, boolean isChecked, Runnable onVerifiedAndChanged) {
        SettingsLockManager lm = new SettingsLockManager(requireContext());

        // Lock is only required when TURNING OFF (disabling a feature)
        if (!lm.isLockEnabled() || isChecked) {
            onVerifiedAndChanged.run();
            refreshList();
            return;
        }

        // Revert switch visually first (since auth is async)
        buttonView.setOnCheckedChangeListener(null);
        buttonView.setChecked(originalState);

        authenticateToChangeSetting(reason, () -> {
            buttonView.setChecked(isChecked);
            onVerifiedAndChanged.run();
            refreshList(); // Restore listeners by refreshing list
        }, this::refreshList); // If cancelled or failed, refresh list to rebind the switch!

        // Temporarily assign a no-op listener until refreshList triggers
        buttonView.setOnCheckedChangeListener((v, c) -> {
        });
    }

    private String getLockTypeLabel(String type) {
        switch (type) {
            case "device": return "Device Lock";
            case "custom": return "Custom PIN";
            case "math": return "Math Equation";
            case "scream": return "Scream (Voice)";
            case "breath": return "Hold Breath (10s)";
            case "text": return "Type Quote";
            case "shake": return "Shake to Unlock";
            case "oneday": return "1-Day Lock";
            case "window10":
                int mins = defaultSharedPreferences.getInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, 10);
                return mins + "-Min Bypass Window";
            default: return "Device Lock";
        }
    }

    private String getBlockerChallengeLabel(String type) {
        switch (type) {
            case "none": return "Normal Blocker";
            case "math": return "Math Equation";
            case "scream": return "Scream (Voice)";
            case "breath": return "Hold Breath (10s)";
            case "text": return "Type Quote";
            case "shake": return "Shake to Unlock";
            case "oneday": return "1-Day Lock";
            case "window10":
                int mins = defaultSharedPreferences.getInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, 10);
                return mins + "-Min Bypass Window";
            default: return "Normal Blocker";
        }
    }

    private void showSettingsLockTypePicker() {
        Intent intent = new Intent(requireContext(), com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.class);
        intent.putExtra(com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.EXTRA_SELECTION_MODE, com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.MODE_SETTINGS);
        String current = defaultSharedPreferences.getString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, "device");
        intent.putExtra(com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.EXTRA_CURRENT_VALUE, current);
        challengeLauncher.launch(intent);
    }

    private void showBlockerChallengePicker() {
        Intent intent = new Intent(requireContext(), com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.class);
        intent.putExtra(com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.EXTRA_SELECTION_MODE, com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.MODE_BLOCKER);
        String current = defaultSharedPreferences.getString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
        intent.putExtra(com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.EXTRA_CURRENT_VALUE, current);

        int intensity = defaultSharedPreferences.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0);
        if (intensity == 1) { // Friction
            intent.putExtra(com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.EXTRA_ALLOWED_TYPES,
                    new String[]{"math", "scream", "breath", "text", "shake", "window10"});
        } else if (intensity == 3) { // Temp Lock
            intent.putExtra(com.gxdevs.mindmint.Activities.LockTypeSelectionActivity.EXTRA_ALLOWED_TYPES,
                    new String[]{"math", "scream", "breath", "text", "shake", "window10", "oneday"});
        }

        challengeLauncher.launch(intent);
    }

    private void applyBlockerIntensity(int stop) {
        int oldIntensity = defaultSharedPreferences.getInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, 0);
        if (stop == oldIntensity) return;

        SettingsLockManager lm = new SettingsLockManager(requireContext());
        if (lm.isLockEnabled()) {
            authenticateToChangeSetting("Change Blocker Intensity", () -> {
                confirmAndApplyBlockerIntensity(stop);
            }, () -> {
                refreshList();
            });
        } else {
            confirmAndApplyBlockerIntensity(stop);
        }
    }

    private void confirmAndApplyBlockerIntensity(int stop) {
        if (stop == 4) {
            showOneDayLockWarning(() -> {
                saveBlockerIntensity(4);
                refreshList();
            }, () -> {
                refreshList();
            });
        } else {
            saveBlockerIntensity(stop);
            refreshList();
        }
    }

    private void saveBlockerIntensity(int stop) {
        SharedPreferences.Editor editor = defaultSharedPreferences.edit();
        editor.putInt(ChallengeLockManager.PREF_BLOCKER_INTENSITY, stop);

        switch (stop) {
            case 0: // None
                editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
                editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
                editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
                break;
            case 1: // Friction
                editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
                editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
                String ch = defaultSharedPreferences.getString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
                if ("none".equals(ch) || "oneday".equals(ch)) {
                    editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math");
                }
                break;
            case 2: // Reminder
                editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
                editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, true);
                editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
                break;
            case 3: // Temp Lock
                {
                    boolean scrollOn = defaultSharedPreferences.getBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
                    boolean timeOn = defaultSharedPreferences.getBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
                    if (!scrollOn && !timeOn) {
                        editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, true);
                    }
                    String ch3 = defaultSharedPreferences.getString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "none");
                    if ("none".equals(ch3) || "oneday".equals(ch3)) {
                        editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "math");
                    }
                }
                break;
            case 4: // Permanent
                editor.putBoolean(AppUsageAccessibilityService.PREF_BLOCK_AFTER_WASTED_TIME_ENABLED, false);
                editor.putBoolean(AppUsageAccessibilityService.PREF_REMIND_DOOM_SCROLLING_ENABLED, false);
                editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, "oneday");
                break;
        }
        editor.apply();
    }

    private void showOneDayLockWarning(Runnable onConfirmed, Runnable onCancelled) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_confirm, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.dialogMessage);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirm);

        tvTitle.setText("⚠️ Strict 1-Day Lockout");
        tvMessage.setText("WARNING: Enabling the Permanent (1-Day) Lock will strictly lock you out of your blocked apps for 24 hours. "
                + "This is system-enforced and CANNOT be undone, paused, or bypassed by changing the device clock or resetting PINs.\n\n"
                + "Do you want to proceed?");

        btnConfirm.setEnabled(false);
        btnConfirm.setText("Understand (5s)");
        btnCancel.setText("Cancel");

        dialog.show();

        final int[] secondsLeft = {5};
        android.os.Handler countdownHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (secondsLeft[0] > 0) {
                    btnConfirm.setText("Understand (" + secondsLeft[0] + "s)");
                    secondsLeft[0]--;
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Understand");
                }
            }
        };
        countdownHandler.post(countdownRunnable);

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirmed.run();
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            countdownHandler.removeCallbacksAndMessages(null);
            onCancelled.run();
        });
    }

    private void handleEditCustomPin() {
        SettingsLockManager lockMgr = new SettingsLockManager(requireContext());
        if (lockMgr.hasCustomPin()) {
            CustomDialogUtils.showCustomDialog(
                    requireContext(),
                    "Change Settings PIN",
                    "Choose how you want to proceed:",
                    "Change PIN",
                    "Cancel",
                    "Reset / Forgot PIN",
                    () -> {
                        lockMgr.showVerifyPinDialog(requireContext(), "Enter current PIN to continue", verified -> {
                            if (verified) {
                                lockMgr.showSetCustomPinDialog(requireContext(), true, this::refreshList);
                            }
                        });
                    },
                    () -> {},
                    () -> {
                        ChallengeLockManager clm = new ChallengeLockManager(requireContext());
                        if (clm.isPinResetCooldownActive()) {
                            long remainingMs = clm.getPinResetRemainingMs();
                            long hours = remainingMs / (60 * 60 * 1000L);
                            long minutes = (remainingMs / (60 * 1000L)) % 60;
                            long seconds = (remainingMs / 1000L) % 60;
                            Toast.makeText(requireContext(),
                                    String.format(Locale.US, "PIN Reset Cooldown active. Time remaining: %02dh %02dm %02ds", hours, minutes, seconds),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Intent challengeIntent = new Intent(requireContext(), com.gxdevs.mindmint.Activities.LockChallengeActivity.class);
                            challengeIntent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_LOCK_TYPE, "pin_reset");
                            challengeIntent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_IS_SETTINGS_LOCK, true);
                            challengeLauncher.launch(challengeIntent);
                        }
                    }
            );
        } else {
            lockMgr.showSetCustomPinDialog(requireContext(), false, this::refreshList);
        }
    }
}