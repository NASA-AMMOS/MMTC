package edu.jhuapl.sd.sig.mmtc.autocorrelate;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.NoTelemetryFoundException;
import edu.jhuapl.sd.sig.mmtc.app.TelemetryQualityException;
import edu.jhuapl.sd.sig.mmtc.autocorrelate.config.AutocorrelateConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationCliInputConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfigInputSupplier;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSampleAndMetrics;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetryRetriever;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionAndAdjustmentOptions;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.*;

public class AutocorrelateApp {
    private static final Logger logger = LogManager.getLogger();

    private final TimeCorrelationCliInputConfig timeCorrelationCliInputConfig;
    private final AutocorrelateConfig config;
    // fixme
    private final OffsetDateTime runUpToErtUtc = now();

    public AutocorrelateApp(String... args) throws Exception {
        try {
            this.timeCorrelationCliInputConfig = new TimeCorrelationCliInputConfig(TimeCorrelationRunConfig.TargetSampleInputErtMode.RUN_UNTIL, args);
            this.config = new AutocorrelateConfig();
            config.getTelemetrySource().applyConfiguration(config);
            // todo apply CLI args to tlm source here


            init();
        } catch (Exception e) {
            throw new MmtcException("MMTC correlation initialization failed.", e);
        }
    }

    private void init() throws TimeConvertException, MmtcException {
        logger.debug("Loading SPICE library");
        TimeConvert.loadSpiceLib();
    }

    public void run() throws Exception {
        OffsetDateTimeRange nextRangeToRun = null;
        boolean lastRunWasSuccessful = false;
        while (true) {
            // reconnect to telemetry source
            config.getTelemetrySource().connect();

            Optional<OffsetDateTimeRange> maybeNextRangeToRun;
            try {
                // temporarily load kernels, including input SCLK kernel, so we can calculate next range to run (which involves TSC & LSK time conversions)
                TimeConvert.loadSpiceKernels(config.getKernelsToLoad(true));
                TimeConvert.validateLoadedSclkKernels(config.getSpacecraftId());

                maybeNextRangeToRun = generateNextRangeToRun(nextRangeToRun, config, lastRunWasSuccessful);
            } finally {
                TimeConvert.unloadSpiceKernels();
            }

            if (! maybeNextRangeToRun.isPresent()) {
                logger.info("Automation complete");
                config.getTelemetrySource().disconnect();
                return;
            }

            try {
                nextRangeToRun = maybeNextRangeToRun.get();
                logger.info(String.format("Next run range: %s to %s", nextRangeToRun.getStart(), nextRangeToRun.getStop()));

                // calls TimeCorrelationApp, which manages its own kernel loading & unloading
                lastRunWasSuccessful = doCorrelationRun(nextRangeToRun, config).isPresent();
            } finally {
                config.getTelemetrySource().disconnect();
            }
        }
    }

    private Optional<OffsetDateTimeRange> generateNextRangeToRun(OffsetDateTimeRange prevRunErtRange, AutocorrelateConfig config, boolean lastRunWasSuccessful) throws Exception {

        // final CorrelationTriplet lastTriplet = latestSclkKernel.getLastTriplet();

        // no matter which mode is enabled, place the starting point at the end of the prior ERT run range, or the last SCLK kernel triplet if this is the first run
        final OffsetDateTime startTimeErt;
        {
            if (prevRunErtRange == null || (! lastRunWasSuccessful)) {
                // if this is our first correlation in the automation run, or if the last correlation wasn't successful, start from the last triplet (this is to give the chance at the widest possible sample set)
                // config.getInputSclkKernelPath looks up the latest SCLK kernel with each call
                final Path inputSclkKernelPath = config.getInputSclkKernelPath();
                final SclkKernel latestSclkKernel = SclkKernel.read(inputSclkKernelPath);
                Optional<OffsetDateTime> possibleStartTimeErt = findFirstTelemetryWithTdtGAfter(config, latestSclkKernel.getLastTriplet().getTdt());

                if (possibleStartTimeErt.isPresent()) {
                    startTimeErt = possibleStartTimeErt.get();
                } else {
                    // if there's no telemetry after the latest entry in the SCLK kernel, we're done
                    return Optional.empty();
                }
            } else {
                // else if there was a successful prior run, bound the start for the next run to the stop time of the previous run
                startTimeErt = prevRunErtRange.getStop();
            }
        }
        logger.debug("Next run range start time: " + startTimeErt);

        final OffsetDateTime stopTimeErt;
        {
            // either of these could be null if a triggering condition is not valid
            final OffsetDateTime stopTimeErtPeriodicTrigger;

            if (config.isAutocorrelatePeriodicTriggerEnabled()) {
                OffsetDateTime possibleStopTimeErtPeriodicTrigger;
                if (prevRunErtRange == null) {
                    logger.info("First run:");
                    possibleStopTimeErtPeriodicTrigger = startTimeErt.plusMinutes((int) config.getAutocorrelatePeriodicTriggerPeriodHours() * 60L);
                } else {
                    if (lastRunWasSuccessful) {
                        logger.info("last run successful:");
                        possibleStopTimeErtPeriodicTrigger = startTimeErt.plusMinutes((int) config.getAutocorrelatePeriodicTriggerPeriodHours() * 60L);
                    } else {
                        possibleStopTimeErtPeriodicTrigger = prevRunErtRange.getStop().plusMinutes((int) config.getAutocorrelatePeriodicTriggerPeriodHours() * 60L);
                    }
                }


                if (possibleStopTimeErtPeriodicTrigger.isBefore(now())) {
                    stopTimeErtPeriodicTrigger = possibleStopTimeErtPeriodicTrigger;
                    logger.debug("Next run range stop time (from periodic trigger): " + stopTimeErtPeriodicTrigger);
                } else {
                    stopTimeErtPeriodicTrigger = null;
                    logger.debug("Next run range stop time would be past the current time; will not influence stop time");
                }
            } else {
                stopTimeErtPeriodicTrigger = null;
                logger.debug("Periodic trigger not enabled; will not influence stop time");
            }

            final OffsetDateTime stopTimeErtScetErrorTrigger;
            if (config.isAutocorrelateScetErrorTriggerEnabled()) {
                Optional<FrameSampleAndMetrics> fsam = findFirstFrameSampleWithScetErrorExceedingThreshold(config, startTimeErt);
                if (fsam.isPresent()) {
                    OffsetDateTime scetErrorThresholdCrossingTime = TimeConvert.parseIsoDoyUtcStr(fsam.get().frameSample.getErtStr());
                    logger.debug("Telemetry has been found that exceeds SCET Error threshold at ERT (UTC): " + scetErrorThresholdCrossingTime);

                    // ideally, we'd create a run that prevents the threshold from being crossed
                    // if this is the first
                    if (prevRunErtRange == null || (prevRunErtRange.getStop().isBefore(scetErrorThresholdCrossingTime))) {
                        // if this is the first run, or if the prior run did not extend out to the sample that crosses the SCET Error threshold, run up until that time
                        stopTimeErtScetErrorTrigger = scetErrorThresholdCrossingTime;
                        logger.debug("Next run range stop time (from SCET Error trigger, at the time of threshold violation): " + stopTimeErtScetErrorTrigger);
                    } else {
                        // else we had a prior run that met or exceeded the SCET error threshold crossing time, so we're past the error threshold, but we should still do a best-effort to correlate as soon as possible after it
                        OffsetDateTime possibleStopTimeErtScetErrorTrigger = prevRunErtRange.getStop().plusMinutes((int) config.getAutocorrelatePeriodicTriggerPeriodHours() * 60L);
                        if (possibleStopTimeErtScetErrorTrigger.isBefore(now())) {
                            stopTimeErtScetErrorTrigger = possibleStopTimeErtScetErrorTrigger;
                            logger.debug("Next run range stop time (from SCET Error trigger, advancing after threshold violation time): " + stopTimeErtScetErrorTrigger);
                        } else {
                            stopTimeErtScetErrorTrigger = null;
                            logger.debug("Next run range stop time (from SCET Error trigger, advancing after threshold violation time) would be past the current time; will not influence stop time");
                        }
                    }
                } else {
                    stopTimeErtScetErrorTrigger = null;
                    logger.debug("No telemetry found that exceeds SCET Error threshold.  SCET Error trigger will not influence stop time.");
                }
            } else {
                stopTimeErtScetErrorTrigger = null;
                logger.debug("SCET Error trigger not enabled; will not influence stop time");
            }

            if (stopTimeErtPeriodicTrigger != null && stopTimeErtScetErrorTrigger != null) {
                stopTimeErt = earliest(stopTimeErtPeriodicTrigger, stopTimeErtScetErrorTrigger);
            } else if (stopTimeErtPeriodicTrigger != null) {
                stopTimeErt = stopTimeErtPeriodicTrigger;
            } else if (stopTimeErtScetErrorTrigger != null) {
                stopTimeErt = stopTimeErtScetErrorTrigger;
            } else {
                stopTimeErt = null;
            }
        }

        // startTimeErt cannot be null, but stopTimeErt could be null if no valid trigger condition was found
        if (stopTimeErt != null) {
            return Optional.of(new OffsetDateTimeRange(startTimeErt, stopTimeErt));
        } else {
            return Optional.empty();
        }
    }

    private Optional<OffsetDateTime> findFirstTelemetryWithTdtGAfter(AutocorrelateConfig config, double afterTdtG) throws Exception {
        final TelemetryRetriever retriever = new TelemetryRetriever(config);
        List<FrameSampleAndMetrics> frameSampleAndMetrics = retriever.retrieveTelemetryAndCalculateBasicStats(
                new TelemetrySelectionAndAdjustmentOptions() {
                    @Override
                    public TimeCorrelationRunConfig.TargetSampleInputErtMode getTargetSampleInputErtMode() {
                        return TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE;
                    }

                    @Override
                    public Optional<OffsetDateTimeRange> getResolvedTargetSampleRange() {
                        // we're going to use TDT(G) here as a conservative proxy for ERT.  it'll always be earlier than ERT, but the later filtering on actual TDT_G values will handle it
                        try {
                            return Optional.of(new OffsetDateTimeRange(TimeConvert.tdtToUtc(afterTdtG, 6), now()));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public Optional<OffsetDateTime> getResolvedTargetSampleExactErt() {
                        return Optional.empty();
                    }

                    @Override
                    public boolean isTestMode() {
                        return config.isTestModeOwltEnabled();
                    }

                    @Override
                    public double getTestModeOwlt() {
                        return config.getTestModeOwltSec();
                    }
                },
                false
        );

        for (FrameSampleAndMetrics fsm : frameSampleAndMetrics) {
            if (fsm.metrics.tdtG > afterTdtG) {
                return Optional.of(TimeConvert.parseIsoDoyUtcStr(fsm.frameSample.getErtStr()));
            }
        }

        return Optional.empty();
    }

    private Optional<FrameSampleAndMetrics> findFirstFrameSampleWithScetErrorExceedingThreshold(AutocorrelateConfig config, OffsetDateTime startTimeErt) throws Exception {

        final TelemetryRetriever retriever = new TelemetryRetriever(config);
        List<FrameSampleAndMetrics> frameSampleAndMetrics = retriever.retrieveTelemetryAndCalculateBasicStats(
                new TelemetrySelectionAndAdjustmentOptions() {
                    @Override
                    public TimeCorrelationRunConfig.TargetSampleInputErtMode getTargetSampleInputErtMode() {
                        return TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE;
                    }

                    @Override
                    public Optional<OffsetDateTimeRange> getResolvedTargetSampleRange() {
                        return Optional.of(new OffsetDateTimeRange(startTimeErt, now()));
                    }

                    @Override
                    public Optional<OffsetDateTime> getResolvedTargetSampleExactErt() {
                        return Optional.empty();
                    }

                    @Override
                    public boolean isTestMode() {
                        return config.isTestModeOwltEnabled();
                    }

                    @Override
                    public double getTestModeOwlt() {
                        return config.getTestModeOwltSec();
                    }
                },
                true
        );

        for (FrameSampleAndMetrics fsm : frameSampleAndMetrics) {
            if (Math.abs(fsm.metrics.getScetErrorMs()) >= config.getAutocorrelateScetErrorTriggerThreshold()) {
                return Optional.of(fsm);
            }
        }

        return Optional.empty();
    }

    private TimeCorrelationRunConfigInputSupplier getSupplierForRange(OffsetDateTimeRange range) {
        return new TimeCorrelationRunConfigInputSupplier() {
            @Override
            public TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(List<TelemetrySource.AdditionalOption> additionalTlmSourceOptions) throws Exception {
                return TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs.copyWithErtRangeSet(
                        timeCorrelationCliInputConfig.getRunConfigInputs(additionalTlmSourceOptions),
                        range
                );
            }
        };
    }

    private Optional<TimeCorrelationContext> doCorrelationRun(OffsetDateTimeRange range, MmtcConfigWithTlmSource config) throws Exception {
        logger.info("Running on period: " + range);
        try {
            return Optional.of(new TimeCorrelationApp(new TimeCorrelationRunConfig(getSupplierForRange(range), config)).run());
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
}
