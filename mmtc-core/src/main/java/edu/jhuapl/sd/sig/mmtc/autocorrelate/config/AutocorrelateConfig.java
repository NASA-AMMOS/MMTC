package edu.jhuapl.sd.sig.mmtc.autocorrelate.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.range.ErtRange;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.now;

public class AutocorrelateConfig extends MmtcConfigWithTlmSource {
    private final OffsetDateTime runUntilTime;

    public AutocorrelateConfig(AutocorrelateCliConfig cliConfig) throws Exception {
        super();
        this.runUntilTime = cliConfig.getRunUntilTime();
    }

    public AutocorrelateConfig() throws Exception {
        super();
        this.runUntilTime = now();
    }

    @Override
    public void validate() throws MmtcException {
        super.validate();

        if ((! isAutocorrelatePeriodicTriggerEnabled()) && (! isAutocorrelateScetErrorTriggerEnabled())) {
            throw new MmtcException("Must enabled at least one autocorrelate trigger");
        }

        if (isAutocorrelatePeriodicTriggerEnabled()) {
            if (! containsKey("autocorrelate.trigger.periodic.minTimeBetweenCorrelationsHours")) {
                throw new MmtcException("If the periodic trigger is enabled, the period duration must be specified");
            }

            if (getAutocorrelatePeriodicMinTimeBetweenCorrelationsHours() <= 0.0) {
                throw new MmtcException("If the periodic trigger is enabled, a min time between correlations of more than 0 hours must be specified");
            }
        }

        if (isAutocorrelateScetErrorTriggerEnabled()) {
            if (! containsKey("autocorrelate.trigger.scetError.threshold")) {
                throw new MmtcException("If SCET Error trigger is enabled, an error threshold must be specified");
            }

            if (getAutocorrelateScetErrorTriggerThresholdMs() <= 0.0) {
                throw new MmtcException("If SCET Error trigger is enabled, an error threshold above 0 must be specified");
            }
        }
    }

    public boolean isTestModeOwltEnabled() {
        return getBoolean("autocorrelate.testmode.enabled", false);
    }

    public double getTestModeOwltSec() {
        return getDouble("autocorrelate.testmode.owltSec", 0.0);
    }

    public OffsetDateTime getRunUntilTime() {
        return runUntilTime;
    }

    public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getTimeCorrelationRunConfigInputsForErtRange(ErtRange ertRange) {
        return new TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs(
                TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE,
                Optional.of(ertRange.getStart()),
                Optional.of(ertRange.getStop()),
                Optional.of(TimeCorrelationRunConfig.TargetSampleRangeErtSeekOrder.ASCENDING),
                Optional.empty(),
                Optional.empty(),
                isTestModeOwltEnabled(),
                Optional.of(getTestModeOwltSec()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                new TimeCorrelationRunConfig.DryRunConfig(TimeCorrelationRunConfig.DryRunMode.NOT_DRY_RUN, null),
                Arrays.asList()
        );
    }
}
