package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.util.HashMap;
import java.util.Map;

public abstract class MmtcConfigWithTlmSource extends MmtcConfig {
    protected final TelemetrySource telemetrySource;

    public MmtcConfigWithTlmSource() throws Exception {
        super();
        this.telemetrySource = this.initTlmSource();
    }

    public MmtcConfigWithTlmSource(MmtcConfigWithTlmSource config) {
        super(config);
        this.telemetrySource = config.telemetrySource;
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }
}
