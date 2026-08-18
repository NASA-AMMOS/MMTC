package edu.jhuapl.sd.sig.mmtc.correlation.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.autocorrelate.config.AutocorrelateCliConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig.ClockChangeRateMode.ASSIGN;
import static edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig.ClockChangeRateMode.ASSIGN_KEY;

public class TimeCorrelationCliInputConfig implements TimeCorrelationRunConfigInputSupplier {
    protected static final Logger logger = LogManager.getLogger();

    private final TimeCorrelationRunConfig.TargetSampleInputErtMode targetSampleErtMode;
    protected final String[] args;

    private CorrelationCliConfig cmdLineConfig;

    // default mode is RANGE
    public TimeCorrelationCliInputConfig(String... args) throws Exception {
        this.targetSampleErtMode = TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE;
        this.args = args;
    }

    public TimeCorrelationCliInputConfig(TimeCorrelationRunConfig.TargetSampleInputErtMode targetSampleErtMode, String... args) throws Exception {
        this.targetSampleErtMode = targetSampleErtMode;
        this.args = args;
    }

    @Override
    public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(List<TelemetrySource.AdditionalOption> additionalTlmSourceOptions) throws Exception {
        logger.info("Command line arguments: " + Arrays.asList(args));

        switch (targetSampleErtMode) {
            case RANGE:
                this.cmdLineConfig = new CorrelationRangeCliConfig(
                        args,
                        additionalTlmSourceOptions.stream().map(additionalOption -> additionalOption.cliOption).collect(Collectors.toList())
                );
                break;
            case RUN_UNTIL:
                this.cmdLineConfig = new AutocorrelateCliConfig(
                        args,
                        additionalTlmSourceOptions.stream().map(additionalOption -> additionalOption.cliOption).collect(Collectors.toList())
                );
                break;
            default:
                throw new UnsupportedOperationException("Mode " + targetSampleErtMode + " is not supported for CLI usage.");
        }

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

        final Optional<OffsetDateTime> targetSampleRangeStartErt;
        final Optional<OffsetDateTime> targetSampleRangeStopErt;
        final Optional<OffsetDateTime> targetSampleExactErt;
        final Optional<OffsetDateTime> runUntilErt;
        switch (targetSampleErtMode) {
            case RANGE:
                targetSampleRangeStartErt = Optional.of(((CorrelationRangeCliConfig) cmdLineConfig).getStartTime());
                targetSampleRangeStopErt = Optional.of(((CorrelationRangeCliConfig) cmdLineConfig).getStopTime());
                targetSampleExactErt = Optional.empty();
                runUntilErt = Optional.empty();
                break;
            case RUN_UNTIL:
                targetSampleRangeStartErt = Optional.empty();
                targetSampleRangeStopErt = Optional.empty();
                targetSampleExactErt = Optional.empty();
                runUntilErt = Optional.of(((AutocorrelateCliConfig) cmdLineConfig).getUntilTime());
                break;
            default:
                throw new UnsupportedOperationException("Mode " + targetSampleErtMode + " is not supported for CLI usage.");
        }

        return new TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs(
                TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE,
                targetSampleRangeStartErt,
                targetSampleRangeStopErt,
                targetSampleExactErt,                                  // todo enable this on the cmd line
                runUntilErt,
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
