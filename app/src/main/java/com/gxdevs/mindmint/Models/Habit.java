package com.gxdevs.mindmint.Models;

import java.util.Date;

public class Habit {
    private String id;
    private String name;
    private String reason;
    private Difficulty difficulty;

    private DurationType durationType;
    private int durationDays; // used only when CHALLENGE_N_DAYS
    private java.util.Date untilDate; // optional end date when using UNTIL_DATE
    private String emoji; // user-selected emoji icon for this habit
    private GoodBad goodBad; // classify habit as good or bad

    private Date createdAt;
    private Date lastCompletedDate; // for daily check
    private int currentStreakDays;

    // Progress milestones index: 0=3d,1=1w,2=2w,3=1m,4=3m
    private int milestoneIndex;

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    public enum DurationType {
        CHALLENGE_N_DAYS,
        INDEFINITE,
        UNTIL_GOAL,
        UNTIL_DATE
    }

    public enum GoodBad {
        GOOD, BAD
    }

    public Habit() {
        this.id = generateId();
        this.createdAt = new Date();
        this.difficulty = Difficulty.MEDIUM;
        this.durationType = DurationType.INDEFINITE;
        this.durationDays = 30;
        this.currentStreakDays = 0;
        this.milestoneIndex = 0;
        this.emoji = "";
        this.goodBad = GoodBad.GOOD;
    }

    public Habit(String name, String reason, Difficulty difficulty, DurationType durationType, int durationDays) {
        this();
        this.name = name;
        this.reason = reason;
        this.difficulty = difficulty;
        this.durationType = durationType;
        this.durationDays = durationDays;
    }

    private String generateId() {
        return "habit_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public DurationType getDurationType() { return durationType; }
    public void setDurationType(DurationType durationType) { this.durationType = durationType; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
    public Date getCreatedAt() { return createdAt; }
    public int getCurrentStreakDays() { return currentStreakDays; }
    public int getMilestoneIndex() { return milestoneIndex; }
    public Date getLastCompletedDate() { return lastCompletedDate; }
    public Date getUntilDate() { return untilDate; }
    public void setUntilDate(Date untilDate) { this.untilDate = untilDate; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public GoodBad getGoodBad() { return goodBad; }
    public void setGoodBad(GoodBad goodBad) { this.goodBad = goodBad; }

    public boolean isDoneToday() {
        if (lastCompletedDate == null) return false;
        long now = System.currentTimeMillis();
        long startOfToday = now - (now % (24L * 60 * 60 * 1000));
        long endOfToday = startOfToday + (24L * 60 * 60 * 1000) - 1;
        return lastCompletedDate.getTime() >= startOfToday && lastCompletedDate.getTime() <= endOfToday;
    }

    public void markDoneToday() {
        Date today = new Date();
        if (!isDoneToday()) {
            currentStreakDays += 1;
            updateMilestone();
        }
        lastCompletedDate = today;
    }

    // Decrease progress when user unchecks today's completion
    public void unmarkToday() {
        if (isDoneToday()) {
            if (currentStreakDays > 0) currentStreakDays -= 1;
            // Reset lastCompletedDate to null to reflect not done today
            lastCompletedDate = null;
            updateMilestone();
        }
    }

    private void updateMilestone() {
        int[] goals = getDynamicGoals();
        int newIndex = 0;
        for (int i = goals.length - 1; i >= 0; i--) {
            if (currentStreakDays >= goals[i]) {
                newIndex = i;
                break;
            }
        }
        milestoneIndex = newIndex;
    }

    // Compute dynamic milestone targets progressing towards the final goal.
    // Starts: 3d → 1w(7) → 2w(14) → 4w(28) → 1.5m(45) → 2m(60) → then +30d steps until the final goal.
    private int[] getDynamicGoals() {
        int finalGoalDays = getTotalGoalDays();

        // Base ladder (in days)
        int[] base = new int[]{3, 7, 14, 28, 45, 60};

        // If final goal not defined, cap at 90 then continue monthly
        if (finalGoalDays <= 0) {
            finalGoalDays = 90; // sensible default
        }

        java.util.List<Integer> goals = new java.util.ArrayList<>();

        // Always include base steps that are <= finalGoalDays
        for (int g : base) {
            if (g <= finalGoalDays) goals.add(g);
        }

        // If we haven't reached finalGoalDays yet, continue adding monthly (+30d) steps
        int last = goals.isEmpty() ? 0 : goals.get(goals.size() - 1);
        if (last == 0) {
            // Ensure we have at least a first step (min of 3 or final)
            goals.add(Math.min(3, finalGoalDays));
            last = goals.get(goals.size() - 1);
        }

        while (last < finalGoalDays) {
            int next = last + 30;
            if (next > finalGoalDays) {
                next = finalGoalDays;
            }
            if (next > last) goals.add(next);
            last = next;
        }

        // Convert to array
        int[] arr = new int[goals.size()];
        for (int i = 0; i < goals.size(); i++) arr[i] = goals.get(i);
        return arr;
    }

    private int getTotalGoalDays() {
        // UNTIL_DATE: days from createdAt to untilDate
        if (durationType == DurationType.UNTIL_DATE && untilDate != null && createdAt != null) {
            long diffMs = untilDate.getTime() - createdAt.getTime();
            int days = (int) Math.max(0, Math.ceil(diffMs / (1000.0 * 60 * 60 * 24)));
            return Math.max(days, 1);
        }
        // CHALLENGE_N_DAYS: use durationDays
        if (durationType == DurationType.CHALLENGE_N_DAYS && durationDays > 0) {
            return durationDays;
        }
        // INDEFINITE/UNTIL_GOAL: fall back to a rolling long-term goal (e.g., 180 days)
        return 180;
    }

    public int getCurrentTargetDays() {
        int[] goals = getDynamicGoals();
        int idx = Math.min(Math.max(milestoneIndex, 0), goals.length - 1);
        return goals[idx];
    }

    public String getCurrentTargetLabel() {
        return formatTargetLabel(getCurrentTargetDays());
    }

    private String formatTargetLabel(int days) {
        if (days <= 0) return "";
        if (days < 7) return days + "d";
        if (days % 7 == 0 && days < 28) {
            int weeks = days / 7;
            return weeks + "w";
        }
        // Special-case common waypoints
        if (days == 28) return "4w";
        if (days == 45) return "1.5m";
        // Months approximation
        if (days % 30 == 0) {
            int months = days / 30;
            return months + "m";
        }
        // Fallback: round to one decimal month representation
        double monthsApprox = days / 30.0;
        double rounded = Math.round(monthsApprox * 2.0) / 2.0; // nearest .5
        if (Math.abs(rounded - Math.floor(rounded)) < 1e-6) {
            return ((int) rounded) + "m";
        } else {
            return rounded + "m";
        }
    }
}


