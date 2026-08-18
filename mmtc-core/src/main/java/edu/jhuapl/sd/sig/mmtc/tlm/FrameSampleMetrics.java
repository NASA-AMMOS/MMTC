package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

public class FrameSampleMetrics {
    public final double tdtG;
    public final OffsetDateTime scetUtc;
    public final double scetErrorNanos;
    public final double owltSec;

    public FrameSampleMetrics(double tdtG, OffsetDateTime scetUtc, double scetErrorNanos, double owltSec) {
        this.tdtG = tdtG;
        this.scetUtc = scetUtc;
        this.scetErrorNanos = scetErrorNanos;
        this.owltSec = owltSec;
    }

    public double getScetErrorMs() {
        return new BigDecimal(scetErrorNanos).divide(new BigDecimal(TimeConvert.NS_PER_MS), TimeConvert.MC).doubleValue();
    }

    @Override
    public String toString() {
        return "FrameSampleMetrics{" +
                "tdtG=" + tdtG +
                ", scetUtc=" + scetUtc +
                ", scetErrorNanos=" + scetErrorNanos +
                ", owltSec=" + owltSec +
                '}';
    }
}
