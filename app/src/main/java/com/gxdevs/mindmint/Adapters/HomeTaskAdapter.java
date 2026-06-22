package com.gxdevs.mindmint.Adapters;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.gxdevs.mindmint.Activities.FocusMode;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.FocusService;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.TaskManager;
import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeTaskAdapter extends RecyclerView.Adapter<HomeTaskAdapter.TaskViewHolder> {

    private final List<Task> tasks;
    private final TaskManager taskManager;
    private final HabitManager habitManager;
    private final Runnable habitsUpdatedCallback;

    public HomeTaskAdapter(List<Task> tasks, TaskManager taskManager, HabitManager habitManager, Runnable habitsUpdatedCallback) {
        this.tasks = tasks;
        this.taskManager = taskManager;
        this.habitManager = habitManager;
        this.habitsUpdatedCallback = habitsUpdatedCallback;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_home, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.title.setText(task.getName());

        if (task.getPriority() != null) {
            if (task.getPriority().name().equalsIgnoreCase("medium")) {
                holder.priorityChip.setText("MED");
            } else {
                holder.priorityChip.setText(task.getPriority().name());
            }
            holder.priorityChip.setBackgroundTintList(ColorStateList.valueOf(getPriorityColor(task)));
            holder.priorityChip.setTextColor(getPriorityTxtColor(task));
        } else {
            holder.priorityChip.setText("");
        }

        if (task.getScheduledDate() != null) {
            holder.timeChip.setText(formatTime(task.getScheduledDate()));
            holder.timeChip.setVisibility(View.VISIBLE);
        } else {
            holder.timeChip.setVisibility(View.GONE);
        }

        holder.recurringIcon.setVisibility(task.isRecurring() ? View.VISIBLE : View.GONE);
        holder.habitIcon.setVisibility(task.isHabit() ? View.VISIBLE : View.GONE);

        holder.radioButton.setChecked(false);
        holder.title.setPaintFlags(holder.title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));

        // Whole-item tap → always go to focus mode (open-ended, or timed if focus is enabled)
        holder.itemView.setOnClickListener(v -> handleTaskTap(holder, task));

        // Radio/checkbox → mark complete directly (or open focus if task is focus-linked)
        holder.radioButton.setOnClickListener(v -> handleRadioClick(holder, task));
    }

    private void handleTaskTap(TaskViewHolder holder, Task task) {
        // Navigate to the appropriate focus mode
        android.content.Context ctx = holder.itemView.getContext();

        Runnable startFocus = () -> {
            // Stop any existing session
            Intent stopIntent = new Intent(ctx, FocusService.class);
            stopIntent.setAction(FocusService.ACTION_STOP_TIMER);
            ctx.startService(stopIntent);

            // Start new focus session
            Intent startIntent = new Intent(ctx, FocusService.class);
            startIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);

            long durationMs = task.isFocusModeEnabled() ? (task.getFocusDurationMinutes() * 60000L) : 0L;
            boolean isOpenEnded = (durationMs == 0);

            startIntent.putExtra("durationInMillis", durationMs);
            startIntent.putExtra("topicName", task.getName());
            startIntent.putExtra(FocusService.EXTRA_TASK_ID, task.getId());
            startIntent.putExtra(FocusService.EXTRA_IS_OPEN_ENDED, isOpenEnded);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(startIntent);
            } else {
                ctx.startService(startIntent);
            }

            Intent focusIntent = new Intent(ctx, FocusMode.class);
            focusIntent.putExtra(FocusService.EXTRA_TASK_ID, task.getId());
            focusIntent.putExtra("topicName", task.getName());
            focusIntent.putExtra(FocusService.EXTRA_IS_OPEN_ENDED, isOpenEnded);
            ctx.startActivity(focusIntent);
        };

        if (FocusService.isPublicFocusRun) {
            boolean isLockedIn = PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
            if (isLockedIn) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)
                    .setTitle("Locked In Mode Active")
                    .setMessage("You cannot stop or switch sessions while Locked In Mode is active.")
                    .setPositiveButton("OK", null)
                    .show();
                return;
            }
            // Another session is running — ask what to do
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx, R.style.AlertDialogTheme)
                .setTitle("Focus Session Running")
                .setMessage("Another focus session is already running. Stop it and start this task?")
                .setPositiveButton("Stop & Start", (dialog, which) -> startFocus.run())
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            startFocus.run();
        }
    }

    private void handleRadioClick(TaskViewHolder holder, Task task) {
        int adapterPosition = holder.getBindingAdapterPosition();
        if (adapterPosition == RecyclerView.NO_POSITION) return;

        if (task.isFocusModeEnabled()) {
            // Focus-linked task: radio also goes to focus instead of marking directly
            handleTaskTap(holder, task);
            // Reset radio so it doesn't appear checked
            holder.radioButton.setChecked(false);
            return;
        }

        // Normal task: animate and mark complete
        holder.title.setPaintFlags(holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        holder.itemView.animate()
                .alpha(0f)
                .translationX(100f)
                .setDuration(300)
                .withEndAction(() -> {
                    task.setCompleted(true);
                    task.setCompletedDate(new Date());
                    if (!task.isHabit()) {
                        new MintCrystals(holder.itemView.getContext()).addCoins(2);
                    }
                    taskManager.updateTask(task);

                    if (task.isHabit() && task.getHabitId() != null) {
                        Habit habit = habitManager.getHabitById(task.getHabitId());
                        if (habit != null) {
                            habit.setLastCompletedDate(new Date());
                            habit.setCurrentStreakDays(habit.getCurrentStreakDays() + 1);
                            habitManager.updateHabit(habit);
                            if (habitsUpdatedCallback != null) {
                                habitsUpdatedCallback.run();
                            }
                        }
                    }

                    tasks.remove(adapterPosition);
                    notifyItemRemoved(adapterPosition);
                    notifyItemRangeChanged(adapterPosition, tasks.size());
                })
                .start();
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    private int getPriorityColor(Task task) {
        switch (task.getPriority()) {
            case HIGH:
                return Color.parseColor("#4e2A35");
            case MEDIUM:
                return Color.parseColor("#3C3020");
            case LOW:
            default:
                return Color.parseColor("#1B3D37");
        }
    }

    private int getPriorityTxtColor(Task task) {
        switch (task.getPriority()) {
            case HIGH:
                return Color.parseColor("#FB7185");
            case MEDIUM:
                return Color.parseColor("#E3AE24");
            case LOW:
            default:
                return Color.parseColor("#34D399");
        }
    }

    private String formatTime(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(date);
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView title, priorityChip, timeChip;
        RadioButton radioButton;
        ImageView recurringIcon, habitIcon;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.taskTitle);
            priorityChip = itemView.findViewById(R.id.priorityChip);
            timeChip = itemView.findViewById(R.id.timeChip);
            radioButton = itemView.findViewById(R.id.taskRadioButton);
            recurringIcon = itemView.findViewById(R.id.recurringIcon);
            habitIcon = itemView.findViewById(R.id.habitIcon);
        }
    }
}
