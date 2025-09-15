package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.CoverageTracker;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.model.OffsetDateTimeRangeEntity;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class CoverageTrackerTest {
    @Test
    public void testGetUncovRanges() {
        // multiple partial areas of coverage
        {
            CoverageTracker covTracker = new CoverageTracker();
            covTracker.addAll(Arrays.asList(rangeOf(1, 3), rangeOf(4, 6), rangeOf(7, 9)));
            assertEquals(
                    Arrays.asList(rangeOf(3, 4), rangeOf(6, 7)),
                    covTracker.getUncoveredRangesWithin(rangeOf(2, 8))
            );
        }

        // multiple partial areas of coverage
        {
            CoverageTracker covTracker = new CoverageTracker();
            covTracker.addAll(Arrays.asList(rangeOf(4, 6)));
            assertEquals(
                    Arrays.asList(rangeOf(2, 4), rangeOf(6, 8)),
                    covTracker.getUncoveredRangesWithin(rangeOf(2, 8))
            );
        }

        // a realistic 'appending' pattern, starting from empty
        {
            CoverageTracker covTracker = new CoverageTracker();

            // someone wants to run MMTC from launch (at '1') through time '3'; it should query that whole time without any coverage
            assertEquals(
                    Arrays.asList(rangeOf(1, 3)),
                    covTracker.getUncoveredRangesWithin(rangeOf(1, 3))
            );

            // MMTC has been run through time '3'
            covTracker.addAll(Arrays.asList(rangeOf(1, 3)));

            // someone wants to update MMTC from launch through time '4'; it should only query time 3-4
            assertEquals(
                    Arrays.asList(rangeOf(3, 4)),
                    covTracker.getUncoveredRangesWithin(rangeOf(1, 4))
            );

            // MMTC queries from 3-4
            covTracker.add(rangeOf(3,4));

            // someone wants to update MMTC from launch through time '5'; it should only query time 4-5
            assertEquals(
                    Arrays.asList(rangeOf(4, 5)),
                    covTracker.getUncoveredRangesWithin(rangeOf(1, 5))
            );
        }

        // no coverage
        {
            CoverageTracker covTracker = new CoverageTracker();
            covTracker.addAll(Arrays.asList(rangeOf(1, 3), rangeOf(4, 6), rangeOf(7, 9)));

            assertEquals(
                Arrays.asList(rangeOf(15, 16)),
                covTracker.getUncoveredRangesWithin(rangeOf(15, 16))
            );
        }
    }

     public static OffsetDateTimeRange rangeOf(String start, String stop) {
             return new OffsetDateTimeRange(
                 TimeConvert.parseIsoDoyUtcStr(start),
                 TimeConvert.parseIsoDoyUtcStr(stop)
             );
     }

     public static OffsetDateTimeRange rangeOf(int startDoy, int endDoy) {
         return rangeOf(
            String.format("2025-%03dT00:00:00", startDoy),
            String.format("2025-%03dT00:00:00", endDoy)
         );
     }
}