package com.gxdevs.mindmint.Activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;
import com.gxdevs.mindmint.Utils.ChallengeLockManager;
import com.gxdevs.mindmint.Utils.SettingsLockManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LockChallengeActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "LockChallengeActivity";
    private static final int MIC_PERMISSION_REQUEST_CODE = 401;

    // Challenge types matching ChallengeLockManager constants
    public static final String EXTRA_LOCK_TYPE = "extra_lock_type";
    public static final String EXTRA_TARGET_PACKAGE = "extra_target_package";
    public static final String EXTRA_IS_SETTINGS_LOCK = "extra_is_settings_lock";

    private String lockType;
    private String targetPackage;
    private boolean isSettingsLock;

    private ChallengeLockManager challengeLockMgr;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Vibrator vibrator;

    // UI elements
    private TextView tvChallengeTitle;
    private TextView tvChallengeSubtitle;
    private Button btnVerify;

    // Math elements
    private View containerMath;
    private TextView tvMathEquation;
    private EditText etMathAnswer;
    private int mathCorrectAnswer;

    // Scream elements
    private View containerScream;
    private ProgressBar pbVolumeGauge;
    private TextView tvScreamTimer;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private double screamSustainedMs = 0;
    private static final double SCREAM_TARGET_MS = 3000.0;

    // Breath elements
    private View containerBreath;
    private TextView tvBreathTimer;
    private Button btnBreathHold;
    private double breathRemainingMs = 10000.0;
    private boolean isHoldingBreath = false;

    // Text Typing elements
    private View containerText;
    private TextView tvTypeQuote;
    private EditText etTypeInput;
    private String quoteToType;
    private static final String[] QUOTES = {
            "Focus, consistency, & discipline—these are the cornerstones of success; without them, goals are just dreams!",
            "The secret of focus is simple: find out what's important, discard the rest, and do NOT check your phone!",
            "Success doesn't just 'happen'—you have to design it, sweat for it, & ignore 1,000 distractions every single day."
    };
    private static final String PIN_RESET_BYPASS_PARAGRAPH = 
            "Discipline is the bridge between goals and accomplishment. I choose to resist short-term impulses and focus on my long-term growth. This 250-character paragraph exists to ensure I make conscious choices—not impulsive ones!";

    // Shake elements
    private View containerShake;
    private ProgressBar pbShakeProgress;
    private TextView tvShakePercentage;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float shakeProgress = 0;
    private static final float SHAKE_DECAY_RATE = 0.5f; // decay per update
    private static final float SHAKE_INCREMENT = 8.0f; // increment per shake event

    // 1-Day Lock elements
    private View containerOneDay;
    private TextView tvOneDayCountdown;

    // 10-Min Window elements
    private View containerWindow10;
    private TextView tvWindow10Desc;
    private Button btnActivateWindow;

    // PIN Reset options elements
    private View containerPinReset;
    private Button btnPinResetCooldown;
    private Button btnPinResetType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_challenge);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleCancel();
            }
        });

        challengeLockMgr = new ChallengeLockManager(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Parse Intent
        Intent intent = getIntent();
        lockType = intent.getStringExtra(EXTRA_LOCK_TYPE);
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        isSettingsLock = intent.getBooleanExtra(EXTRA_IS_SETTINGS_LOCK, false);

        if (lockType == null) {
            lockType = "math"; // Fallback default
        }

        initViews();
        setupChallengeMode();
    }

    private void initViews() {
        tvChallengeTitle = findViewById(R.id.tv_challenge_title);
        tvChallengeSubtitle = findViewById(R.id.tv_challenge_subtitle);
        btnVerify = findViewById(R.id.btn_verify);
        Button btnCancel = findViewById(R.id.btn_challenge_cancel);

        // Containers
        containerMath = findViewById(R.id.container_math);
        tvMathEquation = findViewById(R.id.tv_math_equation);
        etMathAnswer = findViewById(R.id.et_math_answer);

        containerScream = findViewById(R.id.container_scream);
        pbVolumeGauge = findViewById(R.id.pb_volume_gauge);
        tvScreamTimer = findViewById(R.id.tv_scream_timer);

        containerBreath = findViewById(R.id.container_breath);
        tvBreathTimer = findViewById(R.id.tv_breath_timer);
        btnBreathHold = findViewById(R.id.btn_breath_hold);

        containerText = findViewById(R.id.container_text);
        tvTypeQuote = findViewById(R.id.tv_type_quote);
        etTypeInput = findViewById(R.id.et_type_input);

        containerShake = findViewById(R.id.container_shake);
        pbShakeProgress = findViewById(R.id.pb_shake_progress);
        tvShakePercentage = findViewById(R.id.tv_shake_percentage);

        containerOneDay = findViewById(R.id.container_oneday);
        tvOneDayCountdown = findViewById(R.id.tv_oneday_countdown);

        containerWindow10 = findViewById(R.id.container_window10);
        tvWindow10Desc = findViewById(R.id.tv_window10_desc);
        btnActivateWindow = findViewById(R.id.btn_activate_window);

        containerPinReset = findViewById(R.id.container_pin_reset);
        btnPinResetCooldown = findViewById(R.id.btn_pin_reset_cooldown);
        btnPinResetType = findViewById(R.id.btn_pin_reset_type);

        btnCancel.setOnClickListener(v -> handleCancel());
    }

    private void setupChallengeMode() {
        // Reset visibilities
        containerMath.setVisibility(View.GONE);
        containerScream.setVisibility(View.GONE);
        containerBreath.setVisibility(View.GONE);
        containerText.setVisibility(View.GONE);
        containerShake.setVisibility(View.GONE);
        containerOneDay.setVisibility(View.GONE);
        containerWindow10.setVisibility(View.GONE);
        containerPinReset.setVisibility(View.GONE);
        btnVerify.setVisibility(View.GONE);

        // Customize headers depending on context
        if (isSettingsLock) {
            tvChallengeTitle.setText("Settings Protection");
        } else {
            String appName = targetPackage != null ? getAppName(targetPackage) : "App";
            tvChallengeTitle.setText(appName + " is Restricted");
        }

        switch (lockType) {
            case "math":
                setupMathChallenge();
                break;
            case "scream":
                setupScreamChallenge();
                break;
            case "breath":
                setupBreathChallenge();
                break;
            case "text":
                setupTextChallenge(false);
                break;
            case "shake":
                setupShakeChallenge();
                break;
            case "oneday":
                setupOneDayLockout();
                break;
            case "window10":
                setupWindow10Challenge();
                break;
            case "pin_reset":
                setupPinResetChallenge();
                break;
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "This App";
        }
    }

    private void triggerSuccess() {
        vibrate(80);
        Toast.makeText(this, "Verification Successful!", Toast.LENGTH_SHORT).show();

        if (isSettingsLock) {
            // Settings Unlock success broadcast
            Intent intent = new Intent("com.gxdevs.mindmint.action.CHALLENGE_RESOLVED");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } else {
            // Blocker Bypass success broadcast to AppUsageAccessibilityService
            if (targetPackage != null) {
                int durationMinutes = challengeLockMgr.getBypassDurationMinutes();
                Intent intent = new Intent("com.gxdevs.mindmint.action.APP_BYPASS_GRANTED");
                intent.putExtra("package_name", targetPackage);
                intent.putExtra("duration_minutes", durationMinutes);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }
        }

        setResult(RESULT_OK);
        finish();
    }

    private void handleCancel() {
        if (!isSettingsLock) {
            // Accessibility Blocker: Go Back action closes overlay and triggers Home command
            Intent closeAppIntent = new Intent(AppUsageAccessibilityService.ACTION_PERFORM_GLOBAL_HOME_FROM_OVERLAY);
            closeAppIntent.setPackage(getPackageName());
            sendBroadcast(closeAppIntent);
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void vibrate(long ms) {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }
    }

    // ================= 1. MATHS CHALLENGE =================

    private void setupMathChallenge() {
        containerMath.setVisibility(View.VISIBLE);
        btnVerify.setVisibility(View.VISIBLE);
        tvChallengeSubtitle.setText("Solve the equation to proceed");

        // Generate Math Equation A * B - C or A * B + C
        int a = (int) (Math.random() * 11) + 2; // 2 to 12
        int b = (int) (Math.random() * 11) + 2; // 2 to 12
        int c = (int) (Math.random() * 41) + 10; // 10 to 50
        boolean isSubtract = Math.random() > 0.5;

        if (isSubtract) {
            mathCorrectAnswer = (a * b) - c;
            tvMathEquation.setText("(" + a + " × " + b + ") - " + c + " = ?");
        } else {
            mathCorrectAnswer = (a * b) + c;
            tvMathEquation.setText("(" + a + " × " + b + ") + " + c + " = ?");
        }

        btnVerify.setOnClickListener(v -> {
            String ansStr = etMathAnswer.getText().toString().trim();
            if (ansStr.isEmpty()) {
                Toast.makeText(this, "Please enter an answer", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int userAns = Integer.parseInt(ansStr);
                if (userAns == mathCorrectAnswer) {
                    triggerSuccess();
                } else {
                    vibrate(150);
                    Toast.makeText(this, "Incorrect. Generating a new equation.", Toast.LENGTH_SHORT).show();
                    etMathAnswer.setText("");
                    setupMathChallenge(); // Regenerate
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid format", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= 2. SCREAM CHALLENGE =================

    private void setupScreamChallenge() {
        containerScream.setVisibility(View.VISIBLE);
        tvChallengeSubtitle.setText("Hold a loud scream for 3 continuous seconds");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            com.gxdevs.mindmint.Utils.Utils.showPermissionSheet(this, com.gxdevs.mindmint.Utils.Utils.PermissionType.AUDIO,
                    new com.gxdevs.mindmint.Utils.Utils.PermissionLauncher() {
                        @Override
                        public void launchAccessibility(Intent intent) {}

                        @Override
                        public void launchBattery(Intent intent) {}

                        @Override
                        public void launchNotification(String permission) {
                            ActivityCompat.requestPermissions(LockChallengeActivity.this,
                                    new String[]{permission}, MIC_PERMISSION_REQUEST_CODE);
                        }
                    },
                    () -> {
                        Toast.makeText(LockChallengeActivity.this, "Microphone permission required for Scream challenge.", Toast.LENGTH_LONG).show();
                        handleCancel();
                    });
        } else {
            startScreamDetection();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScreamDetection();
            } else {
                Toast.makeText(this, "Microphone permission required for Scream challenge.", Toast.LENGTH_LONG).show();
                handleCancel();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startScreamDetection() {
        if (isRecording) return;
        isRecording = true;

        int sampleRate = 8000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufferSize * 2);
        
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed.");
            isRecording = false;
            return;
        }

        audioRecord.startRecording();

        recordingThread = new Thread(() -> {
            short[] buffer = new short[minBufferSize];
            
            while (isRecording) {
                int readSize = audioRecord.read(buffer, 0, buffer.length);
                if (readSize > 0) {
                    double sum = 0;
                    for (int i = 0; i < readSize; i++) {
                        sum += buffer[i] * buffer[i];
                    }
                    double amplitude = Math.sqrt(sum / readSize);
                    
                    // Normalize amplitude to percentage
                    final int progress = (int) Math.min(100, (amplitude / 4000.0) * 100);

                    handler.post(() -> updateScreamProgress(progress));
                }
            }
        }, "ScreamDetectionThread");

        recordingThread.start();
    }

    private void updateScreamProgress(int currentVolume) {
        if (!isRecording) return;

        pbVolumeGauge.setProgress(currentVolume);

        if (currentVolume >= 80) {
            screamSustainedMs += 100;
            double timeLeft = Math.max(0.0, (SCREAM_TARGET_MS - screamSustainedMs) / 1000.0);
            tvScreamTimer.setText(String.format(Locale.US, "Hold it: %.1fs", timeLeft));

            if (screamSustainedMs >= SCREAM_TARGET_MS) {
                stopScreamDetection();
                triggerSuccess();
            }
        } else {
            // Reset sustained timer if volume drops
            screamSustainedMs = 0;
            tvScreamTimer.setText("Hold it: 3.0s");
        }
    }

    private void stopScreamDetection() {
        isRecording = false;
        if (recordingThread != null) {
            recordingThread.interrupt();
            recordingThread = null;
        }
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio recorder", e);
            }
            audioRecord = null;
        }
    }

    // ================= 3. HOLD BREATH CHALLENGE =================

    @SuppressLint("ClickableViewAccessibility")
    private void setupBreathChallenge() {
        containerBreath.setVisibility(View.VISIBLE);
        tvChallengeSubtitle.setText("Hold down the button for 10 seconds to think");

        tvBreathTimer.setText("10.0s");
        breathRemainingMs = 10000.0;
        isHoldingBreath = false;

        btnBreathHold.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isHoldingBreath = true;
                    vibrate(40);
                    startBreathHoldCountdown();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isHoldingBreath = false;
                    if (breathRemainingMs > 0) {
                        vibrate(120);
                        Toast.makeText(this, "Don't let go! Resetting...", Toast.LENGTH_SHORT).show();
                        breathRemainingMs = 10000.0;
                        tvBreathTimer.setText("10.0s");
                    }
                    break;
            }
            return true;
        });
    }

    private void startBreathHoldCountdown() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isHoldingBreath) return;

                breathRemainingMs -= 100;
                double displaySec = Math.max(0.0, breathRemainingMs / 1000.0);
                tvBreathTimer.setText(String.format(Locale.US, "%.1fs", displaySec));

                if (breathRemainingMs <= 0) {
                    isHoldingBreath = false;
                    triggerSuccess();
                } else {
                    handler.postDelayed(this, 100);
                }
            }
        });
    }

    // ================= 4. TEXT TYPING CHALLENGE =================

    private void setupTextChallenge(boolean pinResetMode) {
        containerText.setVisibility(View.VISIBLE);
        btnVerify.setVisibility(View.VISIBLE);
        tvChallengeSubtitle.setText("Type the quote below exactly to proceed");

        if (pinResetMode) {
            quoteToType = PIN_RESET_BYPASS_PARAGRAPH;
        } else {
            // Select random quote
            int index = (int) (Math.random() * QUOTES.length);
            quoteToType = QUOTES[index];
        }

        tvTypeQuote.setText(quoteToType);
        etTypeInput.setText("");

        btnVerify.setOnClickListener(v -> {
            String input = etTypeInput.getText().toString().trim();
            if (input.equals(quoteToType)) {
                if (pinResetMode) {
                    // For PIN reset bypass: clear pin directly
                    SettingsLockManager lockMgr = new SettingsLockManager(this);
                    lockMgr.setLockEnabled(false);
                    lockMgr.clearCustomPin();
                    challengeLockMgr.clearPinResetCooldown();
                    Toast.makeText(this, "PIN successfully reset!", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    triggerSuccess();
                }
            } else {
                vibrate(150);
                Toast.makeText(this, "Text mismatch. Pay attention to spaces and symbols.", Toast.LENGTH_LONG).show();
            }
        });
    }

    // ================= 5. SHAKE TO UNLOCK CHALLENGE =================

    private void setupShakeChallenge() {
        containerShake.setVisibility(View.VISIBLE);
        tvChallengeSubtitle.setText("Shake your phone sustained for 12 seconds");

        shakeProgress = 0;
        pbShakeProgress.setProgress(0);
        tvShakePercentage.setText("0%");

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        // Start shake decay loop
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (lockType.equals("shake") && shakeProgress > 0) {
                    shakeProgress = Math.max(0, shakeProgress - SHAKE_DECAY_RATE);
                    pbShakeProgress.setProgress((int) shakeProgress);
                    tvShakePercentage.setText((int) shakeProgress + "%");
                }
                handler.postDelayed(this, 100);
            }
        });
    }

    private float lastX, lastY, lastZ;
    private long lastSensorUpdate = 0;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long curTime = System.currentTimeMillis();
            // Only test sensor every 100ms
            if ((curTime - lastSensorUpdate) > 100) {
                long diffTime = (curTime - lastSensorUpdate);
                lastSensorUpdate = curTime;

                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000;

                if (speed > 800) { // Shake detected
                    shakeProgress = Math.min(100.0f, shakeProgress + SHAKE_INCREMENT);
                    pbShakeProgress.setProgress((int) shakeProgress);
                    tvShakePercentage.setText((int) shakeProgress + "%");

                    if (shakeProgress >= 100.0f) {
                        sensorManager.unregisterListener(this);
                        triggerSuccess();
                    }
                }
                lastX = x;
                lastY = y;
                lastZ = z;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ================= 6. 1-DAY LOCKOUT =================

    private void setupOneDayLockout() {
        containerOneDay.setVisibility(View.VISIBLE);
        tvChallengeSubtitle.setText("This target is locked under strict lockout protection.");

        // Start ticking timer
        handler.post(new Runnable() {
            @Override
            public void run() {
                long remainingMs = 0;
                if (isSettingsLock) {
                    remainingMs = challengeLockMgr.getSettingsOneDayLockRemainingMs();
                } else if (targetPackage != null) {
                    remainingMs = challengeLockMgr.getBlockerOneDayLockRemainingMs(targetPackage);
                }

                if (remainingMs <= 0) {
                    // Lockout complete!
                    triggerSuccess();
                    return;
                }

                long hours = remainingMs / (60 * 60 * 1000L);
                long minutes = (remainingMs / (60 * 1000L)) % 60;
                long seconds = (remainingMs / 1000L) % 60;

                tvOneDayCountdown.setText(String.format(Locale.US, "%02dh %02dm %02ds remaining", hours, minutes, seconds));

                handler.postDelayed(this, 1000);
            }
        });
    }

    // ================= 7. ONE-TIME WINDOW CHALLENGE =================

    private void setupWindow10Challenge() {
        containerWindow10.setVisibility(View.VISIBLE);
        int mins = challengeLockMgr.getBypassDurationMinutes();
        tvChallengeSubtitle.setText("Activate a single " + mins + " minute bypass window.");

        btnActivateWindow.setText("Activate " + mins + " Min Window");
        tvWindow10Desc.setText("This can only be activated once per day. Access will close automatically when the timer finishes.");

        btnActivateWindow.setOnClickListener(v -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String keyPkg = targetPackage != null ? targetPackage : "settings";
            
            if (challengeLockMgr.hasUsed10MinWindowToday(keyPkg, today)) {
                vibrate(150);
                Toast.makeText(this, "You have already used your bypass window for today!", Toast.LENGTH_LONG).show();
            } else {
                challengeLockMgr.mark10MinWindowUsed(keyPkg, today);
                triggerSuccess();
            }
        });
    }

    // ================= 8. PIN RESET CHALLENGE =================

    private void setupPinResetChallenge() {
        containerPinReset.setVisibility(View.VISIBLE);
        tvChallengeSubtitle.setText("Enforcing high-friction PIN Reset options");

        btnPinResetCooldown.setOnClickListener(v -> {
            challengeLockMgr.startPinResetCooldown();
            Toast.makeText(this, "24-Hour Cooldown started! Please wait until tomorrow.", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        });

        btnPinResetType.setOnClickListener(v -> {
            containerPinReset.setVisibility(View.GONE);
            setupTextChallenge(true); // Launch quotes typing in pin_reset override mode
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopScreamDetection();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScreamDetection();
        sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
    }
}
