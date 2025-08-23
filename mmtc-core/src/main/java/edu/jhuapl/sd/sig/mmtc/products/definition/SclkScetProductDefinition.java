package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScetFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes the set of SCLK kernel output products that MMTC performs operations on.
 * A single SCLK kernel is modeled by {@link SclkKernel}.
 */
public class SclkScetProductDefinition extends EntireFileOutputProductDefinition {
    public SclkScetProductDefinition() {
        super("SCLKSCET File");
    }

    @Override
    public ResolvedProductDirPrefixSuffix resolveLocation(MmtcConfig conf) throws MmtcException {
        conf.validateSclkScetConfiguration();

        return new ResolvedProductDirPrefixSuffix(
                conf.getSclkScetOutputDir().toAbsolutePath(),
                conf.getSclkScetFileBasename(),
                conf.getSclkScetFileSuffix()
        );
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext ctx) {
        return ctx.config.createSclkScetFile();
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

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir) {
        final Map<String, String> confUpdates = new HashMap<>();
        confUpdates.put("product.sclkScetFile.dir", newProductOutputDir.toAbsolutePath().toString());
        return confUpdates;
    }
}
