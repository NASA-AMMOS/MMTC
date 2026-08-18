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

    TimeCorrelationRunConfig.TargetSampleInputErtMode getTargetSampleInputErtMode();

    // int getSamplesPerSet();

    Optional<OffsetDateTimeRange> getResolvedTargetSampleRange();

    Optional<OffsetDateTime> getResolvedTargetSampleExactErt();

    // int getSamplingSampleSetBuildingStrategyQueryWidthMinutes();

    // int getSamplingSampleSetBuildingStrategySamplingRateMinutes();

    // int getTargetSampleExactErtSupplementalQueryWindowMin();

    // int getSupplementalSampleOffset();
}
