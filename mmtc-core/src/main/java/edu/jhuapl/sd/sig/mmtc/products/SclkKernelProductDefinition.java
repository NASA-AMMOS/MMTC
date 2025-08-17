package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.nio.file.Path;
import java.nio.file.Paths;

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

    @Override
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        try {
            final SclkKernel newSclkKernel = new SclkKernel(ctx.currentSclkKernel.get());

            newSclkKernel.setProductCreationTime(ctx.appRunTime);
            newSclkKernel.setDir(ctx.config.getSclkKernelOutputDir().toString());
            newSclkKernel.setName(ctx.config.getSclkKernelBasename() + ctx.config.getSclkKernelSeparator() + ctx.newSclkVersionString.get() + ".tsc");
            newSclkKernel.setNewTriplet(
                    ctx.correlation.target.get().getTargetSampleEncSclk(),
                    TimeConvert.tdtToTdtStr(ctx.correlation.target.get().getTargetSampleTdtG()),
                    ctx.correlation.predicted_clock_change_rate.get()
            );

            if (ctx.correlation.interpolated_clock_change_rate.isSet()) {
                newSclkKernel.setReplacementClockChgRate(ctx.correlation.interpolated_clock_change_rate.get());
            }

            newSclkKernel.createFile();

            final Path path = Paths.get(newSclkKernel.getPath());
            ctx.newSclkKernelPath.set(path);

            return new ProductWriteResult(
                    path,
                    ctx.newSclkVersionString.get()
            );
        } catch (TextProductException | TimeConvertException ex) {
            throw new MmtcException("Unable to write SCLK kernel", ex);
        }
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return true;
    }
}
