package edu.jhuapl.sd.sig.mmtc.trending.stats;

import edu.jhuapl.sd.sig.mmtc.tlm.TlmUtils;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public class ErrorTrendingMetrics {
    public final Settable<String> title = new Settable<>();
    public final Settable<String> trendingPeriodId = new Settable();

    public final Settable<Boolean> sufficientDataForTrendingExists = new Settable<>();
    public final Settable<OffsetDateTimeRange> retrievedTelemetryTimeRange = new Settable<>();

    public final Settable<List<TlmUtils.TlmPoint>> tlmForPlotting = new Settable<>();

    public final Settable<OffsetDateTime> priorScetUtcForErrorCalc = new Settable<>();
    public final Settable<BigDecimal> priorErrorMs = new Settable<>();

    public final Settable<OffsetDateTime> latestScetUtcForErrorCalc = new Settable<>();
    public final Settable<BigDecimal> latestErrorMs = new Settable<>();

    public final Settable<BigDecimal> errorThresholdMs = new Settable<>();
    public final Settable<BigDecimal> valChangeRateMsPerDay = new Settable<>();
    public final Settable<Boolean> thresholdAlreadyViolated = new Settable<>();
    public final Settable<Optional<OffsetDateTime>> estimatedScetWhenErrorThresholdWillBeReached = new Settable<>();
    public final Settable<Optional<BigDecimal>> numDaysUntilErrorThresholdWillBeReached = new Settable<>();
}
