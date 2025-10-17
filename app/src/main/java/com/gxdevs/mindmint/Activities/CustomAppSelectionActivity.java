package com.gxdevs.mindmint.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private RecyclerView recyclerViewApps;
    private AppSelectionAdapter adapter;
    private final List<AppInfo> appList = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private Set<String> blockedAppPackages;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_custom_app_selection);

        Utils.setPad(findViewById(R.id.main), "bottom", this);
        findViewById(R.id.backBtn).setOnClickListener(v -> onBackPressed());

        recyclerViewApps = findViewById(R.id.rv_apps_list);
        progressBar = findViewById(R.id.pb_loading_apps);
        recyclerViewApps.setLayoutManager(new LinearLayoutManager(this));

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        blockedAppPackages = new HashSet<>(sharedPreferences.getStringSet(AppUsageAccessibilityService.PREF_CUSTOM_BLOCKED_APPS, new HashSet<>()));

        adapter = new AppSelectionAdapter(this, appList, this);
        recyclerViewApps.setAdapter(adapter);

        loadInstalledApps();
    }

    private void loadInstalledApps() {
        String myPackageName = getPackageName();
        progressBar.setVisibility(View.VISIBLE);
        recyclerViewApps.setVisibility(View.GONE);

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            // Get all apps that can be launched
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            List<AppInfo> installedApps = new ArrayList<>();

            for (ResolveInfo info : resolveInfos) {
                String appName = info.loadLabel(pm).toString();
                String packageName = info.activityInfo.packageName;
                Drawable icon = info.loadIcon(pm);

                try {
                    if (!packageName.equals(myPackageName)) {
                        boolean isBlocked = blockedAppPackages.contains(packageName);
                        installedApps.add(new AppInfo(appName, packageName, icon, isBlocked));
                    }
                } catch (Exception e) {
                    Log.e("CustomAppSelection", "Error getting app info for " + packageName, e);
                }
            }

            // Sort by name
            installedApps.sort((app1, app2) -> app1.appName.compareToIgnoreCase(app2.appName));

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
            blockedAppPackages.add(packageName);
            Log.d("CustomAppSelection", "Adding to block list: " + packageName);
        } else {
            blockedAppPackages.remove(packageName);
            Log.d("CustomAppSelection", "Removing from block list: " + packageName);
        }
        // Persist immediately and check result for debugging
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(AppUsageAccessibilityService.PREF_CUSTOM_BLOCKED_APPS, blockedAppPackages);
        // Using commit() for immediate feedback during debugging
        boolean success = editor.commit();
        if (success) {
            Log.d("CustomAppSelection", "SharedPreferences commit successful. Current blocked apps: " + blockedAppPackages.toString());
        } else {
            Log.e("CustomAppSelection", "SharedPreferences commit FAILED. Blocked apps may not be saved.");
            Toast.makeText(this, "Error saving blocked apps list!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 