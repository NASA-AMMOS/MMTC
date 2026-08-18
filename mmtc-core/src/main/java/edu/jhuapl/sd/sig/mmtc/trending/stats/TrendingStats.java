package edu.jhuapl.sd.sig.mmtc.trending.stats;

import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.util.ArrayList;
import java.util.List;

public class TrendingStats {
    public final Settable<String> title = new Settable<>();

    // Telemetry info
    public final Settable<OffsetDateTimeRange> trendOverTimeRangeScetUtc = new Settable<>();

    // Error metrics
    public final Settable<ErrorTrendingMetrics> scetErrorMetrics = new Settable<>();
    public final Settable<ErrorTrendingMetrics> tdtSErrorMetrics = new Settable<>();

    public List<ErrorTrendingMetrics> getTrendingMetricTypes() {
        List<ErrorTrendingMetrics> metrics = new ArrayList<>();
        if (scetErrorMetrics.isSet()) {
            metrics.add(scetErrorMetrics.get());
        }

        if (tdtSErrorMetrics.isSet()) {
            metrics.add(tdtSErrorMetrics.get());
        }

        return metrics;
    }
}
