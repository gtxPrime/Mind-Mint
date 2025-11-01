package com.gxdevs.mindmint.Activities;

import static android.app.ActivityOptions.makeSceneTransitionAnimation;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.gxdevs.mindmint.Services.FocusService.TOTAL_FOCUSED_TIME_KEY;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.gxdevs.mindmint.Common.IntentActions;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.StreakPrefs;
import com.gxdevs.mindmint.Utils.TaskManager;
import com.gxdevs.mindmint.Utils.Utils;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;

import java.util.List;

import antonkozyriatskyi.circularprogressindicator.CircularProgressIndicator;
import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class HomeActivity extends AppCompatActivity {
    private static final String instaSwitchState = "instaSwitchState";
    private static final String snapSwitchState = "snapSwitchState";
    private static final String ytSwitchState = "ytSwitchState";
    private static final String KEY_INSTA_MOD = "InstaMod";
    private static final String KEY_SNAP_MOD = "SnapMod";
    private static final String PREFS_NAME = "AppData";
    private static final String KEY_YT_MOD = "YtMod";
    private static final String KEY_FIRST_NAME = "user_first_name";
    private static final String KEY_AFFIRM_VISIT_COUNT = "affirm_visit_count";
    private BlurView brainBlur, focusBlur, blockerBlur, taskBlur, habitBlur, permissionBlur;
    private ActivityResultLauncher<Intent> accessibilityLauncher;
    private MaterialSwitch ytSwitch, instaSwitch, snapSwitch;
    private CircularProgressIndicator circularProgress;
    private SharedPreferences sharedPreferences, prefs;
    private MaterialTextView totalWastedTimeTextView;
    private BroadcastReceiver timeUpdateReceiver;
    private BottomSheetDialog bottomSheetDialog;
    private AppUpdateManager appUpdateManager;
    private ShapeableImageView playPause;
    private BottomSheetDialog pauseTimer;
    private MaterialTextView mintCrystals;
    private MintCrystals mintCrystalsObj;
    private MaterialCardView blockerCard;
    private MaterialCardView focusCard;
    private MaterialCardView habitCard;
    private MaterialCardView mindCard;
    private MaterialCardView taskCard;
    private boolean isServicePaused;
    private TaskManager taskManager;
    private MaterialTextView count;
    private BlurTarget blurTarget;
    private TextView streakHolder;
    private TextView minuteHolder;
    private TextView countHolder;
    private TextView activeHolder;
    private TextView greetings;
    private ImageView brain;
    private Balloon balloon;
    private int totalWastedScrolls;

    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
        if (result.getResultCode() != RESULT_OK) {
            Toast.makeText(this, "Update canceled or failed", Toast.LENGTH_SHORT).show();
            checkForUpdate();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Utils.setPad(findViewById(R.id.main), "bottom", this);

        registerForPermission();
        intiViews();
        applyColors();
        checkForUpdate();
        maybeAskForReview();
        setupCards();
        updateAllData();
        updateTodayTasksCount();
        updateStreakDisplay();
        updateBlockerStatus();
        updateGuidanceBalloon();
        updatePlayPauseButton();
        updateGreetingWithFirstName();
        checkAndShowPermissionCard();

        totalWastedScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "yt") + Utils.calculateTotalUsageScrolls(sharedPreferences, "insta") + Utils.calculateTotalUsageScrolls(sharedPreferences, "snap");

        mintCrystals.setText(String.valueOf(mintCrystalsObj.getCoins()));
        timeUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateAllData();
            }
        };
        IntentFilter timeUpdateFilter = new IntentFilter(IntentActions.getActionTimeUpdated(this));
        ContextCompat.registerReceiver(this, timeUpdateReceiver, timeUpdateFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        playPause.setOnClickListener(v -> {
            if (isServicePaused) {
                resumeService();
            } else {
                showTimePickerBottomSheet();
            }
        });
        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, SettingsActivity.class)));

        View coinImg = findViewById(R.id.coinImg);
        if (coinImg != null) {
            coinImg.setOnClickListener(v -> showPeaceCoinsDialog());
        }
        if (mintCrystals != null) {
            mintCrystals.setOnClickListener(v -> showPeaceCoinsDialog());
        }
    }

    private void applyColors() {
        View arc1 = findViewById(R.id.arcTopLeft);
        View arc2 = findViewById(R.id.arcBottomRight);
        View arc3 = findViewById(R.id.arcBottomLeft);
        Utils.applySecondaryColor(mindCard, this);
        Utils.applySecondaryColor(focusCard, this);
        Utils.applySecondaryColor(blockerCard, this);
        Utils.applySecondaryColor(taskCard, this);
        Utils.applySecondaryColor(habitCard, this);
        Utils.applyAccentColors(arc1, arc2, arc3, this);
    }

    private void intiViews() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        isServicePaused = sharedPreferences.getBoolean("isServicePaused", false);
        totalWastedTimeTextView = findViewById(R.id.totalWastedTime);
        circularProgress = findViewById(R.id.circularProgress);
        streakHolder = findViewById(R.id.streakHolder);
        minuteHolder = findViewById(R.id.minuteHolder);
        countHolder = findViewById(R.id.countHolder);
        activeHolder = findViewById(R.id.activeHolder);
        blockerBlur = findViewById(R.id.blockerBlur);
        blurTarget = findViewById(R.id.blurTarget);
        mintCrystals = findViewById(R.id.mintCrystals);
        blurTarget = findViewById(R.id.blurTarget);
        playPause = findViewById(R.id.playPause);
        habitBlur = findViewById(R.id.habitBlur);
        brainBlur = findViewById(R.id.brainBlur);
        focusBlur = findViewById(R.id.focusBlur);
        permissionBlur = findViewById(R.id.permissionBlur);
        taskBlur = findViewById(R.id.taskBlur);
        brain = findViewById(R.id.brainHolder);
        count = findViewById(R.id.count);
        greetings = findViewById(R.id.greetings);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        taskManager = new TaskManager(this);
        mintCrystalsObj = new MintCrystals(this);
        appUpdateManager = AppUpdateManagerFactory.create(this);
        mindCard = findViewById(R.id.mindCard);
        focusCard = findViewById(R.id.focusCard);
        blockerCard = findViewById(R.id.blockerCard);
        taskCard = findViewById(R.id.taskCard);
        habitCard = findViewById(R.id.habitCard);
    }

    private void setupCards() {
        long totalFocusedSeconds = prefs.getLong(TOTAL_FOCUSED_TIME_KEY, 0);
        minuteHolder.setText(formatTime((int) totalFocusedSeconds));
        mindCard.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class), makeSceneTransitionAnimation(this, brain, "brainTransition").toBundle()));
        habitCard.setOnClickListener(v -> startActivity(new Intent(this, HabitActivity.class)));
        taskCard.setOnClickListener(v -> startActivity(new Intent(this, TaskActivity.class)));
        focusCard.setOnClickListener(v -> startActivity(new Intent(this, FocusMode.class)));
        blockerCard.setOnClickListener(v -> {
            if (isAccessibilityPermissionGranted()) {
                showBlockerBottomSheet();
            } else {
                Toast.makeText(this, "Accessibility permission required", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.report).setOnClickListener(v -> {
            String reportUrl = "https://forms.gle/rNiEevQ2aEDojpBi9";
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No browser found to open URL", Toast.LENGTH_SHORT).show();
            }
        });

        brainBlur.setupWith(blurTarget).setBlurRadius(5f);
        focusBlur.setupWith(blurTarget).setBlurRadius(5f);
        blockerBlur.setupWith(blurTarget).setBlurRadius(5f);
        taskBlur.setupWith(blurTarget).setBlurRadius(5f);
        habitBlur.setupWith(blurTarget).setBlurRadius(5f);
        permissionBlur.setupWith(blurTarget).setBlurRadius(5f);
    }

    public void updateAllData() {
        updateBrainImage(totalWastedScrolls);
        String setter;
        if (totalWastedScrolls < 200) {
            setter = getString(R.string.cond1);
        } else if (totalWastedScrolls < 400) {
            setter = getString(R.string.cond2);
        } else if (totalWastedScrolls < 600) {
            setter = getString(R.string.cond3);
        } else if (totalWastedScrolls < 800) {
            setter = getString(R.string.cond4);
        } else if (totalWastedScrolls < 1000) {
            setter = getString(R.string.cond5);
        } else if (totalWastedScrolls < 1200) {
            setter = getString(R.string.cond6);
        } else if (totalWastedScrolls < 1400) {
            setter = getString(R.string.cond7);
        } else if (totalWastedScrolls < 1600) {
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
            View brainGlow = findViewById(R.id.brainGlowHolder);
            brainGlow.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#87A44C")));
        }
        brain.setImageResource(drawableId);

        circularProgress.setMaxProgress(1400);

        int healthLeft = (int) (circularProgress.getMaxProgress() - totalWastedScrolls);
        circularProgress.setCurrentProgress(Math.max(0, healthLeft));
        circularProgress.setProgressColor(ContextCompat.getColor(this, R.color.brainColor));

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

    private void maybeAskForReview() {
        ReviewManager manager = ReviewManagerFactory.create(HomeActivity.this);
        Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                ReviewInfo reviewInfo = task.getResult();
                Task<Void> flow = manager.launchReviewFlow(HomeActivity.this, reviewInfo);
                flow.addOnCompleteListener(t -> {
                });
            }
        });
    }

    private void checkForUpdate() {
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(appUpdateInfo, updateLauncher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
                    } catch (Exception e) {
                        Log.e("Update error", "Error while auto update", e);
                    }
                }
            }
        });
    }

    private void resumeService() {
        Intent intent = new Intent(IntentActions.getActionPauseService(this));
        intent.putExtra("pause_duration", 0); // 0 means resume
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        isServicePaused = false;
        updatePlayPauseButton();
        Toast.makeText(this, "Service Resumed", Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure theme and accents are consistent after returning from Settings
        Utils.applyAppThemeFromPrefs(this);
        updateTodayTasksCount();
        updateStreakDisplay();
        checkAndShowPermissionCard();
        applyColors();

        // Update coin display
        mintCrystals.setText(String.valueOf(mintCrystalsObj.getCoins()));

        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(appUpdateInfo, updateLauncher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
                } catch (Exception e) {
                    Log.e("Update error", "Error while auto update", e);
                }
            }
        });

        isServicePaused = sharedPreferences.getBoolean("isServicePaused", false);
        updatePlayPauseButton();
        updateAllData();
        updateBlockerStatus();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Check and show the appropriate guidance balloon (permission > name > affirmations)
        // Count this visit for affirmation gating only if eligible (permission + name set)
        if (isAccessibilityPermissionGranted() && isFirstNameSet()) {
            int visits = sharedPreferences.getInt(KEY_AFFIRM_VISIT_COUNT, 0);
            // Cap to avoid unbounded growth
            sharedPreferences.edit().putInt(KEY_AFFIRM_VISIT_COUNT, Math.min(visits + 1, 100000)).apply();
        }
        updateGuidanceBalloon();

        updateGreetingWithFirstName();
    }

    private String formatTime(int timeInSeconds) {
        int totalMinutes = timeInSeconds / 60;
        return totalMinutes + "m";
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateAllData();
    }

    public void registerForPermission() {
        accessibilityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (isAccessibilityPermissionGranted()) {
                Toast.makeText(this, "Thank you for granting Accessibility permission!", Toast.LENGTH_SHORT).show();
                // Dismiss the balloon if permission is granted
                checkAndShowPermissionCard();
                // Dismiss bottom sheet if it's showing
                if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) {
                    bottomSheetDialog.dismiss();
                }
            } else {
                Toast.makeText(this, "Accessibility permission not granted.", Toast.LENGTH_SHORT).show();
                // Show guidance balloon again (will prioritize permission)
                updateGuidanceBalloon();
            }
        });
    }

    private boolean isAccessibilityPermissionGranted() {
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String packageName = getPackageName();
        return enabledServices != null && enabledServices.contains(packageName);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeUpdateReceiver != null) {
            try {
                unregisterReceiver(timeUpdateReceiver);
                Log.d("HomeActivity", "Unregistered timeUpdateReceiver.");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void updatePlayPauseButton() {
        if (isServicePaused) {
            playPause.setImageResource(R.drawable.ic_play);
        } else {
            playPause.setImageResource(R.drawable.ic_pause);
        }
    }

    private void showTimePickerBottomSheet() {
        pauseTimer = new BottomSheetDialog(HomeActivity.this);
        View bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_time_picker, findViewById(R.id.bottomSheetTimePickerLayout));
        NumberPicker hourPickerBottomSheet = bottomSheetView.findViewById(R.id.hours_selector_bottom_sheet);
        NumberPicker minutePickerBottomSheet = bottomSheetView.findViewById(R.id.minutes_selector_bottom_sheet);
        Button setLimitBtnBottomSheet = bottomSheetView.findViewById(R.id.setLimitBtnBottomSheet);

        setLimitBtnBottomSheet.setText(ContextCompat.getString(this, R.string.pause));

        hourPickerBottomSheet.setMinValue(0);
        hourPickerBottomSheet.setMaxValue(23);
        minutePickerBottomSheet.setMinValue(0);
        minutePickerBottomSheet.setMaxValue(59);

        setLimitBtnBottomSheet.setOnClickListener(v -> {
            int hours = hourPickerBottomSheet.getValue();
            int minutes = minutePickerBottomSheet.getValue();
            long pauseDuration = (hours * 3600L + minutes * 60L) * 1000L;

            if (pauseDuration > 0) {
                Intent intent = new Intent(IntentActions.getActionPauseService(this));
                intent.putExtra("pause_duration", pauseDuration);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
                isServicePaused = true;
                updatePlayPauseButton();
                Toast.makeText(this, "Blocking paused for " + hours + "h " + minutes + "m", Toast.LENGTH_SHORT).show();
            }
            pauseTimer.dismiss();
        });

        pauseTimer.setContentView(bottomSheetView);
        pauseTimer.show();
    }

    private void showBottomSheet(int heading, int desc, int step1, int step2) {
        bottomSheetDialog = new BottomSheetDialog(HomeActivity.this);
        String mainText = ContextCompat.getString(this, desc);
        String moreInfo = " More info?";
        String longText = ContextCompat.getString(this, R.string.accessibility_more_info);
        String showLess = " Show less";

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

        SpannableString spannableShort = new SpannableString(mainText + moreInfo);
        ClickableSpan moreInfoSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // expanded version
                SpannableString spannableLong = new SpannableString(longText + showLess);
                ClickableSpan showLessSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        // back to collapsed
                        permissionDesc.setText(spannableShort);
                        permissionDesc.setMovementMethod(LinkMovementMethod.getInstance());
                        permissionDesc.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(ContextCompat.getColor(HomeActivity.this, R.color.cyan));
                        ds.setUnderlineText(false);
                    }
                };

                spannableLong.setSpan(showLessSpan, longText.length(), longText.length() + showLess.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                permissionDesc.setText(spannableLong);
                permissionDesc.setMovementMethod(LinkMovementMethod.getInstance());
                permissionDesc.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); // expanded = left
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(ContextCompat.getColor(HomeActivity.this, R.color.cyan));
                ds.setUnderlineText(false);
            }
        };

        spannableShort.setSpan(moreInfoSpan, mainText.length(), mainText.length() + moreInfo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        permissionDesc.setText(spannableShort);
        permissionDesc.setMovementMethod(LinkMovementMethod.getInstance());
        permissionDesc.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); // collapsed = center
        permissionStep3.setVisibility(VISIBLE);

        proceed.setOnClickListener(v -> {
            if (!isAccessibilityPermissionGranted()) {
                accessibilityLauncher.launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                if (bottomSheetDialog.isShowing()) {
                    bottomSheetDialog.dismiss();
                }
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

    private void showBlockerBottomSheet() {
        BottomSheetDialog blockerSheet = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        View bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_blocker, findViewById(R.id.bottomSheetBlockerLayout));

        // Initialize switches
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
        ytSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSwitchState(isChecked, ytSwitchState);
            updateTrackedPackages();
            updateBlockerStatus();
            changeVisibility(ytModHolder, isChecked);
            ytModCheckbox.setEnabled(isChecked);

            // If switch is turned off, also turn off and disable the mod checkbox
            if (!isChecked) {
                ytModCheckbox.setChecked(false);
                saveModState(false, KEY_YT_MOD);
            }
        });

        // Instagram Switch Listener
        instaSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSwitchState(isChecked, instaSwitchState);
            updateTrackedPackages();
            updateBlockerStatus();
            changeVisibility(instaModHolder, isChecked);
            instaModCheckbox.setEnabled(isChecked);

            // If switch is turned off, also turn off and disable the mod checkbox
            if (!isChecked) {
                instaModCheckbox.setChecked(false);
                saveModState(false, KEY_INSTA_MOD);
            }
        });

        // Snapchat Switch Listener
        snapSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSwitchState(isChecked, snapSwitchState);
            updateTrackedPackages();
            updateBlockerStatus();
            changeVisibility(snapModHolder, isChecked);
            snapModCheckbox.setEnabled(isChecked);

            // If switch is turned off, also turn off and disable the mod checkbox
            if (!isChecked) {
                snapModCheckbox.setChecked(false);
                saveModState(false, KEY_SNAP_MOD);
            }
        });

        // YouTube Mod Checkbox Listener
        ytModCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (ytSwitch.isChecked()) {
                saveModState(isChecked, KEY_YT_MOD);
            }
        });

        // Instagram Mod Checkbox Listener
        instaModCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (instaSwitch.isChecked()) {
                saveModState(isChecked, KEY_INSTA_MOD);
            }
        });

        // Snapchat Mod Checkbox Listener
        snapModCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (snapSwitch.isChecked()) {
                saveModState(isChecked, KEY_SNAP_MOD);
            }
        });

        blockerSheet.setContentView(bottomSheetView);
        blockerSheet.show();
    }

    private void updateTrackedPackages() {
        Intent intent = new Intent(IntentActions.getActionUpdatePackages(this));
        Bundle bundle = new Bundle();

        bundle.putBoolean("home_yt_switch_on", ytSwitch.isChecked());
        bundle.putBoolean("home_insta_switch_on", instaSwitch.isChecked());
        bundle.putBoolean("home_snap_switch_on", snapSwitch.isChecked());
        intent.putExtras(bundle);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        Log.d("HomeActivity", "Sending Home switch states to service: YT_ON=" + ytSwitch.isChecked() + ", INSTA_ON=" + instaSwitch.isChecked() + ", SNAP_ON=" + snapSwitch.isChecked());
    }

    private void changeVisibility(ConstraintLayout view, Boolean isVisible) {
        if (isVisible) {
            view.setVisibility(VISIBLE);
        } else {
            view.setVisibility(GONE);
        }
    }

    private void updateTodayTasksCount() {
        if (taskManager == null) return;

        List<com.gxdevs.mindmint.Models.Task> allTasks = taskManager.loadTasks();
        java.util.Calendar today = java.util.Calendar.getInstance();
        today.set(java.util.Calendar.HOUR_OF_DAY, 0);
        today.set(java.util.Calendar.MINUTE, 0);
        today.set(java.util.Calendar.SECOND, 0);
        today.set(java.util.Calendar.MILLISECOND, 0);

        int incompleteToday = 0;
        int totalToday = 0;

        for (com.gxdevs.mindmint.Models.Task task : allTasks) {
            // Check if task is for today
            boolean isToday = false;

            if (task.getScheduledDate() != null) {
                java.util.Calendar taskDate = java.util.Calendar.getInstance();
                taskDate.setTime(task.getScheduledDate());
                taskDate.set(java.util.Calendar.HOUR_OF_DAY, 0);
                taskDate.set(java.util.Calendar.MINUTE, 0);
                taskDate.set(java.util.Calendar.SECOND, 0);
                taskDate.set(java.util.Calendar.MILLISECOND, 0);

                isToday = taskDate.getTimeInMillis() == today.getTimeInMillis();
            } else if (task.getCreatedDate() != null) {
                java.util.Calendar createdDate = java.util.Calendar.getInstance();
                createdDate.setTime(task.getCreatedDate());
                createdDate.set(java.util.Calendar.HOUR_OF_DAY, 0);
                createdDate.set(java.util.Calendar.MINUTE, 0);
                createdDate.set(java.util.Calendar.SECOND, 0);
                createdDate.set(java.util.Calendar.MILLISECOND, 0);

                isToday = createdDate.getTimeInMillis() == today.getTimeInMillis();
            }

            if (isToday) {
                totalToday++;
                if (!task.isCompleted()) {
                    incompleteToday++;
                }
            }
        }

        if (countHolder != null) {
            String counterText = incompleteToday + "/" + totalToday;
            countHolder.setText(counterText);
        }
    }

    private void updateStreakDisplay() {
        if (streakHolder == null) return;
        StreakPrefs streakPrefs = new StreakPrefs(this);
        int streak = streakPrefs.getStreak();
        String lastDate = streakPrefs.getLastCompletedDate();
        String today = StreakPrefs.getTodayDateString();
        // If user missed a day, reset streak
        if (!today.equals(lastDate) && !StreakPrefs.isYesterday(lastDate)) {
            streak = 0;
            streakPrefs.setStreak(0);
        }
        String streakText = "Streak " + streak;
        streakHolder.setText(streakText);

        int color;
        if (streak == 0) {
            color = ContextCompat.getColor(this, android.R.color.darker_gray);
        } else if (streak < 3) {
            color = ContextCompat.getColor(this, R.color.redIcon); // Use your red color resource
        } else if (streak < 7) {
            color = ContextCompat.getColor(this, R.color.yellow); // Use your yellow color resource
        } else {
            color = ContextCompat.getColor(this, R.color.greenIcon); // Use your green color resource
        }
        streakHolder.setTextColor(color);
    }

    private void updateBlockerStatus() {
        if (activeHolder == null) return;

        // Count active blockers
        int activeBlockers = 0;
        if (sharedPreferences.getBoolean(ytSwitchState, false)) activeBlockers++;
        if (sharedPreferences.getBoolean(instaSwitchState, false)) activeBlockers++;
        if (sharedPreferences.getBoolean(snapSwitchState, false)) activeBlockers++;

        // Update text and color based on active count
        String statusText;
        int color;

        switch (activeBlockers) {
            case 0:
                statusText = "Inactive •";
                color = ContextCompat.getColor(this, R.color.redIcon);
                break;
            case 1:
                statusText = "Active •";
                color = Color.parseColor("#FF9800"); // Orange
                break;
            case 2:
                statusText = "Active •";
                color = ContextCompat.getColor(this, R.color.yellow);
                break;
            case 3:
                statusText = "Active •";
                color = ContextCompat.getColor(this, R.color.greenIcon);
                break;
            default:
                statusText = "Active •";
                color = ContextCompat.getColor(this, R.color.greenIcon);
                break;
        }

        activeHolder.setText(statusText);
        activeHolder.setTextColor(color);
    }

    private void checkAndShowPermissionCard() {
        MaterialCardView permissionCard = findViewById(R.id.permissionCard);
        if (!isAccessibilityPermissionGranted()) {
            permissionCard.setVisibility(VISIBLE);
            permissionCard.setOnClickListener(v -> showBottomSheet(R.string.ass_permission, R.string.why_accessibility, R.string.click_on_proceed, R.string.select_installed_apps));
        } else {
            permissionCard.setVisibility(GONE);
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
            // Randomly decide between affirmation and Instagram follow balloon
            int choice = new java.util.Random().nextInt(2); // 0 or 1
            if (choice == 0) {
                showAffirmationBalloon();
            } else {
                showInstagramBalloon();
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

        balloon = new Balloon.Builder(this)
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
                .setLifecycleOwner(this)
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                    showFirstNameDialogIfNeeded();
                })
                .build();
        brain.post(() -> balloon.showAlignTop(findViewById(R.id.brainHolder)));
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

        balloon = new Balloon.Builder(this)
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
                .setLifecycleOwner(this)
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                })
                .build();
        brain.post(() -> balloon.showAlignTop(findViewById(R.id.brainHolder)));
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

        String[] lateNight = new String[]{
                "It’s late—keep screens away and protect your sleep.",
                "Night time: slow down and unplug a little.",
                "Late hours—wind down, not scroll down."
        };
        String[] morning = new String[]{
                "Good morning—start light, avoid doom‑scrolling.",
                "Fresh start—set a small goal, not a scroll.",
                "Morning focus beats morning feed."
        };
        String[] midday = new String[]{
                "Midday check‑in: energy low? Rest a minute.",
                "Quick reset > quick scroll. Breathe.",
                "Hydrate, stretch, then continue."
        };
        String[] evening = new String[]{
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

    private int computeHealthPercent() {
        int max = 1400;
        int yt = Utils.calculateTotalUsageScrolls(sharedPreferences, "yt");
        int insta = Utils.calculateTotalUsageScrolls(sharedPreferences, "insta");
        int snap = Utils.calculateTotalUsageScrolls(sharedPreferences, "snap");
        int total = yt + insta + snap;
        int healthLeft = Math.max(0, max - total);
        return (int) ((healthLeft * 100f) / max);
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

        balloon = new Balloon.Builder(this)
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
                .setLifecycleOwner(this)
                .setOnBalloonClickListener(v -> {
                    if (balloon != null && balloon.isShowing()) {
                        balloon.dismiss();
                    }
                    openInstagramProfile();
                })
                .build();
        brain.post(() -> balloon.showAlignTop(findViewById(R.id.brainHolder)));
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
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Hardcoded Text", String.valueOf(uri));
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showPeaceCoinsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_peace_coins);
        dialog.findViewById(R.id.closeButton).setOnClickListener(v -> dialog.dismiss());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showFirstNameDialogIfNeeded() {
        String savedName = sharedPreferences.getString(KEY_FIRST_NAME, null);
        if (savedName != null && !savedName.trim().isEmpty()) {
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_first_name);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextInputEditText firstNameEditText = dialog.findViewById(R.id.firstNameEditText);
        MaterialButton saveButton = dialog.findViewById(R.id.saveNameButton);

        saveButton.setOnClickListener(v -> {
            String name = firstNameEditText.getText() != null ? firstNameEditText.getText().toString().trim() : "";
            if (name.isEmpty()) {
                firstNameEditText.setError("Enter first name");
                return;
            }
            sharedPreferences.edit().putString(KEY_FIRST_NAME, name).apply();
            updateGreetingWithFirstName();
            dialog.dismiss();
            updateGuidanceBalloon();
        });

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void updateGreetingWithFirstName() {
        if (greetings == null) return;
        String savedName = sharedPreferences.getString(KEY_FIRST_NAME, null);
        String finalTxt;
        if (savedName != null && !savedName.trim().isEmpty()) {
            finalTxt = "Hello\n" + savedName + " " + ContextCompat.getString(this, R.string.hello_hand);
        } else {
            finalTxt = "Hello\nuser " + ContextCompat.getString(this, R.string.hello_hand);
        }

        greetings.setText(finalTxt);
    }
}
















