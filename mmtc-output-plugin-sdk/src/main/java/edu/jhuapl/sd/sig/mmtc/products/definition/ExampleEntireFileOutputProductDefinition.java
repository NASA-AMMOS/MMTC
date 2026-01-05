package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductDirPrefixSuffix;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public class ExampleEntireFileOutputProductDefinition extends EntireFileOutputProductDefinition {
    private static final Logger logger = LogManager.getLogger();

    private static final String FILE_PREFIX = "Example-Entire-File_";
    private static final String FILE_SUFFIX = ".txt";
    private final Map<String, String> config;

    public ExampleEntireFileOutputProductDefinition(String name, Map<String, String> config) {
        super(name);
        this.config = Collections.unmodifiableMap(config);
    }

    @Override
    public ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        final Path outputPath = Paths.get(System.getenv("MMTC_HOME"), "output", FILE_PREFIX + ctx.newSclkVersionString.get() + FILE_SUFFIX);

        logger.info(config);

        final String contents = new StringBuilder()
                .append("Header: " + config.getOrDefault("customHeaderContent", "Default Header Content"))
                .append("\n")
                .append("Predicted clock change rate: ")
                .append(ctx.correlation.predicted_clock_change_rate.get())
                .append("\n")
                .append("Footer: " + config.getOrDefault("customFooterContent", "Default Footer Content"))
                .toString();

        try {
            Files.write(
                    outputPath,
                    contents.getBytes(StandardCharsets.UTF_8)
            );
        } catch (IOException e) {
            throw new MmtcException(e);
        }

        return new ProductWriteResult(outputPath, ctx.newSclkVersionString.get());
    }

    @Override
    public ResolvedProductDirPrefixSuffix resolveLocation(MmtcConfig config) throws MmtcException {
        return new ResolvedProductDirPrefixSuffix(
                Paths.get(System.getenv("MMTC_HOME"), "output"),
                FILE_PREFIX,
                FILE_SUFFIX
        );
    }

    @Override
    public boolean isConfigured(MmtcConfig config) {
        return true;
    }

    @Override
    public boolean shouldBeWritten(TimeCorrelationContext ctx) {
        return true;
    }

    @Override
    public String getDryRunPrintout(TimeCorrelationContext ctx) throws MmtcException, TextProductException, IOException, TimeConvertException {
        return String.format("This is the product's results for this run in the form of a string that will be printed and logged when the dry run option is used.\n" +
                "This will generally be the latest x entries in the new product: \n" +
                "\t%d,%s,%.11f\n", ctx.runId.get(), ctx.newSclkVersionString.get(), ctx.correlation.predicted_clock_change_rate.get());
    }

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputDir) {
        return Collections.emptyMap();
    }

    @Override
    public String getDisplayName() {
        return "Example Entire File";
    }
}
