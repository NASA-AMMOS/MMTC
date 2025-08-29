package edu.jhuapl.sd.sig.mmtc.app;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

public class TimeCorrelationAncillaryOperations {
    private static final Logger logger = LogManager.getLogger();

    private final TimeCorrelationContext ctx;

    public TimeCorrelationAncillaryOperations(TimeCorrelationContext ctx) {
        this.ctx = ctx;
    }

    public void perform() throws Exception {
        computeErrorMetrics();
        computeGeometryValues();
        retrieveSpacecraftStateValues();
        retrieveAndComputeGncParams();
    }

    private void computeErrorMetrics() throws TimeConvertException, MmtcException, TextProductException {
        // Compute and record interval between new and previous TDT(G).
        ctx.correlation.metrics.dt.set(computeDt());

        // Compute and record the error in prediction
        ctx.correlation.metrics.ep_ms.set(computeTdtPredictionErrorMs());

        // Compute and record TDT error (ms) / TDT interval
        ctx.correlation.metrics.ep_dt.set(Math.abs(
                ctx.correlation.metrics.ep_ms.get() / ctx.correlation.metrics.dt.get()
        ));
    }

    private double computeDt() throws TimeConvertException, TextProductException {
        // The TDT(G) value can be either a calendar string or a numeric value inside an SCLK kernel
        String tdtGPrevStr = ctx.currentSclkKernel.get().getLastRecValue(SclkKernel.TRIPLET_TDTG_FIELD_INDEX);
        final double tdtGPrev;
        if (SclkKernel.isNumVal(tdtGPrevStr)) {
            tdtGPrev = Double.parseDouble(tdtGPrevStr);
        } else {
            tdtGPrev = TimeConvert.tdtCalStrToTdt(tdtGPrevStr.replace("@", ""));
        }

        return (ctx.correlation.target.get().getTargetSampleTdtG() - tdtGPrev) / TimeConvert.SECONDS_PER_DAY;
    }

    /**
     * Computes the error in TDT, in milliseconds, as predicted by the prior SCLK kernel for the current SCLK.
     *
     * @return the error between an estimated TDT using the prior SCLK kernel to the actual TDT, in milliseconds
     * @throws MmtcException when unable to convert from SCLK to ET or from ET to TDT
     */
    private double computeTdtPredictionErrorMs() throws MmtcException {
        // the encoded SCLK, from the current time correlation run, from which to compute an estimated TDT
        final double actualEncSclk = ctx.correlation.target.get().getTargetSampleEncSclk();

        // the TDT value from the current time correlation run, used as the actual TDT value in computing the error
        final double actualTdt = ctx.correlation.target.get().getTargetSampleTdtG();

        try {
            // these SPICE calls will use the currently-loaded SCLK kernel, which will be the 'prior' one once this run finishes
            double estimatedEtUsingPriorCorrelation  = CSPICE.sct2e(ctx.config.getNaifSpacecraftId(), actualEncSclk);
            double estimatedTdtUsingPriorCorrelation = CSPICE.unitim(estimatedEtUsingPriorCorrelation, "ET", "TDT");
            return (estimatedTdtUsingPriorCorrelation - actualTdt) * TimeConvert.MSEC_PER_SECOND;
            // experimental == estimated - actual/accepted
        } catch (SpiceErrorException ex) {
            throw new MmtcException("Unable to compute TDT error: " + ex.getMessage(), ex);
        }
    }

    private void retrieveSpacecraftStateValues() throws MmtcException {
        final TelemetrySource tlmSource = ctx.telemetrySource;
        final FrameSample targetSample = ctx.correlation.target.get().getTargetSample();

        try {
            tlmSource.connect();

            final String oscillatorId = tlmSource.getActiveOscillatorId(targetSample);
            ctx.ancillary.oscillator_id.set(oscillatorId);

            final double oscillatorTemperature;
            if (! oscillatorId.equals("-")) {
                oscillatorTemperature = tlmSource.getOscillatorTemperature(
                        ctx.correlation.equivalent_scet_utc_for_tdt_g.get(),
                        oscillatorId
                );
            } else {
                oscillatorTemperature = Double.NaN;
            }

            ctx.ancillary.oscillator_temperature_deg_c.set(oscillatorTemperature);
            ctx.ancillary.active_radio_id.set(
                tlmSource.getActiveRadioId(targetSample)
            );
        } finally {
            tlmSource.disconnect();
        }
    }

    private void retrieveAndComputeGncParams() throws MmtcException, TimeConvertException {
        logger.info("Retrieving GNC parameter values and calculating related metrics");

        // create local refs for brevity throughout the rest of this method
        final TimeCorrelationRunConfig config = ctx.config;
        final TelemetrySource tlmSource = ctx.telemetrySource;
        // final FrameSample targetSample = ctx.correlation.target.get().getTargetSample();
        final TimeCorrelationTarget tcTarget = ctx.correlation.target.get();

        final TelemetrySource.GncParms gncParms;
        tlmSource.connect();
        try {
            gncParms = tlmSource.getGncTkParms(ctx.correlation.equivalent_scet_utc_for_tdt_g.get(), tcTarget.getTargetSampleTdtG());
        } finally {
            tlmSource.disconnect();
        }

        ctx.ancillary.gnc.sclk_1.set(gncParms.getSclk1());
        ctx.ancillary.gnc.tdt_1.set(gncParms.getTdt1());
        ctx.ancillary.gnc.clk_change_rate_for_tdt_s.set(gncParms.getClkchgrate1());
        ctx.ancillary.gnc.sclk_for_tdt_s.set(gncParms.getGncSclk());
        ctx.ancillary.gnc.tdt_s.set(gncParms.getTdt_s());

        // If GNC SCLK *and* TDT(S) were obtained from GNC telemetry, compute the TDT(S) Error, and create warning messages if applicable
        final Double gnc_sclk_for_tdt_s = gncParms.getGncSclk();
        final Double gnc_tdt_s = gncParms.getTdt_s();
        if (gnc_tdt_s.isNaN() || gnc_sclk_for_tdt_s.isNaN()) {
            logger.warn("No value for the GNC SCLK and/or GNC TDT(S) was obtained from telemetry.  TDT(S) Error not computed.");
        } else if ((gnc_tdt_s < 0) || (gnc_sclk_for_tdt_s < 0)) {
            logger.warn("The collected GNC SCLK and/or GNC TDT(S) is of a negative value, possibly indicating it is not set. TDT(S) Error not computed.");
        } else {
            final double tdtSError = computeTdtSErrorMs(
                    gncParms,
                    TimeConvert.encSclkToSclk(config.getNaifSpacecraftId(), TimeConvert.getSclkKernelTickRate(config.getNaifSpacecraftId()), tcTarget.getTargetSampleEncSclk()),
                    tcTarget.getTargetSampleTdtG(),
                    ctx.correlation.predicted_clock_change_rate.get()
            );
            ctx.ancillary.gnc.tdt_s_error_ms.set(tdtSError);

            if (config.getTdtSErrorWarningThresholdMs().isPresent()) {
                if (Math.abs(tdtSError) > ctx.config.getTdtSErrorWarningThresholdMs().get()) {
                    logger.warn(String.format("Magnitude of computed error in TDT(S) of %f exceeds threshold of %f.", tdtSError, config.getTdtSErrorWarningThresholdMs().get()));

                    ctx.addWarning("TDT(S)_Error_threshold_exceeded!");
                }
            } else {
                logger.warn("Not checking TDT(S) Error value against any threshold, as no value was specified for its threshold in MMTC configuration.");
            }
        }
    }

    private void computeGeometryValues() throws MmtcException, TimeConvertException {
        logger.info("Computing geometrical (distance & velocity) values");
        final String naifScId = String.valueOf(ctx.config.getNaifSpacecraftId());

        double scSunDistKm = Double.NaN;
        double scEarthDistKm = Double.NaN;
        double scVelSsbKmS = Double.NaN;
        double scVelEarthKmS = Double.NaN;
        double earthVelSsbKmS = Double.NaN;
        double earthSunDistKm = Double.NaN;
        double scSunDistAu = Double.NaN;
        double earthSunDistAu = Double.NaN;

        // If in test mode, ephemeris data might not be available, so don't try to compute spacecraft state vectors
        if (ctx.config.isTestMode()) {
            logger.warn("MMTC is operating in test mode. Spacecraft and Earth distance and velocity values will not be computed. They will be recorded as NaN.");
        } else {
            scSunDistKm = computeDistanceKm(naifScId, "SUN");
            scEarthDistKm = computeDistanceKm(naifScId, "EARTH");
            scVelSsbKmS = computeVelocityKmS(naifScId, "SSB");
            scVelEarthKmS = computeVelocityKmS(naifScId, "EARTH");

            try {
                scSunDistAu = CSPICE.convrt(scSunDistKm, "KM", "AU");
            } catch (SpiceErrorException ex) {
                logger.warn("Unable to convert SC-Sun distance to AU: " + ex.getMessage());
                scSunDistAu = -1.0;
            }
        }

        // Compute the Earth-Sun state vectors.
        earthVelSsbKmS = computeVelocityKmS("EARTH", "SSB");
        earthSunDistKm = computeDistanceKm("EARTH", "SUN");

        try {
            earthSunDistAu = CSPICE.convrt(earthSunDistKm, "KM", "AU");
        } catch (SpiceErrorException e) {
            throw new MmtcException("Could not convert Earth-Sun distance from km to AU", e);
        }


        ctx.geometry.scSunDistKm.set(scSunDistKm);
        ctx.geometry.scEarthDistKm.set(scEarthDistKm);
        ctx.geometry.scVelSsbKmS.set(scVelSsbKmS);
        ctx.geometry.scVelEarthKmS.set(scVelEarthKmS);
        ctx.geometry.earthVelSsbKmS.set(earthVelSsbKmS);
        ctx.geometry.earthSunDistKm.set(earthSunDistKm);
        ctx.geometry.scSunDistAu.set(scSunDistAu);
        ctx.geometry.earthSunDistAu.set(earthSunDistAu);
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
    private double[] computeTargetState(String target, String observer) throws TimeConvertException, MmtcException {
        final TimeCorrelationRunConfig config = ctx.config;
        final FrameSample targetSample = ctx.correlation.target.get().getTargetSample();

        final String corr = "NONE";               // Aberration correction
        final String ref = "J2000";               // Reference frame
        double[] state = new double[6];     // State vector; [0, 2] is dist, [3, 5] is velocity
        double[] lightTime = new double[1]; // OWLT between observer and target in seconds

        double et = TimeConvert.sclkToEt(
                config.getNaifSpacecraftId(),
                config.getSclkPartition(TimeConvert.parseIsoDoyUtcStr(targetSample.getErtStr())),
                targetSample.getTkSclkCoarse(),
                new BigDecimal(targetSample.getTkSclkFine())
                        .divide(new BigDecimal(ctx.tk_sclk_fine_tick_modulus.get()), RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(ctx.sclk_kernel_fine_tick_modulus.get()))
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
    private double computeDistanceKm(String target, String observer) throws TimeConvertException, MmtcException {
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
    private double computeVelocityKmS(String target, String observer) throws TimeConvertException, MmtcException {
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
}
