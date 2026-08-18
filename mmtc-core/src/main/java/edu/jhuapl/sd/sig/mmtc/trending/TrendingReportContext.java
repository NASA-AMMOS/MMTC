package edu.jhuapl.sd.sig.mmtc.trending;

import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.trending.config.TrendingConfig;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.trending.stats.TrendingStats;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrendingReportContext {
    public final TrendingConfig config;
    public final TelemetrySource telemetrySource;
    public final MmtcConfig.TrendingReportPeriodsConfig reportConfig;
    public final OffsetDateTime appRunTime;

    // SCLK kernel info (that was used to calculate the rest of the values)
    public final Settable<SclkKernel> currentSclkKernel = new Settable<>();
    public final Settable<String> currentSclkKernelDescription = new Settable<>();
    public final Settable<CorrelationTriplet> currentSclkKernelLatestTriplet = new Settable<>();
    public final Settable<Double> currentSclkKernelLatestTripletAgeDays = new Settable<>();

    public final Settable<TrendingStats> statsPastNDays = new Settable<>();
    public final Settable<TrendingStats> statsSinceLastCorrelation = new Settable<>();

    public final Map<MmtcConfig.ReportProductFormat, Path> reportOutputPathsByFormat = new HashMap<>();

    protected TrendingReportContext(TrendingConfig config, MmtcConfig.TrendingReportPeriodsConfig reportConfig, OffsetDateTime appRunTime) {
        this.config = config;
        this.telemetrySource = config.getTelemetrySource();
        this.reportConfig = reportConfig;
        this.appRunTime = appRunTime;
    }

    public List<TrendingStats> getTrendingPeriods() {
        List<TrendingStats> stats = new ArrayList<>();

        if (statsPastNDays.isSet()) {
            stats.add(statsPastNDays.get());
        }

        if (statsSinceLastCorrelation.isSet()) {
            stats.add(statsSinceLastCorrelation.get());
        }

        return stats;
    }
}
