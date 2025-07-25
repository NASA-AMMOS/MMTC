package edu.jhuapl.sd.sig.mmtc.app;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig.ClockChangeRateMode;
import edu.jhuapl.sd.sig.mmtc.filter.ContactFilter;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.products.*;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.table.*;
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
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

/**
 * <p><strong>The Multi-Mission Time Correlation System (MMTC)</strong></p>
 * <p>Developed at the Johns Hopkins Applied Physics Laboratory in Laurel, Maryland</p>
 * TimeCorrelationApp is the top-level class in the MMTC application.
 *
 * MMTC is described in:
 * <p><i>M. R. Reid and S. B. Cooper, "The Multi-mission Time Correlation System," 2019 IEEE International Conference on Space Mission Challenges for Information Technology (SMC-IT), Pasadena, CA, USA, 2019, pp. 41-46.
 * doi: 10.1109/SMC-IT.2019.00010</i></p>
 */
public class TimeCorrelationApp {
    public static final Marker USER_NOTICE = MarkerManager.getMarker("USER_NOTICE");

    public static final String MMTC_TITLE = "Multi-Mission Time Correlation (MMTC)";
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    private static final Logger logger = LogManager.getLogger();

    private final OffsetDateTime appRunTime = OffsetDateTime.now(ZoneOffset.UTC);
    private final String[] cliArgs;
    private final List<String> timeHistoryFileWarnings = new ArrayList<>();

    private final TimeCorrelationAppConfig config;
    private final TelemetrySource tlmSource;

    private RunHistoryFile runHistoryFile;
    private RawTelemetryTable rawTlmTable;
    private TimeHistoryFile timeHistoryFile;
    private FrameSample targetSample;
    private TableRecord runHistoryFileRecord;
    private TableRecord timeHistoryFileRecord;
    private SclkKernel currentSclkKernel;
    private SclkKernel newSclkKernel;
    private Map<String, TimeCorrelationFilter> filters;

    // This is the SCLK modulus (i.e., fine time ticks per second) related to FrameSample.tkSclkFine.
    // It is used to compute TF Offset for use in clock change rate calculations.
    // This is set by the config key spacecraft.sclkModulusOverride, and if that key is not set, then the SCLK fine modulus from the loaded SCLK kernel is used.
    private int tk_sclk_fine_tick_modulus = -1;

    // The SCLK modulus used to read & write values from/into the SCLK kernel and SCLK-SCET files.
    private int sclk_kernel_fine_tick_modulus = -1;

    // The subseconds offset from the frame whole SCLK.
    private double tf_offset = Double.NaN;

    private ClockChangeRateMode actualClkChgRateMode;

    // The version number of the new SCLK Kernel and new SCLK/SCET file in the filenames.
    private String newVersionStr = "";

    /**
     * Create the configuration objects.
     *
     * @param args the incoming command line arguments
     */
    private TimeCorrelationApp(String[] args) throws Exception {
        config = new TimeCorrelationAppConfig(args);
        tlmSource = config.getTelemetrySource();
        cliArgs = args;
    }

    /**
     * Initialize the time correlation application by loading configuration and
     * the specified SPICE kernels. Load the SCLK kernel separately.
     *
     * @return true if successful, false otherwise
     */
    private void init() throws Exception {
        config.validate();

        logger.debug("Loading SPICE library");
        TimeConvert.loadSpiceLib();
        TimeConvert.loadSpiceKernels(config.getKernelsToLoad());

        logger.info("SPICE kernels loaded:\n" + String.join("\n", TimeConvert.getLoadedKernelNames()));

        currentSclkKernel = new SclkKernel(config.getSclkKernelPath().toString());
        logger.info("Loaded SCLK kernel: " + currentSclkKernel.getPath() + ".");

        // Check that the SCLK is a 2-stage clock. Only 2-stage clocks are currently supported. The number of
        // stages is given in the SCLK01_N_FIELDS_nnn field of the SCLK Kernel.
        Integer numSclkStages = TimeConvert.getNumSclkStages(config.getNaifSpacecraftId());
        if (numSclkStages != 2) {
            throw new MmtcException(String.format(
                    "ERROR: SCLK Kernel variable SCLK01_N_FIELDS_nnn indicates an SCLK with %d stages. Only 2-stage SCLK clocks are supported.", numSclkStages
            ));
        }
        currentSclkKernel.readSourceProduct();

        rawTlmTable = new RawTelemetryTable(config.getRawTelemetryTableUri());

        timeHistoryFile = new TimeHistoryFile(config.getTimeHistoryFileUri(), config.getTimeHistoryFileExcludeColumns());
        timeHistoryFileRecord = new TableRecord(timeHistoryFile.getHeaders());

        runHistoryFile = new RunHistoryFile(config.getRunHistoryFileUri());
        runHistoryFileRecord = new TableRecord(runHistoryFile.getHeaders());
        recordRunHistoryFileBeforeValues();

        filters = config.getFilters();

        tk_sclk_fine_tick_modulus = config.getTkSclkFineTickModulus();
        logger.info("tk_sclk_fine_tick_modulus set to: " + tk_sclk_fine_tick_modulus);

        sclk_kernel_fine_tick_modulus = TimeConvert.getSclkKernelTickRate(config.getNaifSpacecraftId());
        logger.info("sclk_kernel_fine_tick_modulus set to: " + sclk_kernel_fine_tick_modulus);
    }

    private TimeCorrelationTarget buildSampleSet() throws MmtcException {
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
        for (Map.Entry<String, TimeCorrelationFilter> entry : filters.entrySet()) {
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
                lookBackRec = currentSclkKernel.getPriorRec(tcTarget.getTargetSampleTdtG(), 0.0);
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
     * Write the sample set to the raw telemetry table.
     *
     * @param samples the current sample set
     * @throws MmtcException when a record cannot be written
     */
    private void writeRawTelemetryTable(List<FrameSample> samples) throws MmtcException {
        for (FrameSample sample : samples) {
            TableRecord rec = sample.toRawTelemetryTableRecord(rawTlmTable.getHeaders());
            rec.setValue(RawTelemetryTable.RUN_TIME, appRunTime.toString());
            rawTlmTable.writeRecord(rec);
        }

        logger.info(USER_NOTICE, "Appended new entries to Raw Telemetry Table located at " + rawTlmTable.getPath());
    }

    /**
     * Write the next record to the time history file.
     *
     * @throws MmtcException when a failure occurs on write
     * @throws TimeConvertException if a time conversion operation failed
     */
    private void writeTimeHistoryFile(OffsetDateTime scet_tdt_g, TimeCorrelationTarget tcTarget, Double currentPredictedClockChangeRate) throws MmtcException, TimeConvertException, TextProductException {
        final int sclkPartition = config.getSclkPartition(TimeConvert.parseIsoDoyUtcStr(targetSample.getErtStr()));

        final String scId = String.valueOf(config.getNaifSpacecraftId());

        timeHistoryFileRecord.setValue(TimeHistoryFile.RUN_TIME,                      appRunTime.toString());
        timeHistoryFileRecord.setValue(TimeHistoryFile.SCLK_PARTITION,                String.valueOf(sclkPartition));
        timeHistoryFileRecord.setValue(TimeHistoryFile.RF_ENCODING,                   targetSample.getTkRfEncoding());
        timeHistoryFileRecord.setValue(TimeHistoryFile.STATION_ID,                    config.getStationId(targetSample.getPathId()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.DATA_RATE_BPS,                 targetSample.getTkDataRateBpsAsRoundedString());
        timeHistoryFileRecord.setValue(TimeHistoryFile.CLK_CHANGE_RATE_INTERVAL_DAYS, String.format("%.2f", config.getPredictedClkRateLookBackDays()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.SPACECRAFT_TIME_DELAY,         String.valueOf(config.getSpacecraftTimeDelaySec()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.BITRATE_TIME_ERROR,            String.valueOf(targetSample.getDerivedTdBe()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.TF_OFFSET,                     String.valueOf(tf_offset));

        switch (actualClkChgRateMode) {
            case COMPUTE_INTERPOLATED:
                timeHistoryFileRecord.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "I");
                break;
            case COMPUTE_PREDICTED:
                timeHistoryFileRecord.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "P");
                break;
            case ASSIGN:
            case ASSIGN_KEY:
                timeHistoryFileRecord.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "A");
                break;
            case NO_DRIFT:
                timeHistoryFileRecord.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "N");
                break;
            default:
                break;
        }

        double scSunDistKm = Double.NaN;
        double scEarthDistKm = Double.NaN;
        double scVelSsbKmS = Double.NaN;
        double scVelEarthKmS = Double.NaN;
        double earthVelSsbKmS = Double.NaN;
        double earthSunDistKm = Double.NaN;

        double scSunDistAu = Double.NaN;
        double earthSunDistAu = Double.NaN;

        // If in test mode, ephemeris data might not be available, so don't try to compute spacecraft state vectors
        if (config.isTestMode()) {
            logger.warn("MMTC is operating in test mode. Time history spacecraft and Earth distance and velocity values will not be computed. They will be recorded as NaN.");
        } else {
            scSunDistKm = computeDistanceKm(scId, "SUN");
            scEarthDistKm = computeDistanceKm(scId, "EARTH");
            scVelSsbKmS = computeVelocityKmS(scId, "SSB");
            scVelEarthKmS = computeVelocityKmS(scId, "EARTH");

            try {
                scSunDistAu = CSPICE.convrt(scSunDistKm, "KM", "AU");
            }
            catch (SpiceErrorException ex) {
                logger.warn("Unable to convert SC-Sun distance to AU: " + ex.getMessage());
                scSunDistAu = -1.0;
            }

        }

        // Compute the Earth-Sun state vectors.
        earthVelSsbKmS = computeVelocityKmS("EARTH", "SSB");
        earthSunDistKm = computeDistanceKm("EARTH", "SUN");

        try {
            earthSunDistAu = CSPICE.convrt(earthSunDistKm, "KM", "AU");
        }
        catch (SpiceErrorException ex) {
            logger.warn("Unable to convert Earth-Sun distance to AU: " + ex.getMessage());
            earthSunDistAu = Double.NaN;
        }

        timeHistoryFileRecord.setValue(TimeHistoryFile.SC_SUN_DIST_KM,            String.valueOf(Double.isNaN(scSunDistKm) ? scSunDistKm : (int)scSunDistKm));
        timeHistoryFileRecord.setValue(TimeHistoryFile.SC_SUN_DIST_AU,            String.format("%.6f", scSunDistAu));
        timeHistoryFileRecord.setValue(TimeHistoryFile.SC_EARTH_DIST_KM,          String.valueOf(Double.isNaN(scEarthDistKm) ? scEarthDistKm : (int)scEarthDistKm));
        timeHistoryFileRecord.setValue(TimeHistoryFile.EARTH_SUN_DIST_KM,         String.valueOf(Double.isNaN(earthSunDistKm) ? earthSunDistKm : (int)earthSunDistKm));
        timeHistoryFileRecord.setValue(TimeHistoryFile.EARTH_SUN_DIST_AU,         String.format("%.6f", earthSunDistAu));
        timeHistoryFileRecord.setValue(TimeHistoryFile.SC_VELOCITY_SSB,           String.format("%.3f", scVelSsbKmS));
        timeHistoryFileRecord.setValue(TimeHistoryFile.SC_VELOCITY_EARTH,         String.format("%.3f", scVelEarthKmS));
        timeHistoryFileRecord.setValue(TimeHistoryFile.EARTH_VELOCITY_SSB,        String.format("%.3f", earthVelSsbKmS));

        timeHistoryFileRecord.setValue(TimeHistoryFile.TARGET_FRAME_SCLK_COARSE,  String.valueOf(targetSample.getTkSclkCoarse()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.TARGET_FRAME_ENC_SCLK,
                String.valueOf(TimeConvert.sclkToEncSclk(
                                config.getNaifSpacecraftId(),
                                sclkPartition,
                                targetSample.getTkSclkCoarse(),
                                0
                        )
                )
        );

        final Optional<Double> tdtSErrorWarningThreshold = config.getTdtSErrorWarningThresholdMs();
        if (tdtSErrorWarningThreshold.isPresent()) {
            timeHistoryFileRecord.setValue(
                    TimeHistoryFile.TDT_S_ERROR_WARNING_THRESHOLD,
                    String.valueOf(tdtSErrorWarningThreshold.get())
            );
        } else {
            timeHistoryFileRecord.setValue(
                    TimeHistoryFile.TDT_S_ERROR_WARNING_THRESHOLD,
                    "-"
            );
        }

        logger.info("Retrieving GNC and oscillator temperature values to include with the Time History File");

        TelemetrySource.GncParms gncParms;
        String activeOscillatorId;
        double oscillatorTemperature = Double.NaN;
        String radioId;

        tlmSource.connect();
        try {
            gncParms = tlmSource.getGncTkParms(scet_tdt_g, tcTarget.getTargetSampleTdtG());
            activeOscillatorId = tlmSource.getActiveOscillatorId(targetSample);
            if (! activeOscillatorId.equals("-")) {
                oscillatorTemperature = tlmSource.getOscillatorTemperature(scet_tdt_g, activeOscillatorId);
            }
            radioId = tlmSource.getActiveRadioId(targetSample);
        } finally {
            tlmSource.disconnect();
        }

        // write downlinked parameter values to Time History File
        timeHistoryFileRecord.setValue(TimeHistoryFile.SCLK_1,                    () -> String.valueOf(gncParms.getSclk1()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.TDT_1,                     () -> String.valueOf(gncParms.getTdt1()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.TDT_1_STRING,              () -> {
            try {
                return TimeConvert.tdtToTdtStr(gncParms.getTdt1());
            } catch (TimeConvertException e) {
                throw new RuntimeException(e);
            }
        });

        timeHistoryFileRecord.setValue(TimeHistoryFile.CLK_CHANGE_RATE_FOR_TDT_S, () -> String.valueOf(gncParms.getClkchgrate1()));

        // write downlinked SCLK input and TDT(S) telemetry values, which are paired inputs and results from onboard TDT(S) conversions
        timeHistoryFileRecord.setValue(TimeHistoryFile.SCLK_FOR_TDT_S,            () -> String.valueOf(gncParms.getGncSclk()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.TDT_S,                     () -> String.format("%.6f", gncParms.getTdt_s()));
        timeHistoryFileRecord.setValue(TimeHistoryFile.TDT_S_STR,                 () -> {
            try {
                return TimeConvert.tdtToTdtStr(gncParms.getTdt_s());
            } catch (TimeConvertException e) {
                throw new RuntimeException(e);
            }
        });

        // If GNC SCLK *and* TDT(S) were obtained from GNC telemetry, compute the TDT(S) Error, and create warning messages if applicable
        final Double gnc_sclk_for_tdt_s = gncParms.getGncSclk();
        final Double gnc_tdt_s = gncParms.getTdt_s();
        if (gnc_tdt_s.isNaN() || gnc_sclk_for_tdt_s.isNaN()) {
            logger.warn("No value for the GNC SCLK and/or GNC TDT(S) was obtained from telemetry.  TDT(S) Error not computed for TimeHistoryFile.");
        } else if ((gnc_tdt_s < 0) || (gnc_sclk_for_tdt_s < 0)) {
            logger.warn("The collected GNC SCLK and/or GNC TDT(S) is of a negative value, possibly indicating it is not set. TDT(S) Error not computed for TimeHistoryFile.");
        } else {
            final double tdtSError = computeTdtSErrorMs(
                    gncParms,
                    TimeConvert.encSclkToSclk(config.getNaifSpacecraftId(), TimeConvert.getSclkKernelTickRate(config.getNaifSpacecraftId()), tcTarget.getTargetSampleEncSclk()),
                    tcTarget.getTargetSampleTdtG(),
                    currentPredictedClockChangeRate
            );

            timeHistoryFileRecord.setValue(
                    TimeHistoryFile.TDT_S_ERROR,
                    new DecimalFormat("0.000000").format(tdtSError)
            );

            if (config.getTdtSErrorWarningThresholdMs().isPresent()) {
                if (Math.abs(tdtSError) > config.getTdtSErrorWarningThresholdMs().get()) {
                    logger.warn(String.format("Magnitude of computed error in TDT(S) of %f exceeds threshold of %f. Warning noted in TimeHistoryFile.", tdtSError, config.getTdtSErrorWarningThresholdMs().get()));
                    timeHistoryFileWarnings.add("TDT(S)_Error_threshold_exceeded!");
                }
            }

            // Set the Warnings in the TimeHistoryFile (nominally, these will be blank).
            if (timeHistoryFileWarnings.size() > 0) {
                timeHistoryFileRecord.setValue(TimeHistoryFile.WARNING, String.join(";", timeHistoryFileWarnings));
            } else {
                timeHistoryFileRecord.setValue(TimeHistoryFile.WARNING, "");
            }
        }

        timeHistoryFileRecord.setValue(TimeHistoryFile.RADIO_ID,                  radioId);
        timeHistoryFileRecord.setValue(TimeHistoryFile.OSCILLATOR,                activeOscillatorId);
        timeHistoryFileRecord.setValue(TimeHistoryFile.OSCILLATOR_TEMP_DEGC,      String.valueOf(oscillatorTemperature));

        timeHistoryFile.writeRecord(timeHistoryFileRecord);

        logger.info(USER_NOTICE, "Appended a new entry to Time History File located at " + timeHistoryFile.getPath());
    }

    private double computeTdtSErrorMs(TelemetrySource.GncParms gncParms, Double currentCorrelationSclk, Double currentCorrelationTdtG, Double currentCorrelationPredictedClockChangeRate) {
        final double tdt_s_n = gncParms.getTdt_s();
        final double tdt_g_prior = currentCorrelationTdtG;

        final double sclk_for_tdt_s_n = gncParms.getGncSclk();
        final double sclk_prior = currentCorrelationSclk;

        // if the prior clock change rate is going to be updated with the interpolated value, use that.  otherwise, use the unchanged prior (predicted) clock change rate value from the kernel
        final double clkchgrate_prior = currentCorrelationPredictedClockChangeRate;

        logger.debug(
                String.format(
                        "TDT(S)_Error(ms) calc: (tdt_s_n %f - tdt_g_prior %f)\n"
                                + "- ((sclk_for_tdt_s_n %f - sclk_prior %f) * clkchgrate_prior %f)",
                        tdt_s_n, tdt_g_prior,
                        sclk_for_tdt_s_n, sclk_prior, clkchgrate_prior
                )
        );

        return TimeConvert.MSEC_PER_SECOND * (
                (tdt_s_n - tdt_g_prior)
                - ((sclk_for_tdt_s_n - sclk_prior) * clkchgrate_prior)
        );
    }

    /**
     * Write the SCLK kernel.
     *
     * @param tdt_g the ground calculated TDT
     * @param encSclk the encoded SCLK
     * @param clockChangeRate the predicted or assigned clock change rate
     * @param clockChangeRateInterp the interpolated clock change rate
     * @throws MmtcException if the SCLK kernel could not be written
     */
    private void writeSclkKernel(double tdt_g, double encSclk, double clockChangeRate, double clockChangeRateInterp) throws MmtcException {
        try {
            newSclkKernel = new SclkKernel(currentSclkKernel);
            newSclkKernel.setProductCreationTime(appRunTime);
            newSclkKernel.setDir(config.getSclkKernelOutputDir().toString());
            newSclkKernel.setName(getNextSclkKernelName());
            newSclkKernel.setNewTriplet(encSclk, TimeConvert.tdtToTdtStr(tdt_g), clockChangeRate);

            if (actualClkChgRateMode == ClockChangeRateMode.COMPUTE_INTERPOLATED) {
                newSclkKernel.setReplacementClockChgRate(clockChangeRateInterp);
            }

            newSclkKernel.createFile();
        }
        catch (TextProductException | TimeConvertException ex) {
            throw new MmtcException("Unable to write SCLK kernel", ex);
        }
    }

    /**
     * Write the SCLK/SCET file, if applicable.
     *
     * @throws MmtcException when the file is unable to be written
     */
    private void writeSclkScetFile() throws MmtcException {
        try {
            SclkScetFile scetFile;

            // Create the new SCLK/SCET file from the newly-created SCLK kernel.
            scetFile = new SclkScetFile(
                    config,
                    createNewSclkScetName(newVersionStr),
                    newVersionStr
            );

            scetFile.setProductCreationTime(appRunTime);
            scetFile.setClockTickRate(sclk_kernel_fine_tick_modulus);
            SclkScet.setScetStrSecondsPrecision(config.getSclkScetScetUtcPrecision());
            scetFile.createNewSclkScetFile(newSclkKernel.getPath());
        } catch (TimeConvertException | TextProductException ex) {
            throw new MmtcException("Unable to write SCLK/SCET file", ex);
        }
    }

    /**
     * Writes the Uplink Command File
     * @param et_g the ground time as ephemeris time (ET or TDB)
     * @param tdt_g the ground time ast TDT
     * @param clockChangeRate the clock change rate
     * @throws MmtcException if the Uplink Command File cannot be written
     */
    private void writeUplinkCommandFile(double et_g, double tdt_g, double clockChangeRate) throws MmtcException {
        String cmdFilespec = "";
        try {
            UplinkCommand uplinkCmd = new UplinkCommand(
                    targetSample.getTkSclkCoarse(),
                    et_g,
                    tdt_g,
                    TimeConvert.tdtToTdtStr(tdt_g),
                    clockChangeRate);

            String cmdFilename = config.getUplinkCmdFileBasename() +
                    appRunTime.toEpochSecond() +
                    UplinkCmdFile.FILE_SUFFIX;
            UplinkCmdFile cmdFile = new UplinkCmdFile(Paths.get(
                    config.getUplinkCmdFileDir(), cmdFilename).toString());
            cmdFile.write(uplinkCmd);
        } catch (IOException | TimeConvertException ex) {
            throw new MmtcException("Unable to write the Uplink Command File: " + cmdFilespec, ex);
        }
    }

    /**
     * Determines the most recent counter or timestamp of a particular type of output file
     * @param dir the Path of the directory where the relevant files can be found
     * @param prefix the shared basename of the target files, i.e. "uplinkCmd"
     * @param suffix the file type
     * @return the highest numeric suffix of files with the given prefix as a String
     */
    public static String getLatestFileCounterByPrefix(Path dir, String prefix, String suffix) {
        // Create an array of all files in dir that start with prefix
        File directoryPath = new File(dir.toAbsolutePath().toString());
        String[] targetFilenames = directoryPath.list((dir1, name) -> name.startsWith(prefix) && name.endsWith(suffix));

        if (targetFilenames == null || targetFilenames.length == 0) { // No files found (likely haven't been created yet)
            logger.debug(String.format("No files '%s*%s' found in output directory %s, returning '-'.", prefix, suffix, dir));
            return "-";
        }

        // Isolate the counter/date/digit suffix and return the max
        int max = 0;
        for (String file:targetFilenames) {
            int counter = Integer.parseInt(file.replaceAll("^\\D*(\\d+).*$", "$1"));
            if (counter > max) {
                max = counter;
            }
        }

        return String.valueOf(max);
    }

    /**
     * Gets the states (last line or most recent text product file) of MMTC's output products and adds them to the runHistoryFileRecord.
     * Meant to be run after all output product objects have been initialized but before they've been modified by a successful run.
     */
    private void recordRunHistoryFileBeforeValues() throws MmtcException {
        int newRunId;
        List<TableRecord> prevRuns = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS);
        if (prevRuns.isEmpty()) {
            newRunId = 1;
        } else {
            newRunId = Integer.parseInt(prevRuns.get(prevRuns.size()-1).getValue("Run ID")) + 1;
        }
        // Run Info
        runHistoryFileRecord.setValue(RunHistoryFile.RUN_TIME,              appRunTime.toString());
        runHistoryFileRecord.setValue(RunHistoryFile.RUN_ID,                String.format("%05d",newRunId));
        runHistoryFileRecord.setValue(RunHistoryFile.ROLLEDBACK,            "false");
        runHistoryFileRecord.setValue(RunHistoryFile.RUN_USER,              System.getProperty("user.name"));
        runHistoryFileRecord.setValue(RunHistoryFile.CLI_ARGS,              String.join(" ",cliArgs));

        // SCLK Kernel
        runHistoryFileRecord.setValue(RunHistoryFile.PRERUN_SCLK,           getLatestFileCounterByPrefix(config.getSclkKernelOutputDir(), config.getSclkKernelBasename(), SclkKernel.FILE_SUFFIX));

        // TimeHistoryFile
        runHistoryFileRecord.setValue(RunHistoryFile.PRERUN_TIMEHIST,       String.valueOf(timeHistoryFile.getLastLineNumber()));

        // RawTlmTable
        runHistoryFileRecord.setValue(RunHistoryFile.PRERUN_RAWTLMTABLE,    String.valueOf(rawTlmTable.getLastLineNumber()));

        // SCLKSCET File
        String latestSclkScetFileCounter;
        if (config.createSclkScetFile()) {
            // if this run is creating a SCLK-SCET file, assume the associated configuration is populated and read it
            latestSclkScetFileCounter = getLatestFileCounterByPrefix(config.getSclkScetOutputDir(), config.getSclkScetFileBasename(), config.getSclkScetFileSuffix());
        } else {
            // else, if this run is not creating a SCLK-SCET file, then use the prior value, if any exists, or "-" otherwise
            latestSclkScetFileCounter = runHistoryFile.readLatestValueOf(RunHistoryFile.POSTRUN_SCLKSCET, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).orElse("-");
        }
        runHistoryFileRecord.setValue(RunHistoryFile.PRERUN_SCLKSCET, latestSclkScetFileCounter);

        // Uplink Command File
        String latestUplinkCmdFileCounter;
        if (config.createUplinkCmdFile()) {
            // if this run is creating an uplink command file, assume the associated configuration is populated and read it
            latestUplinkCmdFileCounter = getLatestFileCounterByPrefix(Paths.get(config.getUplinkCmdFileDir()), config.getUplinkCmdFileBasename(), UplinkCmdFile.FILE_SUFFIX);
        } else {
            // else, if this run is not creating an uplink command file, then use the prior value, if any exists, or "-" otherwise
            latestUplinkCmdFileCounter = runHistoryFile.readLatestValueOf(RunHistoryFile.POSTRUN_UPLINKCMD, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).orElse("-");
        }
        runHistoryFileRecord.setValue(RunHistoryFile.PRERUN_UPLINKCMD, latestUplinkCmdFileCounter);
    }

    /**
     * Records the final states of output products after a successful run and writes them (as well as
     * the initial states recorded by recordRunHistoryFileBeforeValues()) to a CSV file.
     * @throws MmtcException if anything prevents the record from being written to file
     */
    private void writeRunHistoryFileAfterRun() throws MmtcException {
        // SCLK kernel
        runHistoryFileRecord.setValue(RunHistoryFile.POSTRUN_SCLK,          newVersionStr);

        // TimeHistoryFile
        runHistoryFileRecord.setValue(RunHistoryFile.POSTRUN_TIMEHIST,      String.valueOf(timeHistoryFile.getLastLineNumber()));

        // RawTlmTable
        runHistoryFileRecord.setValue(RunHistoryFile.POSTRUN_RAWTLMTABLE,   String.valueOf(rawTlmTable.getLastLineNumber()));

        // SCLKSCET file
        if (config.createSclkScetFile()) {
            runHistoryFileRecord.setValue(RunHistoryFile.POSTRUN_SCLKSCET, newVersionStr);
        } else {
            // else, if this run did not create a SCLK-SCET file, then use the prior value, if any exists, or "-" otherwise
            runHistoryFileRecord.setValue(RunHistoryFile.POSTRUN_SCLKSCET, runHistoryFile.readLatestValueOf(RunHistoryFile.POSTRUN_SCLKSCET, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).orElse("-"));
        }

        // Uplink Command File
        if (config.createUplinkCmdFile()) {
            runHistoryFileRecord.setValue(RunHistoryFile.POSTRUN_UPLINKCMD, getLatestFileCounterByPrefix(Paths.get(config.getUplinkCmdFileDir()), config.getUplinkCmdFileBasename(), UplinkCmdFile.FILE_SUFFIX));
        } else {
            runHistoryFileRecord.setValue(RunHistoryFile.POSTRUN_UPLINKCMD, runHistoryFile.readLatestValueOf(RunHistoryFile.POSTRUN_UPLINKCMD, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).orElse("-"));
        }

        runHistoryFile.writeRecord(runHistoryFileRecord);

        logger.info(USER_NOTICE, "Appended a new entry to Run History File located at " + runHistoryFile.getPath());
    }

    /**
     * Creates a new SCLK kernel file name from the previous one by
     * incrementing the version number.
     *
     * @return the new SCLK kernel filename
     * @throws MmtcException if the new SCLK kernel filename could not be derived
     */
    private String getNextSclkKernelName() throws MmtcException {
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
        try {
            if(!config.generateUniqueKernelCounters() || !runHistoryRecordExists) {
                newVersionStr = constructNextSclkKernelCounter(
                        currentSclkKernel.getName(),
                        config.getSclkKernelSeparator(),
                        SclkKernel.FILE_SUFFIX
                );
            } else {
                newVersionStr = StringUtils.leftPad(String.valueOf(
                        Integer.parseInt(currentSclkCounterStr)+1),
                        currentSclkCounterStr.length(),
                        "0"
                );
            }
            return config.getSclkKernelBasename() + config.getSclkKernelSeparator() + newVersionStr + ".tsc";
        }
        catch (IndexOutOfBoundsException | NumberFormatException ex) {
            throw new MmtcException("Unable to determine next SCLK kernel filename", ex);
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


    /**
     * Creates a new SCLK/SCET file name from the previous one by incrementing the version
     * number.
     *
     * @param version IN the version of the current SCLK/SCET file
     * @return the new SCLK/SCET filename
     */
    private String createNewSclkScetName(String version) {
        String newName = config.getSclkScetFileBasename() +
                config.getSclkScetFileSeparator() +
                version +
                config.getSclkScetFileSuffix();

        return newName;
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
     * @throws TimeConvertException if the SCLK tick rate could not be obtained
     */
    private Double computeClkChgRate(int sclk0, double tdt_g0, int sclk1, double tdt_g1) throws TimeConvertException {
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
        logger.debug("computeClkChgRate: clkChgRate = " + clkChgRate.toString());

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

        String[] lookBackRec = currentSclkKernel.getPriorRec(tdt_g, config.getPredictedClkRateLookBackDays()*24.);

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

        if (sclk0 >= sclk) {
            throw new MmtcException("ERROR: Input data sample SCLK (" + sclk +
                    " is earlier than or the same as the lookback SCLK (" + sclk0 + ") record in SCLK Kernel. Make sure that" +
                    " the start time is later than the last time correlation record in the input SCLK Kernel.");
        }

        if (tdt_g0 >= tdt_g) {
            throw new MmtcException("ERROR: Input data sample TDT(G) (" + tdt_g +
                    " is earlier than or the same as the lookback TDT(G) (" + tdt_g0 + ") record in SCLK kernel. Make sure that" +
                    " the start time is later than the last time correlation record in the input SCLK Kernel.");
        }

        // Check that the selected look back record from the input SCLK Kernel is not older than the time of the current
        // sample minus the maximum number of look back hours as specified in the configuration parameters.  If it is,
        // throw an exception. Processing should not continue. The user should check that the input SCLK Kernel is
        // correct for the run or rerun in Assign (--clkchgrate-assign) or No-Drift (--clkchgrate-nodrift) modes to
        // compute the CLKRATE.
        Double deltaTDT = tdt_g - tdt_g0;
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
    private Double computeInterpolatedClkChgRate(Integer sclk, Double tdt_g)
            throws TextProductException, TimeConvertException, MmtcException {

        logger.debug("computeInterpolatedClkChgRate(): Last rec in existing SCLK kernel = " +
                currentSclkKernel.getLastRecValue(SclkKernel.ENCSCLK) + " " +
                currentSclkKernel.getLastRecValue(SclkKernel.TDTG) + " " +
                currentSclkKernel.getLastRecValue(SclkKernel.CLKCHGRATE));
        ;
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

        if (sclk0 >= sclk) {
            logger.error("Start/Stop time interval of current run overlaps that of the last run. Current run Start Time " +
                    "must be later than the last time correlation record in the input SCLK Kernel.");
            throw new MmtcException("ERROR: input data sample SCLK is earlier than or the same as the last record in SCLK kernel.");
        }
        return computeClkChgRate(sclk0, tdt_g0, sclk, tdt_g);
    }


    /**
     * Compute the state vector for a given target body relative to an
     * observer.
     *
     * The J2000 reference frame is used and no aberration correction is applied.
     *
     * @param target the SPICE target as a string
     * @param observer the SPICE observer as a string
     * @return the position and velocity of the target relative to the observer
     * @throws TimeConvertException if a computation error occurred
     * @throws MmtcException if the state vector could not be computed
     */
    private double[] computeTargetState(String target, String observer)
            throws TimeConvertException, MmtcException {
        String corr = "NONE";               // Aberration correction
        String ref = "J2000";               // Reference frame
        double[] state = new double[6];     // State vector; [0, 2] is dist, [3, 5] is velocity
        double[] lightTime = new double[1]; // OWLT between observer and target in seconds

        double et = TimeConvert.sclkToEt(
                config.getNaifSpacecraftId(),
                config.getSclkPartition(TimeConvert.parseIsoDoyUtcStr(targetSample.getErtStr())),
                targetSample.getTkSclkCoarse(),
                new BigDecimal(targetSample.getTkSclkFine())
                        .divide(new BigDecimal(tk_sclk_fine_tick_modulus), RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(sclk_kernel_fine_tick_modulus))
                        .intValue()
        );
        try {
            CSPICE.spkezr(target, et, ref, corr, observer, state, lightTime);
            return state;
        } catch (SpiceErrorException ex) {
            throw new MmtcException("Unable to compute state vector: " + ex.getMessage(), ex);
        }
    }

    /**
     * Get the distance between two bodies.
     *
     * @param target the SPICE target as a string
     * @param observer the SPICE observer as a string
     * @return the distance between the two objects in km
     * @throws TimeConvertException if a computation could not be performed
     * @throws MmtcException if the target distance could not be computed
     */
    private double computeDistanceKm(String target, String observer)
            throws TimeConvertException, MmtcException {
        double[] state;
        double dist;

        try {
            state = computeTargetState(target, observer);
            dist = CSPICE.vnorm(Arrays.copyOfRange(state, 0, 3));
        }
        catch (SpiceErrorException ex) {
            throw new MmtcException("Unable to compute distance: " + target + " to " + observer + ": " + ex.getMessage(), ex);
        }

        return dist;
    }


    /**
     * Get the velocity of a target body relative to an observer.
     *
     * @param target the SPICE target as a string
     * @param observer the SPICE observer as a string
     * @return the target's velocity in km/s
     * @throws TimeConvertException if a computation could not be performed
     * @throws MmtcException if the velocity vector could not be computed
     */
    private double computeVelocityKmS(String target, String observer)
            throws TimeConvertException, MmtcException {
        double[] state;
        double vel;

        try {
            state = computeTargetState(target, observer);
            vel = CSPICE.vnorm(Arrays.copyOfRange(state, 3, 6));
        }
        catch (SpiceErrorException ex) {
            throw new MmtcException("Unable to compute velocity: " + target + " to " + observer + ": " + ex.getMessage(), ex);
        }

        return vel;
    }


    /**
     * Computes the error in TDT, in milliseconds, as predicted by the prior SCLK kernel for the current SCLK.
     *
     * @param actualEncSclk the encoded SCLK, from the current time correlation run, from which to compute an estimated TDT
     * @param actualTdt the TDT value from the current time correlation run, used as the actual TDT value in computing the error
     * @return the error between an estimated TDT using the prior SCLK kernel to the actual TDT, in milliseconds
     * @throws MmtcException when unable to convert from SCLK to ET or from ET to TDT
     */
    private double computeTdtPredictionErrorMs(double actualEncSclk, double actualTdt) throws MmtcException {
        try {
            // these SPICE calls will use the currently-loaded SCLK kernel, which will be the 'prior' one once this run finishes
            double estimatedEtUsingPriorCorrelation  = CSPICE.sct2e(config.getNaifSpacecraftId(), actualEncSclk);
            double estimatedTdtUsingPriorCorrelation = CSPICE.unitim(estimatedEtUsingPriorCorrelation, "ET", "TDT");
            return (estimatedTdtUsingPriorCorrelation - actualTdt) * TimeConvert.MSEC_PER_SECOND;
        } catch (SpiceErrorException ex) {
            throw new MmtcException("Unable to compute TDT error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Main application processing.
     */
    private void run() throws Exception {
        logger.info(USER_NOTICE, String.format("Running time correlation between %s and %s", config.getStartTime().toString(), config.getStopTime().toString()));

        if (config.isTestMode()) {
            logger.warn(String.format("Test mode is enabled! One-way light time will be set to the provided value %f and ancillary positional and velocity calculations will be skipped.", config.getTestModeOwlt()));
        }

        if (tk_sclk_fine_tick_modulus == -1) {
            throw new MmtcException("tf_offset_sclk_modulus not set in TimeCorrelationApp.");
        }

        if (sclk_kernel_fine_tick_modulus == -1) {
            throw new MmtcException("sclk_kernel_fine_tick_modulus not set in TimeCorrelationApp.");
        }

        // -------- Compute Parameters for the new time correlation. --------
        final TimeCorrelationTarget tcTarget = buildSampleSet();

        logger.debug("Sample set:");
        tcTarget.getSampleSet().forEach(logger::debug);

        String vcidMsg = "VCIDs in sample set: ";
        for (FrameSample sample : tcTarget.getSampleSet()) {
            vcidMsg += sample.getVcid() + ", ";
        }
        logger.debug(vcidMsg.substring(0, vcidMsg.length() - 2));

        targetSample = tcTarget.getTargetSample();

        final String groundStationId = tcTarget.getTargetSampleGroundStationId();

        logger.info(USER_NOTICE, "--- Target and supplemental frame summary ---");
        logger.info(USER_NOTICE, targetSample.toSummaryString(groundStationId));
        logger.info(USER_NOTICE, "---------------------------------------------");

        logger.debug("TargetSample:\n");
        logger.debug(targetSample.toString());

        double owlt = tcTarget.getTargetSampleOwlt();
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

        // Record OWLT
        timeHistoryFileRecord.setValue(TimeHistoryFile.OWLT_SEC, String.format("%.6f", owlt));

        double encSclk = tcTarget.getTargetSampleEncSclk();
        logger.debug("Encoded SCLK from supplemental sample coarse TK SCLK: " + encSclk);

        double etG = tcTarget.getTargetSampleEtG();
        logger.debug(tcTarget.getTargetSampleErtGCalcLogStatement());
        logger.debug("ET(G) = " + etG);
        this.tf_offset = tcTarget.getTargetSampleTfOffset();

        // Compute TDT(G) from ERT in ET form.
        double tdtG = tcTarget.getTargetSampleTdtG();
        logger.debug("TDT(G) value computed using ET(G): " + tdtG);

        String tdtGStr = TimeConvert.tdtToTdtStr(tdtG);

        // Record the ground computed TDT and ET values. These are the ground time equivalents of the SCLK.
        DecimalFormat tdf = new DecimalFormat("#.000000");
        tdf.setRoundingMode(RoundingMode.HALF_UP);
        String tdt_g_numericStr = tdf.format(tdtG);
        String et_numericStr    = tdf.format(etG);

        timeHistoryFileRecord.setValue(TimeHistoryFile.TDT_G, tdt_g_numericStr);
        timeHistoryFileRecord.setValue(TimeHistoryFile.TDT_G_STR, tdtGStr);
        timeHistoryFileRecord.setValue(TimeHistoryFile.ET_G, et_numericStr);

        final String equivalent_scet_utc_for_tdt_g_iso_doy = TimeConvert.tdtStrToUtc(tdtGStr, config.getTimeHistoryFileScetUtcPrecision());
        timeHistoryFileRecord.setValue(TimeHistoryFile.SCET_UTC, equivalent_scet_utc_for_tdt_g_iso_doy);

        final OffsetDateTime equivalent_scet_utc_for_tdt_g = TimeConvert.parseIsoDoyUtcStr(equivalent_scet_utc_for_tdt_g_iso_doy);

        // Compute and record interval between new and previous TDT(G).

        // The TDT(G) value can be either a calendar string or a numeric value.
        String tdtGPrevStr = currentSclkKernel.getLastRecValue(SclkKernel.TDTG);
        double tdtGPrev;
        if (SclkKernel.isNumVal(tdtGPrevStr)) {
            tdtGPrev = Double.parseDouble(tdtGPrevStr);
        }
        else {
            tdtGPrev = TimeConvert.tdtStrToTdt(tdtGPrevStr.replace("@", ""));
        }

        double dt = (tdtG - tdtGPrev) / TimeConvert.SECONDS_PER_DAY;
        if (dt < 0.000000) {
            throw new MmtcException("ERROR: TDT(G) of the last record in SCLK kernel is later than the input data sample.");
        }

        timeHistoryFileRecord.setValue(TimeHistoryFile.DT, String.format("%.2f", dt));

        double epMs = computeTdtPredictionErrorMs(encSclk, tdtG);
        timeHistoryFileRecord.setValue(TimeHistoryFile.EP, String.format("%.3f", epMs));

        // Compute and record TDT error (ms) / TDT interval
        timeHistoryFileRecord.setValue(TimeHistoryFile.EP_DT, String.format("%.3f", Math.abs(epMs / dt)));

        // Provide the clock change rate for the new time correlation record based on the method specified
        // by the user.
        double clockChangeRate;

        // Get the user-specified method for computing the CLKRATE. If this is the very first run of the application
        // for a mission, the input SCLK Kernel is assumed to be the seed kernel. In this case and ONLY in this case,
        // if the user selected COMPUTE_INTERPOLATED, switch to COMPUTE_PREDICTED so as not to overwrite the seed
        // kernel CLKRATE with an interpolated value. Typically, the user would select ASSIGN or NO_DRIFT as the
        // CLKRATE method anyway for the first few runs.
        ClockChangeRateMode requestedClockChangeRateMode = config.getClockChangeRateMode();
        if (requestedClockChangeRateMode == ClockChangeRateMode.COMPUTE_INTERPOLATED && currentSclkKernel.getSourceProductDataRecCount() == 1) {
            logger.warn("Switching from interpolated mode to predicted so as not to overwrite seed kernel entry.");
            actualClkChgRateMode = ClockChangeRateMode.COMPUTE_PREDICTED;
        } else {
            actualClkChgRateMode = requestedClockChangeRateMode;
        }

        logger.info("Using clock change rate mode: " + actualClkChgRateMode);

        // Compute the interpolated clock change rate if selected for.
        double interpClkChgRate = -1.;
        if (actualClkChgRateMode == ClockChangeRateMode.COMPUTE_INTERPOLATED) {
            try {
                interpClkChgRate = computeInterpolatedClkChgRate(targetSample.getTkSclkCoarse(), tdtG);

                if (timeHistoryFile.exists()) {
                    Map<String, String> lastRecord = timeHistoryFile.readLastRecord();
                    lastRecord.replace(TimeHistoryFile.INTERP_CLK_CHANGE_RATE, String.valueOf(interpClkChgRate));
                    timeHistoryFile.replaceLastRecord(lastRecord);
                }
            } catch (MmtcException ex) {
                throw new MmtcException("Can't compute or record interpolated clock change rate.", ex);
            }
        }

        switch (actualClkChgRateMode) {
            case NO_DRIFT:
                clockChangeRate = 1.0;
                break;
            case ASSIGN_KEY:
            case ASSIGN:
                clockChangeRate = config.getClockChangeRateAssignedValue();
                break;
            case COMPUTE_PREDICTED:
            case COMPUTE_INTERPOLATED:
                clockChangeRate = computePredictedClkChgRate(targetSample.getTkSclkCoarse(), tdtG);

                // Compute and record the drift rate of the SCLK counter in ms/day
                double sclkDrift = ((1.0 / clockChangeRate) - 1.0) * TimeConvert.SECONDS_PER_DAY * TimeConvert.MSEC_PER_SECOND;
                timeHistoryFileRecord.setValue(TimeHistoryFile.SCLK_DRIFT, String.format("%4.3f", sclkDrift));
                break;
            default:
                throw new MmtcException("Invalid clock change rate method selected.");
        }

        // Record the predicted / assigned clock change rate value
        timeHistoryFileRecord.setValue(TimeHistoryFile.PRED_CLK_CHANGE_RATE, String.valueOf(clockChangeRate));

        // Write the SCLK kernel.
        writeSclkKernel(tdtG, encSclk, clockChangeRate, interpClkChgRate);

        // Write the optional SCLK/SCET file if selected to do so.
        if (config.createSclkScetFile()) {
            writeSclkScetFile();
        }

        // Write the optional Uplink Command File if selected to do so.
        if (config.createUplinkCmdFile()) {
            writeUplinkCommandFile(etG, tdtG, clockChangeRate);
        }

        // Write the tables.
        writeRawTelemetryTable(tcTarget.getSampleSet());

        writeTimeHistoryFile(
                equivalent_scet_utc_for_tdt_g,
                tcTarget,
                clockChangeRate
        );

        writeRunHistoryFileAfterRun();
        logger.info(String.format("Run at %s recorded to %s", appRunTime, config.getRunHistoryFileUri().toString()));

        logger.info(USER_NOTICE, "MMTC completed successfully.");
    }

    /**
     * Entry point of the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        logger.info(USER_NOTICE, String.format("************ %s version %s ************", MMTC_TITLE, BUILD_INFO.version));
        logger.info(USER_NOTICE, String.format("Commit %s built at %s", BUILD_INFO.commit, BUILD_INFO.buildDate));

        if (args[0].equalsIgnoreCase("rollback")) {
            logger.info(USER_NOTICE, String.format("Rollback invoked by command %s, starting rollback process", Arrays.toString(args)));
            try {
                TimeCorrelationRollback rollbackInstance = new TimeCorrelationRollback();
                rollbackInstance.rollback();
            } catch (Exception e) {
                logger.fatal("Rollback failed.", e);
                System.exit(1);
            }
        } else {
            try {
                TimeCorrelationApp app = new TimeCorrelationApp(args);
                app.init();
                try {
                    app.run();
                } catch (Exception ex) {
                    logger.fatal("MMTC run failed.", ex);
                    System.exit(1);
                }
            } catch (Exception ex) {
                logger.fatal("MMTC initialization failed.", ex);
                System.exit(1);
            }
        }
    }
}
