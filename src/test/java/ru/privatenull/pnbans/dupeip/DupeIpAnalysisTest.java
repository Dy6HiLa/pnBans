package ru.privatenull.pnbans.dupeip;

import org.junit.jupiter.api.Test;
import ru.privatenull.pnbans.model.AccountProfile;
import ru.privatenull.pnbans.storage.PunishmentService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DupeIpAnalysisTest {

    @Test
    void sameIpCanAlertEvenBelowRiskThreshold() {
        AccountProfile related = profile("Related", "127.0.0.1");
        DupeIpService.Match match = new DupeIpService.Match(
                related, "Локальная сеть", true, false, 0.10D,
                List.of("127.0.0.1"), 1, System.currentTimeMillis(),
                new PunishmentService.PunishmentSummary(0, 0, 0, 0, 0),
                30, "Средний", List.of("Совпадает IP-адрес"), false);
        DupeIpService.Analysis analysis = new DupeIpService.Analysis(
                DupeIpService.Status.READY, UUID.randomUUID(), "Current", profile("Current", "127.0.0.1"),
                "127.0.0.1", "Локальная сеть", 1, List.of(match), 50, true, true);

        assertTrue(analysis.shouldAlert());
        assertEquals(1, analysis.sameIpCount());
        assertEquals(0, analysis.similarNameCount());
    }

    @Test
    void missingProfileNeverAlerts() {
        DupeIpService.Analysis analysis = new DupeIpService.Analysis(
                DupeIpService.Status.NO_PROFILE, null, "Unknown", null,
                "unknown", "Не определена", 0, List.of(), 0, true, true);

        assertFalse(analysis.shouldAlert());
    }

    private AccountProfile profile(String name, String ip) {
        long now = System.currentTimeMillis();
        return new AccountProfile(UUID.randomUUID(), name, ip, "world", 0D, 64D, 0D, now, now);
    }
}
