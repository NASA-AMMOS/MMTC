package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class SclkPartitionMapTests {
    private TimeCorrelationCliAppConfig config;
    private SclkPartitionMap partitionMap;

    @BeforeEach
    void loadMap() throws Exception {
        // Load the config to get the path to the partition map
        TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(new String[]{"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"});

        // Create a new partition map object (ignore the one from TimeCorrelationAppConfig)
        partitionMap = new SclkPartitionMap(config.getSclkPartitionMapPath());

        if (!partitionMap.load()) {
            fail("Unable to load SCLK Partition Map");
        }
    }

    @Test
    void testGetPartition1() {
        int partitionNum = 1;
        try {
            OffsetDateTime date = partitionMap.getPartitionDate(partitionNum);
            assertEquals("2010-01-01T00:00Z", date.toString());
        }
        catch (MmtcException ex) {
            fail("Invalid partition number " + partitionNum);
        }
    }
}
