package ru.privatenull.pnbans.database;

import ru.privatenull.pnbans.model.Punishment;
import ru.privatenull.pnbans.model.AccountProfile;
import ru.privatenull.pnbans.model.AccountIpRecord;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DatabaseBackend extends AutoCloseable {

    void open() throws Exception;

    List<Punishment> loadActive() throws Exception;

    void insert(Punishment punishment) throws Exception;

    void revoke(Collection<String> ids, String actor, long revokedAt) throws Exception;

    List<Punishment> history(String targetUuid, String targetName, int offset, int limit) throws Exception;

    List<Punishment> historyByActor(String actor, int offset, int limit) throws Exception;

    List<Punishment> historyByIps(Collection<String> ips, int limit) throws Exception;

    void upsertAccountProfile(AccountProfile profile) throws Exception;

    AccountProfile accountProfile(UUID uuid, String name) throws Exception;

    List<AccountProfile> accountProfilesByIp(String ip, int limit) throws Exception;

    List<AccountProfile> recentAccountProfiles(int limit) throws Exception;

    List<AccountIpRecord> accountIpHistory(UUID uuid, String name, int limit) throws Exception;

    List<AccountIpRecord> accountIpHistoryByIp(String ip, int limit) throws Exception;

    @Override
    void close();
}
