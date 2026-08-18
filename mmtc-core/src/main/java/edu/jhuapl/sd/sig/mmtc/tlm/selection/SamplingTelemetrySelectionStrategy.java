package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.NoTelemetryFoundException;
import edu.jhuapl.sd.sig.mmtc.app.TelemetryQualityException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class SamplingTelemetrySelectionStrategy extends TelemetrySelectionStrategy {

    private static final Logger logger = LogManager.getLogger();

    public SamplingTelemetrySelectionStrategy(MmtcConfigWithTlmSource config, TelemetrySelectionAndAdjustmentOptions tlmOptions) {
        super(config, tlmOptions);

        if (! tlmOptions.getTargetSampleInputErtMode().equals(TimeCorrelationRunConfig.TargetSampleInputErtMode.RANGE)) {
            throw new IllegalStateException("This telemetry selection strategy only supports querying over an ERT range for a target sample");
        }
    }

    @Override
    public TimeCorrelationTarget get(FilterFunction filterFunction) throws MmtcException {
        // generate all possible query ranges
        List<Pair<OffsetDateTime, OffsetDateTime>> queryRanges = generateQueryRanges();

        if (queryRanges.isEmpty()) {
            throw new MmtcException("Could not generate any valid telemetry query periods within the input time range.  Please either widen the input query time range or decrease the sampling query width.");
        }

        long totalSampleCountFound = 0;

        for (Pair<OffsetDateTime, OffsetDateTime> queryRange : queryRanges) {
            final List<FrameSample> samplesInQueryRange = getSamplesInRange(queryRange.getLeft(), queryRange.getRight());
            totalSampleCountFound += samplesInQueryRange.size();

            if (samplesInQueryRange.size() < config.getSamplesPerSet()) {
                logger.info(USER_NOTICE, String.format("Only %d samples found in range compared to the necessary %d; continuing...", samplesInQueryRange.size(), config.getSamplesPerSet()));
                continue;
            }

            final List<FrameSample> sampleSet = new ArrayList<>(samplesInQueryRange.subList(samplesInQueryRange.size() - config.getSamplesPerSet(), samplesInQueryRange.size()));

            final TimeCorrelationTarget tcTarget = new TimeCorrelationTarget(sampleSet, config, tlmOptions);

            if (filterFunction.apply(tcTarget)) {
                logger.info(USER_NOTICE, "The candidate sample set passed all filters and is valid. MMTC will use it as the sample set for time correlation.");
                return tcTarget;
            } else {
                logger.warn("Discarding the candidate sample set because it didn't pass all filters");
            }
        }

        if (totalSampleCountFound == 0) {
            throw new NoTelemetryFoundException("No telemetry found within query window");
        } else {
            throw new TelemetryQualityException(String.format("All %d samples found did not pass filters or validation", totalSampleCountFound));
        }
    }

    private List<Pair<OffsetDateTime, OffsetDateTime>> generateQueryRanges() throws MmtcException {
        return generateQueryRanges(tlmOptions.getResolvedTargetSampleRange().get().getStart(), tlmOptions.getResolvedTargetSampleRange().get().getStop());
    }

    private List<Pair<OffsetDateTime, OffsetDateTime>> generateQueryRanges(OffsetDateTime queryStart, OffsetDateTime queryStop) throws MmtcException {
        if (! (config.getSamplingSampleSetBuildingStrategyQueryWidthMinutes() <= config.getSamplingSampleSetBuildingStrategySamplingRateMinutes())) {
            throw new MmtcException(String.format(
                    "Sampling telemetry selection strategy requires a query width <= sampling rate, but the configured query width is %d minutes and the sampling rate is %d minutes",
                    config.getSamplingSampleSetBuildingStrategyQueryWidthMinutes(),
                    config.getSamplingSampleSetBuildingStrategySamplingRateMinutes()
                    )
            );
        }

        final List<Pair<OffsetDateTime, OffsetDateTime>> queryRanges = new ArrayList<>();

        OffsetDateTime stop = null;
        OffsetDateTime start;

        // generates periods that:
        // - have the width and frequency given in input configuration, except for possibly the earliest query which is bounded by the input start time
        // - does not query telemetry outside the given input start and stop times
        while (true) {
            if (stop == null) {
                stop = queryStop;
            } else {
                stop = stop.minusMinutes(config.getSamplingSampleSetBuildingStrategySamplingRateMinutes());
            }

            if (! stop.isAfter(queryStart)) {
                break;
            }

            start = stop.minusMinutes(config.getSamplingSampleSetBuildingStrategyQueryWidthMinutes());

            if (start.isBefore(queryStart)) {
                // bound query start time to input start time
                start = queryStart;
            }

            queryRanges.add(Pair.of(start, stop));
        }

        return queryRanges;
    }

    @Override
    public List<TimeCorrelationTarget> getAll(OffsetDateTime queryStartTimeErt, OffsetDateTime queryStopTimeErt, FilterFunction filterFunction) throws MmtcException {
        List<TimeCorrelationTarget> results = new ArrayList<>();

        // generate all possible query ranges
        List<Pair<OffsetDateTime, OffsetDateTime>> queryRanges = generateQueryRanges(queryStartTimeErt, queryStopTimeErt);

        if (queryRanges.isEmpty()) {
            throw new MmtcException("Could not generate any valid telemetry query periods within the input time range.  Please either widen the input query time range or decrease the sampling query width.");
        }

        for (Pair<OffsetDateTime, OffsetDateTime> queryRange : queryRanges) {
            final List<FrameSample> samplesInQueryRange = getSamplesInRange(queryRange.getLeft(), queryRange.getRight());

            if (samplesInQueryRange.size() < config.getSamplesPerSet()) {
                continue;
            }

            final List<FrameSample> sampleSet = new ArrayList<>(samplesInQueryRange.subList(samplesInQueryRange.size() - config.getSamplesPerSet(), samplesInQueryRange.size()));

            final TimeCorrelationTarget tcTarget = new TimeCorrelationTarget(sampleSet, config, tlmOptions);

            if (filterFunction.apply(tcTarget)) {
                results.add(tcTarget);

            }
        }

        return results;
    }
}
