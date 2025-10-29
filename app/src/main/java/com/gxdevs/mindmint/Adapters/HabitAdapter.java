package com.gxdevs.mindmint.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.R;
import com.skydoves.progressview.ProgressView;

import java.util.ArrayList;
import java.util.List;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.HabitViewHolder> {

    public interface OnHabitActionListener {
        void onHabitCompletedToday(Habit habit, int position);
        void onHabitClicked(Habit habit, int position);
        void onHabitUncompletedToday(Habit habit, int position);
    }

    private final Context context;
    private final List<Habit> habits;
    private final OnHabitActionListener listener;

    public HabitAdapter(Context context, List<Habit> habits, OnHabitActionListener listener) {
        this.context = context;
        this.habits = new ArrayList<>(habits);
        this.listener = listener;
    }

    @NonNull
    @Override
    public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.habit_item, parent, false);
        return new HabitViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
        Habit habit = habits.get(position);
        holder.title.setText(habit.getName());
        holder.reason.setText(habit.getReason());

        // Emoji
        TextView emojiTv = holder.icon.findViewById(R.id.emojiText);
        if (emojiTv != null && habit.getEmoji() != null && !habit.getEmoji().isEmpty()) {
            emojiTv.setText(habit.getEmoji());
        }

        // Progress and goal text X/Y using dynamic target based on habit configuration
        int current = habit.getCurrentStreakDays();
        int target = habit.getCurrentTargetDays();
        int progress = Math.min(100, target > 0 ? (int) (current * 100f / target) : 0);
        holder.progress.setProgress(progress);
        String targetLabel = habit.getCurrentTargetLabel();
        holder.dayGoal.setText(current + "/" + target);

        // Checkbox reflects state
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(habit.isDoneToday());
        holder.checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) listener.onHabitCompletedToday(habit, holder.getAdapterPosition());
            else listener.onHabitUncompletedToday(habit, holder.getAdapterPosition());
        });

        // Click toggles checkbox
        holder.itemView.setOnClickListener(v -> {
            boolean newState = !holder.checkBox.isChecked();
            holder.checkBox.setChecked(newState);
        });

        // Long press opens edit page (not delete)
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onHabitClicked(habit, holder.getAdapterPosition());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return habits.size();
    }

    public void setData(List<Habit> list) {
        habits.clear();
        habits.addAll(list);
        notifyDataSetChanged();
    }

    static class HabitViewHolder extends RecyclerView.ViewHolder {
        TextView title, reason, dayGoal;
        MaterialCardView icon;
        ProgressView progress;
        MaterialCheckBox checkBox;

        HabitViewHolder(@NonNull View itemView) {
            super(itemView);;
            title = itemView.findViewById(R.id.habitTitle);
            reason = itemView.findViewById(R.id.habitReason);
            icon = itemView.findViewById(R.id.habitIcon);
            progress = itemView.findViewById(R.id.habitProgress);
            dayGoal = itemView.findViewById(R.id.dayGoal);
            checkBox = itemView.findViewById(R.id.habitDoneCheck);
        }
    }
}


