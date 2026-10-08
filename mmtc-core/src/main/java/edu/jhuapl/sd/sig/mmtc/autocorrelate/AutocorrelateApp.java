package edu.jhuapl.sd.sig.mmtc.autocorrelate;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.NoTelemetryFoundException;
import edu.jhuapl.sd.sig.mmtc.app.TelemetryQualityException;
import edu.jhuapl.sd.sig.mmtc.autocorrelate.config.AutocorrelateCliConfig;
import edu.jhuapl.sd.sig.mmtc.autocorrelate.config.AutocorrelateConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfigInputSupplier;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSampleAndMetrics;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetryRetriever;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.tlm.range.ErtRange;
import edu.jhuapl.sd.sig.mmtc.tlm.range.ErtRangeWithMinTdtG;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionAndAdjustmentOptions;
import edu.jhuapl.sd.sig.mmtc.util.Owlt;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.*;

public class AutocorrelateApp {
    private static final Logger logger = LogManager.getLogger();

    private final AutocorrelateConfig config;

    // whether this app has been interrupted and should be stopped
    private volatile boolean interrupted = false;

    public AutocorrelateApp(String... args) throws Exception {
        try {
            this.config = new AutocorrelateConfig(new AutocorrelateCliConfig(args));
            config.getTelemetrySource().applyConfiguration(config);
            init();
        } catch (Exception e) {
            throw new MmtcException("MMTC autocorrelate initialization failed.", e);
        }
    }

    private void init() throws TimeConvertException {
        logger.debug("Loading SPICE library");
        TimeConvert.loadSpiceLib();
    }

    public List<TimeCorrelationContext> run() throws Exception {
        ErtRange nextRangeToRun = null;
        boolean lastRunWasSuccessful = false;

        List<TimeCorrelationContext> completedCorrelationRuns = new ArrayList<>();

        while (! interrupted) {
            // reconnect to telemetry source
            config.getTelemetrySource().connect();

            Optional<ErtRange> maybeNextRangeToRun;
            try {
                // temporarily load kernels, including input SCLK kernel, so we can calculate next range to run (which involves TSC & LSK time conversions)
                TimeConvert.loadSpiceKernels(config.getKernelsToLoad(true));
                TimeConvert.validateLoadedSclkKernels(config.getSpacecraftId());

                maybeNextRangeToRun = generateNextCorrelationRangeToRun(nextRangeToRun, lastRunWasSuccessful);
            } finally {
                TimeConvert.unloadSpiceKernels();
            }

            if (! maybeNextRangeToRun.isPresent()) {
                logger.info("Automation complete");
                config.getTelemetrySource().disconnect();
                return completedCorrelationRuns;
            }

            try {
                nextRangeToRun = maybeNextRangeToRun.get();

                // calls TimeCorrelationApp, which manages its own kernel loading & unloading
                Optional<TimeCorrelationContext> lastCorrelationRunContext = doCorrelationRun(nextRangeToRun);
                lastCorrelationRunContext.ifPresent(completedCorrelationRuns::add);
                lastRunWasSuccessful = lastCorrelationRunContext.isPresent();
            } finally {
                config.getTelemetrySource().disconnect();
            }
        }

        logger.info("Exiting due to received interrupt.");

        return completedCorrelationRuns;
    }

    private Optional<ErtRange> generateNextCorrelationRangeToRun(ErtRange prevRunErtRange, boolean lastRunWasSuccessful) throws Exception {
        final Optional<OffsetDateTime> maybeStartTimeErt = earliestPresent(getPeriodicTriggerStartTimeErt(), getScetErrorTriggerStartTimeErt());

        if (! maybeStartTimeErt.isPresent()) {
            logger.info("No ranges to run.");
            return Optional.empty();
        }

        final OffsetDateTime startTimeErt = maybeStartTimeErt.get();
        OffsetDateTime stopTimeErt;
        if ((prevRunErtRange == null) || lastRunWasSuccessful) {
            // if this is the first run, or if the last run was successful, then set the time correlation range to a length of the advancement hours
            stopTimeErt = startTimeErt.plusHours(config.getAutocorrelateAdvancementDurationHours());
        } else {
            // if the last run was not successful, then advance the time correlation range by a length of the advancement hours
            stopTimeErt = prevRunErtRange.getStop().plusHours(config.getAutocorrelateAdvancementDurationHours());
        }

        // if the next start time is not before the run-until time, then no further runs are necessary
        if (! startTimeErt.isBefore(config.getRunUntilTime())) {
            logger.info("Start time of next run range is caught up to configured stop time: " + config.getRunUntilTime());
            return Optional.empty();
        }

        // if the next stop time is not before the run-until time, make sure we've had at least one run up to the run-until time
        if (! stopTimeErt.isBefore(config.getRunUntilTime())) {
            // if the stop time is after the run-until time
            if (prevRunErtRange == null) {
                // and if this is our first run, then cap the stop time to the run-until time
                logger.info("Stop time of next run range is capped to the configured stop time: " + config.getRunUntilTime());
                stopTimeErt = config.getRunUntilTime();
            } else {
                // and if this is not our first run
                if (prevRunErtRange.getStop().equals(config.getRunUntilTime())) {
                    // and if the prior run already ran to the run-until time, we're done
                    logger.info("Stop time of next run range is caught up to configured stop time: " + config.getRunUntilTime());
                    return Optional.empty();
                } else {
                    // if the prior run has not yet ran to the run-until time, , then cap the stop time to the run-until time
                    logger.info("Stop time of next run range is capped to the configured stop time: " + config.getRunUntilTime());
                    stopTimeErt = config.getRunUntilTime();
                }
            }
        }

        final ErtRange nextRunRange = new ErtRange(startTimeErt, stopTimeErt);
        logger.info("Next run range: " + nextRunRange);
        return Optional.of(nextRunRange);
    }

    private Optional<OffsetDateTime> findErtOfFirstValidTimeCorrTargetWithScetErrorExceedingThreshold(AutocorrelateConfig config, ErtRangeWithMinTdtG ertRangeWithMinTdtG) throws Exception {
        final TelemetryRetriever retriever = new TelemetryRetriever(config);

        List<TelemetryRetriever.TcTargetAndFsam> results = retriever.retrieveAllFrameSamplesAsTcTargetsInRange(
                new TelemetrySelectionAndAdjustmentOptions() {
                    @Override
                    public TimeCorrelationRunConfig.TargetSampleInputErtMode getTargetSampleInputErtMode() {
                        return TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE;
                    }

                    @Override
                    public Optional<OffsetDateTimeRange> getResolvedTargetSampleErtRange() {
                        return Optional.of(new OffsetDateTimeRange(ertRangeWithMinTdtG.getStart(), ertRangeWithMinTdtG.getStop()));
                    }

                    @Override
                    public TargetSampleRangeErtSeekOrder getTargetSampleRangeErtSeekOrder() {
                        return TargetSampleRangeErtSeekOrder.ASCENDING;
                    }

                    @Override
                    public Optional<OffsetDateTime> getResolvedTargetSampleExactErt() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Double> getMinTdtGExclusive() {
                        return Optional.of(ertRangeWithMinTdtG.minTdtGExclusive);
                    }

                    @Override
                    public boolean isTestMode() {
                        return config.isTestModeOwltEnabled();
                    }

                    @Override
                    public double getTestModeOwlt() {
                        return config.getTestModeOwltSec();
                    }
                }
        );

        for (TelemetryRetriever.TcTargetAndFsam res : results) {
            if (Math.abs(res.fsamForTargetFrame.metrics.getScetErrorMs()) >= config.getAutocorrelateScetErrorTriggerThresholdMs()) {
                return Optional.of(
                        TimeConvert.parseIsoDoyUtcStr(
                            res.tcTarget.getSampleSet().get(0).getErtStr()
                        )
                );
            }
        }

        return Optional.empty();
    }

    private Optional<OffsetDateTime> getPeriodicTriggerStartTimeErt() throws MmtcException, IOException, TimeConvertException, TextProductException {
        if (! config.isAutocorrelatePeriodicTriggerEnabled()) {
            logger.info("Periodic trigger not enabled.");
            return Optional.empty();
        }

        final Path inputSclkKernelPath = config.getInputSclkKernelPath();
        final SclkKernel latestSclkKernel = SclkKernel.read(inputSclkKernelPath);
        final double mostRecentTripletTdt = latestSclkKernel.getLastTriplet().getTdt();

        final OffsetDateTime mostRecentTripletApproximateErt = Owlt.scetToCenterOfEarthErt(TimeConvert.tdtToUtc(mostRecentTripletTdt, 6), config.getNaifSpacecraftId());
        final OffsetDateTime startTimeErt = mostRecentTripletApproximateErt.plusHours(config.getAutocorrelatePeriodicMinTimeBetweenCorrelationsHours());
        logger.info("Periodic trigger start time candidate: " + startTimeErt);
        return Optional.of(startTimeErt);
    }

    private Optional<OffsetDateTime> getScetErrorTriggerStartTimeErt() throws Exception {
        if (! config.isAutocorrelateScetErrorTriggerEnabled()) {
            logger.info("SCET error trigger not enabled.");
            return Optional.empty();
        }

        final Path inputSclkKernelPath = config.getInputSclkKernelPath();
        final SclkKernel latestSclkKernel = SclkKernel.read(inputSclkKernelPath);
        final double mostRecentTripletTdt = latestSclkKernel.getLastTriplet().getTdt();

        // if there are any samples from the last correlation to the run-until time that violate the SCET error threshold, get their ERT and use it as the start time for the next correlation run
        OffsetDateTime ertQueryStartTime = Owlt.scetToCenterOfEarthErt(tdtToUtc(mostRecentTripletTdt, 6), config.getNaifSpacecraftId());
        Optional<OffsetDateTime> ert = findErtOfFirstValidTimeCorrTargetWithScetErrorExceedingThreshold(config, new ErtRangeWithMinTdtG(ertQueryStartTime, config.getRunUntilTime(), mostRecentTripletTdt));

        if (ert.isPresent()) {
            OffsetDateTime scetErrorThresholdCrossingTimeErt = ert.get();
            logger.info("Telemetry has been found that exceeds SCET Error threshold at ERT (UTC): " + scetErrorThresholdCrossingTimeErt);
            logger.info("This will be used as the SCET error trigger start time candidate");
            return Optional.of(scetErrorThresholdCrossingTimeErt);
        } else {
            logger.debug("No telemetry found that exceeds SCET Error threshold.");
            return Optional.empty();
        }
    }

    private TimeCorrelationRunConfigInputSupplier getSupplierForRange(ErtRange range) {
        return new TimeCorrelationRunConfigInputSupplier() {
            @Override
            public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(List<TelemetrySource.AdditionalOption> additionalTlmSourceOptions) throws Exception {
                return config.getTimeCorrelationRunConfigInputsForErtRange(range);
            }
        };
    }

    private Optional<TimeCorrelationContext> doCorrelationRun(ErtRange ertRange) throws Exception {
        logger.info("Running over period: " + ertRange);
        try {
            return Optional.of(new TimeCorrelationApp(new TimeCorrelationRunConfig(getSupplierForRange(ertRange), config)).run());
        } catch (NoTelemetryFoundException e) {
            logger.info("No telemetry found.", e);
            return Optional.empty();
        } catch (TelemetryQualityException e) {
            logger.warn("All telemetry found within the query window did not pass filters or validation", e);
            return Optional.empty();
        } catch (Exception e) {
            logger.info("Autocorrelate run failed", e);
            throw e;
        }
    }

    public void interrupt() {
        logger.info("Received interrupt");
        this.interrupted = true;
    }
}
