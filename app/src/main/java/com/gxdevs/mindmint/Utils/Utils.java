package com.gxdevs.mindmint.Utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.gxdevs.mindmint.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@SuppressLint("BatteryLife")
public class Utils {
    public static String YtViewId = "reel_watch_player";
    public static String instaViewId = "clips_video_container";
    public static String snapViewId = "favorite";
    public static final Map<String, String> ALL_PACKAGES = new HashMap<>();
    public static final Map<String, String> ORIGINAL_PACKAGES = new HashMap<>();
    public static final Map<String, String> BROWSERS_PACKAGES = new HashMap<>();
    public static final List<String> browserIds = new ArrayList<>();

    static {
        // Initialize the dictionary with both mod and original packages
        ALL_PACKAGES.put("com.myinsta.android", "insta");
        ALL_PACKAGES.put("com.rvx.android.youtube", "yt");
        ALL_PACKAGES.put("app.morphe.android.youtube", "yt");
        ALL_PACKAGES.put("com.revance.android.youtube", "yt");
        ALL_PACKAGES.put("com.snapchat.android", "snap");
        ALL_PACKAGES.put("com.instagram.android", "insta");
        ALL_PACKAGES.put("com.google.android.youtube", "yt");
        ALL_PACKAGES.put("com.instafel.android", "insta");
        ALL_PACKAGES.put("com.instander.android", "insta");
        ALL_PACKAGES.put("com.instagold.android", "insta");
        ALL_PACKAGES.put("com.instapro2.android", "insta");
        ALL_PACKAGES.put("com.instaflow.android", "insta");
        ALL_PACKAGES.put("cc.honista.app", "insta");
        ALL_PACKAGES.put("com.instaprime.android", "insta");

        // Initialize the dictionary with only original packages
        ORIGINAL_PACKAGES.put("com.instagram.android", "insta");
        ORIGINAL_PACKAGES.put("com.google.android.youtube", "yt");
        ORIGINAL_PACKAGES.put("com.snapchat.android", "snap");

        // Chrome browsers
        BROWSERS_PACKAGES.put("com.android.chrome", "url_bar");
        BROWSERS_PACKAGES.put("com.chrome.beta", "url_bar");
        BROWSERS_PACKAGES.put("com.chrome.dev", "url_bar");
        BROWSERS_PACKAGES.put("com.chrome.canary", "url_bar");

        // Firefox browsers
        BROWSERS_PACKAGES.put("org.mozilla.firefox", "mozac_browser_toolbar_url_view");
        BROWSERS_PACKAGES.put("org.mozilla.firefox_beta", "mozac_browser_toolbar_url_view");
        BROWSERS_PACKAGES.put("org.mozilla.fenix", "mozac_browser_toolbar_url_view"); // Firefox Nightly
        BROWSERS_PACKAGES.put("org.mozilla.focus", "mozac_browser_toolbar_url_view"); // Firefox Focus

        // Microsoft Edge browsers
        BROWSERS_PACKAGES.put("com.microsoft.emmx", "url_bar");
        BROWSERS_PACKAGES.put("com.microsoft.emmx.beta", "url_bar");
        BROWSERS_PACKAGES.put("com.microsoft.emmx.dev", "url_bar");
        BROWSERS_PACKAGES.put("com.microsoft.emmx.canary", "url_bar");

        // Samsung Internet
        BROWSERS_PACKAGES.put("com.sec.android.app.sbrowser", "location_bar_edit_text");

        // OnePlus Browser
        BROWSERS_PACKAGES.put("com.heytap.browser", "web_title"); // OnePlus (shared with OPPO/Realme)

        // Xiaomi browsers
        BROWSERS_PACKAGES.put("com.android.browser", "url_bar"); // Mi Browser (system)
        BROWSERS_PACKAGES.put("com.mi.globalbrowser.mini", "url_bar"); // Mint Browser

        // Nothing Browser
        BROWSERS_PACKAGES.put("com.nothing.browser", "url_bar");

        // Opera browsers
        BROWSERS_PACKAGES.put("com.opera.browser", "url_field");
        BROWSERS_PACKAGES.put("com.opera.mini.native", "url_field");
        BROWSERS_PACKAGES.put("com.opera.gx", "url_field");

        // Brave browsers
        BROWSERS_PACKAGES.put("com.brave.browser", "url_bar");
        BROWSERS_PACKAGES.put("com.brave.browser_beta", "url_bar");
        BROWSERS_PACKAGES.put("com.brave.browser_nightly", "url_bar");

        //Extras
        BROWSERS_PACKAGES.put("org.torproject.torbrowser", "mozac_browser_toolbar_url_view");
        BROWSERS_PACKAGES.put("com.duckduckgo.mobile.android", "omnibarTextInput");
        BROWSERS_PACKAGES.put("idm.internet.download.manager", "search");

        browserIds.add("url_bar");
        browserIds.add("omnibarTextInput");
        browserIds.add("mozac_browser_toolbar_url_view");
        browserIds.add("url_field");
        browserIds.add("web_title");
        browserIds.add("url_bar");
        browserIds.add("location_bar_edit_text");
        browserIds.add("search");
        browserIds.add("url");
        browserIds.add("search_content");
        browserIds.add("edit_text");
    }

    public static int calculateTotalUsageScrolls(SharedPreferences sharedPreferences, String tag) {
        long totalScrolls = 0;
        for (Map.Entry<String, String> entry : ALL_PACKAGES.entrySet()) {
            String packageName = entry.getKey();
            String packageTag = entry.getValue();

            if (tag.equals(packageTag)) {
                totalScrolls += sharedPreferences.getLong(packageName + "_scrolls", 0L);
            }
        }
        return (int) totalScrolls;
    }

    public static void setPad(View view, String angle, Activity context) {
        WindowCompat.setDecorFitsSystemWindows(context.getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());

            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (angle.equals("top")) {
                mlp.topMargin = insets.top;
            } else if (angle.equals("bottom")) {
                mlp.bottomMargin = insets.bottom;
            }

            v.setLayoutParams(mlp);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static void applyAppThemeFromPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString("pref_theme_mode", "Dark Theme");
        if ("Dark Theme".equalsIgnoreCase(mode) || "dark".equalsIgnoreCase(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("Light Theme".equalsIgnoreCase(mode) || "light".equalsIgnoreCase(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static boolean isAccessibilityPermissionGranted(Context context) {
        String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String packageName = context.getPackageName();
        return enabledServices != null && enabledServices.contains(packageName);
    }

    public static boolean isKeepAlive(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("keepServiceAlive", false);
    }

    public static int dpToPx(float dp, Context context) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public enum PermissionType {
        ACCESSIBILITY,
        NOTIFICATION,
        ALARM,
        BATTERY,
        AUDIO
    }

    private static class Step {
        String label;
        String highlight;
        int iconRes;

        Step(String label, String highlight, int iconRes) {
            this.label = label;
            this.highlight = highlight;
            this.iconRes = iconRes;
        }

        Step(String label, String highlight) {
            this(label, highlight, 0);
        }
    }

    private static class PermissionConfig {
        String colorHex;
        int mainIconRes;
        String title;
        String description;
        List<Step> steps;
        String trustTitle;
        String trustDesc;
        String buttonText;

        PermissionConfig(String colorHex, int mainIconRes, String title, String description, List<Step> steps, String trustTitle, String trustDesc, String buttonText) {
            this.colorHex = colorHex;
            this.mainIconRes = mainIconRes;
            this.title = title;
            this.description = description;
            this.steps = steps;
            this.trustTitle = trustTitle;
            this.trustDesc = trustDesc;
            this.buttonText = buttonText;
        }
    }

    public interface PermissionLauncher {
        void launchAccessibility(Intent intent);
        void launchBattery(Intent intent);
        void launchNotification(String permission);
    }

    public static void showPermissionSheet(Context context, PermissionType type, PermissionLauncher launcher, Runnable onCancel) {
            View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_permission, null);
            BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.CustomBottomSheetTheme);
            dialog.setContentView(view);

            PermissionConfig config = getConfigForType(type);
            int themeColor = Color.parseColor(config.colorHex);

            ImageView ivMainIcon = view.findViewById(R.id.ivMainIcon);
            TextView tvTitle = view.findViewById(R.id.tvTitle);
            TextView tvDesc = view.findViewById(R.id.tvDesc);

            ivMainIcon.setImageResource(config.mainIconRes);
            ivMainIcon.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);

            tvTitle.setText(config.title);
            tvDesc.setText(config.description);

            // Style trust badge
            LinearLayout trustBadge = view.findViewById(R.id.trustBadge);
            ImageView ivTrustIcon = view.findViewById(R.id.ivTrustIcon);
            TextView tvTrustTitle = view.findViewById(R.id.tvTrustTitle);
            TextView tvTrustDesc = view.findViewById(R.id.tvTrustDesc);

            trustBadge.setBackgroundTintList(ColorStateList.valueOf(adjustAlpha(themeColor, 0.1f)));
            ivTrustIcon.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);

            tvTrustTitle.setText(config.trustTitle);
            tvTrustTitle.setTextColor(themeColor);
            tvTrustDesc.setText(config.trustDesc);
            tvTrustDesc.setTextColor(adjustAlpha(themeColor, 0.8f));

            // Style buttons
            TextView btnProceed = view.findViewById(R.id.btnProceed);
            TextView btnCancel = view.findViewById(R.id.btnCancel);

            btnProceed.setText(config.buttonText);
            btnProceed.setBackgroundTintList(ColorStateList.valueOf(themeColor));
            btnProceed.setTextColor(Color.WHITE);

            btnProceed.setOnClickListener(v -> {
                dialog.dismiss();
                handlePermissionRequestWithLauncher(context, type, launcher);
            });

            btnCancel.setOnClickListener(v -> {
                dialog.dismiss();
                if (onCancel != null) {
                    onCancel.run();
                }
            });

            // Setup steps
            int[] stepLayoutIds = {R.id.layoutStep1, R.id.layoutStep2, R.id.layoutStep3};
            for (int i = 0; i < stepLayoutIds.length; i++) {
                View stepView = view.findViewById(stepLayoutIds[i]);

                if (i < config.steps.size()) {
                    stepView.setVisibility(View.VISIBLE);
                    Step stepData = config.steps.get(i);

                    TextView tvStepNum = stepView.findViewById(R.id.tvStepNum);
                    TextView tvStepLabel = stepView.findViewById(R.id.tvStepLabel);
                    TextView tvStepAction = stepView.findViewById(R.id.tvStepAction);
                    ImageView ivStepIcon = stepView.findViewById(R.id.ivStepIcon);

                    tvStepNum.setText(String.valueOf(i + 1));
                    tvStepNum.setTextColor(themeColor);
                    tvStepNum.setBackgroundTintList(ColorStateList.valueOf(adjustAlpha(themeColor, 0.15f)));

                    tvStepLabel.setText(stepData.label);
                    tvStepAction.setText(stepData.highlight);

                    if (stepData.iconRes != 0) {
                        ivStepIcon.setVisibility(View.VISIBLE);
                        ivStepIcon.setImageResource(stepData.iconRes);
                        ivStepIcon.setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);
                    } else {
                        ivStepIcon.setVisibility(View.GONE);
                    }
                } else {
                    stepView.setVisibility(View.GONE);
                }
            }

            dialog.show();
        }

        private static void handlePermissionRequestWithLauncher(Context context, PermissionType type, PermissionLauncher launcher) {
            try {
                switch (type) {
                    case ACCESSIBILITY:
                        Intent accessibilityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        if (launcher != null) {
                            launcher.launchAccessibility(accessibilityIntent);
                        } else {
                            context.startActivity(accessibilityIntent);
                        }
                        break;

                    case NOTIFICATION:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (launcher != null) {
                                launcher.launchNotification(Manifest.permission.POST_NOTIFICATIONS);
                            } else {
                                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                                context.startActivity(intent);
                            }
                        }
                        break;

                    case ALARM:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            intent.setData(Uri.parse("package:" + context.getPackageName()));
                            context.startActivity(intent);
                        }
                        break;

                    case BATTERY:
                        Intent batteryIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        batteryIntent.setData(Uri.parse("package:" + context.getPackageName()));
                        if (launcher != null) {
                            launcher.launchBattery(batteryIntent);
                        } else {
                            context.startActivity(batteryIntent);
                        }
                        break;

                    case AUDIO:
                        if (launcher != null) {
                            launcher.launchNotification(Manifest.permission.RECORD_AUDIO);
                        } else if (context instanceof Activity) {
                            androidx.core.app.ActivityCompat.requestPermissions((Activity) context,
                                    new String[]{Manifest.permission.RECORD_AUDIO}, 401);
                        }
                        break;
                }
            } catch (Exception e) {
                Intent fallbackIntent = new Intent(Settings.ACTION_SETTINGS);
                context.startActivity(fallbackIntent);
            }
        }

        private static PermissionConfig getConfigForType(PermissionType type) {
            return switch (type) {
                case ACCESSIBILITY -> new PermissionConfig(
                        "#FF6B6B", // Brand Pink
                        R.drawable.shield_check, // Replace with R.drawable.ic_shield_check
                        "Enable Monk Mode",
                        "To detect scrolling and block apps, Mind Mint needs Accessibility Service access.",
                        Arrays.asList(
                                new Step("Tap button below", "Proceed"),
                                new Step("Select menu", "Installed Apps", android.R.drawable.ic_menu_manage),
                                new Step("Find & Toggle ON", "Mind Mint", android.R.drawable.ic_menu_view)
                        ),
                        "PRIVACY GUARANTEE",
                        "We strictly process data locally. No personal data collection.",
                        "Proceed to Settings"
                );
                case NOTIFICATION -> new PermissionConfig(
                        "#3B82F6", // Blue
                        R.drawable.bell, // Replace with R.drawable.ic_bell_ring
                        "Stay in the Loop",
                        "Get notified when your habit streaks are at risk or when a focus session completes.",
                        Arrays.asList(
                                new Step("Tap button below", "Enable"),
                                new Step("System Dialog", "Click 'Allow'", android.R.drawable.ic_dialog_alert)
                        ),
                        "NO SPAM PROMISE",
                        "We only send notifications for the goals you explicitly set.",
                        "Enable Notifications"
                );
                case ALARM -> new PermissionConfig(
                        "#F59E0B", // Amber
                        R.drawable.alarm, // Replace with R.drawable.ic_clock
                        "Exact Alarms",
                        "To ensure your focus sessions end exactly on time, we need permission to set exact alarms.",
                        Arrays.asList(
                                new Step("Tap button below", "Allow Alarms"),
                                new Step("Toggle Switch", "Allow setting alarms", android.R.drawable.btn_radio)
                        ),
                        "PRECISION TIMING",
                        "Required for reliable timer notifications even when locked.",
                        "Allow Alarms"
                );
                case BATTERY -> new PermissionConfig(
                        "#10B981", // Emerald
                        R.drawable.battery, // Replace with R.drawable.ic_battery_warning
                        "Run in Background",
                        "Prevent the system from killing the timer when you switch apps.",
                        Arrays.asList(
                                new Step("Select Filter", "All Apps"),
                                new Step("Find Mind Mint", "Don't Optimize", android.R.drawable.ic_menu_search)
                        ),
                        "SYSTEM STABILITY",
                        "Ensures your block sessions aren't interrupted by OS cleanup.",
                        "Ignore Optimization"
                );
                case AUDIO -> new PermissionConfig(
                        "#8B5CF6", // Purple
                        R.drawable.bell,
                        "Microphone Access",
                        "To use the Scream challenge to unlock restricted goals, Mind Mint needs audio recording permission.",
                        Arrays.asList(
                                new Step("Tap button below", "Grant"),
                                new Step("System Dialog", "Click 'Allow'", android.R.drawable.ic_dialog_alert)
                        ),
                        "AUDIO PRIVACY",
                        "We do not store or transmit any sound. Volume levels are processed locally.",
                        "Allow Mic Access"
                );
            };
        }

        private static int adjustAlpha(int color, float factor) {
            int alpha = Math.round(Color.alpha(color) * factor);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            return Color.argb(alpha, red, green, blue);
        }
}