package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScet;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScetFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

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
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        final TimeCorrelationAppConfig conf = ctx.config;

        try {
            final String newSclkScetFilename = conf.getSclkScetFileBasename() +
                    conf.getSclkScetFileSeparator() +
                    ctx.newSclkVersionString.get() +
                    conf.getSclkScetFileSuffix();

            // Create the new SCLK/SCET file from the newly-created SCLK kernel.
            final SclkScetFile scetFile = new SclkScetFile(
                    conf,
                    newSclkScetFilename,
                    ctx.newSclkVersionString.get()
            );

            scetFile.setProductCreationTime(ctx.appRunTime);
            scetFile.setClockTickRate(ctx.sclk_kernel_fine_tick_modulus.get());
            SclkScet.setScetStrSecondsPrecision(conf.getSclkScetScetUtcPrecision());

            return new ProductWriteResult(
                    scetFile.createNewSclkScetFile(ctx.newSclkKernelPath.get().toString()),
                    ctx.newSclkVersionString.get()
            );
        } catch (TimeConvertException | TextProductException ex) {
            throw new MmtcException("Unable to write SCLK/SCET file", ex);
        }
    }
}
