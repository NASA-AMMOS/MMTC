package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductDirPrefixSuffix;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkCoefficientFormat;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel.FILE_SUFFIX;

/**
 * Describes the set of SCLK kernel output products that MMTC performs operations on.
 * A single SCLK kernel is modeled by {@link SclkKernel}.
 */
public class SclkKernelProductDefinition extends EntireFileOutputProductDefinition {
    public static final String PRODUCT_NAME = "SCLK Kernel";

    public SclkKernelProductDefinition() {
        super(PRODUCT_NAME);
    }

    @Override
    public ResolvedProductDirPrefixSuffix resolveLocation(MmtcConfig conf) {
        return new ResolvedProductDirPrefixSuffix(
                conf.getSclkKernelOutputDir().toAbsolutePath(),
                conf.getSclkKernelBasename(),
                FILE_SUFFIX
        );
    }

    @Override
    public boolean isConfigured(MmtcConfig config) {
        return true;
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return true;
    }

    /**
     * Writes a new SCLK Kernel
     * @param ctx the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if the SCLK Kernel cannot be written
     */
    @Override
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        final Path outputPath = ctx.config.getSclkKernelOutputDir().resolve(
                ctx.config.getSclkKernelBasename() + ctx.config.getSclkKernelSeparator() + ctx.newSclkVersionString.get() + FILE_SUFFIX
        );
        ctx.newSclkKernelPath.set(outputPath);

        return SclkKernel.writeNewProduct(ctx, ctx.newSclkKernelPath.get());
    }

    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx, Path sclkKernelOutputPath) throws MmtcException {
        ctx.newSclkKernelPath.set(sclkKernelOutputPath);
        return SclkKernel.writeNewProduct(ctx, sclkKernelOutputPath);
    }

    /**
     * This implementation is distinct from that of other output products in that it does still write the SCLK kernel to
     * disk. The only difference is that it's written to the /tmp directory and its latest two lines are recorded here.
     * @param ctx The active run's TimeCorrelationContext
     * @return A string with details about the changed SCLK kernel(s) ready to be logged.
     * @throws MmtcException if there are any problems generating the SCLK kernel
     */
    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        // write product to tmp directory even though it's a dry run, because it has to be loaded for SCLK-SCET file creation
        writeNewProduct(
                ctx,
                Paths.get("/tmp").resolve(
                        ctx.config.getSclkKernelBasename() + ctx.config.getSclkKernelSeparator() + ctx.newSclkVersionString.get() + FILE_SUFFIX
                )
        );

        List<CorrelationTriplet> newSclkEntries = ctx.newSclkKernel.get().getTriplets().subList(ctx.newSclkKernel.get().getTriplets().size() - 2, ctx.newSclkKernel.get().getTriplets().size());

        // If an interpolated clock change rate has replaced the rate in the existing SCLK kernel record,
        // or if a smoothing record was inserted, print the two latest records.
        // Otherwise, just return the new record

        final SclkCoefficientFormat coeffFmt = ctx.newSclkKernel.get().getCoefficientsFormat();
        try {
            if (ctx.correlation.updatedInterpolatedTriplet.isSet() || ctx.correlation.newSmoothingTriplet.isSet()) {
                return "[DRY RUN] Updated SCLK entries: \n"
                        + newSclkEntries.get(0).format(coeffFmt) + "\n"
                        + newSclkEntries.get(1).format(coeffFmt);
            } else {
                return "[DRY RUN] New SCLK entry: \n" + newSclkEntries.get(1).format(coeffFmt);
            }
        } catch (TimeConvertException e) {
            throw new MmtcException(e);
        }
    }

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir) {
        final Map<String, String> confUpdates = new HashMap<>();
        confUpdates.put("spice.kernel.sclk.kerneldir", newProductOutputDir.toAbsolutePath().toString());
        return confUpdates;
    }

    @Override
    public String getDisplayName() {
        return "SCLK Kernel";
    }

    public static String getVersionString(final Path path, final String sclkBaseName, final String separator) {
        return path.getFileName().toString().replace(sclkBaseName + separator, "").replace(FILE_SUFFIX, "");
    }
}
