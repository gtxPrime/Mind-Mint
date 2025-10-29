package com.gxdevs.mindmint.Fragments;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.gxdevs.mindmint.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RepeatOptionsBottomSheet extends BottomSheetDialogFragment {

    public interface OnRepeatOptionsListener {
        void onRepeatOptionsSelected(RepeatOptions repeatOptions);
    }

    public static class RepeatOptions {
        public enum FrequencyType {
            DAILY("day"), WEEKLY("week"), MONTHLY("month"), YEARLY("year");

            private final String displayName;

            FrequencyType(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        public enum EndType {
            NEVER, ON_DATE, AFTER_OCCURRENCES
        }

        public enum MonthlyType {
            BY_DATE, BY_WEEK_DAY
        }

        public int frequency = 1;
        public FrequencyType frequencyType = FrequencyType.DAILY;
        public Set<Integer> weekDays = new HashSet<>(); // 1=Sunday, 2=Monday, etc.
        public MonthlyType monthlyType = MonthlyType.BY_DATE;
        public Calendar startDate = Calendar.getInstance();
        public EndType endType = EndType.NEVER;
        public Calendar endDate = Calendar.getInstance();
        public int occurrences = 30;

        public String getDisplayText() {
            StringBuilder text = new StringBuilder();

            if (frequency == 1) {
                text.append("Every ").append(frequencyType.getDisplayName());
            } else {
                text.append("Every ").append(frequency).append(" ").append(frequencyType.getDisplayName()).append("s");
            }

            if (frequencyType == FrequencyType.WEEKLY && !weekDays.isEmpty()) {
                text.append(" on ");
                String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                List<String> selectedDays = new ArrayList<>();
                for (int day : weekDays) {
                    if (day >= 1 && day <= 7) {
                        selectedDays.add(dayNames[day - 1]);
                    }
                }
                text.append(String.join(", ", selectedDays));
            }

            return text.toString();
        }
    }

    private EditText frequencyNumber;
    private Spinner frequencyTypeSpinner;
    private LinearLayout weeklyOptionsContainer;
    private RadioGroup monthlyRadioGroup;
    private RadioButton radioMonthlyByDate, radioMonthlyByWeek;
    private TextView startDateText;
    private LinearLayout startDateContainer;
    private RadioGroup endRadioGroup;
    private RadioButton radioNever, radioOnDate, radioAfterOccurrences;
    private EditText endDateInput, occurrencesInput;
    private Button cancelButton, doneButton;

    // Week day buttons
    private MaterialButton[] weekDayButtons;
    private boolean[] selectedWeekDays = new boolean[7]; // Sunday to Saturday

    private OnRepeatOptionsListener listener;
    private RepeatOptions currentOptions;
    private Calendar selectedStartDate;

    public static RepeatOptionsBottomSheet newInstance(Calendar startDate) {
        RepeatOptionsBottomSheet fragment = new RepeatOptionsBottomSheet();
        Bundle args = new Bundle();
        args.putLong("start_date", startDate.getTimeInMillis());
        fragment.setArguments(args);
        return fragment;
    }

    public static RepeatOptionsBottomSheet newInstance(Calendar startDate, RepeatOptions existingOptions) {
        RepeatOptionsBottomSheet fragment = newInstance(startDate);
        fragment.currentOptions = existingOptions;
        return fragment;
    }

    public void setOnRepeatOptionsListener(OnRepeatOptionsListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_repeat_options, container, false);
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

        // Get start date from arguments
        if (getArguments() != null) {
            selectedStartDate = Calendar.getInstance();
            selectedStartDate.setTimeInMillis(getArguments().getLong("start_date"));
        } else {
            selectedStartDate = Calendar.getInstance();
        }

        initViews(view);
        setupSpinners();
        setupWeekDayButtons();
        setupClickListeners();

        if (currentOptions != null) {
            populateExistingOptions();
        } else {
            setDefaultOptions();
        }
    }

    private void initViews(View view) {
        frequencyNumber = view.findViewById(R.id.frequencyNumber);
        frequencyTypeSpinner = view.findViewById(R.id.frequencyTypeSpinner);
        weeklyOptionsContainer = view.findViewById(R.id.weeklyOptionsContainer);
        monthlyRadioGroup = view.findViewById(R.id.monthlyRadioGroup);
        radioMonthlyByDate = view.findViewById(R.id.radioMonthlyByDate);
        radioMonthlyByWeek = view.findViewById(R.id.radioMonthlyByWeek);
        startDateText = view.findViewById(R.id.startDateText);
        startDateContainer = view.findViewById(R.id.startDateContainer);
        endRadioGroup = view.findViewById(R.id.endRadioGroup);
        radioNever = view.findViewById(R.id.radioNever);
        radioOnDate = view.findViewById(R.id.radioOnDate);
        radioAfterOccurrences = view.findViewById(R.id.radioAfterOccurrences);
        endDateInput = view.findViewById(R.id.endDateInput);
        occurrencesInput = view.findViewById(R.id.occurrencesInput);
        cancelButton = view.findViewById(R.id.cancelButton);
        doneButton = view.findViewById(R.id.doneButton);

        // Week day buttons
        weekDayButtons = new MaterialButton[7];
        weekDayButtons[0] = view.findViewById(R.id.btnSunday);
        weekDayButtons[1] = view.findViewById(R.id.btnMonday);
        weekDayButtons[2] = view.findViewById(R.id.btnTuesday);
        weekDayButtons[3] = view.findViewById(R.id.btnWednesday);
        weekDayButtons[4] = view.findViewById(R.id.btnThursday);
        weekDayButtons[5] = view.findViewById(R.id.btnFriday);
        weekDayButtons[6] = view.findViewById(R.id.btnSaturday);
    }

    private void setupSpinners() {
        // Setup frequency type spinner
        String[] frequencyTypes = {"day", "week", "month", "year"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                frequencyTypes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequencyTypeSpinner.setAdapter(adapter);

        frequencyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateVisibilityBasedOnFrequency(position);
                updateMonthlyOptions();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupWeekDayButtons() {
        for (int i = 0; i < weekDayButtons.length; i++) {
            final int dayIndex = i;
            MaterialButton button = weekDayButtons[i];

            // Ensure button is visible and properly styled
            button.setVisibility(View.VISIBLE);
            updateWeekDayButtonAppearance(dayIndex);

            button.setOnClickListener(v -> {
                selectedWeekDays[dayIndex] = !selectedWeekDays[dayIndex];
                updateWeekDayButtonAppearance(dayIndex);

                // Debug log
                android.util.Log.d("RepeatOptions", "Day " + dayIndex + " clicked, selected: " + selectedWeekDays[dayIndex]);
            });
        }
    }

    private void updateWeekDayButtonAppearance(int dayIndex) {
        MaterialButton button = weekDayButtons[dayIndex];
        if (selectedWeekDays[dayIndex]) {
            // Selected state - green background
            button.setBackgroundTintList(getResources().getColorStateList(R.color.greenIcon, null));
            button.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            // Unselected state - white background
            button.setBackgroundTintList(getResources().getColorStateList(R.color.transparent, null));
            button.setStrokeColor(getResources().getColorStateList(R.color.sexyGrey, null));
            button.setStrokeWidth(20);
            button.setTextColor(getResources().getColor(R.color.white, null));
        }
        // Force refresh
        button.invalidate();
        button.requestLayout();
    }

    private void setupClickListeners() {
        frequencyNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateFrequencyDisplay();
            }
        });

        startDateContainer.setOnClickListener(v -> showStartDatePicker());

        endDateInput.setOnClickListener(v -> {
            radioOnDate.setChecked(true);
            showEndDatePicker();
        });

        occurrencesInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                radioAfterOccurrences.setChecked(true);
            }
        });

        // Set up radio button listeners to ensure only one is selected
        endRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // Enable/disable related inputs based on selection
            android.util.Log.d("RepeatOptions", "Radio button changed: " + checkedId);

            if (checkedId == R.id.radioOnDate) {
                endDateInput.setEnabled(true);
                endDateInput.setAlpha(1.0f);
                occurrencesInput.setEnabled(false);
                occurrencesInput.setAlpha(0.5f);
            } else if (checkedId == R.id.radioAfterOccurrences) {
                endDateInput.setEnabled(false);
                endDateInput.setAlpha(0.5f);
                occurrencesInput.setEnabled(true);
                occurrencesInput.setAlpha(1.0f);
            } else if (checkedId == R.id.radioNever) {
                endDateInput.setEnabled(false);
                endDateInput.setAlpha(0.5f);
                occurrencesInput.setEnabled(false);
                occurrencesInput.setAlpha(0.5f);
            }
        });

        // Ensure radio buttons are properly grouped
        radioNever.setOnClickListener(v -> {
            endRadioGroup.check(R.id.radioNever);
        });

        radioOnDate.setOnClickListener(v -> {
            endRadioGroup.check(R.id.radioOnDate);
        });

        radioAfterOccurrences.setOnClickListener(v -> {
            endRadioGroup.check(R.id.radioAfterOccurrences);
        });

        cancelButton.setOnClickListener(v -> dismiss());
        doneButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRepeatOptionsSelected(buildRepeatOptions());
            }
            dismiss();
        });
    }

    private void updateVisibilityBasedOnFrequency(int position) {
        // Hide all optional containers first
        weeklyOptionsContainer.setVisibility(View.GONE);
        monthlyRadioGroup.setVisibility(View.GONE);

        switch (position) {
            case 1: // weekly
                weeklyOptionsContainer.setVisibility(View.VISIBLE);
                // Select current day by default if none selected
                if (!hasAnyWeekDaySelected()) {
                    int currentDay = selectedStartDate.get(Calendar.DAY_OF_WEEK) - 1; // Convert to 0-based
                    selectedWeekDays[currentDay] = true;
                    updateWeekDayButtonAppearance(currentDay);
                }
                // Force layout update
                weeklyOptionsContainer.requestLayout();
                break;
            case 2: // monthly
                monthlyRadioGroup.setVisibility(View.VISIBLE);
                monthlyRadioGroup.requestLayout();
                break;
        }
    }

    private boolean hasAnyWeekDaySelected() {
        for (boolean selected : selectedWeekDays) {
            if (selected) return true;
        }
        return false;
    }

    private void updateFrequencyDisplay() {
        // This could be used to update plural forms in the spinner if needed
    }

    private void updateMonthlyOptions() {
        if (monthlyRadioGroup.getVisibility() == View.VISIBLE) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());
            String dayOfMonth = dayFormat.format(selectedStartDate.getTime());
            radioMonthlyByDate.setText("Day " + dayOfMonth);

            // Calculate week of month and day name
            int weekOfMonth = selectedStartDate.get(Calendar.WEEK_OF_MONTH);
            String[] weekNames = {"First", "Second", "Third", "Fourth", "Fifth"};
            String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            int dayOfWeek = selectedStartDate.get(Calendar.DAY_OF_WEEK) - 1;

            String weekName = weekOfMonth <= weekNames.length ? weekNames[weekOfMonth - 1] : "Last";
            radioMonthlyByWeek.setText(weekName + " " + dayNames[dayOfWeek]);
        }
    }

    private void showStartDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                R.style.DatePickerTheme,
                (view, year, month, dayOfMonth) -> {
                    selectedStartDate.set(year, month, dayOfMonth);
                    updateStartDateDisplay();
                    updateMonthlyOptions();
                },
                selectedStartDate.get(Calendar.YEAR),
                selectedStartDate.get(Calendar.MONTH),
                selectedStartDate.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void showEndDatePicker() {
        Calendar endCal = Calendar.getInstance();
        endCal.add(Calendar.MONTH, 1); // Default to 1 month from now

        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                R.style.DatePickerTheme,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedEndDate = Calendar.getInstance();
                    selectedEndDate.set(year, month, dayOfMonth);
                    SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                    endDateInput.setText(format.format(selectedEndDate.getTime()));
                },
                endCal.get(Calendar.YEAR),
                endCal.get(Calendar.MONTH),
                endCal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void updateStartDateDisplay() {
        SimpleDateFormat format = new SimpleDateFormat("dd MMMM", Locale.getDefault());
        startDateText.setText(format.format(selectedStartDate.getTime()));
    }

    private void setDefaultOptions() {
        frequencyNumber.setText("1");
        frequencyTypeSpinner.setSelection(0); // daily
        updateStartDateDisplay();
        radioNever.setChecked(true);
        occurrencesInput.setText("30");

        // Set initial enabled/disabled states
        endDateInput.setEnabled(false);
        occurrencesInput.setEnabled(false);

        // Initialize all weekday buttons as unselected
        for (int i = 0; i < selectedWeekDays.length; i++) {
            selectedWeekDays[i] = false;
            updateWeekDayButtonAppearance(i);
        }
    }

    private void populateExistingOptions() {
        frequencyNumber.setText(String.valueOf(currentOptions.frequency));
        frequencyTypeSpinner.setSelection(currentOptions.frequencyType.ordinal());

        // Set week days if weekly
        if (currentOptions.frequencyType == RepeatOptions.FrequencyType.WEEKLY) {
            for (int day : currentOptions.weekDays) {
                if (day >= 1 && day <= 7) {
                    selectedWeekDays[day - 1] = true;
                    updateWeekDayButtonAppearance(day - 1);
                }
            }
        }

        // Set monthly options
        if (currentOptions.frequencyType == RepeatOptions.FrequencyType.MONTHLY) {
            if (currentOptions.monthlyType == RepeatOptions.MonthlyType.BY_DATE) {
                radioMonthlyByDate.setChecked(true);
            } else {
                radioMonthlyByWeek.setChecked(true);
            }
        }

        selectedStartDate = (Calendar) currentOptions.startDate.clone();
        updateStartDateDisplay();

        // Set end options
        switch (currentOptions.endType) {
            case NEVER:
                radioNever.setChecked(true);
                break;
            case ON_DATE:
                radioOnDate.setChecked(true);
                SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                endDateInput.setText(format.format(currentOptions.endDate.getTime()));
                break;
            case AFTER_OCCURRENCES:
                radioAfterOccurrences.setChecked(true);
                occurrencesInput.setText(String.valueOf(currentOptions.occurrences));
                break;
        }
    }

    private RepeatOptions buildRepeatOptions() {
        RepeatOptions options = new RepeatOptions();

        try {
            options.frequency = Integer.parseInt(frequencyNumber.getText().toString());
        } catch (NumberFormatException e) {
            options.frequency = 1;
        }

        options.frequencyType = RepeatOptions.FrequencyType.values()[frequencyTypeSpinner.getSelectedItemPosition()];

        // Set week days if weekly
        if (options.frequencyType == RepeatOptions.FrequencyType.WEEKLY) {
            options.weekDays.clear();
            for (int i = 0; i < selectedWeekDays.length; i++) {
                if (selectedWeekDays[i]) {
                    options.weekDays.add(i + 1); // Convert to 1-based (Sunday = 1)
                }
            }
        }

        // Set monthly type
        if (options.frequencyType == RepeatOptions.FrequencyType.MONTHLY) {
            options.monthlyType = radioMonthlyByDate.isChecked() ?
                    RepeatOptions.MonthlyType.BY_DATE : RepeatOptions.MonthlyType.BY_WEEK_DAY;
        }

        options.startDate = (Calendar) selectedStartDate.clone();

        // Set end options
        if (radioNever.isChecked()) {
            options.endType = RepeatOptions.EndType.NEVER;
        } else if (radioOnDate.isChecked()) {
            options.endType = RepeatOptions.EndType.ON_DATE;
            // Parse end date from input if needed
        } else if (radioAfterOccurrences.isChecked()) {
            options.endType = RepeatOptions.EndType.AFTER_OCCURRENCES;
            try {
                options.occurrences = Integer.parseInt(occurrencesInput.getText().toString());
            } catch (NumberFormatException e) {
                options.occurrences = 30;
            }
        }

        return options;
    }
}
