package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gxdevs.mindmint.Models.Task;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private static final String PREFS_NAME = "TaskPrefs";
    private static final String TASKS_KEY = "tasks";

    private final SharedPreferences prefs;
    private final Gson gson;
    
    public TaskManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    public void saveTasks(List<Task> tasks) {
        String tasksJson = gson.toJson(tasks);
        prefs.edit().putString(TASKS_KEY, tasksJson).apply();
    }
    
    public List<Task> loadTasks() {
        String tasksJson = prefs.getString(TASKS_KEY, "");
        if (tasksJson.isEmpty()) {
            return new ArrayList<>();
        }
        
        Type listType = new TypeToken<List<Task>>(){}.getType();
        List<Task> tasks = gson.fromJson(tasksJson, listType);
        return tasks != null ? tasks : new ArrayList<>();
    }
    
    public void addTask(Task task) {
        List<Task> tasks = loadTasks();
        tasks.add(task);
        saveTasks(tasks);
    }
    
    public void updateTask(Task updatedTask) {
        List<Task> tasks = loadTasks();
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(updatedTask.getId())) {
                tasks.set(i, updatedTask);
                break;
            }
        }
        saveTasks(tasks);
    }
    
    public void deleteTask(String taskId) {
        List<Task> tasks = loadTasks();
        tasks.removeIf(task -> task.getId().equals(taskId));
        saveTasks(tasks);
    }
    
    public void clearAllTasks() {
        prefs.edit().remove(TASKS_KEY).apply();
    }
}
