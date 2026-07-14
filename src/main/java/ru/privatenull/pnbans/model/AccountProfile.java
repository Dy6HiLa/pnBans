package ru.privatenull.pnbans.model;

import java.util.Locale;
import java.util.UUID;

public record AccountProfile(UUID uuid, String name, String ip, String world,
                             double x, double y, double z, long firstSeen, long lastSeen) {

    public String nameKey() {
        return name.toLowerCase(Locale.ROOT);
    }

    public String locationText() {
        if (world == null || world.isBlank()) {
            return "Не определена";
        }
        return world + " " + block(x) + " " + block(y) + " " + block(z);
    }

    private int block(double value) {
        return (int) Math.floor(value);
    }
}
