package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RawTlmTableProductDefinition extends AppendedFileOutputProductDefinition {
    public RawTlmTableProductDefinition() {
        super("RawTlmTable");
    }

    @Override
    public ResolvedProductPath resolveLocation(MmtcConfig config) {
        return new ResolvedProductPath(config.getRawTelemetryTablePath());
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return true;
    }

    @Override
    public ProductWriteResult appendToProduct(TimeCorrelationContext context) throws MmtcException {
        return RawTelemetryTable.appendCorrelationFrameSamplesToRawTelemetryTable(context);
    }

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputPath) {
        final Map<String, String> confUpdates = new HashMap<>();
        confUpdates.put("table.rawTelemetryTable.path", newProductOutputPath.toString());
        return confUpdates;
    }
}
