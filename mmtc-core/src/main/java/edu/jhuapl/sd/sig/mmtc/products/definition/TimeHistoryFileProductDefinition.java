package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.products.model.TimeHistoryFile;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TimeHistoryFileProductDefinition extends AppendedFileOutputProductDefinition {
    public TimeHistoryFileProductDefinition() {
        super("TimeHistoryFile");
    }

    @Override
    public ResolvedProductPath resolveLocation(MmtcConfig config) {
        return new ResolvedProductPath(config.getTimeHistoryFilePath());
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext ctx) {
        return ctx.config.createTimeHistoryFile();
    }

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        TimeHistoryFile timeHistFile = new TimeHistoryFile(ctx.config.getTimeHistoryFilePath(), ctx.config.getTimeHistoryFileExcludeColumns());
        TableRecord timeHistRecord = new TableRecord(timeHistFile.getHeaders());
        try {
            TimeHistoryFile.generateNewTimeHistRec(ctx, timeHistFile, timeHistRecord);
        } catch (TimeConvertException e) {
            throw new RuntimeException(e);
        }
        List<String> thHeaders = new TimeHistoryFile(ctx.config.getTimeHistoryFilePath()).getHeaders();
        Collection<String> thValues = timeHistRecord.getValues();
        String zippedThRow = IntStream.range(0, thHeaders.size())
                .mapToObj(i -> "\t" + thHeaders.get(i) + "\t:\t"+new ArrayList<>(thValues)
                .get(i))
                .collect(Collectors.joining("\n"));
        return String.format("[DRY RUN] Updated Time History file records: \n%s", zippedThRow);
    }

    @Override
    public ProductWriteResult appendToProduct(TimeCorrelationContext ctx) throws MmtcException {
        return TimeHistoryFile.appendRowFor(ctx);
    }

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputPath) {
        final Map<String, String> confUpdates = new HashMap<>();
        confUpdates.put("table.timeHistoryFile.path", newProductOutputPath.toString());
        return confUpdates;
    }
}
