package com.gxdevs.mindmint.Models;

import java.util.Date;

public class Task {
    private String id;
    private String name;
    private String shortDescription;
    private int imageResource;
    private boolean isCompleted;
    private Priority priority;
    private Date createdDate;
    private Date completedDate;
    private Date scheduledDate;
    private RecurringType recurringType;
    private boolean isRecurring;
    private boolean hasReminder;
    private String repeatOptionsJson; // Store advanced repeat options as JSON
    private boolean isHabit; // mark if this task represents a Habit
    private String habitId; // optional link to Habit
    private String emoji; // emoji for the task

    public enum Priority {
        LOW("Low", 1),
        MEDIUM("Medium", 2),
        HIGH("High", 3);

        private final String displayName;
        private final int value;

        Priority(String displayName, int value) {
            this.displayName = displayName;
            this.value = value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getValue() {
            return value;
        }
    }

    public enum RecurringType {
        NONE("None"),
        DAILY("Daily"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly"),
        CUSTOM_DATE("Custom Date");

        private final String displayName;

        RecurringType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Constructors
    public Task() {
        this.id = generateId();
        this.createdDate = new Date();
        this.isCompleted = false;
        this.priority = Priority.MEDIUM;
        this.recurringType = RecurringType.NONE;
        this.isRecurring = false;
        this.hasReminder = false;
    }

    public Task(String name, String shortDescription, int imageResource) {
        this();
        this.name = name;
        this.shortDescription = shortDescription;
        this.imageResource = imageResource;
    }

    public Task(String name, String shortDescription, int imageResource, Priority priority) {
        this(name, shortDescription, imageResource);
        this.priority = priority;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public int getImageResource() {
        return imageResource;
    }

    public void setImageResource(int imageResource) {
        this.imageResource = imageResource;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
        if (completed && completedDate == null) {
            completedDate = new Date();
        } else if (!completed) {
            completedDate = null;
        }
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Date completedDate) {
        this.completedDate = completedDate;
    }

    public Date getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(Date scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public RecurringType getRecurringType() {
        return recurringType;
    }

    public void setRecurringType(RecurringType recurringType) {
        this.recurringType = recurringType;
        this.isRecurring = recurringType != RecurringType.NONE;
    }

    public boolean isRecurring() {
        return isRecurring;
    }

    public void setRecurring(boolean recurring) {
        isRecurring = recurring;
        if (!recurring) {
            recurringType = RecurringType.NONE;
        }
    }

    public boolean hasReminder() {
        return hasReminder;
    }

    public void setHasReminder(boolean hasReminder) {
        this.hasReminder = hasReminder;
    }

    public String getRepeatOptionsJson() {
        return repeatOptionsJson;
    }

    public void setRepeatOptionsJson(String repeatOptionsJson) {
        this.repeatOptionsJson = repeatOptionsJson;
    }

    public boolean isHabit() {
        return isHabit;
    }

    public void setHabit(boolean habit) {
        isHabit = habit;
    }

    public String getHabitId() {
        return habitId;
    }

    public void setHabitId(String habitId) {
        this.habitId = habitId;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    // Helper methods
    private String generateId() {
        return "task_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    public String getPriorityColor() {
        switch (priority) {
            case HIGH:
                return "#EE4B37"; // Red
            case MEDIUM:
                return "#A998FD"; // Purple
            case LOW:
                return "#9EABB8"; // Gray
            default:
                return "#9EABB8";
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Task task = (Task) obj;
        return id != null ? id.equals(task.id) : task.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    // Helper method to create next recurring task
    public Task createNextRecurringTask() {
        if (!isRecurring || recurringType == RecurringType.NONE) {
            return null;
        }

        Task nextTask = new Task(this.name, this.shortDescription, this.imageResource, this.priority);
        nextTask.setRecurringType(this.recurringType);
        
        Date nextDate = calculateNextDate();
        nextTask.setScheduledDate(nextDate);
        nextTask.setCreatedDate(nextDate);
        
        return nextTask;
    }

    private Date calculateNextDate() {
        Date baseDate = scheduledDate != null ? scheduledDate : new Date();
        
        switch (recurringType) {
            case DAILY:
                return new Date(baseDate.getTime() + 24 * 60 * 60 * 1000); // Add 1 day
            case WEEKLY:
                return new Date(baseDate.getTime() + 7 * 24 * 60 * 60 * 1000); // Add 1 week
            case MONTHLY:
                // Add 1 month (approximately 30 days)
                return new Date(baseDate.getTime() + 30L * 24 * 60 * 60 * 1000);
            default:
                return new Date();
        }
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", shortDescription='" + shortDescription + '\'' +
                ", isCompleted=" + isCompleted +
                ", priority=" + priority +
                ", recurringType=" + recurringType +
                ", createdDate=" + createdDate +
                '}';
    }
}
