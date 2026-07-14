package ru.privatenull.pnbans.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurationParserTest {
    @Test void acceptsCombinedDuration() { var parsed = DurationParser.parse("1d2h30m"); assertTrue(parsed.isPresent()); assertEquals(95_400L, parsed.get().duration().toSeconds()); }
    @Test void acceptsLongDuration() { var parsed = DurationParser.parse("100d"); assertTrue(parsed.isPresent()); assertEquals(8_640_000L, parsed.get().duration().toSeconds()); }
    @Test void rejectsTextInsteadOfDuration() { assertTrue(DurationParser.parse("forever").isEmpty()); assertTrue(DurationParser.parse("week").isEmpty()); }
    @Test void rejectsInvalidDuration() { assertTrue(DurationParser.parse("0d").isEmpty()); assertTrue(DurationParser.parse("1x").isEmpty()); }
}
