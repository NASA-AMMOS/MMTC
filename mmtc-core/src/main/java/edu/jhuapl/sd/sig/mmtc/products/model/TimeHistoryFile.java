package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Represents an instance of the Time History File, which consists mostly of
 * computed values, along with some raw telemetry.
 */
public class TimeHistoryFile extends AbstractTimeCorrelationTable {
    public static final String TARGET_FRAME_ENC_SCLK = "Enc_SCLK";
    public static final String TARGET_FRAME_SCLK_COARSE = "Int_SCLK";
    public static final String TDT_G = "TDT(G)";
    public static final String TDT_G_STR = "TDT(G)_Calendar";
    public static final String ET_G = "TDB(G)";  // NOTE: This is referred to as et (ephemeris time) elsewhere in the code.
    public static final String PRED_CLK_CHANGE_RATE = "Predicted Clk Chg Rate (s/s)";
    public static final String INTERP_CLK_CHANGE_RATE = "Interpolated Clk Chg Rate (s/s)";
    public static final String SCLK_DRIFT = "SCLK_Drift(ms/day)";
    public static final String OSCILLATOR = "Oscillator";
    public static final String OSCILLATOR_TEMP_DEGC = "Osc_Temp(degC)";
    public static final String CLK_CHANGE_RATE_MODE = "ClkChgRateMode";
    public static final String CLK_CHANGE_RATE_INTERVAL_DAYS = "ChrRateInterv(days)";
    public static final String EP = "Ep(ms)";
    public static final String EP_DT = "|Ep/dt|(ms/day)";
    public static final String DT = "Interval(days)";
    public static final String SCLK_PARTITION = "SCLK_Partition";
    public static final String RF_ENCODING = "RF_Encoding";
    public static final String STATION_ID = "GroundStationID";
    public static final String OWLT_SEC = "OWLT(sec)";
    public static final String SPACECRAFT_TIME_DELAY = "TD SC (sec)";
    public static final String BITRATE_TIME_ERROR = "TD BE (sec)";
    public static final String TF_OFFSET = "TF Offset";
    public static final String SC_SUN_DIST_AU = "SpacecraftSunDist(AU)";
    public static final String SC_SUN_DIST_KM = "SpacecraftSunDist(km)";
    public static final String SC_EARTH_DIST_KM = "SpacecraftEarthDist(km)";
    public static final String EARTH_SUN_DIST_AU = "EarthSunDist(AU)";
    public static final String EARTH_SUN_DIST_KM = "EarthSunDist(km)";
    public static final String SC_VELOCITY_SSB = "SpacecraftVelocity(SSB_km/s)";
    public static final String EARTH_VELOCITY_SSB = "EarthVelocity(SSB_km/s)";
    public static final String SC_VELOCITY_EARTH = "SpacecraftVelocity(Earth_km/s)";
    public static final String RADIO_ID = "The Active Radio";
    public static final String DATA_RATE_BPS = "DataRate(bps)";
    public static final String SCET_UTC = "SCET (UTC)";
    public static final String TDT_S = "TDT(S)";
    public static final String TDT_S_STR = "TDT(S)_Calendar";
    public static final String TDT_S_ERROR = "TDT(S)_Error(ms)";
    public static final String SCLK_FOR_TDT_S = "SCLK_for_TDT(S)";
    public static final String TDT_S_ERROR_WARNING_THRESHOLD = "TDT(S)ErrorWarningThreshold(ms)";
    public static final String SCLK_1 = "SCLK1";
    public static final String TDT_1 = "TDT1";
    public static final String TDT_1_STRING = "TDT1_Calendar";
    public static final String CLK_CHANGE_RATE_FOR_TDT_S = "ClockChangeRateForTDT(S)";
    public static final String WARNING = "Warning";

    private static final List<String> DEFAULT_COLUMNS = Arrays.asList(
            TARGET_FRAME_ENC_SCLK,
            TARGET_FRAME_SCLK_COARSE,
            TDT_G,
            TDT_G_STR,
            ET_G,
            PRED_CLK_CHANGE_RATE,
            INTERP_CLK_CHANGE_RATE,
            EP,
            EP_DT,
            DT,
            CLK_CHANGE_RATE_MODE,
            CLK_CHANGE_RATE_INTERVAL_DAYS,
            SCLK_DRIFT,
            OSCILLATOR,
            OSCILLATOR_TEMP_DEGC,
            SCLK_PARTITION,
            RF_ENCODING,
            STATION_ID,
            OWLT_SEC,
            SPACECRAFT_TIME_DELAY,
            BITRATE_TIME_ERROR,
            TF_OFFSET,
            SC_SUN_DIST_AU,
            SC_SUN_DIST_KM,
            SC_EARTH_DIST_KM,
            EARTH_SUN_DIST_AU,
            EARTH_SUN_DIST_KM,
            SC_VELOCITY_SSB,
            EARTH_VELOCITY_SSB,
            SC_VELOCITY_EARTH,
            RADIO_ID,
            DATA_RATE_BPS,
            SCET_UTC,
            TDT_S,
            TDT_S_STR,
            TDT_S_ERROR,
            SCLK_FOR_TDT_S,
            TDT_S_ERROR_WARNING_THRESHOLD,
            SCLK_1,
            TDT_1,
            TDT_1_STRING,
            CLK_CHANGE_RATE_FOR_TDT_S,
            RUN_TIME,
            WARNING
    );

    private final ArrayList<String> columns;

    public TimeHistoryFile(Path path) {
        this(path, Collections.emptyList());
    }

    public TimeHistoryFile(Path path, List<String> columnsToExclude) {
        super(path);
        this.columns = new ArrayList<>(DEFAULT_COLUMNS);
        this.columns.removeAll(columnsToExclude);
    }

    @Override
    public List<String> getHeaders() {
        return Collections.unmodifiableList(columns);
    }

    public static ProductWriteResult appendRowFor(TimeCorrelationContext ctx) throws MmtcException {
        final TimeHistoryFile timeHistoryFile = new TimeHistoryFile(ctx.config.getTimeHistoryFilePath(), ctx.config.getTimeHistoryFileExcludeColumns());
        final TableRecord newThfRec = new TableRecord(timeHistoryFile.getHeaders());

        try {
            writeNewRow(ctx, timeHistoryFile, newThfRec);
        } catch (TimeConvertException | TextProductException e) {
            throw new MmtcException(e);
        }

        return new ProductWriteResult(
                timeHistoryFile.getPath(),
                timeHistoryFile.getLastLineNumber()
        );
    }

    /**
     * Helper function for creation of a new Time History file record. Takes a reference to a time history file record
     * and modifies it in place so its caller can eventually write it or print it in the case of dry runs
     * @param ctx The current TimeCorrelationContext
     * @param timeHistoryFile The Time hist file to be modified
     * @param newThfRec A reference to the TableRecord that will be modified by this method
     * @throws MmtcException
     * @throws TimeConvertException
     */
    public static void generateNewTimeHistRec(TimeCorrelationContext ctx, TimeHistoryFile timeHistoryFile, TableRecord newThfRec) throws MmtcException, TimeConvertException {
        final DecimalFormat tdf = new DecimalFormat("#.000000");
        tdf.setRoundingMode(RoundingMode.HALF_UP);

        if (timeHistoryFile.exists() && ctx.correlation.interpolated_clock_change_rate.isSet()) {
            Map<String, String> lastRecord = timeHistoryFile.readLastRecord();
            lastRecord.replace(TimeHistoryFile.INTERP_CLK_CHANGE_RATE, String.valueOf(ctx.correlation.interpolated_clock_change_rate.get()));
            timeHistoryFile.replaceLastRecord(lastRecord);
        }

        newThfRec.setValue(TimeHistoryFile.OWLT_SEC, String.format("%.6f", ctx.correlation.owlt_sec.get()));

        // Record the ground computed TDT, ET, and SCET (UTC) values. These are the ground time equivalents of the SCLK.
        final String tdtGStr = TimeConvert.tdtToTdtStr(ctx.correlation.target.get().getTargetSampleTdtG());
        final String equivalent_scet_utc_for_tdt_g_iso_doy = TimeConvert.tdtStrToUtc(tdtGStr, ctx.config.getTimeHistoryFileScetUtcPrecision());
        {
            newThfRec.setValue(TimeHistoryFile.TDT_G, tdf.format(ctx.correlation.target.get().getTargetSampleTdtG()));
            newThfRec.setValue(TimeHistoryFile.TDT_G_STR, tdtGStr);
            newThfRec.setValue(TimeHistoryFile.ET_G, tdf.format(ctx.correlation.target.get().getTargetSampleEtG()));
            newThfRec.setValue(TimeHistoryFile.SCET_UTC, equivalent_scet_utc_for_tdt_g_iso_doy);
        }

        newThfRec.setValue(TimeHistoryFile.DT, String.format("%.2f", ctx.correlation.metrics.dt.get()));
        newThfRec.setValue(TimeHistoryFile.EP, String.format("%.3f", ctx.correlation.metrics.ep_ms.get()));
        newThfRec.setValue(TimeHistoryFile.EP_DT, String.format("%.3f", ctx.correlation.metrics.ep_dt.get()));

        if (ctx.correlation.sclk_drift_ms_per_day.isSet()) {
            newThfRec.setValue(TimeHistoryFile.SCLK_DRIFT, String.format("%4.3f", ctx.correlation.sclk_drift_ms_per_day.get()));
        }

        newThfRec.setValue(TimeHistoryFile.PRED_CLK_CHANGE_RATE, String.valueOf(ctx.correlation.predicted_clock_change_rate.get()));

        newThfRec.setValue(TimeHistoryFile.RUN_TIME, ctx.appRunTime.toString());
        final int sclkPartition = ctx.config.getSclkPartition(TimeConvert.parseIsoDoyUtcStr(ctx.correlation.target.get().getTargetSample().getErtStr()));
        {
            final FrameSample targetSample = ctx.correlation.target.get().getTargetSample();
            newThfRec.setValue(TimeHistoryFile.SCLK_PARTITION, String.valueOf(sclkPartition));

            newThfRec.setValue(TimeHistoryFile.RF_ENCODING, targetSample.getTkRfEncoding());
            newThfRec.setValue(TimeHistoryFile.STATION_ID, ctx.config.getStationId(targetSample.getPathId()));
            newThfRec.setValue(TimeHistoryFile.DATA_RATE_BPS, targetSample.getTkDataRateBpsAsRoundedString());
            newThfRec.setValue(TimeHistoryFile.CLK_CHANGE_RATE_INTERVAL_DAYS, String.format("%.2f", ctx.config.getPredictedClkRateLookBackDays()));
            newThfRec.setValue(TimeHistoryFile.SPACECRAFT_TIME_DELAY, String.valueOf(ctx.config.getSpacecraftTimeDelaySec()));
            newThfRec.setValue(TimeHistoryFile.BITRATE_TIME_ERROR, String.valueOf(targetSample.getDerivedTdBe()));
            newThfRec.setValue(TimeHistoryFile.TF_OFFSET, String.valueOf(ctx.correlation.target.get().getTargetSampleTfOffset()));
        }

        switch (ctx.correlation.actual_clock_change_rate_mode.get()) {
            case COMPUTE_INTERPOLATED:
                newThfRec.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "I");
                break;
            case COMPUTE_PREDICTED:
                newThfRec.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "P");
                break;
            case ASSIGN:
            case ASSIGN_KEY:
                newThfRec.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "A");
                break;
            case NO_DRIFT:
                newThfRec.setValue(TimeHistoryFile.CLK_CHANGE_RATE_MODE, "N");
                break;
            default:
                throw new IllegalStateException("Unrecognized clock change rate mode: " + ctx.correlation.actual_clock_change_rate_mode.get());
        }

        {
            final double scSunDistKm = ctx.geometry.scSunDistKm.get();
            newThfRec.setValue(TimeHistoryFile.SC_SUN_DIST_KM, String.valueOf(Double.isNaN(scSunDistKm) ? scSunDistKm : (int) scSunDistKm));

            newThfRec.setValue(TimeHistoryFile.SC_SUN_DIST_AU, String.format("%.6f", ctx.geometry.scSunDistAu.get()));

            final double scEarthDistKm = ctx.geometry.scEarthDistKm.get();
            newThfRec.setValue(TimeHistoryFile.SC_EARTH_DIST_KM, String.valueOf(Double.isNaN(scEarthDistKm) ? scEarthDistKm : (int) scEarthDistKm));

            final double earthSunDistKm = ctx.geometry.earthSunDistKm.get();
            newThfRec.setValue(TimeHistoryFile.EARTH_SUN_DIST_KM, String.valueOf(Double.isNaN(earthSunDistKm) ? earthSunDistKm : (int) earthSunDistKm));

            newThfRec.setValue(TimeHistoryFile.EARTH_SUN_DIST_AU, String.format("%.6f", ctx.geometry.earthSunDistAu.get()));
            newThfRec.setValue(TimeHistoryFile.SC_VELOCITY_SSB, String.format("%.3f", ctx.geometry.scVelSsbKmS.get()));
            newThfRec.setValue(TimeHistoryFile.SC_VELOCITY_EARTH, String.format("%.3f", ctx.geometry.scVelEarthKmS.get()));
            newThfRec.setValue(TimeHistoryFile.EARTH_VELOCITY_SSB, String.format("%.3f", ctx.geometry.earthVelSsbKmS.get()));
        }

        {
            newThfRec.setValue(TimeHistoryFile.TARGET_FRAME_SCLK_COARSE, String.valueOf(ctx.correlation.target.get().getTargetSample().getTkSclkCoarse()));
            newThfRec.setValue(TimeHistoryFile.TARGET_FRAME_ENC_SCLK,
                    String.valueOf(TimeConvert.sclkToEncSclk(
                                    ctx.config.getNaifSpacecraftId(),
                                    sclkPartition,
                                    ctx.correlation.target.get().getTargetSample().getTkSclkCoarse(),
                                    0
                            )
                    )
            );
        }

        {
            final Optional<Double> tdtSErrorWarningThreshold = ctx.config.getTdtSErrorWarningThresholdMs();
            if (tdtSErrorWarningThreshold.isPresent()) {
                newThfRec.setValue(
                        TimeHistoryFile.TDT_S_ERROR_WARNING_THRESHOLD,
                        String.valueOf(tdtSErrorWarningThreshold.get())
                );
            } else {
                newThfRec.setValue(
                        TimeHistoryFile.TDT_S_ERROR_WARNING_THRESHOLD,
                        "-"
                );
            }
        }

        // write downlinked parameter values to Time History File
        newThfRec.setValue(TimeHistoryFile.SCLK_1,                    () -> String.valueOf(ctx.ancillary.gnc.sclk_1.get()));
        newThfRec.setValue(TimeHistoryFile.TDT_1,                     () -> String.valueOf(ctx.ancillary.gnc.tdt_1.get()));
        newThfRec.setValue(TimeHistoryFile.TDT_1_STRING,              () -> {
            try {
                return TimeConvert.tdtToTdtStr(ctx.ancillary.gnc.tdt_1.get());
            } catch (TimeConvertException e) {
                throw new RuntimeException(e);
            }
        });

        newThfRec.setValue(TimeHistoryFile.CLK_CHANGE_RATE_FOR_TDT_S, () -> String.valueOf(ctx.ancillary.gnc.clk_change_rate_for_tdt_s.get()));

        // write downlinked SCLK input and TDT(S) telemetry values, which are paired inputs and results from onboard TDT(S) conversions
        newThfRec.setValue(TimeHistoryFile.SCLK_FOR_TDT_S,            () -> String.valueOf(ctx.ancillary.gnc.sclk_for_tdt_s.get()));
        newThfRec.setValue(TimeHistoryFile.TDT_S,                     () -> String.format("%.6f", ctx.ancillary.gnc.tdt_s.get()));
        newThfRec.setValue(TimeHistoryFile.TDT_S_STR,                 () -> {
            try {
                return TimeConvert.tdtToTdtStr(ctx.ancillary.gnc.tdt_s.get());
            } catch (TimeConvertException e) {
                throw new RuntimeException(e);
            }
        });

        // If GNC SCLK *and* TDT(S) were obtained from GNC telemetry, compute the TDT(S) Error, and create warning messages if applicable
        if (ctx.ancillary.gnc.tdt_s_error_ms.isSet()) {
            newThfRec.setValue(
                    TimeHistoryFile.TDT_S_ERROR,
                    new DecimalFormat("0.000000").format(ctx.ancillary.gnc.tdt_s_error_ms.get())
            );
        }

        // Set the Warnings in the TimeHistoryFile (nominally, these will be blank).
        if (! ctx.getWarnings().isEmpty()) {
            newThfRec.setValue(TimeHistoryFile.WARNING, String.join(";", ctx.getWarnings()));
        } else {
            newThfRec.setValue(TimeHistoryFile.WARNING, "");
        }

        newThfRec.setValue(TimeHistoryFile.RADIO_ID,                  ctx.ancillary.active_radio_id.get());
        newThfRec.setValue(TimeHistoryFile.OSCILLATOR,                ctx.ancillary.oscillator_id.get());
        newThfRec.setValue(TimeHistoryFile.OSCILLATOR_TEMP_DEGC,      String.valueOf(ctx.ancillary.oscillator_temperature_deg_c.get()));
    }

    /**
     * Writes the next record for the Time History File (and updates the prior interpolated clock change rate, if applicable)
     *
     * @throws MmtcException if an unhandled MMTC configuration or other operation from the helper function fails
     * @throws TimeConvertException if a time conversion operation fails
     */
    private static void writeNewRow(TimeCorrelationContext ctx, TimeHistoryFile timeHistoryFile, TableRecord newThfRec) throws MmtcException, TimeConvertException, TextProductException {
        generateNewTimeHistRec(ctx, timeHistoryFile, newThfRec);
        timeHistoryFile.writeRecord(newThfRec);
    }
}
