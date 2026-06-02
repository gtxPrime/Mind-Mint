package com.gxdevs.mindmint.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.gxdevs.mindmint.db.dao.DailyStatsDao;
import com.gxdevs.mindmint.db.dao.FocusDao;
import com.gxdevs.mindmint.db.dao.FocusScheduleDao;
import com.gxdevs.mindmint.db.dao.HabitCachedStatsDao;
import com.gxdevs.mindmint.db.dao.HabitCompletionDao;
import com.gxdevs.mindmint.db.dao.HabitDao;
import com.gxdevs.mindmint.db.dao.TaskDao;
import com.gxdevs.mindmint.db.dao.FocusTopicDao;
import com.gxdevs.mindmint.db.dao.BlockedAppDao;
import com.gxdevs.mindmint.db.entities.DailyStatsEntity;
import com.gxdevs.mindmint.db.entities.FocusDailyStatEntity;
import com.gxdevs.mindmint.db.entities.FocusScheduleEntity;
import com.gxdevs.mindmint.db.entities.FocusSessionEntity;
import com.gxdevs.mindmint.db.entities.FocusStateEntity;
import com.gxdevs.mindmint.db.entities.FocusTopicEntity;
import com.gxdevs.mindmint.db.entities.HabitCachedStatsEntity;
import com.gxdevs.mindmint.db.entities.HabitCompletionEntity;
import com.gxdevs.mindmint.db.entities.HabitEntity;
import com.gxdevs.mindmint.db.entities.TaskEntity;
import com.gxdevs.mindmint.db.entities.BlockedAppEntity;
import java.util.concurrent.Executors;

@Database(entities = {
        TaskEntity.class,
        HabitEntity.class,
        HabitCompletionEntity.class,
        HabitCachedStatsEntity.class,
        FocusStateEntity.class,
        FocusDailyStatEntity.class,
        FocusSessionEntity.class,
        DailyStatsEntity.class,
        FocusTopicEntity.class,
        FocusScheduleEntity.class,
        BlockedAppEntity.class
}, version = 17, exportSchema = false)
public abstract class MindMintRoomDatabase extends RoomDatabase {
    private static volatile MindMintRoomDatabase INSTANCE;

    public abstract TaskDao taskDao();

    public abstract HabitDao habitDao();

    public abstract HabitCompletionDao habitCompletionDao();

    public abstract HabitCachedStatsDao habitCachedStatsDao();

    public abstract FocusDao focusDao();

    public abstract FocusTopicDao focusTopicDao();

    public abstract FocusScheduleDao focusScheduleDao();

    public abstract DailyStatsDao dailyStatsDao();

    public abstract BlockedAppDao blockedAppDao();

    /**
     * Migration from version 7 to 8
     */
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add new columns to habits table with defaults
            database.execSQL("ALTER TABLE habits ADD COLUMN frequency_goal TEXT DEFAULT 'DAILY'");
            database.execSQL("ALTER TABLE habits ADD COLUMN frequency_times_per_week INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE habits ADD COLUMN reset_time_hour INTEGER NOT NULL DEFAULT 4");
            database.execSQL("ALTER TABLE habits ADD COLUMN target_count INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE habits ADD COLUMN target_unit TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE habits ADD COLUMN priority TEXT DEFAULT 'MEDIUM'");
            database.execSQL("ALTER TABLE habits ADD COLUMN allowed_skips_per_week INTEGER NOT NULL DEFAULT 0");

            // Add new columns to habit_completions table
            database.execSQL("ALTER TABLE habit_completions ADD COLUMN day_part TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE habit_completions ADD COLUMN adjusted_date_ms INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE habit_completions ADD COLUMN count INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE habit_completions ADD COLUMN note TEXT DEFAULT ''");

            // Create index on adjusted_date_ms
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_habit_completions_adjusted_date_ms ON habit_completions(adjusted_date_ms)");

            // Create new habit_cached_stats table
            database.execSQL("CREATE TABLE IF NOT EXISTS habit_cached_stats (" + "habit_id TEXT NOT NULL PRIMARY KEY, "
                    + "total_completions INTEGER NOT NULL DEFAULT 0, "
                    + "average_interval_ms INTEGER NOT NULL DEFAULT 0, "
                    + "longest_break_ms INTEGER NOT NULL DEFAULT 0, "
                    + "current_week_count INTEGER NOT NULL DEFAULT 0, "
                    + "skips_used_this_week INTEGER NOT NULL DEFAULT 0, " + "day_of_week_heatmap TEXT, "
                    + "day_part_distribution TEXT, " + "this_week_volume INTEGER NOT NULL DEFAULT 0, "
                    + "last_week_volume INTEGER NOT NULL DEFAULT 0, "
                    + "volume_change_percent REAL NOT NULL DEFAULT 0, " + "dominant_day_part TEXT, "
                    + "dominant_day_part_percent REAL NOT NULL DEFAULT 0, "
                    + "last_updated_ms INTEGER NOT NULL DEFAULT 0" + ")");
        }
    };

    /**
     * Migration from version 8 to 9
     */
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE habits ADD COLUMN is_ask_emotion INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE habit_completions ADD COLUMN emotion TEXT DEFAULT NULL");
        }
    };

    /**
     * Migration from 9 to 10
     */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // New fields for HabitEntity
            database.execSQL("ALTER TABLE habits ADD COLUMN is_goal_tracking INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE habits ADD COLUMN one_tap_value INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE habits ADD COLUMN current_progress INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE habits ADD COLUMN last_progress_date_ms INTEGER NOT NULL DEFAULT 0");

            // New field for HabitCompletionEntity
            database.execSQL("ALTER TABLE habit_completions ADD COLUMN is_completed INTEGER NOT NULL DEFAULT 1");
        }
    };

    /**
     * Migration from 10 to 11
     * - Create focus_sessions table
     * - Add completed_date_str and completed_time_str to tasks table (nullable)
     */
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Create focus_sessions table
            database.execSQL("CREATE TABLE IF NOT EXISTS focus_sessions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " + "start_time_ms INTEGER NOT NULL, "
                    + "end_time_ms INTEGER NOT NULL, " + "duration_ms INTEGER NOT NULL, " + "date_str TEXT)");

            // Add new columns to tasks table
            database.execSQL("ALTER TABLE tasks ADD COLUMN completed_date_str TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE tasks ADD COLUMN completed_time_str TEXT DEFAULT NULL");
        }
    };

    /**
     * Migration from 11 to 12
     * - Add total_screen_time_seconds and total_scrolls to daily_stats
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE daily_stats ADD COLUMN total_screen_time_seconds INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE daily_stats ADD COLUMN total_scrolls INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Migration from 12 to 13
     * - Create focus_topics table
     */
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS focus_topics ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " + "name TEXT)");

            // Pre-populate defaults
            database.execSQL("INSERT INTO focus_topics (name) VALUES ('Work')");
            database.execSQL("INSERT INTO focus_topics (name) VALUES ('Coding')");
            database.execSQL("INSERT INTO focus_topics (name) VALUES ('Studying')");
        }
    };

    /**
     * Migration from 13 to 14
     * - Add topic_name to focus_sessions
     * - Recreate focus_state with new fields
     */
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add topic_name to focus_sessions
            database.execSQL("ALTER TABLE focus_sessions ADD COLUMN topic_name TEXT");

            // Recreate focus_state table since we added many fields and SQLite ALERT TABLE
            // is limited
            // However, we just need to add columns, so ALTER works for simple additions.
            database.execSQL("ALTER TABLE focus_state ADD COLUMN start_time INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN current_segment_start INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN accumulated_focus INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN is_break INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN is_pomodoro INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN pomodoro_focus_interval INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN pomodoro_break_interval INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN topic_name TEXT");
        }
    };

    /**
     * Migration from 14 to 15
     * - Add pause state fields to focus_state for Pomodoro pause functionality
     */
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE focus_state ADD COLUMN is_paused INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN crystal_reveal_fraction REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN break_start_time INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE focus_state ADD COLUMN idle_timeout_start INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Migration from 15 to 16
     * - Add focus mode fields to tasks table
     * - Create focus_schedules table
     */
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Focus mode columns for tasks
            database.execSQL("ALTER TABLE tasks ADD COLUMN focus_mode_enabled INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN focus_duration_minutes INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN focus_time_spent_ms INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN focus_status TEXT DEFAULT 'IDLE'");

            // Recurring focus schedule table
            database.execSQL("CREATE TABLE IF NOT EXISTS focus_schedules ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "label TEXT, "
                    + "startHour INTEGER NOT NULL DEFAULT 0, "
                    + "startMinute INTEGER NOT NULL DEFAULT 0, "
                    + "durationMinutes INTEGER NOT NULL DEFAULT 25, "
                    + "daysOfWeek TEXT, "
                    + "isLockedIn INTEGER NOT NULL DEFAULT 0, "
                    + "isEnabled INTEGER NOT NULL DEFAULT 1)");
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS blocked_apps ("
                    + "packageName TEXT NOT NULL PRIMARY KEY, "
                    + "appName TEXT, "
                    + "isRestricted INTEGER NOT NULL DEFAULT 0, "
                    + "scope TEXT, "
                    + "useMod INTEGER NOT NULL DEFAULT 0, "
                    + "sectionViewId TEXT)");
        }
    };


    public static MindMintRoomDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (MindMintRoomDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MindMintRoomDatabase.class,
                            "mindmint.db")
                            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                                    MIGRATION_15_16, MIGRATION_16_17)
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    // Pre-populate for clean installs
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        if (INSTANCE != null) {
                                            INSTANCE.focusTopicDao().insert(new FocusTopicEntity("Work"));
                                            INSTANCE.focusTopicDao().insert(new FocusTopicEntity("Coding"));
                                            INSTANCE.focusTopicDao().insert(new FocusTopicEntity("Studying"));
                                        }
                                    });
                                }
                            })
                            .fallbackToDestructiveMigration() // Only if migration fails
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
