package com.gxdevs.mindmint.Adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.gxdevs.mindmint.Models.SettingsItem;
import com.gxdevs.mindmint.R;

import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private final List<SettingsItem> items;
    private OnSeekbarChangeListener onSeekbarChangeListener;
    private OnThemeChangeListener onThemeChangeListener;
    private OnLockTabActionListener onLockTabActionListener;
    private String currentTheme;

    public interface OnSeekbarChangeListener {
        void onProgressChanged(int itemId, int progress);
    }

    public interface OnThemeChangeListener {
        void onThemeChanged(String newTheme);
    }

    public interface OnLockTabActionListener {
        /** Called when user REQUESTS to change the lock type */
        void onRequestLockTypeChange(String newLockType, Runnable onSuccess);
        /** Called when user long-presses on the Custom PIN row to edit the PIN. */
        void onEditCustomPin();
    }

    public SettingsAdapter(Context context, List<SettingsItem> items) {
        this.context = context;
        this.items = items;
    }

    public void setOnSeekbarChangeListener(OnSeekbarChangeListener listener) {
        this.onSeekbarChangeListener = listener;
    }

    public void setOnThemeChangeListener(OnThemeChangeListener listener) {
        this.onThemeChangeListener = listener;
    }

    public void setOnLockTabActionListener(OnLockTabActionListener listener) {
        this.onLockTabActionListener = listener;
    }

    public void setCurrentTheme(String currentTheme) {
        this.currentTheme = currentTheme;
    }

    private OnBlockerIntensityChangeListener onBlockerIntensityChangeListener;
    private OnBlockTriggerChangeListener onBlockTriggerChangeListener;

    public interface OnBlockerIntensityChangeListener {
        void onIntensityChanged(int itemId, int intensity);
    }

    public interface OnBlockTriggerChangeListener {
        void onTriggerChanged(int itemId, String newTrigger);
    }

    public void setOnBlockerIntensityChangeListener(OnBlockerIntensityChangeListener listener) {
        this.onBlockerIntensityChangeListener = listener;
    }

    public void setOnBlockTriggerChangeListener(OnBlockTriggerChangeListener listener) {
        this.onBlockTriggerChangeListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return switch (viewType) {
            case SettingsItem.TYPE_HEADER ->
                    new HeaderViewHolder(inflater.inflate(R.layout.item_settings_header, parent, false));
            case SettingsItem.TYPE_SWITCH ->
                    new CommonViewHolder(inflater.inflate(R.layout.item_settings_common, parent, false));
            case SettingsItem.TYPE_SEEKBAR ->
                    new SeekbarViewHolder(inflater.inflate(R.layout.item_settings_seekbar, parent, false));
            case SettingsItem.TYPE_THEME ->
                    new ThemeViewHolder(inflater.inflate(R.layout.item_settings_theme, parent, false));
            case SettingsItem.TYPE_BACKUP ->
                    new BackupViewHolder(inflater.inflate(R.layout.item_settings_backup, parent, false));
            case SettingsItem.TYPE_SCROLL_TAB ->
                    new ScrollTabViewHolder(inflater.inflate(R.layout.item_settings_scroll_tab, parent, false));
            case SettingsItem.TYPE_LOCK_TAB ->
                    new LockTabViewHolder(inflater.inflate(R.layout.item_settings_lock_type_tab, parent, false));
            case SettingsItem.TYPE_BLOCKER_SLIDER ->
                    new BlockerSliderViewHolder(inflater.inflate(R.layout.item_settings_blocker_slider, parent, false));
            case SettingsItem.TYPE_BLOCK_TRIGGER_TAB ->
                    new BlockTriggerTabViewHolder(inflater.inflate(R.layout.item_settings_block_trigger_tab, parent, false));
            default ->
                    new PermissionViewHolder(inflater.inflate(R.layout.item_settings_permission, parent, false));
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SettingsItem item = items.get(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (holder instanceof ScrollTabViewHolder) {
            ((ScrollTabViewHolder) holder).bind(item);
            updateBackground(holder.itemView, position);
        } else if (holder instanceof LockTabViewHolder) {
            ((LockTabViewHolder) holder).bind(item);
            updateBackground(holder.itemView, position);
        } else if (holder instanceof CommonViewHolder) {
            ((CommonViewHolder) holder).bind(item);
            updateBackground(holder.itemView, position);
        } else if (holder instanceof SeekbarViewHolder) {
            ((SeekbarViewHolder) holder).bind(item);
            updateBackground(holder.itemView, position);
        } else if (holder instanceof ThemeViewHolder) {
            ((ThemeViewHolder) holder).bind();
            updateBackground(holder.itemView, position);
        } else if (holder instanceof BackupViewHolder) {
            ((BackupViewHolder) holder).bind(item);
            updateBackground(holder.itemView, position);
        } else if (holder instanceof BlockerSliderViewHolder) {
            ((BlockerSliderViewHolder) holder).bind(item);
            updateBackground(holder.itemView, position);
        } else if (holder instanceof BlockTriggerTabViewHolder) {
            ((BlockTriggerTabViewHolder) holder).bind(item);
            updateBackground(holder.itemView, position);
        } else if (holder instanceof PermissionViewHolder) {
            ((PermissionViewHolder) holder).bind(item);
            // Adjust margin: first permission card gets 24dp top margin, others get 12dp
            boolean isFirstPermission = true;
            for (int i = 0; i < position; i++) {
                if (items.get(i).getType() == SettingsItem.TYPE_PERMISSION) {
                    isFirstPermission = false;
                    break;
                }
            }
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView
                    .getLayoutParams();
            if (params != null) {
                params.topMargin = isFirstPermission ? dpToPx(24) : dpToPx(12);
                holder.itemView.setLayoutParams(params);
            }
        }
    }

    private void updateBackground(View view, int position) {
        boolean isPrevGroupable = false;
        if (position > 0) {
            int prevType = items.get(position - 1).getType();
            isPrevGroupable = isGroupable(prevType);
        }

        boolean isNextGroupable = false;
        if (position < items.size() - 1) {
            int nextType = items.get(position + 1).getType();
            isNextGroupable = isGroupable(nextType);
        }

        int bgRes;
        if (!isPrevGroupable && !isNextGroupable) {
            bgRes = R.drawable.bg_settings_single;
        } else if (!isPrevGroupable) {
            bgRes = R.drawable.bg_settings_top;
        } else if (!isNextGroupable) {
            bgRes = R.drawable.bg_settings_bottom;
        } else {
            bgRes = R.drawable.bg_settings_middle;
        }

        view.setBackgroundResource(bgRes);

        View divider = view.findViewById(R.id.divider);
        if (divider != null) {
            divider.setVisibility(isPrevGroupable ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isGroupable(int type) {
        return type == SettingsItem.TYPE_SWITCH ||
                type == SettingsItem.TYPE_SEEKBAR ||
                type == SettingsItem.TYPE_THEME ||
                type == SettingsItem.TYPE_BACKUP ||
                type == SettingsItem.TYPE_SCROLL_TAB ||
                type == SettingsItem.TYPE_LOCK_TAB ||
                type == SettingsItem.TYPE_BLOCKER_SLIDER ||
                type == SettingsItem.TYPE_BLOCK_TRIGGER_TAB;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView title;

        HeaderViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.headerTitle);
        }

        void bind(SettingsItem item) {
            title.setText(item.getTitle());
        }
    }

    class CommonViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle, actionLink;
        ImageView icon, arrowIcon;
        MaterialSwitch switchView;
        View root, divider;

        CommonViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.itemRootLayout);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            icon = itemView.findViewById(R.id.icon);
            switchView = itemView.findViewById(R.id.settingSwitch);
            arrowIcon = itemView.findViewById(R.id.arrowIcon);
            actionLink = itemView.findViewById(R.id.actionLink);
            divider = itemView.findViewById(R.id.divider);
        }

        void bind(SettingsItem item) {
            title.setText(item.getTitle());

            if (item.getFormattedSubtitle() != null) {
                subtitle.setText(item.getFormattedSubtitle());
            } else {
                subtitle.setText(item.getSubtitle());
            }

            // Bind Icon & Background
            icon.setImageResource(item.getIconRes());

            // Icon Background — always reset first to avoid tint bleed on RecyclerView recycle
            if (item.getIconBgRes() != 0) {
                icon.setBackgroundResource(item.getIconBgRes());
            } else {
                icon.setBackgroundResource(0); // clear any recycled background
            }

            if (item.getIconBgTint() != 0) {
                Drawable bg = icon.getBackground();
                if (bg != null) {
                    bg = DrawableCompat.wrap(bg).mutate();
                    DrawableCompat.setTint(bg, item.getIconBgTint());
                    icon.setBackground(bg);
                }
            } else {
                // Clear any tint left over from a recycled cell
                Drawable bg = icon.getBackground();
                if (bg != null) {
                    DrawableCompat.setTintList(DrawableCompat.wrap(bg).mutate(), null);
                }
            }

            if (item.getIconTint() != 0) {
                Drawable d = ContextCompat.getDrawable(context, item.getIconRes());
                if (d != null) {
                    d = DrawableCompat.wrap(d).mutate();
                    DrawableCompat.setTint(d, item.getIconTint());
                    icon.setImageDrawable(d);
                    icon.setColorFilter(item.getIconTint());
                }
            } else {
                icon.clearColorFilter();
            }

            if (item.isSwitchVisible()) {
                switchView.setVisibility(View.VISIBLE);
                arrowIcon.setVisibility(View.GONE);

                switchView.setOnCheckedChangeListener(null);
                switchView.setChecked(item.isSwitchChecked());
                switchView.setOnCheckedChangeListener(item.getOnCheckedChangeListener());
            } else {
                switchView.setVisibility(View.GONE);
                if (item.isArrowVisible()) {
                    arrowIcon.setVisibility(View.VISIBLE);
                } else {
                    arrowIcon.setVisibility(View.GONE);
                }
            }

            if (item.getActionText() != null && item.isSwitchVisible()) {
                if (actionLink != null) {
                    actionLink.setVisibility(View.VISIBLE);
                    actionLink.setText(item.getActionText());
                }
            } else {
                if (actionLink != null) {
                    actionLink.setVisibility(View.GONE);
                }
            }

            if (item.getOnClickListener() != null) {
                root.setOnClickListener(item.getOnClickListener());
            } else {
                root.setOnClickListener(v -> {
                    if (item.isSwitchVisible()) {
                        switchView.toggle();
                    }
                });
            }

            if (item.getOnLongClickListener() != null) {
                root.setOnLongClickListener(item.getOnLongClickListener());
            } else {
                root.setOnLongClickListener(null);
            }

        }
    }

    class SeekbarViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle, valueText, labelFast, labelSlow;
        SeekBar seekBar;
        ImageView icon;
        View divider;

        SeekbarViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            icon = itemView.findViewById(R.id.icon);
            valueText = itemView.findViewById(R.id.valueText);
            seekBar = itemView.findViewById(R.id.seekBar);
            labelFast = itemView.findViewById(R.id.labelFast);
            labelSlow = itemView.findViewById(R.id.labelSlow);
            divider = itemView.findViewById(R.id.divider);
        }

        void bind(SettingsItem item) {

            title.setText(item.getTitle());
            subtitle.setText(item.getSubtitle());
            icon.setImageResource(item.getIconRes());

            // Icon Background — always reset first to avoid tint bleed on RecyclerView recycle
            if (item.getIconBgRes() != 0) {
                icon.setBackgroundResource(item.getIconBgRes());
            } else {
                icon.setBackgroundResource(0);
            }
            if (item.getIconBgTint() != 0) {
                Drawable bg = icon.getBackground();
                if (bg != null) {
                    bg = DrawableCompat.wrap(bg).mutate();
                    DrawableCompat.setTint(bg, item.getIconBgTint());
                    icon.setBackground(bg);
                }
            } else {
                Drawable bg = icon.getBackground();
                if (bg != null) {
                    DrawableCompat.setTintList(DrawableCompat.wrap(bg).mutate(), null);
                }
            }
            if (item.getIconTint() != 0) {
                icon.setColorFilter(item.getIconTint());
            } else {
                icon.clearColorFilter();
            }

            valueText.setText(item.getSeekbarValueText());
            seekBar.setMax(item.getSeekbarMax());
            seekBar.setProgress(item.getSeekbarProgress());

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (item.getId() == 8) { // ID_POPUP_DURATION
                        int seconds = progress + 3;
                        valueText.setText(seconds + "s");
                    } else { // Blocker Bypass Duration (ID_POPUP_DURATION + 10 = 18)
                        int minutes = progress + 5;
                        valueText.setText(minutes + "m");
                    }
                    if (fromUser && onSeekbarChangeListener != null) {
                        onSeekbarChangeListener.onProgressChanged(item.getId(), progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    class ThemeViewHolder extends RecyclerView.ViewHolder {
        Spinner themeSpinner;

        ThemeViewHolder(View itemView) {
            super(itemView);
            themeSpinner = itemView.findViewById(R.id.themeSpinner);
        }

        void bind() {
            String[] themeOptions = new String[] { "Dark Theme", "Light Theme", "System" };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.spinner_dropdown_item, themeOptions);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            themeSpinner.setAdapter(adapter);

            int selectedIndex = 0;
            if ("Light Theme".equalsIgnoreCase(currentTheme)) {
                selectedIndex = 1;
            } else if ("System".equalsIgnoreCase(currentTheme)) {
                selectedIndex = 2;
            }
            themeSpinner.setSelection(selectedIndex, false);

            themeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (onThemeChangeListener != null) {
                        onThemeChangeListener.onThemeChanged(themeOptions[position]);
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }
    }

    static class PermissionViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc;
        ImageView icon, arrow;
        View root;

        PermissionViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.cardRoot);
            title = itemView.findViewById(R.id.alertTitle);
            desc = itemView.findViewById(R.id.alertDesc);
            icon = itemView.findViewById(R.id.alertIcon);
            arrow = itemView.findViewById(R.id.ivArrow);
        }

        void bind(SettingsItem item) {
            title.setText(item.getTitle());
            desc.setText(item.getSubtitle());

            if (item.getCardBgColor() != 0) {
                root.setBackgroundTintList(android.content.res.ColorStateList.valueOf(item.getCardBgColor()));
            } else {
                root.setBackgroundTintList(null);
            }

            if (item.getCardIconColor() != 0) {
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(item.getCardIconColor()));
            }

            if (item.getCardTitleColor() != 0) {
                title.setTextColor(item.getCardTitleColor());
            }

            if (item.getCardTextColor() != 0) {
                desc.setTextColor(item.getCardTextColor());
            }

            if (item.getIconRes() != 0) {
                icon.setImageResource(item.getIconRes());
            }

            if (item.getIconBgTint() != 0) {
                icon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(item.getIconBgTint()));
            } else {
                icon.setBackgroundTintList(null);
            }

            if (item.getCardArrowColor() != 0 && arrow != null) {
                arrow.setImageTintList(android.content.res.ColorStateList.valueOf(item.getCardArrowColor()));
            }

            if (item.getOnClickListener() != null) {
                root.setOnClickListener(item.getOnClickListener());
            }
        }
    }

    class ScrollTabViewHolder extends RecyclerView.ViewHolder {
        TextView tabCombined, tabPerApp;
        View divider;

        ScrollTabViewHolder(View itemView) {
            super(itemView);
            tabCombined = itemView.findViewById(R.id.tabCombined);
            tabPerApp   = itemView.findViewById(R.id.tabPerApp);
            divider     = itemView.findViewById(R.id.divider);
        }

        void bind(SettingsItem item) {
            boolean perApp = item.isScrollTabPerApp();
            applyTabStyle(perApp);

            tabCombined.setOnClickListener(v -> {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                prefs.edit().putBoolean("pref_scroll_counter_per_app", false).apply();
                item.setScrollTabPerApp(false);
                applyTabStyle(false);
            });

            tabPerApp.setOnClickListener(v -> {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                prefs.edit().putBoolean("pref_scroll_counter_per_app", true).apply();
                item.setScrollTabPerApp(true);
                applyTabStyle(true);
            });
        }

        private void applyTabStyle(boolean perApp) {
            int selectedTxt = getAttrColor(context, R.attr.text_primary);
            int normalTxt = getAttrColor(context, R.attr.text_tertiary);

            // Combined tab
            if (!perApp) {
                tabCombined.setBackgroundResource(R.drawable.bg_segment_selected);
                tabCombined.setTextColor(selectedTxt);
            } else {
                tabCombined.setBackground(null);
                tabCombined.setTextColor(normalTxt);
            }

            // Per App tab
            if (perApp) {
                tabPerApp.setBackgroundResource(R.drawable.bg_segment_selected);
                tabPerApp.setTextColor(selectedTxt);
            } else {
                tabPerApp.setBackground(null);
                tabPerApp.setTextColor(normalTxt);
            }
        }
    }

    public interface OnBackupActionListener {
        void onExport();
        void onImport(boolean override);
    }

    private OnBackupActionListener onBackupActionListener;

    public void setOnBackupActionListener(OnBackupActionListener listener) {
        this.onBackupActionListener = listener;
    }

    class BackupViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle;
        ImageView icon, arrow;
        View root, expandedContent, importOptions;
        View btnExport, btnImport, btnSelectFile;
        android.widget.RadioGroup radioGroup;
        android.widget.RadioButton rbMerge, rbOverride;

        BackupViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.itemRootLayout);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            root = itemView.findViewById(R.id.itemRootLayout);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            icon = itemView.findViewById(R.id.icon);
            expandedContent = itemView.findViewById(R.id.expandedContent);
            importOptions = itemView.findViewById(R.id.importOptions);
            btnExport = itemView.findViewById(R.id.btnExport);
            btnImport = itemView.findViewById(R.id.btnImport);
            btnSelectFile = itemView.findViewById(R.id.btnSelectFile);
            radioGroup = itemView.findViewById(R.id.radioGroup);
            rbMerge = itemView.findViewById(R.id.rbMerge);
            rbOverride = itemView.findViewById(R.id.rbOverride);
        }

        void bind(SettingsItem item) {
            title.setText(item.getTitle());
            subtitle.setText(item.getSubtitle());
            icon.setImageResource(item.getIconRes());

            if (item.getIconBgTint() != 0) {
                Drawable bg = ContextCompat.getDrawable(context, R.drawable.shape_circle);
                if (bg != null) {
                    bg = DrawableCompat.wrap(bg).mutate();
                    DrawableCompat.setTint(bg, item.getIconBgTint());
                    icon.setBackground(bg);
                }
            }
            if (item.getIconTint() != 0) {
                icon.setColorFilter(item.getIconTint());
            } else {
                icon.clearColorFilter();
            }

            // Expansion
            boolean isExpanded = item.isExpanded();
            expandedContent.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            expandedContent.setVisibility(View.VISIBLE);

            root.setOnClickListener(null);
            root.setClickable(false);

            btnExport.setOnClickListener(v -> {
                if (onBackupActionListener != null)
                    onBackupActionListener.onExport();
            });

            btnImport.setOnClickListener(v -> {
                if (importOptions.getVisibility() == View.VISIBLE) {
                    importOptions.setVisibility(View.GONE);
                } else {
                    importOptions.setVisibility(View.VISIBLE);
                }
            });

            btnSelectFile.setOnClickListener(v -> {
                if (onBackupActionListener != null) {
                    boolean override = rbOverride.isChecked();
                    onBackupActionListener.onImport(override);
                }
            });
        }
    }

    // ─── Lock Type Tab ViewHolder ────────────────────────────────────────────

    class LockTabViewHolder extends RecyclerView.ViewHolder {
        TextView tabDevice, tabCustomPin;
        View divider;

        LockTabViewHolder(View itemView) {
            super(itemView);
            tabDevice     = itemView.findViewById(R.id.tabDevice);
            tabCustomPin  = itemView.findViewById(R.id.tabCustomPin);
            divider       = itemView.findViewById(R.id.divider);
        }

        void bind(SettingsItem item) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String lockType = prefs.getString(
                    com.gxdevs.mindmint.Utils.SettingsLockManager.PREF_LOCK_TYPE,
                    com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_DEVICE);
            boolean isCustom = com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_CUSTOM.equals(lockType);
            applyTabStyle(isCustom);

            tabDevice.setOnClickListener(v -> {
                if (!com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_CUSTOM.equals(lockType)) return;
                if (onLockTabActionListener != null) {
                    onLockTabActionListener.onRequestLockTypeChange(
                            com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_DEVICE,
                            () -> {
                                prefs.edit().putString(
                                        com.gxdevs.mindmint.Utils.SettingsLockManager.PREF_LOCK_TYPE,
                                        com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_DEVICE).apply();
                                applyTabStyle(false);
                            });
                }
            });

            tabCustomPin.setOnClickListener(v -> {
                // If it is already custom, don't change
                String current = prefs.getString(com.gxdevs.mindmint.Utils.SettingsLockManager.PREF_LOCK_TYPE, com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_DEVICE);
                if (com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_CUSTOM.equals(current)) return;

                if (onLockTabActionListener != null) {
                    onLockTabActionListener.onRequestLockTypeChange(
                            com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_CUSTOM,
                            () -> {
                                prefs.edit().putString(
                                        com.gxdevs.mindmint.Utils.SettingsLockManager.PREF_LOCK_TYPE,
                                        com.gxdevs.mindmint.Utils.SettingsLockManager.LOCK_TYPE_CUSTOM).apply();
                                applyTabStyle(true);
                            });
                }
            });
        }

        private void applyTabStyle(boolean isCustom) {
            int selectedTxt = getAttrColor(context, R.attr.text_primary);
            int normalTxt = getAttrColor(context, R.attr.text_tertiary);

            if (!isCustom) {
                tabDevice.setBackgroundResource(R.drawable.bg_segment_selected);
                tabDevice.setTextColor(selectedTxt);
                tabCustomPin.setBackground(null);
                tabCustomPin.setTextColor(normalTxt);
            } else {
                tabCustomPin.setBackgroundResource(R.drawable.bg_segment_selected);
                tabCustomPin.setTextColor(selectedTxt);
                tabDevice.setBackground(null);
                tabDevice.setTextColor(normalTxt);
            }
        }
    }

    private static int getAttrColor(Context context, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return ContextCompat.getColor(context, R.color.white);
    }

    class BlockerSliderViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle, labelNone, labelFriction, labelReminder, labelTempLock, labelPermanent;
        SeekBar seekBar;
        ImageView icon;

        BlockerSliderViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            icon = itemView.findViewById(R.id.icon);
            seekBar = itemView.findViewById(R.id.seekBar);
            labelNone = itemView.findViewById(R.id.labelNone);
            labelFriction = itemView.findViewById(R.id.labelFriction);
            labelReminder = itemView.findViewById(R.id.labelReminder);
            labelTempLock = itemView.findViewById(R.id.labelTempLock);
            labelPermanent = itemView.findViewById(R.id.labelPermanent);
        }

        void bind(SettingsItem item) {
            title.setText(item.getTitle());
            icon.setImageResource(item.getIconRes());
            
            if (labelNone != null) {
                labelNone.setText("Normal");
            }
            
            seekBar.setMax(4);
            seekBar.setOnSeekBarChangeListener(null);
            seekBar.setProgress(item.getSliderProgress());
            
            updateIntensityUI(item.getSliderProgress());

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateIntensityUI(progress);
                    if (fromUser && onBlockerIntensityChangeListener != null) {
                        onBlockerIntensityChangeListener.onIntensityChanged(item.getId(), progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            View.OnClickListener clickListener = v -> {
                int progress = 0;
                if (v == labelNone) progress = 0;
                else if (v == labelFriction) progress = 1;
                else if (v == labelReminder) progress = 2;
                else if (v == labelTempLock) progress = 3;
                else if (v == labelPermanent) progress = 4;
                
                seekBar.setProgress(progress);
                updateIntensityUI(progress);
                if (onBlockerIntensityChangeListener != null) {
                    onBlockerIntensityChangeListener.onIntensityChanged(item.getId(), progress);
                }
            };
            
            labelNone.setOnClickListener(clickListener);
            labelFriction.setOnClickListener(clickListener);
            labelReminder.setOnClickListener(clickListener);
            labelTempLock.setOnClickListener(clickListener);
            labelPermanent.setOnClickListener(clickListener);
        }

        private void updateIntensityUI(int progress) {
            String subtitleText;
            int tintColor;
            
            switch (progress) {
                case 1:
                    subtitleText = "Friction";
                    tintColor = 0xFF4A90E2; // Blue
                    break;
                case 2:
                    subtitleText = "Reminder";
                    tintColor = 0xFF2ECC71; // Green
                    break;
                case 3:
                    subtitleText = "Temp Lock";
                    tintColor = 0xFFE67E22; // Orange
                    break;
                case 4:
                    subtitleText = "Permanent";
                    tintColor = 0xFFE74C3C; // Red
                    break;
                default:
                    subtitleText = "Normal";
                    tintColor = 0xFF95A5A6; // Gray
                    break;
            }
            
            subtitle.setText(subtitleText);
            subtitle.setTextColor(tintColor);
            seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(tintColor));
            seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(tintColor));
            icon.setColorFilter(tintColor);
            
            labelNone.setAlpha(progress == 0 ? 1.0f : 0.4f);
            labelFriction.setAlpha(progress == 1 ? 1.0f : 0.4f);
            labelReminder.setAlpha(progress == 2 ? 1.0f : 0.4f);
            labelTempLock.setAlpha(progress == 3 ? 1.0f : 0.4f);
            labelPermanent.setAlpha(progress == 4 ? 1.0f : 0.4f);
        }
    }

    class BlockTriggerTabViewHolder extends RecyclerView.ViewHolder {
        TextView tabOnScroll, tabAfterTime;

        BlockTriggerTabViewHolder(View itemView) {
            super(itemView);
            tabOnScroll = itemView.findViewById(R.id.tabOnScroll);
            tabAfterTime = itemView.findViewById(R.id.tabAfterTime);
        }

        void bind(SettingsItem item) {
            String currentTrigger = item.getTriggerTab();
            boolean isTime = "time".equals(currentTrigger);
            applyTabStyle(isTime);

            tabOnScroll.setOnClickListener(v -> {
                if ("scroll".equals(item.getTriggerTab())) return;
                item.setTriggerTab("scroll");
                applyTabStyle(false);
                if (onBlockTriggerChangeListener != null) {
                    onBlockTriggerChangeListener.onTriggerChanged(item.getId(), "scroll");
                }
            });

            tabAfterTime.setOnClickListener(v -> {
                if ("time".equals(item.getTriggerTab())) return;
                item.setTriggerTab("time");
                applyTabStyle(true);
                if (onBlockTriggerChangeListener != null) {
                    onBlockTriggerChangeListener.onTriggerChanged(item.getId(), "time");
                }
            });
        }

        private void applyTabStyle(boolean isTime) {
            int selectedTxt = getAttrColor(context, R.attr.text_primary);
            int normalTxt = getAttrColor(context, R.attr.text_tertiary);

            if (!isTime) {
                tabOnScroll.setBackgroundResource(R.drawable.bg_segment_selected);
                tabOnScroll.setTextColor(selectedTxt);
                tabAfterTime.setBackground(null);
                tabAfterTime.setTextColor(normalTxt);
            } else {
                tabAfterTime.setBackgroundResource(R.drawable.bg_segment_selected);
                tabAfterTime.setTextColor(selectedTxt);
                tabOnScroll.setBackground(null);
                tabOnScroll.setTextColor(normalTxt);
            }
        }
    }
}
