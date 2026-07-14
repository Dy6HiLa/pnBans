package ru.privatenull.pnbans.util;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+)(mo|w|d|h|m|s)");
    private DurationParser() { }

    public static Optional<DurationValue> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String value = input.toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(value);
        long seconds = 0L;
        int end = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() != end) return Optional.empty();
                long amount = Long.parseLong(matcher.group(1));
                long multiplier = switch (matcher.group(2)) {
                    case "s" -> 1L; case "m" -> 60L; case "h" -> 3_600L;
                    case "d" -> 86_400L; case "w" -> 604_800L; case "mo" -> 2_592_000L;
                    default -> throw new IllegalStateException("Unknown duration unit");
                };
                seconds = Math.addExact(seconds, Math.multiplyExact(amount, multiplier));
                end = matcher.end();
            }
        } catch (ArithmeticException | NumberFormatException exception) { return Optional.empty(); }
        if (end != value.length() || seconds <= 0L || seconds > Long.MAX_VALUE / 1_000L) return Optional.empty();
        return Optional.of(new DurationValue(Duration.ofSeconds(seconds), value));
    }

    public record DurationValue(Duration duration, String display) { }
}
