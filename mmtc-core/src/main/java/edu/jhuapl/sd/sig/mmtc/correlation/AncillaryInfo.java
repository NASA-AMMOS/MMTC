package edu.jhuapl.sd.sig.mmtc.correlation;

import edu.jhuapl.sd.sig.mmtc.util.Settable;

/**
 * Information about the state of various parts of the SC at or close to the time of the correlation
 */
public class AncillaryInfo {
    public final GncInfo gnc = new GncInfo();
    public final Settable<String> active_radio_id = new Settable<>();
    public final Settable<String> oscillator_id = new Settable<>();
    public final Settable<Double> oscillator_temperature_deg_c = new Settable<>();
}
