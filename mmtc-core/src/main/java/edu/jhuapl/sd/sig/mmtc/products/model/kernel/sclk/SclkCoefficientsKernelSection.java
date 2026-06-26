package edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk;

import edu.jhuapl.sd.sig.mmtc.products.model.kernel.KernelSection;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SclkCoefficientsKernelSection extends KernelSection {
    protected final List<String> beforeTripletLines;
    protected final SclkCoefficientFormat sclkCoefficientFormat;
    protected final List<CorrelationTriplet> triplets;
    protected final List<String> afterTripletLines;

    public SclkCoefficientsKernelSection(List<String> beforeTripletLines, SclkCoefficientFormat sclkCoefficientFormat, List<CorrelationTriplet> triplets, List<String> afterTripletLines) {
        this.beforeTripletLines = beforeTripletLines;
        this.sclkCoefficientFormat = sclkCoefficientFormat;
        this.triplets = triplets;
        this.afterTripletLines = afterTripletLines;
    }

    public SclkCoefficientFormat getSclkCoefficientFormat() {
        return sclkCoefficientFormat;
    }

    @Override
    public List<String> toOutputLines() throws TimeConvertException {
        List<String> outputLines = new ArrayList<>();
        outputLines.addAll(beforeTripletLines);

        for (CorrelationTriplet triplet : triplets) {
            outputLines.add(triplet.format(sclkCoefficientFormat));
        }

        outputLines.addAll(afterTripletLines);

        return outputLines;
    }

    public static SclkCoefficientsKernelSection parse(List<String> lines) throws TimeConvertException {
        final List<String> beforeTripletLines = new ArrayList<>();
        SclkCoefficientFormat sclkCoefficientFormat = null;
        final List<CorrelationTriplet> triplets = new ArrayList<>();
        final List<String> afterTripletLines = new ArrayList<>();

        boolean sawSclk01CoefficientsOpening = false;
        boolean sawClosingParen = false;

        for (String line : lines) {
            if (! sawSclk01CoefficientsOpening) {
                if (line.matches("^SCLK01_COEFFICIENTS_(\\S)+(\\s)*=(\\s)*\\((\\s*)")) {
                    sawSclk01CoefficientsOpening = true;
                }

                beforeTripletLines.add(line);
            } else if (! sawClosingParen) {
                if (line.trim().endsWith(")")) {
                    sawClosingParen = true;
                    afterTripletLines.add(")");
                    line = line.replace(")", "");

                    // if the line is empty now after consuming the closing paren, move on to the next line
                    // else, there is a triplet to consume
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                } else if (line.trim().isEmpty()) {
                    // if the line is empty, and it didn't hold a closing paren, then capture it as a blank line and continue
                    // if we haven't seen triplets yet, then add it to beforeTripletLines
                    // otherwise, add it to afterTripletLines
                    if (triplets.isEmpty()) {
                        beforeTripletLines.add("");
                    } else {
                        afterTripletLines.add("");
                    }

                    continue;
                }

                if (sclkCoefficientFormat == null) {
                    sclkCoefficientFormat = SclkCoefficientFormat.parseFormat(line);
                }

                triplets.add(CorrelationTriplet.parse(line));
            } else {
                afterTripletLines.add(line);
            }
        }

        return new SclkCoefficientsKernelSection(
                beforeTripletLines,
                sclkCoefficientFormat,
                triplets,
                afterTripletLines
        );
    }

    public SclkCoefficientsKernelSection withUpdatedLastTriplet(CorrelationTriplet updatedTriplet) {
        final List<CorrelationTriplet> newTriplets =  new ArrayList<>(triplets);
        newTriplets.remove(newTriplets.size() - 1);
        newTriplets.add(updatedTriplet);

        return new SclkCoefficientsKernelSection(
                beforeTripletLines,
                sclkCoefficientFormat,
                newTriplets,
                afterTripletLines
        );
    }

    public SclkCoefficientsKernelSection withAppendedTriplets(List<CorrelationTriplet> tripletsToAppend) {
        final List<CorrelationTriplet> newTriplets =  new ArrayList<>(triplets);
        newTriplets.addAll(tripletsToAppend);

        return new SclkCoefficientsKernelSection(
                beforeTripletLines,
                sclkCoefficientFormat,
                newTriplets,
                afterTripletLines
        );
    }

    public List<CorrelationTriplet> getTriplets() {
        return Collections.unmodifiableList(triplets);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SclkCoefficientsKernelSection that = (SclkCoefficientsKernelSection) o;
        return Objects.equals(beforeTripletLines, that.beforeTripletLines) && Objects.equals(triplets, that.triplets) && Objects.equals(afterTripletLines, that.afterTripletLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beforeTripletLines, triplets, afterTripletLines);
    }
}
