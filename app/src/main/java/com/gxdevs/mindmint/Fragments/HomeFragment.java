package com.gxdevs.mindmint.Fragments;

import static android.app.Activity.RESULT_OK;
import static android.app.ActivityOptions.makeSceneTransitionAnimation;
import static android.content.Context.MODE_PRIVATE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.gxdevs.mindmint.Utils.Utils.isAccessibilityPermissionGranted;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.gxdevs.mindmint.Activities.FocusMode;
import com.gxdevs.mindmint.Activities.HabitCreateActivity;
import com.gxdevs.mindmint.Activities.HomeActivity;
import com.gxdevs.mindmint.Activities.StatsActivity;
import com.gxdevs.mindmint.Adapters.HomeTaskAdapter;
import com.gxdevs.mindmint.Adapters.UpdateLogAdapter;
import com.gxdevs.mindmint.Common.IntentActions;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.SettingsLockManager;
import com.gxdevs.mindmint.Utils.ChallengeLockManager;
import com.gxdevs.mindmint.Utils.StreakManager;
import com.gxdevs.mindmint.Utils.TaskManager;
import com.gxdevs.mindmint.Utils.UpdateLogData;
import com.gxdevs.mindmint.Utils.AnimUtils;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Widgets.HabitListWidgetProvider;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;

import java.util.List;
import java.util.function.Consumer;

import antonkozyriatskyi.circularprogressindicator.CircularProgressIndicator;

public class HomeFragment extends Fragment {

    private TextView greetings;
    private SharedPreferences sharedPreferences, prefs;
    private static final String KEY_FIRST_NAME = "user_first_name";
    private static final String PREFS_NAME = "AppData";
    private MaterialButton blockerBtn;
    private MaterialButton playPause;
    private MaterialButton reportBtn;
    private MaterialSwitch ytSwitch, instaSwitch, snapSwitch;
    private static final String instaSwitchState = "instaSwitchState";
    private static final String snapSwitchState = "snapSwitchState";
    private static final String ytSwitchState = "ytSwitchState";
    private static final String KEY_INSTA_MOD = "InstaMod";
    private static final String KEY_SNAP_MOD = "SnapMod";
    private static final String KEY_YT_MOD = "YtMod";
    private boolean isServicePaused;
    private static final String KEY_AFFIRM_VISIT_COUNT = "affirm_visit_count";
    private static final String KEY_DATA_MIGRATION_SHOWN = "data_migration_dialog_shown";
    private static final String KEY_HOME_TUTORIAL_SHOWN = "home_tutorial_shown";
    private static final String KEY_NAME_EDIT_HINT_SHOWN = "name_edit_hint_shown";
    private View view;
    private BottomSheetDialog pauseTimer;
    private ActivityResultLauncher<Intent> accessibilityLauncher;
    private MaterialCardView mindCard;
    private ImageView brain;
    private MaterialTextView count;
    private int totalWastedScrolls;
    private MaterialTextView totalWastedTimeTextView;
    private CircularProgressIndicator circularProgress;
    private ConstraintLayout focusCard;
    private TaskManager taskManager;
    private HabitManager habitManager;
    private MintCrystals mintCrystals;
    private Balloon balloon;
    private boolean isTutorialActive = false;
    private RecyclerView tasksRecyclerView;
    private boolean shakeIntent = false;
    private BroadcastReceiver timeUpdateReceiver;
    private com.google.android.material.card.MaterialCardView taskCard;
    private AppUpdateManager appUpdateManager;
    private ConstraintLayout habitTile1, habitTile2, habitTile3, habitTile4;
    private ImageView habitIcon1, habitIcon2, habitIcon3, habitIcon4;
    private TextView habitTitle1, habitTitle2, habitTitle3, habitTitle4;
    private StreakManager streakManager;
    private Habit[] currentHabits = new Habit[4]; // Store current habits to preserve order
    private TextView habitStreak1, habitStreak2, habitStreak3, habitStreak4;
    /** True until the first onResume fires — guards entrance animations. */
    private boolean firstResume = true;

    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() != RESULT_OK) {
                    Toast.makeText(requireContext(), "Update canceled or failed", Toast.LENGTH_SHORT).show();
                    checkForUpdate();
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);

        registerForPermission();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        checkAndShowDataMigrationDialog();
        initViews();
        preHideAnimatedViews(); // Prevent flash: hide before first render, animateEntrance() will reveal them
        initClicks();
        updateGreetingWithFirstName();
        checkAndShowPermissionCard();
        ((HomeActivity) requireActivity()).maybeAskForReview();
        updateAllData();
        setupHabitTiles();
        updatePlayPauseButton();
        checkForUpdate();
        setupTaskList();

        Intent intent = requireActivity().getIntent();
        if (intent != null && intent.hasExtra("from_guard")) {
            shakeIntent = intent.getBooleanExtra("from_guard", false);
        }

        if (shakeIntent) {
            shakeTheCard();
        }

        timeUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateAllData();
                setupHabitTiles();
                setupTaskList();
            }
        };
        IntentFilter timeUpdateFilter = new IntentFilter(IntentActions.getActionTimeUpdated(requireContext()));
        ContextCompat.registerReceiver(requireContext(), timeUpdateReceiver, timeUpdateFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        return view;
    }

    /** Pre-hides views that animateEntrance() will animate-in, so they are invisible from the
     *  first render and won't flash visible before the animation fires in onResume. */
    private void preHideAnimatedViews() {
        if (greetings != null) greetings.setAlpha(0f);
        if (mindCard != null) mindCard.setAlpha(0f);
        if (focusCard != null) focusCard.setAlpha(0f);
        if (taskCard != null) taskCard.setAlpha(0f);
        if (blockerBtn != null) { blockerBtn.setAlpha(0f); blockerBtn.setScaleX(0.6f); blockerBtn.setScaleY(0.6f); }
        if (playPause != null) { playPause.setAlpha(0f); playPause.setScaleX(0.6f); playPause.setScaleY(0.6f); }
        if (reportBtn != null) { reportBtn.setAlpha(0f); reportBtn.setScaleX(0.6f); reportBtn.setScaleY(0.6f); }
    }

    /** Staggered entrance animations for the home screen cards and buttons.
     *  Must be called from onResume() so it runs AFTER the fragment is visible. */
    private void animateEntrance() {
        // Greeting fades in first
        if (greetings != null) AnimUtils.fadeIn(greetings, 0, 260);

        // Cards bounce in with slight stagger for energetic feel
        if (mindCard != null) AnimUtils.bounceIn(mindCard, 40);
        if (focusCard != null) AnimUtils.bounceIn(focusCard, 110);
        if (taskCard != null) AnimUtils.bounceIn(taskCard, 180);

        // Action buttons spring in after cards
        if (blockerBtn != null) AnimUtils.bounceIn(blockerBtn, 200);
        if (playPause != null) AnimUtils.bounceIn(playPause, 250);
        if (reportBtn != null) AnimUtils.bounceIn(reportBtn, 300);
    }

    private void initClicks() {
        AnimUtils.attachTouchRipple(blockerBtn);
        blockerBtn.setOnClickListener(v -> {
            if (isAccessibilityPermissionGranted(requireContext())) {
                showBlockerBottomSheet();
            } else {
                shakeTheCard();
                Toast.makeText(requireContext(), "Accessibility permission required", Toast.LENGTH_SHORT).show();
            }
        });

        AnimUtils.attachTouchRipple(reportBtn);
        reportBtn.setOnClickListener(v -> {
            String reportUrl = "https://forms.gle/rNiEevQ2aEDojpBi9";
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No browser found to open URL", Toast.LENGTH_SHORT).show();
            }
        });

        AnimUtils.attachTouchRipple(playPause);
        playPause.setOnClickListener(v -> {
            // Block pause if a focus/lock-in session is active
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean isFocusActive = com.gxdevs.mindmint.Services.FocusService.isPublicFocusRun
                    || sp.getBoolean(com.gxdevs.mindmint.Services.FocusService.PREF_IS_LOCKED_IN, false);
            if (isFocusActive) {
                Toast.makeText(requireContext(), "You are in focus mode right now.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isServicePaused) {
                resumeService();
            } else {
                showTimePickerBottomSheet();
            }
        });

        AnimUtils.attachTouchRipple(mindCard);
        mindCard.setOnClickListener(v -> startActivity(new Intent(requireContext(), StatsActivity.class),
                makeSceneTransitionAnimation(requireActivity(), brain, "brainTransition").toBundle()));

        AnimUtils.attachTouchRipple(focusCard);
        focusCard.setOnClickListener(v -> startActivity(new Intent(requireContext(), FocusMode.class)));

        greetings.setOnLongClickListener(v -> {
            showFirstNameDialog(true);
            return true;
        });
    }

    private void initViews() {
        prefs = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        isServicePaused = sharedPreferences.getBoolean("isServicePaused", false);
        greetings = view.findViewById(R.id.greetings);
        blockerBtn = view.findViewById(R.id.blockerBtn);
        playPause = view.findViewById(R.id.playPause);
        reportBtn = view.findViewById(R.id.reportBtn);
        mindCard = view.findViewById(R.id.mindCard);
        brain = view.findViewById(R.id.brainHolder);
        count = view.findViewById(R.id.count);
        totalWastedScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "yt") + Utils.calculateTotalUsageScrolls(sharedPreferences, "insta") + Utils.calculateTotalUsageScrolls(sharedPreferences, "snap");
        totalWastedTimeTextView = view.findViewById(R.id.totalWastedTime);
        circularProgress = view.findViewById(R.id.circularProgress);
        focusCard = view.findViewById(R.id.focusCard);
        taskManager = new TaskManager(requireContext());
        habitManager = new HabitManager(requireContext());
        streakManager = new StreakManager(requireContext());
        mintCrystals = new MintCrystals(requireContext());
        tasksRecyclerView = view.findViewById(R.id.tasksRecyclerView);
        taskCard = view.findViewById(R.id.taskCard);
        appUpdateManager = AppUpdateManagerFactory.create(requireContext());

        // Initialize habit tiles
        habitTile1 = view.findViewById(R.id.habitTile1);
        habitTile2 = view.findViewById(R.id.habitTile2);
        habitTile3 = view.findViewById(R.id.habitTile3);
        habitTile4 = view.findViewById(R.id.habitTile4);

        habitIcon1 = view.findViewById(R.id.habitIcon1);
        habitIcon2 = view.findViewById(R.id.habitIcon2);
        habitIcon3 = view.findViewById(R.id.habitIcon3);
        habitIcon4 = view.findViewById(R.id.habitIcon4);

        habitTitle1 = view.findViewById(R.id.habitTitle1);
        habitTitle2 = view.findViewById(R.id.habitTitle2);
        habitTitle3 = view.findViewById(R.id.habitTitle3);
        habitTitle4 = view.findViewById(R.id.habitTitle4);

        habitStreak1 = view.findViewById(R.id.habitStreak1);
        habitStreak2 = view.findViewById(R.id.habitStreak2);
        habitStreak3 = view.findViewById(R.id.habitStreak3);
        habitStreak4 = view.findViewById(R.id.habitStreak4);
    }

    private void updateGreetingWithFirstName() {
        if (greetings == null)
            return;
        String savedName = sharedPreferences.getString(KEY_FIRST_NAME, null);
        String finalTxt;
        if (savedName != null && !savedName.trim().isEmpty()) {
            finalTxt = savedName;
        } else {
            finalTxt = "user";
        }

        greetings.setText(finalTxt);
    }

    public void showBlockerBottomSheet() {
        BottomSheetDialog blockerSheet = new BottomSheetDialog(requireContext(), R.style.CustomBottomSheetTheme);
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_blocker,
                view.findViewById(R.id.bottomSheetBlockerLayout));
        // Initialize switches
        ImageView crossBtn = bottomSheetView.findViewById(R.id.crossBtn);
        ytSwitch = bottomSheetView.findViewById(R.id.ytSwitch);
        instaSwitch = bottomSheetView.findViewById(R.id.instaSwitch);
        snapSwitch = bottomSheetView.findViewById(R.id.snapSwitch);

        // Initialize mod checkboxes
        MaterialCheckBox ytModCheckbox = bottomSheetView.findViewById(R.id.ytModSwitch);
        MaterialCheckBox instaModCheckbox = bottomSheetView.findViewById(R.id.instaModSwitch);
        MaterialCheckBox snapModCheckbox = bottomSheetView.findViewById(R.id.snapModSwitch);

        // Initialize mod holders
        ConstraintLayout ytModHolder = bottomSheetView.findViewById(R.id.ytModHolder);
        ConstraintLayout instaModHolder = bottomSheetView.findViewById(R.id.instaModHolder);
        ConstraintLayout snapModHolder = bottomSheetView.findViewById(R.id.snapModHolder);

        // Load saved switch states from SharedPreferences
        boolean ytSwitchChecked = sharedPreferences.getBoolean(ytSwitchState, false);
        boolean instaSwitchChecked = sharedPreferences.getBoolean(instaSwitchState, false);
        boolean snapSwitchChecked = sharedPreferences.getBoolean(snapSwitchState, false);

        ytSwitch.setChecked(ytSwitchChecked);
        instaSwitch.setChecked(instaSwitchChecked);
        snapSwitch.setChecked(snapSwitchChecked);

        // Load saved mod checkbox states from AppData prefs
        boolean ytModChecked = prefs.getBoolean(KEY_YT_MOD, false);
        boolean instaModChecked = prefs.getBoolean(KEY_INSTA_MOD, false);
        boolean snapModChecked = prefs.getBoolean(KEY_SNAP_MOD, false);

        ytModCheckbox.setChecked(ytModChecked);
        instaModCheckbox.setChecked(instaModChecked);
        snapModCheckbox.setChecked(snapModChecked);

        // Set initial visibility and enabled state based on switch states
        changeVisibility(ytModHolder, ytSwitchChecked);
        ytModCheckbox.setEnabled(ytSwitchChecked);

        changeVisibility(instaModHolder, instaSwitchChecked);
        instaModCheckbox.setEnabled(instaSwitchChecked);

        changeVisibility(snapModHolder, snapSwitchChecked);
        snapModCheckbox.setEnabled(snapSwitchChecked);

        // YouTube Switch Listener
        applyLockedSwitch(ytSwitch, "Change YouTube Blocker", (isChecked) -> {
            saveSwitchState(isChecked, ytSwitchState);
            updateTrackedPackages();
            changeVisibility(ytModHolder, isChecked);
            ytModCheckbox.setEnabled(isChecked);
            if (!isChecked) {
                ytModCheckbox.setChecked(false);
                saveModState(false, KEY_YT_MOD);
            }
        });

        // Instagram Switch Listener
        applyLockedSwitch(instaSwitch, "Change Instagram Blocker", (isChecked) -> {
            saveSwitchState(isChecked, instaSwitchState);
            updateTrackedPackages();
            changeVisibility(instaModHolder, isChecked);
            instaModCheckbox.setEnabled(isChecked);
            if (!isChecked) {
                instaModCheckbox.setChecked(false);
                saveModState(false, KEY_INSTA_MOD);
            }
        });

        // Snapchat Switch Listener
        applyLockedSwitch(snapSwitch, "Change Snapchat Blocker", (isChecked) -> {
            saveSwitchState(isChecked, snapSwitchState);
            updateTrackedPackages();
            changeVisibility(snapModHolder, isChecked);
            snapModCheckbox.setEnabled(isChecked);
            if (!isChecked) {
                snapModCheckbox.setChecked(false);
                saveModState(false, KEY_SNAP_MOD);
            }
        });

        // YouTube Mod Checkbox Listener
        applyLockedSwitch(ytModCheckbox, "Change YouTube Mod", (isChecked) -> {
            if (ytSwitch.isChecked())
                saveModState(isChecked, KEY_YT_MOD);
        });

        // Instagram Mod Checkbox Listener
        applyLockedSwitch(instaModCheckbox, "Change Instagram Mod", (isChecked) -> {
            if (instaSwitch.isChecked())
                saveModState(isChecked, KEY_INSTA_MOD);
        });

        // Snapchat Mod Checkbox Listener
        applyLockedSwitch(snapModCheckbox, "Change Snapchat Mod", (isChecked) -> {
            if (snapSwitch.isChecked())
                saveModState(isChecked, KEY_SNAP_MOD);
        });

        // Blocker Bypass Duration SeekBar setup
        SeekBar durationSeekBar = bottomSheetView.findViewById(R.id.durationSeekBar);
        TextView durationValueText = bottomSheetView.findViewById(R.id.durationValueText);
        int savedBypass = sharedPreferences.getInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, 10);
        if (savedBypass < 5) savedBypass = 5;
        if (savedBypass > 60) savedBypass = 60;

        durationValueText.setText(savedBypass + "m");
        durationSeekBar.setMax(55); // Range is 55 (5 to 60)
        durationSeekBar.setProgress(savedBypass - 5);

        durationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 5;
                durationValueText.setText(value + "m");
                sharedPreferences.edit().putInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, value).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        crossBtn.setOnClickListener(v -> blockerSheet.dismiss());

        blockerSheet.setContentView(bottomSheetView);
        blockerSheet.show();
    }

    private void applyLockedSwitch(CompoundButton switchView, String reason, Consumer<Boolean> logic) {
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsLockManager lm = new SettingsLockManager(requireContext());

            if (!lm.isLockEnabled() || isChecked) {
                logic.accept(isChecked);
                return;
            }

            buttonView.setOnCheckedChangeListener(null);
            buttonView.setChecked(true);

            lm.authenticate(requireActivity(), reason, new SettingsLockManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    buttonView.setChecked(false);
                    logic.accept(false);
                    applyLockedSwitch(switchView, reason, logic); // reattach listener
                }

                @Override
                public void onFailure(String err) {
                    if (!"Cancelled".equals(err)) {
                        Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
                    }
                    applyLockedSwitch(switchView, reason, logic); // reattach listener
                }
            });

            buttonView.setOnCheckedChangeListener((v, c) -> {
            });
        });
    }

    private void changeVisibility(ConstraintLayout view, Boolean isVisible) {
        if (isVisible) {
            view.setVisibility(VISIBLE);
        } else {
            view.setVisibility(GONE);
        }
    }

    private void saveSwitchState(boolean state, String key) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, state);
        editor.apply();
    }

    private void saveModState(boolean state, String key) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, state);
        editor.apply();
    }

    private void updateTrackedPackages() {
        Intent intent = new Intent(IntentActions.getActionUpdatePackages(requireContext()));
        Bundle bundle = new Bundle();
        bundle.putBoolean("home_yt_switch_on", ytSwitch.isChecked());
        bundle.putBoolean("home_insta_switch_on", instaSwitch.isChecked());
        bundle.putBoolean("home_snap_switch_on", snapSwitch.isChecked());
        intent.putExtras(bundle);
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent);
        Log.d("HomeActivity", "Sending Home switch states to service: YT_ON=" + ytSwitch.isChecked() + ", INSTA_ON="
                + instaSwitch.isChecked() + ", SNAP_ON=" + snapSwitch.isChecked());
    }

    private void shakeTheCard() {
        ConstraintLayout card = view.findViewById(R.id.permissionCard);
        card.setScaleX(1f);
        card.setScaleY(1f);
        card.setTranslationX(0f);

        vibrateHaptic();

        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.05f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.05f);
        scaleUpX.setDuration(200);
        scaleUpY.setDuration(200);

        ObjectAnimator shake = ObjectAnimator.ofFloat(
                card,
                "translationX",
                0, 20, -20, 15, -15, 10, -10, 5, -5, 0);
        shake.setDuration(650);

        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(card, "scaleX", 1.05f, 1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(card, "scaleY", 1.05f, 1f);
        scaleDownX.setDuration(200);
        scaleDownY.setDuration(200);

        AnimatorSet set = new AnimatorSet();
        set.play(scaleUpX).with(scaleUpY);
        set.play(shake).after(scaleUpX);
        set.play(scaleDownX).with(scaleDownY).after(shake);
        set.start();
    }

    private void vibrateHaptic() {
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null)
            return;
        VibrationEffect effect;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            effect = VibrationEffect.createOneShot(40, VibrationEffect.EFFECT_HEAVY_CLICK);
        } else {
            effect = VibrationEffect.createOneShot(40, 200);
        }
        vibrator.vibrate(effect);
    }

    private void resumeService() {
        Intent intent = new Intent(IntentActions.getActionPauseService(requireContext()));
        intent.putExtra("pause_duration", 0); // 0 means resume
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent);
        isServicePaused = false;
        updatePlayPauseButton();
        Toast.makeText(requireContext(), "Service Resumed", Toast.LENGTH_SHORT).show();
    }

    private void showTimePickerBottomSheet() {
        pauseTimer = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_time,
                view.findViewById(R.id.bottomSheetTimePickerLayout));
        NumberPicker hourPickerBottomSheet = bottomSheetView.findViewById(R.id.hours_selector_bottom_sheet);
        NumberPicker minutePickerBottomSheet = bottomSheetView.findViewById(R.id.minutes_selector_bottom_sheet);
        Button setLimitBtnBottomSheet = bottomSheetView.findViewById(R.id.setLimitBtnBottomSheet);

        setLimitBtnBottomSheet.setText(ContextCompat.getString(requireContext(), R.string.pause));

        hourPickerBottomSheet.setMinValue(0);
        hourPickerBottomSheet.setMaxValue(23);
        minutePickerBottomSheet.setMinValue(0);
        minutePickerBottomSheet.setMaxValue(59);

        setLimitBtnBottomSheet.setOnClickListener(v -> {
            int hours = hourPickerBottomSheet.getValue();
            int minutes = minutePickerBottomSheet.getValue();
            long pauseDuration = (hours * 3600L + minutes * 60L) * 1000L;

            if (pauseDuration > 0) {
                Runnable doPause = () -> {
                    Intent intent = new Intent(IntentActions.getActionPauseService(requireContext()));
                    intent.putExtra("pause_duration", pauseDuration);
                    intent.setPackage(requireContext().getPackageName());
                    requireContext().sendBroadcast(intent);
                    isServicePaused = true;
                    updatePlayPauseButton();
                    Toast.makeText(requireContext(), "Blocking paused for " + hours + "h " + minutes + "m",
                            Toast.LENGTH_SHORT).show();
                    pauseTimer.dismiss();
                };

                SettingsLockManager lm = new SettingsLockManager(requireContext());
                if (lm.isLockEnabled()) {
                    lm.authenticate(requireActivity(), "Pause Blocker", new SettingsLockManager.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            doPause.run();
                        }

                        @Override
                        public void onFailure(String r) {
                            if (!"Cancelled".equals(r)) {
                                Toast.makeText(requireContext(), r, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } else {
                    doPause.run();
                }
            } else {
                pauseTimer.dismiss();
            }
        });

        bottomSheetView.findViewById(R.id.crossBtn).setOnClickListener(v -> pauseTimer.dismiss());

        pauseTimer.setContentView(bottomSheetView);
        pauseTimer.show();
    }

    private void updatePlayPauseButton() {
        if (isServicePaused) {
            playPause.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_play));
        } else {
            playPause.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_pause));
        }
    }

    /**
     * Bug 7 fix: re-read isServicePaused from prefs and refresh icon. Called
     * externally on session end.
     */
    public void refreshPauseButtonState() {
        if (!isAdded() || playPause == null)
            return;
        isServicePaused = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("isServicePaused", false);
        updatePlayPauseButton();
    }

    private void checkAndShowPermissionCard() {
        ConstraintLayout permissionCard = view.findViewById(R.id.permissionCard);
        if (!isAccessibilityPermissionGranted(requireContext())) {
            permissionCard.setVisibility(VISIBLE);
            permissionCard.setOnClickListener(v -> reqAccess());
        } else {
            permissionCard.setVisibility(GONE);
        }
    }

    private void reqAccess() {
        Utils.showPermissionSheet(requireContext(), Utils.PermissionType.ACCESSIBILITY,
                new Utils.PermissionLauncher() {
                    @Override
                    public void launchAccessibility(Intent intent) {
                        if (accessibilityLauncher != null) {
                            accessibilityLauncher.launch(intent);
                        } else {
                            startActivity(intent);
                        }
                    }

                    @Override
                    public void launchBattery(Intent intent) {
                        // Not used for accessibility
                    }

                    @Override
                    public void launchNotification(String permission) {
                        // Not used for accessibility
                    }
                },
                () -> {
                    Toast.makeText(requireContext(), "Accessibility permission not granted.", Toast.LENGTH_SHORT)
                            .show();
                    checkAndShowPermissionCard();
                });
    }

    public void registerForPermission() {
        accessibilityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isAccessibilityPermissionGranted(requireContext())) {
                        Toast.makeText(requireContext(), "Thank you for granting Accessibility permission!",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Accessibility permission not granted.", Toast.LENGTH_SHORT)
                                .show();
                    }
                    checkAndShowPermissionCard();
                    // Also refresh the Lock In pill in HomeActivity
                    if (getActivity() instanceof HomeActivity) {
                        ((HomeActivity) getActivity()).updateLockInPillLabel();
                    }
                });
    }

    public void updateAllData() {
        // Refresh scroll counts from SharedPreferences
        totalWastedScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "yt")
                + Utils.calculateTotalUsageScrolls(sharedPreferences, "insta")
                + Utils.calculateTotalUsageScrolls(sharedPreferences, "snap");

        updateBrainImage(totalWastedScrolls);
        String setter;
        if (totalWastedScrolls < 150) {
            setter = getString(R.string.cond1);
        } else if (totalWastedScrolls < 300) {
            setter = getString(R.string.cond2);
        } else if (totalWastedScrolls < 500) {
            setter = getString(R.string.cond3);
        } else if (totalWastedScrolls < 700) {
            setter = getString(R.string.cond4);
        } else if (totalWastedScrolls < 900) {
            setter = getString(R.string.cond5);
        } else if (totalWastedScrolls < 1100) {
            setter = getString(R.string.cond6);
        } else if (totalWastedScrolls < 1200) {
            setter = getString(R.string.cond7);
        } else if (totalWastedScrolls < 1400) {
            setter = getString(R.string.cond8);
        } else {
            setter = getString(R.string.cond9);
        }
        totalWastedTimeTextView.setText(setter);
        count.setText(String.valueOf(totalWastedScrolls));
    }

    private void updateBrainImage(int totalWastedScrolls) {
        int drawableId = getDrawableId(totalWastedScrolls);
        if (totalWastedScrolls >= 700) {
            View brainGlow = view.findViewById(R.id.brainGlowHolder);
            brainGlow.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#87A44C")));
        }
        brain.setImageResource(drawableId);

        circularProgress.setMaxProgress(1400);

        int healthLeft = (int) (circularProgress.getMaxProgress() - totalWastedScrolls);
        circularProgress.setCurrentProgress(Math.max(0, healthLeft));
        circularProgress.setProgressColor(ContextCompat.getColor(requireContext(), R.color.brainColor));

        int color;
        if (healthLeft > 1200) {
            color = Color.parseColor("#FD7D81"); // Brain color (Healthy)
        } else if (healthLeft > 800) {
            color = Color.parseColor("#FFC107"); // Yellow (Getting worse)
        } else if (healthLeft > 400) {
            color = Color.parseColor("#FF5722"); // Orange (Critical)
        } else {
            color = Color.parseColor("#F44336"); // Red (Dead)
        }
        circularProgress.setProgressColor(color);
    }

    private static int getDrawableId(int totalWastedScrolls) {
        int drawableId;
        if (totalWastedScrolls < 150) {
            drawableId = R.drawable.brain1;
        } else if (totalWastedScrolls < 300) {
            drawableId = R.drawable.brain2;
        } else if (totalWastedScrolls < 500) {
            drawableId = R.drawable.brain3;
        } else if (totalWastedScrolls < 700) {
            drawableId = R.drawable.brain4;
        } else if (totalWastedScrolls < 900) {
            drawableId = R.drawable.brain5;
        } else if (totalWastedScrolls < 1100) {
            drawableId = R.drawable.brain6;
        } else if (totalWastedScrolls < 1200) {
            drawableId = R.drawable.brain7;
        } else if (totalWastedScrolls < 1400) {
            drawableId = R.drawable.brain8;
        } else {
            drawableId = R.drawable.brain9;
        }
        return drawableId;
    }

    private void setupHabitTiles() {
        List<Habit> allHabits = habitManager.loadHabits();

        // If we have stored habits, preserve their order; otherwise sort by streak
        if (currentHabits[0] == null && currentHabits[1] == null && currentHabits[2] == null
                && currentHabits[3] == null) {
            // First time: sort by streak
            allHabits = habitManager.getHabitsSortedByStreak();
            for (int i = 0; i < 4 && i < allHabits.size(); i++) {
                currentHabits[i] = allHabits.get(i);
            }
        } else {
            // Update existing habits with fresh data while preserving order
            for (int i = 0; i < 4; i++) {
                if (currentHabits[i] != null) {
                    for (Habit h : allHabits) {
                        if (h.getId().equals(currentHabits[i].getId())) {
                            currentHabits[i] = h; // Update with fresh data
                            break;
                        }
                    }
                }
            }
        }

        // Bind each tile (max 4 habits)
        bindHabitTile(habitTile1, habitIcon1, habitTitle1, habitStreak1, currentHabits[0]);
        bindHabitTile(habitTile2, habitIcon2, habitTitle2, habitStreak2, currentHabits[1]);
        bindHabitTile(habitTile3, habitIcon3, habitTitle3, habitStreak3, currentHabits[2]);
        bindHabitTile(habitTile4, habitIcon4, habitTitle4, habitStreak4, currentHabits[3]);
    }

    private void bindHabitTile(ConstraintLayout tile, ImageView icon, TextView title, TextView streak, Habit habit) {
        // Add spring press feedback to every tile
        AnimUtils.attachTouchRipple(tile);
        if (habit == null) {
            icon.getLayoutParams().width = dpToPx(35);
            icon.getLayoutParams().height = dpToPx(35);
            icon.requestLayout();
            icon.setImageResource(R.drawable.ic_add);
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brand_pink)));
            title.setVisibility(View.VISIBLE);
            title.setText(ContextCompat.getString(requireContext(), R.string.add_habits));
            streak.setVisibility(View.GONE);
            tile.setOnClickListener(v -> startActivity(new Intent(requireContext(), HabitCreateActivity.class)));
        } else {
            // Show as habit tile
            icon.getLayoutParams().width = dpToPx(22);
            icon.getLayoutParams().height = dpToPx(22);
            icon.requestLayout();
            // Use habit icon, default to flame if not set
            int iconRes = habit.getIcon() != 0 ? habit.getIcon() : R.drawable.flame;
            icon.setImageResource(iconRes);

            title.setVisibility(View.VISIBLE);
            title.setText(habit.getName());
            streak.setVisibility(View.VISIBLE);
            streak.setText(String.valueOf(habit.getCurrentStreakDays()));

            // Set icon tint based on completion status
            boolean completedToday = habitManager.isCompletedToday(habit);
            if (completedToday) {
                tile.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(R.attr.habitTileMarked)));
                icon.setImageTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brainColor)));
            } else {
                tile.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(R.attr.surface_card)));
                icon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#808080")));
            }

            tile.setOnClickListener(v -> {
                AnimatorSet bounce = new AnimatorSet();
                bounce.playTogether(
                        ObjectAnimator.ofFloat(icon, "scaleX", 1f, 1.2f, 1f),
                        ObjectAnimator.ofFloat(icon, "scaleY", 1f, 1.2f, 1f));
                bounce.setDuration(300);
                bounce.start();

                // Check state before any action
                boolean wasCompleted = habitManager.isCompletedToday(habit);

                // Handle goal habits differently
                if (habit.isGoalTracking()) {
                    if (wasCompleted) {
                        unmarkHabit(habit, tile, icon, streak);
                    } else {
                        // Not completed - show goal progress bottom sheet
                        showGoalProgressBottomSheet(habit, tile, icon, streak);
                    }
                    return;
                }

                // Normal habit - toggle completion
                if (wasCompleted) {
                    unmarkHabit(habit, tile, icon, streak);
                } else {
                    if (habit.isAskEmotion()) {
                        onShowEmotionLog(habit, tile, icon, streak);
                    } else {
                        completeHabit(habit, tile, icon, streak, null);
                    }
                }

                setupTaskList();
            });
        }
    }

    private int getAttrColor(int attr) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private void completeHabit(Habit habit, ConstraintLayout tile, ImageView icon, TextView streak, String emotion) {
        habitManager.markHabit(habit, emotion);
        mintCrystals.addCoins(5);

        // Mark related tasks
        List<com.gxdevs.mindmint.Models.Task> all = taskManager.loadTasks();
        for (com.gxdevs.mindmint.Models.Task t : all) {
            if (habit.getId().equals(t.getHabitId())) {
                t.setCompleted(true);
            }
        }
        taskManager.saveTasks(all);

        // Update streak
        if (streakManager != null) {
            streakManager.updateStreakOnHabitCompletion();
        }

        // Update UI
        tile.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(R.attr.habitTileMarked)));
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brainColor)));

        Habit updated = reloadHabit(habit.getId());
        if (updated != null) {
            streak.setText(String.valueOf(updated.getCurrentStreakDays()));
            updateStoredHabit(updated);
        }
        setupTaskList();
        updateWidgets();
    }

    private void unmarkHabit(Habit habit, ConstraintLayout tile, ImageView icon, TextView streak) {
        habitManager.unmarkHabit(habit);
        mintCrystals.subtractCoins(5);

        // Unmark related tasks
        List<com.gxdevs.mindmint.Models.Task> all = taskManager.loadTasks();
        for (com.gxdevs.mindmint.Models.Task t : all) {
            if (habit.getId().equals(t.getHabitId())) {
                t.setCompleted(false);
            }
        }
        taskManager.saveTasks(all);

        // Update streak
        if (streakManager != null) {
            List<Habit> habits = habitManager.loadHabits();
            streakManager.checkAndResetStreakIfNeeded(habits);
        }

        // Update UI
        tile.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(R.attr.surface_card)));
        icon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#808080")));

        Habit updated = reloadHabit(habit.getId());
        if (updated != null) {
            streak.setText(String.valueOf(updated.getCurrentStreakDays()));
            updateStoredHabit(updated);
        }
        setupTaskList();
        updateWidgets();
    }

    private void onShowEmotionLog(Habit habit, ConstraintLayout tile, ImageView icon, TextView streak) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_mood, null);
        dialog.setContentView(sheet);

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

            completeHabit(habit, tile, icon, streak, emotion);
            dialog.dismiss();
        };

        sheet.findViewById(R.id.moodTired).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodMotivated).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodRelieved).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodSatisfied).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodProud).setOnClickListener(emotionListener);
        sheet.findViewById(R.id.moodNeutral).setOnClickListener(emotionListener);

        sheet.findViewById(R.id.btnSkip).setOnClickListener(v -> {
            completeHabit(habit, tile, icon, streak, null);
            dialog.dismiss();
        });

        sheet.findViewById(R.id.crossBtn).setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    private Habit reloadHabit(String habitId) {
        List<Habit> allHabits = habitManager.loadHabits();
        for (Habit h : allHabits) {
            if (h.getId().equals(habitId)) {
                return h;
            }
        }
        return null;
    }

    private void updateStoredHabit(Habit updatedHabit) {
        for (int i = 0; i < 4; i++) {
            if (currentHabits[i] != null && currentHabits[i].getId().equals(updatedHabit.getId())) {
                currentHabits[i] = updatedHabit;
                break;
            }
        }
    }

    private void showGoalProgressBottomSheet(Habit habit, ConstraintLayout tile, ImageView tileIcon, TextView streak) {
        BottomSheetDialog goalSheet = new BottomSheetDialog(requireContext(), R.style.CustomBottomSheetTheme);
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_progress,
                view.findViewById(R.id.bottomSheetGoalProgressLayout));

        // Find views
        ImageView crossBtn = bottomSheetView.findViewById(R.id.crossBtn);
        TextView habitName = bottomSheetView.findViewById(R.id.habitName);
        ImageView habitIcon = bottomSheetView.findViewById(R.id.habitIcon);
        TextView tvCurrentProgress = bottomSheetView.findViewById(R.id.tvCurrentProgress);
        TextView tvTargetCount = bottomSheetView.findViewById(R.id.tvTargetCount);
        TextView tvTargetUnit = bottomSheetView.findViewById(R.id.tvTargetUnit);
        ProgressBar pbGoal = bottomSheetView.findViewById(R.id.pbGoal);
        ImageView btnMinus = bottomSheetView.findViewById(R.id.btnMinus);
        ImageView btnPlus = bottomSheetView.findViewById(R.id.btnPlus);
        TextView helperText = bottomSheetView.findViewById(R.id.helperText);

        // Bind habit data
        habitName.setText(habit.getName());
        int iconRes = habit.getIcon() != 0 ? habit.getIcon() : R.drawable.flame;
        habitIcon.setImageResource(iconRes);

        // Colors
        int iconTint = habit.getIconTint();
        int iconBg = habit.getIconBackgroundTint();
        int brandColor = getAttrColor(R.attr.brand_pink);

        habitIcon.setColorFilter(iconTint);
        habitIcon.setBackgroundTintList(ColorStateList.valueOf(iconBg));

        // Reset progress if needed (new day)
        habit.resetProgressIfNeeded();

        // Use an array to hold mutable progress value in lambda
        final int[] currentProgress = { habit.getCurrentProgress() };
        final int targetCount = habit.getTargetCount();
        final int oneTapValue = habit.getOneTapValue();

        tvCurrentProgress.setText(String.valueOf(currentProgress[0]));
        tvCurrentProgress.setTextColor(iconTint);

        tvTargetCount.setText(String.valueOf(targetCount));
        tvTargetCount.setTextColor(getAttrColor(R.attr.text_secondary));

        tvTargetUnit.setText(habit.getTargetUnit() != null ? habit.getTargetUnit() : "");
        tvTargetUnit.setTextColor(getAttrColor(R.attr.text_secondary));

        pbGoal.setMax(targetCount);
        pbGoal.setProgress(currentProgress[0]);
        pbGoal.setProgressTintList(ColorStateList.valueOf(iconTint));
        pbGoal.setProgressBackgroundTintList(ColorStateList.valueOf(iconBg));

        btnPlus.setImageTintList(ColorStateList.valueOf(brandColor));
        btnMinus.setImageTintList(ColorStateList.valueOf(brandColor));

        // Update helper text based on progress
        Runnable updateHelperText = () -> {
            if (currentProgress[0] >= targetCount) {
                helperText.setText("Goal reached! Tap to dismiss.");
                helperText.setTextColor(ContextCompat.getColor(requireContext(), R.color.brainColor));
            } else {
                helperText.setText("Reach your goal to complete this habit");
                helperText.setTextColor(getAttrColor(R.attr.text_tertiary));
            }
        };
        updateHelperText.run();

        // Plus button
        btnPlus.setOnClickListener(v -> {
            if (currentProgress[0] >= targetCount) {
                return; // Already at goal
            }

            currentProgress[0] = Math.min(currentProgress[0] + oneTapValue, targetCount);
            tvCurrentProgress.setText(String.valueOf(currentProgress[0]));
            pbGoal.setProgress(currentProgress[0]);
            updateHelperText.run();

            // Update habit progress in DB
            boolean justCompleted = habitManager.updateHabitProgress(habit, currentProgress[0]);
            habit.setCurrentProgress(currentProgress[0]);

            if (justCompleted) {
                // Goal reached - mark as complete and update tile
                mintCrystals.addCoins(5);

                // Update tile appearance
                tile.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(R.attr.habitTileMarked)));
                tileIcon.setImageTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brainColor)));

                // Reload and update stored habit
                Habit updatedHabit = reloadHabit(habit.getId());
                if (updatedHabit != null) {
                    streak.setText(String.valueOf(updatedHabit.getCurrentStreakDays()));
                    updateStoredHabit(updatedHabit);
                }

                // Show emotion log if enabled
                if (habit.isAskEmotion()) {
                    goalSheet.dismiss();
                    onShowEmotionLog(habit, tile, tileIcon, streak);
                }

                setupTaskList();
            }
            updateWidgets();
        });

        // Minus button
        btnMinus.setOnClickListener(v -> {
            if (currentProgress[0] <= 0) {
                return;
            }

            boolean wasCompleted = habitManager.isCompletedToday(habit);
            currentProgress[0] = Math.max(0, currentProgress[0] - oneTapValue);
            tvCurrentProgress.setText(String.valueOf(currentProgress[0]));
            pbGoal.setProgress(currentProgress[0]);
            updateHelperText.run();

            // Update habit progress in DB
            habitManager.updateHabitProgress(habit, currentProgress[0]);
            habit.setCurrentProgress(currentProgress[0]);

            // If was completed and now below target, coins already subtracted by
            // updateHabitProgress
            if (wasCompleted && currentProgress[0] < targetCount) {
                mintCrystals.subtractCoins(5);

                // Update tile appearance
                tile.setBackgroundTintList(ColorStateList.valueOf(getAttrColor(R.attr.surface_card)));
                tileIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#808080")));

                // Reload and update stored habit
                Habit updatedHabit = reloadHabit(habit.getId());
                if (updatedHabit != null) {
                    streak.setText(String.valueOf(updatedHabit.getCurrentStreakDays()));
                    updateStoredHabit(updatedHabit);
                }

                setupTaskList();
            }
            updateWidgets();
        });

        // Close button
        crossBtn.setOnClickListener(v -> goalSheet.dismiss());

        goalSheet.setContentView(bottomSheetView);
        goalSheet.show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupTaskList() {
        List<com.gxdevs.mindmint.Models.Task> tasks = taskManager.getTasksSortedByPriorityAndTime();
        HomeTaskAdapter taskAdapter = new HomeTaskAdapter(tasks, taskManager, habitManager, this::setupHabitTiles);
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        tasksRecyclerView.setAdapter(taskAdapter);

        boolean isEmpty = tasks == null || tasks.isEmpty();
        if (taskCard != null) {
            taskCard.setVisibility(isEmpty ? GONE : VISIBLE);
        }
    }

    private void updateGuidanceBalloon() {
        // Priority 1: Ask for name via a tappable balloon
        if (!isFirstNameSet()) {
            showNameBalloon();
            return;
        }

        // Priority 2: Show contextual affirmations
        int visits = sharedPreferences.getInt(KEY_AFFIRM_VISIT_COUNT, 0);
        if (visits >= 5) {
            sharedPreferences.edit().putInt(KEY_AFFIRM_VISIT_COUNT, 0).apply();
            // Randomly decide between affirmation, Instagram, Telegram, or GitHub balloons
            int choice = new java.util.Random().nextInt(4);
            if (choice == 0) {
                showAffirmationBalloon();
            } else if (choice == 1) {
                showInstagramBalloon();
            } else if (choice == 2) {
                showTelegramBalloon();
            } else {
                showGithubBalloon();
            }
        } else {
            // No balloon this time
            if (balloon != null && balloon.isShowing()) {
                balloon.dismiss();
            }
        }
    }

    private boolean isFirstNameSet() {
        String savedName = sharedPreferences.getString(KEY_FIRST_NAME, null);
        return savedName != null && !savedName.trim().isEmpty();
    }

    private void showNameBalloon() {
        int bgColor;
        if (totalWastedScrolls >= 700) {
            bgColor = R.color.rotBrainColor;
        } else {
            bgColor = R.color.brainColor;
        }

        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }

        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setArrowPosition(0.5f)
                .setWidthRatio(0.7f)
                .setTextTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(16f)
                .setCornerRadius(12f)
                .setAlpha(0.95f)
                .setPadding(2)
                .setMarginRight(5)
                .setText("Tell us your name.\n(tap to add name)")
                .setTextColorResource(R.color.white)
                .setBackgroundColorResource(bgColor)
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                    showFirstNameDialog(false);
                })
                .build();
        brain.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignTop(view.findViewById(R.id.brainHolder));
            }
        });
    }

    private void showAffirmationBalloon() {
        String text = getAffirmationText();
        int bgColor;
        if (totalWastedScrolls >= 700) {
            bgColor = R.color.rotBrainColor;
        } else {
            bgColor = R.color.brainColor;
        }
        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }

        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setAutoDismissDuration(5000)
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setArrowPosition(0.5f)
                .setWidthRatio(0.7f)
                .setTextTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(16f)
                .setCornerRadius(12f)
                .setAlpha(0.95f)
                .setPadding(2)
                .setMarginRight(5)
                .setText(text)
                .setTextColorResource(R.color.white)
                .setBackgroundColorResource(bgColor)
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                })
                .build();
        brain.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignTop(view.findViewById(R.id.brainHolder));
            }
        });
    }

    private void showInstagramBalloon() {
        int bgColor;
        if (totalWastedScrolls >= 700) {
            bgColor = R.color.rotBrainColor;
        } else {
            bgColor = R.color.brainColor;
        }

        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }

        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setAutoDismissDuration(7000)
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setArrowPosition(0.5f)
                .setWidthRatio(0.7f)
                .setTextTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(16f)
                .setCornerRadius(12f)
                .setAlpha(0.95f)
                .setPadding(2)
                .setMarginRight(5)
                .setText("Follow us on Insta!")
                .setTextColorResource(R.color.white)
                .setBackgroundColorResource(bgColor)
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                    openInstagramProfile();
                })
                .build();
        brain.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignTop(view.findViewById(R.id.brainHolder));
            }
        });
    }

    private void checkAndShowDataMigrationDialog() {
        // Check if dialog has already been shown
        if (sharedPreferences.getBoolean(KEY_DATA_MIGRATION_SHOWN, false)) {
            return; // Already shown, skip
        }
        SharedPreferences habitPrefs = requireContext().getSharedPreferences("HabitPrefs", Context.MODE_PRIVATE);
        SharedPreferences taskPrefs = requireContext().getSharedPreferences("TaskPrefs", Context.MODE_PRIVATE);

        // Check if old prefs have any data
        boolean hasOldHabitData = !habitPrefs.getAll().isEmpty();
        boolean hasOldTaskData = !taskPrefs.getAll().isEmpty();

        if (!hasOldHabitData && !hasOldTaskData) {
            return; // No old data found, skip dialog
        }

        // Clear old prefs immediately (before managers are created)
        habitPrefs.edit().clear().apply();
        taskPrefs.edit().clear().apply();

        // Add 25 coins as compensation
        mintCrystals = new MintCrystals(requireContext());
        mintCrystals.addCoins(25);

        // Mark dialog as shown
        sharedPreferences.edit().putBoolean(KEY_DATA_MIGRATION_SHOWN, true).apply();

        // Show migration dialog to inform user
        showDataMigrationDialog();
    }

    private void showDataMigrationDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_data_migration);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageButton btnClose = dialog.findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);
        dialog.show();
    }

    private void showFirstNameDialog(boolean force) {
        String savedName = sharedPreferences.getString(KEY_FIRST_NAME, null);
        if (!force && savedName != null && !savedName.trim().isEmpty()) {
            return;
        }

        View customView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_first_name, null);
        TextInputEditText firstNameEditText = customView.findViewById(R.id.firstNameEditText);

        if (force && savedName != null) {
            firstNameEditText.setText(savedName);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.what_s_your_first_name)
                .setView(customView)
                .setCancelable(true)
                .setPositiveButton(R.string.save, null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Override the positive button click to prevent automatic dismissal on empty
        // input
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = firstNameEditText.getText() != null ? firstNameEditText.getText().toString().trim() : "";
            if (name.isEmpty()) {
                firstNameEditText.setError("Enter first name");
                return;
            }
            sharedPreferences.edit().putString(KEY_FIRST_NAME, name).apply();
            updateGreetingWithFirstName();
            dialog.dismiss();

            if (!sharedPreferences.getBoolean(KEY_NAME_EDIT_HINT_SHOWN, false)) {
                showNameEditHintBalloon();
            } else {
                updateGuidanceBalloon();
            }
        });
    }

    private String getAffirmationText() {
        int healthPercent = computeHealthPercent();

        // Progress-based first
        if (healthPercent < 20) {
            return "Running low—take a short break and rest your eyes.";
        } else if (healthPercent < 50) {
            return "Screen time is high—step away for a bit and reset.";
        } else if (healthPercent >= 70 && healthPercent <= 85) {
            return "Mind getting tired—ease up to avoid burnout.";
        }

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

        String[] lateNight = new String[] {
                "It’s late—keep screens away and protect your sleep.",
                "Night time: slow down and unplug a little.",
                "Late hours—wind down, not scroll down."
        };
        String[] morning = new String[] {
                "Good morning—start light, avoid doom‑scrolling.",
                "Fresh start—set a small goal, not a scroll.",
                "Morning focus beats morning feed."
        };
        String[] midday = new String[] {
                "Midday check‑in: energy low? Rest a minute.",
                "Quick reset > quick scroll. Breathe.",
                "Hydrate, stretch, then continue."
        };
        String[] evening = new String[] {
                "Evening wind‑down: keep it calm.",
                "Unplug a bit—your mind will thank you.",
                "Wrap up strong, not endless scrolling."
        };

        String[] pool;
        if (hour == 23 || hour < 5) {
            pool = lateNight;
        } else if (hour < 11) {
            pool = morning;
        } else if (hour < 16) {
            pool = midday;
        } else {
            pool = evening;
        }

        int idx = new java.util.Random().nextInt(pool.length);
        return pool[idx];
    }

    private void openInstagramProfile() {
        Uri uri = Uri.parse("https://instagram.com/mindmintapp");
        Intent instaIntent = new Intent(Intent.ACTION_VIEW, uri);
        instaIntent.setPackage("com.instagram.android");
        try {
            startActivity(instaIntent);
        } catch (ActivityNotFoundException e) {
            // Fallback to any app that can handle the link (browser, etc.)
            Intent fallback = new Intent(Intent.ACTION_VIEW, uri);
            try {
                startActivity(fallback);
            } catch (Exception ignored) {
                ClipboardManager clipboard = (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Hardcoded Text", String.valueOf(uri));
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showTelegramBalloon() {
        int bgColor;
        if (totalWastedScrolls >= 700) {
            bgColor = R.color.rotBrainColor;
        } else {
            bgColor = R.color.brainColor;
        }

        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }

        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setAutoDismissDuration(7000)
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setArrowPosition(0.5f)
                .setWidthRatio(0.7f)
                .setTextTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(16f)
                .setCornerRadius(12f)
                .setAlpha(0.95f)
                .setPadding(2)
                .setMarginRight(5)
                .setText("Join on Telegram")
                .setTextColorResource(R.color.white)
                .setBackgroundColorResource(bgColor)
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                    openTelegram();
                })
                .build();
        brain.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignTop(view.findViewById(R.id.brainHolder));
            }
        });
    }

    private void openTelegram() {
        Uri uri = Uri.parse("https://t.me/mindmintapp");
        Intent telegramIntent = new Intent(Intent.ACTION_VIEW, uri);
        telegramIntent.setPackage("org.telegram.messenger");
        try {
            startActivity(telegramIntent);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_VIEW, uri);
            try {
                startActivity(fallback);
            } catch (Exception ignored) {
                ClipboardManager clipboard = (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Hardcoded Text", String.valueOf(uri));
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showGithubBalloon() {
        int bgColor;
        if (totalWastedScrolls >= 700) {
            bgColor = R.color.rotBrainColor;
        } else {
            bgColor = R.color.brainColor;
        }

        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }

        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setAutoDismissDuration(7000)
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setArrowPosition(0.5f)
                .setWidthRatio(0.7f)
                .setTextTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(16f)
                .setCornerRadius(12f)
                .setAlpha(0.95f)
                .setPadding(2)
                .setMarginRight(5)
                .setText("Star on Github")
                .setTextColorResource(R.color.white)
                .setBackgroundColorResource(bgColor)
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                    openGithub();
                })
                .build();
        brain.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignTop(view.findViewById(R.id.brainHolder));
            }
        });
    }

    private void openGithub() {
        Uri uri = Uri.parse("https://github.com/gtxprime/mind-mint");
        Intent githubIntent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(githubIntent);
        } catch (Exception ignored) {
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Hardcoded Text", String.valueOf(uri));
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private int computeHealthPercent() {
        int max = 1400;
        int yt = Utils.calculateTotalUsageScrolls(sharedPreferences, "yt");
        int insta = Utils.calculateTotalUsageScrolls(sharedPreferences, "insta");
        int snap = Utils.calculateTotalUsageScrolls(sharedPreferences, "snap");
        int total = yt + insta + snap;
        int healthLeft = Math.max(0, max - total);
        return (int) ((healthLeft * 100f) / max);
    }

    @Override
    public void onDestroy() {
        if (timeUpdateReceiver != null) {
            try {
                requireContext().unregisterReceiver(timeUpdateReceiver);
                Log.d("HomeActivity", "Unregistered timeUpdateReceiver.");
            } catch (IllegalArgumentException ignored) {
            }
        }
        super.onDestroy();
    }

    private void checkForUpdate() {
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(appUpdateInfo, updateLauncher,
                                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
                    } catch (Exception e) {
                        Log.e("Update error", "Error while auto update", e);
                    }
                }
            }
        });
    }

    public void onResume() {
        super.onResume();
        // Play entrance animations only the first time this tab is actually VISIBLE.
        // ViewPager2 with offscreenPageLimit calls onResume for ALL pre-loaded fragments
        // when the Activity resumes, so we must additionally confirm this page is current.
        if (firstResume && isCurrentPage(com.gxdevs.mindmint.Adapters.HomePagerAdapter.PAGE_HOME)) {
            firstResume = false;
            if (view != null) view.post(this::animateEntrance);
        }
        if (view != null) {
            setupTaskList();
        }
        // Always refresh service state from prefs on resume
        if (sharedPreferences != null) {
            isServicePaused = sharedPreferences.getBoolean("isServicePaused", false);
            if (playPause != null)
                updatePlayPauseButton();
        }
        updatePlayPauseButton();
        updateAllData();
        // Reset habit order on resume to show any new habits
        currentHabits = new Habit[4];
        setupHabitTiles();
        setupTaskList();
        if (isAccessibilityPermissionGranted(requireContext()) && isFirstNameSet()) {
            int visits = sharedPreferences.getInt(KEY_AFFIRM_VISIT_COUNT, 0);
            sharedPreferences.edit().putInt(KEY_AFFIRM_VISIT_COUNT, Math.min(visits + 1, 100000)).apply();
        }

        checkAndShowUpdateLog(this::startTutorialSequence);

        updateGreetingWithFirstName();
        Utils.applyAppThemeFromPrefs(requireContext());
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(appUpdateInfo, updateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
                } catch (Exception e) {
                    Log.e("Update error", "Error while auto update", e);
                }
            }
        });
    }

    /** Returns true only when this fragment is the page currently shown by the host ViewPager2. */
    private boolean isCurrentPage(int expectedPageIndex) {
        if (getActivity() instanceof HomeActivity) {
            androidx.viewpager2.widget.ViewPager2 vp =
                    getActivity().findViewById(com.gxdevs.mindmint.R.id.nav_host_container);
            if (vp != null) return vp.getCurrentItem() == expectedPageIndex;
        }
        return true; // fallback: allow animation
    }

    @Override
    public void onStart() {
        super.onStart();
        updateAllData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timeUpdateReceiver != null) {
            try {
                requireContext().unregisterReceiver(timeUpdateReceiver);
                Log.d("HomeActivity", "Unregistered timeUpdateReceiver.");
            } catch (IllegalArgumentException ignored) {
            }
        }

    }

    private void checkAndShowUpdateLog(Runnable onDismiss) {
        try {
            PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(),
                    0);
            int currentVersionCode = pInfo.versionCode;
            int lastShownVersion = prefs.getInt("last_shown_update_log_version", -1);

            if (lastShownVersion < currentVersionCode) {
                showUpdateLogBottomSheet(pInfo.versionName, onDismiss);
                prefs.edit().putInt("last_shown_update_log_version", currentVersionCode).apply();
            } else {
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            if (onDismiss != null) {
                onDismiss.run();
            }
        }
    }

    private void showUpdateLogBottomSheet(String versionName, Runnable onDismiss) {
        BottomSheetDialog updateLogSheet = new BottomSheetDialog(requireContext(), R.style.CustomBottomSheetTheme);
        View bottomSheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_update_log,
                view.findViewById(R.id.bottomSheetUpdateLogLayout));

        TextView footer = bottomSheetView.findViewById(R.id.footer);
        footer.setText("Mind Mint v" + versionName);

        RecyclerView recyclerView = bottomSheetView.findViewById(R.id.logsRecyclerView);
        TextView subTitle = bottomSheetView.findViewById(R.id.subTitle);
        TextView title = bottomSheetView.findViewById(R.id.title);
        ImageView crossBtn = bottomSheetView.findViewById(R.id.crossBtn);

        // Customize texts if needed
        subTitle.setText("What's New");
        title.setText("Update Log");

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        UpdateLogAdapter adapter = new UpdateLogAdapter(UpdateLogData.getLogs());
        recyclerView.setAdapter(adapter);

        crossBtn.setOnClickListener(v -> updateLogSheet.dismiss());

        updateLogSheet.setOnDismissListener(dialog -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        updateLogSheet.setContentView(bottomSheetView);
        updateLogSheet.show();
    }

    private void updateWidgets() {
        AppWidgetManager man = AppWidgetManager.getInstance(requireContext());
        int[] listIds = man.getAppWidgetIds(new ComponentName(requireContext(), HabitListWidgetProvider.class));
        if (listIds.length > 0) {
            man.notifyAppWidgetViewDataChanged(listIds, R.id.widget_habit_list_view);
        }
    }

    private void startTutorialSequence() {
        if (isTutorialActive)
            return;
        if (!sharedPreferences.getBoolean(KEY_HOME_TUTORIAL_SHOWN, false)) {
            isTutorialActive = true;
            startHomeTutorial();
        } else {
            checkAndShowNameTutorial();
        }
    }

    private void checkAndShowNameTutorial() {
        if (!isFirstNameSet()) {
            updateGuidanceBalloon();
        } else if (!sharedPreferences.getBoolean(KEY_NAME_EDIT_HINT_SHOWN, false)) {
            showNameEditHintBalloon();
        } else {
            updateGuidanceBalloon();
        }
    }

    private void startHomeTutorial() {
        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }
        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowPosition(0.5f)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(14f)
                .setCornerRadius(10f)
                .setAlpha(0.9f)
                .setPadding(8)
                .setText("Block distractions instantly")
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                .setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brainColor))
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setDismissWhenClicked(true)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonDismissListener(this::showPlayPauseBalloonStep)
                .build();

        blockerBtn.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignBottom(blockerBtn);
            }
        });
    }

    private void showReportBalloonStep() {
        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }
        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowPosition(0.84f)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(14f)
                .setCornerRadius(10f)
                .setAlpha(0.9f)
                .setPadding(8)
                .setText("Send feedback or request feature")
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                .setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brainColor))
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setDismissWhenClicked(true)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonDismissListener(() -> {
                    sharedPreferences.edit().putBoolean(KEY_HOME_TUTORIAL_SHOWN, true).apply();
                    isTutorialActive = false;
                    checkAndShowNameTutorial();
                })
                .build();

        reportBtn.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignBottom(reportBtn);
            }
        });
    }

    private void showPlayPauseBalloonStep() {
        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }
        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowPosition(0.64f)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(14f)
                .setCornerRadius(10f)
                .setAlpha(0.9f)
                .setPadding(8)
                .setText("Pause blocking when you need a break")
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                .setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brainColor))
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setDismissWhenClicked(true)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonDismissListener(this::showReportBalloonStep)
                .build();

        playPause.post(() -> {
            if (isAdded() && balloon != null) {
                balloon.showAlignBottom(playPause);
            }
        });
    }

    private void showNameEditHintBalloon() {
        if (balloon != null && balloon.isShowing()) {
            balloon.dismiss();
        }

        balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowPosition(0.25f)
                .setWidthRatio(0.6f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(14f)
                .setCornerRadius(10f)
                .setAlpha(0.9f)
                .setPadding(8)
                .setText("Long press to edit")
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                .setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brainColor))
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setDismissWhenClicked(true)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonDismissListener(() -> {
                    sharedPreferences.edit().putBoolean(KEY_NAME_EDIT_HINT_SHOWN, true).apply();
                    updateGuidanceBalloon();
                })
                .build();

        greetings.post(() -> balloon.showAlignBottom(greetings));
    }
}
