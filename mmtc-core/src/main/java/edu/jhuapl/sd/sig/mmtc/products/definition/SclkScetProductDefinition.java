package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductDirPrefixSuffix;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScetFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;
import java.util.Arrays;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private final int ENTRIES_TO_PRINT = 4;

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

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException {
        SclkScetFile scetFile = SclkScetFile.calculateNewProduct(ctx);
        try {
            scetFile.setSourceFilespec(ctx.newSclkKernel.get().getPath());
            scetFile.updateFile();
        } catch (TextProductException | TimeConvertException | IOException e) {
            throw new MmtcException("Failed to generate SCLKSCET file");
        }
        List<String> newProductLines = scetFile.getNewProductLines();
        String newRecs = IntStream.range(Math.max(0, newProductLines.size() - ENTRIES_TO_PRINT), newProductLines.size())
                .mapToObj(newProductLines::get)
                .collect(Collectors.joining("\n\t"));
        return String.format("[DRY RUN] Latest %d SCLKSCET file entries: \n\t%s\n\t%s", ENTRIES_TO_PRINT, scetFile.getSclkscetFields(), newRecs);
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
