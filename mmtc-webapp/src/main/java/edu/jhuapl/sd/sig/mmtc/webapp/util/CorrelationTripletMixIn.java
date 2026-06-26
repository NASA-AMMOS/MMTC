package edu.jhuapl.sd.sig.mmtc.webapp.util;

import com.fasterxml.jackson.annotation.JsonGetter;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

// this class exists only for the reason of providing a field named `tdtStr` with the TDT cal string on serialized CorrelationTriplets
public abstract class CorrelationTripletMixIn {

    @JsonGetter("tdtStr")
    public abstract String getTdtCalStr() throws TimeConvertException;
}
