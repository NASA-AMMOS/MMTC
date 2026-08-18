package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.NoTelemetryFoundException;
import edu.jhuapl.sd.sig.mmtc.app.TelemetryQualityException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class WindowingTelemetrySelectionStrategy extends TelemetrySelectionStrategy {
    private static final Logger logger = LogManager.getLogger();
    private final int windowSlidingIncrement;

    private WindowingTelemetrySelectionStrategy(MmtcConfigWithTlmSource config, TelemetrySelectionAndAdjustmentOptions tlmOptions, int windowSlidingIncrement) {
        super(config, tlmOptions);
        this.windowSlidingIncrement = windowSlidingIncrement;
    }

    public static WindowingTelemetrySelectionStrategy forSeparateConsecutiveWindows(MmtcConfigWithTlmSource config, TelemetrySelectionAndAdjustmentOptions tlmOptions) {
        return new WindowingTelemetrySelectionStrategy(config, tlmOptions, config.getSamplesPerSet());
    }

    public static WindowingTelemetrySelectionStrategy forSlidingWindow(MmtcConfigWithTlmSource config, TelemetrySelectionAndAdjustmentOptions tlmOptions) {
        return new WindowingTelemetrySelectionStrategy(config, tlmOptions, 1);
    }

    /**
     * Retrieve a valid subset of SCLK/ERT records that passes all filters and
     * can be used for further processing.
     *
     * The list of samples retrieved from the telemetry source are assumed to
     * be in ascending chronological order. Candidate sample sets are pulled
     * from the list and put through a series of filters to determine quality
     * for time correlation processing. The sets are pulled starting from the
     * end of the list back to the beginning until one passes all filters.
     *
     * This function puts candidate sample
     * sets through a series of filters, where the sets are pulled from a list
     *
     * @return the sample set as a list of SCLK/ERT records
     * @throws MmtcException when the sample set is unable to be built
     */
    @Override
    public TimeCorrelationTarget get(FilterFunction filterFunction) throws MmtcException {
        final int samplesPerSet = config.getSamplesPerSet();

        TimeCorrelationTarget tcTarget;

        // Find a valid sample set:
        // 1) Retrieve a list of all samples in range.
        // 2a) Take the first or last N samples that have not been rejected by filters, where N is the configuration-specified number of samples per sample set.
        // 2b) Run this candidate sample set through the filters.
        // 2c) If any filter fails, reject all samples in the candidate sample set.
        // 3) Repeat step 2 until a candidate sample set passes all filters or no samples are left.
        //
        // Samples in range can be large, so this might not be an efficient approach.  SamplingTelemetrySelectionStrategy provides an alternate implementation.

        final OffsetDateTime queryStartTime;
        final OffsetDateTime queryStopTime;
        if (tlmOptions.getTargetSampleInputErtMode().equals(TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE)) {
            queryStartTime = tlmOptions.getResolvedTargetSampleErtRange().get().getStart();
            queryStopTime = tlmOptions.getResolvedTargetSampleErtRange().get().getStop();
        } else if (tlmOptions.getTargetSampleInputErtMode().equals(TimeCorrelationRunConfig.TargetSampleInputErtMode.EXACT)) {
            final int targetSampleExactErtSupplementalQueryWindowMin = config.getTargetSampleExactErtSupplementalQueryWindowMin();
            queryStartTime = tlmOptions.getResolvedTargetSampleExactErt().get().minus(targetSampleExactErtSupplementalQueryWindowMin, ChronoUnit.MINUTES);
            queryStopTime = tlmOptions.getResolvedTargetSampleExactErt().get().plus(targetSampleExactErtSupplementalQueryWindowMin, ChronoUnit.MINUTES);
        } else {
            throw new IllegalStateException();
        }

        final List<FrameSample> samplesInRange = getSamplesInRange(queryStartTime, queryStopTime);
        final int numSamplesInRange = samplesInRange.size();

        if (numSamplesInRange == 0) {
            throw new NoTelemetryFoundException("No telemetry found within query window");
        }

        if (numSamplesInRange < samplesPerSet) {
            throw new TelemetryQualityException(
                    String.format("Not enough frames found within the query interval to build a sample set. A sample set requires %d frames; %d were found.", samplesPerSet, numSamplesInRange)
            );
        }

        logger.info(String.format("The query interval contains %d frames. Attempting to find a valid sample set within those frames...", numSamplesInRange));

        final TimeCorrelationRunConfig.TargetSampleRangeErtSeekOrder seekOrder = tlmOptions.getTargetSampleRangeErtSeekOrder();

        int sampleFromIndex;
        int sampleToIndex;
        switch(seekOrder) {
            case DESCENDING:
                sampleFromIndex = numSamplesInRange - samplesPerSet;
                sampleToIndex = numSamplesInRange;
                break;
            case ASCENDING:
                sampleFromIndex = 0;
                sampleToIndex = samplesPerSet;
                break;
            default:
                throw new IllegalStateException("Unexpected seek order: " + seekOrder);
        }

        while (true) {
            List<FrameSample> sampleSet;

            if (sampleFromIndex >= 0 && sampleToIndex <= numSamplesInRange) {
                if (samplesPerSet == 1) {
                    logger.info(String.format("Creating new candidate sample set using frame %d", sampleFromIndex + 1));
                } else {
                    logger.info(String.format("Creating new candidate sample set using frames %d to %d", sampleFromIndex + 1, sampleToIndex));
                }
                sampleSet = new ArrayList<>(samplesInRange.subList(sampleFromIndex, sampleToIndex));

                switch(seekOrder) {
                    case DESCENDING:
                        sampleFromIndex -= windowSlidingIncrement;
                        sampleToIndex -= windowSlidingIncrement;
                        break;
                    case ASCENDING:
                        sampleFromIndex += windowSlidingIncrement;
                        sampleToIndex += windowSlidingIncrement;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected seek order: " + seekOrder);
                }
            } else {
                throw new TelemetryQualityException("Not enough frames from the query interval remaining.");
            }

            tcTarget = new TimeCorrelationTarget(sampleSet, config, tlmOptions);

            if (tlmOptions.getTargetSampleInputErtMode().equals(TimeCorrelationRunConfig.TargetSampleInputErtMode.EXACT)) {
                final OffsetDateTime desiredTargetFrameErt = tlmOptions.getResolvedTargetSampleExactErt().get();

                if (TimeConvert.parseIsoDoyUtcStr(tcTarget.getTargetSample().getErtStr()).equals(desiredTargetFrameErt)) {
                    logger.info("The candidate sample matches the desired ERT");
                } else {
                    logger.info("Discarding the candidate sample set because it does not match the desired ERT");
                    continue;
                }
            }

            if (filterFunction.apply(tcTarget)) {
                logger.info(USER_NOTICE, "The candidate sample set passed all filters and is valid. MMTC will use it as the sample set for time correlation.");
                break;
            } else {
                logger.warn("Discarding the candidate sample set because it didn't pass all filters");
            }
        }

        return tcTarget;
    }

    @Override
    public List<TimeCorrelationTarget> getAll(OffsetDateTime queryStartTimeErt, OffsetDateTime queryStopTimeErt, FilterFunction filterFunction) throws MmtcException {
        List<TimeCorrelationTarget> results = new ArrayList<>();

        final int samplesPerSet = config.getSamplesPerSet();

        final List<FrameSample> samplesInRange = getSamplesInRange(queryStartTimeErt, queryStopTimeErt);
        final int numSamplesInRange = samplesInRange.size();

        if (numSamplesInRange == 0) {
            // todo can I remove this? leaves concerning messages in stdout when trending
            // logger.warn("No telemetry found within query window");
            return results;
        }

        int sampleToIndex = numSamplesInRange;

        if (numSamplesInRange < samplesPerSet) {
            logger.warn(String.format("Not enough frames found within the query interval to build a sample set. A sample set requires %d frames; %d were found.", samplesPerSet, numSamplesInRange));
            return results;
        }

        logger.info(String.format("The query interval contains %d frames.", numSamplesInRange));

        while (true) {
            List<FrameSample> sampleSet;

            int sampleFromIndex = sampleToIndex - samplesPerSet;

            if (sampleFromIndex >= 0) {
                if (samplesPerSet == 1) {
                    logger.info(String.format("Creating new candidate sample set using frame %d", sampleFromIndex + 1));
                } else {
                    logger.info(String.format("Creating new candidate sample set using frames %d to %d", sampleFromIndex + 1, sampleToIndex));
                }
                sampleSet = new ArrayList<>(samplesInRange.subList(sampleFromIndex, sampleToIndex));
                sampleToIndex -= windowSlidingIncrement;
            } else {
                return results;
            }

            final TimeCorrelationTarget tcTarget = new TimeCorrelationTarget(sampleSet, config, tlmOptions);

            if (filterFunction.apply(tcTarget)) {
                results.add(tcTarget);
            }
        }
    }
}
