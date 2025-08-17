package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable;

public class RawTlmTableProductDefinition extends AppendedFileOutputProductDefinition {
    public RawTlmTableProductDefinition() {
        super("RawTlmTable");
    }

    @Override
    public ResolvedProductPath resolveLocation(RollbackConfig config) {
        return new ResolvedProductPath(
                config.getRawTelemetryTablePath(),
                new RawTelemetryTable(config.getRawTelemetryTablePath())
        );
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return true;
    }

    @Override
    public ProductWriteResult writeToProduct(TimeCorrelationContext context) throws MmtcException {
        final RawTelemetryTable rawTlmTable = new RawTelemetryTable(context.config.getRawTelemetryTablePath());

        rawTlmTable.writeRawTelemetryTable(
                context.correlation.target.get().getSampleSet(),
                context.appRunTime

        );

        return new ProductWriteResult(
                context.config.getRawTelemetryTablePath(),
                rawTlmTable.getLastLineNumber()
        );
    }
}
