package ru.privatenull.pnbans.api;

import ru.privatenull.pnbans.model.PunishmentType;
import ru.privatenull.pnbans.storage.PunishmentService;

import java.util.UUID;

public final class PnBansMuteApiImpl implements PnBansMuteApi {
    private final PunishmentService punishments;
    public PnBansMuteApiImpl(PunishmentService punishments) { this.punishments = punishments; }
    @Override public boolean isMuted(UUID playerId, String playerName) { return punishments.getPlayerPunishment(playerId, playerName, PunishmentType.MUTE) != null; }
}
