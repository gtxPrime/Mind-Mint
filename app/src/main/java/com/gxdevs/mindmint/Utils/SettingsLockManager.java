package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.ChallengeLockManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SettingsLockManager
 *
 * Central helper class that manages the "require password to change settings" feature.
 * Supports two modes:
 *   1. DEVICE — uses the device lock screen / biometrics (fingerprint, face, PIN, pattern, password).
 *   2. CUSTOM  — a user-defined 6-digit case-sensitive PIN stored in SharedPreferences.
 *
 * Usage pattern (in any Activity/Fragment):
 *
 *   SettingsLockManager lock = new SettingsLockManager(requireContext());
 *   if (lock.isLockEnabled()) {
 *       lock.authenticate(this, "Change setting", new SettingsLockManager.AuthCallback() {
 *           &#64;Override public void onSuccess() { /* do the protected action *&#47; }
 *           &#64;Override public void onFailure(String reason) { /* show error *&#47; }
 *       });
 *   } else {
 *       // proceed directly
 *   }
 */
public class SettingsLockManager {

    // SharedPreference keys
    public static final String PREF_LOCK_ENABLED    = "pref_settings_lock_enabled";
    public static final String PREF_LOCK_TYPE       = "pref_settings_lock_type";
    public static final String PREF_CUSTOM_PIN      = "pref_settings_custom_pin";

    // Lock type values
    public static final String LOCK_TYPE_DEVICE = "device";
    public static final String LOCK_TYPE_CUSTOM = "custom";

    // Brute-force protection
    private static final String PREF_FAILED_ATTEMPTS  = "pref_pin_failed_attempts";
    private static final String PREF_LOCKOUT_UNTIL_MS = "pref_pin_lockout_until_ms";
    private static final int    MAX_PIN_ATTEMPTS       = 5;

    // PIN hashing
    private static final String PIN_HASH_SALT = "mindmint_v2_";

    private final SharedPreferences prefs;
    private final Context context;

    public SettingsLockManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = PreferenceManager.getDefaultSharedPreferences(this.context);
        performMigration();
    }

    private void performMigration() {
        SharedPreferences.Editor editor = prefs.edit();
        boolean needsCommit = false;

        // 1. Migrate legacy "settings_lock_password" → PREF_CUSTOM_PIN (hashed)
        if (prefs.contains("settings_lock_password")) {
            String legacyPass = prefs.getString("settings_lock_password", null);
            if (legacyPass != null) {
                if (legacyPass.length() < 6) {
                    legacyPass = String.format("%-6s", legacyPass).replace(' ', '0');
                } else if (legacyPass.length() > 6) {
                    legacyPass = legacyPass.substring(0, 6);
                }
                editor.putString(PREF_CUSTOM_PIN, hashPin(legacyPass));
            }
            editor.remove("settings_lock_password");
            needsCommit = true;
        }

        // 2. Migrate legacy "requirePasswordToChangeSettings" → PREF_LOCK_ENABLED
        if (prefs.contains("requirePasswordToChangeSettings")) {
            editor.putBoolean(PREF_LOCK_ENABLED, prefs.getBoolean("requirePasswordToChangeSettings", false));
            editor.remove("requirePasswordToChangeSettings");
            needsCommit = true;
        }

        // 3. Set default lock type if not yet set
        if (!prefs.contains(PREF_LOCK_TYPE)) {
            if (hasCustomPin()) {
                editor.putString(PREF_LOCK_TYPE, LOCK_TYPE_CUSTOM);
            } else if (prefs.contains(PREF_LOCK_ENABLED)) {
                editor.putString(PREF_LOCK_TYPE, LOCK_TYPE_DEVICE);
            }
            needsCommit = true;
        }

        // 4. Migrate plaintext PIN (length == 6) → SHA-256 hash (length == 64)
        String storedPin = prefs.getString(PREF_CUSTOM_PIN, null);
        if (storedPin != null && storedPin.length() == 6) {
            editor.putString(PREF_CUSTOM_PIN, hashPin(storedPin));
            needsCommit = true;
        }

        if (needsCommit) editor.apply();
    }

    // ─── PIN Hashing ──────────────────────────────────────────────────────────

    private static String hashPin(@NonNull String rawPin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((PIN_HASH_SALT + rawPin).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return rawPin; // Should never happen on Android
        }
    }

    // ─── Brute-Force Protection ───────────────────────────────────────────────

    private boolean isPinLockedOut() {
        return System.currentTimeMillis() < prefs.getLong(PREF_LOCKOUT_UNTIL_MS, 0L);
    }

    private long getLockoutRemainingMs() {
        return Math.max(0L, prefs.getLong(PREF_LOCKOUT_UNTIL_MS, 0L) - System.currentTimeMillis());
    }

    private void recordFailedPinAttempt() {
        int attempts = prefs.getInt(PREF_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor ed = prefs.edit().putInt(PREF_FAILED_ATTEMPTS, attempts);
        if (attempts >= MAX_PIN_ATTEMPTS) {
            long extraBeyondMax = attempts - MAX_PIN_ATTEMPTS;
            long lockMs = Math.min((long) (30_000L * Math.pow(2, extraBeyondMax)), 3_600_000L);
            ed.putLong(PREF_LOCKOUT_UNTIL_MS, System.currentTimeMillis() + lockMs);
        }
        ed.apply();
    }

    private void clearFailedPinAttempts() {
        prefs.edit().remove(PREF_FAILED_ATTEMPTS).remove(PREF_LOCKOUT_UNTIL_MS).apply();
    }

    public int getRemainingPinAttempts() {
        if (isPinLockedOut()) return 0;
        int used = prefs.getInt(PREF_FAILED_ATTEMPTS, 0);
        return Math.max(0, MAX_PIN_ATTEMPTS - used);
    }

    // ─── Feature Toggle ──────────────────────────────────────────────────────

    public boolean isLockEnabled() {
        return prefs.getBoolean(PREF_LOCK_ENABLED, false);
    }

    public void setLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_LOCK_ENABLED, enabled).apply();
    }

    // ─── Lock Type ───────────────────────────────────────────────────────────

    /** Returns LOCK_TYPE_DEVICE or LOCK_TYPE_CUSTOM */
    public String getLockType() {
        return prefs.getString(PREF_LOCK_TYPE, LOCK_TYPE_DEVICE);
    }

    public void setLockType(String type) {
        prefs.edit().putString(PREF_LOCK_TYPE, type).apply();
    }

    public boolean isDeviceLock() {
        return LOCK_TYPE_DEVICE.equals(getLockType());
    }

    public boolean isCustomPin() {
        return LOCK_TYPE_CUSTOM.equals(getLockType());
    }

    // ─── Custom PIN ──────────────────────────────────────────────────────────

    /** Returns true if a custom PIN has been saved. */
    public boolean hasCustomPin() {
        String pin = prefs.getString(PREF_CUSTOM_PIN, null);
        return pin != null && !pin.isEmpty();
    }

    /** Save a new custom PIN (must be exactly 6 characters). Stored as SHA-256 hash. */
    public void saveCustomPin(@NonNull String pin) {
        if (pin.length() != 6) throw new IllegalArgumentException("PIN must be 6 characters");
        prefs.edit().putString(PREF_CUSTOM_PIN, hashPin(pin)).apply();
    }

    /** Verify a candidate PIN. Handles both legacy plaintext and hashed PINs. */
    public boolean verifyCustomPin(@NonNull String candidate) {
        String saved = prefs.getString(PREF_CUSTOM_PIN, null);
        if (saved == null) return false;
        if (saved.length() == 6) return saved.equals(candidate); // Legacy plaintext (pre-migration)
        return saved.equals(hashPin(candidate)); // Normal hashed comparison
    }

    /** Clear the stored custom PIN. */
    public void clearCustomPin() {
        prefs.edit().remove(PREF_CUSTOM_PIN).apply();
    }

    // ─── Authentication ──────────────────────────────────────────────────────

    public interface AuthCallback {
        void onSuccess();
        void onFailure(@Nullable String reason);
    }

    /**
     * Authenticate the user based on the current lock type.
     * For DEVICE: shows biometric / device-credential prompt.
     * For CUSTOM: caller must show the PIN dialog and call verifyCustomPin() itself,
     *             then call onSuccess()/onFailure() accordingly.
     *
     * @param activity  FragmentActivity host (needed for BiometricPrompt)
     * @param subtitle  Short description shown in the biometric prompt
     * @param callback  Result callback
     */
    public void authenticate(@NonNull FragmentActivity activity,
                             @NonNull String subtitle,
                             @NonNull AuthCallback callback) {
        if (!isLockEnabled()) {
             callback.onSuccess();
             return;
        }
        if (isDeviceLock()) {
            authenticateWithDevice(activity, subtitle, callback);
        } else {
            showVerifyPinDialog(activity, subtitle, verified -> {
                if (verified) callback.onSuccess();
                else {
                    callback.onFailure("Cancelled");
                }
            });
        }
    }

    // ─── Custom PIN UI Helpers ────────────────────────────────────────────────

    /**
     * Show a 6-box dialog to verify the existing custom PIN.
     * Includes brute-force lockout, and a "Forgot PIN" / "Cooldown Active" button.
     * Calls back with true if PIN matches, false otherwise.
     */
    public void showVerifyPinDialog(Context dialogContext, String subtitle, java.util.function.Consumer<Boolean> callback) {
        // Check lockout before showing dialog
        if (isPinLockedOut()) {
            long rem = getLockoutRemainingMs();
            Toast.makeText(dialogContext,
                    String.format("Too many failed attempts. Try again in %dm %ds.", rem / 60_000, (rem / 1000) % 60),
                    Toast.LENGTH_LONG).show();
            callback.accept(false);
            return;
        }

        // Determine Forgot PIN button label based on cooldown state
        ChallengeLockManager clm = new ChallengeLockManager(dialogContext);
        boolean cooldownActive = clm.isPinResetCooldownActive();
        String neutralLabel = cooldownActive ? "Cooldown Active" : "Forgot PIN";

        View pinView = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_pin_input, null);
        EditText[] boxes = getPinBoxes(pinView);
        android.widget.TextView errorText = pinView.findViewById(R.id.pin_error_text);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(dialogContext)
                .setTitle("Enter PIN")
                .setMessage(subtitle)
                .setView(pinView)
                .setCancelable(true)
                .setNegativeButton("Cancel", (d, w) -> d.cancel())
                .setNeutralButton(neutralLabel, (d, w) -> {
                    d.dismiss();
                    if (cooldownActive) {
                        // F4: Show remaining cooldown time instead of launching reset flow
                        long remainingMs = clm.getPinResetRemainingMs();
                        long hours = remainingMs / (60 * 60 * 1000L);
                        long mins  = (remainingMs / (60 * 1000L)) % 60;
                        Toast.makeText(dialogContext,
                                String.format("PIN reset cooldown active. %dh %dm remaining.", hours, mins),
                                Toast.LENGTH_LONG).show();
                    } else {
                        android.content.Intent intent = new android.content.Intent(
                                dialogContext, com.gxdevs.mindmint.Activities.LockChallengeActivity.class);
                        intent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_LOCK_TYPE, "pin_reset");
                        intent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_IS_SETTINGS_LOCK, true);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); // B8: guard for non-Activity contexts
                        dialogContext.startActivity(intent);
                    }
                })
                .setOnCancelListener(d -> callback.accept(false))
                .create();

        Runnable onComplete = () -> {
            String entered = collectPin(boxes);
            if (entered.length() == 6) {
                // Re-check lockout in case it changed during input
                if (isPinLockedOut()) {
                    long rem = getLockoutRemainingMs();
                    errorText.setText(String.format("Locked out. Try in %dm %ds.", rem / 60_000, (rem / 1000) % 60));
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                if (verifyCustomPin(entered)) {
                    clearFailedPinAttempts();
                    dialog.dismiss();
                    callback.accept(true);
                } else {
                    recordFailedPinAttempt();
                    int attLeft = getRemainingPinAttempts();
                    if (attLeft <= 0) {
                        long rem = getLockoutRemainingMs();
                        errorText.setText(String.format("Too many attempts! Locked for %dm %ds.", rem / 60_000, (rem / 1000) % 60));
                    } else {
                        errorText.setText("Incorrect PIN — " + attLeft + " attempt(s) left");
                    }
                    errorText.setVisibility(View.VISIBLE);
                    for (EditText b : boxes) if (b != null) b.setText("");
                    if (boxes[0] != null) boxes[0].requestFocus();
                }
            }
        };

        setupPinBoxNavigation(boxes, onComplete);

        dialog.show();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        if (boxes[0] != null) requestKeyboard(boxes[0]);
    }

    /**
     * Show a 6-box dialog to set (or change) the custom PIN.
     * 3-param overload that delegates to the 4-param version.
     *
     * @param isEdit      true = changing an existing PIN
     * @param onComplete  optional runnable after PIN is saved
     */
    public void showSetCustomPinDialog(Context dialogContext, boolean isEdit, Runnable onComplete) {
        showSetCustomPinDialog(dialogContext, isEdit, onComplete, null);
    }

    /**
     * Show a 6-box dialog to set (or change) the custom PIN.
     *
     * @param isEdit      true = changing an existing PIN
     * @param onComplete  optional runnable after PIN is saved
     * @param onCancel    optional runnable if the user cancels PIN setup
     */
    public void showSetCustomPinDialog(Context dialogContext, boolean isEdit, Runnable onComplete, Runnable onCancel) {
        // Step 1: enter new PIN
        View pinView1 = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_pin_input, null);
        EditText[] boxes1 = getPinBoxes(pinView1);
        android.widget.TextView errorText1 = pinView1.findViewById(R.id.pin_error_text);

        androidx.appcompat.app.AlertDialog step1 = new MaterialAlertDialogBuilder(dialogContext)
                .setTitle(isEdit ? "New PIN" : "Create PIN")
                .setMessage("Enter a 6-digit PIN")
                .setView(pinView1)
                .setCancelable(true)
                .setNegativeButton("Cancel", null)
                .setOnCancelListener(d -> { if (onCancel != null) onCancel.run(); })
                .create();
        step1.setOnShowListener(d -> step1.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener(v -> { step1.dismiss(); if (onCancel != null) onCancel.run(); }));

        Runnable onStep1Complete = () -> {
            String pin1 = collectPin(boxes1);
            if (pin1.length() < 6) return;
            step1.dismiss();

            // Step 2: confirm new PIN
            View pinView2 = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_pin_input, null);
            EditText[] boxes2 = getPinBoxes(pinView2);
            android.widget.TextView errorText2 = pinView2.findViewById(R.id.pin_error_text);

            androidx.appcompat.app.AlertDialog step2 = new MaterialAlertDialogBuilder(dialogContext)
                    .setTitle("Confirm PIN")
                    .setMessage("Re-enter your PIN to confirm")
                    .setView(pinView2)
                    .setCancelable(true)
                    .setNegativeButton("Back", (d, w) -> showSetCustomPinDialog(dialogContext, isEdit, onComplete, onCancel))
                    .setOnCancelListener(d -> { if (onCancel != null) onCancel.run(); })
                    .create();

            Runnable onStep2Complete = () -> {
                String pin2 = collectPin(boxes2);
                if (pin2.length() < 6) return;

                if (!pin1.equals(pin2)) {
                    errorText2.setText("PINs don't match — try again");
                    errorText2.setVisibility(View.VISIBLE);
                    for (EditText b : boxes2) if (b != null) b.setText("");
                    if (boxes2[0] != null) boxes2[0].requestFocus();
                    return;
                }
                saveCustomPin(pin1);
                step2.dismiss();
                Toast.makeText(dialogContext, "PIN saved", Toast.LENGTH_SHORT).show();
                if (onComplete != null) onComplete.run();
            };

            setupPinBoxNavigation(boxes2, onStep2Complete);
            step2.show();
            step2.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            if (boxes2[0] != null) requestKeyboard(boxes2[0]);
        };

        setupPinBoxNavigation(boxes1, onStep1Complete);
        step1.show();
        step1.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        if (boxes1[0] != null) requestKeyboard(boxes1[0]);
    }

    private String collectPin(EditText[] boxes) {
        StringBuilder sb = new StringBuilder();
        for (EditText box : boxes) {
            if (box != null && box.getText() != null) sb.append(box.getText().toString());
        }
        return sb.toString();
    }

    private EditText[] getPinBoxes(View pinView) {
        return new EditText[]{
                pinView.findViewById(R.id.pin_digit_1),
                pinView.findViewById(R.id.pin_digit_2),
                pinView.findViewById(R.id.pin_digit_3),
                pinView.findViewById(R.id.pin_digit_4),
                pinView.findViewById(R.id.pin_digit_5),
                pinView.findViewById(R.id.pin_digit_6)
        };
    }

    private void setupPinBoxNavigation(EditText[] boxes, Runnable onPinComplete) {
        for (int i = 0; i < boxes.length; i++) {
            final int current = i;
            final EditText box = boxes[i];
            if (box == null) continue;

            box.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && current < boxes.length - 1 && boxes[current + 1] != null) {
                        boxes[current + 1].requestFocus();
                    }
                    if (collectPin(boxes).length() == 6) {
                        if (onPinComplete != null) onPinComplete.run();
                    }
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
            });

            box.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DEL
                        && box.getText() != null
                        && box.getText().length() == 0
                        && current > 0
                        && boxes[current - 1] != null) {
                    boxes[current - 1].requestFocus();
                    boxes[current - 1].setText("");
                    return true;
                }
                return false;
            });
        }
    }

    private void requestKeyboard(EditText editText) {
        if (editText == null) return;
        editText.requestFocus();
        editText.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) editText.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    // ─── Biometric / Device credential ────────────────────────────────────────

    private void authenticateWithDevice(@NonNull FragmentActivity activity,
                                        @NonNull String subtitle,
                                        @NonNull AuthCallback callback) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Settings Lock")
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.BIOMETRIC_WEAK
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        callback.onFailure(errString.toString());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Do NOT call onFailure here — system shows its own retry UI.
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    // ─── Device lock availability ─────────────────────────────────────────────

    /** Returns true if the device has at least some biometric or credential enrolled. */
    public boolean isDeviceLockAvailable() {
        BiometricManager bm = BiometricManager.from(context);
        int result = bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }
}
