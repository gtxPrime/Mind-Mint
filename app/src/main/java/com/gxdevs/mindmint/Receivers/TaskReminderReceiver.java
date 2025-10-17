package com.gxdevs.mindmint.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gxdevs.mindmint.Utils.TaskNotificationManager;

public class TaskReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String taskId = intent.getStringExtra("task_id");
        String taskName = intent.getStringExtra("task_name");
        String taskDescription = intent.getStringExtra("task_description");
        boolean isPreReminder = intent.getBooleanExtra("is_pre_reminder", false);

        if (taskId != null && taskName != null) {
            TaskNotificationManager notificationManager = new TaskNotificationManager(context);
            notificationManager.showTaskNotification(taskId, taskName, taskDescription, isPreReminder);
        }
    }
}
