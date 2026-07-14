package ru.privatenull.pnbans.model;

import java.util.UUID;

/** One persisted UUID/IP observation. Unlike AccountProfile, old addresses are never overwritten. */
public record AccountIpRecord(UUID uuid, String name, String ip, String world,
                              double x, double y, double z,
                              long firstSeen, long lastSeen, long joins) {

    public String locationText() {
        if (world == null || world.isBlank()) return "Не определена";
        return world + " " + block(x) + " " + block(y) + " " + block(z);
    }

    private int block(double value) {
        return (int) Math.floor(value);
    }
}
