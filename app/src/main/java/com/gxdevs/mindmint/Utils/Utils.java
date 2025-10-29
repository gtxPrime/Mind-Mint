package com.gxdevs.mindmint.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;
import com.gxdevs.mindmint.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
    public static String YtViewId = "reel_watch_player";
    public static String instaViewId = "clips_video_container";
    public static String snapViewId = "favorite";
    public static final Map<String, String> ALL_PACKAGES = new HashMap<>();
    public static final Map<String, String> ORIGINAL_PACKAGES = new HashMap<>();
    public static final Map<String, String> BROWSERS_PACKAGES = new HashMap<>();
    public static final List<String> BLOCKED_URLS = new ArrayList<>();
    public static final List<String> FULL_BLOCKED_URLS = new ArrayList<>();

    static {
        // Initialize the dictionary with both mod and original packages
        ALL_PACKAGES.put("com.myinsta.android", "insta");
        ALL_PACKAGES.put("com.rvx.android.youtube", "yt");
        ALL_PACKAGES.put("com.revance.android.youtube", "yt");
        ALL_PACKAGES.put("com.snapchat.android", "snap");
        ALL_PACKAGES.put("com.instagram.android", "insta");
        ALL_PACKAGES.put("com.google.android.youtube", "yt");
        ALL_PACKAGES.put("com.instafel.android", "insta");
        ALL_PACKAGES.put("com.instander.android", "insta");
        ALL_PACKAGES.put("com.instagold.android", "insta");
        ALL_PACKAGES.put("com.instaflow.android", "insta");
        ALL_PACKAGES.put("cc.honista.app", "insta");

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
        BROWSERS_PACKAGES.put("org.mozilla.firefox", "navigation_bar");
        BROWSERS_PACKAGES.put("org.mozilla.firefox_beta", "navigation_bar");
        BROWSERS_PACKAGES.put("org.mozilla.fenix", "navigation_bar"); // Firefox Nightly
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

        // Default blocked URLs for browser doom-scrolling (customize later)
        BLOCKED_URLS.add("m.youtube.com/shorts");
        BLOCKED_URLS.add("instagram.com/reel");
        BLOCKED_URLS.add("snapchat.com/spotlight");

        FULL_BLOCKED_URLS.add("instagram");
        FULL_BLOCKED_URLS.add("youtube");
        FULL_BLOCKED_URLS.add("snapchat");
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
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

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

    public static void applyAccentColors(View arc1View, View arc2View, View arc3View, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString("pref_theme_mode", "Dark Theme");

        boolean isDark;
        if ("Dark Theme".equalsIgnoreCase(mode) || "dark".equalsIgnoreCase(mode)) {
            isDark = true;
        } else if ("Light Theme".equalsIgnoreCase(mode) || "light".equalsIgnoreCase(mode)) {
            isDark = false;
        } else {
            int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDark = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
        }

        boolean advanced = prefs.getBoolean("pref_theme_advanced_enabled", false);

        int c1, c2, c3;

        if (advanced) {
            c1 = getColorPref(prefs, "pref_theme_color_1", isDark, "#59FD7D81", "#B4FD7D81");
            c2 = getColorPref(prefs, "pref_theme_color_2", isDark, "#2614B8A6", "#8014B8A6");
            c3 = getColorPref(prefs, "pref_theme_color_3", isDark, "#33A78BFA", "#D9A78BFA");
        } else if (isDark) {
            c1 = Color.parseColor("#59FD7D81");
            c2 = Color.parseColor("#2614B8A6");
            c3 = Color.parseColor("#33A78BFA");
        } else {
            c1 = Color.parseColor("#B4FD7D81");
            c2 = Color.parseColor("#8014B8A6");
            c3 = Color.parseColor("#D9A78BFA");
        }

        if (arc1View != null)
            ViewCompat.setBackgroundTintList(arc1View, ColorStateList.valueOf(c1));
        if (arc2View != null)
            ViewCompat.setBackgroundTintList(arc2View, ColorStateList.valueOf(c2));
        if (arc3View != null)
            ViewCompat.setBackgroundTintList(arc3View, ColorStateList.valueOf(c3));
    }

    private static int getColorPref(SharedPreferences prefs, String key, boolean isDark, String darkDefault, String lightDefault) {
        if (prefs.contains(key)) {
            return prefs.getInt(key, 0);
        } else {
            return Color.parseColor(isDark ? darkDefault : lightDefault);
        }
    }

    public static void applySecondaryColor(MaterialCardView view, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean advanced = prefs.getBoolean("pref_theme_advanced_enabled", false);
        int cSecondary;
        if (advanced) {
            cSecondary = prefs.getInt("pref_theme_color_secondary", resolveAttrColor(context, R.attr.secondaryColor));
        } else {
            cSecondary = resolveAttrColor(context, R.attr.secondaryColor);
        }
        view.setCardBackgroundColor(cSecondary);
    }

    private static int resolveAttrColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    public static int getEffectiveSecondaryColor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean advanced = prefs.getBoolean("pref_theme_advanced_enabled", false);
        int cSecondary;
        if (advanced) {
            cSecondary = prefs.getInt("pref_theme_color_secondary", resolveAttrColor(context, R.attr.secondaryColor));
        } else {
            cSecondary = resolveAttrColor(context, R.attr.secondaryColor);
        }
        return cSecondary;
    }

    public static void applyAppThemeFromPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString("pref_theme_mode", "Dark Theme");
        if ("Dark Theme".equalsIgnoreCase(mode) || "dark".equalsIgnoreCase(mode)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("Light Theme".equalsIgnoreCase(mode) || "light".equalsIgnoreCase(mode)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}















