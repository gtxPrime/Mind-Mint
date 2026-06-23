package com.gxdevs.mindmint.Adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.gxdevs.mindmint.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    public interface OnDayClickListener {
        void onDayClick(int dayOfMonth, boolean isCompleted);
    }

    private final List<Integer> days = new ArrayList<>();
    private final Set<Integer> completedDays = new HashSet<>();
    private int todayDay = -1;
    private final OnDayClickListener listener;

    public CalendarDayAdapter(OnDayClickListener listener) {
        this.listener = listener;
    }

    public void setMonth(int year, int month, Set<Integer> completed) {
        this.completedDays.clear();
        this.completedDays.addAll(completed);
        this.days.clear();

        Calendar cal = Calendar.getInstance();

        // Check if viewing current month
        int thisYear = cal.get(Calendar.YEAR);
        int thisMonth = cal.get(Calendar.MONTH);
        int thisDay = cal.get(Calendar.DAY_OF_MONTH);
        todayDay = (year == thisYear && month == thisMonth) ? thisDay : -1;

        cal.set(year, month, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < firstDayOfWeek; i++) {
            days.add(0); // 0 means empty
        }

        for (int d = 1; d <= daysInMonth; d++) {
            days.add(d);
        }

        notifyDataSetChanged();
    }

    public void toggleCompletion(int day) {
        if (completedDays.contains(day)) {
            completedDays.remove(day);
        } else {
            completedDays.add(day);
        }
        int pos = days.indexOf(day);
        if (pos >= 0) {
            notifyItemChanged(pos);
        }
    }

    public boolean isCompleted(int day) {
        return completedDays.contains(day);
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        int day = days.get(position);

        if (day == 0) {
            holder.dayText.setText("");
            holder.dayText.setBackground(null);
            holder.itemView.setOnClickListener(null);
        } else {
            holder.dayText.setText(String.valueOf(day));

            boolean isCompleted = completedDays.contains(day);
            boolean isToday = day == todayDay;

            if (isCompleted) {
                holder.dayText.setBackgroundResource(R.drawable.bg_day_completed);
                holder.dayText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
            } else if (isToday) {
                holder.dayText.setBackgroundResource(R.drawable.bg_day_today);
                holder.dayText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_pink));
            } else {
                holder.dayText.setBackgroundResource(R.drawable.bg_day_normal);
                holder.dayText.setTextColor(resolveAttrColor(holder.itemView.getContext(), R.attr.text_primary));
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDayClick(day, isCompleted);
                }
            });
        }
    }

    private static int resolveAttrColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView dayText;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            dayText = itemView.findViewById(R.id.dayText);
        }
    }
}
