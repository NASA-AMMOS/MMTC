package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.filter.ContactFilter;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.*;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spice.basic.SpiceErrorException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class TelemetryRetriever {
    private static final Logger logger = LogManager.getLogger();
    private final MmtcConfigWithTlmSource config;
    private final SclkKernel currentSclkKernel;
    private final int sclk_kernel_fine_tick_modulus;

    public TelemetryRetriever(MmtcConfigWithTlmSource config) throws Exception {
        this.config = config;
        this.currentSclkKernel = SclkKernel.read(config.getInputSclkKernelPath());
        this.sclk_kernel_fine_tick_modulus = TimeConvert.getSclkKernelTickRate(config.getNaifSpacecraftId());
    }

    public TimeCorrelationTarget selectSampleSetAndTimeCorrelationTarget(TelemetrySelectionAndAdjustmentOptions tlmOptions) throws MmtcException {
        final TelemetrySelectionStrategy tlmSelecStrat = getTlmSelecStrat(tlmOptions);
        return tlmSelecStrat.get(this::processFilters);
    }

    public static class TcTargetAndFsam {
        public final TimeCorrelationTarget tcTarget;
        public final FrameSampleAndMetrics fsamForTargetFrame;

        public TcTargetAndFsam(TimeCorrelationTarget tcTarget, FrameSampleAndMetrics fsamForTargetFrame) {
            this.tcTarget = tcTarget;
            this.fsamForTargetFrame = fsamForTargetFrame;
        }
    }

    public List<TcTargetAndFsam> retrieveAllFrameSamplesAsTcTargetsInRange(TelemetrySelectionAndAdjustmentOptions tlmOptions) throws MmtcException, SpiceErrorException, TimeConvertException {
        logger.info("Querying telemetry over ERT range: " + tlmOptions.getResolvedTargetSampleErtRange().get());

        // gather 'enriched' FrameSamples that pass validation checks and filters
        final List<TimeCorrelationTarget> tcTargets = retrieveAllSamplesInRangeAsTcTargets(tlmOptions);

        final List<TcTargetAndFsam> results = new ArrayList<>();

        for (TimeCorrelationTarget tct : tcTargets) {
            final FrameSampleAndMetrics fsam = new FrameSampleAndMetrics(
                    tct.getTargetSample(),
                    TimeConvert.calculateFrameSampleMetrics(config, tlmOptions, tct.getTargetSample())
            );

            if (tlmOptions.getMinTdtGExclusive().isPresent()) {
                if (fsam.metrics.tdtG > tlmOptions.getMinTdtGExclusive().get()) {
                    results.add(new TcTargetAndFsam(tct, fsam));
                }
            } else {
                results.add(new TcTargetAndFsam(tct, fsam));
            }
        }

        return results;
    }

    public List<FrameSampleAndMetrics> retrieveAllFrameSamplesInRange(TelemetrySelectionAndAdjustmentOptions tlmOptions) throws MmtcException, SpiceErrorException, TimeConvertException {
        logger.info("Querying telemetry over ERT range: " + tlmOptions.getResolvedTargetSampleErtRange().get());

        // gather 'enriched' FrameSamples that pass validation checks and filters
        final List<FrameSample> frameSamples = retrieveAllSamplesInRangeAsTcTargets(tlmOptions)
                .stream()
                .map(TimeCorrelationTarget::getTargetSample)
                .collect(Collectors.toList());

        if (! frameSamples.isEmpty()) {
            logger.info("Earliest ERT retrieved: " + frameSamples.get(0).getErtStr());
        }


        final List<FrameSampleAndMetrics> frameSamplesAndMetrics = new ArrayList<>();

        for (FrameSample fs : frameSamples) {
            FrameSampleAndMetrics fsam = new FrameSampleAndMetrics(
                    fs,
                    TimeConvert.calculateFrameSampleMetrics(config, tlmOptions, fs)
            );

            if (tlmOptions.getMinTdtGExclusive().isPresent()) {
                if (fsam.metrics.tdtG > tlmOptions.getMinTdtGExclusive().get()) {
                    frameSamplesAndMetrics.add(fsam);
                }
            } else {
                frameSamplesAndMetrics.add(fsam);
            }
        }

        if (! frameSamples.isEmpty()) {
            logger.info("Earliest ERT that'll be returned: " + frameSamplesAndMetrics.get(0).frameSample.getErtStr());
        }

        return frameSamplesAndMetrics;
    }

    private TelemetrySelectionStrategy getTlmSelecStrat(TelemetrySelectionAndAdjustmentOptions tlmOptions) {
        switch (config.getSampleSetBuildingStrategy()) {
            case SEPARATE_CONSECUTIVE_WINDOWS:
                return WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(config, tlmOptions);
            case SLIDING_WINDOW:
                return WindowingTelemetrySelectionStrategy.forSlidingWindow(config, tlmOptions);
            case SAMPLING:
                return new SamplingTelemetrySelectionStrategy(config, tlmOptions);
            default:
                throw new IllegalStateException("No such sample set building strategy: " + config.getSampleSetBuildingStrategy());
        }
    }

    private List<TimeCorrelationTarget> retrieveAllSamplesInRangeAsTcTargets(TelemetrySelectionAndAdjustmentOptions tlmOptions) throws MmtcException {
        final TelemetrySelectionStrategy tlmSelecStrat = getTlmSelecStrat(tlmOptions);

        // use the telemetry selection strategy (which includes enrichment, validation, and filtering) and ensure the results are sorted in ERT order
        return tlmSelecStrat.getAll(
                        tlmOptions.getResolvedTargetSampleErtRange().get().getStart(),
                        tlmOptions.getResolvedTargetSampleErtRange().get().getStop(),
                        (FilterFunction) this::processFilters
                )
                .stream()
                .sorted(Comparator.comparing(a -> TimeConvert.parseIsoDoyUtcStr(a.getTargetSample().getErtStr())))
                .collect(Collectors.toList());
    }

    /**
     * Run a set of samples through a configurable list of filters and return
     * the result.
     *
     * @param tcTarget the time correlation sample set and target frame for the filters to evaluate
     * @return true if the samples pass all filters, false otherwise
     * @throws MmtcException when the filters are not parsed
     *  correctly from config
     */
    private boolean processFilters(TimeCorrelationTarget tcTarget) throws MmtcException {
        // Apply all 'regular' filters
        for (Map.Entry<String, TimeCorrelationFilter> entry : config.getFilters().entrySet()) {
            String filterName = entry.getValue().getClass().getSimpleName();
            if (entry.getValue().process(tcTarget.getSampleSet(), config)) {
                logger.info("The candidate sample set passed the " + filterName);
            } else {
                logger.warn(USER_NOTICE, "The candidate sample set failed the " + filterName);
                return false;
            }
        }

        // Apply the Contact Filter, if enabled & possible
        if (config.isContactFilterDisabled()) {
            logger.info(USER_NOTICE, "Contact Filter is disabled either in configuration parameters or by command line option -F.");
        } else {
            // In this case, the lookBackRec is the latest record in the current (soon to be previous) SCLK kernel.
            final CorrelationTriplet lookBackRec;
            final int sclk_p;

            try {
                RunHistoryFile runHistoryFile = new RunHistoryFile(config.getRunHistoryFilePath(), config.getAllOutputProductDefs());
                lookBackRec = currentSclkKernel.getPriorRec(tcTarget.getTargetSampleTdtG(), 0.0, runHistoryFile.getSmoothingTripletTdtGValsToIgnoreDuringLookback());
                sclk_p = TimeConvert.encSclkToSclk(config.getNaifSpacecraftId(), sclk_kernel_fine_tick_modulus, lookBackRec.getEncSclk()).intValue();
            } catch (TextProductException | TimeConvertException e) {
                throw new MmtcException("Could not find or convert lookback record for Contact Filter in SCLK kernel", e);
            }

            if (sclk_p == 0) {
                logger.info(USER_NOTICE, "The prior record in the SCLK kernel is the initial/seed entry with SCLK = 0; therefore, the Contact Filter will not be run against this entry, and processing will continue.");
            } else {
                ContactFilter contactFilter = new ContactFilter();

                contactFilter.setEncSclk_previous(Double.toString(lookBackRec.getEncSclk()));
                try {
                    contactFilter.setTdt_g_previous(lookBackRec.getTdtCalStr());
                } catch (TimeConvertException e) {
                    throw new MmtcException(e);
                }
                contactFilter.setTdt_g_current(tcTarget.getTargetSampleTdtG());

                if (contactFilter.process(tcTarget.getTargetSample(), config, sclk_kernel_fine_tick_modulus)) {
                    logger.info("The candidate sample passed the ContactFilter.");
                } else {
                    logger.warn(USER_NOTICE, "The candidate sample failed the ContactFilter.");
                    return false;
                }
            }
        }

        return true;
    }
}
