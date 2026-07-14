package ru.privatenull.pnbans.storage;

import ru.privatenull.pnbans.database.DatabaseBackend;
import ru.privatenull.pnbans.model.AccountIpRecord;
import ru.privatenull.pnbans.model.AccountProfile;
import ru.privatenull.pnbans.model.Punishment;
import ru.privatenull.pnbans.model.PunishmentType;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class PunishmentService {

    private final DatabaseBackend database;
    private final Map<String, Punishment> activePunishments = new ConcurrentHashMap<>();

    public PunishmentService(DatabaseBackend database) throws Exception {
        this.database = database;
        for (Punishment punishment : database.loadActive()) {
            activePunishments.put(punishment.id(), punishment);
        }
    }

    public synchronized void create(Punishment punishment) throws Exception {
        database.insert(punishment);
        activePunishments.put(punishment.id(), punishment);
    }

    public synchronized boolean revokePlayer(UUID uuid, String name, PunishmentType type, String actor) throws Exception {
        return revoke(punishment -> punishment.type() == type && targets(punishment, uuid, name), actor);
    }

    public synchronized boolean revokeIp(String ip, String actor) throws Exception {
        return revoke(punishment -> punishment.type() == PunishmentType.IP_BAN && ip.equals(punishment.ip()), actor);
    }

    public Punishment getPlayerPunishment(UUID uuid, String name, PunishmentType type) {
        return find(punishment -> punishment.type() == type && targets(punishment, uuid, name));
    }

    public Punishment getIpPunishment(String ip) {
        return find(punishment -> punishment.type() == PunishmentType.IP_BAN && ip.equals(punishment.ip()));
    }

    public int activeWarnings(UUID uuid, String name) {
        int count = 0;
        long now = System.currentTimeMillis();
        for (Punishment punishment : activePunishments.values()) {
            if (punishment.type() == PunishmentType.WARN && targets(punishment, uuid, name) && punishment.isActive(now)) count++;
        }
        return count;
    }

    public List<Punishment> history(UUID uuid, String name, int offset, int limit) throws Exception {
        return database.history(uuid == null ? "" : uuid.toString(), name, offset, limit);
    }

    public List<Punishment> historyByActor(String actor, int offset, int limit) throws Exception {
        return database.historyByActor(actor, offset, limit);
    }

    public List<Punishment> historyWithIps(UUID uuid, String name, Collection<String> ips, int limit) throws Exception {
        Map<String, Punishment> unique = new LinkedHashMap<>();
        for (Punishment punishment : history(uuid, name, 0, limit)) unique.put(punishment.id(), punishment);
        if (ips != null && !ips.isEmpty()) {
            for (Punishment punishment : database.historyByIps(ips, limit)) unique.put(punishment.id(), punishment);
        }
        return unique.values().stream()
                .sorted(Comparator.comparingLong(Punishment::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    public void recordLogin(Player player, String ip) throws Exception {
        long now = System.currentTimeMillis();
        Location location = player.getLocation();
        AccountProfile profile = new AccountProfile(player.getUniqueId(), player.getName(), ip,
                location.getWorld() == null ? null : location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), now, now);
        upsertAccountProfile(profile);
    }

    public void upsertAccountProfile(AccountProfile profile) throws Exception {
        database.upsertAccountProfile(profile);
    }

    public AccountProfile accountProfile(UUID uuid, String name) throws Exception {
        return database.accountProfile(uuid, name);
    }

    public List<AccountProfile> accountProfilesByIp(String ip, int limit) throws Exception {
        return database.accountProfilesByIp(ip, limit);
    }

    public List<AccountProfile> recentAccountProfiles(int limit) throws Exception {
        return database.recentAccountProfiles(limit);
    }

    public List<AccountIpRecord> accountIpHistory(UUID uuid, String name, int limit) throws Exception {
        return database.accountIpHistory(uuid, name, limit);
    }

    public List<AccountIpRecord> accountIpHistoryByIp(String ip, int limit) throws Exception {
        return database.accountIpHistoryByIp(ip, limit);
    }

    public PunishmentSummary summary(UUID uuid, String name) throws Exception {
        return summary(uuid, name, List.of());
    }

    public PunishmentSummary summary(UUID uuid, String name, Collection<String> ips) throws Exception {
        return summarize(historyWithIps(uuid, name, ips, 200));
    }

    public PunishmentSummary summarize(Collection<Punishment> punishments) {
        int bans = 0;
        int mutes = 0;
        int warns = 0;
        int activeBans = 0;
        int activeMutes = 0;
        long now = System.currentTimeMillis();
        for (Punishment punishment : punishments) {
            switch (punishment.type()) {
                case BAN, IP_BAN -> {
                    bans++;
                    if (punishment.isActive(now)) activeBans++;
                }
                case MUTE -> {
                    mutes++;
                    if (punishment.isActive(now)) activeMutes++;
                }
                case WARN -> warns++;
            }
        }
        return new PunishmentSummary(bans, mutes, warns, activeBans, activeMutes);
    }

    private boolean revoke(Predicate<Punishment> filter, String actor) throws Exception {
        List<String> ids = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Punishment punishment : activePunishments.values()) {
            if (filter.test(punishment) && punishment.isActive(now)) ids.add(punishment.id());
        }
        if (ids.isEmpty()) return false;
        database.revoke(ids, actor, now);
        ids.forEach(activePunishments::remove);
        return true;
    }

    private Punishment find(Predicate<Punishment> filter) {
        long now = System.currentTimeMillis();
        for (Punishment punishment : activePunishments.values()) {
            if (!punishment.isActive(now)) {
                activePunishments.remove(punishment.id(), punishment);
                continue;
            }
            if (filter.test(punishment)) return punishment;
        }
        return null;
    }

    private boolean targets(Punishment punishment, UUID uuid, String name) {
        if (uuid != null && uuid.equals(punishment.targetUuid())) return true;
        return punishment.targetName().equalsIgnoreCase(name);
    }

    public record PunishmentSummary(int bans, int mutes, int warns, int activeBans, int activeMutes) {
    }
}
