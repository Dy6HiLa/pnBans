package ru.privatenull.pnbans.model;

import java.util.UUID;

public record Punishment(String id, UUID targetUuid, String targetName, String ip, PunishmentType type,
                         String reason, String actor, long createdAt, long expiresAt, boolean active,
                         String revokedBy, long revokedAt) {

    public boolean isActive(long now) {
        return active && (expiresAt == 0L || expiresAt > now);
    }
}
