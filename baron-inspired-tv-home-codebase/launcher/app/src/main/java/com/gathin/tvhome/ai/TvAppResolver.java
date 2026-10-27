package com.gathin.tvhome.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure Java multi-tier application resolver implementing TvAppLookup.
 * Executes Tiers 1-5: Exact Package ID, Exact Label, Known TV App Aliases,
 * Normalized Token Containment, and Levenshtein Distance (<= 2).
 */
public class TvAppResolver implements TvAppLookup {

    public static class AppEntry {
        public final String pkg;
        public final String label;
        public final String normalizedLabel;

        public AppEntry(String pkg, String label) {
            this.pkg = pkg != null ? pkg : "";
            this.label = label != null ? label : "";
            this.normalizedLabel = normalize(this.label);
        }
    }

    private final Map<String, String> packageToLabel = new HashMap<>();
    private final Map<String, String> lowerPkgToActualPkg = new HashMap<>();
    private final Map<String, String> lowerLabelToPkg = new HashMap<>();
    private final List<AppEntry> installedApps = new ArrayList<>();

    // Static Dictionary of Known TV App Aliases
    private static final Map<String, List<String>> ALIAS_MAP = new HashMap<>();

    static {
        registerAlias(Arrays.asList("youtube", "yt", "you tube", "utube"),
                "com.google.android.youtube.tv", "com.google.android.youtube");
        registerAlias(Arrays.asList("youtube music", "yt music", "ytmusic"),
                "com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music");
        registerAlias(Arrays.asList("netflix", "net flix"),
                "com.netflix.ninja", "com.netflix.mediaclient");
        registerAlias(Arrays.asList("prime video", "prime", "amazon prime", "amazon prime video", "amazon video"),
                "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient");
        registerAlias(Arrays.asList("disney", "disney+", "disneyplus", "disney plus", "hotstar", "disney hotstar", "disney plus hotstar"),
                "in.startv.hotstar", "com.disney.disneyplus");
        registerAlias(Arrays.asList("spotify", "spotfy"),
                "com.spotify.tv.android", "com.spotify.music");
        registerAlias(Arrays.asList("kodi", "xbmc"),
                "org.xbmc.kodi");
        registerAlias(Arrays.asList("vlc", "vlc player", "vlc for android"),
                "org.videolan.vlc");
        registerAlias(Arrays.asList("plex", "plex tv"),
                "com.plexapp.android");
        registerAlias(Arrays.asList("twitch"),
                "tv.twitch.android.app");
        registerAlias(Arrays.asList("hulu"),
                "com.hulu.livingroomplus", "com.hulu.plus");
        registerAlias(Arrays.asList("max", "hbo", "hbo max"),
                "com.wbd.stream", "com.hbo.hbonow");
        registerAlias(Arrays.asList("apple tv", "apple tv+", "appletvplus", "apple tv plus", "appletv"),
                "com.apple.atve.androidtv.appletv");
        registerAlias(Arrays.asList("paramount", "paramount+", "paramountplus", "paramount plus"),
                "com.cbs.ott");
        registerAlias(Arrays.asList("peacock", "peacock tv"),
                "com.peacocktv.peacockandroid");
        registerAlias(Arrays.asList("crunchyroll", "anime"),
                "com.crunchyroll.crunchyroid");
        registerAlias(Arrays.asList("tubi", "tubi tv"),
                "com.tubitv");
        registerAlias(Arrays.asList("pluto", "pluto tv"),
                "tv.pluto.android");
        registerAlias(Arrays.asList("jiocinema", "jio cinema", "jio"),
                "com.jio.media.ondemand");
        registerAlias(Arrays.asList("sony liv", "sonyliv", "sony"),
                "com.msr.app", "com.sonyliv");
        registerAlias(Arrays.asList("zee5", "zee 5", "zee"),
                "com.graymatrix.did");
        registerAlias(Arrays.asList("sun nxt", "sunnxt"),
                "com.suntv.sunnxt");
        registerAlias(Arrays.asList("mx player", "mxplayer"),
                "com.mxtech.videoplayer.television", "com.mxtech.videoplayer.ad");
        registerAlias(Arrays.asList("smarttube", "smart tube", "smarttubenext"),
                "com.teamsmart.videomanager.tv", "com.liskovsoft.videomanager");
        registerAlias(Arrays.asList("play store", "google play", "app store", "playstore"),
                "com.android.vending");
        registerAlias(Arrays.asList("file commander", "files", "file manager"),
                "com.mobisystems.fileman", "com.android.documentsui");
    }

    private static void registerAlias(List<String> aliases, String... packages) {
        List<String> pkgList = Arrays.asList(packages);
        for (String alias : aliases) {
            ALIAS_MAP.put(alias.toLowerCase(Locale.ROOT), pkgList);
            ALIAS_MAP.put(normalize(alias), pkgList);
        }
    }

    public TvAppResolver() {}

    public TvAppResolver(Map<String, String> labelToPackageMap) {
        if (labelToPackageMap != null) {
            for (Map.Entry<String, String> entry : labelToPackageMap.entrySet()) {
                addApp(entry.getValue(), entry.getKey());
            }
        }
    }

    public void addApp(String pkg, String label) {
        if (pkg == null || pkg.isEmpty()) return;
        String finalLabel = (label != null && !label.isEmpty()) ? label : pkg;
        packageToLabel.put(pkg, finalLabel);
        lowerPkgToActualPkg.put(pkg.toLowerCase(Locale.ROOT), pkg);
        lowerLabelToPkg.put(finalLabel.toLowerCase(Locale.ROOT), pkg);
        installedApps.add(new AppEntry(pkg, finalLabel));
    }

    @Override
    public String findPackageForLabel(String query) {
        if (query == null) return null;
        String q = query.trim();
        if (q.isEmpty()) return null;

        // Tier 1: Exact Package ID
        String tier1 = matchTier1PackageId(q);
        if (tier1 != null) return tier1;

        // Tier 2: Exact Case-Insensitive Label
        String tier2 = matchTier2ExactLabel(q);
        if (tier2 != null) return tier2;

        // Tier 3: Known TV App Aliases
        String tier3 = matchTier3Alias(q);
        if (tier3 != null) return tier3;

        // Tier 4: Normalized Token Containment
        String tier4 = matchTier4Containment(q);
        if (tier4 != null) return tier4;

        // Tier 5: Pure Java Levenshtein Distance (<= 2)
        String tier5 = matchTier5Levenshtein(q);
        if (tier5 != null) return tier5;

        return null;
    }

    @Override
    public String getLabelForPackage(String pkg) {
        if (pkg == null) return null;
        String label = packageToLabel.get(pkg);
        return label != null ? label : pkg;
    }

    private String matchTier1PackageId(String q) {
        if (q.indexOf('.') > 0) {
            String direct = lowerPkgToActualPkg.get(q.toLowerCase(Locale.ROOT));
            if (direct != null) return direct;
            if (packageToLabel.containsKey(q)) return q;
        }
        return null;
    }

    private String matchTier2ExactLabel(String q) {
        return lowerLabelToPkg.get(q.toLowerCase(Locale.ROOT));
    }

    private String matchTier3Alias(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        List<String> candidates = ALIAS_MAP.get(lower);
        if (candidates == null) {
            candidates = ALIAS_MAP.get(normalize(q));
        }
        if (candidates != null) {
            for (String pkg : candidates) {
                if (packageToLabel.containsKey(pkg)) {
                    return pkg;
                }
            }
        }
        return null;
    }

    private String matchTier4Containment(String q) {
        String normQ = normalize(q);
        if (normQ.length() < 3) return null;

        String bestMatch = null;
        int bestLengthDiff = Integer.MAX_VALUE;

        for (AppEntry app : installedApps) {
            String normLabel = app.normalizedLabel;
            if (normLabel.isEmpty()) continue;

            if (normLabel.equals(normQ)) {
                return app.pkg;
            }
            if (normLabel.contains(normQ) || normQ.contains(normLabel)) {
                int diff = Math.abs(normLabel.length() - normQ.length());
                if (diff < bestLengthDiff) {
                    bestLengthDiff = diff;
                    bestMatch = app.pkg;
                }
            }
        }
        return bestMatch;
    }

    private String matchTier5Levenshtein(String q) {
        String normQ = normalize(q);
        int qLen = normQ.length();
        if (qLen < 3) return null;
        int maxDist = (qLen == 3) ? 1 : 2;

        String bestMatch = null;
        int bestDist = Integer.MAX_VALUE;
        int bestLengthDiff = Integer.MAX_VALUE;

        // Compare against installed app labels
        for (AppEntry app : installedApps) {
            String normLabel = app.normalizedLabel;
            if (normLabel.isEmpty()) continue;

            int lenDiff = Math.abs(normLabel.length() - qLen);
            if (lenDiff > maxDist) continue;

            int dist = LevenshteinDistance.compute(normQ, normLabel, maxDist);
            if (dist <= maxDist) {
                if (dist < bestDist || (dist == bestDist && lenDiff < bestLengthDiff)) {
                    bestDist = dist;
                    bestLengthDiff = lenDiff;
                    bestMatch = app.pkg;
                }
            }
        }

        if (bestMatch != null) return bestMatch;

        // Compare against known aliases pointing to installed apps
        for (Map.Entry<String, List<String>> entry : ALIAS_MAP.entrySet()) {
            String normAlias = normalize(entry.getKey());
            int lenDiff = Math.abs(normAlias.length() - qLen);
            if (lenDiff > maxDist) continue;

            int dist = LevenshteinDistance.compute(normQ, normAlias, maxDist);
            if (dist <= maxDist) {
                for (String candidatePkg : entry.getValue()) {
                    if (packageToLabel.containsKey(candidatePkg)) {
                        if (dist < bestDist || (dist == bestDist && lenDiff < bestLengthDiff)) {
                            bestDist = dist;
                            bestLengthDiff = lenDiff;
                            bestMatch = candidatePkg;
                        }
                        break;
                    }
                }
            }
        }

        return bestMatch;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + ('a' - 'A')));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (c == '+') {
                sb.append("plus");
            }
        }
        return sb.toString();
    }
}
