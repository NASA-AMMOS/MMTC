package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.products.model.TimeHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.util.GenericCsv;

import java.nio.file.Files;
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
    public boolean isConfigured(MmtcConfig config) {
        return true;
    }

    @Override
    public void validateExistingState(MmtcConfig config) throws MmtcException {
        if (! Files.exists(config.getRawTelemetryTablePath())) {
            // if the file doesn't exist yet, there's no state to check
            return;
        }

        // ensure that the headers that will be written match the actual headers in the file
        List<String> currentHeaders = new GenericCsv(config.getRawTelemetryTablePath()).getHeaders();

        List<String> colsToWrite = new RawTelemetryTable(config.getRawTelemetryTablePath()).getHeaders();

        if (! currentHeaders.equals(colsToWrite)) {
            throw new MmtcException(String.format(
                    "The columns configured for the Raw Telemetry Table do not match those in the file at %s.  Expected to write cols: %s.  The existing file has cols: %s.",
                    config.getRawTelemetryTablePath(),
                    currentHeaders,
                    colsToWrite
            ));
        }
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

    @Override
    public String getDisplayName() {
        return "Raw Telemetry Table";
    }
}
