package edu.jhuapl.sd.sig.mmtc.tlm;

public class FrameSampleAndMetrics {
    public final FrameSample frameSample;
    public final FrameSampleMetrics metrics;

    public FrameSampleAndMetrics(FrameSample frameSample, FrameSampleMetrics metrics) {
        this.frameSample = frameSample;
        this.metrics = metrics;
    }
}
