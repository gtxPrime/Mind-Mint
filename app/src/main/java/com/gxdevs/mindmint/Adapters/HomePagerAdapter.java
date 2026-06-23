package com.gxdevs.mindmint.Adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.gxdevs.mindmint.Fragments.HabitFragment;
import com.gxdevs.mindmint.Fragments.HomeFragment;
import com.gxdevs.mindmint.Fragments.SettingsFragment;
import com.gxdevs.mindmint.Fragments.TasksFragment;

public class HomePagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_TASKS    = 0;
    public static final int PAGE_HOME     = 1;
    public static final int PAGE_HABITS   = 2;
    public static final int PAGE_SETTINGS = 3;
    public static final int PAGE_COUNT    = 4;

    public HomePagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case PAGE_TASKS -> new TasksFragment();
            case PAGE_HABITS -> new HabitFragment();
            case PAGE_SETTINGS -> new SettingsFragment();
            default -> new HomeFragment();
        };
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
