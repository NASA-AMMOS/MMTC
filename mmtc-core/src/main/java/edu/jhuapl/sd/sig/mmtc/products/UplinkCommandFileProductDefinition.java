package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.*;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;
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
     * @throws MmtcException if the Uplink Command File cannot be written
     */
    // private void writeUplinkCommandFile(double et_g, double tdt_g, double clockChangeRate) throws MmtcException {
    @Override
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        String cmdFilespec = "";
        try {
            final UplinkCommand uplinkCmd = new UplinkCommand(
                    ctx.correlation.target.get().getTargetSample().getTkSclkCoarse(),
                    ctx.correlation.target.get().getTargetSampleEtG(),
                    ctx.correlation.target.get().getTargetSampleTdtG(),
                    TimeConvert.tdtToTdtStr(ctx.correlation.target.get().getTargetSampleTdtG()),
                    ctx.correlation.predicted_clock_change_rate.get()
            );

            final String cmdFilename = ctx.config.getUplinkCmdFileBasename() +
                    ctx.appRunTime.toEpochSecond() +
                    UplinkCmdFile.FILE_SUFFIX;

            final UplinkCmdFile cmdFile = new UplinkCmdFile(Paths.get(
                    ctx.config.getUplinkCmdFileDir(), cmdFilename
            ).toString());

            return new ProductWriteResult(
                    cmdFile.write(uplinkCmd),
                    Long.toString(ctx.appRunTime.toEpochSecond())
            );
        } catch (IOException | TimeConvertException ex) {
            throw new MmtcException("Unable to write the Uplink Command File: " + cmdFilespec, ex);
        }
    }
}
