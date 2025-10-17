package com.gxdevs.mindmint.Models;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public String appName;
    public String packageName;
    public Drawable icon;
    public boolean isBlocked;

    public AppInfo(String appName, String packageName, Drawable icon, boolean isBlocked) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isBlocked = isBlocked;
    }
} 