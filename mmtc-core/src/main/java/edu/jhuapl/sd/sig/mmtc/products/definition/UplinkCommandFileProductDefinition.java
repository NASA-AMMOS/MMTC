package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.*;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

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

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        try {
            UplinkCommand uplinkCommand = UplinkCmdFile.generateNewProduct(ctx);
            return String.format("Generated Uplink Command string: \n"+ uplinkCommand);
        } catch (TimeConvertException e) {
            throw new MmtcException("Unable to generate the Uplink Command File: ", e);
        }
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
