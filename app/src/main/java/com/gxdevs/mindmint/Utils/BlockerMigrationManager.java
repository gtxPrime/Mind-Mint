package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.gxdevs.mindmint.db.MindMintRoomDatabase;
import com.gxdevs.mindmint.db.dao.BlockedAppDao;
import com.gxdevs.mindmint.db.entities.BlockedAppEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockerMigrationManager {
    private static final String TAG = "BlockerMigrationManager";

    public static void migrateAndSeed(@NonNull Context context) {
        MindMintRoomDatabase db = MindMintRoomDatabase.getInstance(context);
        BlockedAppDao dao = db.blockedAppDao();
        
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences appDataPrefs = context.getSharedPreferences("AppData", Context.MODE_PRIVATE);

        // 1. Seed default apps if the database is empty
        if (dao.getCount() == 0) {
            Log.d(TAG, "Seeding default apps into database...");
            List<BlockedAppEntity> defaultApps = new ArrayList<>();

            // YouTube packages
            String[] ytPkgs = {
                    "com.google.android.youtube",
                    "com.rvx.android.youtube",
                    "com.revance.android.youtube"
            };
            for (String pkg : ytPkgs) {
                BlockedAppEntity app = new BlockedAppEntity();
                app.packageName = pkg;
                app.appName = "YouTube Shorts";
                app.isRestricted = false;
                app.scope = "section";
                app.useMod = false;
                app.sectionViewId = Utils.YtViewId;
                defaultApps.add(app);
            }

            // Instagram packages
            String[] instaPkgs = {
                    "com.instagram.android",
                    "com.myinsta.android",
                    "com.instafel.android",
                    "com.instander.android",
                    "com.instagold.android",
                    "com.instapro2.android",
                    "com.instaflow.android",
                    "cc.honista.app",
                    "com.instaprime.android"
            };
            for (String pkg : instaPkgs) {
                BlockedAppEntity app = new BlockedAppEntity();
                app.packageName = pkg;
                app.appName = "Instagram Reels";
                app.isRestricted = false;
                app.scope = "section";
                app.useMod = false;
                app.sectionViewId = Utils.instaViewId;
                defaultApps.add(app);
            }

            // Snapchat packages
            String[] snapPkgs = {
                    "com.snapchat.android"
            };
            for (String pkg : snapPkgs) {
                BlockedAppEntity app = new BlockedAppEntity();
                app.packageName = pkg;
                app.appName = "Snapchat Highlights";
                app.isRestricted = false;
                app.scope = "section";
                app.useMod = false;
                app.sectionViewId = Utils.snapViewId;
                defaultApps.add(app);
            }

            dao.insertAll(defaultApps);
        }

        // 2. Perform Migration of Legacy Settings from SharedPreferences
        boolean migrated = sharedPreferences.getBoolean("pref_blocker_db_migrated", false);
        if (!migrated) {
            Log.d(TAG, "Migrating legacy blocker configs to database...");

            // Load switch states
            boolean ytRestricted = sharedPreferences.getBoolean("ytSwitchState", false);
            boolean instaRestricted = sharedPreferences.getBoolean("instaSwitchState", false);
            boolean snapRestricted = sharedPreferences.getBoolean("snapSwitchState", false);

            boolean ytMod = appDataPrefs.getBoolean("YtMod", false);
            boolean instaMod = appDataPrefs.getBoolean("InstaMod", false);
            boolean snapMod = appDataPrefs.getBoolean("SnapMod", false);

            // Update database rows
            List<BlockedAppEntity> allApps = dao.getAllSync();
            for (BlockedAppEntity app : allApps) {
                boolean changed = false;
                if (app.packageName.contains("youtube")) {
                    app.isRestricted = ytRestricted;
                    app.useMod = ytMod;
                    changed = true;
                } else if (app.packageName.contains("insta") || app.packageName.contains("honista")) {
                    app.isRestricted = instaRestricted;
                    app.useMod = instaMod;
                    changed = true;
                } else if (app.packageName.contains("snapchat")) {
                    app.isRestricted = snapRestricted;
                    app.useMod = snapMod;
                    changed = true;
                }
                if (changed) {
                    dao.update(app);
                }
            }

            // Migrate Custom Blocked Apps
            Set<String> legacyCustomBlocked = sharedPreferences.getStringSet("custom_blocked_apps_set", new HashSet<>());
            if (legacyCustomBlocked != null && !legacyCustomBlocked.isEmpty()) {
                PackageManager pm = context.getPackageManager();
                for (String pkg : legacyCustomBlocked) {
                    if (pkg == null || pkg.trim().isEmpty()) continue;
                    
                    BlockedAppEntity existing = dao.getByPackageName(pkg);
                    if (existing == null) {
                        String appLabel;
                        try {
                            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                            appLabel = pm.getApplicationLabel(ai).toString();
                        } catch (Exception e) {
                            appLabel = pkg;
                        }

                        BlockedAppEntity app = new BlockedAppEntity();
                        app.packageName = pkg;
                        app.appName = appLabel;
                        app.isRestricted = true;
                        app.scope = "full";
                        app.useMod = false;
                        app.sectionViewId = "";
                        dao.insert(app);
                    } else {
                        existing.isRestricted = true;
                        existing.scope = "full";
                        dao.update(existing);
                    }
                }
            }

            // Mark migration complete
            sharedPreferences.edit()
                    .putBoolean("pref_blocker_db_migrated", true)
                    // Optionally clear the old values or keep them for safety?
                    // It is safer to clear them to prevent any future clashing.
                    .remove("ytSwitchState")
                    .remove("instaSwitchState")
                    .remove("snapSwitchState")
                    .apply();

            appDataPrefs.edit()
                    .remove("YtMod")
                    .remove("InstaMod")
                    .remove("SnapMod")
                    .apply();

            Log.d(TAG, "Legacy blocker config migration complete.");
        }
    }
}
