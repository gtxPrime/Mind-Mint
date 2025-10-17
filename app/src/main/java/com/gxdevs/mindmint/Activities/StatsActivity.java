package com.gxdevs.mindmint.Activities;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.tabs.TabLayout;
import com.gxdevs.mindmint.Components.CustomPieChart;
import com.gxdevs.mindmint.Components.RoundedBarChart;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.TaskManager;
import com.gxdevs.mindmint.Utils.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

// Custom renderer for rounded bars with gradient
public class StatsActivity extends AppCompatActivity {
    private int currentWeekOffset = 0;
    private int currentMonthOffset = 0;
    private boolean isWeekly = true;
    public int startColor;
    public int endColor;
    private ImageView brain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        // Setup Pie Chart for scrolls
        CustomPieChart pieChart = findViewById(R.id.pieChart);
        BlurTarget blurTarget = findViewById(R.id.blurTarget);
        BlurView focusBlur = findViewById(R.id.focusBlur);
        BlurView taskBlur = findViewById(R.id.taskBlur);
        BlurView habitBlur = findViewById(R.id.habitBlur);
        brain = findViewById(R.id.brainImage);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int instaScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "insta");
        int ytScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "yt");
        int snapScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "snap");
        List<Integer> data = new ArrayList<>();
        data.add(instaScrolls);
        data.add(ytScrolls);
        data.add(snapScrolls);
        List<String> segmentNames = new ArrayList<>();
        segmentNames.add("Instagram");
        segmentNames.add("YouTube");
        segmentNames.add("Snapchat");
        int[] pieChartColors = new int[]{
                ContextCompat.getColor(this, R.color.sexyInsta),
                ContextCompat.getColor(this, R.color.sexyYt),
                ContextCompat.getColor(this, R.color.sexySnap)
        };
        pieChart.setData(data, segmentNames, pieChartColors);

        startColor = ContextCompat.getColor(this, R.color.barGradient1);
        endColor = ContextCompat.getColor(this, R.color.barGradient2);

        // Setup TabLayout for weekly/monthly
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText("Weekly"));
        tabLayout.addTab(tabLayout.newTab().setText("Monthly"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isWeekly = tab.getPosition() == 0;
                updateAllCharts();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Setup navigation arrows
        findViewById(R.id.arrowLeft).setOnClickListener(v -> {
            if (isWeekly) currentWeekOffset--;
            else currentMonthOffset--;
            updateAllCharts();
        });
        findViewById(R.id.arrowRight).setOnClickListener(v -> {
            if (isWeekly) currentWeekOffset++;
            else currentMonthOffset++;
            updateAllCharts();
        });

        updateAllCharts();
        updateBrainImage(ytScrolls + instaScrolls + snapScrolls);

        focusBlur.setupWith(blurTarget).setBlurRadius(10f).setOverlayColor(Color.parseColor("#331E1E2A"));
        taskBlur.setupWith(blurTarget).setBlurRadius(10f).setOverlayColor(Color.parseColor("#331E1E2A"));
        habitBlur.setupWith(blurTarget).setBlurRadius(10f).setOverlayColor(Color.parseColor("#331E1E2A"));

        findViewById(R.id.backButton).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void updateAllCharts() {
        updatePeriodLabel();
        updateFocusBarChart();
        updateTasksBarChart();
        updateHabitsBarChart();
    }

    private void updateBrainImage(int totalWastedScrolls) {
        int drawableId = getDrawableId(totalWastedScrolls);
        brain.setImageResource(drawableId);
    }

    private static int getDrawableId(int totalWastedScrolls) {
        int drawableId;
        if (totalWastedScrolls < 150) {
            drawableId = R.drawable.brain1;
        } else if (totalWastedScrolls < 300) {
            drawableId = R.drawable.brain2;
        } else if (totalWastedScrolls < 500) {
            drawableId = R.drawable.brain3;
        } else if (totalWastedScrolls < 700) {
            drawableId = R.drawable.brain4;
        } else if (totalWastedScrolls < 900) {
            drawableId = R.drawable.brain5;
        } else if (totalWastedScrolls < 1100) {
            drawableId = R.drawable.brain6;
        } else if (totalWastedScrolls < 1200) {
            drawableId = R.drawable.brain7;
        } else if (totalWastedScrolls < 1400) {
            drawableId = R.drawable.brain8;
        } else {
            drawableId = R.drawable.brain9;
        }
        return drawableId;
    }

    private void updatePeriodLabel() {
        TextView label = findViewById(R.id.periodLabel);
        Calendar cal = Calendar.getInstance();
        if (isWeekly) {
            cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
            int week = cal.get(Calendar.WEEK_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            label.setText("Week " + week + ", " + year);
        } else {
            cal.add(Calendar.MONTH, currentMonthOffset);
            int month = cal.get(Calendar.MONTH);
            int year = cal.get(Calendar.YEAR);
            String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime());
            label.setText(monthName + " " + year);
        }
    }

    private void updateFocusBarChart() {
        RoundedBarChart chart = findViewById(R.id.focusBarChart);
        List<String> labels = new ArrayList<>();
        List<BarEntry> entries = new ArrayList<>();

        Map<String, Long> focusData = getFocusStatsForCurrentPeriod();

        float totalSeconds = 0;
        int i = 0;
        for (String key : focusData.keySet()) {
            labels.add(key);
            long seconds = focusData.get(key);
            totalSeconds += seconds;
            entries.add(new BarEntry(i++, seconds / 3600f)); // hours
        }

        // Update heading
        TextView focusHeading = findViewById(R.id.focusHeading);
        Calendar cal = Calendar.getInstance();
        if (isWeekly) {
            cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
            int week = cal.get(Calendar.WEEK_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            focusHeading.setText("Focused in Week " + week + ", " + year);
        } else {
            cal.add(Calendar.MONTH, currentMonthOffset);
            String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime());
            int year = cal.get(Calendar.YEAR);
            focusHeading.setText("Focused in " + monthName + " " + year);
        }

        // Update total
        TextView totalFocus = findViewById(R.id.totalFocus);
        float totalHours = totalSeconds / 3600f;
        totalFocus.setText(String.format(Locale.getDefault(), "%.1fh of Focus", totalHours));

        BarDataSet dataSet = new BarDataSet(entries, "Focus (hrs)");
        dataSet.setDrawValues(false);
        dataSet.setColor(ContextCompat.getColor(this, R.color.barGradient1));
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.45f);
        chart.setData(barData);

        //X Axis
        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        x.setTextColor(Color.WHITE);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);

        //Y Axis
        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.LTGRAY);
        left.setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);

        //Chart styling
        chart.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setExtraOffsets(8, 8, 8, 8);
        chart.animateY(700, Easing.EaseInOutQuad);
        chart.invalidate();
    }

    private Map<String, Long> getFocusStatsForCurrentPeriod() {
        Map<String, Long> result = new LinkedHashMap<>();
        SharedPreferences statsPrefs = getSharedPreferences("FOCUS_STATS_PREFS", MODE_PRIVATE);
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        if (isWeekly) {
            cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            for (int i = 0; i < 7; i++) {
                String key = fmt.format(cal.getTime());
                long val = statsPrefs.getLong("focus_time_" + key, 0);
                result.put(new SimpleDateFormat("EEE", Locale.getDefault()).format(cal.getTime()), val);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else {
            cal.add(Calendar.MONTH, currentMonthOffset);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int i = 0; i < days; i++) {
                String key = fmt.format(cal.getTime());
                long val = statsPrefs.getLong("focus_time_" + key, 0);
                result.put(String.valueOf(i + 1), val);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return result;
    }

    private void updateTasksBarChart() {
        RoundedBarChart chart = findViewById(R.id.tasksBarChart);
        List<String> labels = new ArrayList<>();
        List<BarEntry> entries = new ArrayList<>();

        Map<String, Integer> taskData = getTaskStatsForCurrentPeriod();
        //Map<String, Integer> taskData = getDemoData();

        int totalTasks = 0;
        int i = 0;
        for (String key : taskData.keySet()) {
            labels.add(key);
            int count = taskData.get(key);
            totalTasks += count;
            entries.add(new BarEntry(i++, count));
        }

        // Update heading
        TextView taskHeading = findViewById(R.id.taskHeading);
        Calendar cal = Calendar.getInstance();
        if (isWeekly) {
            cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
            int week = cal.get(Calendar.WEEK_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            taskHeading.setText("Tasks completed in Week " + week + ", " + year);
        } else {
            cal.add(Calendar.MONTH, currentMonthOffset);
            String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime());
            int year = cal.get(Calendar.YEAR);
            taskHeading.setText("Tasks completed in " + monthName + " " + year);
        }

        // Update total
        TextView totalTask = findViewById(R.id.totalTask);
        totalTask.setText(totalTasks + " Task" + (totalTasks != 1 ? "s" : ""));

        BarDataSet dataSet = new BarDataSet(entries, "Tasks Done");
        dataSet.setDrawValues(false);
        dataSet.setColor(ContextCompat.getColor(this, R.color.barGradient1));
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.45f);
        chart.setData(barData);

        //X Axis
        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        x.setTextColor(Color.WHITE);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);

        //Y Axis
        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.LTGRAY);
        left.setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);

        //Chart styling
        chart.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setExtraOffsets(8, 8, 8, 8);
        chart.animateY(700, Easing.EaseInOutQuad);
        chart.invalidate();
    }

    private Map<String, Integer> getTaskStatsForCurrentPeriod() {
        Map<String, Integer> result = new LinkedHashMap<>();
        TaskManager taskManager = new TaskManager(this);
        List<Task> tasks = taskManager.loadTasks();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        if (isWeekly) {
            cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            for (int i = 0; i < 7; i++) {
                String key = fmt.format(cal.getTime());
                int count = 0;
                for (Task t : tasks) {
                    if (t.isCompleted() && t.getCompletedDate() != null && fmt.format(t.getCompletedDate()).equals(key)) {
                        count++;
                    }
                }
                result.put(new SimpleDateFormat("EEE", Locale.getDefault()).format(cal.getTime()), count);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else {
            cal.add(Calendar.MONTH, currentMonthOffset);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int i = 0; i < days; i++) {
                String key = fmt.format(cal.getTime());
                int count = 0;
                for (Task t : tasks) {
                    if (t.isCompleted() && t.getCompletedDate() != null && fmt.format(t.getCompletedDate()).equals(key)) {
                        count++;
                    }
                }
                result.put(String.valueOf(i + 1), count);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return result;
    }

    private void updateHabitsBarChart() {
        RoundedBarChart chart = findViewById(R.id.habitsBarChart);
        Map<String, Integer> habitData = getHabitStatsForCurrentPeriod();
        //Map<String, Integer> habitData = getDemoData();

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int totalHabits = 0;
        int i = 0;
        for (String key : habitData.keySet()) {
            Log.d("CharData", key + " -> " + habitData.get(key));
            labels.add(key);
            int count = habitData.get(key);
            totalHabits += count;
            entries.add(new BarEntry(i++, count));
        }

        // Update heading
        TextView habitHeading = findViewById(R.id.habitHeading);
        Calendar cal = Calendar.getInstance();
        if (isWeekly) {
            cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
            int week = cal.get(Calendar.WEEK_OF_YEAR);
            int year = cal.get(Calendar.YEAR);
            habitHeading.setText("Habits completed in Week " + week + ", " + year);
        } else {
            cal.add(Calendar.MONTH, currentMonthOffset);
            String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime());
            int year = cal.get(Calendar.YEAR);
            habitHeading.setText("Habits completed in " + monthName + " " + year);
        }

        // Update total
        TextView habitTask = findViewById(R.id.habitTask);
        habitTask.setText(totalHabits + " Habit" + (totalHabits != 1 ? "s" : ""));

        BarDataSet dataSet = new BarDataSet(entries, "Habits Done");
        dataSet.setDrawValues(false);
        dataSet.setColor(ContextCompat.getColor(this, R.color.barGradient1));
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.45f);
        chart.setData(barData);

        //X Axis
        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        x.setTextColor(Color.WHITE);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);

        //Y Axis
        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.LTGRAY);
        left.setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);

        //Chart styling
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setExtraOffsets(8, 8, 8, 8);
        chart.animateY(700, Easing.EaseInOutQuad);
        chart.invalidate();
    }

    private Map<String, Integer> getHabitStatsForCurrentPeriod() {
        Map<String, Integer> result = new LinkedHashMap<>();
        HabitManager habitManager = new HabitManager(this);
        List<Habit> habits = habitManager.loadHabits();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        if (isWeekly) {
            cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            for (int i = 0; i < 7; i++) {
                String key = fmt.format(cal.getTime());
                int count = 0;
                for (Habit h : habits) {
                    if (h.getLastCompletedDate() != null && fmt.format(h.getLastCompletedDate()).equals(key)) {
                        count++;
                    }
                }
                result.put(new SimpleDateFormat("EEE", Locale.getDefault()).format(cal.getTime()), count);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else {
            cal.add(Calendar.MONTH, currentMonthOffset);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int i = 0; i < days; i++) {
                String key = fmt.format(cal.getTime());
                int count = 0;
                for (Habit h : habits) {
                    if (h.getLastCompletedDate() != null && fmt.format(h.getLastCompletedDate()).equals(key)) {
                        count++;
                    }
                }
                result.put(String.valueOf(i + 1), count);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return result;
    }


    private Map<String, Integer> getDemoData() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("Mon", 1);
        result.put("Tue", 2);
        result.put("Wed", 3);
        result.put("Thu", 4);
        result.put("Fri", 5);
        result.put("Sat", 6);
        result.put("Sun", 7);
        Toast.makeText(this, "Data set", Toast.LENGTH_SHORT).show();
        return result;
    }
}
















