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

    private final SharedPreferences prefs;
    private final Context context;

    public SettingsLockManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = PreferenceManager.getDefaultSharedPreferences(this.context);
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
        return pin != null && pin.length() == 6;
    }

    /** Save a new custom PIN (must be exactly 6 characters). */
    public void saveCustomPin(@NonNull String pin) {
        if (pin.length() != 6) throw new IllegalArgumentException("PIN must be 6 characters");
        prefs.edit().putString(PREF_CUSTOM_PIN, pin).apply();
    }

    /** Verify a candidate PIN (case-sensitive). */
    public boolean verifyCustomPin(@NonNull String candidate) {
        String saved = prefs.getString(PREF_CUSTOM_PIN, null);
        return saved != null && saved.equals(candidate);
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
     * Calls back with true if PIN matches, false otherwise.
     */
    public void showVerifyPinDialog(Context dialogContext, String subtitle, java.util.function.Consumer<Boolean> callback) {
        View pinView = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_pin_input, null);
        EditText[] boxes = getPinBoxes(pinView);
        android.widget.TextView errorText = pinView.findViewById(R.id.pin_error_text);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(dialogContext)
                .setTitle("Enter PIN")
                .setMessage(subtitle)
                .setView(pinView)
                .setCancelable(true)
                .setNegativeButton("Cancel", (d, w) -> d.cancel())
                .setNeutralButton("Forgot PIN", (d, w) -> {
                    d.dismiss();
                    android.content.Intent intent = new android.content.Intent(dialogContext, com.gxdevs.mindmint.Activities.LockChallengeActivity.class);
                    intent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_LOCK_TYPE, "pin_reset");
                    intent.putExtra(com.gxdevs.mindmint.Activities.LockChallengeActivity.EXTRA_IS_SETTINGS_LOCK, true);
                    dialogContext.startActivity(intent);
                })
                .setOnCancelListener(d -> callback.accept(false))
                .create();

        Runnable onComplete = () -> {
            String entered = collectPin(boxes);
            if (entered.length() == 6) {
                if (verifyCustomPin(entered)) {
                    dialog.dismiss();
                    callback.accept(true);
                } else {
                    errorText.setText("Incorrect PIN");
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
     *
     * @param isEdit      true = changing an existing PIN (no need to verify old one; caller handles that)
     * @param onComplete  optional runnable after PIN is saved
     */
    public void showSetCustomPinDialog(Context dialogContext, boolean isEdit, Runnable onComplete) {
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
                .create();

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
                    .setNegativeButton("Back", (d, w) -> showSetCustomPinDialog(dialogContext, isEdit, onComplete))
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
