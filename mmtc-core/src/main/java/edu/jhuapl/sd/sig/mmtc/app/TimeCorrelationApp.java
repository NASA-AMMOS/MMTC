package edu.jhuapl.sd.sig.mmtc.app;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.math.BigDecimal;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.filter.ContactFilter;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.model.*;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.SamplingTelemetrySelectionStrategy;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.WindowingTelemetrySelectionStrategy;
import edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionStrategy;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.appender.RollingFileAppender;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

/**
 * <p><strong>The Multi-Mission Time Correlation System (MMTC)</strong></p>
 * <p>Developed at the Johns Hopkins Applied Physics Laboratory in Laurel, Maryland</p>
 *
 * MMTC is described in:
 * <p><i>M. R. Reid and S. B. Cooper, "The Multi-mission Time Correlation System," 2019 IEEE International Conference on Space Mission Challenges for Information Technology (SMC-IT), Pasadena, CA, USA, 2019, pp. 41-46.
 * doi: 10.1109/SMC-IT.2019.00010</i></p>
 */
public class TimeCorrelationApp {
    private static final Logger logger = LogManager.getLogger();

    private final TimeCorrelationAppConfig config;
    private final TimeCorrelationContext ctx;

    private RunHistoryFile runHistoryFile;
    private TableRecord newRunHistoryFileRecord;

    // This is the SCLK modulus (i.e., fine time ticks per second) related to FrameSample.tkSclkFine.
    // It is used to compute TF Offset for use in clock change rate calculations.
    // This is set by the config key spacecraft.sclkModulusOverride, and if that key is not set, then the SCLK fine modulus from the loaded SCLK kernel is used.
    private int tk_sclk_fine_tick_modulus = -1;

    // The SCLK modulus used to read & write values from/into the SCLK kernel and SCLK-SCET files.
    private int sclk_kernel_fine_tick_modulus = -1;

    public TimeCorrelationApp(String... args) throws Exception {
        try {
            this.config = new TimeCorrelationAppConfig(args);
            this.ctx = new TimeCorrelationContext(config);
            init();
        } catch (Exception e) {
            throw new MmtcException("MMTC correlation initialization failed.", e);
        }
    }

    /**
     * Initialize the time correlation application by loading configuration and
     * the specified SPICE kernels. Load the SCLK kernel separately.
     */
    private void init() throws Exception {
        config.validate();

        logger.debug("Loading SPICE library");
        TimeConvert.loadSpiceLib();
        TimeConvert.loadSpiceKernels(config.getKernelsToLoad());

        logger.info("SPICE kernels loaded:\n" + String.join("\n", TimeConvert.getLoadedKernelNames()));

        {
            SclkKernel currentSclkKernel = new SclkKernel(config.getInputSclkKernelPath().toString());
            logger.info("Loaded SCLK kernel: " + currentSclkKernel.getPath() + ".");

            // Check that the SCLK is a 2-stage clock. Only 2-stage clocks are currently supported. The number of
            // stages is given in the SCLK01_N_FIELDS_nnn field of the SCLK Kernel.
            Integer numSclkStages = TimeConvert.getNumSclkStages(config.getNaifSpacecraftId());
            if (numSclkStages != 2) {
                throw new MmtcException(String.format(
                        "ERROR: SCLK Kernel variable SCLK01_N_FIELDS_nnn indicates an SCLK with %d stages. Only 2-stage clocks are supported by MMTC.", numSclkStages
                ));
            }
            currentSclkKernel.readSourceProduct();
            ctx.currentSclkKernel.set(currentSclkKernel);
        }

        runHistoryFile = new RunHistoryFile(config.getRunHistoryFilePath(), config.getAllOutputProductDefs());
        runHistoryFile.updateRowsForNewProducts();
        newRunHistoryFileRecord = new TableRecord(runHistoryFile.getHeaders());
        if (!ctx.config.isDryRun()) {
            recordRunHistoryFilePreRunValues();
        }

        tk_sclk_fine_tick_modulus = config.getTkSclkFineTickModulus();
        logger.info("tk_sclk_fine_tick_modulus set to: " + tk_sclk_fine_tick_modulus);
        ctx.tk_sclk_fine_tick_modulus.set(tk_sclk_fine_tick_modulus);

        sclk_kernel_fine_tick_modulus = TimeConvert.getSclkKernelTickRate(config.getNaifSpacecraftId());
        logger.info("sclk_kernel_fine_tick_modulus set to: " + sclk_kernel_fine_tick_modulus);
        ctx.sclk_kernel_fine_tick_modulus.set(sclk_kernel_fine_tick_modulus);
    }

    private TimeCorrelationTarget selectSampleSetAndTimeCorrelationTarget() throws MmtcException {
        final TelemetrySource tlmSource = config.getTelemetrySource();
        final TelemetrySelectionStrategy tlmSelecStrat;

        switch(config.getSampleSetBuildingStrategy()) {
            case SEPARATE_CONSECUTIVE_WINDOWS: tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(config, tlmSource, tk_sclk_fine_tick_modulus); break;
            case SLIDING_WINDOW: tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(config, tlmSource, tk_sclk_fine_tick_modulus); break;
            case SAMPLING: tlmSelecStrat = new SamplingTelemetrySelectionStrategy(config, tlmSource, tk_sclk_fine_tick_modulus); break;
            default:
                throw new IllegalStateException("No such sample set building strategy: " + config.getSampleSetBuildingStrategy());
        }

        tlmSource.connect();
        try {
            logger.info(USER_NOTICE, "Querying and filtering for valid telemetry...");
            return tlmSelecStrat.get(this::processFilters);
        } finally {
            tlmSource.disconnect();
        }
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
            final String[] lookBackRec;
            final int sclk_p;

            try {
                lookBackRec = ctx.currentSclkKernel.get().getPriorRec(tcTarget.getTargetSampleTdtG(), 0.0);
                sclk_p = TimeConvert.encSclkToSclk(config.getNaifSpacecraftId(), sclk_kernel_fine_tick_modulus, Double.parseDouble(lookBackRec[SclkKernel.ENCSCLK])).intValue();
            } catch (TextProductException | TimeConvertException e) {
                throw new MmtcException("Could not find or convert lookback record for Contact Filter in SCLK kernel", e);
            }

            if (sclk_p == 0) {
                logger.info(USER_NOTICE, "The prior record in the SCLK kernel is the initial/seed entry with SCLK = 0; therefore, the Contact Filter will not be run against this entry, and processing will continue.");
            } else {
                ContactFilter contactFilter = new ContactFilter();

                contactFilter.setEncSclk_previous(lookBackRec[SclkKernel.ENCSCLK]);
                contactFilter.setTdt_g_previous(lookBackRec[SclkKernel.TDTG]);
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

    /**
     * Gets the states (last line or most recent text product file) of MMTC's output products and adds them to the runHistoryFileRecord.
     * Meant to be run after all output product objects have been initialized but before they've been modified by a successful run.
     */
    private void recordRunHistoryFilePreRunValues() throws MmtcException {
        final int newRunId;
        {
            List<TableRecord> prevRuns = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS);

            if (prevRuns.isEmpty()) {
                newRunId = 1;
            } else {
                newRunId = Integer.parseInt(prevRuns.get(prevRuns.size() - 1).getValue("Run ID")) + 1;
            }
        }

        ctx.runId.set(newRunId);

        // Run info
        newRunHistoryFileRecord.setValue(RunHistoryFile.RUN_TIME,              ctx.appRunTime.toString());
        newRunHistoryFileRecord.setValue(RunHistoryFile.RUN_ID,                String.format("%05d",newRunId));
        newRunHistoryFileRecord.setValue(RunHistoryFile.ROLLEDBACK,            "false");
        newRunHistoryFileRecord.setValue(RunHistoryFile.RUN_USER,              System.getProperty("user.name"));
        newRunHistoryFileRecord.setValue(RunHistoryFile.CLI_ARGS,              String.join(" ", config.getCliArgs()));

        // Output products
        for (OutputProductDefinition<?> prodDef : config.getAllOutputProductDefs()) {
            final String preRunProdColName = RunHistoryFile.getPreRunProductColNameFor(prodDef);
            final String postRunProdColName = RunHistoryFile.getPostRunProductColNameFor(prodDef);

            if (prodDef instanceof SclkKernelProductDefinition) {
                // handle the SCLK kernel uniquely, as the seed kernel is already in place before any MMTC run is executed
                newRunHistoryFileRecord.setValue(preRunProdColName, ctx.currentSclkKernel.get().getVersionString(config.getSclkKernelBasename(), config.getSclkKernelSeparator()));
            } else {
                // all the other products are only created after at least a single run of MMTC, so we can reuse the postrun values from the prior run here
                newRunHistoryFileRecord.setValue(preRunProdColName, runHistoryFile.getLatestNonEmptyValueOfCol(postRunProdColName, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).orElse("-"));
            }
        }
    }

    /**
     * Compute the clock change rate. This is the critical calculation that provides
     * the clock change rate between successive runs of the MMTC. It is computed as
     * follows:
     * <p><i>CLKRATE = (TDT(G1) - TDT(G0)) / (SCLK1 - SCLK0)</i></p>
     * where:
     * <ul>
     * <li> TDT(G0) is the ground time of the last time correlation in the input SCLK Kernel.
     * <li> SCLK0 is the SCLK time of the last time correlation in the input SCLK Kernel (converted from encoded SCLK).
     * <li> TDT(G1) is the ground time computed in the current time correlation
     * <li> SCLK1 is the SCLK time used in the current time correlation
     * </ul>
     *
     * @param sclk0  IN the previous coarse SCLK value
     * @param tdt_g0 IN the previous TDT(G) ground time in seconds of epoch
     * @param sclk1  IN the current coarse SCLK value
     * @param tdt_g1 IN the current TDT(G) ground time in seconds of epoch
     * @return the clock change rate in TDT seconds per SCLK seconds
     */
    private static Double computeClkChgRate(int sclk0, double tdt_g0, int sclk1, double tdt_g1) {
        logger.debug(String.format("computeClkChgRate initial call: (sclk0=%d,tdt_g0=%.8f,sclk1=%d,tdt_g1=%.8f", sclk0, tdt_g0, sclk1, tdt_g1));

        // Convert TDT(G) values to high-precision values at the microsecond-level precision, as this is the actual maximum precision that the DSN will provide
        BigDecimal tdt_g0L = new BigDecimal(tdt_g0);
        tdt_g0L = tdt_g0L.setScale(6, RoundingMode.HALF_UP);

        BigDecimal tdt_g1L = new BigDecimal(tdt_g1);
        tdt_g1L = tdt_g1L.setScale(6, RoundingMode.HALF_UP);

        // Compute delta TDT(G)
        BigDecimal delta_tdt = tdt_g1L.subtract(tdt_g0L);

        // Convert the SCLK values to high-precision values.
        BigInteger sclk0L  = new BigInteger(String.valueOf(sclk0));
        BigInteger sclk1L  = new BigInteger(String.valueOf(sclk1));

        logger.debug(String.format("computeClkChgRate post-rounding: (sclk0L=%d,tdt_g0L=%.8f,sclk1L=%d,tdt_g1L=%.8f", sclk0L, tdt_g0L, sclk1L, tdt_g1L));

        // Compute delta SCLK
        BigInteger delta_sclk = sclk1L.subtract(sclk0L);

        logger.debug(String.format("computeClkChgRate delta_sclk=%d", delta_sclk));

        // Compute the clock change rate.
        BigDecimal chgRate = delta_tdt.divide(new BigDecimal(delta_sclk), 11, RoundingMode.HALF_UP);

        Double clkChgRate = chgRate.doubleValue();
        logger.debug("computeClkChgRate: clkChgRate = " + clkChgRate);

        return clkChgRate;
    }


    /**
     * Compute the predicted clock change rate. This method compares the newly
     * computed TDT(G) and SCLK values with those of a record in the SCLK
     * kernel that is a set amount of time prior to that of the new TDT(G).
     * The amount of look-back time is read from configuration parameters.
     *
     * @param sclk    IN the new coarse SCLK value
     * @param tdt_g   IN the new TDT(G) value
     * @return the predicted clock change rate
     * @throws TextProductException if the previous SCLK kernel values could not be obtained
     * @throws TimeConvertException if the TDT values could not be computed
     * @throws MmtcException if the new SCLK or TDT values overlap a previous time correlation
     */
    private Double computePredictedClkChgRate(Integer sclk, Double tdt_g) throws TextProductException, TimeConvertException, MmtcException {
        String[] lookBackRec = ctx.currentSclkKernel.get().getPriorRec(tdt_g, config.getPredictedClkRateLookBackDays()*24.);

        logger.debug("computePredictedClkChgRate(): lookBackRec from SCLK = " +
                lookBackRec[SclkKernel.ENCSCLK] + " " + lookBackRec[SclkKernel.TDTG] + " "
                + lookBackRec[SclkKernel.CLKCHGRATE]);
        String tdtGStr = TimeConvert.tdtToTdtStr(tdt_g);
        logger.debug("computePredictedClkChgRate(): New TDT(G) = " + tdtGStr + ".");

        Double priorEncSclk  = Double.parseDouble(lookBackRec[SclkKernel.ENCSCLK]);
        int naifScId         = config.getNaifSpacecraftId();
        int sclk0            = TimeConvert.encSclkToSclk(naifScId, sclk_kernel_fine_tick_modulus, priorEncSclk).intValue();
        Double tdt_g0        = TimeConvert.tdtStrToTdt(lookBackRec[SclkKernel.TDTG].substring(1));

        logger.debug("computePredictedClkChgRate(): sclk0 = " + sclk0 + ", tdt_g0 = " + tdt_g0 + ", sclk = " + sclk + ", tdt_g = " + tdt_g);

        // Check that the selected look back record from the input SCLK Kernel is not older than the time of the current
        // sample minus the maximum number of look back hours as specified in the configuration parameters.  If it is,
        // throw an exception. Processing should not continue. The user should check that the input SCLK Kernel is
        // correct for the run or rerun in Assign (--clkchgrate-assign) or No-Drift (--clkchgrate-nodrift) modes to
        // compute the CLKRATE.
        final double deltaTDT = tdt_g - tdt_g0;
        logger.debug("computePredictedClkChgRate(): The look back record from the SCLK Kernel used to compute the " +
                " Predicted CLKRATE is " + lookBackRec[SclkKernel.TDTG] + ",\nwhich is " + deltaTDT/3600 + " hours earlier than the current TDT(G) of " +
                tdtGStr + ". Processing continues.");

        if (deltaTDT > (config.getMaxPredictedClkRateLookBackHours()) * 3600.) {
            String errorMsg = "Insufficient earlier data in the input SCLK Kernel to compute the Predicted CLKRATE. ";
            errorMsg       += "The most recent lookback record in the input SCLK Kernel is at TDT(G) = " + lookBackRec[SclkKernel.TDTG].substring(1) + ",";
            errorMsg       += "which is " + deltaTDT/3600 + " hours older than the new record being generated. ";
            errorMsg       += "The most recent lookback record is determined using a combination of the lookback and max lookback configuration values, and may not necessarily be the most recent entry in the latest SCLK Kernel. ";
            errorMsg       += String.format("However, the maximum allowable difference specified by the compute.tdtG.rate.predicted.maxLookBackDays configuration option is %d hours. ", config.getMaxPredictedClkRateLookBackHours());
            errorMsg       += "Please consider rerunning MMTC with either the --clkchgrate-assign or --clkchgrate-nodrift mode selected, or rerun within a different time period.";

            throw new MmtcException(errorMsg);
        }

        return computeClkChgRate(sclk0, tdt_g0, sclk, tdt_g);
    }


    /**
     * Compute the interpolated clock change rate. This method compares the
     * newly computed TDT(G) and SCLK values with those of the last record in
     * the current SCLK kernel.
     *
     * @param sclk    IN the new coarse SCLK value
     * @param tdt_g   IN the new TDT(G) value
     * @return the interpolated clock change rate
     * @throws TextProductException if the previous SCLK kernel values could not be obtained
     * @throws TimeConvertException if the TDT values could not be computed
     * @throws MmtcException if the input SCLK or TDT overlaps a previous time correlation
     */
    private Double computeInterpolatedClkChgRate(Integer sclk, Double tdt_g) throws TextProductException, TimeConvertException, MmtcException {
        final SclkKernel currentSclkKernel = ctx.currentSclkKernel.get();

        logger.debug("computeInterpolatedClkChgRate(): Last rec in existing SCLK kernel = " +
                currentSclkKernel.getLastRecValue(SclkKernel.ENCSCLK) + " " +
                currentSclkKernel.getLastRecValue(SclkKernel.TDTG) + " " +
                currentSclkKernel.getLastRecValue(SclkKernel.CLKCHGRATE));

        int naifScId = config.getNaifSpacecraftId();

        // Get parameters from the last record in the existing SCLK kernel.
        String existingKernelEncSclkStr = currentSclkKernel.getLastRecValue(SclkKernel.ENCSCLK);
        String existingKernelTdt_gStr   = currentSclkKernel.getLastRecValue(SclkKernel.TDTG);

        double priorEncSclk = Double.parseDouble(existingKernelEncSclkStr);
        int sclk0           = TimeConvert.encSclkToSclk(naifScId, sclk_kernel_fine_tick_modulus, priorEncSclk).intValue();
        double tdt_g0       = TimeConvert.tdtStrToTdt(existingKernelTdt_gStr.replace("@", ""));

        logger.debug("computeInterpolatedClkChgRate(): existingKernelEncSclkStr = " + existingKernelEncSclkStr);
        logger.debug("computeInterpolatedClkChgRate(): existingKernelTdt_gStr = " + existingKernelTdt_gStr);
        logger.debug("computeInterpolatedClkChgRate(): sclk0 = " + sclk0 + ", tdt_g0 = " + tdt_g0 +
                ", sclk = " + sclk + ", tdt_g = " + tdt_g);

        return computeClkChgRate(sclk0, tdt_g0, sclk, tdt_g);
    }


    /**
     * Perform the new time correlation run
     *
     * @throws Exception if time correlation cannot be successfully completed
     */
    public void run() throws Exception {
        logger.info(USER_NOTICE, String.format("Running time correlation between %s and %s", config.getStartTime().toString(), config.getStopTime().toString()));
        if (config.isTestMode()) {
            logger.warn(String.format("Test mode is enabled! One-way light time will be set to the provided value %f and ancillary positional and velocity calculations will be skipped.", config.getTestModeOwlt()));
        }
        if (config.isDryRun()) {
            logger.warn("Dry run mode is enabled! No data products from this run will be kept and will instead be printed to the console and recorded in the log file according to log4j2.xml");
        }


        // Select telemetry for this new time correlation run
        final TimeCorrelationTarget tcTarget = selectSampleSetAndTimeCorrelationTarget();
        ctx.correlation.target.set(tcTarget);

        // Calculate and set SCET (UTC) values that correspond to the target's TDT(G)
        {
            final String tdtGStr = TimeConvert.tdtToTdtStr(tcTarget.getTargetSampleTdtG());
            ctx.correlation.equivalent_scet_utc_for_tdt_g_iso_doy.set(TimeConvert.tdtStrToUtc(tdtGStr, 6)); // set to ms precision
            ctx.correlation.equivalent_scet_utc_for_tdt_g.set(TimeConvert.parseIsoDoyUtcStr(ctx.correlation.equivalent_scet_utc_for_tdt_g_iso_doy.get()));
        }

        // Log general information about the telemetry that will be used for this time correlation run
        {
            logger.debug("Sample set:");
            tcTarget.getSampleSet().forEach(logger::debug);

            String vcidMsg = "VCIDs in sample set: ";
            for (FrameSample sample : tcTarget.getSampleSet()) {
                vcidMsg += sample.getVcid() + ", ";
            }
            logger.debug(vcidMsg.substring(0, vcidMsg.length() - 2));

            logger.info(USER_NOTICE, "--- Target and supplemental frame summary ---");
            logger.info(USER_NOTICE, tcTarget.getTargetSample().toSummaryString(tcTarget.getTargetSampleGroundStationId()));
            logger.info(USER_NOTICE, "---------------------------------------------");

            logger.debug("TargetSample:\n");
            logger.debug(tcTarget.getTargetSample().toString());

            {
                final double owlt = tcTarget.getTargetSampleOwlt();
                if (config.isTestMode()) {
                    // In test mode, a user may supply the OWLT rather than having the application compute it from ephemeris.
                    // There are test environment scenarios where a user might want to supply a negative OWLT to make test
                    // data align, so the application allows this. A negative OWLT should NEVER occur operationally.
                    if (owlt < 0.) {
                        logger.warn("Invalid input. Input OWLT for test mode is negative! Processing continues, but results will be erroneous!");
                    }
                } else {
                    logger.debug("Computed OWLT (sec): " + owlt);
                }
                ctx.correlation.owlt_sec.set(owlt);
            }

            logger.debug("Encoded SCLK from supplemental sample coarse TK SCLK: " + tcTarget.getTargetSampleEncSclk());
            logger.debug(tcTarget.getTargetSampleErtGCalcLogStatement());
            logger.debug("ET(G) = " + tcTarget.getTargetSampleEtG());
            logger.debug("TDT(G) value computed using ET(G): " + tcTarget.getTargetSampleTdtG());
        }

        // Ensure we're adding records that have strictly increasing TDT(G) and SCLK values
        ensureIncreasingTdtAndSclkCorrelationValues(ctx);

        // Determine clock change rate mode, and calculate the appropriate rate(s)
        {
            final int curr_sclk_coarse = tcTarget.getTargetSample().getTkSclkCoarse();
            final double curr_tdt_g = tcTarget.getTargetSampleTdtG();
            final double predictedClockChangeRate;

            final TimeCorrelationAppConfig.ClockChangeRateMode actualClockChangeRateMode;
            if (config.getClockChangeRateMode().equals(TimeCorrelationAppConfig.ClockChangeRateMode.COMPUTE_INTERPOLATED) && ctx.currentSclkKernel.get().getSourceProductDataRecCount() == 1) {
                /*
                 * If this is the very first run of the application for a mission, the input SCLK Kernel is assumed to be the seed kernel.
                 * In this case and ONLY in this case, only compute the predicted clock change rate value, so as
                 * not to overwrite the seed kernel CLKRATE with an interpolated value. Typically, the user would select ASSIGN or NO_DRIFT as the
                 * CLKRATE method anyway for the first few runs.
                 */
                logger.warn("Not computing interpolated rate for prior SCLK kernel record so as not to overwrite seed kernel entry; switching clock change rate mode to compute-predicted");
                actualClockChangeRateMode = TimeCorrelationAppConfig.ClockChangeRateMode.COMPUTE_PREDICTED;
            } else {
                actualClockChangeRateMode = config.getClockChangeRateMode();
            }

            logger.info("Using clock change rate mode: " + actualClockChangeRateMode);
            ctx.correlation.actual_clock_change_rate_mode.set(actualClockChangeRateMode);
            switch (actualClockChangeRateMode) {
                case NO_DRIFT:
                    predictedClockChangeRate = 1.0;
                    break;
                case ASSIGN_KEY:
                case ASSIGN:
                    predictedClockChangeRate = config.getClockChangeRateAssignedValue();
                    break;
                case COMPUTE_INTERPOLATED:
                    ctx.correlation.interpolated_clock_change_rate.set(computeInterpolatedClkChgRate(curr_sclk_coarse, curr_tdt_g));
                    // purposeful fall-through to also compute the predicted (forward-looking) clock change rate
                case COMPUTE_PREDICTED:
                    predictedClockChangeRate = computePredictedClkChgRate(tcTarget.getTargetSample().getTkSclkCoarse(), curr_tdt_g);

                    // Compute and record the drift rate of the SCLK counter in ms/day
                    ctx.correlation.sclk_drift_ms_per_day.set(((1.0 / predictedClockChangeRate) - 1.0) * TimeConvert.SECONDS_PER_DAY * TimeConvert.MSEC_PER_SECOND);

                    break;
                default:
                    throw new MmtcException("Invalid clock change rate method selected.");
            }

            ctx.correlation.predicted_clock_change_rate.set(predictedClockChangeRate);
        }

        // Perform all ancillary post-correlation operations
        new TimeCorrelationAncillaryOperations(ctx).perform();

        // Write or log all output products
        ctx.newSclkVersionString.set(getNextSclkKernelVersionString());
        for (OutputProductDefinition<?> prodDef : config.getAllOutputProductDefs()) {
            final String postRunColProdColName = RunHistoryFile.getPostRunProductColNameFor(prodDef);

            if (prodDef.shouldBeWritten(ctx) && !ctx.config.isDryRun()) {
                final ProductWriteResult res = prodDef.write(ctx);
                newRunHistoryFileRecord.setValue(postRunColProdColName, res.newVersion);

            } else if (prodDef.shouldBeWritten(ctx) && ctx.config.isDryRun()) {
                // Log/print output products instead of writing them to files
                final String productPrintout = prodDef.getDryRunPrintout(ctx);
                logger.info(USER_NOTICE, productPrintout);
            } else {
                newRunHistoryFileRecord.setValue(postRunColProdColName,  runHistoryFile.getLatestNonEmptyValueOfCol(postRunColProdColName, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).orElse("-"));
            }
        }

        // Update run history file if this isn't a dry run. If it is, delete the previously created temp SCLK kernel.
        if (!ctx.config.isDryRun()) {
            runHistoryFile.writeRecord(newRunHistoryFileRecord);
            logger.info(USER_NOTICE, "Appended a new entry to Run History File located at " + runHistoryFile.getPath());
            logger.info(String.format("Run at %s recorded to %s", ctx.appRunTime, config.getRunHistoryFilePath().toString()));
        }

        logger.info(USER_NOTICE, "MMTC completed successfully.");
    }

    private static void ensureIncreasingTdtAndSclkCorrelationValues(TimeCorrelationContext ctx) throws TextProductException, TimeConvertException, MmtcException {
        final TimeCorrelationTarget tcTarget = ctx.correlation.target.get();
        final double targetTdtG = tcTarget.getTargetSampleTdtG();

        final String prevTdtGStr = ctx.currentSclkKernel.get().getLastRecValue(SclkKernel.TDTG);
        final double prevTdtG;
        if (SclkKernel.isNumVal(prevTdtGStr)) {
            prevTdtG = Double.parseDouble(prevTdtGStr);
        } else {
            prevTdtG = TimeConvert.tdtStrToTdt(prevTdtGStr.replace("@", ""));
        }

        if (!(targetTdtG > prevTdtG)) {
            throw new MmtcException(String.format(
                    "Error: the target sample has an earlier or equal TDT(G) as compared to the last triplet in the input SCLK kernel; new correlations must have subsequent SCLK and TDT(G) values that are strictly increasing. Target sample TDT(G): %s; SCLK kernel's latest TDT(G): %s",
                    TimeConvert.tdtToTdtStr(targetTdtG),
                    prevTdtGStr
            ));
        }

        final double prevEncSclk = Double.parseDouble(ctx.currentSclkKernel.get().getLastRecValue(SclkKernel.ENCSCLK));
        if (!(tcTarget.getTargetSampleEncSclk() > prevEncSclk)) {
            throw new MmtcException(String.format(
                    "Error: the target sample has an earlier or equal SCLK as compared to the last triplet in the input SCLK kernel; new correlations must have subsequent SCLK and TDT(G) values that are strictly increasing. Target sample enc SCLK: %f; SCLK kernel's latest enc SCLK: %f",
                    tcTarget.getTargetSampleEncSclk(),
                    prevEncSclk
            ));
        }
    }

    private String getNextSclkKernelVersionString() {
        boolean runHistoryRecordExists;
        Map<String, String> lastRunHistoryRecord;
        String currentSclkCounterStr = "";

        try {
            lastRunHistoryRecord = runHistoryFile.readLastRecord();
            currentSclkCounterStr = lastRunHistoryRecord.get("Latest SCLK Kernel Post-run");
            runHistoryRecordExists = true;
        } catch (MmtcException e) {
            runHistoryRecordExists = false;
        }

        if(!config.generateUniqueKernelCounters() || !runHistoryRecordExists) {
            return constructNextSclkKernelCounter(
                    ctx.currentSclkKernel.get().getName(),
                    config.getSclkKernelSeparator(),
                    SclkKernel.FILE_SUFFIX
            );
        } else {
            return StringUtils.leftPad(String.valueOf(
                            Integer.parseInt(currentSclkCounterStr)+1),
                    currentSclkCounterStr.length(),
                    "0"
            );
        }
    }

    /**
     * Given the current SCLK kernel filename, extracts the version counter and constructs a new counter that is
     * incremented by 1.
     *
     * @param currentName The filename of the current SCLK kernel
     * @param separator The character used to separate the counter from the rest of the filename
     * @param suffix The filename's suffix/extension, including the preceding period character
     * @return The new version counter, which is incremented by 1 from the current SCLK kernel's version counter
     */
    public static String constructNextSclkKernelCounter(String currentName, String separator, String suffix)  {
        // The basename is everything before the suffix. If the suffix isn't present, this throws IndexOutOfBounds.
        String basename = currentName.substring(0, currentName.lastIndexOf(suffix));

        // The version counter is everything in the basename after the last occurrence of the separator. If the
        // separator isn't present, this throws IndexOutOfBounds.
        // By the way, substring(1) doesn't work if separator is multibyte character. Try String.offsetByCodePoints()?
        String currentVersionStr = basename.substring(basename.lastIndexOf(separator)).substring(1);

        // Increment the version counter. If it's not an integer, this throws NumberFormatException.
        int currentVersion = Integer.parseInt(currentVersionStr);
        int nextVersion = currentVersion + 1;
        String nextVersionStr = String.valueOf(nextVersion);

        // If the current counter contains leading zeroes, then use leading zeroes in the new counter as well.
        if (currentVersionStr.charAt(0) == '0') {
            nextVersionStr = StringUtils.leftPad(nextVersionStr, currentVersionStr.length(), "0");
        }

        return nextVersionStr;
    }
}