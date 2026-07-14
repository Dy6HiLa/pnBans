package ru.privatenull.pnbans.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeFormat {
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)(mo|w|d|h|m|s)");
    private TimeFormat() { }

    public static String remaining(long expiresAt, long now) {
        if (expiresAt == 0L) return "навсегда"; // Legacy punishments created by older plugin builds.
        long millis = expiresAt - now;
        if (millis <= 0L) return "истёк";
        long seconds = millis / 1_000L + (millis % 1_000L == 0L ? 0L : 1L);
        return human(seconds);
    }

    public static String duration(DurationParser.DurationValue duration) {
        Matcher matcher = DURATION_PART.matcher(duration.display());
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            parts.add(switch (matcher.group(2)) {
                case "mo" -> word(amount, "месяц", "месяца", "месяцев");
                case "w" -> word(amount, "неделя", "недели", "недель");
                case "d" -> word(amount, "день", "дня", "дней");
                case "h" -> word(amount, "час", "часа", "часов");
                case "m" -> word(amount, "минута", "минуты", "минут");
                case "s" -> word(amount, "секунда", "секунды", "секунд");
                default -> throw new IllegalStateException("Unknown duration unit");
            });
        }
        return parts.isEmpty() ? duration.display() : String.join(" ", parts);
    }

    private static String human(long seconds) {
        List<String> parts = new ArrayList<>();
        long days = seconds / 86_400L; seconds %= 86_400L;
        long hours = seconds / 3_600L; seconds %= 3_600L;
        long minutes = seconds / 60L; seconds %= 60L;
        if (days > 0) parts.add(word(days, "день", "дня", "дней"));
        if (hours > 0) parts.add(word(hours, "час", "часа", "часов"));
        if (minutes > 0) parts.add(word(minutes, "минута", "минуты", "минут"));
        if (seconds > 0) parts.add(word(seconds, "секунда", "секунды", "секунд"));
        return String.join(" ", parts);
    }

    private static String word(long amount, String singular, String few, String many) {
        long modulo100 = amount % 100;
        if (modulo100 >= 11 && modulo100 <= 14) return amount + " " + many;
        return switch ((int) (amount % 10)) { case 1 -> amount + " " + singular; case 2, 3, 4 -> amount + " " + few; default -> amount + " " + many; };
    }
}
