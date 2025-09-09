package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;

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
    public String getDryRunPrintout(TimeCorrelationContext ctx) {
        TableRecord rawTlmTableRecord = RawTelemetryTable.calculateUpdatedRawTlmTable(ctx);
        return String.format("Updated Raw TLM table records: %s \n %s", new RawTelemetryTable(ctx.config.getRawTelemetryTablePath()).getHeaders(),
                rawTlmTableRecord.getValues().toString());
    }

    @Override
    public ProductWriteResult writeToProduct(TimeCorrelationContext context) throws MmtcException {
        return RawTelemetryTable.appendCorrelationFrameSamplesToRawTelemetryTable(context);
    }
}
