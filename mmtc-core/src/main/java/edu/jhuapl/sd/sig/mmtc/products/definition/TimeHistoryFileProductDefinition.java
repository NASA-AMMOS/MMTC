package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.products.model.TimeHistoryFile;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Optional;

public class TimeHistoryFileProductDefinition extends AppendedFileOutputProductDefinition {
    public TimeHistoryFileProductDefinition() {
        super("TimeHistoryFile");
    }

    @Override
    public ResolvedProductPath resolveLocation(RollbackConfig config) {
        return new ResolvedProductPath(
                config.getTimeHistoryFilePath(),
                new TimeHistoryFile(config.getTimeHistoryFilePath())
        );
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return true;
    }

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        TimeHistoryFile timeHistFile = new TimeHistoryFile(ctx.config.getRawTelemetryTablePath(), ctx.config.getTimeHistoryFileExcludeColumns());
        TableRecord timeHistRecord = new TableRecord(timeHistFile.getHeaders());
        try {
            TimeHistoryFile.generateNewTimeHistRec(ctx, timeHistFile, timeHistRecord);
        } catch (TimeConvertException e) {
            throw new RuntimeException(e);
        }
        return String.format("Updated Time History file records: %s \n %s", new TimeHistoryFile(ctx.config.getTimeHistoryFilePath()).getHeaders(),
                timeHistRecord.getValues().toString());
    }

    @Override
    public ProductWriteResult writeToProduct(TimeCorrelationContext ctx) throws MmtcException {
        return TimeHistoryFile.appendRowFor(ctx);
    }
}
