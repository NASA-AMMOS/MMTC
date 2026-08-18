package edu.jhuapl.sd.sig.mmtc.tlm.range;

import java.time.OffsetDateTime;

public class ErtRangeWithMinTdtG extends ErtRange {
    public final double minTdtGExclusive;

    public ErtRangeWithMinTdtG(OffsetDateTime start, OffsetDateTime stop, double minTdtGExclusive) {
        super(start, stop);
        this.minTdtGExclusive = minTdtGExclusive;
    }
}
