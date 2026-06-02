package com.gxdevs.mindmint.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.gxdevs.mindmint.db.entities.BlockedAppEntity;

import java.util.List;

@Dao
public interface BlockedAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BlockedAppEntity app);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BlockedAppEntity> apps);

    @Update
    void update(BlockedAppEntity app);

    @Query("SELECT * FROM blocked_apps")
    List<BlockedAppEntity> getAllSync();

    @Query("SELECT * FROM blocked_apps WHERE isRestricted = 1")
    List<BlockedAppEntity> getRestrictedSync();

    @Query("SELECT * FROM blocked_apps WHERE packageName = :pkg LIMIT 1")
    BlockedAppEntity getByPackageName(String pkg);

    @Query("SELECT COUNT(*) FROM blocked_apps")
    int getCount();
}
