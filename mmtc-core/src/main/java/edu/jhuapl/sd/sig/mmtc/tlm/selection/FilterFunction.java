package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationTarget;

@FunctionalInterface
public interface FilterFunction {
    Boolean apply(TimeCorrelationTarget timeCorrelationTarget) throws MmtcException;
}
