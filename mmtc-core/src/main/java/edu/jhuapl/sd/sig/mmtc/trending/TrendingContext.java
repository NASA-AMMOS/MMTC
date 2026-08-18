package edu.jhuapl.sd.sig.mmtc.trending;

import edu.jhuapl.sd.sig.mmtc.trending.config.TrendingConfig;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSampleAndMetrics;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class TrendingContext {
    public final TrendingConfig config;
    public final TelemetrySource telemetrySource;
    public final OffsetDateTime appRunTime;

    public final Settable<SclkKernel> currentSclkKernel = new Settable<>();
    public final Settable<OffsetDateTimeRange> telemetryTrendingQueryPeriod = new Settable<>();
    public Settable<List<FrameSampleAndMetrics>> trendingTelemetry = new Settable<>();

    public final TrendingStatsSinceLastCorrelation statsSinceLastCorrelation = new TrendingStatsSinceLastCorrelation();
    // public final TrendingStatistics historicalStats = new TrendingStatistics();

    public TrendingContext(TrendingConfig config) {
        this.config = config;
        this.telemetrySource = config.getTelemetrySource();
        this.appRunTime = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
