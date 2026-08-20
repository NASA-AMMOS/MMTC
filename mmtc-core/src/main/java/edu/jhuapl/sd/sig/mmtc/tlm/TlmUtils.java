package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.util.Pair;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.now;

public class TlmUtils {
    public static class TlmPoint extends Pair<OffsetDateTime, Double> {
        public TlmPoint(OffsetDateTime timeScetUtc, Double val) {
            super(timeScetUtc, val);
        }

        public OffsetDateTime getTimeScetUtc() {
            return getLeft();
        }

        public Double getVal() {
            return getRight();
        }
    }

    public static OffsetDateTime estimateTimeAtWhichThresholdWillBeViolated(TlmPoint earlierSample, TlmPoint latestSample, double threshold) {
        if (! latestSample.getTimeScetUtc().isAfter(earlierSample.getTimeScetUtc())) {
            throw new IllegalStateException("Second sample provided must be after the earlier sample");
        }

        // if we've already measured it at oast the threshold, then report that it's been violated now
        if (threshold >= 0) {
            if (latestSample.getVal() > threshold) {
                return now();
            }
        } else {
            if (latestSample.getVal() < threshold) {
                return now();
            }
        }

        BigDecimal diffSec = new BigDecimal(Duration.between(earlierSample.getTimeScetUtc(), latestSample.getTimeScetUtc()).getSeconds());
        BigDecimal diffVal = new BigDecimal(latestSample.getVal()).min(new BigDecimal(earlierSample.getVal()));

        BigDecimal valChangeRatePerSec = diffVal.divide(diffSec);

        final BigDecimal differenceUnderThreshold = new BigDecimal(threshold - latestSample.getVal());
        final BigDecimal timeSecTilThresholdIsReached = differenceUnderThreshold.divide(valChangeRatePerSec);

        return latestSample.getTimeScetUtc().plus(Math.round(timeSecTilThresholdIsReached.doubleValue()), ChronoUnit.SECONDS);
    }
}
