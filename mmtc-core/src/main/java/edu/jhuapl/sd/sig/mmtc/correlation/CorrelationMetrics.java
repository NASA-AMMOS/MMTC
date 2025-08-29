package edu.jhuapl.sd.sig.mmtc.correlation;

import edu.jhuapl.sd.sig.mmtc.util.Settable;

public class CorrelationMetrics {
    // length of time since the prior correlation, in days
    public final Settable<Double> dt = new Settable<>();

    // error in prediction
    public final Settable<Double> ep_ms = new Settable<>();

    // TDT error (ms) / TDT interval
    public final Settable<Double> ep_dt = new Settable<>();
}
