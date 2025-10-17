package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gxdevs.mindmint.Models.Habit;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HabitManager {
    private static final String PREFS_NAME = "HabitPrefs";
    private static final String HABITS_KEY = "habits";

    private final SharedPreferences prefs;
    private final Gson gson;

    public HabitManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void saveHabits(List<Habit> habits) {
        prefs.edit().putString(HABITS_KEY, gson.toJson(habits)).apply();
    }

    public List<Habit> loadHabits() {
        String json = prefs.getString(HABITS_KEY, "");
        if (json.isEmpty()) return new ArrayList<>();
        Type listType = new TypeToken<List<Habit>>(){}.getType();
        List<Habit> list = gson.fromJson(json, listType);
        return list != null ? list : new ArrayList<>();
    }
}


