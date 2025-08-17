package edu.jhuapl.sd.sig.mmtc.correlation;

import edu.jhuapl.sd.sig.mmtc.util.Settable;

public class GeometryInfo {
    public final Settable<Double> scSunDistKm = new Settable<>();
    public final Settable<Double> scEarthDistKm = new Settable<>();
    public final Settable<Double> scVelSsbKmS = new Settable<>();
    public final Settable<Double> scVelEarthKmS = new Settable<>();
    public final Settable<Double> earthVelSsbKmS = new Settable<>();
    public final Settable<Double> earthSunDistKm = new Settable<>();
    public final Settable<Double> scSunDistAu = new Settable<>();
    public final Settable<Double> earthSunDistAu = new Settable<>();
}
