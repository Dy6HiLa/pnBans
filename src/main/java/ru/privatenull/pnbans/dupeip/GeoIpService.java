package ru.privatenull.pnbans.dupeip;

import ru.privatenull.pnbans.PnBansPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves a public IP to a Russian country name through ipwho.is and caches the result. */
public final class GeoIpService {
    private static final Pattern COUNTRY = Pattern.compile("\\\"country\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern SUCCESS_FALSE = Pattern.compile("\\\"success\\\"\\s*:\\s*false", Pattern.CASE_INSENSITIVE);
    private static final String UNKNOWN = "Не определена";
    private static final String LOCAL = "Локальная сеть";

    private final PnBansPlugin plugin;
    private final Map<String, CachedCountry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<String>> lookups = new ConcurrentHashMap<>();

    public GeoIpService(PnBansPlugin plugin) {
        this.plugin = plugin;
    }

    public String country(String rawIp) {
        String ip = normalizeIp(rawIp);
        if (ip == null) return UNKNOWN;
        if (isLocal(ip)) return LOCAL;
        if (!plugin.getConfig().getBoolean("dupeip.geoip.enabled", true)) return UNKNOWN;

        long now = System.currentTimeMillis();
        CachedCountry cached = cache.get(ip);
        if (cached != null && cached.expiresAt() > now) return cached.value();

        CompletableFuture<String> ownLookup = new CompletableFuture<>();
        CompletableFuture<String> activeLookup = lookups.putIfAbsent(ip, ownLookup);
        if (activeLookup != null) {
            int timeout = Math.max(1_000, plugin.getConfig().getInt("dupeip.geoip.connect-timeout-ms", 3_000)
                    + plugin.getConfig().getInt("dupeip.geoip.read-timeout-ms", 3_000) + 1_000);
            try {
                return activeLookup.get(timeout, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                return UNKNOWN;
            }
        }

        try {
            String country = lookup(ip);
            long hours = Math.max(1L, plugin.getConfig().getLong("dupeip.geoip.cache-hours", 24L));
            trimCache(now);
            cache.put(ip, new CachedCountry(country, now + hours * 3_600_000L));
            ownLookup.complete(country);
            return country;
        } catch (RuntimeException exception) {
            ownLookup.complete(UNKNOWN);
            throw exception;
        } finally {
            lookups.remove(ip, ownLookup);
        }
    }

    /** Returns an already known country without starting a blocking HTTP request. */
    public String cachedCountry(String rawIp) {
        String ip = normalizeIp(rawIp);
        if (ip == null) return UNKNOWN;
        if (isLocal(ip)) return LOCAL;
        if (!plugin.getConfig().getBoolean("dupeip.geoip.enabled", true)) return UNKNOWN;
        CachedCountry cached = cache.get(ip);
        return cached != null && cached.expiresAt() > System.currentTimeMillis()
                ? cached.value() : UNKNOWN;
    }

    public static String normalizeIp(String rawIp) {
        if (rawIp == null) return null;
        String value = rawIp.trim();
        if (value.isEmpty() || !value.matches("[0-9A-Fa-f:.]+")) return null;
        if (value.regionMatches(true, 0, "::ffff:", 0, 7)) value = value.substring(7);
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String lookup(String ip) {
        HttpURLConnection connection = null;
        try {
            String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            URI uri = URI.create("https://ipwho.is/" + encodedIp
                    + "?fields=success,country,country_code&lang=ru");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(Math.max(500, plugin.getConfig().getInt("dupeip.geoip.connect-timeout-ms", 3_000)));
            connection.setReadTimeout(Math.max(500, plugin.getConfig().getInt("dupeip.geoip.read-timeout-ms", 3_000)));
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "pnBans GeoIP/" + plugin.getDescription().getVersion());
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return UNKNOWN;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (SUCCESS_FALSE.matcher(body).find()) return UNKNOWN;
            Matcher matcher = COUNTRY.matcher(body);
            return matcher.find() ? unescape(matcher.group(1)) : UNKNOWN;
        } catch (Exception exception) {
            plugin.getLogger().fine("GeoIP lookup failed for " + ip + ": " + exception.getMessage());
            return UNKNOWN;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void trimCache(long now) {
        int maximum = Math.max(100, plugin.getConfig().getInt("dupeip.geoip.max-cache-entries", 5_000));
        if (cache.size() < maximum) return;
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        while (cache.size() >= maximum) {
            String key = cache.keySet().stream().findFirst().orElse(null);
            if (key == null) return;
            cache.remove(key);
        }
    }

    private boolean isLocal(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()) return true;
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (Exception ignored) {
            return true;
        }
    }

    private String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                result.append(current);
                continue;
            }
            char escaped = value.charAt(++index);
            if (escaped == 'u' && index + 4 < value.length()) {
                try {
                    result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    index += 4;
                } catch (NumberFormatException exception) {
                    result.append("\\u");
                }
            } else {
                result.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> escaped;
                });
            }
        }
        return result.toString();
    }

    private record CachedCountry(String value, long expiresAt) {
    }
}
