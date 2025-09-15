package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class to track 'covered' ranges, e.g. for time ranges of queries that have been completed on a telemetry source.
 * The underlying {@link RangeSet} implementation maintains a minimal representation of all covered ranges and aids
 * in calculating uncovered ranges as well.
 */
class CoverageTracker {
    private final RangeSet<OffsetDateTime> coveredRanges = TreeRangeSet.create();

    public CoverageTracker() { }

    public CoverageTracker(List<OffsetDateTimeRange> offsetDateTimeRanges) {
        addAll(offsetDateTimeRanges);
    }

    public synchronized void add(OffsetDateTimeRange offsetDateTimeRange) {
        coveredRanges.add(offsetDateTimeRange.toRange());
    }

    public synchronized void addAll(Collection<OffsetDateTimeRange> offsetDateTimeRanges) {
        offsetDateTimeRanges.forEach(r ->
                coveredRanges.add(r.toRange())
        );
    }

    public synchronized List<OffsetDateTimeRange> getUncoveredRangesWithin(OffsetDateTimeRange queryRange) {
        return coveredRanges
                .complement()
                .subRangeSet(queryRange.toRange())
                .asRanges()
                .stream()
                .map(OffsetDateTimeRange::fromRange)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<OffsetDateTimeRange> getAllCoveredRanges() {
        return coveredRanges.asRanges()
                .stream()
                .map(OffsetDateTimeRange::fromRange)
                .sorted()
                .collect(Collectors.toList());
    }
}
