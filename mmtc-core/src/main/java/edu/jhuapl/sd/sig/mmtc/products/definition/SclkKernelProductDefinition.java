package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;

/**
 * Describes the set of SCLK kernel output products that MMTC performs operations on.
 * A single SCLK kernel is modeled by {@link SclkKernel}.
 */
public class SclkKernelProductDefinition extends EntireFileOutputProductDefinition {
    public SclkKernelProductDefinition() {
        super("SCLK Kernel");
    }

    @Override
    public ResolvedProductDirAndPrefix resolveLocation(RollbackConfig conf) {
        return new ResolvedProductDirAndPrefix(
                conf.getSclkKernelOutputDir().toAbsolutePath(),
                conf.getSclkKernelBasename()
        );
    }

    /**
     * Writes a new SCLK Kernel
     * @param ctx the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if the SCLK Kernel cannot be written
     */
    @Override
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        return SclkKernel.writeNewProduct(ctx);
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return true;
    }

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        try {
            SclkKernel newKernel = SclkKernel.calculateNewProduct(ctx);
            newKernel.updateFile();
            String[] newSclkEntries = newKernel.getLastXRecords(2);
            return String.format("New SCLK entries: \n"+newSclkEntries[0]+"\n "+newSclkEntries[1]);
        } catch (TimeConvertException | TextProductException | IOException e) {
            throw new MmtcException("Unable to generate SCLK kernel", e);
        }
    }
}
