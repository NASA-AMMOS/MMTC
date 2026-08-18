package edu.jhuapl.sd.sig.mmtc.correlation.config;

import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;

import java.util.List;

@FunctionalInterface
public interface TimeCorrelationRunConfigInputSupplier {
    TimeCorrelationRunConfig.TimeCorrelationRunConfigInputs getRunConfigInputs(List<TelemetrySource.AdditionalOption> additionalTlmSourceOptions) throws Exception;
}
