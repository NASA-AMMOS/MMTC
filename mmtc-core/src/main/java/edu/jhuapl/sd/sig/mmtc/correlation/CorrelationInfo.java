package edu.jhuapl.sd.sig.mmtc.correlation;

import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.time.OffsetDateTime;

public class CorrelationInfo {
    public final CorrelationMetrics metrics = new CorrelationMetrics();

    // downlink OWLT from SC to station
    public final Settable<Double> owlt_sec = new Settable<>();

    // time correlation target information, including the sample set, the chosen FrameSample, and some computed information
    public final Settable<TimeCorrelationTarget> target = new Settable<>();

    public final Settable<TimeCorrelationAppConfig.ClockChangeRateMode> actual_clock_change_rate_mode = new Settable<>();
    public final Settable<Double> predicted_clock_change_rate = new Settable<>();
    public final Settable<Double> interpolated_clock_change_rate = new Settable<>();

    public Settable<SclkKernel.CorrelationTriplet> smoothingTriplet = new Settable<>();

    // other computed information
    public final Settable<Double> sclk_drift_ms_per_day = new Settable<>();
    public final Settable<String> equivalent_scet_utc_for_tdt_g_iso_doy = new Settable<>();
    public final Settable<OffsetDateTime> equivalent_scet_utc_for_tdt_g = new Settable<>();
}
