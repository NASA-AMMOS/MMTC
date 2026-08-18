package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.ParameterGroupUpdateFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ParameterGroupUpdateFileProductDefinition extends AppendedFileOutputProductDefinition {
    public ParameterGroupUpdateFileProductDefinition() {
        super("ParameterGroupUpdateFile");
    }


    @Override
    public ProductWriteResult appendToProduct(TimeCorrelationContext ctx) throws MmtcException {
        return ParameterGroupUpdateFile.appendRowFor(ctx);
    }

    @Override
    public boolean isConfigured(MmtcConfig config) {
        return true;
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return context.config.isCreateParameterGroupUpdateFile();
    }

    @Override
    public ResolvedProductPath resolveLocation(MmtcConfig config) throws MmtcException {
        return new ResolvedProductPath(config.getParameterGroupUpdateFilePath());
    }

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException, TextProductException, IOException, TimeConvertException {
        final ParameterGroupUpdateFile parameterFile = new ParameterGroupUpdateFile(ctx.config.getParameterGroupUpdateFilePath());
        final TableRecord newRec = new TableRecord(ParameterGroupUpdateFile.COLUMNS);
        try {
            ParameterGroupUpdateFile.generateNewParameterUpdateGroupRecord(ctx, parameterFile, newRec);
        } catch (TimeConvertException e) {
            throw new MmtcException(e);
        }

        String zippedRow = getFormattedDryRunOutputForTableRow(
                ParameterGroupUpdateFile.COLUMNS,
                newRec
        );

        return String.format("[DRY RUN] Update ParameterGroupUpdateFile record: \n%s", zippedRow);
    }

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir) {
        final Map<String, String> confUpdates = new HashMap<>();
        confUpdates.put("product.parameterGroupUpdateFile.path", newProductOutputDir.toString());
        return confUpdates;
    }

    @Override
    public String getDisplayName() {
        return "Parameter Group Update File";
    }


}
