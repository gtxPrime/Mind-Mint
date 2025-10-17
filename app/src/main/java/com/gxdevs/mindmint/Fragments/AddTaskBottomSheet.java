package com.gxdevs.mindmint.Fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.Gson;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Views.AnalogClockView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

public class AddTaskBottomSheet extends BottomSheetDialogFragment {

    public interface OnTaskActionListener {
        void onTaskCreated(Task task);

        void onTaskUpdated(Task task);
    }

    private EditText taskNameInput;
    private Spinner prioritySpinner;
    private TextView dueDateText;
    private MaterialSwitch reminderSwitch;
    private Button cancelButton, saveButton;
    private TextView sheetTitle;
    private TextView emojiTextView;
    private MaterialCardView emojiCard;

    private OnTaskActionListener listener;
    private Task editingTask;
    private Calendar selectedDateTime;
    private boolean isEditMode = false;
    private RepeatOptionsBottomSheet.RepeatOptions currentRepeatOptions;

    public static AddTaskBottomSheet newInstance() {
        return new AddTaskBottomSheet();
    }

    public static AddTaskBottomSheet newInstance(Task task) {
        AddTaskBottomSheet fragment = new AddTaskBottomSheet();
        fragment.editingTask = task;
        fragment.isEditMode = true;
        return fragment;
    }

    public void setOnTaskActionListener(OnTaskActionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_task, container, false);
    }

    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupSpinners();
        setupDateTimePicker();
        setupClickListeners();

        if (isEditMode && editingTask != null) {
            populateFieldsForEdit();
        } else {
            setDefaultValues();
        }
    }

    private void initViews(View view) {
        taskNameInput = view.findViewById(R.id.taskNameInput);
        prioritySpinner = view.findViewById(R.id.prioritySpinner);
        dueDateText = view.findViewById(R.id.dueDateText);
        reminderSwitch = view.findViewById(R.id.reminderSwitch);
        cancelButton = view.findViewById(R.id.cancelButton);
        saveButton = view.findViewById(R.id.saveButton);
        sheetTitle = view.findViewById(R.id.sheetTitle);
        emojiTextView = view.findViewById(R.id.emojiText);
        emojiCard = view.findViewById(R.id.emojiCard);

        selectedDateTime = Calendar.getInstance();
        
        // Setup emoji picker
        setupEmojiPicker();
    }

    private void setupSpinners() {
        // Setup priority spinner
        ArrayAdapter<Task.Priority> priorityAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item,
                Task.Priority.values()
        );
        priorityAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        prioritySpinner.setAdapter(priorityAdapter);
    }

    private void setupEmojiPicker() {
        // Emoji picker - cycles through emojis on tap
        emojiCard.setOnClickListener(v -> {
            String current = emojiTextView.getText().toString();
            String[] options = new String[]{"✅", "📝", "🎯", "🔥", "💡", "⭐", "🏃", "📚", "💪", "🎨"};
            int idx = 0;
            for (int i = 0; i < options.length; i++)
                if (options[i].equals(current)) {
                    idx = i;
                    break;
                }
            emojiTextView.setText(options[(idx + 1) % options.length]);
        });
    }

    private void setupDateTimePicker() {
        View dueDateClickable = requireView().findViewById(R.id.dueDateClickable);
        dueDateClickable.setOnClickListener(v -> showDateTimePicker());
        dueDateText.setOnClickListener(v -> showDateTimePicker());
    }

    private void showDateTimePicker() {
        showCustomDatePicker();
    }

    private void showCustomDatePicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_date_picker, null);

        CalendarView calendarView = dialogView.findViewById(R.id.calendarView);
        LinearLayout setTimeButton = dialogView.findViewById(R.id.setTimeButton);
        LinearLayout repeatButton = dialogView.findViewById(R.id.repeatButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button doneButton = dialogView.findViewById(R.id.doneButton);

        // Set current date
        calendarView.setDate(selectedDateTime.getTimeInMillis());

        AlertDialog dialog = builder.setView(dialogView).create();

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDateTime.set(Calendar.YEAR, year);
            selectedDateTime.set(Calendar.MONTH, month);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        });

        setTimeButton.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomTimePicker();
        });

        repeatButton.setOnClickListener(v -> {
            dialog.dismiss();
            showRepeatOptionsDialog();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        doneButton.setOnClickListener(v -> {
            updateDateTimeDisplay();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showCustomTimePicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_picker, null);

        TextView hourDisplay = dialogView.findViewById(R.id.hourDisplay);
        TextView minuteDisplay = dialogView.findViewById(R.id.minuteDisplay);
        TextView amButton = dialogView.findViewById(R.id.amButton);
        TextView pmButton = dialogView.findViewById(R.id.pmButton);
        AnalogClockView analogClock = dialogView.findViewById(R.id.analogClock);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button okButton = dialogView.findViewById(R.id.okButton);

        // Initialize with current time
        int currentHour = selectedDateTime.get(Calendar.HOUR);
        int currentMinute = selectedDateTime.get(Calendar.MINUTE);
        boolean isPM = selectedDateTime.get(Calendar.AM_PM) == Calendar.PM;

        if (currentHour == 0) currentHour = 12;

        updateTimeDisplay(hourDisplay, minuteDisplay, amButton, pmButton, currentHour, currentMinute, isPM);
        analogClock.setTime(currentHour, currentMinute);

        AlertDialog dialog = builder.setView(dialogView).create();

        // Clock interaction
        analogClock.setOnTimeChangeListener((hour, minute) -> updateTimeDisplay(hourDisplay, minuteDisplay, amButton, pmButton, hour, minute, isPM));

        // AM/PM toggle
        amButton.setOnClickListener(v -> updateTimeDisplay(hourDisplay, minuteDisplay, amButton, pmButton,
                Integer.parseInt(hourDisplay.getText().toString()),
                Integer.parseInt(minuteDisplay.getText().toString()), false));

        pmButton.setOnClickListener(v -> updateTimeDisplay(hourDisplay, minuteDisplay, amButton, pmButton,
                Integer.parseInt(hourDisplay.getText().toString()),
                Integer.parseInt(minuteDisplay.getText().toString()), true));

        // Hour display click
        hourDisplay.setOnClickListener(v -> {
            analogClock.setSelectingHour(true);
            hourDisplay.setBackgroundResource(R.drawable.time_picker_selected_bg);
            minuteDisplay.setBackgroundResource(R.drawable.background_outline);
        });

        // Minute display click
        minuteDisplay.setOnClickListener(v -> {
            analogClock.setSelectingHour(false);
            hourDisplay.setBackgroundResource(R.drawable.background_outline);
            minuteDisplay.setBackgroundResource(R.drawable.time_picker_selected_bg);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        okButton.setOnClickListener(v -> {
            int finalHour = Integer.parseInt(hourDisplay.getText().toString());
            int finalMinute = Integer.parseInt(minuteDisplay.getText().toString());
            boolean finalIsPM = Objects.equals(pmButton.getBackground().getConstantState(), Objects.requireNonNull(ContextCompat.getDrawable(requireContext(), R.drawable.time_picker_selected_bg)).getConstantState());

            // Convert to 24-hour format
            if (finalIsPM && finalHour != 12) {
                finalHour += 12;
            } else if (!finalIsPM && finalHour == 12) {
                finalHour = 0;
            }

            selectedDateTime.set(Calendar.HOUR_OF_DAY, finalHour);
            selectedDateTime.set(Calendar.MINUTE, finalMinute);
            updateDateTimeDisplay();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateTimeDisplay(TextView hourDisplay, TextView minuteDisplay, TextView amButton, TextView pmButton, int hour, int minute, boolean isPM) {
        hourDisplay.setText(String.format(Locale.getDefault(), "%02d", hour));
        minuteDisplay.setText(String.format(Locale.getDefault(), "%02d", minute));

        if (isPM) {
            amButton.setBackgroundResource(R.drawable.background_outline);
            amButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColor));
            pmButton.setBackgroundResource(R.drawable.time_picker_selected_bg);
            pmButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        } else {
            amButton.setBackgroundResource(R.drawable.time_picker_selected_bg);
            amButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            pmButton.setBackgroundResource(R.drawable.background_outline);
            pmButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.textColor));
        }
    }

    private void updateDateTimeDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

        String dateStr = dateFormat.format(selectedDateTime.getTime());
        String timeStr = timeFormat.format(selectedDateTime.getTime());

        // Check if it's today
        Calendar today = Calendar.getInstance();
        if (selectedDateTime.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                selectedDateTime.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            dueDateText.setText("Today " + timeStr);
        } else {
            dueDateText.setText(dateStr + " " + timeStr);
        }
    }

    private void setupClickListeners() {
        cancelButton.setOnClickListener(v -> dismiss());

        saveButton.setOnClickListener(v -> {
            if (validateInput()) {
                if (isEditMode) {
                    updateTask();
                } else {
                    createTask();
                }
                dismiss();
            }
        });
    }

    private boolean validateInput() {
        String taskName = taskNameInput.getText().toString().trim();
        if (taskName.isEmpty()) {
            Toast.makeText(getContext(), "Task name is required", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void createTask() {
        String taskName = taskNameInput.getText().toString().trim();
        Task.Priority priority = (Task.Priority) prioritySpinner.getSelectedItem();
        Task.RecurringType recurringType = getRecurringTypeFromRepeatOptions();

        // Create task with placeholder image (will be replaced with emoji in adapter)
        Task newTask = new Task(taskName, "", R.drawable.todo, priority);
        newTask.setRecurringType(recurringType);
        newTask.setScheduledDate(selectedDateTime.getTime());
        newTask.setHasReminder(reminderSwitch.isChecked());
        newTask.setEmoji(emojiTextView.getText().toString());

        // Save advanced repeat options if available
        if (currentRepeatOptions != null) {
            Gson gson = new Gson();
            newTask.setRepeatOptionsJson(gson.toJson(currentRepeatOptions));
        }

        if (listener != null) {
            listener.onTaskCreated(newTask);
        }
    }

    private void updateTask() {
        String taskName = taskNameInput.getText().toString().trim();
        Task.Priority priority = (Task.Priority) prioritySpinner.getSelectedItem();
        Task.RecurringType recurringType = getRecurringTypeFromRepeatOptions();

        editingTask.setName(taskName);
        editingTask.setPriority(priority);
        editingTask.setRecurringType(recurringType);
        editingTask.setScheduledDate(selectedDateTime.getTime());
        editingTask.setHasReminder(reminderSwitch.isChecked());
        editingTask.setEmoji(emojiTextView.getText().toString());

        // Save advanced repeat options if available
        if (currentRepeatOptions != null) {
            Gson gson = new Gson();
            editingTask.setRepeatOptionsJson(gson.toJson(currentRepeatOptions));
        }

        if (listener != null) {
            listener.onTaskUpdated(editingTask);
        }
    }

    private Task.RecurringType getRecurringTypeFromRepeatOptions() {
        if (currentRepeatOptions == null) {
            return Task.RecurringType.NONE;
        }

        switch (currentRepeatOptions.frequencyType) {
            case DAILY:
                return Task.RecurringType.DAILY;
            case WEEKLY:
                return Task.RecurringType.WEEKLY;
            case MONTHLY:
                return Task.RecurringType.MONTHLY;
            case YEARLY:
                return Task.RecurringType.MONTHLY; // Use monthly as closest approximation
            default:
                return Task.RecurringType.NONE;
        }
    }

    private void populateFieldsForEdit() {
        sheetTitle.setText("Edit Task");
        saveButton.setText("Update");

        taskNameInput.setText(editingTask.getName());
        prioritySpinner.setSelection(editingTask.getPriority().ordinal());

        // Set emoji
        if (editingTask.getEmoji() != null && !editingTask.getEmoji().isEmpty()) {
            emojiTextView.setText(editingTask.getEmoji());
        }

        // Set scheduled date
        if (editingTask.getScheduledDate() != null) {
            selectedDateTime.setTime(editingTask.getScheduledDate());
            updateDateTimeDisplay();
        }

        // Set reminder switch
        reminderSwitch.setChecked(editingTask.hasReminder());

        // Load existing repeat options
        if (editingTask.getRepeatOptionsJson() != null && !editingTask.getRepeatOptionsJson().isEmpty()) {
            try {
                Gson gson = new Gson();
                currentRepeatOptions = gson.fromJson(editingTask.getRepeatOptionsJson(),
                        RepeatOptionsBottomSheet.RepeatOptions.class);
            } catch (Exception e) {
                // If parsing fails, ignore and use default options
                currentRepeatOptions = null;
            }
        }
    }

    private void setDefaultValues() {
        sheetTitle.setText("Add Task");
        saveButton.setText("Save");

        // Set default priority to Medium
        prioritySpinner.setSelection(1);

        // Set default time to current time + 1 hour
        selectedDateTime.add(Calendar.HOUR_OF_DAY, 1);
        updateDateTimeDisplay();
    }

    private void showRepeatOptionsDialog() {
        RepeatOptionsBottomSheet repeatSheet;
        if (currentRepeatOptions != null) {
            repeatSheet = RepeatOptionsBottomSheet.newInstance(selectedDateTime, currentRepeatOptions);
        } else {
            repeatSheet = RepeatOptionsBottomSheet.newInstance(selectedDateTime);
        }

        repeatSheet.setOnRepeatOptionsListener(repeatOptions -> {
            currentRepeatOptions = repeatOptions;
            // After setting repeat options, return to date picker
            showCustomDatePicker();
        });

        repeatSheet.show(getParentFragmentManager(), "RepeatOptionsBottomSheet");
    }

}
