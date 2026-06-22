package com.gxdevs.mindmint.Fragments;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apachat.swipereveallayout.core.SwipeLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.gxdevs.mindmint.Activities.FocusMode;
import com.gxdevs.mindmint.Adapters.TaskAdapter;
import com.gxdevs.mindmint.Models.Habit;
import com.gxdevs.mindmint.Models.Task;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.FocusService;
import com.gxdevs.mindmint.Utils.HabitManager;
import com.gxdevs.mindmint.Utils.MintCrystals;
import com.gxdevs.mindmint.Utils.StreakManager;
import com.gxdevs.mindmint.Utils.TaskManager;
import com.gxdevs.mindmint.Utils.TaskNotificationManager;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Utils.CustomDialogUtils;
import com.gxdevs.mindmint.Utils.AnimUtils;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TasksFragment extends Fragment implements TaskAdapter.OnTaskClickListener, AddTaskBottomSheet.OnTaskActionListener {
    private static final String KEY_TASK_CREATE_TUTORIAL_SHOWN = "task_create_tutorial_shown";
    private static final String KEY_TASK_SWIPE_FOCUS_TUTORIAL_SHOWN = "task_swipe_focus_tutorial_shown";
    private static final String KEY_TASK_SWIPE_LIST_TUTORIAL_SHOWN = "task_swipe_list_tutorial_shown";
    private static final String KEY_TASK_FOCUS_GUIDE_SHOWN = "task_focus_guide_shown";
    // Tracks whether this fragment has been seen at least once this app session
    private boolean tutorialsShownThisSession = false;
    /** Guards entrance animation — only plays on first tab visit. */
    private boolean firstResume = true;
    private View view;
    private MaterialButton addTaskButton;
    private EditText searchEditText;
    private ImageView filterButton;
    private RecyclerView tasksRecyclerView;
    private ConstraintLayout emptyStateLayout;
    private TextView emptyStateText;
    private HorizontalScrollView filterScrollView;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private String currentFilter = "All";
    private String currentSearchQuery = "";
    private TaskManager taskManager;
    private TaskNotificationManager notificationManager;
    private HabitManager habitManager;
    private StreakManager streakManager;
    private MintCrystals mintCrystals;
    private LinearLayout filterChipContainer;
    private SwipeLayout focusSwipeLayout;
    private androidx.cardview.widget.CardView currentFocusCard;
    private TextView currentFocusPriorityLabel;
    private TextView currentFocusDueDate;
    private TextView currentFocusTaskName;
    private ConstraintLayout currentFocusMarkButton;
    private Task currentFocusTask;
    private TextView currentFocusLabel;
    private ImageView currentFocusMenu;
    private TextView currentFocusMarkButtonText;
    private TextView dateDetails;
    private android.content.BroadcastReceiver focusUpdateReceiver;
    
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(requireContext(), "Notification permission granted", Toast.LENGTH_SHORT).show();
                    updateNotificationPermissionCard();
                } else {
                    Toast.makeText(requireContext(), "Notification permission denied. Task reminders won't work.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_tasks, container, false);

        taskManager = new TaskManager(requireContext());
        habitManager = new HabitManager(requireContext());
        notificationManager = new TaskNotificationManager(requireContext());
        streakManager = new StreakManager(requireContext());
        mintCrystals = new MintCrystals(requireContext());
        initViews();
        setupRecyclerView();
        setupClickListeners();
        updateNotificationPermissionCard();
        loadTasks();
        checkPermissionAndMoveOn();
        setupSearchAndFilter();
        preHideAnimatedViews(); // Prevent flash: views invisible until animateEntrance() fires in onResume

        focusUpdateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, android.content.Intent intent) {
                if (FocusService.ACTION_TASK_FOCUS_UPDATE.equals(intent.getAction())) {
                    // Full reload ensures the focus card hides/updates immediately
                    loadTasks();
                    updateCurrentFocusCardVisibility();
                }
            }
        };
        IntentFilter filter = new android.content.IntentFilter(FocusService.ACTION_TASK_FOCUS_UPDATE);
        ContextCompat.registerReceiver(requireContext(), focusUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        return view;
    }

    private void checkPermissionAndMoveOn() {
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        ConstraintLayout permissionCard = view.findViewById(R.id.permissionCard);
        if (permissionCard == null)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean allowed = alarmManager != null && alarmManager.canScheduleExactAlarms();
            permissionCard.setVisibility(allowed ? GONE : VISIBLE);
            if (!allowed) {
                permissionCard.setOnClickListener(v -> askForExactAlarmPermission());
            } else {
                permissionCard.setOnClickListener(null);
            }
        } else {
            permissionCard.setVisibility(GONE);
            permissionCard.setOnClickListener(null);
        }
    }

    private void askForExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Utils.showPermissionSheet(requireContext(), Utils.PermissionType.ALARM,
                    null,
                    () -> Toast.makeText(requireContext(), "Alarm permission is required for task reminders.",
                            Toast.LENGTH_SHORT).show());
        }
    }

    /** Returns true only when this fragment is the page currently shown by the host ViewPager2. */
    private boolean isCurrentPage(int expectedPageIndex) {
        if (getActivity() instanceof com.gxdevs.mindmint.Activities.HomeActivity) {
            androidx.viewpager2.widget.ViewPager2 vp =
                    getActivity().findViewById(com.gxdevs.mindmint.R.id.nav_host_container);
            if (vp != null) return vp.getCurrentItem() == expectedPageIndex;
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Run entrance animation only the first time this tab is actually VISIBLE.
        // ViewPager2 with offscreenPageLimit calls onResume for ALL pre-loaded fragments
        // when the Activity resumes — guard against that.
        if (firstResume && isCurrentPage(com.gxdevs.mindmint.Adapters.HomePagerAdapter.PAGE_TASKS)) {
            firstResume = false;
            if (view != null) view.post(this::animateEntrance);
        }
        checkPermissionAndMoveOn();
        updateNotificationPermissionCard();
        // Reload tasks when returning from FocusMode so the list and focus card
        // reflect any changes made during the focus session (status, time spent, etc.)
        loadTasks();
        // Only show tutorials on the very first time we land on this fragment (per session).
        // Returning from FocusMode repeatedly should not re-trigger tutorial balloons.
        if (!tutorialsShownThisSession) {
            tutorialsShownThisSession = true;
            view.postDelayed(this::checkAndShowTaskTutorials, 600);
        }
    }

    private void checkAndShowTaskTutorials() {
        if (getContext() == null)
            return;
        SharedPreferences prefs = requireContext().getSharedPreferences("AppData", Context.MODE_PRIVATE);
        boolean createShown = prefs.getBoolean(KEY_TASK_CREATE_TUTORIAL_SHOWN, false);
        boolean focusSwipeShown = prefs.getBoolean(KEY_TASK_SWIPE_FOCUS_TUTORIAL_SHOWN, false);
        boolean listSwipeShown = prefs.getBoolean(KEY_TASK_SWIPE_LIST_TUTORIAL_SHOWN, false);

        // 1. Create Task Tutorial (Only if empty)
        if (!createShown && (taskList == null || taskList.isEmpty())) {
            showTutorialBalloon(addTaskButton, "Add your first task and tap it to start a focus session!",
                    () -> prefs.edit().putBoolean(KEY_TASK_CREATE_TUTORIAL_SHOWN, true).apply(), 0.85f);
            return;
        }

        // 2. Task Focus Guide bottom sheet (shown once when tasks exist)
        checkAndShowTaskFocusGuide(prefs);

        // 3. Focus Card Swipe Tutorial (If Focus Card is visible)
        if (!focusSwipeShown && currentFocusCard != null && currentFocusCard.getVisibility() == VISIBLE) {
            showTutorialBalloon(currentFocusCard, "Tap the card to jump into your focus session ·  Swipe down for quick edit/delete",
                    () -> prefs.edit().putBoolean(KEY_TASK_SWIPE_FOCUS_TUTORIAL_SHOWN, true).apply(), 0.5f);
        }

        // 4. List Swipe Tutorial (If List has items)
        if (!listSwipeShown && taskAdapter != null && taskAdapter.getItemCount() > 0) {
            tasksRecyclerView.postDelayed(() -> {
                RecyclerView.ViewHolder vh = tasksRecyclerView.findViewHolderForAdapterPosition(0);
                if (vh != null) {
                    showTutorialBalloon(vh.itemView, "Tap to focus · Checkbox to mark done · Swipe left for edit/delete",
                            () -> prefs.edit().putBoolean(KEY_TASK_SWIPE_LIST_TUTORIAL_SHOWN, true).apply(), 0.5f);
                }
            }, 200);
        }
    }

    private void checkAndShowTaskFocusGuide(SharedPreferences prefs) {
        if (taskList == null || taskList.isEmpty()) return;
        if (prefs.getBoolean(KEY_TASK_FOCUS_GUIDE_SHOWN, false)) return;
        if (getContext() == null || getViewLifecycleOwner() == null) return;

        BottomSheetDialog guideSheet = new BottomSheetDialog(requireContext(), R.style.CustomBottomSheetTheme);
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_task_focus_guide,
                        view.findViewById(R.id.bottomSheetTaskFocusGuideLayout));

        sheetView.findViewById(R.id.crossBtn).setOnClickListener(v -> guideSheet.dismiss());
        sheetView.findViewById(R.id.gotItBtn).setOnClickListener(v -> guideSheet.dismiss());

        guideSheet.setContentView(sheetView);
        guideSheet.setOnDismissListener(d -> {
            prefs.edit().putBoolean(KEY_TASK_FOCUS_GUIDE_SHOWN, true).apply();
            // After dismissal, show an interaction balloon on the first task
            showTaskFocusInteractionBalloon();
        });
        guideSheet.show();
    }

    private void showTaskFocusInteractionBalloon() {
        if (taskAdapter == null || taskAdapter.getItemCount() == 0 || tasksRecyclerView == null) return;

        tasksRecyclerView.postDelayed(() -> {
            tasksRecyclerView.smoothScrollToPosition(0);
            tasksRecyclerView.postDelayed(() -> {
                RecyclerView.ViewHolder vh = tasksRecyclerView.findViewHolderForAdapterPosition(0);
                View target = (vh != null) ? vh.itemView : tasksRecyclerView.getChildAt(0);
                if (target != null && isAdded()) {
                    Balloon balloon = new Balloon.Builder(requireContext())
                            .setArrowSize(10)
                            .setArrowOrientation(ArrowOrientation.BOTTOM)
                            .setArrowPosition(0.5f)
                            .setWidthRatio(0.78f)
                            .setHeight(BalloonSizeSpec.WRAP)
                            .setTextSize(13f)
                            .setCornerRadius(10f)
                            .setAlpha(0.92f)
                            .setPadding(10)
                            .setText("Tap to start a focus session · Checkbox marks it done")
                            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            .setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brainColor))
                            .setBalloonAnimation(BalloonAnimation.ELASTIC)
                            .setDismissWhenClicked(true)
                            .setLifecycleOwner(getViewLifecycleOwner())
                            .build();
                    balloon.showAlignTop(target);
                }
            }, 300);
        }, 300);
    }

    private void showTutorialBalloon(View target, String text, Runnable onDismiss, float position) {
        if (target == null)
            return;
        Balloon balloon = new Balloon.Builder(requireContext())
                .setArrowSize(10)
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowPosition(position)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setTextSize(14f)
                .setCornerRadius(10f)
                .setAlpha(0.9f)
                .setPadding(8)
                .setText(text)
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                .setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brainColor))
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setDismissWhenClicked(true)
                .setLifecycleOwner(getViewLifecycleOwner())
                .setOnBalloonDismissListener(onDismiss::run)
                .build();

        balloon.showAlignBottom(target);
    }

    private void initViews() {
        ConstraintLayout focusEditButton = view.findViewById(R.id.editButton);
        ConstraintLayout focusDeleteButton = view.findViewById(R.id.deleteButton);
        addTaskButton = view.findViewById(R.id.addTasksBtn);
        searchEditText = view.findViewById(R.id.searchEditText);
        filterButton = view.findViewById(R.id.filterButton);
        tasksRecyclerView = view.findViewById(R.id.tasksRecyclerView);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        emptyStateText = view.findViewById(R.id.emptyStateText);
        filterScrollView = view.findViewById(R.id.filterScrollView);
        filterChipContainer = view.findViewById(R.id.filterChipContainer);
        focusSwipeLayout = view.findViewById(R.id.swipeLayout);
        currentFocusCard = view.findViewById(R.id.currentFocusCard);
        currentFocusPriorityLabel = view.findViewById(R.id.currentFocusPriorityLabel);
        currentFocusDueDate = view.findViewById(R.id.currentFocusDueDate);
        currentFocusTaskName = view.findViewById(R.id.currentFocusTaskName);
        currentFocusMarkButton = view.findViewById(R.id.currentFocusMarkButton);
        currentFocusLabel = view.findViewById(R.id.currentFocusLabel);
        currentFocusMenu = view.findViewById(R.id.currentFocusMenu);
        currentFocusMarkButtonText = view.findViewById(R.id.currentFocusMarkButtonText);
        dateDetails = view.findViewById(R.id.dateDetails);

        // Initialize card as hidden
        if (currentFocusCard != null) {
            currentFocusCard.setVisibility(View.GONE);
            currentFocusCard.setAlpha(0f);
        }

        if (currentFocusLabel != null) {
            currentFocusLabel.setVisibility(View.GONE);
            currentFocusLabel.setAlpha(0f);
        }
        if (currentFocusMenu != null) {
            currentFocusMenu.setVisibility(View.GONE);
            currentFocusMenu.setAlpha(0f);
        }

        if (currentFocusMarkButton != null) {
            currentFocusMarkButton.setOnClickListener(v -> {
                if (currentFocusTask != null && !currentFocusTask.isCompleted()) {
                    // Card action button ALWAYS opens focus mode for any task
                    int pos = findTaskPosition(currentFocusTask);
                    onTaskClick(currentFocusTask, Math.max(pos, 0));
                }
            });
        }

        if (currentFocusCard != null) {
            currentFocusCard.setOnClickListener(v -> {
                if (currentFocusTask != null && !currentFocusTask.isCompleted()) {
                    int pos = findTaskPosition(currentFocusTask);
                    onTaskClick(currentFocusTask, Math.max(pos, 0));
                }
            });
        }

        View currentFocusInner = view.findViewById(R.id.currentFocusInner);
        if (currentFocusInner != null) {
            currentFocusInner.setOnClickListener(v -> {
                if (currentFocusTask != null && !currentFocusTask.isCompleted()) {
                    int pos = findTaskPosition(currentFocusTask);
                    onTaskClick(currentFocusTask, Math.max(pos, 0));
                }
            });
        }

        // Setup focus card swipe actions
        if (focusEditButton != null) {
            focusEditButton.setOnClickListener(v -> {
                if (currentFocusTask != null) {
                    showEditTaskBottomSheet(currentFocusTask);
                }
                if (focusSwipeLayout != null) {
                    focusSwipeLayout.close(true);
                }
            });
        }

        if (focusDeleteButton != null) {
            focusDeleteButton.setOnClickListener(v -> {
                if (currentFocusTask != null) {
                    int position = findTaskPosition(currentFocusTask);
                    if (position < 0) {
                        position = 0;
                    }
                    onTaskDelete(currentFocusTask, position);
                }
                if (focusSwipeLayout != null) {
                    focusSwipeLayout.close(true);
                }
            });
        }
    }

    private int findTaskPosition(Task targetTask) {
        for (int i = 0; i < taskList.size(); i++) {
            if (taskList.get(i).getId().equals(targetTask.getId())) {
                return i;
            }
        }
        return -1;
    }

    private void setupRecyclerView() {
        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(requireContext(), taskList);
        taskAdapter.setOnTaskClickListener(this);
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        tasksRecyclerView.setAdapter(taskAdapter);
    }

    private void setupSearchAndFilter() {
        // Search functionality
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                currentSearchQuery = s.toString();
                filterTasks();
            }
        });

        // Filter chips with blurs
        String[] chipLabels = { "All", "Pending", "Completed", "High Priority", "Medium Priority", "Low Priority" };
        filterChipContainer.removeAllViews();

        final View[] selectedChip = { null };

        for (String label : chipLabels) {
            FrameLayout chipWrapper = new FrameLayout(requireContext());
            TextView chipText = new TextView(requireContext());
            chipText.setText(label);
            chipText.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            chipText.setTextSize(14f);
            chipText.setPadding(25, 12, 25, 12);
            chipText.setGravity(Gravity.CENTER);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(requireContext(), R.color.transparent));
            bg.setCornerRadius(50f);

            GradientDrawable bgSelected = new GradientDrawable();
            bgSelected.setColor(ContextCompat.getColor(requireContext(), R.color.sexyGrey));
            bgSelected.setCornerRadius(50f);

            chipText.setBackground(bg);
            chipWrapper.addView(chipText);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(8, 0, 8, 0);
            chipWrapper.setLayoutParams(params);
            chipWrapper.setOnClickListener(v -> {
                if (selectedChip[0] != null) {
                    TextView previousText = (TextView) ((FrameLayout) selectedChip[0]).getChildAt(0);
                    previousText.setBackground(bg);
                }
                chipText.setBackground(bgSelected);
                selectedChip[0] = chipWrapper;
                currentFilter = label;
                filterTasks();
            });

            filterChipContainer.addView(chipWrapper);
            if (label.equals("All")) {
                chipWrapper.performClick();
            }

        }
    }

    private void setupClickListeners() {
        AnimUtils.attachTouchRipple(addTaskButton);
        addTaskButton.setOnClickListener(v -> showAddTaskBottomSheet());
        AnimUtils.attachTouchRipple(filterButton);
        filterButton.setOnClickListener(v -> {
            if (filterScrollView.getVisibility() == VISIBLE) {
                filterScrollView.animate().alpha(0f).translationY(-8f).setDuration(200)
                        .withEndAction(() -> filterScrollView.setVisibility(GONE)).start();
            } else {
                filterScrollView.setAlpha(0f);
                filterScrollView.setTranslationY(-8f);
                filterScrollView.setVisibility(VISIBLE);
                filterScrollView.animate().alpha(1f).translationY(0f).setDuration(220)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            }
        });
    }

    /** Pre-hides views that animateEntrance() animates in, so there is no flash before onResume fires. */
    private void preHideAnimatedViews() {
        if (addTaskButton != null) { addTaskButton.setAlpha(0f); addTaskButton.setScaleX(0.6f); addTaskButton.setScaleY(0.6f); }
        if (searchEditText != null) searchEditText.setAlpha(0f);
        // filterScrollView starts GONE by default — do NOT hide it here, the filter button manages it
        if (tasksRecyclerView != null) tasksRecyclerView.setAlpha(0f);
    }

    /** Entrance animations for tasks screen — runs from onResume so it is visible. */
    private void animateEntrance() {
        if (addTaskButton != null) AnimUtils.bounceIn(addTaskButton, 30);
        if (searchEditText != null) AnimUtils.fadeIn(searchEditText, 0, 280);
        // filterScrollView excluded — starts GONE, revealed by filter button on demand
        if (tasksRecyclerView != null) AnimUtils.fadeIn(tasksRecyclerView, 120, 300);
    }

    private void filterTasks() {
        updateCurrentFocusCardVisibility();
        Task taskToExclude = (shouldShowCurrentFocusCard() && currentFocusTask != null) ? currentFocusTask : null;
        taskAdapter.filterTasks(currentSearchQuery, currentFilter, taskToExclude);
        updateEmptyState();
        updateRemainingTasksCount();
    }

    private void updateEmptyState() {
        boolean isListEmpty = taskAdapter.getItemCount() == 0;
        boolean isFocusVisible = currentFocusCard != null && currentFocusCard.getVisibility() == View.VISIBLE;
        boolean isEmpty = isListEmpty && !isFocusVisible;
        emptyStateLayout.setVisibility(isEmpty ? VISIBLE : GONE);
        tasksRecyclerView.setVisibility(isEmpty ? GONE : VISIBLE);

        if (isEmpty) {
            if (!currentSearchQuery.isEmpty()) {
                emptyStateText.setText(R.string.no_tasks_match_your_search);
            } else if (!currentFilter.equals("All")) {
                String curTxt = "No " + currentFilter.toLowerCase() + " tasks";
                emptyStateText.setText(curTxt);
            } else {
                emptyStateText.setText(R.string.no_tasks_found);
            }
        }
    }

    public void showAddTaskBottomSheet() {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstance();
        bottomSheet.setOnTaskActionListener(this);
        bottomSheet.show(getChildFragmentManager(), "AddTaskBottomSheet");
    }

    private void showEditTaskBottomSheet(Task task) {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstance(task);
        bottomSheet.setOnTaskActionListener(this);
        bottomSheet.show(getChildFragmentManager(), "EditTaskBottomSheet");
    }

    private void loadTasks() {
        taskList = taskManager.loadTasks();
        for (Task task : taskList) {
            if (task.getScheduledDate() != null && !task.isCompleted()) {
                notificationManager.scheduleTaskReminder(task);
            }
        }

        taskAdapter.updateTaskList(taskList);
        filterTasks(); // Apply initial filter and sorting (this will also update focus card)
        updateEmptyState();
        updateRemainingTasksCount();
    }

    private void updateCurrentFocusCard() {
        // Only update if card should be visible
        if (!shouldShowCurrentFocusCard()) {
            hideCurrentFocusCard();
            return;
        }

        if (taskList == null || taskList.isEmpty()) {
            currentFocusTask = null;
            hideCurrentFocusCard();
            return;
        }

        List<Task> incompleteTasks = new ArrayList<>();
        for (Task task : taskList) {
            if (!task.isCompleted()) {
                incompleteTasks.add(task);
            }
        }

        if (incompleteTasks.isEmpty()) {
            currentFocusTask = null;
            hideCurrentFocusCard();
            return;
        }

        incompleteTasks.sort((t1, t2) -> {
            int p1 = t1.getPriority() != null ? t1.getPriority().getValue() : 0;
            int p2 = t2.getPriority() != null ? t2.getPriority().getValue() : 0;
            int priorityCompare = Integer.compare(p2, p1); // Descending order (high to low)
            if (priorityCompare != 0) return priorityCompare;

            Date d1 = t1.getScheduledDate();
            Date d2 = t2.getScheduledDate();
            if (d1 != null && d2 != null) {
                return d1.compareTo(d2); // Ascending order (earliest first)
            } else if (d1 != null)
                return -1; // t1 has date, t2 doesn't - t1 comes first
            else if (d2 != null)
                return 1; // t2 has date, t1 doesn't - t2 comes first
            else
                return 0; // Both null, keep order
        });

        if (!incompleteTasks.isEmpty() && incompleteTasks.get(0) != null) {
            // First check if ANY task is currently IN_PROGRESS
            Task runningTask = null;
            for (Task task : incompleteTasks) {
                if ("IN_PROGRESS".equals(task.getFocusStatus())) {
                    runningTask = task;
                    break;
                }
            }
            
            currentFocusTask = runningTask != null ? runningTask : incompleteTasks.get(0);
            
            if (currentFocusLabel != null) {
                currentFocusLabel.setText(runningTask != null ? "Currently Working On" : "Suggested Task");
            }
            
            populateCurrentFocusCard(currentFocusTask);
            showCurrentFocusCard();
        } else {
            currentFocusTask = null;
            hideCurrentFocusCard();
        }
    }

    private boolean shouldShowCurrentFocusCard() {
        return currentFilter != null && currentFilter.equals("All") && (currentSearchQuery == null || currentSearchQuery.isEmpty());
    }

    private void updateCurrentFocusCardVisibility() {
        boolean shouldShow = shouldShowCurrentFocusCard();
        if (shouldShow) {
            updateCurrentFocusCard();
        } else {
            hideCurrentFocusCard();
        }
    }

    private void showCurrentFocusCard() {
        if (currentFocusCard == null)
            return;
        if (!shouldShowCurrentFocusCard()) {
            return;
        }

        currentFocusCard.clearAnimation();
        if (currentFocusLabel != null)
            currentFocusLabel.clearAnimation();
        if (currentFocusMenu != null)
            currentFocusMenu.clearAnimation();

        if (currentFocusCard.getVisibility() == View.GONE) {
            currentFocusCard.setAlpha(0f);
            currentFocusCard.setVisibility(View.VISIBLE);
            currentFocusCard.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        } else if (currentFocusCard.getAlpha() < 1f) {
            // If already visible but fading, ensure it's fully visible
            currentFocusCard.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        // Show label and menu
        if (currentFocusLabel != null) {
            if (currentFocusLabel.getVisibility() == View.GONE) {
                currentFocusLabel.setAlpha(0f);
                currentFocusLabel.setVisibility(View.VISIBLE);
                currentFocusLabel.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            } else if (currentFocusLabel.getAlpha() < 1f) {
                currentFocusLabel.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        }
        if (currentFocusMenu != null) {
            if (currentFocusMenu.getVisibility() == View.GONE) {
                currentFocusMenu.setAlpha(0f);
                currentFocusMenu.setVisibility(View.VISIBLE);
                currentFocusMenu.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            } else if (currentFocusMenu.getAlpha() < 1f) {
                currentFocusMenu.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        }
    }

    private void hideCurrentFocusCard() {
        if (currentFocusCard == null)
            return;

        currentFocusCard.animate().cancel();
        currentFocusCard.setVisibility(View.GONE);
        currentFocusCard.setAlpha(0f);

        // Hide label and menu
        if (currentFocusLabel != null) {
            currentFocusLabel.animate().cancel();
            currentFocusLabel.setVisibility(View.GONE);
            currentFocusLabel.setAlpha(0f);
        }
        if (currentFocusMenu != null) {
            currentFocusMenu.animate().cancel();
            currentFocusMenu.setVisibility(View.GONE);
            currentFocusMenu.setAlpha(0f);
        }
    }

    private void populateCurrentFocusCard(Task task) {
        if (task == null || currentFocusTaskName == null || currentFocusPriorityLabel == null || currentFocusDueDate == null || currentFocusMarkButton == null) {
            return;
        }

        // Set task name dynamically
        currentFocusTaskName.setText(task.getName());

        if (task.getPriority() != null) {
            String priorityText = task.getPriority().name();
            currentFocusPriorityLabel.setText(priorityText);

            int bgColor = getPriorityColor(task);
            int txtColor = getPriorityTxtColor(task);
            currentFocusPriorityLabel.setBackgroundTintList(ColorStateList.valueOf(bgColor));
            currentFocusPriorityLabel.setTextColor(txtColor);
        }

        if (task.getScheduledDate() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            Calendar taskCal = Calendar.getInstance();
            taskCal.setTime(task.getScheduledDate());
            Calendar today = Calendar.getInstance();
            Date now = new Date();
            boolean isPassed = task.getScheduledDate().before(now);
            String passedPrefix = isPassed ? "(passed) " : "";

            if (taskCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    taskCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                currentFocusDueDate.setText("Due " + passedPrefix + timeFormat.format(task.getScheduledDate()));
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
                currentFocusDueDate.setText("Due " + passedPrefix + dateFormat.format(task.getScheduledDate()) + " " + timeFormat.format(task.getScheduledDate()));
            }
        } else {
            currentFocusDueDate.setText("No due date");
        }

        // Set state-aware button label and priority badge
        boolean isRunning = "IN_PROGRESS".equals(task.getFocusStatus());
        if (currentFocusMarkButtonText != null) {
            currentFocusMarkButtonText.setText(isRunning ? "Go to Focus" : "Start Focus");
        }

        // Overlay priority label with a live "● WORKING" badge when task is running
        if (isRunning) {
            currentFocusDueDate.setText("Status: Task running");
            currentFocusPriorityLabel.setText("● WORKING");
            currentFocusPriorityLabel.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brainColor)));
            currentFocusPriorityLabel.setTextColor(android.graphics.Color.WHITE);
        }
    }

    private void animateMarkComplete() {
        if (currentFocusTask == null || currentFocusMarkButton == null)
            return;

        // Animate button background from white to green
        ValueAnimator bgAnimator = ValueAnimator.ofArgb(
                Color.WHITE,
                Color.parseColor("#34D399") // Green color
        );
        bgAnimator.setDuration(600);
        bgAnimator.addUpdateListener(animator -> {
            if (currentFocusMarkButton != null) {
                int color = (int) animator.getAnimatedValue();
                ColorStateList colorStateList = ColorStateList.valueOf(color);
                currentFocusMarkButton.setBackgroundTintList(colorStateList);
            }
        });

        int brandPinkColor = ContextCompat.getColor(requireContext(), R.color.brand_pink);
        ValueAnimator iconTintAnimator = ValueAnimator.ofArgb(
                Color.BLACK,
                brandPinkColor);
        iconTintAnimator.setDuration(600);
        iconTintAnimator.addUpdateListener(animator -> {
            if (currentFocusMarkButton != null) {
                int color = (int) animator.getAnimatedValue();
            }
        });

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(bgAnimator, iconTintAnimator);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (currentFocusTask != null && taskList != null) {
                    currentFocusTask.setCompleted(true);
                    currentFocusTask.setCompletedDate(new Date());
                    int position = taskList.indexOf(currentFocusTask);
                    onTaskCompleted(currentFocusTask, Math.max(position, 0));
                }
            }
        });
        animatorSet.start();
    }

    private void saveTasks() {
        taskManager.saveTasks(taskList);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (focusUpdateReceiver != null) {
            requireContext().unregisterReceiver(focusUpdateReceiver);
        }
        saveTasks();
    }

    @Override
    public void onTaskClick(Task task, int position) {
        if (task.isCompleted()) return;

        if (FocusService.isPublicFocusRun) {
            boolean isLockedIn = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean(FocusService.PREF_IS_LOCKED_IN, false);
            if (isLockedIn) {
                CustomDialogUtils.showCustomDialog(requireContext(),
                    "Locked In Mode Active",
                    "You cannot stop or switch sessions while Locked In Mode is active.",
                    "OK",
                    null,
                    null,
                    null);
                return;
            }
        }

        Runnable startNewFocus = () -> {
            // Stop any existing session just in case
            Intent stopIntent = new Intent(requireContext(), FocusService.class);
            stopIntent.setAction(FocusService.ACTION_STOP_TIMER);
            requireContext().startService(stopIntent);

            // Start new task-linked focus session
            Intent startIntent = new Intent(requireContext(), FocusService.class);
            startIntent.setAction(FocusService.ACTION_START_FOREGROUND_SERVICE);
            
            long durationMs = task.isFocusModeEnabled() ? (task.getFocusDurationMinutes() * 60000L) : 0L;
            startIntent.putExtra("durationInMillis", durationMs);
            startIntent.putExtra("topicName", task.getName());
            startIntent.putExtra(FocusService.EXTRA_TASK_ID, task.getId());
            startIntent.putExtra(FocusService.EXTRA_IS_OPEN_ENDED, durationMs == 0);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                requireContext().startForegroundService(startIntent);
            } else {
                requireContext().startService(startIntent);
            }
            
            Intent focusIntent = new Intent(requireContext(), FocusMode.class);
            focusIntent.putExtra(FocusService.EXTRA_TASK_ID, task.getId());
            focusIntent.putExtra("topicName", task.getName());
            focusIntent.putExtra(FocusService.EXTRA_IS_OPEN_ENDED, durationMs == 0);
            startActivity(focusIntent);
            Toast.makeText(requireContext(), "Starting focus: " + task.getName(), Toast.LENGTH_SHORT).show();
        };

        if (FocusService.isPublicFocusRun) {
            // Check if this task IS the one currently running
            boolean isThisTaskRunning = "IN_PROGRESS".equals(task.getFocusStatus());
            if (!isThisTaskRunning) {
                // Fallback: check persisted linked task id (handles stale in-memory status)
                String persistedTaskId = androidx.preference.PreferenceManager
                        .getDefaultSharedPreferences(requireContext())
                        .getString(FocusService.PREF_LINKED_TASK_ID, null);
                isThisTaskRunning = task.getId().equals(persistedTaskId);
            }

            if (isThisTaskRunning) {
                // Re-open the running session directly
                Intent focusIntent = new Intent(requireContext(), FocusMode.class);
                focusIntent.putExtra(FocusService.EXTRA_TASK_ID, task.getId());
                focusIntent.putExtra("topicName", task.getName());
                long durMs = task.isFocusModeEnabled() ? (task.getFocusDurationMinutes() * 60000L) : 0L;
                focusIntent.putExtra(FocusService.EXTRA_IS_OPEN_ENDED, durMs == 0);
                startActivity(focusIntent);
            } else {
                // Different task running: check whether it's a task-linked session or standalone.
                String persistedTaskId = androidx.preference.PreferenceManager
                        .getDefaultSharedPreferences(requireContext())
                        .getString(FocusService.PREF_LINKED_TASK_ID, null);
                boolean isLinkedSession = persistedTaskId != null && !persistedTaskId.isEmpty();

                if (isLinkedSession) {
                    // BUG-11 fix: for a task-linked session, open FocusMode so the user is asked
                    // "Did you complete the task?" before the session is force-stopped.
                    // We keep the new-task info in a holder so startNewFocus can be triggered
                    // after FocusMode handles the completion dialog (user will re-tap on return).
                    CustomDialogUtils.showCustomDialog(requireContext(),
                        "Active Session Detected",
                        "You already have an active focus session for another task. Would you like to view it to stop or complete it first?",
                        "View Session",
                        "Cancel",
                        () -> {
                            // Re-open the running FocusMode with the ACTIVE task id
                            Intent focusIntent = new Intent(requireContext(), FocusMode.class);
                            focusIntent.putExtra(FocusService.EXTRA_TASK_ID, persistedTaskId);
                            boolean persistedOE = androidx.preference.PreferenceManager
                                    .getDefaultSharedPreferences(requireContext())
                                    .getBoolean(FocusService.PREF_IS_OPEN_ENDED, false);
                            focusIntent.putExtra(FocusService.EXTRA_IS_OPEN_ENDED, persistedOE);
                            startActivity(focusIntent);
                        },
                        null);
                } else {
                    // Standalone session: safe to force-stop and start new
                    CustomDialogUtils.showCustomDialog(requireContext(),
                        "Active Session Detected",
                        "You have a standard focus session running. Would you like to stop it and start focusing on this task instead?",
                        "Stop and Start Task",
                        "Cancel",
                        () -> startNewFocus.run(),
                        null);
                }
            }
            return;
        }

        // No running session — jump directly
        startNewFocus.run();
    }

    @Override
    public void onTaskCompleted(Task task, int position) {
        if (!task.isCompleted()) {
            task.setCompleted(true);
            task.setCompletedDate(new Date());
        }
        if (!task.isHabit()) {
            mintCrystals.addCoins(2);
        }

        if (task.isRecurring()) {
            String nextRepeatDate = getNextRepeatDateString(task);
            Toast.makeText(requireContext(), "Task completed for today! Will repeat on " + nextRepeatDate, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), "Task completed: " + task.getName(), Toast.LENGTH_SHORT).show();
        }

        taskManager.updateTask(task);
        notificationManager.cancelTaskReminder(task);
        if (task.isHabit() && task.getHabitId() != null) {
            List<Task> tasks = taskManager.loadTasks();
            boolean allComplete = true;
            for (Task t : tasks) {
                if (task.getHabitId().equals(t.getHabitId()) && !t.isCompleted()) {
                    allComplete = false;
                    break;
                }
            }
            if (allComplete) {
                List<Habit> habits = habitManager.loadHabits();
                for (int i = 0; i < habits.size(); i++) {
                    if (habits.get(i).getId().equals(task.getHabitId())) {
                        habits.get(i).markDoneToday();
                        break;
                    }
                }
                habitManager.saveHabits(habits);
                streakManager.updateStreakOnHabitCompletion();
            }
        }

        loadTasks();
        updateCurrentFocusCardVisibility();
        updateRemainingTasksCount();
    }

    @Override
    public void onTaskUncompleted(Task task, int position) {
        if (!task.isHabit()) {
            mintCrystals.subtractCoins(2);
        }

        Toast.makeText(requireContext(), "Task marked as pending: " + task.getName(), Toast.LENGTH_SHORT).show();
        taskManager.updateTask(task);
        if (task.isHabit() && task.getHabitId() != null) {
            List<Habit> habits = habitManager.loadHabits();
            for (int i = 0; i < habits.size(); i++) {
                if (habits.get(i).getId().equals(task.getHabitId())) {
                    habits.get(i).unmarkToday();
                    break;
                }
            }
            habitManager.saveHabits(habits);
            streakManager.checkAndResetStreakIfNeeded(habits);
        }

        filterTasks();
        updateRemainingTasksCount();
    }

    @Override
    public void onTaskEdit(Task task, int position) {
        showEditTaskBottomSheet(task);
    }

    @Override
    public void onTaskDelete(Task task, int position) {
        CustomDialogUtils.showCustomDialog(requireContext(),
                "Delete Task",
                "Are you sure you want to delete this task?",
                "Delete",
                "Cancel",
                () -> {
                    // Cancel any scheduled notifications for requireContext() task
                    notificationManager.cancelTaskReminder(task);

                    // Remove from main task list using task ID
                    taskList.removeIf(t -> t.getId().equals(task.getId()));

                    // Update adapter with new task list
                    taskAdapter.updateTaskList(taskList);

                    // Re-apply current filter
                    filterTasks();

                    // Save to persistent storage
                    taskManager.deleteTask(task.getId());
                    updateEmptyState();
                    updateRemainingTasksCount();
                    Toast.makeText(requireContext(), "Task deleted", Toast.LENGTH_SHORT).show();
                },
                null);
    }

    @Override
    public void onTaskCreated(Task task) {
        taskList.add(task);
        taskAdapter.updateTaskList(taskList);
        // Update focus card first, then filter (which will exclude the focus task)
        updateCurrentFocusCardVisibility();
        filterTasks(); // Apply current filter to newly added task
        taskManager.addTask(task);

        // Schedule notification if task has scheduled date
        if (task.getScheduledDate() != null) notificationManager.scheduleTaskReminder(task);

        updateEmptyState();
        updateRemainingTasksCount();

        // Show guide if this is the very first task the user created
        SharedPreferences prefs = requireContext().getSharedPreferences("AppData", Context.MODE_PRIVATE);
        boolean guideAlreadyShown = prefs.getBoolean(KEY_TASK_FOCUS_GUIDE_SHOWN, false);
        if (taskList.size() == 1 && !guideAlreadyShown) {
            // First task ever — delay slightly so the card animates in first
            view.postDelayed(() -> checkAndShowTaskFocusGuide(prefs), 800);
        } else {
            // Not the first task — just run normal tutorial check (swipe hints etc.)
            view.postDelayed(this::checkAndShowTaskTutorials, 300);
        }

        Toast.makeText(requireContext(), "Task added successfully", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTaskUpdated(Task task) {
        // Task object is already updated in place since we pass the reference
        taskAdapter.updateTaskList(taskList);
        filterTasks(); // Re-apply filter to show updated task in correct position
        taskManager.updateTask(task);

        // Cancel existing notifications and reschedule if task has scheduled date
        notificationManager.cancelTaskReminder(task);
        if (task.getScheduledDate() != null) {
            notificationManager.scheduleTaskReminder(task);
        }

        updateRemainingTasksCount();
        // No tutorial trigger on update — edits don't need to re-show hints
        Toast.makeText(requireContext(), "Task updated successfully", Toast.LENGTH_SHORT).show();
    }

    private String getNextRepeatDateString(Task task) {
        if (!task.isRecurring()) {
            return "never";
        }

        Calendar nextDate = Calendar.getInstance();

        switch (task.getRecurringType()) {
            case WEEKLY:
                nextDate.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case MONTHLY:
                nextDate.add(Calendar.MONTH, 1);
                break;
            default:
                nextDate.add(Calendar.DAY_OF_MONTH, 1);
                break;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        return dateFormat.format(nextDate.getTime());
    }

    private int getPriorityColor(Task task) {
        if (task.getPriority() == null) {
            return Color.parseColor("#1B3D37");
        }
        return switch (task.getPriority()) {
            case HIGH -> Color.parseColor("#4e2A35");
            case MEDIUM -> Color.parseColor("#3C3020");
            default -> Color.parseColor("#1B3D37");
        };
    }

    private int getPriorityTxtColor(Task task) {
        if (task.getPriority() == null) {
            return Color.parseColor("#34D399");
        }
        return switch (task.getPriority()) {
            case HIGH -> Color.parseColor("#FB7185");
            case MEDIUM -> Color.parseColor("#E3AE24");
            default -> Color.parseColor("#34D399");
        };
    }

    private void updateNotificationPermissionCard() {
        if (view == null)
            return;
        ConstraintLayout notiPermissionCard = view.findViewById(R.id.notiPermissionCard);
        if (notiPermissionCard == null)
            return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notiPermissionCard.setVisibility(GONE);
            notiPermissionCard.setOnClickListener(null);
            return;
        }

        boolean granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        notiPermissionCard.setVisibility(granted ? GONE : VISIBLE);

        if (!granted) {
            notiPermissionCard
                    .setOnClickListener(v -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS));
        } else {
            notiPermissionCard.setOnClickListener(null);
        }
    }

    private void updateRemainingTasksCount() {
        if (dateDetails == null)
            return;

        int remainingCount = 0;
        for (Task task : taskList) {
            if (!task.isCompleted()) {
                remainingCount++;
            }
        }

        String countText = remainingCount + " REMAINING";
        dateDetails.setText(countText);
    }

}