package edu.jhuapl.sd.sig.mmtc.correlation;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.Settable;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TimeCorrelationContext {
    public final CorrelationInfo correlation = new CorrelationInfo();
    public final GeometryInfo geometry = new GeometryInfo();
    public final AncillaryInfo ancillary = new AncillaryInfo();

    public final TimeCorrelationRunConfig config;
    public final TelemetrySource telemetrySource;
    public final OffsetDateTime appRunTime;
    public final Settable<Integer> runId = new Settable<>();

    public final Settable<SclkKernel> currentSclkKernel = new Settable<>();
    public final Settable<SclkKernel> newSclkKernel = new Settable<>();
    public final Settable<String> newSclkVersionString = new Settable<>();
    public final Settable<Path> newSclkKernelPath = new Settable<>();
    public final Settable<Integer> tk_sclk_fine_tick_modulus = new Settable<>();
    public final Settable<Integer> sclk_kernel_fine_tick_modulus = new Settable<>();

    private final List<String> warnings;

    public TimeCorrelationContext(final TimeCorrelationRunConfig config) {
        this.config = config;
        this.telemetrySource = config.getTelemetrySource();
        this.warnings = new ArrayList<>();
        this.appRunTime = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void addWarning(String msg) {
        warnings.add(msg);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
