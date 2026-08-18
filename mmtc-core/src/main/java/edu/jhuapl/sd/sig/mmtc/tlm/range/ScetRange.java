package edu.jhuapl.sd.sig.mmtc.tlm.range;

import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;

import java.time.OffsetDateTime;

public class ScetRange extends OffsetDateTimeRange {
    public ScetRange(OffsetDateTime start, OffsetDateTime stop) {
        super(start, stop);
    }
}
