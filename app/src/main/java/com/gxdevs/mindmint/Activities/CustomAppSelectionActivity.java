package com.gxdevs.mindmint.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.gxdevs.mindmint.Adapters.AppSelectionAdapter;
import com.gxdevs.mindmint.Models.AppInfo;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Services.AppUsageAccessibilityService;
import com.gxdevs.mindmint.Utils.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomAppSelectionActivity extends AppCompatActivity implements AppSelectionAdapter.OnAppBlockStateChangedListener {

    /** Intent extra: pass "blacklist" (default) or "whitelist" to switch modes. */
    public static final String EXTRA_MODE = "extra_app_selection_mode";
    public static final String MODE_BLACKLIST = "blacklist";
    public static final String MODE_WHITELIST = "whitelist";

    private RecyclerView recyclerViewApps;
    private AppSelectionAdapter adapter;
    private final List<AppInfo> appList = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private Set<String> selectedPackages;
    private CircularProgressIndicator progressBar;

    private String mode = MODE_BLACKLIST;
    private String prefKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_custom_app_selection);

        Utils.setPad(findViewById(R.id.main), "bottom", this);
        findViewById(R.id.backBtn).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        String intentMode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_WHITELIST.equals(intentMode)) {
            mode = MODE_WHITELIST;
        }
        prefKey = MODE_WHITELIST.equals(mode)
                ? AppUsageAccessibilityService.PREF_LOCKED_IN_EXTRA_WHITELIST
                : AppUsageAccessibilityService.PREF_CUSTOM_BLOCKED_APPS;

        applyModeVisuals();

        recyclerViewApps = findViewById(R.id.rv_apps_list);
        progressBar = findViewById(R.id.pb_loading_apps);
        recyclerViewApps.setLayoutManager(new LinearLayoutManager(this));

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        selectedPackages = new HashSet<>(sharedPreferences.getStringSet(prefKey, new HashSet<>()));

        adapter = new AppSelectionAdapter(this, appList, this);
        recyclerViewApps.setAdapter(adapter);

        loadInstalledApps();
    }

    private void applyModeVisuals() {
        boolean isWhitelist = MODE_WHITELIST.equals(mode);

        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        if (toolbarTitle != null) {
            toolbarTitle.setText(isWhitelist ? "Strict Focus Whitelist" : "Custom App Blocking");
        }

        View modeBadge = findViewById(R.id.modeBadge);
        TextView modeBadgeText = findViewById(R.id.modeBadgeText);
        if (modeBadge != null && modeBadgeText != null) {
            modeBadge.setVisibility(View.VISIBLE);
            if (isWhitelist) {
                modeBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#224CAF50")));
                modeBadgeText.setText("✓  Apps toggled ON will be ALLOWED through Strict Lock-In");
                modeBadgeText.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                modeBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#22FF5F5F")));
                modeBadgeText.setText("✕  Apps toggled ON will be BLOCKED during all focus sessions");
                modeBadgeText.setTextColor(Color.parseColor("#FF5F5F"));
            }
        }

        // Progress indicator color
        if (progressBar != null) {
            int color = isWhitelist ? Color.parseColor("#4CAF50") : Color.parseColor("#FFAFB2");
            progressBar.setIndicatorColor(color);
        }
    }

    private void loadInstalledApps() {
        String myPackageName = getPackageName();
        progressBar.setVisibility(View.VISIBLE);
        recyclerViewApps.setVisibility(View.GONE);

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            List<AppInfo> installedApps = new ArrayList<>();
            Set<String> addedPackages = new HashSet<>();

            for (ResolveInfo info : resolveInfos) {
                String packageName = info.activityInfo.packageName;
                try {
                    if (!packageName.equals(myPackageName) && !addedPackages.contains(packageName)) {
                        addedPackages.add(packageName);
                        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                        String appName = pm.getApplicationLabel(appInfo).toString();
                        Drawable icon = pm.getApplicationIcon(appInfo);
                        boolean isSelected = selectedPackages.contains(packageName);
                        installedApps.add(new AppInfo(appName, packageName, icon, isSelected));
                    }
                } catch (Exception e) {
                    Log.e("CustomAppSelection", "Error getting app info for " + packageName, e);
                }
            }

            installedApps.sort((a, b) -> a.appName.compareToIgnoreCase(b.appName));

            runOnUiThread(() -> {
                appList.clear();
                appList.addAll(installedApps);
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                recyclerViewApps.setVisibility(View.VISIBLE);

                if (appList.isEmpty()) {
                    Toast.makeText(this, "No user-installed apps found.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public void onAppBlockStateChanged(String packageName, boolean isBlocked) {
        if (isBlocked) {
            selectedPackages.add(packageName);
        } else {
            selectedPackages.remove(packageName);
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(prefKey, selectedPackages);

        boolean success = editor.commit();
        if (!success) {
            Toast.makeText(this, "Error saving app list!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}