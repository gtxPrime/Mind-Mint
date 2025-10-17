package com.gxdevs.mindmint.Utils;

import android.content.Context;

import com.gxdevs.mindmint.Models.Habit;

import java.util.List;

public class StreakManager {

    private final StreakPrefs streakPrefs;
    
    public StreakManager(Context context) {
        this.streakPrefs = new StreakPrefs(context);
    }

    public void updateStreakOnHabitCompletion() {
        String today = StreakPrefs.getTodayDateString();
        String lastDate = streakPrefs.getLastCompletedDate();
        
        // Only update streak if we haven't already updated it today
        if (!today.equals(lastDate)) {
            int newStreak = 1;
            // If last completion was yesterday, increment the streak
            if (StreakPrefs.isYesterday(lastDate)) {
                newStreak = streakPrefs.getStreak() + 1;
            }
            streakPrefs.setStreak(newStreak);
            streakPrefs.setLastCompletedDate(today);
        }
    }

    public void checkAndResetStreakIfNeeded(List<Habit> allHabits) {
        boolean anyCompletedToday = false;
        
        for (Habit habit : allHabits) {
            if (habit.isDoneToday()) {
                anyCompletedToday = true;
                break;
            }
        }
        
        // If no habits are completed today, decrement streak by 1 for undoing today's completion.
        // If a day is actually missed (handled elsewhere via HomeActivity), it will reset.
        if (!anyCompletedToday) {
            int current = streakPrefs.getStreak();
            int newValue = Math.max(0, current - 1);
            streakPrefs.setStreak(newValue);
            // Clear last completed date for today since no habits remain completed
            streakPrefs.setLastCompletedDate("");
        }
    }

    public int getCurrentStreak() {
        return streakPrefs.getStreak();
    }
}

