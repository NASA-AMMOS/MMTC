package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.products.model.TimeHistoryFile;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Optional;

public class TimeHistoryFileProductDefinition extends AppendedFileOutputProductDefinition {
    public TimeHistoryFileProductDefinition() {
        super("TimeHistoryFile");
    }

    @Override
    public ResolvedProductPath resolveLocation(RollbackConfig config) {
        return new ResolvedProductPath(
                config.getTimeHistoryFilePath(),
                new TimeHistoryFile(config.getTimeHistoryFilePath())
        );
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return true;
    }

    @Override
    public ProductWriteResult writeToProduct(TimeCorrelationContext ctx) throws MmtcException {
        TimeHistoryFile timeHistoryFile = new TimeHistoryFile(ctx.config.getTimeHistoryFilePath(), ctx.config.getTimeHistoryFileExcludeColumns());
        TableRecord newThfRec = new TableRecord(timeHistoryFile.getHeaders());

        try {
            writeRow(ctx, timeHistoryFile, newThfRec);
        } catch (TimeConvertException | TextProductException e) {
            throw new MmtcException(e);
        }

        return new ProductWriteResult(
                timeHistoryFile.getPath(),
                timeHistoryFile.getLastLineNumber()
        );
    }

    /**
     * Write the next record to the time history file.
     *
     * @throws MmtcException when a failure occurs on write
     * @throws TimeConvertException if a time conversion operation failed
     */
    private static void writeRow(TimeCorrelationContext ctx, TimeHistoryFile timeHistoryFile, TableRecord newThfRec) throws MmtcException, TimeConvertException, TextProductException {
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
        newThfRec.setValue(TimeHistoryFile.EP_DT, String.format("%.3f", Math.abs(ctx.correlation.metrics.ep_ms.get() / ctx.correlation.metrics.dt.get())));

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

        timeHistoryFile.writeRecord(newThfRec);
    }
}
