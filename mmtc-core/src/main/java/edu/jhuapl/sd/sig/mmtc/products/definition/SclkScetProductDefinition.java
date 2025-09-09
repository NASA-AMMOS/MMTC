package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScetFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;
import java.util.Arrays;

/**
 * Describes the set of SCLK kernel output products that MMTC performs operations on.
 * A single SCLK kernel is modeled by {@link SclkKernel}.
 */
public class SclkScetProductDefinition extends EntireFileOutputProductDefinition {
    public SclkScetProductDefinition() {
        super("SCLKSCET File");
    }

    @Override
    public ResolvedProductDirAndPrefix resolveLocation(RollbackConfig conf) throws MmtcException {
        conf.validateSclkScetConfiguration();

        return new ResolvedProductDirAndPrefix(
                conf.getSclkScetOutputDir().toAbsolutePath(),
                conf.getSclkScetFileBasename()
        );
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext ctx) {
        return ctx.config.createSclkScetFile();
    }

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        SclkScetFile scetFile = SclkScetFile.calculateNewProduct(ctx);
        try {
            scetFile.setSourceFilespec(ctx.config.getInputSclkKernelPath().toString()); // Can't use
            scetFile.updateFile();
        } catch (TextProductException | TimeConvertException | IOException e) {
            throw new MmtcException("Failed to generate SCLKSCET file");
        }
        return Arrays.toString(scetFile.getLastXRecords(1));
    }

    /**
     * Writes a new SCLK-SCET File
     * @param ctx the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if the SCLK-SCET File cannot be written
     */
    @Override
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
      return SclkScetFile.writeNewProduct(ctx);
    }
}
