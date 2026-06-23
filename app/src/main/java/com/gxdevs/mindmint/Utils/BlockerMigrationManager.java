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
                    "com.revance.android.youtube",
                    "app.morphe.android.youtube"
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

            // Instagram packages (includes Lite as a mod of the main app)
            String[] instaPkgs = {
                    "com.instagram.android",
                    "com.instagram.lite",          // Instagram Lite (treated as mod of main)
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

            // Facebook — primary app + FB Lite (mod/lite variant)
            BlockedAppEntity facebook = new BlockedAppEntity();
            facebook.packageName = "com.facebook.katana";
            facebook.appName = "Facebook";
            facebook.isRestricted = false;
            facebook.scope = "section";
            facebook.useMod = false;
            facebook.sectionViewId = Utils.facebookViewId;
            defaultApps.add(facebook);

            BlockedAppEntity fbLite = new BlockedAppEntity();
            fbLite.packageName = "com.facebook.lite";
            fbLite.appName = "Facebook Lite";
            fbLite.isRestricted = false;
            fbLite.scope = "full";
            fbLite.useMod = false;
            fbLite.sectionViewId = "";
            defaultApps.add(fbLite);

            // LinkedIn
            BlockedAppEntity linkedin = new BlockedAppEntity();
            linkedin.packageName = "com.linkedin.android";
            linkedin.appName = "LinkedIn";
            linkedin.isRestricted = false;
            linkedin.scope = "section";
            linkedin.useMod = false;
            linkedin.sectionViewId = Utils.linkedinViewId;
            defaultApps.add(linkedin);

            // Reddit — official + popular third-party clients
            String[] redditPkgs = {
                    "com.reddit.frontpage",
                    "com.andrewshu.android.reddit",        // Reddit is Fun (RiF)
                    "ml.docilealligator.infinityforreddit", // Infinity for Reddit
                    "free.reddit.news",                     // Relay for Reddit
                    "com.laurencedawson.reddit_sync",       // Reddit Sync
                    "com.reddit.frontpage.lite"             // Reddit Lite
            };
            for (String pkg : redditPkgs) {
                BlockedAppEntity app = new BlockedAppEntity();
                app.packageName = pkg;
                app.appName = "Reddit";
                app.isRestricted = false;
                app.scope = "section";
                app.useMod = false;
                app.sectionViewId = Utils.redditViewId;
                defaultApps.add(app);
            }

            // TikTok — official + legacy/regional packages
            String[] tiktokPkgs = {
                    "com.ss.android.ugc.trill",      // TikTok (global)
                    "com.zhiliaoapp.musically",      // Legacy/global TikTok package
                    "com.ss.android.ugc.aweme",      // Douyin (Chinese TikTok)
                    "com.ss.android.ugc.aweme.lite"  // Douyin Lite
            };
            for (String pkg : tiktokPkgs) {
                BlockedAppEntity app = new BlockedAppEntity();
                app.packageName = pkg;
                app.appName = "TikTok";
                app.isRestricted = false;
                app.scope = "section";
                app.useMod = false;
                app.sectionViewId = Utils.tiktokViewId;
                defaultApps.add(app);
            }

            // Twitter / X — official + Lite
            String[] twitterPkgs = {
                    "com.twitter.android",
                    "com.twitter.android.lite"  // Twitter Lite (official)
            };
            for (String pkg : twitterPkgs) {
                BlockedAppEntity app = new BlockedAppEntity();
                app.packageName = pkg;
                app.appName = "Twitter";
                app.isRestricted = false;
                app.scope = "section";
                app.useMod = false;
                app.sectionViewId = Utils.twitterViewId;
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

        // 3. Patch blocks — ensure new packages are present for users upgrading from older versions
        patchInsertIfMissing(dao, "app.morphe.android.youtube", "YouTube Shorts", "section", Utils.YtViewId);
        patchInsertIfMissing(dao, "com.facebook.katana",        "Facebook",       "section", Utils.facebookViewId);
        patchInsertIfMissing(dao, "com.facebook.lite",          "Facebook Lite",  "full",    "");
        patchInsertIfMissing(dao, "com.instagram.lite",         "Instagram Reels", "section", Utils.instaViewId);
        patchInsertIfMissing(dao, "com.linkedin.android",       "LinkedIn",       "section", Utils.linkedinViewId);
        patchInsertIfMissing(dao, "com.reddit.frontpage",       "Reddit",         "section", Utils.redditViewId);
        patchInsertIfMissing(dao, "com.andrewshu.android.reddit",        "Reddit",         "section", Utils.redditViewId);
        patchInsertIfMissing(dao, "ml.docilealligator.infinityforreddit", "Reddit",        "section", Utils.redditViewId);
        patchInsertIfMissing(dao, "free.reddit.news",           "Reddit",         "section", Utils.redditViewId);
        patchInsertIfMissing(dao, "com.laurencedawson.reddit_sync", "Reddit",     "section", Utils.redditViewId);
        patchInsertIfMissing(dao, "com.reddit.frontpage.lite",  "Reddit",         "section", Utils.redditViewId);
        patchInsertIfMissing(dao, "com.ss.android.ugc.trill",  "TikTok",         "section", Utils.tiktokViewId);
        patchInsertIfMissing(dao, "com.zhiliaoapp.musically",  "TikTok",         "section", Utils.tiktokViewId);
        patchInsertIfMissing(dao, "com.ss.android.ugc.aweme",  "TikTok",         "section", Utils.tiktokViewId);
        patchInsertIfMissing(dao, "com.ss.android.ugc.aweme.lite", "TikTok",     "section", Utils.tiktokViewId);
        patchInsertIfMissing(dao, "com.twitter.android",       "Twitter",        "section", Utils.twitterViewId);
        patchInsertIfMissing(dao, "com.twitter.android.lite",  "Twitter",        "section", Utils.twitterViewId);
    }

    /** Inserts a BlockedAppEntity only if the package is not already in the DB. */
    private static void patchInsertIfMissing(@NonNull BlockedAppDao dao, String pkg,
                                              String name, String scope, String viewId) {
        if (dao.getByPackageName(pkg) == null) {
            BlockedAppEntity app = new BlockedAppEntity();
            app.packageName = pkg;
            app.appName = name;
            app.isRestricted = false;
            app.scope = scope;
            app.useMod = false;
            app.sectionViewId = viewId;
            dao.insert(app);
            Log.d(TAG, "Patched missing package into DB: " + pkg);
        }
    }
}
