package ru.privatenull.pnbans.dupeip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeoIpServiceTest {
    @Test
    void normalizesIpv4AndMappedIpv4() {
        assertEquals("127.0.0.1", GeoIpService.normalizeIp("127.0.0.1"));
        assertEquals("127.0.0.1", GeoIpService.normalizeIp("::ffff:127.0.0.1"));
    }

    @Test
    void rejectsHostnamesAndBlankValues() {
        assertNull(GeoIpService.normalizeIp("example.com"));
        assertNull(GeoIpService.normalizeIp(""));
    }
}
