package ru.privatenull.pnbans.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUtilTest {

    @Test
    void removesCoordinateSegmentsFromLegacyDupeIpMessages() {
        List<String> filtered = DupeIpMessageFilter.withoutCoordinates(List.of(
                "&7Локация: &f{location} &f| &7Известно IP: &f{known_ips}",
                "&7Локация связи: &f{related_location}",
                "&#A0EFA1▸ &fИгрок: &7{country} &f| &f{location}",
                "&7Риск: &f{risk}/100"
        ));

        assertEquals(List.of(
                " &7Известно IP: &f{known_ips}",
                "&#A0EFA1▸ &fИгрок: &7{country} &f",
                "&7Риск: &f{risk}/100"
        ), filtered);
    }
}
