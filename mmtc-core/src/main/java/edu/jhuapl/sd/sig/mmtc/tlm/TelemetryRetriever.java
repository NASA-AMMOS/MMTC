package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.cfg.app.TimekeepingAdjustmentParameters;
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

    public List<FrameSampleAndMetrics> retrieveTelemetryAndCalculateBasicStats(TelemetrySelectionAndAdjustmentOptions tlmOptions, boolean processFilters) throws MmtcException, SpiceErrorException, TimeConvertException {
        // gather 'enriched' FrameSamples that pass validation checks and filters
        final List<FrameSample> frameSamples = retrieveSamplesAsTcTargets(
                tlmOptions,
                processFilters
            ).stream()
            .map(TimeCorrelationTarget::getTargetSample)
            .collect(Collectors.toList());

        final List<FrameSampleAndMetrics> frameSamplesAndMetrics = new ArrayList<>();

        for (FrameSample fs : frameSamples) {
            frameSamplesAndMetrics.add(
                    new FrameSampleAndMetrics(
                            fs,
                            TimeConvert.calculateFrameSampleMetrics(config, tlmOptions, fs)
                    )
            );
        }

        return frameSamplesAndMetrics;
    }

    private List<TimeCorrelationTarget> retrieveSamplesAsTcTargets(TelemetrySelectionAndAdjustmentOptions tlmOptions, boolean processFilters) throws MmtcException {
        // todo consider allow the specification of different sample set building strategies and filters for trending vs correlation
        // the configuration would override methods used to look up strategies, sample set size, etc.

        final TelemetrySelectionStrategy tlmSelecStrat;

        switch (config.getSampleSetBuildingStrategy()) {
            case SEPARATE_CONSECUTIVE_WINDOWS:
                tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(config, tlmOptions);
                break;
            case SLIDING_WINDOW:
                tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(config, tlmOptions);
                break;
            case SAMPLING:
                tlmSelecStrat = new SamplingTelemetrySelectionStrategy(config, tlmOptions);
                break;
            default:
                throw new IllegalStateException("No such sample set building strategy: " + config.getSampleSetBuildingStrategy());
        }

        logger.info(USER_NOTICE, "Querying and filtering for valid telemetry...");

        // use the telemetry selection strategy (which includes enrichment, validation, and filtering) and ensure the results are sorted in ERT order
        return tlmSelecStrat.getAll(
                        tlmOptions.getResolvedTargetSampleRange().get().getStart(),
                        tlmOptions.getResolvedTargetSampleRange().get().getStop(),
                        (FilterFunction) timeCorrelationTarget -> {
                            if (processFilters) {
                                return processFilters(timeCorrelationTarget);
                            } else {
                                return true;
                            }
                        }
                )
                .stream()
                .sorted(Comparator.comparing(a -> TimeConvert.parseIsoDoyUtcStr(a.getTargetSample().getErtStr())))
                .collect(Collectors.toList());
    }

    /**
     * Run a set of samples through a configurable list of filters and return
     * the result.
     *
     * todo deduplicate this with the same code in TimeCorrelationApp
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
                logger.info(USER_NOTICE, "The candidate sample set passed the " + filterName);
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

            final RunHistoryFile runHistoryFile = new RunHistoryFile(config.getRunHistoryFilePath(), config.getAllOutputProductDefs());

            try {
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
                    logger.info(USER_NOTICE, "The candidate sample passed the ContactFilter.");
                } else {
                    logger.warn(USER_NOTICE, "The candidate sample failed the ContactFilter.");
                    return false;
                }
            }
        }

        return true;
    }
}
