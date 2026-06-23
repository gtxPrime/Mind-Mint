package com.gxdevs.mindmint.Activities;

import android.Manifest;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.gxdevs.mindmint.Adapters.HomePagerAdapter;
import com.gxdevs.mindmint.Common.IntentActions;
import com.gxdevs.mindmint.Fragments.HabitFragment;
import com.gxdevs.mindmint.Fragments.HomeFragment;
import com.gxdevs.mindmint.Fragments.SettingsFragment;
import com.gxdevs.mindmint.Fragments.TasksFragment;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.MidnightResetManager;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Utils.AnimUtils;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.gxdevs.mindmint.Services.FocusService;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_START_FRAGMENT = "extra_start_fragment";
    public static final String START_FRAGMENT_HOME = "home";
    public static final String START_FRAGMENT_TASKS = "tasks";
    public static final String START_FRAGMENT_HABITS = "habits";
    public static final String START_FRAGMENT_SETTINGS = "settings";
    public static final String PREF_START_FRAGMENT = "pref_start_fragment";

    private static final String PREF_REVIEW = "in_app_review";
    private static final String KEY_COUNT = "launch_count";
    private static final String KEY_REVIEW_DONE = "review_done";

    private static final String PREF_LOCK_IN_SAVED_MS = "pref_lock_in_saved_ms";
    private static final String PREF_LOCK_IN_WARNING_SHOWN = "pref_lock_in_warning_shown";

    private ViewPager2 viewPager;
    private ImageButton btnHome;
    private MaterialButton lockInPill;
    private android.content.BroadcastReceiver focusSessionEndedReceiver;

    private final List<FrameLayout> navItems = new ArrayList<>();
    private final List<ImageView> navIcons = new ArrayList<>();

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            syncNavToPage(position);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Utils.setPad(findViewById(R.id.main), "bottom", this);

        findViews();
        setupAdapter();
        setupNavigation();
        setupBackPressHandler();

        if (savedInstanceState == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String startFromIntent = getIntent().getStringExtra(EXTRA_START_FRAGMENT);
            String startFromPrefs = prefs.getString(PREF_START_FRAGMENT, null);
            if (startFromPrefs != null) {
                prefs.edit().remove(PREF_START_FRAGMENT).apply();
            }
            String start = startFromIntent != null ? startFromIntent : startFromPrefs;

            boolean shouldOpenAddTask = getIntent().getBooleanExtra("open_add_task", false);
            if (shouldOpenAddTask)
                start = START_FRAGMENT_TASKS;

            if (start == null)
                start = START_FRAGMENT_HOME;

            viewPager.setCurrentItem(pageIndexFor(start), false);

            if (shouldOpenAddTask) {
                viewPager.post(() -> {
                    Fragment f = getSupportFragmentManager()
                            .findFragmentByTag("f" + HomePagerAdapter.PAGE_TASKS);
                    if (f instanceof TasksFragment) {
                        ((TasksFragment) f).showAddTaskBottomSheet();
                    }
                });
            }
        }

        handleFcmIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleFcmIntent(intent);
    }

    private void handleFcmIntent(Intent intent) {
        if (intent != null && intent.hasExtra("intent")) {
            String fcmIntent = intent.getStringExtra("intent");
            if (fcmIntent != null) {
                switch (fcmIntent) {
                    case "open_focus":
                        startActivity(new Intent(this, FocusMode.class));
                        break;
                    case "open_tasks":
                        viewPager.setCurrentItem(HomePagerAdapter.PAGE_TASKS, false);
                        break;
                    case "open_tasks_add":
                        viewPager.setCurrentItem(HomePagerAdapter.PAGE_TASKS, false);
                        viewPager.post(() -> {
                            Fragment f = getSupportFragmentManager()
                                    .findFragmentByTag("f" + HomePagerAdapter.PAGE_TASKS);
                            if (f instanceof TasksFragment) {
                                ((TasksFragment) f).showAddTaskBottomSheet();
                            }
                        });
                        break;
                    case "open_habits":
                        viewPager.setCurrentItem(HomePagerAdapter.PAGE_HABITS, false);
                        break;
                    case "open_stats":
                        startActivity(new Intent(this, StatsActivity.class));
                        break;
                    case "open_stats_focus":
                        Intent sf = new Intent(this, StatsActivity.class);
                        sf.putExtra("stats_tab", "focus");
                        startActivity(sf);
                        break;
                    case "open_stats_habits":
                        Intent sh = new Intent(this, StatsActivity.class);
                        sh.putExtra("stats_tab", "habits");
                        startActivity(sh);
                        break;
                    case "open_stats_tasks":
                        Intent st = new Intent(this, StatsActivity.class);
                        st.putExtra("stats_tab", "tasks");
                        startActivity(st);
                        break;
                    case "open_settings":
                        viewPager.setCurrentItem(HomePagerAdapter.PAGE_SETTINGS, false);
                        break;
                    case "open_site_blocker":
                        startActivity(new Intent(this, SiteBlockerActivity.class));
                        break;
                    case "open_onboarding":
                        startActivity(new Intent(this, OnBoarding.class));
                        break;
                    case "open_url":
                        String url = intent.getStringExtra("intent_value");
                        if (url != null && !url.isEmpty()) {
                            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                        }
                        break;
                    case "open_play_store":
                        String pkg = getPackageName();
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    android.net.Uri.parse("market://details?id=" + pkg)));
                        } catch (android.content.ActivityNotFoundException e) {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
                        }
                        break;
                }
                // Clear the extra so it doesn't trigger again on rotation
                intent.removeExtra("intent");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MidnightResetManager.checkAndPerformReset(this);
        updateLockInPillLabel();

        lockInPill.postDelayed(this::checkAndShowLockInTutorial, 500);

        focusSessionEndedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateLockInPillLabel();
                Fragment f = getSupportFragmentManager()
                        .findFragmentByTag("f" + HomePagerAdapter.PAGE_HOME);
                if (f instanceof HomeFragment) {
                    ((HomeFragment) f).refreshPauseButtonState();
                }
            }
        };
        ContextCompat.registerReceiver(this, focusSessionEndedReceiver,
                new IntentFilter(IntentActions.getActionFocusSessionEnded(this)), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (focusSessionEndedReceiver != null) {
            try {
                unregisterReceiver(focusSessionEndedReceiver);
            } catch (Exception ignored) {
            }
            focusSessionEndedReceiver = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
    }

    private void findViews() {
        viewPager = findViewById(R.id.nav_host_container);
        btnHome = findViewById(R.id.btn_home);

        navItems.add(findViewById(R.id.nav_unlink));
        navItems.add(findViewById(R.id.nav_task));
        navItems.add(findViewById(R.id.nav_habits));
        navItems.add(findViewById(R.id.nav_setting));

        navIcons.add(findViewById(R.id.quitIcon));
        navIcons.add(findViewById(R.id.taskIcon));
        navIcons.add(findViewById(R.id.habitIcon));
        navIcons.add(findViewById(R.id.settingsIcon));

        lockInPill = findViewById(R.id.lockInPill);
        updateLockInPillLabel();
    }

    private void setupAdapter() {
        HomePagerAdapter pagerAdapter = new HomePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        viewPager.setOffscreenPageLimit(3);

        viewPager.setOverScrollMode(ViewPager2.OVER_SCROLL_NEVER);

        viewPager.registerOnPageChangeCallback(pageChangeCallback);
    }

    private void setupNavigation() {
        navItems.get(0).setOnClickListener(v -> // "Friends" — coming soon
                Toast.makeText(this, "Your friends are coming soon!", Toast.LENGTH_SHORT).show());

        navItems.get(1).setOnClickListener(v -> // Tasks
                viewPager.setCurrentItem(HomePagerAdapter.PAGE_TASKS, true));

        navItems.get(2).setOnClickListener(v -> // Habits
                viewPager.setCurrentItem(HomePagerAdapter.PAGE_HABITS, true));

        navItems.get(3).setOnClickListener(v -> // Settings
                viewPager.setCurrentItem(HomePagerAdapter.PAGE_SETTINGS, true));

        btnHome.setOnClickListener(v -> {
            btnHome.animate()
                    .scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> btnHome.animate().scaleX(1f).scaleY(1f).start());
            viewPager.setCurrentItem(HomePagerAdapter.PAGE_HOME, true);
        });

        btnHome.setOnLongClickListener(v -> {
            startActivity(new Intent(this, OnBoarding.class));
            return true;
        });

        lockInPill.setOnClickListener(v -> {
            // If focus/lock-in session is already active, open FocusMode to show timer
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isAlreadyActive = FocusService.isPublicFocusRun
                    || sharedPreferences.getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
            if (isAlreadyActive) {
                startActivity(new Intent(this, FocusMode.class));
                return;
            }
            // Require notification + exact alarm permissions before starting
            if (!hasLockInPermissions()) {
                // Open FocusMode and request it to shake the missing-permission cards
                Intent permIntent = new Intent(this, FocusMode.class);
                permIntent.putExtra(FocusMode.EXTRA_SHAKE_PERM_CARDS, true);
                startActivity(permIntent);
                return;
            }
            long savedMs = sharedPreferences.getLong(PREF_LOCK_IN_SAVED_MS, 0);
            if (savedMs > 0) {
                // One-tap lock: start immediately with saved time
                startLockInMode(savedMs);
            } else {
                showLockInSheet();
            }
        });

        lockInPill.setOnLongClickListener(v -> {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isAlreadyActive = FocusService.isPublicFocusRun
                    || sharedPreferences.getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
            if (isAlreadyActive) {
                startActivity(new Intent(this, FocusMode.class));
                return true;
            }
            showLockInSheet();
            return true;
        });

        viewPager.post(() -> syncNavToPage(viewPager.getCurrentItem()));
    }

    private void showLockInSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_time, null);

        // Customize title
        TextView titleLabel = sheetView.findViewById(R.id.greetings);
        if (titleLabel != null)
            titleLabel.setText("Lock In");
        TextView subtitle = sheetView.findViewById(R.id.dateDetails);
        if (subtitle != null)
            subtitle.setText("SET DURATION");

        NumberPicker hourPicker = sheetView.findViewById(R.id.hours_selector_bottom_sheet);
        NumberPicker minutePicker = sheetView.findViewById(R.id.minutes_selector_bottom_sheet);
        Button confirmBtn = sheetView.findViewById(R.id.setLimitBtnBottomSheet);
        View rememberRow = sheetView.findViewById(R.id.rememberTimeRow);
        MaterialSwitch rememberSwitch = sheetView.findViewById(R.id.rememberTimeSwitch);

        if (confirmBtn != null)
            confirmBtn.setText("Lock In");
        if (rememberRow != null)
            rememberRow.setVisibility(View.VISIBLE);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        long savedMs = sharedPreferences.getLong(PREF_LOCK_IN_SAVED_MS, 0);
        int savedHours = (int) (savedMs / 3600000);
        int savedMinutes = (int) ((savedMs % 3600000) / 60000);

        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(3);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        hourPicker.setValue(savedHours);
        minutePicker.setValue(savedMinutes > 0 ? savedMinutes : 25);

        if (rememberSwitch != null && savedMs > 0) {
            rememberSwitch.setChecked(true);
        }

        if (confirmBtn != null) {
            confirmBtn.setOnClickListener(v -> {
                int hours = hourPicker.getValue();
                int minutes = minutePicker.getValue();
                long durationMs = (hours * 3600L + minutes * 60L) * 1000L;
                if (durationMs <= 0) {
                    Toast.makeText(this, "Pick a duration", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (rememberSwitch != null && rememberSwitch.isChecked()) {
                    sharedPreferences.edit().putLong(PREF_LOCK_IN_SAVED_MS, durationMs).apply();
                } else {
                    sharedPreferences.edit().remove(PREF_LOCK_IN_SAVED_MS).apply();
                }
                startLockInMode(durationMs);
                updateLockInPillLabel();
                sheet.dismiss();
            });
        }

        sheetView.findViewById(R.id.crossBtn).setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(sheetView);
        sheet.show();
    }


    private boolean hasLockInPermissions() {
        // Exact alarm — required on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null || !am.canScheduleExactAlarms()) return false;
        }
        // Notification — required on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void startLockInMode(long durationMs) {
        // Guard: do not start a second focus session if one is already live
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (FocusService.isPublicFocusRun || sp.getBoolean(FocusService.PREF_IS_LOCKED_IN, false)) {
            startActivity(new Intent(this, FocusMode.class));
            return;
        }

        // First-time warning: user must explicitly accept that they cannot stop early
        if (!sp.getBoolean(PREF_LOCK_IN_WARNING_SHOWN, false)) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("🔒 Lock In Mode")
                    .setMessage(
                            """
                                    Once you start Lock In, all non-essential apps will be blocked \
                                    for the entire duration you set.
                                    
                                    You will NOT be able to stop the session early. \
                                    Make sure you are ready before confirming.
                                    
                                    Essential apps (calls, camera, settings) remain accessible.""")
                    .setPositiveButton("I'm Ready, Lock In", (d, w) -> {
                        sp.edit().putBoolean(PREF_LOCK_IN_WARNING_SHOWN, true).apply();
                        doStartLockIn(durationMs);
                    })
                    .setNegativeButton("Cancel", null)
                    .setCancelable(true)
                    .show();
        } else {
            doStartLockIn(durationMs);
        }
    }

    private void doStartLockIn(long durationMs) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        // Clear any stale pause state that could interfere with the new session
        sp.edit()
                .putBoolean("isServicePaused", false)
                .putLong("resumeTime", 0)
                .apply();

        Intent serviceIntent = new Intent(this, FocusService.class);
        serviceIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
        serviceIntent.putExtra("durationInMillis", durationMs);
        serviceIntent.putExtra(FocusService.EXTRA_IS_LOCKED_IN, true);
        serviceIntent.putExtra("topicName", "Locked In");
        // startForegroundService ensures the service starts reliably on Android 8+
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent);

        // Open FocusMode activity for visual feedback
        startActivity(new Intent(this, FocusMode.class));
        Toast.makeText(this, "Locked In!", Toast.LENGTH_SHORT).show();
    }

    public void updateLockInPillLabel() {
        if (lockInPill == null)
            return;

        boolean isHome = viewPager != null && viewPager.getCurrentItem() == HomePagerAdapter.PAGE_HOME;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isFocusActive = FocusService.isPublicFocusRun
                || sharedPreferences.getBoolean(FocusService.PREF_IS_LOCKED_IN, false);

        if (!Utils.isAccessibilityPermissionGranted(this) || !isHome || isFocusActive) {
            lockInPill.setVisibility(View.GONE);
            return;
        } else {
            lockInPill.setVisibility(View.VISIBLE);
        }

        long savedMs = sharedPreferences.getLong(PREF_LOCK_IN_SAVED_MS, 0);
        if (savedMs > 0) {
            int totalMin = (int) (savedMs / 60000);
            int h = totalMin / 60;
            int m = totalMin % 60;
            String label = h > 0 ? "Lock In · " + h + "h" + (m > 0 ? m + "m" : "") : "Lock In · " + m + "m";
            lockInPill.setText(label);
        } else {
            lockInPill.setText("Lock In");
        }
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() == HomePagerAdapter.PAGE_HOME) {
                    finish();
                } else {
                    viewPager.setCurrentItem(HomePagerAdapter.PAGE_HOME, true);
                }
            }
        });
    }

    private void syncNavToPage(int page) {
        resetNav();
        int navIdx = navIndexForPage(page);
        if (navIdx >= 0 && navIdx < navItems.size()) {
            navItems.get(navIdx).setSelected(true);
            ImageView selectedIcon = navIcons.get(navIdx);
            selectedIcon.setColorFilter(
                    ContextCompat.getColor(this, R.color.icon_selected));
            AnimUtils.navIconSelect(selectedIcon);
        }
        // Show/hide Lock In pill based on current page
        updateLockInPillLabel();
    }

    private int navIndexForPage(int page) {
        return switch (page) {
            case HomePagerAdapter.PAGE_TASKS -> 1;
            case HomePagerAdapter.PAGE_HABITS -> 2;
            case HomePagerAdapter.PAGE_SETTINGS -> 3;
            default -> -1; // Home — no nav item
        };
    }

    private int pageForFragment(Fragment f) {
        if (f instanceof TasksFragment)
            return HomePagerAdapter.PAGE_TASKS;
        if (f instanceof HabitFragment)
            return HomePagerAdapter.PAGE_HABITS;
        if (f instanceof SettingsFragment)
            return HomePagerAdapter.PAGE_SETTINGS;
        return HomePagerAdapter.PAGE_HOME;
    }

    private int pageIndexFor(String startKey) {
        return switch (startKey) {
            case START_FRAGMENT_TASKS -> HomePagerAdapter.PAGE_TASKS;
            case START_FRAGMENT_HABITS -> HomePagerAdapter.PAGE_HABITS;
            case START_FRAGMENT_SETTINGS -> HomePagerAdapter.PAGE_SETTINGS;
            default -> HomePagerAdapter.PAGE_HOME;
        };
    }

    private void resetNav() {
        for (FrameLayout item : navItems)
            item.setSelected(false);
        for (ImageView icon : navIcons)
            icon.setColorFilter(ContextCompat.getColor(this, R.color.icon_unselected));
    }

    public void maybeAskForReview() {
        SharedPreferences prefs = getSharedPreferences(PREF_REVIEW, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_REVIEW_DONE, false))
            return;
        int count = prefs.getInt(KEY_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_COUNT, count).apply();
        if (count != 5)
            return;
        ReviewManager manager = ReviewManagerFactory.create(this);
        manager.requestReviewFlow().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                manager.launchReviewFlow(this, task.getResult())
                        .addOnCompleteListener(t -> prefs.edit().putBoolean(KEY_REVIEW_DONE, true).apply());
            }
        });
    }

    private void checkAndShowLockInTutorial() {
        if (lockInPill == null || lockInPill.getVisibility() != View.VISIBLE)
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("lock_in_tutorial_shown", false))
            return;

        Balloon balloon = new Balloon.Builder(this)
                .setArrowSize(10)
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setArrowPosition(0.5f)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(14f)
                .setCornerRadius(10f)
                .setAlpha(0.95f)
                .setPadding(12)
                .setText("Tap to lock in and block distractive apps!")
                .setTextColor(ContextCompat.getColor(this, R.color.white))
                .setBackgroundColor(ContextCompat.getColor(this, R.color.brainColor))
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setDismissWhenClicked(true)
                .setLifecycleOwner(this)
                .setOnBalloonDismissListener(() -> prefs.edit().putBoolean("lock_in_tutorial_shown", true).apply())
                .build();
        balloon.showAlignTop(lockInPill);
    }
}
