package com.gxdevs.mindmint.Activities;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textview.MaterialTextView;
import com.gxdevs.mindmint.Components.NebulaStarfieldView;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.FocusService;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Views.RevealMaskImageView;

import java.util.Locale;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;
import me.tankery.lib.circularseekbar.CircularSeekBar;

public class FocusMode extends AppCompatActivity {

    private static final String TAG = "FocusMode";
    private static final float REVEAL_COMPLETE_AT = 0.95f; // 95% of timer
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;
    private TextView timerText, instructionText;
    private Button startButton;
    private FocusService focusService;
    private boolean isBound = false;
    private final Handler handler = new Handler();
    private BottomSheetDialog bottomSheetDialog;
    private int selectedMinutes = 10; // Default to minimum 10 minutes
    private CircularSeekBar circularSeekBar;
    private ImageView crystalBase;
    private RevealMaskImageView crystalColor;
    private LottieAnimationView lottieAnimation;
    private boolean warnedInvalidDuration = false;
    private boolean revealCompleteNotified = false;
    private ValueAnimator revealAnimator;
    private MaterialTextView mintCrystalsTxt;
    private MintCrystals mintCrystals;
    private BlurView blurView;
    private BlurTarget blurTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_focus_mode);
        Utils.setPad(findViewById(R.id.main), "bottom", this);

        setupPermissionLauncher();
        initViews();
        setupOnUI();
        setupReveal();
        setupCircularSeekBar();

    }

    private void setupOnUI() {
        findViewById(R.id.coinImg).setOnClickListener(v -> showPeaceCoinsDialog());
        findViewById(R.id.mintCrystals).setOnClickListener(v -> showPeaceCoinsDialog());
        startButton.setOnClickListener(v -> {
            if (checkNotification()) {
                if (isBound && focusService != null) {
                    if (focusService.isTimerRunning()) {
                        focusService.stopService();
                    } else {
                        if (selectedMinutes < 10) {
                            Toast.makeText(this, "Minimum timer is 10 minutes", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        hideCrystalForRunningInit();
                        Intent serviceIntent = new Intent(this, FocusService.class);
                        serviceIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
                        serviceIntent.putExtra("durationInMillis", getDuration());
                        ContextCompat.startForegroundService(this, serviceIntent);
                        circularSeekBar.setProgress(0f);
                        if (!isBound) {
                            bindService();
                        } else {
                            focusService.startTimer(getDuration());
                            updateButtonStates(true);
                            handler.post(updateUITask);
                        }
                    }
                } else {
                    hideCrystalForRunningInit();
                    Intent serviceIntent = new Intent(this, FocusService.class);
                    serviceIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
                    serviceIntent.putExtra("durationInMillis", getDuration());
                    ContextCompat.startForegroundService(this, serviceIntent);
                    bindService();
                }
            }
        });
        NebulaStarfieldView galaxy = findViewById(R.id.starFieldView);
        galaxy.setBackgroundColorCustom(Color.rgb(5, 5, 20));
        galaxy.setStarCount(200);
        galaxy.setDriftRange(2f);
        mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
        blurView.setupWith(blurTarget).setBlurRadius(15f);
    }

    private void initViews() {
        startButton = findViewById(R.id.focusStart);
        timerText = findViewById(R.id.timerText);
        instructionText = findViewById(R.id.instructionText);
        circularSeekBar = findViewById(R.id.circularProgress);
        crystalBase = findViewById(R.id.crystalBase);
        crystalColor = findViewById(R.id.crystalColor);
        lottieAnimation = findViewById(R.id.lottie);
        mintCrystalsTxt = findViewById(R.id.mintCrystals);
        mintCrystals = new MintCrystals(this);
        blurView = findViewById(R.id.btnBlur);
        blurTarget = findViewById(R.id.blurTarget);
    }

    private void setupReveal() {
        if (crystalColor != null) {
            crystalColor.setRevealFraction(0f);
            crystalColor.invalidate(); // Force redraw
            //lastRevealFraction = 0f;
        }
        if (lottieAnimation != null) {
            lottieAnimation.setVisibility(INVISIBLE);
        }
    }

    private void setupCircularSeekBar() {
        circularSeekBar.setProgress(0f);
        try {
            circularSeekBar.setMax(180f);
        } catch (Throwable t) {
            Log.e(TAG, String.valueOf(t));
        }

        circularSeekBar.setOnSeekBarChangeListener(new CircularSeekBar.OnCircularSeekBarChangeListener() {
            @Override
            public void onProgressChanged(CircularSeekBar circularSeekBar, float progress, boolean fromUser) {
                if (fromUser) {
                    selectedMinutes = (int) progress;
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

        if (crystalBase != null) crystalBase.setImageResource(crystalResource);
        if (crystalColor != null) crystalColor.setImageResource(crystalResource);
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
        if (view == null) return;
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(400);
        fadeIn.setFillAfter(true);
        view.startAnimation(fadeIn);
        view.setVisibility(VISIBLE);
    }

    private void fadeOut(View view) {
        if (view == null) return;
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
        instructionText.setText(getString(R.string.stay_focused));
        fadeIn(lottieAnimation);
        circularSeekBar.setEnabled(false);
        circularSeekBar.setVisibility(INVISIBLE);
    }

    private void showTimerStoppedState() {
        instructionText.setText(getString(R.string.set_your_focus_time));
        fadeOut(lottieAnimation);
        circularSeekBar.setEnabled(true);
        circularSeekBar.setVisibility(VISIBLE);
    }

    private void showCrystalForSelection() {
        if (crystalColor == null) return;

        if (revealAnimator != null && revealAnimator.isRunning()) {
            revealAnimator.cancel();
        }

        float currentFraction = crystalColor.getRevealFraction();

        revealAnimator = ValueAnimator.ofFloat(currentFraction, 1f);
        revealAnimator.setDuration(600);
        revealAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            crystalColor.setRevealFraction(value);
            //lastRevealFraction = value;
        });
        revealAnimator.start();
    }

    private void hideCrystalForRunningInit() {
        if (crystalColor == null) return;

        if (revealAnimator != null && revealAnimator.isRunning()) {
            revealAnimator.cancel();
        }

        float currentFraction = crystalColor.getRevealFraction();

        revealAnimator = ValueAnimator.ofFloat(currentFraction, 0f);
        revealAnimator.setDuration(600);
        revealAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            crystalColor.setRevealFraction(value);
            //lastRevealFraction = value;
        });
        revealAnimator.start();
        revealCompleteNotified = false;
        applyCrystalEffectBase();
    }

    private void updateCrystalVisualsForProgress(float progress) {
        if (crystalColor == null) return;

        // Map timer progress (0..0.955) to reveal fraction (0..1)
        float targetFraction = progress <= 0f ? 0f : Math.min(1f, progress / REVEAL_COMPLETE_AT);

        // Direct set - no animation (timer provides smooth progression)
        crystalColor.setRevealFraction(targetFraction);
        //lastRevealFraction = targetFraction;

        // Completion check
        if (targetFraction >= 1f && !revealCompleteNotified) {
            revealCompleteNotified = true;
        } else if (targetFraction < 1f) {
            revealCompleteNotified = false;
        }
    }

    private boolean checkNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                showBottomSheet(R.string.notiHead, R.string.notiDesc, R.string.click_on_proceed, R.string.notiStep2);
                return false;
            }
        }
        return true;
    }

    private void showBottomSheet(int heading, int desc, int step1, int step2) {
        bottomSheetDialog = new BottomSheetDialog(FocusMode.this);
        View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.layout_bottomsheet, findViewById(R.id.sheetContainer));
        TextView permissionHead = view.findViewById(R.id.permissionHead);
        TextView permissionDesc = view.findViewById(R.id.permissionDesc);
        TextView permissionStep1 = view.findViewById(R.id.permissionStep1);
        TextView permissionStep2 = view.findViewById(R.id.permissionStep2);
        TextView permissionStep3 = view.findViewById(R.id.permissionStep3);
        TextView proceed = view.findViewById(R.id.proceed);
        TextView notNow = view.findViewById(R.id.notNow);

        permissionHead.setText(returnRealValue(heading));
        permissionDesc.setText(returnRealValue(desc));
        permissionStep1.setText(returnRealValue(step1));
        permissionStep2.setText(returnRealValue(step2));
        permissionStep3.setVisibility(GONE);
        proceed.setOnClickListener(v -> askNotiPermission());
        notNow.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }

    private void askNotiPermission() {
        if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS");
        }
    }

    private String returnRealValue(int stringId) {
        return getString(stringId);
    }

    private void setupPermissionLauncher() {
        requestNotificationPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Notification permission granted");
                        if (bottomSheetDialog.isShowing()) {
                            bottomSheetDialog.dismiss();
                        }
                        Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w(TAG, "Notification permission denied");
                        Toast.makeText(this, "Notification permission is required to show focus mode notifications.", Toast.LENGTH_SHORT).show();
                    }
                });
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

        if (isBound && focusService != null) {
            boolean isRunning = focusService.isTimerRunning();

            if (isRunning) {
                if (revealAnimator != null && revealAnimator.isRunning()) {
                    revealAnimator.cancel();
                }

                long totalDuration = focusService.getCurrentDuration();
                long elapsedMillis = focusService.getElapsedMillis();

                if (totalDuration <= 0) totalDuration = 1;
                if (elapsedMillis < 0) elapsedMillis = 0;
                if (elapsedMillis > totalDuration) elapsedMillis = totalDuration;

                float progress = elapsedMillis / (float) totalDuration;
                float targetReveal = Math.min(1f, progress / REVEAL_COMPLETE_AT);

                long remainingMillis = totalDuration - elapsedMillis;
                long minutes = remainingMillis / (1000 * 60);
                long seconds = (remainingMillis / 1000) % 60;
                timerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));


                updateButtonStates(true);

                // CRITICAL: Restore reveal AFTER updateButtonStates (which changes theme)
                if (crystalColor != null) {
                    crystalColor.setRevealFraction(targetReveal);
                    //lastRevealFraction = targetReveal;
                }
                handler.post(updateUITask);
            } else {
                updateButtonStates(false);
                timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
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
            boolean isRunning = focusService.isTimerRunning();

            if (isRunning) {
                if (revealAnimator != null && revealAnimator.isRunning()) {
                    revealAnimator.cancel();
                }
                long totalDuration = focusService.getCurrentDuration();
                selectedMinutes = (int) (totalDuration / (1000 * 60));
            } else {
                timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
            }

            updateButtonStates(isRunning);

            // If timer is NOT running, animate crystal to full reveal for selection
            if (!isRunning) {
                showCrystalForSelection();
            }

            if (isRunning) {
                updateTimerUI();

                // CRITICAL: Re-sync crystal AFTER updateButtonStates changes the image
                long elapsedMillis = focusService.getElapsedMillis();
                long totalDuration = focusService.getCurrentDuration();
                if (totalDuration > 0 && crystalColor != null) {
                    float progress = elapsedMillis / (float) totalDuration;
                    float targetReveal = Math.min(1f, progress / REVEAL_COMPLETE_AT);
                    crystalColor.setRevealFraction(targetReveal);
                    //lastRevealFraction = targetReveal;
                }

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
            long elapsedMillis = focusService.getElapsedMillis();
            long totalDuration = focusService.getCurrentDuration();

            // Defensive clamps — prevent NaN / negative / overshoot
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
            timerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));

            float progress = elapsedMillis / (float) totalDuration; // 0..1
            float seekbarProgress = progress * 180f;
            if (Float.isNaN(seekbarProgress) || Float.isInfinite(seekbarProgress)) {
                seekbarProgress = 0f;
            }
            if (seekbarProgress < 0f || seekbarProgress > 180f) {
                if (!warnedInvalidDuration) {
                    warnedInvalidDuration = true;
                }
            }
            seekbarProgress = Math.max(0f, Math.min(seekbarProgress, 180f));
            circularSeekBar.setProgress(seekbarProgress);

            updateCrystalVisualsForProgress(progress);
        } else if (focusService != null && !focusService.isTimerRunning()) {
            timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
        }
    }

    private void updateButtonStates(boolean timerIsRunning) {
        if (timerIsRunning) {
            if (revealAnimator != null && revealAnimator.isRunning()) {
                revealAnimator.cancel();
            }
            showTimerRunningState();
            updateThemeForDuration(selectedMinutes);
            mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
            startButton.setText(getString(R.string.stop));
        } else {
            updateThemeForDuration(selectedMinutes);
            showTimerStoppedState();
            startButton.setText(getString(R.string.start));
            mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
            circularSeekBar.setProgress(selectedMinutes);
            timerText.setText(String.format(Locale.US, "%d min", selectedMinutes));
            handler.removeCallbacks(updateUITask);
        }
    }

    private final Runnable updateUITask = new Runnable() {
        @Override
        public void run() {
            if (isBound && focusService != null) {
                if (focusService.isTimerRunning()) {
                    updateTimerUI();
                    handler.postDelayed(this, 900);
                } else {
                    updateButtonStates(false);
                    // Timer stopped - animate crystal to full reveal for selection
                    showCrystalForSelection();
                    // If completed naturally, show completion dialog while user is on UI
                    if (focusService.consumeCompletedNaturally()) {
                        int minutes = focusService.getLastCompletedDurationMinutes();
                        showFocusCompleteDialog(minutes);
                        mintCrystalsTxt.setText(String.valueOf(mintCrystals.getCoins()));
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
        dialog.findViewById(R.id.closeButton).setOnClickListener(v -> dialog.dismiss());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showFocusCompleteDialog(int minutes) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_focus_complete);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.6f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.getWindow().setBackgroundBlurRadius(10);
            }
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        ImageView crystalImg = dialog.findViewById(R.id.crystalImage);
        LottieAnimationView sparkle = dialog.findViewById(R.id.sparkleAnim);
        TextView title = dialog.findViewById(R.id.titleText);
        TextView desc = dialog.findViewById(R.id.descText);
        TextView coinsText = dialog.findViewById(R.id.coinsText);
        MaterialTextView okBtn = dialog.findViewById(R.id.okBtn);

        int coins;
        int crystalRes;
        int progressColor;
        String lottieResource;
        if (minutes <= 30) {
            coins = 2;
            crystalRes = R.drawable.ruby;
            lottieResource = "ruby.json";
            progressColor = Color.parseColor("#FFD2D7");
        } else if (minutes <= 60) {
            coins = 5;
            crystalRes = R.drawable.emerald;
            lottieResource = "emerald.json";
            progressColor = Color.parseColor("#C1FFE9");
        } else if (minutes <= 90) {
            coins = 7;
            crystalRes = R.drawable.amethyst;
            lottieResource = "amethyst.json";
            progressColor = Color.parseColor("#F3D1FF");
        } else if (minutes <= 120) {
            coins = 10;
            crystalRes = R.drawable.moonstone;
            lottieResource = "moonstone.json";
            progressColor = Color.parseColor("#C7E7FF");
        } else if (minutes <= 150) {
            coins = 15;
            crystalRes = R.drawable.aquamarine;
            lottieResource = "aquamarine.json";
            progressColor = Color.parseColor("#CDF1FF");
        } else {
            coins = 20;
            crystalRes = R.drawable.amber;
            lottieResource = "amber.json";
            progressColor = Color.parseColor("#FFEFCA");
        }

        if (crystalImg != null) crystalImg.setImageResource(crystalRes);
        if (title != null) title.setText(R.string.congratulations);
        if (desc != null) {
            String compText = "Focus complete: " + minutes + " min";
            desc.setText(compText);
            desc.setTextColor(progressColor);
            desc.setAlpha(0.8f);
        }
        if (coinsText != null) {
            coinsText.setTextColor(progressColor);
        }
        if (okBtn != null) {
            okBtn.setOnClickListener(v -> dialog.dismiss());
        }


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

        // Count-up reward text
        if (coinsText != null) {
            ValueAnimator va = ValueAnimator.ofInt(0, coins);
            va.setDuration(900);
            va.addUpdateListener(a -> {
                int val = (int) a.getAnimatedValue();
                String crystalTxt = "+" + val + " Mint Crystals";
                coinsText.setText(crystalTxt);
            });
            va.start();
        }

        if (okBtn != null) {
            okBtn.setOnClickListener(v -> dialog.dismiss());

            okBtn.setOnTouchListener((v, e) -> {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        break;
                }
                v.performClick();
                return true;
            });
        }

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
}















