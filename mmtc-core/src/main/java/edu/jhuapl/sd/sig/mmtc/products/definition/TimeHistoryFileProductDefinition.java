package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.products.model.TimeHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.util.GenericCsv;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
    public boolean isConfigured(MmtcConfig config) {
        return true;
    }

    @Override
    public void validateExistingState(MmtcConfig config) throws MmtcException {
        if (! Files.exists(config.getTimeHistoryFilePath())) {
            // if the file doesn't exist yet, there's no state to check
            return;
        }

        // ensure that the headers that will be written match the actual headers in the file
        List<String> currentHeaders = new GenericCsv(config.getTimeHistoryFilePath()).getHeaders();

        List<String> colsToWrite = new TimeHistoryFile(config.getTimeHistoryFilePath(), config.getTimeHistoryFileExcludeColumns()).getHeaders();

        if (! currentHeaders.equals(colsToWrite)) {
            throw new MmtcException(String.format(
                    "The columns configured for the Time History File do not match those in the file at %s.  Expected to write cols: %s.  The existing file has cols: %s.",
                    config.getTimeHistoryFilePath(),
                    currentHeaders,
                    colsToWrite
            ));
        }
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

        String zippedRow = getFormattedDryRunOutputForTableRow(
                new TimeHistoryFile(ctx.config.getTimeHistoryFilePath()).getHeaders(),
                timeHistRecord
        );

        return String.format("[DRY RUN] Updated Time History file records: \n%s", zippedRow);
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

    @Override
    public String getDisplayName() {
        return "Time History File";
    }
}
