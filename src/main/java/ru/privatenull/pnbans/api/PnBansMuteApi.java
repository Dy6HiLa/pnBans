package ru.privatenull.pnbans.api;

import java.util.UUID;

/** Stable optional API for chat plugins. Implementations must be safe from async chat threads. */
public interface PnBansMuteApi {
    boolean isMuted(UUID playerId, String playerName);
}
