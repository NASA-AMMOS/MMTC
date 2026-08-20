package edu.jhuapl.sd.sig.mmtc.trending;

import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.time.OffsetDateTime;

public class TrendingStatsSinceLastCorrelation{
    public final Settable<String> currentSclkKernelDescription = new Settable<>();
    public final Settable<Double> currentSclkKernelLastTripletAgeDays = new Settable<>();

    public final Settable<Integer> numSamplesTrended = new Settable<>();

    public final Settable<OffsetDateTime> priorScetUtcForSclkToScetErrorMsCalc = new Settable<>();
    public final Settable<Double> priorSclkToScetErrorMs = new Settable<>();

    public final Settable<OffsetDateTime> latestScetUtcForSclkToScetErrorMsCalc = new Settable<>();
    public final Settable<Double> latestSclkToScetErrorMs = new Settable<>();

    public final Settable<OffsetDateTime> estimatedTimeAtWhichScetErrorThresholdWillBeReached = new Settable<>();

    public final Settable<Double> tdtSErrorMs = new Settable<>();
    public final Settable<OffsetDateTime> estimatedTimeAtWhichTdtSErrorThresholdWillBeReached = new Settable<>();
}
