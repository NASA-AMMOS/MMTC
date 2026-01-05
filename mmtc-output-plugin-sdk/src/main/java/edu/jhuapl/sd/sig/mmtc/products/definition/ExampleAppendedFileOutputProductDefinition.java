package edu.jhuapl.sd.sig.mmtc.products.definition;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ResolvedProductPath;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;

public class ExampleAppendedFileOutputProductDefinition extends AppendedFileOutputProductDefinition {
    private static final String FILENAME = "Example-Appended-File.csv";
    private static final Path HARDCODED_OUTPUT_PATH = Paths.get(System.getenv("MMTC_HOME"), "output", FILENAME);
    private static final String HEADER = "Run ID,SCLK Version,Predicted Clock Change Rate";

    private final Map<String, String> config;

    public ExampleAppendedFileOutputProductDefinition(String name, Map<String, String> config) {
        super(name);
        this.config = Collections.unmodifiableMap(config);
    }

    @Override
    public ProductWriteResult appendToProduct(TimeCorrelationContext ctx) throws MmtcException {
        try {
            if (! Files.exists(HARDCODED_OUTPUT_PATH)) {
                Files.write(HARDCODED_OUTPUT_PATH, (HEADER + "\n").getBytes(StandardCharsets.UTF_8));
            }

            Files.write(
                    HARDCODED_OUTPUT_PATH,
                    String.format("%d,%s,%.11f\n", ctx.runId.get(), ctx.newSclkVersionString.get(), ctx.correlation.predicted_clock_change_rate.get()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND
            );

            return new ProductWriteResult(
                    HARDCODED_OUTPUT_PATH,
                    Files.readAllLines(HARDCODED_OUTPUT_PATH).size()
            );
        } catch (IOException e) {
            throw new MmtcException(e);
        }
    }

    @Override
    public ResolvedProductPath resolveLocation(MmtcConfig config) {
        return new ResolvedProductPath(HARDCODED_OUTPUT_PATH);
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
                "This will generally be the new entry that would have been added to the appended product: \n" +
                "\t%s\n\t%d,%s,%.11f\n", HEADER, ctx.runId.get(), ctx.newSclkVersionString.get(), ctx.correlation.predicted_clock_change_rate.get());
    }

    @Override
    public Map<String, String> getSandboxConfigUpdates(MmtcConfig originalConfig, Path newProductOutputPath) {
        return Collections.emptyMap();
    }

    @Override
    public String getDisplayName() {
        return "Example Appended File";
    }
}
