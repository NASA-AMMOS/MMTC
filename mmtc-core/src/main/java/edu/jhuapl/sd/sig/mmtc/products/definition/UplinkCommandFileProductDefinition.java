package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductDirPrefixSuffix;
import edu.jhuapl.sd.sig.mmtc.products.model.*;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes the set of SCLK kernel output products that MMTC performs operations on.
 * A single SCLK kernel is modeled by {@link SclkKernel}.
 */
public class UplinkCommandFileProductDefinition extends EntireFileOutputProductDefinition {
    public UplinkCommandFileProductDefinition() {
        super("Uplink Command File");
    }

    @Override
    public ResolvedProductDirPrefixSuffix resolveLocation(MmtcConfig conf) throws MmtcException {
        conf.ensureValidUplinkCmdFileConfiguration();

        return new ResolvedProductDirPrefixSuffix(
                Paths.get(conf.getUplinkCmdFileDir()).toAbsolutePath(),
                conf.getUplinkCmdFileBasename(),
                UplinkCmdFile.FILE_SUFFIX
        );
    }

    @Override
    public boolean isConfigured(MmtcConfig config) {
        return config.getMissingUplinkCmdFileConfigurationKeys().isEmpty();
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext context) {
        return context.config.isCreateUplinkCmdFile();
    }

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        try {
            UplinkCommand uplinkCommand = UplinkCmdFile.generateNewProduct(ctx);
            return String.format("[DRY RUN] Generated Uplink Command string: \n\t"+ uplinkCommand);
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

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir) {
        final Map<String, String> confUpdates = new HashMap<>();
        confUpdates.put("product.uplinkCmdFile.outputDir", newProductOutputDir.toAbsolutePath().toString());
        return confUpdates;
    }
}
