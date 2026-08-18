package edu.jhuapl.sd.sig.mmtc.tlm.range;

import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;

import java.time.OffsetDateTime;

public class ErtRange extends OffsetDateTimeRange {
    public ErtRange(OffsetDateTime start, OffsetDateTime stop) {
        super(start, stop);
    }
}
