package edu.jhuapl.sd.sig.mmtc.trending;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.trending.config.TrendingConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.filter.ContactFilter;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSampleAndMetrics;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.SamplingTelemetrySelectionStrategy;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionStrategy;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.WindowingTelemetrySelectionStrategy;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spice.basic.SpiceErrorException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class TrendingApp {
    private static final Logger logger = LogManager.getLogger();

    private final TrendingConfig config;
    private final TrendingContext ctx;

    // This is the SCLK modulus (i.e., fine time ticks per second) related to FrameSample.tkSclkFine.
    // It is used to compute TF Offset for use in clock change rate calculations.
    // This is set by the config key spacecraft.sclkModulusOverride, and if that key is not set, then the SCLK fine modulus from the loaded SCLK kernel is used.
    private int tk_sclk_fine_tick_modulus;

    // The SCLK modulus used to read & write values from/into the SCLK kernel and SCLK-SCET files.
    private int sclk_kernel_fine_tick_modulus = -1;


    public TrendingApp(String... args) throws Exception {
        this.config = new TrendingConfig();
        this.ctx = new TrendingContext(config);
        init();

        this.config.getTelemetrySource().connect();
    }

    private void init() throws Exception {
        logger.debug("Loading SPICE library and latest SCLK kernel");
        TimeConvert.loadSpiceLib();
        TimeConvert.loadSpiceKernels(config.getKernelsToLoad());
        TimeConvert.validateLoadedSclkKernels(config.getSpacecraftId());

        logger.info("SPICE kernels loaded:\n" + String.join("\n", TimeConvert.getLoadedKernelNames()));

        ctx.currentSclkKernel.set(SclkKernel.read(config.getInputSclkKernelPath()));

        this.tk_sclk_fine_tick_modulus = config.getTkSclkFineTickModulus(true);
        this.sclk_kernel_fine_tick_modulus = TimeConvert.getSclkKernelTickRate(config.getNaifSpacecraftId());
    }

    public void run() throws Exception {
        try {
            doRun();
        } finally {
            this.config.getTelemetrySource().disconnect();
        }
    }

    // all of these perform write-once mutations on the context
    private void doRun() throws Exception {
        setTrendingPeriod();

        retrieveTelemetryAndCalculateBasicStats();

        calculateStatsSinceLastCorrelation();

        // get SCLK-SCET error since last correlation, and subtract it from last correlation
        // get SCLK-SCET
        // ctx.statsSinceLastCorrelation.

    }
    
    private void calculateStatsSinceLastCorrelation() throws Exception {
        final List<FrameSampleAndMetrics> trendingTelemetry = ctx.trendingTelemetry.get();

/*
        OffsetDateTime lastCorrelationUtc = TimeConvert.tdtToUtc(ctx.currentSclkKernel.get().getLastTriplet().getTdt(), 6);
        ctx.statsSinceLastCorrelation.currentSclkKernelDescription.set(config.getInputSclkKernelPath().getFileName().toString());

        ctx.statsSinceLastCorrelation.currentSclkKernelLastTripletAgeDays.set(
                Duration.between(
                        lastCorrelationUtc,
                        ctx.appRunTime
                ).toDays()
        );
        ctx.lastCorrelationUtc.ertUtcForPriorCorrelation(lastCorrelationUtc);

        final FrameSampleAndMetrics mostRecentUsablePoint = trendingTelemetry.get(trendingTelemetry.size() - 1);
        ctx.statsSinceLastCorrelation.


 */

    }

    private void setTrendingPeriod() throws TimeConvertException {
        final OffsetDateTime latestErtFromSclkKernel = TimeConvert.tdtToUtc(
                ctx.currentSclkKernel.get().getTriplets().get(0).getTdt(),
                6
        );

        final OffsetDateTime minimumTrendingStart = this.ctx.appRunTime.minus(
                this.config.getMinimumTrendingPeriodDurationDays(), ChronoUnit.DAYS
        );

        // trend since the latest correlation in the SCLK kernel, and possibly longer if it doesn't satisfy the minimum trending length
        if (latestErtFromSclkKernel.isAfter(minimumTrendingStart)) {
            this.ctx.telemetryTrendingQueryPeriod.set(new OffsetDateTimeRange(minimumTrendingStart, this.ctx.appRunTime));
        } else {
            this.ctx.telemetryTrendingQueryPeriod.set(new OffsetDateTimeRange(latestErtFromSclkKernel, this.ctx.appRunTime));
        }

        this.config.setTrendingPeriod(this.ctx.telemetryTrendingQueryPeriod.get());
    }

    private void retrieveTelemetryAndCalculateBasicStats() throws MmtcException, SpiceErrorException, TimeConvertException {
        // gather 'enriched' FrameSamples that pass validation checks and filters
        final List<FrameSample> frameSamples = retrieveSamplesAsTcTargets().stream().map(TimeCorrelationTarget::getTargetSample).collect(Collectors.toList());

        final List<FrameSampleAndMetrics> frameSamplesAndMetrics = new ArrayList<>();

        for (FrameSample fs : frameSamples) {
            frameSamplesAndMetrics.add(
                    new FrameSampleAndMetrics(
                            fs,
                            TimeConvert.calculateFrameSampleMetrics(config, config, fs)
                    )
            );
        }

        ctx.trendingTelemetry.set(frameSamplesAndMetrics);
    }

    private List<TimeCorrelationTarget> retrieveSamplesAsTcTargets() throws MmtcException {
        // todo consider allow the specification of different sample set building strategies and filters for trending vs correlation
        // the configuration would override methods used to look up strategies, sample set size, etc.

        final TelemetrySource tlmSource = config.getTelemetrySource();
        final TelemetrySelectionStrategy tlmSelecStrat;

        switch (config.getSampleSetBuildingStrategy()) {
            case SEPARATE_CONSECUTIVE_WINDOWS:
                tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(config, config);
                break;
            case SLIDING_WINDOW:
                tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(config, config);
                break;
            case SAMPLING:
                tlmSelecStrat = new SamplingTelemetrySelectionStrategy(config, config);
                break;
            default:
                throw new IllegalStateException("No such sample set building strategy: " + config.getSampleSetBuildingStrategy());
        }

        logger.info(USER_NOTICE, "Querying and filtering for valid telemetry...");
        // ensure the results are sorted in ERT order
        return tlmSelecStrat.getAll(ctx.telemetryTrendingQueryPeriod.get().getStart(), ctx.telemetryTrendingQueryPeriod.get().getStop(), this::processFilters)
                .stream()
                .sorted((a, b) -> TimeConvert.parseIsoDoyUtcStr(a.getTargetSample().getErtStr()).compareTo(TimeConvert.parseIsoDoyUtcStr(b.getTargetSample().getErtStr())))
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
    protected boolean processFilters(TimeCorrelationTarget tcTarget) throws MmtcException {
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
                lookBackRec = ctx.currentSclkKernel.get().getPriorRec(tcTarget.getTargetSampleTdtG(), 0.0, runHistoryFile.getSmoothingTripletTdtGValsToIgnoreDuringLookback());
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
