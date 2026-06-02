package com.gxdevs.mindmint.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "blocked_apps")
public class BlockedAppEntity {
    @PrimaryKey
    @NonNull
    public String packageName;
    
    public String appName;
    public boolean isRestricted;
    public String scope; // "section" or "full"
    public boolean useMod;
    public String sectionViewId; // view ID (e.g. "reel_watch_player")
}
