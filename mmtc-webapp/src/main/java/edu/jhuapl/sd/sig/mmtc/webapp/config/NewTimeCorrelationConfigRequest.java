package edu.jhuapl.sd.sig.mmtc.webapp.config;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfigInputSupplier;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public class NewTimeCorrelationConfigRequest implements TimeCorrelationRunConfigInputSupplier {
    public TimeCorrelationRunConfig.TargetSampleInputErtMode targetSampleInputErtMode;
    public OffsetDateTime targetSampleRangeStartErt;
    public OffsetDateTime targetSampleRangeStopErt;
    public OffsetDateTime targetSampleExactErt;
    public Double priorCorrelationExactTdt;
    public boolean testModeOwltEnabled;
    public double testModeOwltSec;

    public TimeCorrelationRunConfig.DryRunConfig dryRunConfig;

    public TimeCorrelationRunConfig.ClockChangeRateConfig clockChangeRateConfig;
    public TimeCorrelationRunConfig.AdditionalSmoothingRecordConfig additionalSmoothingRecordConfigOverride;
    public boolean isDisableContactFilter;
    public boolean isCreateUplinkCmdFile;

    public NewTimeCorrelationConfigRequest() {

    }

    public NewTimeCorrelationConfigRequest(NewTimeCorrelationConfigRequest other) {
        this.targetSampleInputErtMode = other.targetSampleInputErtMode;
        this.targetSampleRangeStartErt = other.targetSampleRangeStartErt;
        this.targetSampleRangeStopErt = other.targetSampleRangeStopErt;
        this.targetSampleExactErt = other.targetSampleExactErt;
        this.priorCorrelationExactTdt = other.priorCorrelationExactTdt;
        this.testModeOwltEnabled = other.testModeOwltEnabled;
        this.testModeOwltSec = other.testModeOwltSec;
        this.clockChangeRateConfig = other.clockChangeRateConfig;
        this.dryRunConfig = other.dryRunConfig;
        this.additionalSmoothingRecordConfigOverride = other.additionalSmoothingRecordConfigOverride;
        this.isDisableContactFilter = other.isDisableContactFilter;
        this.isCreateUplinkCmdFile = other.isCreateUplinkCmdFile;
    }


    @Override
    public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(Map<String, TelemetrySource.AdditionalOption> additionalTlmSrcOptionsByName) throws Exception {
        return new TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs(
                targetSampleInputErtMode,
                Optional.ofNullable(targetSampleRangeStartErt),
                Optional.ofNullable(targetSampleRangeStopErt),
                Optional.ofNullable(targetSampleExactErt),
                Optional.ofNullable(priorCorrelationExactTdt),
                testModeOwltEnabled,
                Optional.of(testModeOwltSec),
                Optional.of(clockChangeRateConfig.specifiedClockChangeRateToAssign),
                Optional.empty(),
                Optional.of(clockChangeRateConfig.clockChangeRateModeOverride),
                Optional.of(additionalSmoothingRecordConfigOverride),
                isDisableContactFilter,
                isCreateUplinkCmdFile,
                dryRunConfig
        );
    }

    public void setTargetSampleRangeStartErt(OffsetDateTime targetSampleRangeStartErt) {
        this.targetSampleRangeStartErt = targetSampleRangeStartErt;
    }

    public void setTargetSampleRangeStopErt(OffsetDateTime targetSampleRangeStopErt) {
        this.targetSampleRangeStopErt = targetSampleRangeStopErt;
    }

    public void setTargetSampleExactErt(OffsetDateTime targetSampleExactErt) {
        this.targetSampleExactErt = targetSampleExactErt;
    }

    public void setPriorCorrelationExactTdt(Double priorCorrelationExactTdt) {
        this.priorCorrelationExactTdt = priorCorrelationExactTdt;
    }

    public void setTestModeOwltEnabled(boolean testModeOwltEnabled) {
        this.testModeOwltEnabled = testModeOwltEnabled;
    }

    public void setTestModeOwltSec(double testModeOwltSec) {
        this.testModeOwltSec = testModeOwltSec;
    }

    public void setDryRunConfig(TimeCorrelationRunConfig.DryRunConfig dryRunConfig) {
        this.dryRunConfig = dryRunConfig;
    }

    public void setClockChangeRateConfig(TimeCorrelationRunConfig.ClockChangeRateConfig clockChangeRateConfig) {
        this.clockChangeRateConfig = clockChangeRateConfig;
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

    public void setTargetSampleInputErtMode(TimeCorrelationRunConfig.TargetSampleInputErtMode targetSampleInputErtMode) {
        this.targetSampleInputErtMode = targetSampleInputErtMode;
    }
}
