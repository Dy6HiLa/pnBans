package ru.privatenull.pnbans.dupeip;

import ru.privatenull.pnbans.PnBansPlugin;
import ru.privatenull.pnbans.model.AccountIpRecord;
import ru.privatenull.pnbans.model.AccountProfile;
import ru.privatenull.pnbans.model.Punishment;
import ru.privatenull.pnbans.model.PunishmentType;
import ru.privatenull.pnbans.storage.PunishmentService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DupeIpService {

    private static final long MINUTE = 60_000L;
    private static final String UNKNOWN_IP = "unknown";
    private static final String UNKNOWN_COUNTRY = "Не определена";

    private final PnBansPlugin plugin;
    private final PunishmentService punishmentService;
    private final GeoIpService geoIp;

    public DupeIpService(PnBansPlugin plugin, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
        this.geoIp = new GeoIpService(plugin);
    }

    public Analysis recordAndAnalyze(UUID uuid, String name, String ip, String world,
                                     double x, double y, double z) throws Exception {
        String normalizedIp = GeoIpService.normalizeIp(ip);
        if (normalizedIp == null) return empty(Status.NO_IP, uuid, name);

        long now = System.currentTimeMillis();
        punishmentService.upsertAccountProfile(new AccountProfile(
                uuid, name, normalizedIp, world, x, y, z, now, now));
        AccountProfile stored = punishmentService.accountProfile(uuid, name);
        return analyze(stored == null
                ? new AccountProfile(uuid, name, normalizedIp, world, x, y, z, now, now)
                : stored, true);
    }

    public Analysis analyze(UUID uuid, String name) throws Exception {
        AccountProfile profile = punishmentService.accountProfile(uuid, name);
        return profile == null ? empty(Status.NO_PROFILE, uuid, name) : analyze(profile, false);
    }

    private Analysis empty(Status status, UUID uuid, String name) {
        return new Analysis(status, uuid, name, null, UNKNOWN_IP, UNKNOWN_COUNTRY, 0, List.of(),
                plugin.getConfig().getInt("dupeip.detection.alert-risk-score", 50),
                plugin.getConfig().getBoolean("dupeip.detection.alert-on-same-ip", true),
                plugin.getConfig().getBoolean("dupeip.detection.alert-on-similar-name", true));
    }

    private Analysis analyze(AccountProfile current, boolean alertScan) throws Exception {
        int sameIpLimit = boundedInt("dupeip.same-ip-limit", 30, 1, 500);
        int recentLimit = boundedInt("dupeip.recent-scan-limit", 300, 1, 5_000);
        int ipHistoryLimit = boundedInt("dupeip.ip-history-limit", 30, 1, 200);
        int matchLimit = alertScan
                ? boundedInt("dupeip.alert-max-matches", 20, 1, 100)
                : boundedInt("dupeip.max-matches", 100, 1, 500);
        int historyLimit = boundedInt("dupeip.detection.history-limit", 200, 1, 500);
        double threshold = Math.max(0.50D, Math.min(1D,
                plugin.getConfig().getDouble("dupeip.name-similarity-threshold", 0.82D)));
        long now = System.currentTimeMillis();

        List<AccountIpRecord> currentHistory = new ArrayList<>(
                punishmentService.accountIpHistory(current.uuid(), current.name(), ipHistoryLimit));
        if (currentHistory.isEmpty()) currentHistory.add(ipRecord(current));

        Map<String, AccountIpRecord> currentIps = new LinkedHashMap<>();
        for (AccountIpRecord record : currentHistory) {
            String normalized = GeoIpService.normalizeIp(record.ip());
            if (normalized != null) currentIps.putIfAbsent(normalized, record);
        }
        String currentIp = GeoIpService.normalizeIp(current.ip());
        if (currentIp == null) currentIp = UNKNOWN_IP;
        if (!UNKNOWN_IP.equals(currentIp) && !currentIps.containsKey(currentIp)) {
            currentIps.put(currentIp, ipRecord(current));
        }

        if (ignoredPlayer(current.name())) {
            return new Analysis(Status.READY, current.uuid(), current.name(), current, currentIp,
                    geoIp.country(currentIp), currentIps.size(), List.of(),
                    boundedInt("dupeip.detection.alert-risk-score", 50, 0, 100),
                    plugin.getConfig().getBoolean("dupeip.detection.alert-on-same-ip", true),
                    plugin.getConfig().getBoolean("dupeip.detection.alert-on-similar-name", true));
        }

        Map<UUID, MatchBuilder> matches = new LinkedHashMap<>();
        for (Map.Entry<String, AccountIpRecord> entry : currentIps.entrySet()) {
            if (ignoredIp(entry.getKey())) continue;
            for (AccountIpRecord related : punishmentService.accountIpHistoryByIp(entry.getKey(), sameIpLimit)) {
                if (related.uuid().equals(current.uuid()) || ignoredPlayer(related.name())) continue;
                MatchBuilder builder = matches.computeIfAbsent(related.uuid(),
                        ignored -> new MatchBuilder(profile(related)));
                builder.prefer(profile(related));
                builder.addSharedIp(entry.getKey(), entry.getValue(), related);
                double similarity = nameSimilarity(current.name(), related.name());
                builder.nameSimilarity = Math.max(builder.nameSimilarity, similarity);
                builder.similarName |= similarity >= threshold;
            }
        }

        for (AccountProfile profile : punishmentService.recentAccountProfiles(recentLimit)) {
            if (profile.uuid().equals(current.uuid()) || ignoredPlayer(profile.name())) continue;
            double similarity = nameSimilarity(current.name(), profile.name());
            if (similarity < threshold) continue;
            MatchBuilder builder = matches.computeIfAbsent(profile.uuid(), ignored -> new MatchBuilder(profile));
            builder.prefer(profile);
            builder.nameSimilarity = Math.max(builder.nameSimilarity, similarity);
            builder.similarName = true;
        }

        List<MatchBuilder> candidates = new ArrayList<>(matches.values());
        candidates.sort(DupeIpService::compareCandidates);
        if (candidates.size() > matchLimit) candidates = new ArrayList<>(candidates.subList(0, matchLimit));

        List<Match> result = new ArrayList<>();
        for (MatchBuilder builder : candidates) {
            List<AccountIpRecord> relatedHistory = punishmentService.accountIpHistory(
                    builder.profile.uuid(), builder.profile.name(), ipHistoryLimit);
            Set<String> knownIps = new LinkedHashSet<>();
            for (AccountIpRecord record : relatedHistory) {
                String normalized = GeoIpService.normalizeIp(record.ip());
                if (normalized != null) knownIps.add(normalized);
            }
            String profileIp = GeoIpService.normalizeIp(builder.profile.ip());
            if (profileIp != null) knownIps.add(profileIp);

            List<Punishment> history = punishmentService.historyWithIps(
                    builder.profile.uuid(), builder.profile.name(), knownIps, historyLimit);
            PunishmentService.PunishmentSummary summary = punishmentService.summarize(history);
            RiskAssessment risk = assessRisk(current, builder, threshold, summary, history, now);
            result.add(new Match(builder.profile, UNKNOWN_COUNTRY, !builder.sharedIps.isEmpty(),
                    builder.similarName, builder.nameSimilarity, List.copyOf(builder.sharedIps),
                    knownIps.size(), builder.mostRecentSharedSeen(), summary,
                    risk.score(), risk.level(), risk.signals(), risk.banEvasion()));
        }
        result.sort(Comparator.comparingInt(Match::riskScore).reversed()
                .thenComparing(Match::sameIp, Comparator.reverseOrder())
                .thenComparing(Match::similarName, Comparator.reverseOrder())
                .thenComparing(Match::nameSimilarity, Comparator.reverseOrder())
                .thenComparing(match -> match.profile().lastSeen(), Comparator.reverseOrder()));

        String country = geoIp.country(currentIp);
        int geoIpLookups = boundedInt("dupeip.geoip.max-lookups-per-analysis", 10, 0, 100);
        List<Match> localized = new ArrayList<>(result.size());
        for (int index = 0; index < result.size(); index++) {
            Match match = result.get(index);
            String relatedCountry = index < geoIpLookups
                    ? geoIp.country(match.profile().ip())
                    : geoIp.cachedCountry(match.profile().ip());
            localized.add(match.withCountry(relatedCountry));
        }

        int alertRisk = boundedInt("dupeip.detection.alert-risk-score", 50, 0, 100);
        return new Analysis(Status.READY, current.uuid(), current.name(), current, currentIp, country,
                currentIps.size(), List.copyOf(localized), alertRisk,
                plugin.getConfig().getBoolean("dupeip.detection.alert-on-same-ip", true),
                plugin.getConfig().getBoolean("dupeip.detection.alert-on-similar-name", true));
    }

    private RiskAssessment assessRisk(AccountProfile current, MatchBuilder related, double threshold,
                                      PunishmentService.PunishmentSummary summary, List<Punishment> history,
                                      long now) {
        long evasionWindow = minutes("dupeip.detection.ban-evasion-window-minutes", 30);
        long switchWindow = minutes("dupeip.detection.quick-switch-window-minutes", 10);
        long newAccountWindow = minutes("dupeip.detection.new-account-window-minutes", 15);

        boolean newAccount = current.firstSeen() > 0L && now - current.firstSeen() <= newAccountWindow;
        long afterBan = -1L;
        for (Punishment punishment : history) {
            boolean relevantBan = punishment.type() == PunishmentType.BAN
                    || punishment.type() == PunishmentType.IP_BAN
                    && related.sharedIps.contains(GeoIpService.normalizeIp(punishment.ip()));
            if (!relevantBan) continue;
            for (AccountIpRecord record : related.currentShared.values()) {
                long delay = record.firstSeen() - punishment.createdAt();
                if (delay >= 0L && delay <= evasionWindow && (afterBan < 0L || delay < afterBan)) {
                    afterBan = delay;
                }
            }
        }
        boolean banEvasion = afterBan >= 0L;
        long switchDelay = related.mostRecentSharedSeen() <= 0L
                ? -1L : now - related.mostRecentSharedSeen();
        boolean quickSwitch = !related.sharedIps.isEmpty()
                && switchDelay >= 0L && switchDelay <= switchWindow;

        List<String> signals = new ArrayList<>();
        int score = 0;
        if (!related.sharedIps.isEmpty()) {
            score += 30;
            signals.add("Совпадает IP-адрес");
        }
        if (related.sharedIps.size() > 1) {
            score += Math.min(15, (related.sharedIps.size() - 1) * 5);
            signals.add("Общих IP-адресов: " + related.sharedIps.size());
        }
        if (related.similarName && related.nameSimilarity >= threshold) {
            score += 10 + (int) Math.round(related.nameSimilarity * 15D);
            signals.add("Похожий корень ника: " + percent(related.nameSimilarity) + "%");
        }
        if (summary.activeBans() > 0) {
            score += 25;
            signals.add("Связанный аккаунт или его IP сейчас заблокирован");
        }
        if (summary.activeMutes() > 0) {
            score += 10;
            signals.add("На связанном аккаунте активный мут");
        }
        if (banEvasion) {
            score += 35;
            signals.add("Аккаунт впервые замечен на общем IP через "
                    + durationMinutes(afterBan) + " после бана");
        }
        if (quickSwitch) {
            score += 10;
            signals.add("Быстрое переключение аккаунтов: " + durationMinutes(switchDelay));
        }
        if (newAccount && (!related.sharedIps.isEmpty() || related.similarName)) {
            score += 5;
            signals.add("Этот UUID впервые замечен недавно");
        }
        score = Math.min(100, score);
        return new RiskAssessment(score, riskLevel(score), List.copyOf(signals), banEvasion);
    }

    private int boundedInt(String path, int fallback, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, plugin.getConfig().getInt(path, fallback)));
    }

    private long minutes(String path, int fallback) {
        return Math.max(1L, plugin.getConfig().getLong(path, fallback)) * MINUTE;
    }

    private boolean ignoredIp(String ip) {
        for (String configured : plugin.getConfig().getStringList("dupeip.ignored-ips")) {
            if (ip.equals(GeoIpService.normalizeIp(configured))) return true;
        }
        return false;
    }

    private boolean ignoredPlayer(String name) {
        for (String configured : plugin.getConfig().getStringList("dupeip.ignored-players")) {
            if (configured.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static int compareCandidates(MatchBuilder left, MatchBuilder right) {
        int compared = Boolean.compare(!right.sharedIps.isEmpty(), !left.sharedIps.isEmpty());
        if (compared != 0) return compared;
        compared = Boolean.compare(right.similarName, left.similarName);
        if (compared != 0) return compared;
        compared = Double.compare(right.nameSimilarity, left.nameSimilarity);
        if (compared != 0) return compared;
        return Long.compare(right.profile.lastSeen(), left.profile.lastSeen());
    }

    private static AccountProfile profile(AccountIpRecord record) {
        return new AccountProfile(record.uuid(), record.name(), record.ip(), record.world(),
                record.x(), record.y(), record.z(), record.firstSeen(), record.lastSeen());
    }

    private static AccountIpRecord ipRecord(AccountProfile profile) {
        return new AccountIpRecord(profile.uuid(), profile.name(), profile.ip(), profile.world(),
                profile.x(), profile.y(), profile.z(), profile.firstSeen(), profile.lastSeen(), 1L);
    }

    private static String durationMinutes(long millis) {
        long value = Math.max(1L, (millis + MINUTE - 1L) / MINUTE);
        return value + " мин.";
    }

    private static int percent(double value) {
        return (int) Math.round(value * 100D);
    }

    static double nameSimilarity(String first, String second) {
        String left = normalizeName(first);
        String right = normalizeName(second);
        if (left.isEmpty() || right.isEmpty()) return 0D;
        if (left.equals(right)) return 1D;

        double levenshtein = 1D - ((double) levenshtein(left, right) / Math.max(left.length(), right.length()));
        double dice = diceCoefficient(left, right);
        int shorter = Math.min(left.length(), right.length());
        int longer = Math.max(left.length(), right.length());
        double containment = 0D;
        if (shorter >= 5 && (left.contains(right) || right.contains(left))) {
            containment = 0.85D + 0.15D * ((double) shorter / longer);
        }
        return Math.min(1D, Math.max(containment, Math.max(levenshtein, dice)));
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        StringBuilder normalized = new StringBuilder();
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character >= 'a' && character <= 'z') normalized.append(character);
            else if (character >= '0' && character <= '9') normalized.append(switch (character) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                default -> character;
            });
        }
        return normalized.toString();
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(Math.min(current[rightIndex - 1] + 1,
                        previous[rightIndex] + 1), previous[rightIndex - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static double diceCoefficient(String left, String right) {
        if (left.length() < 2 || right.length() < 2) return 0D;
        Map<String, Integer> pairs = new LinkedHashMap<>();
        for (int index = 0; index < left.length() - 1; index++) {
            pairs.merge(left.substring(index, index + 2), 1, Integer::sum);
        }
        int intersection = 0;
        for (int index = 0; index < right.length() - 1; index++) {
            String pair = right.substring(index, index + 2);
            Integer count = pairs.get(pair);
            if (count != null && count > 0) {
                intersection++;
                pairs.put(pair, count - 1);
            }
        }
        return (2D * intersection) / (left.length() + right.length() - 2D);
    }

    private static String riskLevel(int score) {
        if (score >= 75) return "Критический";
        if (score >= 50) return "Высокий";
        if (score >= 25) return "Средний";
        return "Низкий";
    }

    private static final class MatchBuilder {
        private AccountProfile profile;
        private final Set<String> sharedIps = new LinkedHashSet<>();
        private final Map<String, AccountIpRecord> currentShared = new LinkedHashMap<>();
        private final Map<String, AccountIpRecord> relatedShared = new LinkedHashMap<>();
        private boolean similarName;
        private double nameSimilarity;

        private MatchBuilder(AccountProfile profile) {
            this.profile = profile;
        }

        private void prefer(AccountProfile candidate) {
            if (candidate.lastSeen() > profile.lastSeen()) profile = candidate;
        }

        private void addSharedIp(String ip, AccountIpRecord current, AccountIpRecord related) {
            sharedIps.add(ip);
            currentShared.put(ip, current);
            AccountIpRecord previous = relatedShared.get(ip);
            if (previous == null || related.lastSeen() > previous.lastSeen()) relatedShared.put(ip, related);
        }

        private long mostRecentSharedSeen() {
            return relatedShared.values().stream().mapToLong(AccountIpRecord::lastSeen).max().orElse(0L);
        }
    }

    private record RiskAssessment(int score, String level, List<String> signals, boolean banEvasion) {
    }

    public enum Status {
        READY,
        NO_PROFILE,
        NO_IP
    }

    public record Analysis(Status status, UUID uuid, String name, AccountProfile profile,
                           String ip, String country, int knownIpCount, List<Match> matches,
                           int alertRisk, boolean alertOnSameIp, boolean alertOnSimilarName) {
        public int sameIpCount() {
            return (int) matches.stream().filter(Match::sameIp).count();
        }

        public int similarNameCount() {
            return (int) matches.stream().filter(Match::similarName).count();
        }

        public int activeBanCount() {
            return matches.stream().mapToInt(match -> match.summary().activeBans()).sum();
        }

        public int activeMuteCount() {
            return matches.stream().mapToInt(match -> match.summary().activeMutes()).sum();
        }

        public int highestRiskScore() {
            return matches.stream().mapToInt(Match::riskScore).max().orElse(0);
        }

        public String highestRiskLevel() {
            return riskLevel(highestRiskScore());
        }

        public boolean shouldAlert() {
            if (status != Status.READY) return false;
            return matches.stream().anyMatch(match -> match.riskScore() >= alertRisk
                    || alertOnSameIp && match.sameIp()
                    || alertOnSimilarName && match.similarName());
        }
    }

    public record Match(AccountProfile profile, String country, boolean sameIp, boolean similarName,
                        double nameSimilarity, List<String> sharedIps, int knownIpCount,
                        long sharedLastSeen, PunishmentService.PunishmentSummary summary,
                        int riskScore, String riskLevel, List<String> signals, boolean banEvasion) {
        private Match withCountry(String value) {
            return new Match(profile, value, sameIp, similarName, nameSimilarity, sharedIps,
                    knownIpCount, sharedLastSeen, summary, riskScore, riskLevel, signals, banEvasion);
        }
    }
}
