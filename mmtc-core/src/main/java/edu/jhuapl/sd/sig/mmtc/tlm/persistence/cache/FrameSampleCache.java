package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.jdbi.v3.core.Handle;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manages the portion of the cache related to FrameSamples.  The only expected user of this class is {@link TelemetryCache},
 * which instantiates a single instance of a FrameSampleCache.
 */
class FrameSampleCache {
    private final TelemetrySource tlmSource;
    private final CoverageTracker queriedRangeTracker;

    public FrameSampleCache(TelemetrySource tlmSource) {
        this.tlmSource = tlmSource;
        this.queriedRangeTracker = new CoverageTracker();
    }

    public synchronized boolean init(Handle handle) {
        queriedRangeTracker.addAll(MmtcSqliteCacheDb.readAllFrameSampleCoveredQueryRanges(handle));
        return true;
    }

    public synchronized List<FrameSample> getSamplesInRange(Handle handle, OffsetDateTime startErt, OffsetDateTime stopErt) throws MmtcException {
        // if part of the range hasn't been queried and stored in the cache previously, do so now
        final List<OffsetDateTimeRange> missingRanges = queriedRangeTracker.getUncoveredRangesWithin(new OffsetDateTimeRange(startErt, stopErt));

        for (OffsetDateTimeRange missingRange : missingRanges) {
            final List<FrameSample> samples = tlmSource.getSamplesInRange(missingRange.getStart(), missingRange.getStop());
            MmtcSqliteCacheDb.writeFrameSamples(handle, samples);
            queriedRangeTracker.add(missingRange);
        }

        MmtcSqliteCacheDb.writeAllFrameSampleCoveredQueryRanges(handle, queriedRangeTracker.getAllCoveredRanges());

        // retrieve and return telemetry, covering the entire original query period, from the cache
        return MmtcSqliteCacheDb.readFrameSamples(handle, new OffsetDateTimeRange(startErt, stopErt));
    }

    public synchronized Map<String, String> getCacheStatistics(Handle handle) {
        final Map<String, String> cacheStats = new TreeMap<>();

        cacheStats.put(
                "Number of cached FrameSamples",
                Long.toString(MmtcSqliteCacheDb.readNumFrameSamples(handle))
        );

        final StringBuilder coveredRangesOutput = new StringBuilder("\n");
        queriedRangeTracker.getAllCoveredRanges().forEach(r -> {
            coveredRangesOutput.append(String.format("\t- [%s, %s)\n", r.getStart(), r.getStop()));
        });

        cacheStats.put(
                "Queried time ranges (ERT) contained in cache",
                coveredRangesOutput.toString()
        );

        return cacheStats;
    }
}
