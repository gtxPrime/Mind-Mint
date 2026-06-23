package com.gxdevs.mindmint.Adapters;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Paint;
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
import com.gxdevs.mindmint.Utils.AnimUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final Context context;
    private final List<Task> taskList;
    private final List<Task> filteredTaskList;
    private OnTaskClickListener onTaskClickListener;
    private final boolean manageOnly; // hide completion checkbox & disable toggle

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

    // Tracks last animated item position to skip re-animation on scroll
    private final int[] lastAnimatedPosition = {-1};

    public void setOnTaskClickListener(OnTaskClickListener listener) {
        this.onTaskClickListener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = filteredTaskList.get(position);

        // Item entrance animation
        AnimUtils.animateItemEnter(holder.itemView, position, lastAnimatedPosition);
        // Tap-press feedback on the card
        AnimUtils.attachTouchRipple(holder.taskCardView);

        holder.taskName.setText(task.getName());
        int iconRes = task.getIcon() != 0 ? task.getIcon() : R.drawable.list_todo;
        holder.taskIcon.setImageResource(iconRes);
        holder.priorityLabel.setText(task.getPriority().getDisplayName());
        holder.dateCreated.setText(getFormattedDate(task.getScheduledDate()));

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

        applyCompletedStyling(holder, task.isCompleted());

        if (manageOnly) {
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setVisibility(View.GONE);
        } else {
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(task.isCompleted());
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked != task.isCompleted()) {
                    if (isChecked && task.isFocusModeEnabled()) {
                        // User checked it, but it's focus mode enabled, so open focus mode instead!
                        buttonView.setChecked(false); // Revert checkbox visually
                        if (onTaskClickListener != null) {
                            int actualPosition = findTaskPosition(task);
                            onTaskClickListener.onTaskClick(task, actualPosition);
                        }
                        return; // do not complete it
                    }

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

        holder.taskCardView.setOnClickListener(v -> {
            if (onTaskClickListener != null) {
                int actualPosition = findTaskPosition(task);
                onTaskClickListener.onTaskClick(task, actualPosition);
            }
        });
    }

    private void applyCompletedStyling(TaskViewHolder holder, boolean isCompleted) {
        float alpha = isCompleted ? 0.5f : 1.0f;

        ObjectAnimator.ofFloat(holder.taskName, "alpha", holder.taskName.getAlpha(), alpha).start();
        ObjectAnimator.ofFloat(holder.taskIcon, "alpha", holder.taskIcon.getAlpha(), alpha).start();

        if (isCompleted) {
            holder.taskName.setPaintFlags(holder.taskName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.taskName.setPaintFlags(holder.taskName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }
    }

    private String getFormattedDate(Date date) {
        if (date == null) return "";

        long now = System.currentTimeMillis();
        long taskTime = date.getTime();
        boolean isPassed = taskTime < now;
        String passedPrefix = isPassed ? "(passed) " : "";

        if (DateUtils.isToday(taskTime)) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            return passedPrefix + "Today " + timeFormat.format(date);
        } else if (DateUtils.isToday(taskTime + DateUtils.DAY_IN_MILLIS)) {
            return passedPrefix + "Yesterday";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return passedPrefix + sdf.format(date);
        }
    }

    @Override
    public int getItemCount() {
        return filteredTaskList.size();
    }

    public void filterTasks(String query, String filterType, Task excludeTask) {
        filteredTaskList.clear();

        for (Task task : taskList) {
            if (excludeTask != null && task.getId().equals(excludeTask.getId())) {
                continue;
            }

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

        sortTasksByDeadlineAndPriority();

        notifyDataSetChanged();
    }

    private void sortTasksByDeadlineAndPriority() {
        filteredTaskList.sort((t1, t2) -> {
            int p1 = t1.getPriority() != null ? t1.getPriority().getValue() : 0;
            int p2 = t2.getPriority() != null ? t2.getPriority().getValue() : 0;
            int priorityCompare = Integer.compare(p2, p1); // Descending order (high to low)
            if (priorityCompare != 0) return priorityCompare;

            Date d1 = t1.getScheduledDate();
            Date d2 = t2.getScheduledDate();
            if (d1 != null && d2 != null) {
                return d1.compareTo(d2); // Ascending order (earliest first)
            } else if (d1 != null) return -1; // t1 has date, t2 doesn't - t1 comes first
            else if (d2 != null) return 1; // t2 has date, t1 doesn't - t2 comes first
            else return 0; // Both null, keep order
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
        TextView taskName, priorityLabel, dateCreated;
        ImageView taskIcon, recurringIndicator, habitBadge;
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
            taskIcon = itemView.findViewById(R.id.taskIcon);
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
