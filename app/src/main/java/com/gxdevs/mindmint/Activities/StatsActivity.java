package com.gxdevs.mindmint.Activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.gxdevs.mindmint.Utils.Utils.isAccessibilityPermissionGranted;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.gxdevs.mindmint.Views.CustomPieChart;
import com.gxdevs.mindmint.Fragments.StatsFocusFragment;
import com.gxdevs.mindmint.Fragments.StatsHabitsFragment;
import com.gxdevs.mindmint.Fragments.StatsTasksFragment;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.Utils;
import com.gxdevs.mindmint.Models.StatsViewModel;

import java.util.ArrayList;
import java.util.List;

public class StatsActivity extends AppCompatActivity {

    private StatsViewModel viewModel;
    private ImageView brain;
    private ActivityResultLauncher<Intent> accessibilityLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        Utils.setPad(findViewById(R.id.main), "bottom", this);

        viewModel = new ViewModelProvider(this).get(StatsViewModel.class);

        setupScrollStats();
        setupNavigation();
        setupViewPager();
        checkAndShowPermissionCard();
        registerForPermission();

        // Observe period label changes
        viewModel.getPeriodLabel().observe(this, label -> {
            TextView tv = findViewById(R.id.periodLabel);
            if (tv != null)
                tv.setText(label);
        });

        // Observe Mode
        viewModel.getIsWeekly().observe(this, this::updateWeeklyMonthlyTabs);
    }

    private void setupViewPager() {
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        StatsPagerAdapter adapter = new StatsPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("HABITS");
                    break;
                case 1:
                    tab.setText("FOCUS");
                    break;
                case 2:
                    tab.setText("TASKS");
                    break;
            }
        }).attach();
    }

    // Adapter
    private static class StatsPagerAdapter extends FragmentStateAdapter {

        public StatsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 1 -> new StatsFocusFragment();
                case 2 -> new StatsTasksFragment();
                default -> new StatsHabitsFragment();
            };
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    private void setupScrollStats() {
        CustomPieChart pieChart = findViewById(R.id.pieChartX);
        brain = findViewById(R.id.brainImage);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int instaScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "insta");
        int ytScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "yt");
        int snapScrolls = Utils.calculateTotalUsageScrolls(sharedPreferences, "snap");

        List<Integer> data = new ArrayList<>();
        List<String> segmentNames = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (instaScrolls > 0) {
            data.add(instaScrolls);
            segmentNames.add("Instagram");
            colors.add(ContextCompat.getColor(this, R.color.sexyInsta));
        }
        if (ytScrolls > 0) {
            data.add(ytScrolls);
            segmentNames.add("YouTube");
            colors.add(ContextCompat.getColor(this, R.color.sexyYt));
        }
        if (snapScrolls > 0) {
            data.add(snapScrolls);
            segmentNames.add("Snapchat");
            colors.add(ContextCompat.getColor(this, R.color.sexySnap));
        }

        // Convert list to array
        int[] pieChartColors = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            pieChartColors[i] = colors.get(i);
        }

        pieChart.setData(data, segmentNames, pieChartColors);
        int totalScrolls = ytScrolls + instaScrolls + snapScrolls;
        updateBrainImage(totalScrolls);

        TextView tvTotalScrolls = findViewById(R.id.tvTotalScrolls);
        tvTotalScrolls.setText("Total Scrolls: " + totalScrolls);

        setupStatRows(instaScrolls, ytScrolls, snapScrolls);
    }

    private void setupNavigation() {
        TextView btnWeekly = findViewById(R.id.btnWeekly);
        TextView btnMonthly = findViewById(R.id.btnMonthly);

        btnWeekly.setOnClickListener(v -> viewModel.setWeekly(true));
        btnMonthly.setOnClickListener(v -> viewModel.setWeekly(false));

        findViewById(R.id.arrowLeft).setOnClickListener(v -> viewModel.decrementOffset());
        findViewById(R.id.arrowRight).setOnClickListener(v -> viewModel.incrementOffset());
    }

    private void updateWeeklyMonthlyTabs(boolean isWeekly) {
        TextView btnWeekly = findViewById(R.id.btnWeekly);
        TextView btnMonthly = findViewById(R.id.btnMonthly);

        if (isWeekly) {
            btnWeekly.setBackgroundResource(R.drawable.bg_segment_selected);
            btnWeekly.setTextColor(getAttrColor(R.attr.text_primary));
            btnMonthly.setBackgroundResource(0);
            btnMonthly.setTextColor(getAttrColor(R.attr.text_tertiary));
        } else {
            btnMonthly.setBackgroundResource(R.drawable.bg_segment_selected);
            btnMonthly.setTextColor(getAttrColor(R.attr.text_primary));
            btnWeekly.setBackgroundResource(0);
            btnWeekly.setTextColor(getAttrColor(R.attr.text_tertiary));
        }
    }

    private void setupStatRows(int instaScrolls, int ytScrolls, int snapScrolls) {
        View statInstaView = findViewById(R.id.statInsta);
        ImageView instaIcon = statInstaView.findViewById(R.id.ivAppIcon);
        TextView instaName = statInstaView.findViewById(R.id.tvAppName);
        TextView instaValue = statInstaView.findViewById(R.id.tvAppValue);

        instaIcon.setImageResource(R.drawable.instagram);
        instaIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sexyInsta)));
        instaName.setText("Insta");
        instaValue.setText(String.valueOf(instaScrolls));

        View statYoutubeView = findViewById(R.id.statYoutube);
        ImageView ytIcon = statYoutubeView.findViewById(R.id.ivAppIcon);
        TextView ytName = statYoutubeView.findViewById(R.id.tvAppName);
        TextView ytValue = statYoutubeView.findViewById(R.id.tvAppValue);

        ytIcon.setImageResource(R.drawable.youtube);
        ytIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sexyYt)));
        ytName.setText("YT");
        ytValue.setText(String.valueOf(ytScrolls));

        View statSnapView = findViewById(R.id.statSnap);
        ImageView snapIcon = statSnapView.findViewById(R.id.ivAppIcon);
        TextView snapName = statSnapView.findViewById(R.id.tvAppName);
        TextView snapValue = statSnapView.findViewById(R.id.tvAppValue);

        snapIcon.setImageResource(R.drawable.snapchat);
        snapIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sexySnap)));
        snapName.setText("Snap");
        snapValue.setText(String.valueOf(snapScrolls));
    }

    private void updateBrainImage(int totalWastedScrolls) {
        int drawableId;
        if (totalWastedScrolls < 150)
            drawableId = R.drawable.brain1;
        else if (totalWastedScrolls < 300)
            drawableId = R.drawable.brain2;
        else if (totalWastedScrolls < 500)
            drawableId = R.drawable.brain3;
        else if (totalWastedScrolls < 700)
            drawableId = R.drawable.brain4;
        else if (totalWastedScrolls < 900)
            drawableId = R.drawable.brain5;
        else if (totalWastedScrolls < 1100)
            drawableId = R.drawable.brain6;
        else if (totalWastedScrolls < 1200)
            drawableId = R.drawable.brain7;
        else if (totalWastedScrolls < 1400)
            drawableId = R.drawable.brain8;
        else
            drawableId = R.drawable.brain9;
        brain.setImageResource(drawableId);
    }

    private void checkAndShowPermissionCard() {
        ConstraintLayout permissionCard = findViewById(R.id.permissionCard);
        if (!isAccessibilityPermissionGranted(this)) {
            permissionCard.setVisibility(VISIBLE);
            permissionCard.setOnClickListener(v -> reqAccess());
        } else {
            permissionCard.setVisibility(GONE);
        }
    }

    private void reqAccess() {
        Utils.showPermissionSheet(this, Utils.PermissionType.ACCESSIBILITY,
                new Utils.PermissionLauncher() {
                    @Override
                    public void launchAccessibility(Intent intent) {
                        if (accessibilityLauncher != null) {
                            accessibilityLauncher.launch(intent);
                        } else {
                            startActivity(intent);
                        }
                    }

                    @Override
                    public void launchBattery(Intent intent) {
                    }

                    @Override
                    public void launchNotification(String permission) {
                    }
                },
                () -> {
                    Toast.makeText(this, "Accessibility permission not granted.", Toast.LENGTH_SHORT).show();
                    checkAndShowPermissionCard();
                });
    }

    public void registerForPermission() {
        accessibilityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isAccessibilityPermissionGranted(this)) {
                        Toast.makeText(this, "Thank you for granting Accessibility permission!", Toast.LENGTH_SHORT)
                                .show();
                        checkAndShowPermissionCard();
                    } else {
                        Toast.makeText(this, "Accessibility permission not granted.", Toast.LENGTH_SHORT).show();
                        checkAndShowPermissionCard();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndShowPermissionCard();
    }

    private int getAttrColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}
