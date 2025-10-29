package com.gxdevs.mindmint.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.gxdevs.mindmint.Adapters.TaskAdapter;
import com.gxdevs.mindmint.Fragments.AddTaskBottomSheet;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.TaskManager;
import com.gxdevs.mindmint.Utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class HabitDetailActivity extends AppCompatActivity {

    public static final String EXTRA_HABIT_ID = "extra_habit_id";

    private HabitManager habitManager;
    private TaskManager taskManager;
    private Habit habit;
    private TaskAdapter subTaskAdapter;
    private TextView emojiTextView;
    private EditText habitTitleEdit;
    private Date selectedUntilDate;
    private MaterialCardView emojiCard;
    private MaterialCardView dateCard;
    private TextView dateTitleEdit;
    private Spinner difficultySpinner;
    private Spinner goodBadSpinner;
    private TextInputEditText reasonEdit;
    private ExtendedFloatingActionButton addTaskCard;
    private MaterialButton btnDone;
    private MaterialButton btnDelete;
    private RecyclerView subTasksRecyclerView;
    private BlurTarget blurTarget;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_habit_detail);

        initViews();
        setupUI();

        // Load habit
        boolean isEditMode = false;
        String habitId = getIntent().getStringExtra(EXTRA_HABIT_ID);
        if (habitId != null) {
            for (Habit h : habitManager.loadHabits()) {
                if (habitId.equals(h.getId())) {
                    habit = h;
                    isEditMode = true;
                    break;
                }
            }
        }
        if (habit == null) habit = new Habit();

        // Title by mode
        TextView screenTitle = findViewById(R.id.screenTitle);
        screenTitle.setText(isEditMode ? "Edit habit" : "New habit");

        // Show delete only in edit mode
        btnDelete.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete habit?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    List<Habit> list = habitManager.loadHabits();
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).getId().equals(habit.getId())) {
                            list.remove(i);
                            break;
                        }
                    }
                    habitManager.saveHabits(list);
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show());

        // Bind existing values
        habitTitleEdit.setText(habit.getName());
        reasonEdit.setText(habit.getReason());
        if (habit.getEmoji() != null && !habit.getEmoji().isEmpty()) {
            emojiTextView.setText(habit.getEmoji());
        }
        if (habit.getUntilDate() != null) {
            selectedUntilDate = habit.getUntilDate();
            dateTitleEdit.setText(formatDate(selectedUntilDate));
        }

        // Spinners
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, new String[]{"Easy", "Medium", "Hard"});
        diffAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        difficultySpinner.setAdapter(diffAdapter);
        difficultySpinner.setSelection(habit.getDifficulty() == Habit.Difficulty.EASY ? 0 : habit.getDifficulty() == Habit.Difficulty.MEDIUM ? 1 : 2);

        ArrayAdapter<String> gbAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, new String[]{"Good", "Bad"});
        gbAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        goodBadSpinner.setAdapter(gbAdapter);
        goodBadSpinner.setSelection(habit.getGoodBad() == Habit.GoodBad.GOOD ? 0 : 1);

        // Emoji simple picker
        emojiCard.setOnClickListener(v -> {
            String current = emojiTextView.getText().toString();
            String[] options = new String[]{"💡", "🔥", "✅", "🏃", "📚", "🧘", "🏋️‍♀️", "🥗", "💧", "🌙"};
            int idx = 0;
            for (int i = 0; i < options.length; i++)
                if (options[i].equals(current)) {
                    idx = i;
                    break;
                }
            emojiTextView.setText(options[(idx + 1) % options.length]);
        });

        // Date picker dialog (uses dialog_date_picker.xml); hide time and repeat rows
        View.OnClickListener openDatePicker = v -> showHabitDatePicker(dateTitleEdit);
        dateCard.setOnClickListener(openDatePicker);
        dateTitleEdit.setOnClickListener(openDatePicker);

        // Sub-tasks setup (manageOnly mode = true: no completion)
        subTasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        subTasksRecyclerView.setNestedScrollingEnabled(false);
        List<Task> subTasks = new java.util.ArrayList<>();
        for (Task t : taskManager.loadTasks()) {
            if (habit.getId().equals(t.getHabitId())) subTasks.add(t);
        }
        subTaskAdapter = new TaskAdapter(this, subTasks, true, blurTarget);
        subTasksRecyclerView.setAdapter(subTaskAdapter);

        // Add Task launch on card (only visible/usable in edit mode, but kept for create too)
        addTaskCard.setOnClickListener(v -> {
            AddTaskBottomSheet sheet = AddTaskBottomSheet.newInstance();
            sheet.setOnTaskActionListener(new AddTaskBottomSheet.OnTaskActionListener() {
                @Override
                public void onTaskCreated(Task task) {
                    task.setHabit(true);
                    task.setHabitId(habit.getId());
                    task.setRecurringType(Task.RecurringType.DAILY);
                    taskManager.addTask(task);
                    subTaskAdapter.addTask(task);
                    subTasksRecyclerView.scrollToPosition(0);
                    tryAutoCompleteHabit();
                }

                @Override
                public void onTaskUpdated(Task task) {
                    taskManager.updateTask(task);
                    List<Task> current = subTaskAdapter.getTaskList();
                    int idx = -1;
                    for (int i = 0; i < current.size(); i++) {
                        if (current.get(i).getId().equals(task.getId())) {
                            idx = i;
                            break;
                        }
                    }
                    if (idx != -1) {
                        current.set(idx, task);
                        subTaskAdapter.notifyItemChanged(idx);
                    } else {
                        subTaskAdapter.addTask(task);
                        subTasksRecyclerView.scrollToPosition(0);
                    }
                    tryAutoCompleteHabit();
                }
            });
            sheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
        });

        subTaskAdapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task, int position) {
            }

            @Override
            public void onTaskCompleted(Task task, int position) {
            }

            @Override
            public void onTaskUncompleted(Task task, int position) {
            }

            @Override
            public void onTaskEdit(Task task, int position) {
                AddTaskBottomSheet sheet = AddTaskBottomSheet.newInstance(task);
                sheet.setOnTaskActionListener(new AddTaskBottomSheet.OnTaskActionListener() {
                    @Override
                    public void onTaskCreated(Task t) {
                    }

                    @Override
                    public void onTaskUpdated(Task t) {
                        taskManager.updateTask(t);
                        List<Task> current = subTaskAdapter.getTaskList();
                        for (int i = 0; i < current.size(); i++) {
                            if (current.get(i).getId().equals(t.getId())) {
                                current.set(i, t);
                                subTaskAdapter.notifyItemChanged(i);
                                break;
                            }
                        }
                        tryAutoCompleteHabit();
                    }
                });
                sheet.show(getSupportFragmentManager(), "EditTaskBottomSheet");
            }

            @Override
            public void onTaskDelete(Task task, int position) {
                taskManager.deleteTask(task.getId());
                subTaskAdapter.removeTask(position);
                tryAutoCompleteHabit();
            }
        });

        // Save habit
        btnDone.setOnClickListener(v -> {
            habit.setName(habitTitleEdit.getText() != null ? habitTitleEdit.getText().toString().trim() : "");
            habit.setReason(reasonEdit.getText() != null ? reasonEdit.getText().toString().trim() : "");
            habit.setEmoji(emojiTextView.getText().toString());
            int diffPos = difficultySpinner.getSelectedItemPosition();
            habit.setDifficulty(diffPos == 0 ? Habit.Difficulty.EASY : diffPos == 1 ? Habit.Difficulty.MEDIUM : Habit.Difficulty.HARD);
            habit.setGoodBad(goodBadSpinner.getSelectedItemPosition() == 0 ? Habit.GoodBad.GOOD : Habit.GoodBad.BAD);
            if (selectedUntilDate != null) {
                habit.setDurationType(Habit.DurationType.UNTIL_DATE);
                habit.setUntilDate(selectedUntilDate);
            }

            List<Habit> list = habitManager.loadHabits();
            boolean found = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(habit.getId())) {
                    list.set(i, habit);
                    found = true;
                    break;
                }
            }
            if (!found) list.add(habit);
            habitManager.saveHabits(list);

            setResult(RESULT_OK, new Intent().putExtra(EXTRA_HABIT_ID, habit.getId()));
            finish();
        });
        applyColors();
    }

    private void initViews() {
        habitManager = new HabitManager(this);
        taskManager = new TaskManager(this);
        habitTitleEdit = findViewById(R.id.habitTitleEdit);
        emojiCard = findViewById(R.id.emojiCard);
        emojiTextView = findViewById(R.id.emojiText);
        dateCard = findViewById(R.id.calendarCard);
        dateTitleEdit = findViewById(R.id.dateTitleEdit);
        difficultySpinner = findViewById(R.id.difficultySpinner);
        goodBadSpinner = findViewById(R.id.goodBadSpinner);
        reasonEdit = findViewById(R.id.inputReason);
        addTaskCard = findViewById(R.id.addTaskFAB);
        btnDone = findViewById(R.id.btnDone);
        btnDelete = findViewById(R.id.btnDelete);
        subTasksRecyclerView = findViewById(R.id.subTasksRecyclerView);
        blurTarget = findViewById(R.id.blurTarget);
    }

    private void applyColors() {
        View arc1 = findViewById(R.id.arcTopLeft);
        View arc2 = findViewById(R.id.arcBottomRight);
        View arc3 = findViewById(R.id.arcBottomLeft);
        MaterialCardView nameCard = findViewById(R.id.nameCard);
        MaterialCardView sectionCard = findViewById(R.id.sectionCard);
        Utils.applySecondaryColor(nameCard, this);
        Utils.applySecondaryColor(sectionCard, this);
        Utils.applyAccentColors(arc1, arc2, arc3, this);
    }

    private void setupUI() {
        BlurView nameBlur = findViewById(R.id.nameBlur);
        BlurView descBlur = findViewById(R.id.descBlur);

        nameBlur.setupWith(blurTarget).setBlurRadius(5f);
        descBlur.setupWith(blurTarget).setBlurRadius(5f);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private String formatDate(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("MMM dd", Locale.getDefault()).format(date);
    }

    private void showHabitDatePicker(TextView dateText) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetTheme);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_picker, null);
        android.widget.CalendarView calendarView = dialogView.findViewById(R.id.calendarView);
        View setTimeButton = dialogView.findViewById(R.id.setTimeButton);
        View repeatButton = dialogView.findViewById(R.id.repeatButton);
        View cancelButton = dialogView.findViewById(R.id.cancelButton);
        View doneButton = dialogView.findViewById(R.id.doneButton);

        // Hide time and repeat for habits
        setTimeButton.setVisibility(View.GONE);
        repeatButton.setVisibility(View.GONE);

        Calendar cal = Calendar.getInstance();
        calendarView.setDate(cal.getTimeInMillis());
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            selectedUntilDate = c.getTime();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        doneButton.setOnClickListener(v -> {
            if (selectedUntilDate == null) selectedUntilDate = new Date(calendarView.getDate());
            dateText.setText(formatDate(selectedUntilDate));
            dialog.dismiss();
        });

        dialog.setContentView(dialogView);
        dialog.show();
    }

    // If the user adds tasks and intends to complete them all today, auto mark habit done when list non-empty and user confirms all done.
    private void tryAutoCompleteHabit() {
        List<Task> current = subTaskAdapter.getTaskList();
        if (current.isEmpty()) return;
        // Auto mark done when all tasks added for the habit exist and user confirms
        new AlertDialog.Builder(this)
                .setTitle("Mark today complete?")
                .setMessage("Mark habit as completed for today since all tasks are set?")
                .setPositiveButton("Yes", (d, w) -> {
                    habit.markDoneToday();
                    List<Habit> list = habitManager.loadHabits();
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).getId().equals(habit.getId())) {
                            list.set(i, habit);
                            break;
                        }
                    }
                    habitManager.saveHabits(list);
                })
                .setNegativeButton("No", null)
                .show();
    }
}


