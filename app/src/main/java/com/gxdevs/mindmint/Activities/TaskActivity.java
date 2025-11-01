package com.gxdevs.mindmint.Activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.gxdevs.mindmint.Adapters.TaskAdapter;
import com.gxdevs.mindmint.Fragments.AddTaskBottomSheet;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.StreakManager;
import com.gxdevs.mindmint.Utils.TaskManager;
import com.gxdevs.mindmint.Utils.TaskNotificationManager;
import com.gxdevs.mindmint.Utils.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class TaskActivity extends AppCompatActivity implements TaskAdapter.OnTaskClickListener, AddTaskBottomSheet.OnTaskActionListener {

    private ShapeableImageView backButton, addTaskButton;
    private EditText searchEditText;
    private ImageView filterButton;
    private RecyclerView tasksRecyclerView;
    private ConstraintLayout emptyStateLayout;
    private TextView emptyStateText;
    private HorizontalScrollView filterScrollView;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private String currentFilter = "All";
    private String currentSearchQuery = "";
    private TaskManager taskManager;
    private TaskNotificationManager notificationManager;
    private HabitManager habitManager;
    private StreakManager streakManager;
    private MintCrystals mintCrystals;
    private BlurView searchBlur;
    private BlurView permissionBlur;
    private BlurTarget blurTarget;
    private LinearLayout filterChipContainer;

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);
        taskManager = new TaskManager(this);
        habitManager = new HabitManager(this);
        notificationManager = new TaskNotificationManager(this);
        streakManager = new StreakManager(this);
        mintCrystals = new MintCrystals(this);
        initViews();
        applyColors();
        setupRecyclerView();
        setupClickListeners();
        checkNotificationPermission();
        loadTasks();
        checkPermissionAndMoveOn();

        searchBlur.setupWith(blurTarget).setBlurRadius(5f);
        permissionBlur.setupWith(blurTarget).setBlurRadius(5f);

        setupSearchAndFilter();
    }

    private void checkPermissionAndMoveOn() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        MaterialCardView permissionCard = findViewById(R.id.permissionCard);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                permissionCard.setVisibility(VISIBLE);
                permissionCard.setOnClickListener(v -> askForExactAlarmPermission());
            } else {
                permissionCard.setVisibility(GONE);
            }
        }
    }

    private void askForExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            startActivity(intent);
        }
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        addTaskButton = findViewById(R.id.addTaskButton);
        searchEditText = findViewById(R.id.searchEditText);
        filterButton = findViewById(R.id.filterButton);
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        emptyStateText = findViewById(R.id.emptyStateText);
        filterScrollView = findViewById(R.id.filterScrollView);
        searchBlur = findViewById(R.id.searchBlurView);
        permissionBlur = findViewById(R.id.permissionBlur);
        blurTarget = findViewById(R.id.blurTarget);
        filterChipContainer = findViewById(R.id.filterChipContainer);
    }

    private void setupRecyclerView() {
        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(this, taskList);
        taskAdapter.setOnTaskClickListener(this);
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tasksRecyclerView.setAdapter(taskAdapter);
    }

    private void setupSearchAndFilter() {
        // Search functionality
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                currentSearchQuery = s.toString();
                filterTasks();
            }
        });

        // Filter chips with blurs
        String[] chipLabels = {"All", "Pending", "Completed", "High Priority", "Medium Priority", "Low Priority"};
        filterChipContainer.removeAllViews();

        final View[] selectedChip = {null};

        for (String label : chipLabels) {
            FrameLayout chipWrapper = new FrameLayout(this);
            TextView chipText = new TextView(this);
            chipText.setText(label);
            chipText.setTextColor(ContextCompat.getColor(this, R.color.white));
            chipText.setTextSize(14f);
            chipText.setPadding(25, 12, 25, 12);
            chipText.setGravity(Gravity.CENTER);

            GradientDrawable bg = new GradientDrawable();
            //bg.setStroke(2, ContextCompat.getColor(this, R.color.brainColor));
            bg.setColor(ContextCompat.getColor(this, R.color.transparent));
            bg.setCornerRadius(50f);

            GradientDrawable bgSelected = new GradientDrawable();
            //bgSelected.setStroke(2, ContextCompat.getColor(this, R.color.brainColor));
            bgSelected.setColor(ContextCompat.getColor(this, R.color.sexyGrey));
            bgSelected.setCornerRadius(50f);

            chipText.setBackground(bg);

            BlurView blurView = new BlurView(this);
            FrameLayout.LayoutParams blurParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            blurView.setLayoutParams(blurParams);

            blurView.setupWith(blurTarget).setBlurRadius(5f);
            blurView.setClipToOutline(true);

            float cornerRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
            blurView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });

            chipWrapper.addView(blurView);
            chipWrapper.addView(chipText);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(8, 0, 8, 0);
            chipWrapper.setLayoutParams(params);
            chipWrapper.setOnClickListener(v -> {
                if (selectedChip[0] != null) {
                    TextView previousText = (TextView) ((FrameLayout) selectedChip[0]).getChildAt(1);
                    previousText.setBackground(bg);
                }
                chipText.setBackground(bgSelected);
                selectedChip[0] = chipWrapper;
                currentFilter = label;
                filterTasks();
            });

            filterChipContainer.addView(chipWrapper);
            if (label.equals("All")) {
                chipWrapper.performClick();
            }

        }
    }

    private void applyColors() {
        View arc1 = findViewById(R.id.arcTopLeft);
        View arc2 = findViewById(R.id.arcBottomRight);
        View arc3 = findViewById(R.id.arcBottomLeft);
        MaterialCardView permissionCard = findViewById(R.id.permissionCard);
        Utils.applySecondaryColor(permissionCard, this);
        Utils.applyAccentColors(arc1, arc2, arc3, this);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        addTaskButton.setOnClickListener(v -> showAddTaskBottomSheet());
        filterButton.setOnClickListener(v -> {
            if (filterScrollView.getVisibility() == VISIBLE) {
                filterScrollView.setVisibility(GONE);
            } else {
                filterScrollView.setVisibility(VISIBLE);
            }
        });
    }

    private void filterTasks() {
        taskAdapter.filterTasks(currentSearchQuery, currentFilter);
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = taskAdapter.getItemCount() == 0;
        emptyStateLayout.setVisibility(isEmpty ? VISIBLE : GONE);
        tasksRecyclerView.setVisibility(isEmpty ? GONE : VISIBLE);

        if (isEmpty) {
            if (!currentSearchQuery.isEmpty()) {
                emptyStateText.setText(R.string.no_tasks_match_your_search);
            } else if (!currentFilter.equals("All")) {
                String curTxt = "No " + currentFilter.toLowerCase() + " tasks";
                emptyStateText.setText(curTxt);
            } else {
                emptyStateText.setText(R.string.no_tasks_found);
            }
        }
    }

    private void showAddTaskBottomSheet() {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstance();
        bottomSheet.setOnTaskActionListener(this);
        bottomSheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
    }

    private void showEditTaskBottomSheet(Task task) {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstance(task);
        bottomSheet.setOnTaskActionListener(this);
        bottomSheet.show(getSupportFragmentManager(), "EditTaskBottomSheet");
    }


    private void loadTasks() {
        taskList = taskManager.loadTasks();
        // Schedule notifications for existing tasks with scheduled dates
        for (Task task : taskList) {
            if (task.getScheduledDate() != null && !task.isCompleted()) {
                notificationManager.scheduleTaskReminder(task);
            }
        }

        taskAdapter.updateTaskList(taskList);
        filterTasks(); // Apply initial filter and sorting
        updateEmptyState();
    }

    private void saveTasks() {
        taskManager.saveTasks(taskList);
    }

    @Override
    public void onTaskClick(Task task, int position) {
    }

    @Override
    public void onTaskCompleted(Task task, int position) {
        // Award coins for regular tasks only (not habit-related tasks)
        if (!task.isHabit()) {
            mintCrystals.addCoins(2);
        }

        if (task.isRecurring()) {
            // For recurring tasks, show special message
            String nextRepeatDate = getNextRepeatDateString(task);
            Toast.makeText(this, "Task completed for today! Will repeat on " + nextRepeatDate, Toast.LENGTH_LONG).show();

            // Don't create next task immediately - it will be created on the actual repeat date
            // For now, just mark this task as completed
        } else {
            // For non-recurring tasks
            Toast.makeText(this, "Task completed: " + task.getName(), Toast.LENGTH_SHORT).show();
        }

        saveTasks();
        // Cancel any scheduled notifications for this task now that it's completed
        notificationManager.cancelTaskReminder(task);
        // If this task belongs to a habit, check if all tasks for that habit are completed and mark habit done
        if (task.isHabit() && task.getHabitId() != null) {
            List<Task> tasks = taskManager.loadTasks();
            boolean allComplete = true;
            for (Task t : tasks) {
                if (task.getHabitId().equals(t.getHabitId()) && !t.isCompleted()) {
                    allComplete = false;
                    break;
                }
            }
            if (allComplete) {
                List<Habit> habits = habitManager.loadHabits();
                for (int i = 0; i < habits.size(); i++) {
                    if (habits.get(i).getId().equals(task.getHabitId())) {
                        habits.get(i).markDoneToday();
                        break;
                    }
                }
                habitManager.saveHabits(habits);

                // Update streak when habit is completed via tasks
                streakManager.updateStreakOnHabitCompletion();
            }
        }

        // Re-apply current filter - completed tasks will be hidden from "All" view
        filterTasks();
    }

    @Override
    public void onTaskUncompleted(Task task, int position) {
        // Subtract coins for regular tasks only (not habit-related tasks)
        if (!task.isHabit()) {
            mintCrystals.subtractCoins(2);
        }

        Toast.makeText(this, "Task marked as pending: " + task.getName(), Toast.LENGTH_SHORT).show();
        saveTasks();
        // If this task belongs to a habit, unmark the habit for today because not all tasks are complete
        if (task.isHabit() && task.getHabitId() != null) {
            List<Habit> habits = habitManager.loadHabits();
            for (int i = 0; i < habits.size(); i++) {
                if (habits.get(i).getId().equals(task.getHabitId())) {
                    habits.get(i).unmarkToday();
                    break;
                }
            }
            habitManager.saveHabits(habits);

            // Check if any habits are still completed today and reset streak if needed
            streakManager.checkAndResetStreakIfNeeded(habits);
        }

        // Re-apply current filter to update sorting
        filterTasks();
    }

    @Override
    public void onTaskEdit(Task task, int position) {
        showEditTaskBottomSheet(task);
    }

    @Override
    public void onTaskDelete(Task task, int position) {
        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Cancel any scheduled notifications for this task
                    notificationManager.cancelTaskReminder(task);

                    // Remove from main task list using task ID
                    taskList.removeIf(t -> t.getId().equals(task.getId()));

                    // Update adapter with new task list
                    taskAdapter.updateTaskList(taskList);

                    // Re-apply current filter
                    filterTasks();

                    // Save to persistent storage
                    saveTasks();
                    updateEmptyState();
                    Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onTaskCreated(Task task) {
        taskList.add(task);
        taskAdapter.updateTaskList(taskList);
        filterTasks(); // Apply current filter to newly added task
        saveTasks();

        // Schedule notification if task has scheduled date
        if (task.getScheduledDate() != null) {
            notificationManager.scheduleTaskReminder(task);
        }

        updateEmptyState();
        Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTaskUpdated(Task task) {
        // Task object is already updated in place since we pass the reference
        taskAdapter.updateTaskList(taskList);
        filterTasks(); // Re-apply filter to show updated task in correct position
        saveTasks();

        // Cancel existing notifications and reschedule if task has scheduled date
        notificationManager.cancelTaskReminder(task);
        if (task.getScheduledDate() != null) {
            notificationManager.scheduleTaskReminder(task);
        }

        Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save tasks when activity is paused
        saveTasks();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Save tasks when activity is stopped
        saveTasks();
    }

    private String getNextRepeatDateString(Task task) {
        if (!task.isRecurring()) {
            return "never";
        }

        Calendar nextDate = Calendar.getInstance();

        switch (task.getRecurringType()) {
            case WEEKLY:
                nextDate.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case MONTHLY:
                nextDate.add(Calendar.MONTH, 1);
                break;
            default:
                nextDate.add(Calendar.DAY_OF_MONTH, 1);
                break;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        return dateFormat.format(nextDate.getTime());
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied. Task reminders won't work.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }
}














