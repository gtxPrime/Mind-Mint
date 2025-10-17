package com.gxdevs.mindmint.Utils;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.gxdevs.mindmint.Activities.TaskActivity;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Receivers.TaskReminderReceiver;

import java.util.Calendar;

public class TaskNotificationManager {
    private static final String CHANNEL_ID = "task_reminders";
    private static final String CHANNEL_NAME = "Task Reminders";
    private static final String CHANNEL_DESCRIPTION = "Notifications for task reminders";
    
    private final Context context;
    private final NotificationManager notificationManager;
    private final AlarmManager alarmManager;

    public TaskNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void scheduleTaskReminder(Task task) {
        if (task.getScheduledDate() == null) {
            return;
        }

        Calendar taskTime = Calendar.getInstance();
        taskTime.setTime(task.getScheduledDate());
        
        Calendar now = Calendar.getInstance();
        
        // Don't schedule reminders for past tasks
        if (taskTime.before(now)) {
            return;
        }

        // Always schedule on-time reminder
        scheduleNotification(task, taskTime, false);
        
        // Only schedule 30-minute reminder if user has enabled reminders
        if (task.hasReminder()) {
            Calendar reminderTime = (Calendar) taskTime.clone();
            reminderTime.add(Calendar.MINUTE, -30);
            
            if (reminderTime.after(now)) {
                scheduleNotification(task, reminderTime, true);
            }
        }
    }

    private void scheduleNotification(Task task, Calendar when, boolean isPreReminder) {
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_name", task.getName());
        intent.putExtra("task_description", task.getShortDescription());
        intent.putExtra("is_pre_reminder", isPreReminder);

        // Use unique request codes for each notification
        int requestCode = (task.getId().hashCode() * 2) + (isPreReminder ? 1 : 0);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    when.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    when.getTimeInMillis(),
                    pendingIntent
            );
        }
    }

    public void cancelTaskReminder(Task task) {
        // Cancel both pre-reminder and on-time reminder
        cancelNotification(task, true);
        cancelNotification(task, false);
    }

    private void cancelNotification(Task task, boolean isPreReminder) {
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        int requestCode = (task.getId().hashCode() * 2) + (isPreReminder ? 1 : 0);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        alarmManager.cancel(pendingIntent);
    }

    public void showTaskNotification(String taskId, String taskName, String taskDescription, boolean isPreReminder) {
        Intent intent = new Intent(context, TaskActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = isPreReminder ? "⏰ Task Reminder" : "🔔 Task Due Now";
        String content = isPreReminder ? 
                taskName + " is due in 30 minutes" : 
                "It's time for: " + taskName;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(content + (taskDescription != null && !taskDescription.isEmpty() ? 
                                "\n" + taskDescription : "")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        int notificationId = taskId.hashCode() + (isPreReminder ? 1000 : 2000);
        
        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // Handle notification permission not granted
            android.util.Log.e("TaskNotification", "Notification permission not granted", e);
        }
    }
}
