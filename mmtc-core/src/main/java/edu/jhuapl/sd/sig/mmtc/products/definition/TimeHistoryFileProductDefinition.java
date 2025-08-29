package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.TimeHistoryFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
