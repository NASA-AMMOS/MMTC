package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.apache.commons.cli.Option;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig.ClockChangeRateMode.ASSIGN;
import static edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig.ClockChangeRateMode.ASSIGN_KEY;

public class TimeCorrelationCliInputConfig implements TimeCorrelationRunConfigInputSupplier {
    private static final Logger logger = LogManager.getLogger();

    private final String[] args;

    private CorrelationCommandLineConfig cmdLineConfig;

    public TimeCorrelationCliInputConfig(String... args) throws Exception {
        this.args = args;
    }

    @Override
    public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(List<TelemetrySource.AdditionalOption> additionalTlmSourceOptions) throws Exception {
        logger.info("Command line arguments: " + Arrays.asList(args));

        this.cmdLineConfig = new CorrelationCommandLineConfig(
                args,
                additionalTlmSourceOptions.stream().map(additionalOption -> additionalOption.cliOption).collect(Collectors.toList())
        );

        if (! cmdLineConfig.load()) {
            throw new MmtcException("Error parsing command line arguments.");
        }

        final Optional<Double> testModeOwltSec = cmdLineConfig.isTestMode() ? Optional.of(cmdLineConfig.getTestModeOwlt()) : Optional.empty();

        Optional<Double> assignVal = Optional.empty();
        Optional<String> assignValKey = Optional.empty();

        if (cmdLineConfig.hasClockChangeRateMode()) {
            if (cmdLineConfig.getClockChangeRateMode().equals(ASSIGN)) {
                assignVal = Optional.of(cmdLineConfig.getClockChangeRateAssignedValue());
            } else if  (cmdLineConfig.getClockChangeRateMode().equals(ASSIGN_KEY)) {
                assignValKey = Optional.of(cmdLineConfig.getClockChangeRateAssignedKey());
            }
        }

        final TimeCorrelationRunConfig.DryRunConfig dryRunConfig = cmdLineConfig.isDryRun() ? new TimeCorrelationRunConfig.DryRunConfig(TimeCorrelationRunConfig.DryRunMode.DRY_RUN_RETAIN_NO_PRODUCTS, null) : new TimeCorrelationRunConfig.DryRunConfig(TimeCorrelationRunConfig.DryRunMode.NOT_DRY_RUN, null);

        List<TelemetrySource.ParsedAdditionalOption> parsedAdditionalOptions = new ArrayList<>();

        for (TelemetrySource.AdditionalOption additionalTlmSrcOption : additionalTlmSourceOptions) {
            parsedAdditionalOptions.add(new TelemetrySource.ParsedAdditionalOption(
                    additionalTlmSrcOption.name,
                    Optional.ofNullable(cmdLineConfig.getOptionValue(additionalTlmSrcOption.cliOption.getOpt()))
            ));
        }

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
                dryRunConfig,
                parsedAdditionalOptions
        );
    }


}
