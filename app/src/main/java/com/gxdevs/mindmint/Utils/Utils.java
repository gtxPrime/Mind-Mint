package com.gxdevs.mindmint.Utils;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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
    }

    // Method to calculate total usage time
    public static int calculateTotalUsageTime(SharedPreferences sharedPreferences, String tag) {
        int totalUsageTime = 0;
        // Always use ALL_PACKAGES to include both original and modded apps

        for (Map.Entry<String, String> entry : ALL_PACKAGES.entrySet()) {
            String packageName = entry.getKey();
            String packageTag = entry.getValue();

            if (tag.equals(packageTag)) {
                totalUsageTime += sharedPreferences.getInt(packageName + "_time", 0);
            }
        }

        return totalUsageTime;
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

//    private void updatePieChart() {
//        //ytCount.setText(String.valueOf(Utils.calculateTotalUsageScrolls(sharedPreferences, "yt")));
//        //instaCount.setText(String.valueOf(Utils.calculateTotalUsageScrolls(sharedPreferences, "insta")));
//        //snapCount.setText(String.valueOf(Utils.calculateTotalUsageScrolls(sharedPreferences, "snap")));
//        count.setText(String.valueOf(
//                Utils.calculateTotalUsageScrolls(sharedPreferences, "yt") +
//                        Utils.calculateTotalUsageScrolls(sharedPreferences, "insta") +
//                        Utils.calculateTotalUsageScrolls(sharedPreferences, "snap")
//        ));
//        List<Integer> data = new ArrayList<>();
//        data.add(Utils.calculateTotalUsageScrolls(sharedPreferences, "insta"));
//        data.add(Utils.calculateTotalUsageScrolls(sharedPreferences, "yt"));
//        data.add(Utils.calculateTotalUsageScrolls(sharedPreferences, "snap"));
//        List<String> segmentNames = new ArrayList<>();
//        segmentNames.add("insta");
//        segmentNames.add("yt");
//        segmentNames.add("snap");
//        int[] pieChartColors = new int[]{
//                ContextCompat.getColor(requireContext(), com.gxdevs.mindmint.R.color.sexyInsta),
//                ContextCompat.getColor(requireContext(), com.gxdevs.mindmint.R.color.sexyYt),
//                ContextCompat.getColor(requireContext(), com.gxdevs.mindmint.R.color.sexySnap)
//        };
//        //customPieChart.setData(data, segmentNames, pieChartColors);
//    }

}















