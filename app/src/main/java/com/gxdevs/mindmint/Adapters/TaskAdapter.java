package com.gxdevs.mindmint.Adapters;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.apachat.swipereveallayout.core.SwipeLayout;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eightbitlab.com.blurview.BlurTarget;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final Context context;
    private final List<Task> taskList;
    private final List<Task> filteredTaskList;
    private OnTaskClickListener onTaskClickListener;
    private final boolean manageOnly; // hide completion checkbox & disable toggle
    private BlurTarget blurTarget;

    public interface OnTaskClickListener {
        void onTaskClick(Task task, int position);

        void onTaskCompleted(Task task, int position);

        void onTaskUncompleted(Task task, int position);

        void onTaskEdit(Task task, int position);

        void onTaskDelete(Task task, int position);
    }

    public TaskAdapter(Context context, List<Task> taskList) {
        this(context, taskList, false);
    }

    public TaskAdapter(Context context, List<Task> taskList, boolean manageOnly) {
        this.context = context;
        this.taskList = taskList;
        this.filteredTaskList = new ArrayList<>(taskList);
        this.manageOnly = manageOnly;
    }

    public void setOnTaskClickListener(OnTaskClickListener listener) {
        this.onTaskClickListener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.task_item, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = filteredTaskList.get(position);

        // Set task details
        holder.taskName.setText(task.getName());

        // Display emoji - if not set, assign a random one
        if (task.getEmoji() == null || task.getEmoji().isEmpty()) {
            String[] emojis = new String[]{"✅", "📝", "🎯", "🔥", "💡", "⭐", "🏃", "📚", "💪", "🎨"};
            String randomEmoji = emojis[(int) (Math.random() * emojis.length)];
            task.setEmoji(randomEmoji);
        }
        holder.taskEmoji.setText(task.getEmoji());

        // Set priority
        holder.priorityLabel.setText(task.getPriority().getDisplayName());

        // Set date
        holder.dateCreated.setText(getFormattedDate(task.getScheduledDate()));

        // Show recurring indicator if task is recurring
        if (task.isRecurring()) {
            holder.recurringIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.recurringIndicator.setVisibility(View.GONE);
        }

        // Habit badge
        View habitBadge = holder.habitBadge;
        if (habitBadge != null) {
            habitBadge.setVisibility(task.isHabit() ? View.VISIBLE : View.GONE);
        }

        // Apply completed state styling
        applyCompletedStyling(holder, task.isCompleted());

        // Completion checkbox visibility/behavior based on mode
        if (manageOnly) {
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setVisibility(View.GONE);
        } else {
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(task.isCompleted());
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked != task.isCompleted()) {
                    task.setCompleted(isChecked);
                    applyCompletedStyling(holder, isChecked);

                    if (onTaskClickListener != null) {
                        // Find the actual position in the main task list
                        int actualPosition = findTaskPosition(task);
                        if (isChecked) {
                            onTaskClickListener.onTaskCompleted(task, actualPosition);
                        } else {
                            onTaskClickListener.onTaskUncompleted(task, actualPosition);
                        }
                    }
                }
            });
        }

        // Setup swipe action buttons
        holder.editButton.setOnClickListener(v -> {
            if (onTaskClickListener != null) {
                int actualPosition = findTaskPosition(task);
                onTaskClickListener.onTaskEdit(task, actualPosition);
            }
            holder.swipeLayout.close(true);
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (onTaskClickListener != null) {
                int actualPosition = findTaskPosition(task);
                onTaskClickListener.onTaskDelete(task, actualPosition);
            }
            holder.swipeLayout.close(true);
        });
    }

    private void applyCompletedStyling(TaskViewHolder holder, boolean isCompleted) {
        float alpha = isCompleted ? 0.5f : 1.0f;

        // Animate alpha change
        ObjectAnimator.ofFloat(holder.taskName, "alpha", holder.taskName.getAlpha(), alpha).start();
        ObjectAnimator.ofFloat(holder.taskEmoji, "alpha", holder.taskEmoji.getAlpha(), alpha).start();

        // Strike through effect could be added here if needed
        if (isCompleted) {
            holder.taskName.setPaintFlags(holder.taskName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.taskName.setPaintFlags(holder.taskName.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
        }
    }

    private String getFormattedDate(Date date) {
        if (date == null) return "";

        long now = System.currentTimeMillis();
        long taskTime = date.getTime();

        if (DateUtils.isToday(taskTime)) {
            return "Today";
        } else if (DateUtils.isToday(taskTime + DateUtils.DAY_IN_MILLIS)) {
            return "Yesterday";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return sdf.format(date);
        }
    }

    @Override
    public int getItemCount() {
        return filteredTaskList.size();
    }

    // Filter methods
    public void filterTasks(String query, String filterType) {
        filteredTaskList.clear();

        for (Task task : taskList) {
            boolean matchesQuery = query.isEmpty() ||
                    task.getName().toLowerCase().contains(query.toLowerCase()) ||
                    (task.getShortDescription() != null && task.getShortDescription().toLowerCase().contains(query.toLowerCase()));

            boolean matchesFilter =
                    (filterType.equals("All") && !task.isCompleted()) || // Hide completed tasks from "All"
                            (filterType.equals("Completed") && task.isCompleted()) ||
                            (filterType.equals("Pending") && !task.isCompleted()) ||
                            (filterType.equals("High Priority") && task.getPriority() == Task.Priority.HIGH && !task.isCompleted()) ||
                            (filterType.equals("Medium Priority") && task.getPriority() == Task.Priority.MEDIUM && !task.isCompleted()) ||
                            (filterType.equals("Low Priority") && task.getPriority() == Task.Priority.LOW && !task.isCompleted());

            if (matchesQuery && matchesFilter) {
                filteredTaskList.add(task);
            }
        }

        // Sort tasks by priority (High priority first) since completed tasks are filtered out from "All"
        if (filterType.equals("All") || filterType.equals("Pending")) {
            sortTasksByPriority();
        }

        notifyDataSetChanged();
    }

    private void sortTasksByPriority() {
        filteredTaskList.sort(new Comparator<Task>() {
            @Override
            public int compare(Task t1, Task t2) {
                // Sort by priority (High priority first)
                return Integer.compare(t2.getPriority().getValue(), t1.getPriority().getValue());
            }
        });
    }

    private int findTaskPosition(Task targetTask) {
        for (int i = 0; i < taskList.size(); i++) {
            if (taskList.get(i).getId().equals(targetTask.getId())) {
                return i;
            }
        }
        return -1;
    }

    public void updateTaskList(List<Task> newTaskList) {
        this.taskList.clear();
        this.taskList.addAll(newTaskList);
        this.filteredTaskList.clear();
        this.filteredTaskList.addAll(newTaskList);
        notifyDataSetChanged();
    }

    public void removeTask(int position) {
        if (position >= 0 && position < filteredTaskList.size()) {
            Task taskToRemove = filteredTaskList.get(position);
            filteredTaskList.remove(position);

            // Find and remove from main task list using task ID
            taskList.removeIf(task -> task.getId().equals(taskToRemove.getId()));

            notifyItemRemoved(position);
        }
    }

    public void addTask(Task task) {
        taskList.add(task);
        filteredTaskList.add(task);
        notifyItemInserted(filteredTaskList.size() - 1);
    }

    public List<Task> getTaskList() {
        return taskList;
    }

    public static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView taskName, priorityLabel, dateCreated, taskEmoji;
        ImageView recurringIndicator, habitBadge;
        CheckBox checkBox;
        ConstraintLayout taskCardView;
        LinearLayout swipeActionsLayout;
        ConstraintLayout editButton, deleteButton;
        SwipeLayout swipeLayout;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskName = itemView.findViewById(R.id.taskName);
            priorityLabel = itemView.findViewById(R.id.priorityLabel);
            dateCreated = itemView.findViewById(R.id.dateCreated);
            taskEmoji = itemView.findViewById(R.id.taskEmoji);
            recurringIndicator = itemView.findViewById(R.id.recurringIndicator);
            habitBadge = itemView.findViewById(R.id.habitBadge);
            checkBox = itemView.findViewById(R.id.checkBox);
            taskCardView = itemView.findViewById(R.id.taskCardView);
            swipeActionsLayout = itemView.findViewById(R.id.swipeActionsLayout);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            swipeLayout = itemView.findViewById(R.id.swipeLayout);
        }
    }
}
