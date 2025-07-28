package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MmtcSandboxCreatorConfig extends MmtcConfig {
    private final TelemetrySource telemetrySource;

    public MmtcSandboxCreatorConfig(String... args) throws Exception {
        super();

        this.telemetrySource = this.initTlmSource();
    }

    public Path getNewSandboxPath() {
        // todo parse Path newSandboxPath out of this
        return Paths.get(".");
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }
}
