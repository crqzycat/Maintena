package crqzycat.maintena.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationUtilTest {

    @Test
    void parsesSingleUnits() {
        assertEquals(Long.valueOf(45_000L), DurationUtil.parse("45s"));
        assertEquals(Long.valueOf(30 * 60_000L), DurationUtil.parse("30m"));
        assertEquals(Long.valueOf(24 * 3_600_000L), DurationUtil.parse("24h"));
        assertEquals(Long.valueOf(7 * 86_400_000L), DurationUtil.parse("7d"));
    }

    @Test
    void parsesCombinations() {
        assertEquals(Long.valueOf(129_600_000L), DurationUtil.parse("1d12h"));
        assertEquals(Long.valueOf(5_400_000L), DurationUtil.parse("1h30m"));
        assertEquals(Long.valueOf(90_061_000L), DurationUtil.parse("1d1h1m1s"));
    }

    @Test
    void ignoresCase() {
        assertEquals(Long.valueOf(86_400_000L), DurationUtil.parse("1D"));
        assertEquals(Long.valueOf(5_400_000L), DurationUtil.parse("1H30M"));
    }

    @Test
    void rejectsInvalidInput() {
        assertNull(DurationUtil.parse(""));
        assertNull(DurationUtil.parse("abc"));
        assertNull(DurationUtil.parse("10"));
        assertNull(DurationUtil.parse("7x"));
        assertNull(DurationUtil.parse("-5m"));
        assertNull(DurationUtil.parse("1 d"));
    }

    @Test
    void rejectsWrongOrder() {
        // Reihenfolge muss d -> h -> m -> s sein
        assertNull(DurationUtil.parse("30m1h"));
    }

    @Test
    void rejectsZero() {
        assertNull(DurationUtil.parse("0s"));
        assertNull(DurationUtil.parse("0d0h"));
    }

    @Test
    void rejectsOverflow() {
        assertNull(DurationUtil.parse("99999999999999d"));
        assertNull(DurationUtil.parse("99999999999999999999s"));
    }

    @Test
    void looksLikeDurationAcceptsDurationShapedText() {
        assertTrue(DurationUtil.looksLikeDuration("7d"));
        assertTrue(DurationUtil.looksLikeDuration("1d12h"));
        // sieht aus wie eine Dauer, ist aber ungültig
        assertTrue(DurationUtil.looksLikeDuration("30m1h"));
    }

    @Test
    void looksLikeDurationRejectsOtherText() {
        assertFalse(DurationUtil.looksLikeDuration("spam"));
        assertFalse(DurationUtil.looksLikeDuration("7"));
        assertFalse(DurationUtil.looksLikeDuration("7x"));
        assertFalse(DurationUtil.looksLikeDuration(""));
    }
}
