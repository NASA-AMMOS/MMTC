package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig;

public abstract class TelemetrySelectionOptionsWithoutInputErts implements TelemetrySelectionAndAdjustmentOptions {
    private final MmtcConfig config;

    public TelemetrySelectionOptionsWithoutInputErts(MmtcConfig config) {
        this.config = config;
    }

    public int getSamplesPerSet() {
        return config.getSamplesPerSet();
    }

    public int getSamplingSampleSetBuildingStrategyQueryWidthMinutes(){
        return config.getSamplingSampleSetBuildingStrategyQueryWidthMinutes();
    }

    public int getSamplingSampleSetBuildingStrategySamplingRateMinutes(){
        return config.getSamplingSampleSetBuildingStrategySamplingRateMinutes();
    }

    public int getTargetSampleExactErtSupplementalQueryWindowMin(){
        return config.getTargetSampleExactErtSupplementalQueryWindowMin();
    }

    public int getSupplementalSampleOffset(){
        return config.getSupplementalSampleOffset();
    }
}
