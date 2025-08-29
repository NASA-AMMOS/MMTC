package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.util.HashMap;
import java.util.Map;

public abstract class MmtcConfigWithTlmSource extends MmtcConfig {
    protected final TelemetrySource telemetrySource;
    protected final Map<String, TelemetrySource.AdditionalOption> additionalTlmSrcOptionsByName;

    public MmtcConfigWithTlmSource() throws Exception {
        super();
        this.telemetrySource = this.initTlmSource();

        this.additionalTlmSrcOptionsByName = new HashMap<>();

        telemetrySource.getAdditionalOptions().forEach(additionalOption -> {
            additionalTlmSrcOptionsByName.put(additionalOption.name, additionalOption);
        });

        this.telemetrySource.applyConfiguration(this);
    }

    public MmtcConfigWithTlmSource(MmtcConfigWithTlmSource config) {
        super(config);
        this.telemetrySource = config.telemetrySource;
        this.additionalTlmSrcOptionsByName = config.additionalTlmSrcOptionsByName;
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }

    public abstract String getAdditionalOptionValue(String optionName);
}
