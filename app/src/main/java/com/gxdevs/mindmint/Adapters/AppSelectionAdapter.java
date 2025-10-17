package com.gxdevs.mindmint.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.gxdevs.mindmint.Models.AppInfo;
import com.gxdevs.mindmint.R;
import java.util.List;

public class AppSelectionAdapter extends RecyclerView.Adapter<AppSelectionAdapter.ViewHolder> {

    private final List<AppInfo> appList;
    private final Context context;
    private final OnAppBlockStateChangedListener listener;

    public interface OnAppBlockStateChangedListener {
        void onAppBlockStateChanged(String packageName, boolean isBlocked);
    }

    public AppSelectionAdapter(Context context, List<AppInfo> appList, OnAppBlockStateChangedListener listener) {
        this.context = context;
        this.appList = appList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_app_selectable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo appInfo = appList.get(position);
        holder.appName.setText(appInfo.appName);
        holder.appIcon.setImageDrawable(appInfo.icon);
        // Clear any previous listener to avoid triggering stale callbacks during recycling
        holder.blockSwitch.setOnCheckedChangeListener(null);
        holder.blockSwitch.setChecked(appInfo.isBlocked);

        holder.itemView.setOnClickListener(v -> {
            holder.blockSwitch.toggle();
            // The setOnCheckedChangeListener will handle the logic
        });

        holder.blockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                appInfo.isBlocked = isChecked; // Update model state immediately
                listener.onAppBlockStateChanged(appInfo.packageName, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        MaterialSwitch blockSwitch;

        ViewHolder(View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.iv_app_icon);
            appName = itemView.findViewById(R.id.tv_app_name);
            blockSwitch = itemView.findViewById(R.id.switch_app_blocked);
        }
    }
} 