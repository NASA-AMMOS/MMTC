package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.*;

import java.nio.file.Paths;

/**
 * Describes the set of SCLK kernel output products that MMTC performs operations on.
 * A single SCLK kernel is modeled by {@link SclkKernel}.
 */
public class UplinkCommandFileProductDefinition extends EntireFileOutputProductDefinition {
    public UplinkCommandFileProductDefinition() {
        super("Uplink Command File");
    }

    @Override
    public ResolvedProductDirAndPrefix resolveLocation(RollbackConfig conf) throws MmtcException {
        conf.validateUplinkCmdFileConfiguration();

        return new ResolvedProductDirAndPrefix(
                Paths.get(conf.getUplinkCmdFileDir()).toAbsolutePath(),
                conf.getUplinkCmdFileBasename()
        );
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return context.config.isCreateUplinkCmdFile();
    }

    /**
     * Writes the Uplink Command File
     * @param ctx the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if the Uplink Command File cannot be written
     */
    @Override
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        return UplinkCmdFile.writeNewProduct(ctx);
    }
}
