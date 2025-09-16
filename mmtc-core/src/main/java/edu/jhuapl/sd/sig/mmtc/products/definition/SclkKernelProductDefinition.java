package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductDirPrefixSuffix;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes the set of SCLK kernel output products that MMTC performs operations on.
 * A single SCLK kernel is modeled by {@link SclkKernel}.
 */
public class SclkKernelProductDefinition extends EntireFileOutputProductDefinition {
    public SclkKernelProductDefinition() {
        super("SCLK Kernel");
    }

    @Override
    public ResolvedProductDirPrefixSuffix resolveLocation(MmtcConfig conf) {
        return new ResolvedProductDirPrefixSuffix(
                conf.getSclkKernelOutputDir().toAbsolutePath(),
                conf.getSclkKernelBasename(),
                SclkKernel.FILE_SUFFIX
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

    /**
     * This implementation is notable from that of other output products in that it does still write the SCLK kernel to
     * disk. The only difference is that it's written to the /tmp directory and its latest two lines are recorded here.
     * @param ctx The active run's TimeCorrelationContext
     * @return A string with details about the changed SCLK kernel(s) ready to be logged.
     * @throws MmtcException if there are any problems generating the SCLK kernel
     */
    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        SclkKernel.writeNewProduct(ctx);
        // ctx.newSclkKernel.get().updateFile();

        String[] newSclkEntries = ctx.newSclkKernel.get().getLastXRecords(2);
        // If an interpolated clock change rate has replaced the rate in the existing SCLK kernel record, retrieve the two latest records.
        // Otherwise, just return the new record
        if (ctx.newSclkKernel.get().hasNewClkChgRateSet()) {
            return String.format("[DRY RUN] Updated SCLK entries: \n" + newSclkEntries[0] + "\n" + newSclkEntries[1]);
        } else {
            return String.format("[DRY RUN] New SCLK entry: \n" + newSclkEntries[0]);
        }
    }

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir) {
        final Map<String, String> confUpdates = new HashMap<>();
        confUpdates.put("spice.kernel.sclk.kerneldir", newProductOutputDir.toAbsolutePath().toString());
        return confUpdates;
    }
}
