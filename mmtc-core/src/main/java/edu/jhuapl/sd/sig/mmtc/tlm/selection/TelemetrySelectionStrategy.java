package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSampleValidator;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

public abstract class TelemetrySelectionStrategy {
    public enum SampleSetBuildingStrategy {
        SEPARATE_CONSECUTIVE_WINDOWS,
        SLIDING_WINDOW,
        SAMPLING
    }

    private static final Logger logger = LogManager.getLogger();

    private final TelemetrySource tlmSource;

    protected final TimeCorrelationRunConfig config;
    protected final int tk_sclk_fine_tick_modulus;


    public TelemetrySelectionStrategy(TimeCorrelationRunConfig config, TelemetrySource tlmSource, int tk_sclk_fine_tick_modulus) {
        this.tlmSource = tlmSource;
        this.config = config;
        this.tk_sclk_fine_tick_modulus = tk_sclk_fine_tick_modulus;
    }

    public abstract TimeCorrelationTarget get(FilterFunction filterFunction) throws MmtcException;

    /**
     * Retrieve all samples in the desired time range.
     *
     * @param start the start time, inclusive, for which to query telemetry
     * @param stop the stop time, exclusive, for which to query telemetry
     *
     * @return the sample set as a list of SCLK/ERT records
     * @throws MmtcException when a failure occurs in the retrieval
     */
    protected List<FrameSample> getSamplesInRange(OffsetDateTime start, OffsetDateTime stop) throws MmtcException {
        final List<FrameSample> samples;

        try {
            logger.info(String.format("Querying telemetry source from %s to %s", start, stop));
            samples = tlmSource.getSamplesInRange(start, stop);

            logLatestNFrames( Level.DEBUG, "A portion of the latest frames returned from query range, before enrichment and validation", samples, 10);
            logLatestNFrames( Level.TRACE, "All frames returned from query range, before enrichment and validation", samples, samples.size());

            enrichAndValidateFrameSamples(samples);

            logLatestNFrames( Level.DEBUG, "A portion of the latest frames returned from query range, after enrichment and validation", samples, 10);
            logLatestNFrames( Level.TRACE, "All frames returned from query range, after enrichment and validation", samples, samples.size());
        } catch (MmtcException ex) {
            logger.error("Unable to retrieve samples in desired time range.");
            throw ex;
        }

        return samples;
    }

    private void logLatestNFrames(Level level, String description, List<FrameSample> samples, int samplesToLog) {
        if (samples.isEmpty()) {
            return;
        }

        List<FrameSample> lastFrames = samples.subList(Math.max(0, samples.size() - samplesToLog), samples.size());

        logger.log(level, String.format("%s (%d of %d total returned frames):", description, lastFrames.size(), samples.size()));
        lastFrames.forEach(fs -> logger.log(level, fs));
    }

    private void enrichAndValidateFrameSamples(List<FrameSample> samples) throws MmtcException {
        if (samples.isEmpty()) {
            return;
        }

        // sort by ERT
        samples.sort((one, other) -> {
            try {
                return one.getErt().toTime().compareTo(other.getErt().toTime());
            } catch (TimeConvertException e) {
                throw new RuntimeException(e);
            }
        });

        // ensure downlink data rate is present or calculate it if possible, otherwise fail
        ensureSamplesHaveDownlinkDataRate(samples);

        // compute bitrate-dependent time delay
        for (FrameSample sample : samples) {
            sample.computeAndSetTdBe(config.getFrameErtBitOffsetError());
        }

        // validate that samples have sufficient telemetry for use in time correlation
        FrameSampleValidator.validate(samples, tk_sclk_fine_tick_modulus);
    }

    /**
     * Ensures that all samples have their tkDownlinkDataRate field set.  If they do not, it is computed and assigned
     * if possible.  If not possible, an MmtcException is thrown.
     *
     * @param samples the samples that this method should ensure have a downlink data rate set
     * @throws MmtcException if the given samples do not have a downlink data rate set, and it is impossible to assign them a computed downlink data rate
     */
    private void ensureSamplesHaveDownlinkDataRate(List<FrameSample> samples) throws MmtcException {
        if (samples.stream().allMatch(FrameSample::isTkDataRateSet)) {
            logger.info("Samples have downlink data rate specified from the telemetry source; no need to estimate.");
            return;
        }

        if (samples.stream().allMatch(FrameSample::isFrameSizeBitsSet)) {
            final int firstFrameSizeBits = samples.get(0).getFrameSizeBits();
            if (samples.stream().allMatch(fs -> fs.getFrameSizeBits() == firstFrameSizeBits)) {
                logger.info("Samples do not have downlink data rate specified from the telemetry source; estimating downlink data rate from ERTs & frame sizes");

                for(FrameSample sample : samples) {
                    computeDownlinkDataRate(sample);
                }
            } else {
                throw new MmtcException("Telemetry does not have downlink data rate information and samples do not have a consistent frame size; cannot reliably estimate downlink data rate.");
            }
        } else {
            throw new MmtcException("Telemetry does not have downlink data rate information and samples do not all have a frame size set; cannot estimate downlink data rate.");
        }
    }

    /**
     * Compute a sample's downlink data rate by dividing the amount of data
     * transmitted by the amount of time taken to transmit.
     *
     * The time taken is the difference in ERT between the sample and its
     * supplemental sample. The amount of data is the frame size multiplied by the
     * number of frames between the sample and its supplemental sample.
     *
     * @param sample The sample whose downlink data rate should be computed. The
     *               downlink data rate field gets updated in the sample itself.
     * @throws MmtcException if propagated from a call
     */
    private void computeDownlinkDataRate(FrameSample sample) throws MmtcException {
        if (sample.getErt().isNull() || sample.getSuppErt().isNull()) {
            logger.warn("Can't calculate downlink data rate for frame at ERT " +
                    sample.getErtStr() + ": raw ERT value is unavailable for " +
                    "the frame and/or its supplemental frame.");
        } else {
            // If the downlink data rate is not reported in the TK packet itself, then
            // now that we have the ERT of the current sample as well as of its target
            // sample, calculate the downlink data rate by dividing the amount of data
            // downlinked by the amount of time it took. In other words, take the
            // number of frames from the target sample to the supplemental sample,
            // multiply by the sample size, and divide by the elapsed ERT from the
            // target sample to the supplemental sample.

            // note: this becomes a rougher approximation if supplementalSampleOffset > 1; would need to sum the lengths of all the frames from this (target) frame to its supplemental
            final int frameSizeBits = sample.getFrameSizeBits();
            BigDecimal numBits = BigDecimal.valueOf((long) config.getSupplementalSampleOffset() * frameSizeBits);
            BigDecimal numSeconds = BigDecimal.valueOf(sample.getErt().getDeltaSeconds(sample.getSuppErt()));
            sample.setTkDataRateBps(numBits.divide(numSeconds, 16, RoundingMode.HALF_UP));
        }
    }
}
