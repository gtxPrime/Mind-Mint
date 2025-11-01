package com.gxdevs.mindmint.Activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.gxdevs.mindmint.R;
import com.gxdevs.mindmint.Utils.BlockedSitesManager;
import com.gxdevs.mindmint.Utils.Utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class SiteBlockerActivity extends AppCompatActivity {

    private RecyclerView list;
    private EditText input;
    private MaterialCheckBox exactCheck;
    private SitesAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Utils.applyAppThemeFromPrefs(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_site_blocker);

        findViewById(R.id.backButton).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        input = findViewById(R.id.et_url_input);
        exactCheck = findViewById(R.id.cb_exact_url);
        MaterialButton add = findViewById(R.id.btn_add);
        list = findViewById(R.id.rv_blocked_sites);

        BlockedSitesManager.ensureSetsExist(this);
        // One-time seeding only if lists are empty and never seeded before
        BlockedSitesManager.seedDefaultsIfFirstTimeAndEmpty(this);

        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SitesAdapter(loadAll());
        list.setAdapter(adapter);

        add.setOnClickListener(v -> {
            String raw = input.getText() != null ? input.getText().toString().trim() : "";
            if (TextUtils.isEmpty(raw) || raw.contains(" ")) {
                Toast.makeText(this, "Enter a valid URL without spaces", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean asDomain = !exactCheck.isChecked();
            if (asDomain) {
                String domain = extractHostOrDomain(raw);
                if (TextUtils.isEmpty(domain)) {
                    Toast.makeText(this, "Enter a valid domain or URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                BlockedSitesManager.addDomain(this, domain);
                adapter.add(domain);
            } else {
                // exact URL; accept either full URL or substring pattern
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

        BlurTarget blurTarget = findViewById(R.id.blurTarget);
        BlurView entryBlur = findViewById(R.id.entryBlur);
        entryBlur.setupWith(blurTarget).setBlurRadius(5f);
        applyColors();
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
        // allow patterns like instagram.com/reel for legacy matching
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
        private final android.widget.ImageView icon;
        private final android.widget.TextView text;
        private final android.widget.ImageView action;

        SiteVH(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_icon);
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

            action.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    adapter.removeAt(pos);
                    Toast.makeText(SiteBlockerActivity.this, "Unblocked", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void applyColors() {
        View arc1 = findViewById(R.id.arcTopLeft);
        View arc2 = findViewById(R.id.arcBottomRight);
        View arc3 = findViewById(R.id.arcBottomLeft);
        MaterialCardView addCard = findViewById(R.id.addCard);
        Utils.applySecondaryColor(addCard, this);
        Utils.applyAccentColors(arc1, arc2, arc3, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }
}


