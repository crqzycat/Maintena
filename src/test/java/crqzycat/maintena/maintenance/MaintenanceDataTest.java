package crqzycat.maintena.maintenance;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceDataTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void whitelistChecksTheUuidNotTheName() {
        MaintenanceData data = new MaintenanceData();
        data.addWhitelistedPlayer("Steve", STEVE);

        assertTrue(data.isWhitelisted(STEVE, "Steve"));
        // anderer Account mit demselben Namen kommt nicht durch
        assertFalse(data.isWhitelisted(ALEX, "Steve"));
        // gleicher Account nach einem Namenswechsel schon
        assertTrue(data.isWhitelisted(STEVE, "SteveNew"));
    }

    @Test
    void legacyEntryWithoutUuidMatchesByNameIgnoringCase() {
        MaintenanceData data = new MaintenanceData();
        data.whitelist.add(new MaintenanceData.WhitelistEntry("Steve", null));

        assertTrue(data.hasLegacyEntries());
        assertTrue(data.isWhitelisted(STEVE, "steve"));
        assertFalse(data.isWhitelisted(STEVE, "Alex"));
    }

    @Test
    void refreshBindsLegacyEntryToTheUuid() {
        MaintenanceData data = new MaintenanceData();
        data.whitelist.add(new MaintenanceData.WhitelistEntry("Steve", null));

        assertTrue(data.refreshEntry(STEVE, "Steve"));

        assertFalse(data.hasLegacyEntries());
        // jetzt gilt nur noch die UUID
        assertFalse(data.isWhitelisted(ALEX, "Steve"));
        assertTrue(data.isWhitelisted(STEVE, "Steve"));
    }

    @Test
    void refreshUpdatesAChangedName() {
        MaintenanceData data = new MaintenanceData();
        data.addWhitelistedPlayer("Steve", STEVE);

        assertTrue(data.refreshEntry(STEVE, "SteveNew"));
        assertEquals("SteveNew", data.getWhitelistNames().get(0));
        // nichts mehr zu ändern
        assertFalse(data.refreshEntry(STEVE, "SteveNew"));
    }

    @Test
    void bindLegacyEntriesUsesKnownIdsIgnoringCase() {
        MaintenanceData data = new MaintenanceData();
        data.whitelist.add(new MaintenanceData.WhitelistEntry("Steve", null));
        data.whitelist.add(new MaintenanceData.WhitelistEntry("Unknown", null));

        Map<String, UUID> known = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        known.put("STEVE", STEVE);

        assertTrue(data.bindLegacyEntries(known));

        // Steve ist gebunden, Unknown bleibt ohne UUID
        assertTrue(data.hasLegacyEntries());
        assertTrue(data.isWhitelisted(STEVE, "Steve"));
        assertFalse(data.isWhitelisted(ALEX, "Steve"));
    }

    @Test
    void addingAgainDoesNotCreateDuplicates() {
        MaintenanceData data = new MaintenanceData();
        data.whitelist.add(new MaintenanceData.WhitelistEntry("Steve", null));

        data.addWhitelistedPlayer("Steve", STEVE);

        assertEquals(1, data.whitelist.size());
        assertFalse(data.hasLegacyEntries());

        data.addWhitelistedPlayer("Steve", STEVE);
        assertEquals(1, data.whitelist.size());
    }

    @Test
    void sameNameWithDifferentUuidsAreTwoEntries() {
        MaintenanceData data = new MaintenanceData();
        data.addWhitelistedPlayer("Steve", STEVE);
        data.addWhitelistedPlayer("Steve", ALEX);

        assertEquals(2, data.whitelist.size());
    }

    @Test
    void removeWorksByNameIgnoringCase() {
        MaintenanceData data = new MaintenanceData();
        data.addWhitelistedPlayer("Steve", STEVE);
        data.addWhitelistedPlayer("Alex", ALEX);

        data.removeWhitelistedPlayer("steve");

        assertEquals(1, data.whitelist.size());
        assertFalse(data.isWhitelisted(STEVE, "Steve"));
        assertTrue(data.isWhitelisted(ALEX, "Alex"));
    }

    @Test
    void endTimeHandling() {
        MaintenanceData data = new MaintenanceData();

        assertEquals(-1L, data.getTimeRemaining());
        assertFalse(data.isTimeExpired());

        data.setEndTime(System.currentTimeMillis() - 1000);
        assertTrue(data.isTimeExpired());

        data.setEndTime(System.currentTimeMillis() + 60_000);
        assertFalse(data.isTimeExpired());
        assertTrue(data.getTimeRemaining() > 0);

        data.clearEndTime();
        assertEquals(-1L, data.getTimeRemaining());
    }
}
