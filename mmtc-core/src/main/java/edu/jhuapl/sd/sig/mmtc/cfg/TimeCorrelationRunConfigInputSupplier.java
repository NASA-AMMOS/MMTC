package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.util.Map;

@FunctionalInterface
public interface TimeCorrelationRunConfigInputSupplier {
    TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(Map<String, TelemetrySource.AdditionalOption> additionalTlmSrcOptionsByName) throws Exception;
}
