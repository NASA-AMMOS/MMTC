package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.util.Pair;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

public class TlmUtils {
    private static final Logger logger = LogManager.getLogger();

    public static class TlmPoint extends Pair<OffsetDateTime, BigDecimal> {
        public TlmPoint(OffsetDateTime timeScetUtc, BigDecimal val) {
            super(timeScetUtc, val);
        }

        public OffsetDateTime getTimeScetUtc() {
            return getLeft();
        }

        public BigDecimal getVal() {
            return getRight();
        }

        @Override
        public String toString() {
            return "TlmPoint{" +
                    "time=" + getTimeScetUtc() +
                    ", val=" + getVal() +
                    '}';
        }
    }

    public static class TrendingAndThresholdInformation {
        public final BigDecimal valChangeRatePerDay;
        public final Optional<OffsetDateTime> expectedScetAtWhichThresholdWillBeViolated;
        public final boolean thresholdAlreadyViolated;

        public TrendingAndThresholdInformation(BigDecimal valChangeRatePerMs, OffsetDateTime expectedScetAtWhichThresholdWillBeViolated, boolean thresholdAlreadyViolated) {
            this.valChangeRatePerDay = valChangeRatePerMs.multiply(new BigDecimal(TimeConvert.MS_PER_DAY), TimeConvert.MC);
            this.expectedScetAtWhichThresholdWillBeViolated = Optional.ofNullable(expectedScetAtWhichThresholdWillBeViolated);
            this.thresholdAlreadyViolated = thresholdAlreadyViolated;
        }
    }

    public static TrendingAndThresholdInformation estimateTimeAtWhichThresholdWillBeViolated(TlmPoint earlierSample, TlmPoint latestSample, double threshold) {
        logger.info("Calculating when threshold may be violated...");

        if (! latestSample.getTimeScetUtc().isAfter(earlierSample.getTimeScetUtc())) {
            throw new IllegalStateException("Second sample provided must be after the earlier sample");
        }

        logger.info("Earlier sample: " + earlierSample);
        logger.info("Latest sample: " + latestSample);
        logger.info("Threshold: +-" + threshold);

        final double positiveThreshold = Math.abs(threshold);
        final double negativeThreshold = -1 * positiveThreshold;

        final BigDecimal deltaTimeMs = new BigDecimal(Duration.between(earlierSample.getTimeScetUtc(), latestSample.getTimeScetUtc()).toNanos()).divide(new BigDecimal(TimeConvert.NS_PER_MS), TimeConvert.MC);
        final BigDecimal deltaVal = latestSample.getVal().subtract(earlierSample.getVal(), TimeConvert.MC);

        logger.info("Value changed by " + deltaVal.doubleValue() + " over " +  deltaTimeMs.doubleValue() + " milliseconds.");
        final BigDecimal valChangeRatePerMs = deltaVal.divide(deltaTimeMs, TimeConvert.MC);
        logger.info("This is a change of " + valChangeRatePerMs.doubleValue() + " per millisecond.");

        // if we've already measured it to be past the threshold, then report that it's been violated now
        if (latestSample.getVal().doubleValue() > positiveThreshold) {
            return new TrendingAndThresholdInformation(
                    valChangeRatePerMs,
                    latestSample.getTimeScetUtc(),
                    true
            );
        }

        if (latestSample.getVal().doubleValue() < negativeThreshold) {
            return new TrendingAndThresholdInformation(
                    valChangeRatePerMs,
                    latestSample.getTimeScetUtc(),
                    true
            );
        }


        // if the value change per sec is 0, then return here since we can't calculate a time it'll reach a different value
        if (valChangeRatePerMs.compareTo(BigDecimal.ZERO) == 0) {
            return new TrendingAndThresholdInformation(
                    BigDecimal.ZERO,
                    null,
                    false
            );
        }

        final BigDecimal timeTilThresholdIsReachedMs;
        if (valChangeRatePerMs.compareTo(BigDecimal.ZERO) > 0) {
            // positive error accumulation
            final BigDecimal differenceUnderPositiveThreshold = new BigDecimal(positiveThreshold).subtract(latestSample.getVal(), TimeConvert.MC);
            logger.info("The latest value is " + differenceUnderPositiveThreshold.doubleValue() + " away from the threshold.");
            timeTilThresholdIsReachedMs = differenceUnderPositiveThreshold.divide(valChangeRatePerMs, TimeConvert.MC);
            logger.info("At this rate, the threshold will be violated in " + timeTilThresholdIsReachedMs.doubleValue() + " milliseconds.");
        } else {
            // negative error accumulation
            final BigDecimal differenceAboveNegativeThreshold = new BigDecimal(negativeThreshold).subtract(latestSample.getVal(), TimeConvert.MC);
            logger.info("The latest value is " + differenceAboveNegativeThreshold.doubleValue() + " away from the threshold.");
            timeTilThresholdIsReachedMs = differenceAboveNegativeThreshold.divide(valChangeRatePerMs, TimeConvert.MC);
            logger.info("At this rate, the threshold will be violated in " + timeTilThresholdIsReachedMs.doubleValue() + " milliseconds.");
        }

        if (! (timeTilThresholdIsReachedMs.compareTo(BigDecimal.ZERO) > 0)) {
            throw new IllegalStateException("Unexpected negative time duration until threshold is violated");
        }

        final OffsetDateTime expectedScetAtWhichThresholdMayBeViolated = latestSample.getTimeScetUtc().plusNanos(
                timeTilThresholdIsReachedMs.multiply(new BigDecimal(TimeConvert.NS_PER_MS), TimeConvert.MC).longValue()
        );
        logger.info("Expected time at which threshold may be violated: " + expectedScetAtWhichThresholdMayBeViolated);

        return new TrendingAndThresholdInformation(
                valChangeRatePerMs,
                expectedScetAtWhichThresholdMayBeViolated,
                false
        );
    }
}
