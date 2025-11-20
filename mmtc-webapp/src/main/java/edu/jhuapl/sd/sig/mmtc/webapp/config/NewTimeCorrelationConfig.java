package edu.jhuapl.sd.sig.mmtc.webapp.config;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfigInputSupplier;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public class NewTimeCorrelationConfig implements TimeCorrelationRunConfigInputSupplier {
    public OffsetDateTime targetSampleRangeStartErt;
    public OffsetDateTime targetSampleRangeStopErt;
    public OffsetDateTime targetSampleExactErt;
    public OffsetDateTime priorCorrelationExactTdt;
    public boolean testModeOwltEnabled;
    public double testModeOwltSec;
    public double clockChangeRateAssignedValue;

    public TimeCorrelationRunConfig.DryRunConfig dryRunConfig;

    public TimeCorrelationRunConfig.ClockChangeRateMode clockChangeRateModeOverride;
    public TimeCorrelationRunConfig.AdditionalSmoothingRecordConfig additionalSmoothingRecordConfigOverride;
    public boolean isDisableContactFilter;
    public boolean isCreateUplinkCmdFile;

    public NewTimeCorrelationConfig() {

    }

    public NewTimeCorrelationConfig(NewTimeCorrelationConfig other) {
        this.targetSampleRangeStartErt = other.targetSampleRangeStartErt;
        this.targetSampleRangeStopErt = other.targetSampleRangeStopErt;
        this.targetSampleExactErt = other.targetSampleExactErt;
        this.priorCorrelationExactTdt = other.priorCorrelationExactTdt;
        this.testModeOwltEnabled = other.testModeOwltEnabled;
        this.testModeOwltSec = other.testModeOwltSec;
        this.clockChangeRateAssignedValue = other.clockChangeRateAssignedValue;

        this.dryRunConfig = other.dryRunConfig;

        this.clockChangeRateModeOverride = other.clockChangeRateModeOverride;
        this.additionalSmoothingRecordConfigOverride = other.additionalSmoothingRecordConfigOverride;
        this.isDisableContactFilter = other.isDisableContactFilter;
        this.isCreateUplinkCmdFile = other.isCreateUplinkCmdFile;
    }


    @Override
    public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(Map<String, TelemetrySource.AdditionalOption> additionalTlmSrcOptionsByName) throws Exception {
        return new TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs(
                Optional.ofNullable(targetSampleRangeStartErt),
                Optional.ofNullable(targetSampleRangeStopErt),
                Optional.ofNullable(targetSampleExactErt),
                Optional.ofNullable(priorCorrelationExactTdt),
                testModeOwltEnabled,
                Optional.of(testModeOwltSec),
                Optional.of(clockChangeRateAssignedValue),
                Optional.empty(),
                Optional.of(clockChangeRateModeOverride),
                Optional.of(additionalSmoothingRecordConfigOverride),
                isDisableContactFilter,
                isCreateUplinkCmdFile,
                dryRunConfig
        );
    }

    public void settargetSampleRangeStartErt(OffsetDateTime targetSampleRangeStartErt) {
        this.targetSampleRangeStartErt = targetSampleRangeStartErt;
    }

    public void settargetSampleRangeStopErt(OffsetDateTime targetSampleRangeStopErt) {
        this.targetSampleRangeStopErt = targetSampleRangeStopErt;
    }

    public void setTargetSampleExactErt(OffsetDateTime targetSampleExactErt) {
        this.targetSampleExactErt = targetSampleExactErt;
    }

    public void setPriorCorrelationExactTdt(OffsetDateTime priorCorrelationExactTdt) {
        this.priorCorrelationExactTdt = priorCorrelationExactTdt;
    }

    public void setTestModeOwltEnabled(boolean testModeOwltEnabled) {
        this.testModeOwltEnabled = testModeOwltEnabled;
    }

    public void setTestModeOwltSec(double testModeOwltSec) {
        this.testModeOwltSec = testModeOwltSec;
    }

    public void setClockChangeRateAssignedValue(double clockChangeRateAssignedValue) {
        this.clockChangeRateAssignedValue = clockChangeRateAssignedValue;
    }

    public void setDryRunConfig(TimeCorrelationRunConfig.DryRunConfig dryRunConfig) {
        this.dryRunConfig = dryRunConfig;
    }

    public void setClockChangeRateMode(TimeCorrelationRunConfig.ClockChangeRateMode clockChangeRateMode) {
        this.clockChangeRateModeOverride = clockChangeRateMode;
    }

    public void setAdditionalSmoothingRecordConfigOverride(TimeCorrelationRunConfig.AdditionalSmoothingRecordConfig additionalSmoothingRecordConfigOverride) {
        this.additionalSmoothingRecordConfigOverride = additionalSmoothingRecordConfigOverride;
    }

    public void setDisableContactFilter(boolean disableContactFilter) {
        isDisableContactFilter = disableContactFilter;
    }

    public void setCreateUplinkCmdFile(boolean createUplinkCmdFile) {
        isCreateUplinkCmdFile = createUplinkCmdFile;
    }
}
