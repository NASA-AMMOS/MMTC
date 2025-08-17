package edu.jhuapl.sd.sig.mmtc.correlation;

import edu.jhuapl.sd.sig.mmtc.util.Settable;

public class GncInfo {
    public final Settable<Double> sclk_1 = new Settable<>();
    public final Settable<Double> tdt_1 = new Settable<>();
    public final Settable<Double> clk_change_rate_for_tdt_s = new Settable<>();
    public final Settable<Double> sclk_for_tdt_s = new Settable<>();
    public final Settable<Double> tdt_s = new Settable<>();

    public final Settable<Double> tdt_s_error_ms = new Settable<>();
}
