package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StreakPrefs {
    private static final String PREF_NAME = "streak_prefs";
    private static final String KEY_STREAK_COUNT = "streak_count";
    private static final String KEY_LAST_COMPLETED_DATE = "last_completed_date";
    private SharedPreferences prefs;

    public StreakPrefs(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getStreak() {
        return prefs.getInt(KEY_STREAK_COUNT, 0);
    }

    public void setStreak(int streak) {
        prefs.edit().putInt(KEY_STREAK_COUNT, streak).apply();
    }

    public String getLastCompletedDate() {
        return prefs.getString(KEY_LAST_COMPLETED_DATE, "");
    }

    public void setLastCompletedDate(String date) {
        prefs.edit().putString(KEY_LAST_COMPLETED_DATE, date).apply();
    }

    public static String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public static boolean isYesterday(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateStr);
            Date yesterday = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
            String yestStr = sdf.format(yesterday);
            return yestStr.equals(sdf.format(date));
        } catch (Exception e) {
            return false;
        }
    }
}
