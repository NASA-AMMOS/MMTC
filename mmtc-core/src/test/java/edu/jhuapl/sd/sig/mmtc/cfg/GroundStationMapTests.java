package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class GroundStationMapTests {
    private GroundStationMap stationMap;

    @BeforeEach
    void loadTable() throws Exception {
        // Load the config to get the path to the partition map
        TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(new String[]{"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"});

        // Create a new ground stations map (ignore the one from TimeCorrelationConfig)
        stationMap = new GroundStationMap(config.getGroundStationMapPath());

        if (!stationMap.load()) {
            fail("Unable to load Ground Station Map");
        }
    }

    @Test
    void testGetStationIdFromPathId1() {
        try {
            assertEquals("gs", stationMap.getStationId(0));
        }
        catch (MmtcException ex) {
            fail("Failed to get Station ID from Path ID", ex);
        }
    }

    @Test
    void testGetStationNameFromPathId1() {
        try {
            assertEquals("GroundStation", stationMap.getStationName(0));
        }
        catch (MmtcException ex) {
            fail("Failed to get Station Name from Path ID", ex);
        }
    }

    @Test
    void testGetStationIdFromStationName1() {
        try {
            assertEquals("gs", stationMap.getStationId("GroundStation"));
        }
        catch (MmtcException ex) {
            fail("Failed to get Station ID from Station Name", ex);
        }
    }

    @Test
    void testGetStationNameFromStationId1() {
        try {
            assertEquals("GroundStation", stationMap.getStationName("gs"));
        }
        catch (MmtcException ex) {
            fail("Failed to get Station Name from Station ID", ex);
        }
    }
}
