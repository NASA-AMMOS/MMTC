package edu.jhuapl.sd.sig.mmtc.trending.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionAndAdjustmentOptions;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;

import java.time.OffsetDateTime;
import java.util.Optional;

public class TrendingConfig extends MmtcConfigWithTlmSource implements TelemetrySelectionAndAdjustmentOptions {
    private OffsetDateTimeRange trendingPeriod;

    public TrendingConfig() throws Exception {
        super();
    }

    public void setTrendingPeriod(OffsetDateTimeRange period) {
        this.trendingPeriod = period;
    }

    @Override
    public void validate() throws MmtcException {
        super.validate();
        super.validateMigrationNotNeeded();
    }

    public int getMinimumTrendingPeriodDurationDays() {
        // todo
        return 14;
    }

    @Override
    public TimeCorrelationRunConfig.TargetSampleInputErtMode getTargetSampleInputErtMode() {
        return TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE;
    }

    @Override
    public Optional<OffsetDateTimeRange> getResolvedTargetSampleRange() {
        return Optional.ofNullable(trendingPeriod);
    }

    @Override
    public Optional<OffsetDateTime> getResolvedTargetSampleExactErt() {
        return Optional.empty();
    }

    @Override
    public boolean isTestMode() {
        // todo
        return false;
    }

    @Override
    public double getTestModeOwlt() {
        // todo
        return 0;
    }
}
