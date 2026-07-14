package ru.privatenull.pnbans.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {

    @Test
    void roundsRemainingTimeUpInsteadOfDroppingTheFirstSecond() {
        long now = 1_000_000L;

        assertEquals("10 секунд", TimeFormat.remaining(now + 10_000L, now));
        assertEquals("10 секунд", TimeFormat.remaining(now + 9_999L, now));
        assertEquals("1 секунда", TimeFormat.remaining(now + 1L, now));
        assertEquals("истёк", TimeFormat.remaining(now, now));
    }
}
