package com.gxdevs.mindmint.Activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gxdevs.mindmint.Adapters.HabitAdapter;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.StreakManager;
import com.gxdevs.mindmint.Utils.TaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class HabitActivity extends AppCompatActivity implements HabitAdapter.OnHabitActionListener {

    private HabitAdapter adapter;
    private HabitManager habitManager;
    private TaskManager taskManager;
    private StreakManager streakManager;
    private MintCrystals mintCrystals;
    private List<Habit> habits = new ArrayList<>();
    private EditText searchEditText;
    private String currentFilter = "All"; // default
    private BlurTarget blurTarget;
    private Drawable windowBackground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_habit);

        habitManager = new HabitManager(this);
        taskManager = new TaskManager(this);
        streakManager = new StreakManager(this);
        mintCrystals = new MintCrystals(this);
        blurTarget = findViewById(R.id.blurTarget);

        BlurView searchBlurView = findViewById(R.id.searchBlurView);
        windowBackground = getWindow().getDecorView().getBackground();
        searchBlurView.setupWith(blurTarget)
                .setBlurRadius(18f);

        ImageView back = findViewById(R.id.backButton);
        back.setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.habitsRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new HabitAdapter(this, habits, this);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.addHabitButton).setOnClickListener(v -> startActivity(new Intent(this, HabitDetailActivity.class)));

        // Search & filter
        searchEditText = findViewById(R.id.searchEditText);
        setupSearch();
        load();
        setupCustomBlurChips();
    }


    private void setupSearch() {
        if (searchEditText == null) return;
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearchAndFilterCustomChips();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupCustomBlurChips() {
        LinearLayout filterChipContainer = findViewById(R.id.filterChipContainer);

        // Labels for your chips
        String[] filters = {"All", "Good", "Bad"};
        filterChipContainer.removeAllViews();

        final View[] selectedChip = {null};

        for (String label : filters) {
            // Parent container for each chip
            FrameLayout chipWrapper = new FrameLayout(this);
            LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            wrapperParams.setMargins(12, 0, 12, 0);
            chipWrapper.setLayoutParams(wrapperParams);

            // Blur background
            BlurView blurView = new BlurView(this);
            FrameLayout.LayoutParams blurParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            blurView.setLayoutParams(blurParams);

            float cornerRadiusPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics()
            );
            blurView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
            blurView.setClipToOutline(true);

            blurView.setupWith(blurTarget)
                    .setBlurRadius(10f);

            // Chip text
            TextView chipText = new TextView(this);
            chipText.setText(label);
            chipText.setTextColor(Color.WHITE);
            chipText.setTextSize(14f);
            chipText.setGravity(Gravity.CENTER);
            chipText.setPadding(25, 12, 25, 12);

            // Normal (unselected) background
            GradientDrawable normalBg = new GradientDrawable();
            normalBg.setCornerRadius(50f);
            normalBg.setColor(ContextCompat.getColor(this, R.color.transparent));

            // Selected background
            GradientDrawable selectedBg = new GradientDrawable();
            selectedBg.setCornerRadius(50f);
            selectedBg.setColor(ContextCompat.getColor(this, R.color.sexyGrey));

            chipText.setBackground(normalBg);

            // Add Blur + Text inside wrapper
            chipWrapper.addView(blurView);
            chipWrapper.addView(chipText);

            // Handle clicks
            chipWrapper.setOnClickListener(v -> {
                if (selectedChip[0] != null) {
                    TextView prevText = (TextView) ((FrameLayout) selectedChip[0]).getChildAt(1);
                    prevText.setBackground(normalBg);
                }
                chipText.setBackground(selectedBg);
                selectedChip[0] = chipWrapper;

                currentFilter = label;
                applySearchAndFilterCustomChips();
            });

            // Default select "All"
            if (label.equals("All")) {
                chipWrapper.post(chipWrapper::performClick);
            }

            filterChipContainer.addView(chipWrapper);
        }
    }

    private void applySearchAndFilterCustomChips() {
        String q = searchEditText != null ? searchEditText.getText().toString().toLowerCase() : "";
        String filter = currentFilter != null ? currentFilter : "All";

        List<Habit> base = new ArrayList<>(habits);

        switch (filter) {
            case "Good":
                base = base.stream()
                        .filter(h -> h.getGoodBad() == Habit.GoodBad.GOOD)
                        .collect(Collectors.toList());
                break;
            case "Bad":
                base = base.stream()
                        .filter(h -> h.getGoodBad() == Habit.GoodBad.BAD)
                        .collect(Collectors.toList());
                break;
            default:
                break;
        }

        if (!q.isEmpty()) {
            base = base.stream().filter(h ->
                    (h.getName() != null && h.getName().toLowerCase().contains(q))
                            || (h.getReason() != null && h.getReason().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }
        adapter.setData(base);
    }

    private void load() {
        habits = habitManager.loadHabits();
        applySearchAndFilterCustomChips();
    }

    @Override
    public void onHabitCompletedToday(Habit habit, int position) {
        habit.markDoneToday();
        habitManager.saveHabits(habits);
        
        // Award 5 coins for completing a habit
        mintCrystals.addCoins(5);
        
        // Mark all related tasks as completed
        TaskManager tm = taskManager != null ? taskManager : new TaskManager(this);
        List<Task> all = tm.loadTasks();
        for (Task t : all) {
            if (habit.getId().equals(t.getHabitId())) {
                t.setCompleted(true);
            }
        }
        tm.saveTasks(all);
        adapter.notifyItemChanged(position);

        // Update streak when habit is completed
        streakManager.updateStreakOnHabitCompletion();
    }

    @Override
    public void onHabitClicked(Habit habit, int position) {
        startActivity(new Intent(this, HabitDetailActivity.class)
                .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, habit.getId()));
    }

    @Override
    public void onHabitUncompletedToday(Habit habit, int position) {
        habit.unmarkToday();
        habitManager.saveHabits(habits);
        
        // Subtract 5 coins for uncompleting a habit
        mintCrystals.subtractCoins(5);
        
        // Unmark all related tasks as not completed
        TaskManager tm = taskManager != null ? taskManager : new TaskManager(this);
        List<Task> all = tm.loadTasks();
        for (Task t : all) {
            if (habit.getId().equals(t.getHabitId())) {
                t.setCompleted(false);
            }
        }
        tm.saveTasks(all);
        adapter.notifyItemChanged(position);

        // Check if any habits are still completed today and reset streak if needed
        streakManager.checkAndResetStreakIfNeeded(habits);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }
}


