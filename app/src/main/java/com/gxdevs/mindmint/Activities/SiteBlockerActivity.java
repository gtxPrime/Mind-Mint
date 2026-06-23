package com.gxdevs.mindmint.Activities;

import android.animation.AnimatorListenerAdapter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.BlockedSitesManager;
import com.gxdevs.mindmint.Utils.Utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SiteBlockerActivity extends AppCompatActivity {

    private RecyclerView list;
    private EditText input;
    private TextView tabDomain;
    private TextView tabExactUrl;
    private boolean isDomainMode = true; // true = domain, false = exact URL
    private SitesAdapter adapter;
    private LinearLayout noteWarn;
    private Handler hideHandler;
    private static final String PREF_NOTE_WARN_SHOWN = "note_warn_shown";
    private static final int HIDE_DELAY_MS = 5000; // 5 seconds

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Utils.applyAppThemeFromPrefs(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_site_blocker);
        Utils.setPad(findViewById(R.id.main), "bottom", this);

        input = findViewById(R.id.etUrl);
        tabDomain = findViewById(R.id.tabDomain);
        tabExactUrl = findViewById(R.id.tabExactUrl);
        AppCompatButton btnBlock = findViewById(R.id.btnBlock);
        MaterialButton infoBtn = findViewById(R.id.infoBtn);
        noteWarn = findViewById(R.id.noteWarn);
        list = findViewById(R.id.rv_blocked_sites);

        hideHandler = new Handler(Looper.getMainLooper());

        BlockedSitesManager.ensureSetsExist(this);
        BlockedSitesManager.seedDefaultsIfFirstTimeAndEmpty(this);

        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SitesAdapter(loadAll());
        list.setAdapter(adapter);

        // Setup tab selection
        updateTabSelection();

        tabDomain.setOnClickListener(v -> {
            if (!isDomainMode) {
                isDomainMode = true;
                updateTabSelection();
            }
        });

        tabExactUrl.setOnClickListener(v -> {
            if (isDomainMode) {
                isDomainMode = false;
                updateTabSelection();
            }
        });

        btnBlock.setOnClickListener(v -> {
            String raw = input.getText() != null ? input.getText().toString().trim() : "";
            if (TextUtils.isEmpty(raw) || raw.contains(" ")) {
                Toast.makeText(this, "Enter a valid URL without spaces", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isDomainMode) {
                String domain = extractHostOrDomain(raw);
                if (TextUtils.isEmpty(domain)) {
                    Toast.makeText(this, "Enter a valid domain or URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                BlockedSitesManager.addDomain(this, domain);
                adapter.add(domain);
            } else {
                String url = normalizeUrlOrKeep(raw);
                if (!looksLikeUrl(url)) {
                    Toast.makeText(this, "Enter a valid URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                BlockedSitesManager.addExactUrl(this, url);
                adapter.add(url);
            }
            input.setText("");
            Toast.makeText(this, "Added", Toast.LENGTH_SHORT).show();
        });

        setupNoteWarn();
        infoBtn.setOnClickListener(v -> showNoteWarn());
    }

    private void setupNoteWarn() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hasBeenShown = prefs.getBoolean(PREF_NOTE_WARN_SHOWN, false);

        if (!hasBeenShown) {
            showNoteWarn();
            hideHandler.postDelayed(() -> {
                hideNoteWarn();
                prefs.edit().putBoolean(PREF_NOTE_WARN_SHOWN, true).apply();
            }, HIDE_DELAY_MS);
        } else {
            noteWarn.setVisibility(View.GONE);
            noteWarn.setAlpha(0f);
        }
    }

    private void showNoteWarn() {
        if (noteWarn == null) return;
        hideHandler.removeCallbacksAndMessages(null);
        noteWarn.clearAnimation();
        noteWarn.setVisibility(View.VISIBLE);
        noteWarn.setAlpha(0f);
        noteWarn.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void hideNoteWarn() {
        if (noteWarn == null) return;

        if (noteWarn.getVisibility() == View.VISIBLE) {
            // Hide with fade out
            noteWarn.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            if (noteWarn != null) {
                                noteWarn.setVisibility(View.GONE);
                            }
                        }
                    })
                    .start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideNoteWarn();
        hideHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        hideNoteWarn();
        hideHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hideHandler != null) {
            hideHandler.removeCallbacksAndMessages(null);
        }
    }

    private List<String> loadAll() {
        Set<String> domains = BlockedSitesManager.getBlockedDomains(this);
        Set<String> exacts = BlockedSitesManager.getBlockedExactUrls(this);
        List<String> items = new ArrayList<>(domains.size() + exacts.size());
        items.addAll(domains);
        items.addAll(exacts);
        return items;
    }

    private static boolean looksLikeUrl(String s) {
        if (TextUtils.isEmpty(s)) return false;
        if (s.contains(" ")) return false;
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return Patterns.WEB_URL.matcher(s).matches();
        }
        return s.contains(".") || s.contains("/");
    }

    private static String normalizeUrlOrKeep(String raw) {
        String val = raw.trim();
        if (!(val.startsWith("http://") || val.startsWith("https://"))) return val;
        try {
            URI uri = new URI(val);
            String path = uri.getPath() != null ? uri.getPath() : "";
            String query = uri.getQuery() != null ? ("?" + uri.getQuery()) : "";
            String norm = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? (":" + uri.getPort()) : "") + path + query;
            if (norm.endsWith("/")) norm = norm.substring(0, norm.length() - 1);
            return norm;
        } catch (URISyntaxException e) {
            return val;
        }
    }

    private static String extractHostOrDomain(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String val = raw.trim();

        try {
            if (!(val.startsWith("http://") || val.startsWith("https://"))) {
                val = "https://" + val;
            }

            URI uri = new URI(val);
            String host = uri.getHost();

            if (host == null) {
                val = val.replaceAll("^(https?://)?(www\\.)?", ""); // remove protocol & www
                int slashIndex = val.indexOf('/');
                if (slashIndex != -1) val = val.substring(0, slashIndex);
                return val;
            }

            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            return host;

        } catch (Exception e) {
            // fallback to simple cleanup
            val = val.replaceAll("^(https?://)?(www\\.)?", ""); // remove protocol & www
            int slashIndex = val.indexOf('/');
            if (slashIndex != -1) val = val.substring(0, slashIndex);
            return val;
        }
    }

    private class SitesAdapter extends RecyclerView.Adapter<SiteVH> {
        private final List<String> items;

        SitesAdapter(List<String> items) {
            // keep insertion order, ensure unique
            LinkedHashSet<String> unique = new LinkedHashSet<>(items);
            this.items = new ArrayList<>(unique);
        }

        void add(String value) {
            if (!items.contains(value)) {
                items.add(0, value);
                notifyItemInserted(0);
                list.scrollToPosition(0);
            }
        }

        @NonNull
        @Override
        public SiteVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater().inflate(R.layout.item_blocked_site, parent, false);
            return new SiteVH(view);
        }

        @Override
        public void onBindViewHolder(SiteVH holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void removeAt(int pos) {
            if (pos < 0 || pos >= items.size()) return;
            String val = items.remove(pos);
            BlockedSitesManager.remove(SiteBlockerActivity.this, val);
            notifyItemRemoved(pos);
        }
    }

    private class SiteVH extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final TextView text;
        private final ImageView action;

        SiteVH(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_icon);
            name = itemView.findViewById(R.id.tv_name);
            text = itemView.findViewById(R.id.tv_url);
            action = itemView.findViewById(R.id.iv_unblock);
        }

        void bind(String value) {
            text.setText(value);

            String host = extractHostOrDomain(value);
            String iconUrl = "https://icons.duckduckgo.com/ip3/" + host + ".ico";

            Glide.with(icon.getContext())
                    .load(iconUrl)
                    .error(Glide.with(icon.getContext()).load("https://www.google.com/s2/favicons?domain=" + host + "&sz=64"))
                    .into(icon);

            // Extract and set site name (max 2 words)
            String siteName = extractSiteName(value);
            if (!TextUtils.isEmpty(siteName)) {
                String[] words = siteName.split("\\s+");
                if (words.length > 2) {
                    siteName = words[0] + " " + words[1] + "...";
                }
                name.setText(siteName);
                name.setVisibility(android.view.View.VISIBLE);
            } else {
                name.setVisibility(android.view.View.GONE);
            }

            action.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    adapter.removeAt(pos);
                    Toast.makeText(SiteBlockerActivity.this, "Unblocked", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateTabSelection() {
        if (isDomainMode) {
            tabDomain.setBackgroundResource(R.drawable.bg_segment_selected);
            tabDomain.setTextColor(getAttrColor(R.attr.text_primary));
            tabExactUrl.setBackgroundResource(0);
            tabExactUrl.setTextColor(getAttrColor(R.attr.text_tertiary));
        } else {
            tabExactUrl.setBackgroundResource(R.drawable.bg_segment_selected);
            tabExactUrl.setTextColor(getAttrColor(R.attr.text_primary));
            tabDomain.setBackgroundResource(0);
            tabDomain.setTextColor(getAttrColor(R.attr.text_tertiary));
        }
    }

    private int getAttrColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private static String extractSiteName(String urlOrDomain) {
        if (TextUtils.isEmpty(urlOrDomain)) return "";

        String domain = extractHostOrDomain(urlOrDomain);
        if (TextUtils.isEmpty(domain)) return "";

        // Remove common TLDs and split by dots
        String[] parts = domain.split("\\.");
        if (parts.length == 0) return "";

        String mainPart = parts[0];

        if (!mainPart.isEmpty()) {
            mainPart = mainPart.substring(0, 1).toUpperCase() + mainPart.substring(1).toLowerCase();
        }

        switch (mainPart) {
            case "Youtube":
            case "Yt":
                return "YouTube";
            case "Instagram":
            case "Insta":
                return "Instagram";
            case "Facebook":
            case "Fb":
                return "Facebook";
            case "Twitter":
            case "X":
                return "Twitter";
            case "Tiktok":
            case "Tt":
                return "TikTok";
            case "Reddit":
                return "Reddit";
            case "Linkedin":
                return "LinkedIn";
            case "Snapchat":
            case "Snap":
                return "Snapchat";
        }

        if (urlOrDomain.contains("/")) {
            String path = urlOrDomain;
            try {
                if (!path.startsWith("http://") && !path.startsWith("https://")) {
                    path = "https://" + path;
                }
                URI uri = new URI(path);
                String fullPath = uri.getPath();
                if (fullPath != null && fullPath.length() > 1) {
                    String[] pathSegments = fullPath.split("/");
                    for (String segment : pathSegments) {
                        if (!segment.isEmpty() && segment.length() > 2) {
                            String segmentName = segment.substring(0, 1).toUpperCase() +
                                    segment.substring(1).toLowerCase();
                            // Return max 2 words: main domain + path segment
                            return mainPart + " " + segmentName;
                        }
                    }
                }
            } catch (Exception e) {
                // Fall through to return just main part
            }
        }
        return mainPart;
    }
}


