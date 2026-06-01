package com.gxdevs.mindmint.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.ChallengeLockManager;
import com.gxdevs.mindmint.Utils.SettingsLockManager;
import com.gxdevs.mindmint.Utils.Utils;

public class LockTypeSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTION_MODE = "extra_selection_mode";
    public static final String EXTRA_CURRENT_VALUE = "extra_current_value";
    
    public static final String MODE_SETTINGS = "settings";
    public static final String MODE_BLOCKER = "blocker";

    private String mode;
    private String currentValue;
    private SharedPreferences sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock_selection);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        Utils.setPad(findViewById(R.id.main), "bottom", this);
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        mode = getIntent().getStringExtra(EXTRA_SELECTION_MODE);
        if (mode == null) {
            mode = MODE_SETTINGS;
        }
        currentValue = getIntent().getStringExtra(EXTRA_CURRENT_VALUE);
        if (currentValue == null) {
            currentValue = MODE_SETTINGS.equals(mode) ? "device" : "none";
        }

        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText(MODE_SETTINGS.equals(mode) ? "Settings Lock Type" : "App Blocker Challenge");

        setupOptionsList();
        setupBypassDurationSeekBar();
    }

    private void setupBypassDurationSeekBar() {
        View durationCard = findViewById(R.id.durationCard);
        boolean showBypass = MODE_BLOCKER.equals(mode) && (
                             "math".equals(currentValue) ||
                             "scream".equals(currentValue) ||
                             "breath".equals(currentValue) ||
                             "text".equals(currentValue) ||
                             "shake".equals(currentValue)
        );
        if (showBypass) {
            durationCard.setVisibility(View.VISIBLE);
            
            SeekBar seekBar = findViewById(R.id.durationSeekBar);
            TextView valueText = findViewById(R.id.durationValueText);
            
            int savedBypass = sharedPrefs.getInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, 10);
            if (savedBypass < 5) savedBypass = 5;
            if (savedBypass > 60) savedBypass = 60;
            
            valueText.setText(savedBypass + "m");
            seekBar.setMax(55);
            seekBar.setProgress(savedBypass - 5);
            
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar1, int progress, boolean fromUser) {
                    int value = progress + 5;
                    valueText.setText(value + "m");
                    sharedPrefs.edit().putInt(ChallengeLockManager.PREF_BLOCKER_BYPASS_DURATION_MIN, value).apply();
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar1) {}
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar1) {}
            });
        } else {
            durationCard.setVisibility(View.GONE);
        }
    }

    private void setupOptionsList() {
        LinearLayout container = findViewById(R.id.optionsContainer);
        container.removeAllViews();

        String[] options;
        if (MODE_SETTINGS.equals(mode)) {
            options = new String[]{"device", "custom", "math", "scream", "breath", "text", "shake", "oneday", "window10"};
        } else {
            options = new String[]{"none", "math", "scream", "breath", "text", "shake", "oneday", "window10"};
        }

        for (int i = 0; i < options.length; i++) {
            String option = options[i];
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_lock_option, container, false);
            
            int bgRes;
            if (i == 0) {
                bgRes = R.drawable.bg_settings_top;
            } else if (i == options.length - 1) {
                bgRes = R.drawable.bg_settings_bottom;
            } else {
                bgRes = R.drawable.bg_settings_middle;
            }
            itemView.setBackgroundResource(bgRes);

            View divider = itemView.findViewById(R.id.divider);
            if (divider != null) {
                divider.setVisibility(i > 0 ? View.VISIBLE : View.GONE);
            }

            TextView title = itemView.findViewById(R.id.title);
            TextView subtitle = itemView.findViewById(R.id.subtitle);
            ImageView icon = itemView.findViewById(R.id.icon);
            ImageView checkIcon = itemView.findViewById(R.id.checkIcon);

            title.setText(getLabel(option));
            subtitle.setText(getDescription(option));
            icon.setImageResource(getIconRes(option));

            boolean isSelected = option.equals(currentValue);
            checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> handleOptionSelected(option));

            container.addView(itemView);
        }
    }

    private void handleOptionSelected(String option) {
        if (option.equals(currentValue)) {
            finish();
            return;
        }

        if ("oneday".equals(option)) {
            showOneDayLockWarning(() -> saveAndFinish(option), () -> {});
        } else if (MODE_SETTINGS.equals(mode) && "custom".equals(option)) {
            SettingsLockManager lockMgr = new SettingsLockManager(this);
            if (!lockMgr.hasCustomPin()) {
                lockMgr.showSetCustomPinDialog(this, false, () -> saveAndFinish(option));
            } else {
                saveAndFinish(option);
            }
        } else {
            saveAndFinish(option);
        }
    }

    private void saveAndFinish(String option) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        if (MODE_SETTINGS.equals(mode)) {
            editor.putString(ChallengeLockManager.PREF_SETTINGS_LOCK_TYPE, option);
            if ("oneday".equals(option)) {
                ChallengeLockManager clm = new ChallengeLockManager(this);
                clm.startSettingsOneDayLock();
            }
        } else {
            editor.putString(ChallengeLockManager.PREF_BLOCKER_CHALLENGE_TYPE, option);
        }
        editor.apply();

        Intent resultData = new Intent();
        resultData.putExtra("selected_value", option);
        setResult(RESULT_OK, resultData);
        finish();
    }

    private String getLabel(String option) {
        return switch (option) {
            case "none" -> "Normal Blocker";
            case "device" -> "Device Lock";
            case "custom" -> "Custom PIN";
            case "math" -> "Math Equation";
            case "scream" -> "Scream (Voice)";
            case "breath" -> "Hold Breath (10s)";
            case "text" -> "Type Quote";
            case "shake" -> "Shake to Unlock";
            case "oneday" -> "1-Day Lock";
            case "window10" -> "10-Min Bypass Window";
            default -> option;
        };
    }

    private String getDescription(String option) {
        return switch (option) {
            case "none" -> "Normal blocker behavior by default.";
            case "device" -> "Use your phone's PIN, pattern, or biometrics.";
            case "custom" -> "Require a custom 6-digit PIN.";
            case "math" -> "Solve a moderately difficult math equation.";
            case "scream" -> "Hold a loud scream into the mic for 3 seconds.";
            case "breath" -> "Continuously press a button for 10 seconds.";
            case "text" -> "Type a long quote with special symbols.";
            case "shake" -> "Continuously shake your device for 12 seconds.";
            case "oneday" -> "Strictly lock settings/apps for 24 hours.";
            case "window10" -> "Use a single 10-minute bypass window per day.";
            default -> "";
        };
    }

    private int getIconRes(String option) {
        return switch (option) {
            case "none" -> R.drawable.eye_off;
            case "device" -> R.drawable.smartphone;
            case "custom" -> R.drawable.shield;
            case "math" -> R.drawable.shapes;
            case "scream" -> R.drawable.bell;
            case "breath" -> R.drawable.clock;
            case "text" -> R.drawable.scroll_text;
            case "shake" -> R.drawable.zap;
            case "oneday" -> R.drawable.shield;
            case "window10" -> R.drawable.hourglass;
            default -> R.drawable.shield;
        };
    }

    private void showOneDayLockWarning(Runnable onConfirmed, Runnable onCancelled) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_confirm, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.dialogMessage);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirm);

        tvTitle.setText("⚠️ Strict 1-Day Lockout");
        tvMessage.setText("WARNING: Enabling the 1-Day Lock will strictly lock you out of settings/this app for 24 hours. "
                + "This is system-enforced and CANNOT be undone, paused, or bypassed by changing the device clock or resetting PINs.\n\n"
                + "Do you want to proceed?");

        btnConfirm.setEnabled(false);
        btnConfirm.setText("Understand (5s)");
        btnCancel.setText("Cancel");

        dialog.show();

        Handler countdownHandler = getHandler(btnConfirm);

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

    @NonNull
    private Handler getHandler(MaterialButton btnConfirm) {
        final int[] secondsLeft = {5};
        Handler countdownHandler = new Handler(Looper.getMainLooper());
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
        return countdownHandler;
    }
}
