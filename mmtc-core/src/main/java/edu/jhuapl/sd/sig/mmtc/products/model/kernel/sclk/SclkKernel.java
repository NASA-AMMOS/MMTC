package edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.*;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// an immutable class that retains no reference to its original filepath
public class SclkKernel extends TextKernel {
    public static final Pattern TRIPLET_LINE_PATTERN = Pattern.compile("^(\\s+)(\\S+)(\\s+)(\\S+)(\\s+)(\\S+)(\\s*)");
    public static final String FILE_SUFFIX = ".tsc";

    private static final String SCLK_KERNEL_IDENTIFIER = "KPL/SCLK";

    public static final String TEXT_FIELD_FILENAME = "FILENAME";
    public static final String TEXT_FIELD_CREATION_DATE = "CREATION_DATE";

    protected static final Logger logger = LogManager.getLogger();

    protected SclkKernel(List<KernelSection> sections) {
        super(sections);
    }

    public SclkKernel(SclkKernel other) {
        this(other.sections);
    }

    public SclkCoefficientsKernelSection getCoefficientsSection() {
        KernelSection coeffSection = this.sections.stream().filter(sec -> sec instanceof SclkCoefficientsKernelSection).findFirst().orElseThrow(() -> new IllegalStateException("Did not find an SCLK coefficients section"));
        return (SclkCoefficientsKernelSection) coeffSection;
    }

    public SclkCoefficientFormat getCoefficientsFormat() {
        return getCoefficientsSection().getSclkCoefficientFormat();
    }

    public List<CorrelationTriplet> getTriplets() {
        return getCoefficientsSection().getTriplets();
    }

    public SclkKernel withUpdatedTextField(String fieldName, String fieldVal) {
        List<KernelSection> newSections = new ArrayList<>();

        for (KernelSection section : this.sections) {
            if (section instanceof TextKernelTextSection) {
                TextKernelTextSection textKernelSection = (TextKernelTextSection) section;

                KernelTextLineAccumulator textLineAccum = new KernelTextLineAccumulator();

                Pattern declarationPattern = Pattern.compile("^" + fieldName + "\\s*=\\s*\"(.+)\"\\s*");
                for (String line : textKernelSection.getLines()) {
                    Matcher matcher = declarationPattern.matcher(line);
                    if (matcher.matches()) {
                        String oldVal = matcher.group(1);
                        textLineAccum.add(line.replace(oldVal, fieldVal));
                    } else {
                        textLineAccum.add(line);
                    }
                }

                newSections.add(new TextKernelTextSection(textLineAccum));
            } else {
                newSections.add(section);
            }
        }

        return new SclkKernel(newSections);
    }

    private SclkKernel withAppendedTriplet(CorrelationTriplet correlationTriplet) {
        return withAppendedTriplets(Arrays.asList(correlationTriplet));
    }

    public SclkKernel withAppendedTriplets(List<CorrelationTriplet> newTripletsToAppend) {
        List<KernelSection> newSections = new ArrayList<>();

        for (KernelSection section : this.sections) {
            if (section instanceof SclkCoefficientsKernelSection) {
                SclkCoefficientsKernelSection coeffSection = (SclkCoefficientsKernelSection) section;
                newSections.add(coeffSection.withAppendedTriplets(newTripletsToAppend));
            } else {
                newSections.add(section);
            }
        }

        return new SclkKernel(newSections);
    }

    public SclkKernel withUpdatedFinalTriplet(CorrelationTriplet updatedTriplet) {
        List<KernelSection> newSections = new ArrayList<>();

        for (KernelSection section : this.sections) {
            if (section instanceof SclkCoefficientsKernelSection) {
                SclkCoefficientsKernelSection coeffSection = (SclkCoefficientsKernelSection) section;
                newSections.add(coeffSection.withUpdatedLastTriplet(updatedTriplet));
            } else {
                newSections.add(section);
            }
        }

        return new SclkKernel(newSections);
    }

    public CorrelationTriplet getLastTriplet() throws TextProductException {
        return getTriplets().stream().reduce((a, b) -> b).orElseThrow(() -> new IllegalStateException("No triplets found"));
    }

    /**
     * Retrieves a triplet that is the specified number of hours
     * previous to the supplied time in TDT.
     *
     * @param fromTdt       IN the time to look back from in TDT
     * @param lookBackHours IN the number of hours to look back
     * @return the parsed record that is the number of hours back
     * @throws TextProductException if the prior record could not be found
     */
    public CorrelationTriplet getPriorRec(Double fromTdt, Double lookBackHours, Collection<String> smoothingRecordTdtStringsToIgnore) throws TextProductException {
        // todo can I remove this implementation and just call getPriorRecs and return the head of the list?
        try {
            final double minLookbackSeconds = lookBackHours * 3600.;

            List<CorrelationTriplet> triplets = getTriplets();
            for (int i = triplets.size() - 1; i >= 0; i--) {
                CorrelationTriplet triplet = triplets.get(i);
                final String tdtStr = triplet.getTdtCalStr();
                final double tdtSec = triplet.getTdt();

                if (smoothingRecordTdtStringsToIgnore.contains(tdtStr)) {
                    logger.debug(String.format("getPriorRec: skipping record at TDT %s due to it being a smoothing record", tdtStr));
                    continue;
                }

                if ((fromTdt - tdtSec) < minLookbackSeconds) {
                    logger.debug(String.format("getPriorRec: skipping record at TDT %s due to not meeting lookback minimum", tdtStr));
                    continue;
                }

                return triplet;
            }
        } catch (TimeConvertException e) {
            throw new TextProductException("Unable to convert TDT string to numeric TDT seconds.", e);
        }

        throw new TextProductException("Look back time invalid for the specified SCLK kernel.");
    }

    /**
     * Retrieves a data record from the current SCLK kernel that is the specified number of hours
     * previous to the supplied time in TDT.
     *
     * @param fromTdt       IN the time to look back from in TDT
     * @param minLookbackHours IN the min number of hours to look back
     * @param maxLookbackHours IN the max number of hours to look back
     * @return the parsed record that is the number of hours back
     * @throws TextProductException if there are problems reading the prior record
     */
    public List<CorrelationTriplet> getPriorRecs(Double fromTdt, Double minLookbackHours, Double maxLookbackHours, Collection<String> smoothingRecordTdtStringsToIgnore) throws TextProductException {
        final double minLookbackSeconds = minLookbackHours * 3600.;
        final double maxLookbackSeconds = maxLookbackHours * 3600.;

        List<CorrelationTriplet> results = new ArrayList<>();

        try {
            List<CorrelationTriplet> triplets = getTriplets();
            for (int i = triplets.size() - 1; i >= 0; i--) {
                CorrelationTriplet triplet = triplets.get(i);

                final String recTdtStr = triplet.getTdtCalStr();
                final double recTdtSec = triplet.getTdt();

                if (smoothingRecordTdtStringsToIgnore.contains(recTdtStr)) {
                    logger.debug(String.format("getPriorRecs: skipping record at TDT %s due to it being a smoothing record", recTdtStr));
                    continue;
                }

                final double recDeltaTdt = fromTdt - recTdtSec;
                if ((recDeltaTdt < minLookbackSeconds) || recDeltaTdt > maxLookbackSeconds) {
                    logger.debug(String.format("getPriorRecs: skipping record at TDT %s due to not meeting lookback constraints", recTdtStr));
                    continue;
                }

                results.add(triplet);
            }
        } catch (TimeConvertException e) {
            throw new TextProductException("Unable to convert TDT string to numeric TDT seconds.", e);
        }

        return results;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public static SclkKernel read(Path path) throws IOException, TimeConvertException {
        final List<LinesKernelSection> linesKernelSections = TextKernel.readSections(path);

        // check that the first section is a text kernel section, and that it has the SCLK kernel identifier
        if (! firstLineStartsWith(linesKernelSections.get(0), SCLK_KERNEL_IDENTIFIER)) {
            logger.info("First line of file does not contain " + SCLK_KERNEL_IDENTIFIER);
        }

        final List<KernelSection> resultingSclkKernelSections = new ArrayList<>();

        // identify the data section with the coefficients and update it to a parsed SclkCoefficientsKernelSection
        for (LinesKernelSection section : linesKernelSections) {
            if (section instanceof TextKernelDataSection && section.getLines().stream().anyMatch(l -> l.startsWith("SCLK01_COEFFICIENTS"))) {
                resultingSclkKernelSections.add(SclkCoefficientsKernelSection.parse(section.getLines()));
            } else {
                resultingSclkKernelSections.add(section);
            }
        }

        return new SclkKernel(resultingSclkKernelSections);
    }

    private static String formatCreationDateForKernel(OffsetDateTime creationDateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
        return creationDateTime.format(formatter);
    }

    public static SclkKernel assembleNewKernelFromContext(TimeCorrelationContext ctx) {
        SclkKernel updatedSclkKernel = ctx.currentSclkKernel.get()
                .withUpdatedTextField(SclkKernel.TEXT_FIELD_FILENAME, ctx.config.getSclkKernelBasename() + ctx.config.getSclkKernelSeparator() + ctx.newSclkVersionString.get() + ".tsc")
                .withUpdatedTextField(SclkKernel.TEXT_FIELD_CREATION_DATE, formatCreationDateForKernel(ctx.appRunTime));

        // if applicable, set the updated 'final' / 'ultimate' triplet with the interpolated rate (soon to be penultimate)
        if (ctx.correlation.updatedInterpolatedTriplet.isSet()) {
            updatedSclkKernel = updatedSclkKernel.withUpdatedFinalTriplet(ctx.correlation.updatedInterpolatedTriplet.get());
        }

        // if applicable, add the new smoothing triplet
        if (ctx.correlation.newSmoothingTriplet.isSet()) {
            updatedSclkKernel = updatedSclkKernel.withAppendedTriplet(ctx.correlation.newSmoothingTriplet.get());
        }

        // add the new predicted triplet
        updatedSclkKernel = updatedSclkKernel.withAppendedTriplet(ctx.correlation.newPredictedTriplet.get());

        return updatedSclkKernel;
    }

    public static ProductWriteResult writeNewProduct(TimeCorrelationContext ctx, Path outputPath) throws MmtcException {
        // the passed context already contains the new SCLK kernel

        logger.info("Writing new SCLK kernel product to: " + outputPath);

        try {
            Files.write(outputPath, ctx.newSclkKernel.get().toLines());
        } catch (TimeConvertException | IOException e) {
            throw new MmtcException(e);
        }

        return new ProductWriteResult(
                outputPath,
                ctx.newSclkVersionString.get()
        );
    }
}
