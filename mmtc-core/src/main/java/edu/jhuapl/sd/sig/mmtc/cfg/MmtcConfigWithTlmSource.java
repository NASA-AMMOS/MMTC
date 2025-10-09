package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.util.HashMap;
import java.util.Map;

public abstract class MmtcConfigWithTlmSource extends MmtcConfig {
    protected final TelemetrySource telemetrySource;
    protected final Map<String, TelemetrySource.AdditionalOption> additionalOptionsByName;

    public MmtcConfigWithTlmSource() throws Exception {
        super();
        this.telemetrySource = this.initTlmSource();

        this.additionalOptionsByName = new HashMap<>();

        telemetrySource.getAdditionalOptions().forEach(additionalOption -> {
            additionalOptionsByName.put(additionalOption.name, additionalOption);
        });

        this.telemetrySource.applyConfiguration(this);
    }

    public MmtcConfigWithTlmSource(MmtcConfigWithTlmSource config) {
        super(config);
        this.telemetrySource = config.telemetrySource;
        this.additionalOptionsByName = config.additionalOptionsByName;
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }

    public abstract String getAdditionalOptionValue(String optionName);
}
