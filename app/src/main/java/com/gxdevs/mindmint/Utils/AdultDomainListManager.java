
package com.gxdevs.mindmint.Utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdultDomainListManager {

    private static final String TAG = "AdultDomainListManager";
    private static final String GZIP_URL = "https://www.dl.dropboxusercontent.com/scl/fi/iqhvxoxtuvct1xssllivj/hosts.txt.gz?rlkey=wa4ojotpa58bj0u4ugjryqv9d&st=72gb6qzi&dl=0";
    private static final String GZIP_FILE = "adult_domains.gz";
    private static final String EXTRACTED_FILE = "adult_domains_extracted.txt";
    private static volatile Set<String> cachedDomains;

    // Built-in domains that must always be present (de-duplicated on insert)
    private static final String[] PREDEFINED_DOMAINS = new String[]{
            "xvideos.com",
            "xnxx.com",
            "redtube.com",
            "xhamster.com",
            "youporn.com",
            "coomer.st",
            "spankbang.com",
            "brazzers.com",
            "onlyfans.com",
            "chaturbate.com",
            "xhamster18.desi",
            "xhaccess.com",
            "xvideos.com",
            "xvideos2.com",
            "xxvideos.video",
            "xnxx.health",
            "txnhh.com",
            "xvv1deos.com",
            "amp.xhamster.com",
    };

    // Regex patterns to robustly match dynamic/subdomain variants (case-insensitive)
    // Examples covered:
    // - hi.xhamster.com, xhamster18.desi, cdn-1.xhamster3.net
    // - m.xvideos.com, xvideos2.com, static.xxvideos.video
    // - xnxx.health, de.xnxx.com, xnxx1.net
    // - hi.xhmaster.com (common variant seen in the wild)
    private static final Pattern[] ADULT_DOMAIN_PATTERNS = new Pattern[]{
            // xhamster with optional digits and any TLD, supports subdomains
            Pattern.compile("(?i)^([a-z0-9-]+\\.)*xhamster([a-z0-9-]*\\.)*[a-z]{2,}$"),
            // xvideos or xxvideos with optional digits and any TLD, supports subdomains
            Pattern.compile("(?i)^(?:[a-z0-9-]+\\.)*x{1,9}videos\\d{0,2}\\.[a-z0-9.-]+$"),
            // xnxx with optional digits and any TLD, supports subdomains
            Pattern.compile("(?i)^(?:[a-z0-9-]+\\.)*xnxx\\d{0,2}\\.[a-z0-9.-]+$")
    };

    // Decimal format for MB logging
    private static final DecimalFormat MB = new DecimalFormat("#.##");

    public interface OnDownloadCompleteListener {
        void onSuccess(long mergedFileBytes, @Nullable String sha256Hex, boolean deduped);

        void onError(Exception e);
    }

    // --- Public entrypoint ---
    public static void downloadAndBuildList(@NonNull Context context, @Nullable OnDownloadCompleteListener listener, boolean ignored) {
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder().build();

            File filesDir = context.getFilesDir();
            File gzFile = new File(filesDir, GZIP_FILE);
            File extractedFile = new File(filesDir, EXTRACTED_FILE);

            try {
                // Download gzip
                Log.d(TAG, "Downloading gzip: " + GZIP_URL);
                Request req = new Request.Builder().url(GZIP_URL).build();
                try (Response resp = client.newCall(req).execute()) {
                    if (!resp.isSuccessful()) throw new IOException("HTTP failed: " + resp);
                    try (InputStream in = resp.body().byteStream();
                         OutputStream out = new FileOutputStream(gzFile)) {
                        byte[] buf = new byte[8192];
                        int r;
                        long bytes = 0L;
                        while ((r = in.read(buf)) != -1) {
                            out.write(buf, 0, r);
                            bytes += r;
                        }
                        Log.i(TAG, "Downloaded gzip ~ " + MB.format(bytes / 1024.0 / 1024.0) + " MB");
                    }
                }

                // Extract gzip to txt
                Log.d(TAG, "Extracting gzip to: " + extractedFile.getAbsolutePath());
                try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(gzFile.toPath()));
                     OutputStream out = new FileOutputStream(extractedFile)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = gis.read(buf)) != -1) out.write(buf, 0, r);
                }

                // Import into SQLite with transaction
                DomainDatabaseHelper helper = new DomainDatabaseHelper(context);
                SQLiteDatabase db = null;
                SQLiteStatement stmt = null;
                long inserted = 0L;
                try {
                    db = helper.getWritableDatabase();
                    db.beginTransaction();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(
                            Files.newInputStream(extractedFile.toPath()), StandardCharsets.UTF_8))) {
                        String line;
                        stmt = db.compileStatement("INSERT OR IGNORE INTO " + DomainDatabaseHelper.TABLE_DOMAINS + " (" + DomainDatabaseHelper.COL_DOMAIN + ") VALUES (?)");
                        while ((line = br.readLine()) != null) {
                            String t = line.trim();
                            if (t.isEmpty() || t.startsWith("#")) continue;
                            String[] parts = t.split("\\s+");
                            if (parts.length < 2) continue;
                            String candidate = parts[1].trim().toLowerCase(Locale.ROOT);
                            candidate = normalizeCandidate(candidate);
                            if (candidate == null || candidate.isEmpty()) continue;
                            if (isLikelyDomain(candidate)) continue;
                            stmt.clearBindings();
                            stmt.bindString(1, candidate);
                            long rowId = stmt.executeInsert();
                            if (rowId != -1) inserted++;
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    try {
                        if (stmt != null) stmt.close();
                    } catch (Exception ignore) {
                    }
                    if (db != null) {
                        try {
                            if (db.inTransaction()) db.endTransaction();
                        } catch (Exception ignore) {
                        }
                        try {
                            db.close();
                        } catch (Exception ignore) {
                        }
                    }
                    try {
                        helper.close();
                    } catch (Exception ignore) {
                    }
                }

                // Cleanup files
                // Ensure our predefined domains are present as well (no duplicates)
                try {
                    long builtinInserted = ensurePredefinedDomains(context);
                    inserted += builtinInserted;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to ensure predefined domains", e);
                }
                boolean gzDel = gzFile.delete();
                if (!gzDel) Log.w(TAG, "Failed to delete gzip file: " + gzFile.getAbsolutePath());
                boolean txtDel = extractedFile.delete();
                if (!txtDel)
                    Log.w(TAG, "Failed to delete extracted file: " + extractedFile.getAbsolutePath());
                Log.i(TAG, "Import complete. Inserted=" + inserted + ", gzDeleted=" + gzDel + ", txtDeleted=" + txtDel);

                clearCache();
                if (listener != null) listener.onSuccess(inserted, null, false);
            } catch (Exception e) {
                Log.e(TAG, "Failed to download/import gzip", e);
                if (listener != null) listener.onError(e);
                // Try cleanup on failure
                try {
                    if (gzFile.exists()) {
                        boolean ok = gzFile.delete();
                        if (!ok)
                            Log.w(TAG, "Cleanup failed to delete gzip: " + gzFile.getAbsolutePath());
                    }
                } catch (Exception ignored2) {
                }
                try {
                    if (extractedFile.exists()) {
                        boolean ok2 = extractedFile.delete();
                        if (!ok2)
                            Log.w(TAG, "Cleanup failed to delete extracted: " + extractedFile.getAbsolutePath());
                    }
                } catch (Exception ignored3) {
                }
            }
        }).start();
    }

    // --- Utilities ---

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = Files.newInputStream(src.toPath());
             OutputStream out = Files.newOutputStream(dst.toPath())) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
    }

    // legacy estimate method removed for DB approach

    private static String normalizeCandidate(String s) {
        if (TextUtils.isEmpty(s)) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        // remove common numeric IP or prefixes mistakenly included
        if (v.startsWith("0.0.0.0") || v.startsWith("127.0.0.1")) {
            // possible when host file has IP + domain, but our parser uses last token so normally not needed
            // just try to strip ip if present (unlikely)
            int idx = v.indexOf(' ');
            if (idx >= 0 && idx + 1 < v.length()) v = v.substring(idx + 1);
        }
        // remove scheme/paths if accidentally captured
        if (v.startsWith("http://")) v = v.substring(7);
        if (v.startsWith("https://")) v = v.substring(8);
        if (v.startsWith("www.")) v = v.substring(4);
        // remove trailing slashes or ports
        int slash = v.indexOf('/');
        if (slash >= 0) v = v.substring(0, slash);
        int colon = v.indexOf(':');
        if (colon >= 0) v = v.substring(0, colon);
        // trim dots
        while (v.startsWith(".")) v = v.substring(1);
        while (v.endsWith(".")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private static boolean isLikelyDomain(String s) {
        if (s == null) return true;
        if (!s.contains(".")) return true;
        if (s.length() < 4) return true;
        // simple char checks
        for (char c : s.toCharArray()) {
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-')) return true;
        }
        return false;
    }

    public static long ensurePredefinedDomains(@NonNull Context context) {
        DomainDatabaseHelper helper = new DomainDatabaseHelper(context);
        SQLiteDatabase db = null;
        SQLiteStatement stmt = null;
        long inserted = 0L;
        try {
            db = helper.getWritableDatabase();
            db.beginTransaction();
            stmt = db.compileStatement("INSERT OR IGNORE INTO " + DomainDatabaseHelper.TABLE_DOMAINS + " (" + DomainDatabaseHelper.COL_DOMAIN + ") VALUES (?)");
            for (String domain : PREDEFINED_DOMAINS) {
                if (TextUtils.isEmpty(domain)) continue;
                String candidate = normalizeCandidate(domain);
                if (candidate == null || candidate.isEmpty()) continue;
                if (isLikelyDomain(candidate)) continue;
                stmt.clearBindings();
                stmt.bindString(1, candidate);
                long rowId = stmt.executeInsert();
                if (rowId != -1) inserted++;
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "ensurePredefinedDomains failed", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (Exception ignore) {
            }
            if (db != null) {
                try {
                    if (db.inTransaction()) db.endTransaction();
                } catch (Exception ignore) {
                }
                try {
                    db.close();
                } catch (Exception ignore) {
                }
            }
            try {
                helper.close();
            } catch (Exception ignore) {
            }
        }
        return inserted;
    }


    public static synchronized void clearCache() {
        cachedDomains = null;
    }

    public static boolean isAdultHost(@NonNull Context context, @Nullable String host) {
        if (host == null) return false;
        String normalized = normalizeCandidate(host);
        if (normalized == null || normalized.isEmpty()) return false;
        // Quick regex-based pattern check for dynamic variants (e.g., xhamster18.desi, xvideos2.com)
        try {
            if (matchesAdultPattern(normalized)) return true;
        } catch (Exception e) {
            Log.w(TAG, "pattern check failed", e);
        }

        DomainDatabaseHelper helper = new DomainDatabaseHelper(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        // If not domain-like (no '.') treat input as a possible title/keyword and search in DB
        if (!normalized.contains(".")) {
            try {
                String keyword = normalized.replaceAll("[^a-z0-9]+", " ").trim();
                if (!keyword.isEmpty()) {
                    String[] tokens = keyword.split("\\s+");
                    for (String token : tokens) {
                        if (token.length() < 3)
                            continue; // skip very short tokens to reduce false positives
                        try (Cursor c = db.rawQuery(
                                "SELECT 1 FROM " + DomainDatabaseHelper.TABLE_DOMAINS +
                                        " WHERE " + DomainDatabaseHelper.COL_DOMAIN + " LIKE ? COLLATE NOCASE LIMIT 1",
                                new String[]{"%" + token + "%"})) {
                            if (c.moveToFirst()) return true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "DB keyword query failed", e);
                return false;
            }
        }
        String[] parts = normalized.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            if (sb.length() == 0) sb.append(parts[i]);
            else {
                sb.insert(0, '.');
                sb.insert(0, parts[i]);
            }
            String suffix = sb.toString();
            try (Cursor c = db.rawQuery("SELECT 1 FROM " + DomainDatabaseHelper.TABLE_DOMAINS + " WHERE " + DomainDatabaseHelper.COL_DOMAIN + "=? LIMIT 1", new String[]{suffix})) {
                if (c.moveToFirst()) return true;
            } catch (Exception e) {
                Log.e(TAG, "DB query failed", e);
                return false;
            }
        }
        return false;
    }

    private static boolean matchesAdultPattern(@NonNull String host) {
        for (Pattern pattern : ADULT_DOMAIN_PATTERNS) {
            if (pattern.matcher(host).matches()) return true;
        }
        return false;
    }

    @Nullable
    public static String extractHostFromUrlText(@Nullable String urlText) {
        if (TextUtils.isEmpty(urlText)) return null;
        String s = urlText.trim();
        try {
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                s = "https://" + s;
            }
            URL u = new URL(s);
            String host = u.getHost();
            if (host == null) return null;
            // normalize
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            while (host.startsWith(".")) host = host.substring(1);
            while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            return host;
        } catch (MalformedURLException e) {
            // not a URL; maybe raw host
            return normalizeCandidate(s);
        }
    }
}
