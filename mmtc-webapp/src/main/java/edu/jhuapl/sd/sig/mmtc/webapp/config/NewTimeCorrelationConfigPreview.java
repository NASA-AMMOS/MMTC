package edu.jhuapl.sd.sig.mmtc.webapp.config;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfigInputSupplier;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public class NewTimeCorrelationConfigPreview extends NewTimeCorrelationConfig {
    public OffsetDateTime beginTime;
    public OffsetDateTime endTime;
}
