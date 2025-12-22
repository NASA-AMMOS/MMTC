package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface TimeCorrelationRunConfigInputSupplier {
    TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(List<TelemetrySource.AdditionalOption> additionalTlmSourceOptions) throws Exception;
}
