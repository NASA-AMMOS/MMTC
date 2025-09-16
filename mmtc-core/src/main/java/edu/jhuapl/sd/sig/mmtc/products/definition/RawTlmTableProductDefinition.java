package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    public String getDryRunPrintout(TimeCorrelationContext ctx) {
        TableRecord rawTlmTableRecord = RawTelemetryTable.calculateUpdatedRawTlmTable(ctx);
        List<String> rtHeaders = new RawTelemetryTable(ctx.config.getRawTelemetryTablePath()).getHeaders();
        Collection<String> rtValues = rawTlmTableRecord.getValues();
        String zippedRtRow = IntStream.range(0, rtHeaders.size())
                .mapToObj(i -> "\t" + rtHeaders.get(i) + "\t:\t"+new ArrayList<>(rtValues)
                        .get(i))
                .collect(Collectors.joining("\n"));
        return String.format("[DRY RUN] Updated Raw TLM table records: \n%s", zippedRtRow);
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
