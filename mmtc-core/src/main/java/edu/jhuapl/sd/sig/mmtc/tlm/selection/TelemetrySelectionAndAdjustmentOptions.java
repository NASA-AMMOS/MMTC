package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.app.TimekeepingAdjustmentParameters;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * This is for selecting telemetry over a range or an exact ERT
 */
public interface TelemetrySelectionAndAdjustmentOptions extends TimekeepingAdjustmentParameters {
    // how to iterate over the range for a valid sample set
    enum TargetSampleRangeErtSeekOrder {
        ASCENDING, // earliest ERTs to latest
        DESCENDING // latest ERTs to earliest (default)
    }

    TimeCorrelationRunConfig.TargetSampleInputErtMode getTargetSampleInputErtMode();

    Optional<OffsetDateTimeRange> getResolvedTargetSampleErtRange();

    TargetSampleRangeErtSeekOrder getTargetSampleRangeErtSeekOrder();

    Optional<OffsetDateTime> getResolvedTargetSampleExactErt();

    Optional<Double> getMinTdtGExclusive();
}
