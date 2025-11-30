package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig.ClockChangeRateMode.ASSIGN;
import static edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig.ClockChangeRateMode.ASSIGN_KEY;

public class TimeCorrelationCliInputConfig implements TimeCorrelationRunConfigInputSupplier {
    private static final Logger logger = LogManager.getLogger();

    private final String[] args;

    public TimeCorrelationCliInputConfig(String... args) throws Exception {
        this.args = args;
    }

    @Override
    public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(Map<String, TelemetrySource.AdditionalOption> additionalTlmSrcOptionsByName) throws Exception {
        logger.info("Command line arguments: " + Arrays.asList(args));

        final CorrelationCommandLineConfig cmdLineConfig = new CorrelationCommandLineConfig(
                args,
                additionalTlmSrcOptionsByName.values().stream().map(additionalOption -> additionalOption.cliOption).collect(Collectors.toList())
        );

        if (! cmdLineConfig.load()) {
            throw new MmtcException("Error parsing command line arguments.");
        }


        final Optional<Double> testModeOwltSec = cmdLineConfig.isTestMode() ? Optional.of(cmdLineConfig.getTestModeOwlt()) : Optional.empty();
        final Optional<Double> assignVal = cmdLineConfig.getClockChangeRateMode().equals(ASSIGN) ? Optional.of(cmdLineConfig.getClockChangeRateAssignedValue()) : Optional.empty();
        final Optional<String> assignValKey = cmdLineConfig.getClockChangeRateMode().equals(ASSIGN_KEY) ? Optional.of(cmdLineConfig.getClockChangeRateAssignedKey()) : Optional.empty();

        final TimeCorrelationRunConfig.DryRunConfig dryRunConfig = cmdLineConfig.isDryRun() ? new TimeCorrelationRunConfig.DryRunConfig(TimeCorrelationRunConfig.DryRunMode.DRY_RUN_RETAIN_NO_PRODUCTS, null) : new TimeCorrelationRunConfig.DryRunConfig(TimeCorrelationRunConfig.DryRunMode.NOT_DRY_RUN, null);

        return new TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs(
                TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE,
                Optional.of(cmdLineConfig.getStartTime()),
                Optional.of(cmdLineConfig.getStopTime()),
                Optional.empty(), // todo enable this on the cmd line
                Optional.empty(),                  // todo enable this on the cmd line
                cmdLineConfig.isTestMode(),
                testModeOwltSec,
                assignVal,
                assignValKey,
                cmdLineConfig.hasClockChangeRateMode() ? Optional.of(cmdLineConfig.getClockChangeRateMode()) : Optional.empty(),
                cmdLineConfig.getAdditionalSmoothingRecordInsertionOverride(),
                cmdLineConfig.isContactFilterDisabled(),
                cmdLineConfig.isGenerateCmdFile(),
                dryRunConfig
        );
    }
}
