package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class BlockedSitesManager {

    public static final String PREF_BLOCKED_DOMAINS_SET = "pref_blocked_domains_set"; // e.g. instagram.com, youtube
    public static final String PREF_BLOCKED_EXACT_URLS_SET = "pref_blocked_exact_urls_set"; // e.g. https://m.youtube.com/shorts
    public static final String PREF_DEFAULT_SITES_SEEDED = "pref_default_sites_seeded"; // one-time seeding flag

    public static void ensureSetsExist(@NonNull Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> domains = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_DOMAINS_SET, new HashSet<>()));
        Set<String> exacts = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_EXACT_URLS_SET, new HashSet<>()));
        SharedPreferences.Editor editor = sp.edit();
        editor.putStringSet(PREF_BLOCKED_DOMAINS_SET, domains);
        editor.putStringSet(PREF_BLOCKED_EXACT_URLS_SET, exacts);
        editor.apply();
    }

    public static void seedDefaultsIfMissing(@NonNull Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> domains = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_DOMAINS_SET, new HashSet<>()));
        Set<String> exacts = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_EXACT_URLS_SET, new HashSet<>()));
        boolean changed = false;
        // Hardcoded defaults (previously in Utils)
        String[] defaultExactUrls = new String[]{"youtube.com/shorts", "instagram.com/reel", "snapchat.com/spotlight"};

        for (String e : defaultExactUrls) {
            if (!exacts.contains(e)) {
                exacts.add(e);
                changed = true;
            }
        }
        if (changed) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putStringSet(PREF_BLOCKED_DOMAINS_SET, domains);
            editor.putStringSet(PREF_BLOCKED_EXACT_URLS_SET, exacts);
            editor.apply();
        }
    }

    public static boolean hasSeededDefaults(@NonNull Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(PREF_DEFAULT_SITES_SEEDED, false);
    }

    private static void markDefaultsSeeded(@NonNull Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(PREF_DEFAULT_SITES_SEEDED, true).apply();
    }

    /**
     * Seed default items only if it's the first time (flag not set) AND current lists are empty.
     * After adding, marks the one-time flag so we never seed again from any entry point.
     */
    public static void seedDefaultsIfFirstTimeAndEmpty(@NonNull Context context) {
        if (hasSeededDefaults(context)) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> domains = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_DOMAINS_SET, new HashSet<>()));
        Set<String> exacts = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_EXACT_URLS_SET, new HashSet<>()));
        if (!domains.isEmpty() || !exacts.isEmpty())
            return; // user already has entries; don't seed and don't flip flag

        String[] defaultExactUrls = new String[]{"https://youtube.com/shorts", "https://instagram.com/reel", "https://snapchat.com/spotlight"};
        exacts.addAll(Arrays.asList(defaultExactUrls));
        SharedPreferences.Editor editor = sp.edit();
        editor.putStringSet(PREF_BLOCKED_EXACT_URLS_SET, exacts);
        editor.apply();
        markDefaultsSeeded(context);
    }

    @NonNull
    public static Set<String> getBlockedDomains(@NonNull Context context) {
        ensureSetsExist(context);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_DOMAINS_SET, new HashSet<>()));
    }

    @NonNull
    public static Set<String> getBlockedExactUrls(@NonNull Context context) {
        ensureSetsExist(context);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_EXACT_URLS_SET, new HashSet<>()));
    }

    public static void addDomain(@NonNull Context context, @NonNull String domainOrSubstring) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> domains = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_DOMAINS_SET, new HashSet<>()));
        domains.add(domainOrSubstring);
        sp.edit().putStringSet(PREF_BLOCKED_DOMAINS_SET, domains).apply();
    }

    public static void addExactUrl(@NonNull Context context, @NonNull String urlOrPattern) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> exacts = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_EXACT_URLS_SET, new HashSet<>()));
        exacts.add(urlOrPattern);
        sp.edit().putStringSet(PREF_BLOCKED_EXACT_URLS_SET, exacts).apply();
    }

    public static void remove(@NonNull Context context, @NonNull String value) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> domains = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_DOMAINS_SET, new HashSet<>()));
        Set<String> exacts = new LinkedHashSet<>(sp.getStringSet(PREF_BLOCKED_EXACT_URLS_SET, new HashSet<>()));
        boolean changed = domains.remove(value);
        if (exacts.remove(value)) changed = true;
        if (changed) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putStringSet(PREF_BLOCKED_DOMAINS_SET, domains);
            editor.putStringSet(PREF_BLOCKED_EXACT_URLS_SET, exacts);
            editor.apply();
        }
    }

}


