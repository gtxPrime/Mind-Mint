package com.gxdevs.mindmint.Models;

import android.view.View;
import android.widget.CompoundButton;

public class SettingsItem {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_SWITCH = 1;
    public static final int TYPE_SEEKBAR = 2;
    public static final int TYPE_THEME = 3;
    public static final int TYPE_PERMISSION = 4;
    public static final int TYPE_BACKUP = 5;
    public static final int TYPE_SCROLL_TAB = 6;
    public static final int TYPE_LOCK_TAB = 7;
    public static final int TYPE_BLOCKER_SLIDER = 8;
    public static final int TYPE_BLOCK_TRIGGER_TAB = 9;

    private int id;
    private final int type;
    private final String title;
    private String subtitle;
    private CharSequence formattedSubtitle;
    private int iconRes;
    private int iconTint;
    private boolean isSwitchVisible;
    private boolean isExpanded; // Added for Backup card
    private boolean isSwitchChecked;
    private boolean isArrowVisible;
    private String actionText;
    private int seekbarMax;
    private int seekbarProgress;
    private String seekbarValueText;

    // For TYPE_SCROLL_TAB: true = per-app view, false = combined
    private boolean scrollTabPerApp;

    // Listeners
    private View.OnClickListener onClickListener;
    private View.OnLongClickListener onLongClickListener;
    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener;
    // For seekbar, we might need a custom listener or handle it in adapter

    public SettingsItem(int type, String title) { // For Header
        this.type = type;
        this.title = title;
    }

    public SettingsItem(int id, int type, String title, String subtitle, int iconRes, int iconTint) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.iconRes = iconRes;
        this.iconTint = iconTint;
    }

    // Icon Background customization
    private int iconBgRes;
    private int iconBgTint;

    public SettingsItem setIconValues(int iconBgRes, int iconBgTint) {
        this.iconBgRes = iconBgRes;
        this.iconBgTint = iconBgTint;
        return this;
    }

    public int getIconBgRes() {
        return iconBgRes;
    }

    public int getIconBgTint() {
        return iconBgTint;
    }

    // Builder-like setters for optional fields
    public SettingsItem setSwitch(boolean visible, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        this.isSwitchVisible = visible;
        this.isSwitchChecked = checked;
        this.onCheckedChangeListener = listener;
        return this;
    }

    public SettingsItem setArrow(boolean visible) {
        this.isArrowVisible = visible;
        return this;
    }

    public SettingsItem setActionText(String text) {
        this.actionText = text;
        return this;
    }

    public SettingsItem setOnClickListener(View.OnClickListener listener) {
        this.onClickListener = listener;
        return this;
    }

    public SettingsItem setOnLongClickListener(View.OnLongClickListener listener) {
        this.onLongClickListener = listener;
        return this;
    }

    public void setFormattedSubtitle(CharSequence formattedSubtitle) {
        this.formattedSubtitle = formattedSubtitle;
    }

    public SettingsItem setSeekbar(int max, int progress, String valueText) {
        this.seekbarMax = max;
        this.seekbarProgress = progress;
        this.seekbarValueText = valueText;
        return this;
    }

    public SettingsItem setScrollTabPerApp(boolean perApp) {
        this.scrollTabPerApp = perApp;
        return this;
    }

    public boolean isScrollTabPerApp() {
        return scrollTabPerApp;
    }

    // Getters
    public int getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public CharSequence getFormattedSubtitle() {
        return formattedSubtitle;
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getIconTint() {
        return iconTint;
    }

    public boolean isSwitchVisible() {
        return isSwitchVisible;
    }

    public boolean isSwitchChecked() {
        return isSwitchChecked;
    }

    public boolean isArrowVisible() {
        return isArrowVisible;
    }

    public String getActionText() {
        return actionText;
    }

    public int getSeekbarMax() {
        return seekbarMax;
    }

    public int getSeekbarProgress() {
        return seekbarProgress;
    }

    public String getSeekbarValueText() {
        return seekbarValueText;
    }

    // Permission Card specific fields
    private int cardBgColor;
    private int cardIconColor;
    private int cardTextColor;
    private int cardArrowColor;
    private int cardTitleColor;

    public SettingsItem setPermissionColors(int bgColor, int iconColor, int textColor, int arrowColor, int titleColor) {
        this.cardBgColor = bgColor;
        this.cardIconColor = iconColor;
        this.cardTextColor = textColor;
        this.cardArrowColor = arrowColor;
        this.cardTitleColor = titleColor;
        return this;
    }

    public int getCardBgColor() {
        return cardBgColor;
    }

    public int getCardIconColor() {
        return cardIconColor;
    }

    public int getCardTextColor() {
        return cardTextColor;
    }

    public int getCardArrowColor() {
        return cardArrowColor;
    }

    public int getCardTitleColor() {
        return cardTitleColor;
    }

    public View.OnClickListener getOnClickListener() {
        return onClickListener;
    }

    public View.OnLongClickListener getOnLongClickListener() {
        return onLongClickListener;
    }

    public CompoundButton.OnCheckedChangeListener getOnCheckedChangeListener() {
        return onCheckedChangeListener;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    private int sliderProgress;
    private String triggerTab; // "scroll" or "time"

    public SettingsItem setSliderProgress(int progress) {
        this.sliderProgress = progress;
        return this;
    }

    public int getSliderProgress() {
        return sliderProgress;
    }

    public SettingsItem setTriggerTab(String triggerTab) {
        this.triggerTab = triggerTab;
        return this;
    }

    public String getTriggerTab() {
        return triggerTab;
    }
}
