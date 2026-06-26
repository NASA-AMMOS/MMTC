package edu.jhuapl.sd.sig.mmtc.products.model.kernel;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class TextKernel {
    private static final String TEXT_KERNEL_BEGIN_DATA_MARKER = "\\begindata";
    private static final String TEXT_KERNEL_BEGIN_TEXT_MARKER = "\\begintext";

    protected List<KernelSection> sections;

    public static class KernelTextLineAccumulator {
        protected List<String> lines = new ArrayList<>();

        public void add(String line) {
            lines.add(line);
        }
    }

    protected TextKernel(List<KernelSection> sections) {
        this.sections = Collections.unmodifiableList(sections);
    }

    private enum CurrentParserSection {
        TEXT,
        DATA
    }

    public List<String> toLines() throws TimeConvertException, IOException {
        List<String> outputLines = new ArrayList<>();

        for (KernelSection section : sections) {
            outputLines.addAll(section.toOutputLines());
        }

        return outputLines;
    }

    protected static List<LinesKernelSection> readSections(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);

        List<LinesKernelSection> parsedSections = new ArrayList<>();

        CurrentParserSection currentParserSection = CurrentParserSection.TEXT;
        KernelTextLineAccumulator textLineAccum = new KernelTextLineAccumulator();

        for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
            String line = lines.get(lineNum);

            if (line.startsWith(TEXT_KERNEL_BEGIN_TEXT_MARKER)) {
                addSectionIfNonEmpty(parsedSections, textLineAccum, currentParserSection);

                currentParserSection = CurrentParserSection.TEXT;
                textLineAccum = new KernelTextLineAccumulator();
            } else if (line.startsWith(TEXT_KERNEL_BEGIN_DATA_MARKER)) {
                addSectionIfNonEmpty(parsedSections, textLineAccum, currentParserSection);

                currentParserSection = CurrentParserSection.DATA;
                textLineAccum = new KernelTextLineAccumulator();
            }

            textLineAccum.add(line);
        }

        addSectionIfNonEmpty(parsedSections, textLineAccum, currentParserSection);

        return Collections.unmodifiableList(parsedSections);
    }

    private static void addSectionIfNonEmpty(List<LinesKernelSection> parsedSections, KernelTextLineAccumulator accum, CurrentParserSection currentParserSection) {
        if (! accum.lines.isEmpty()) {
            if (currentParserSection.equals(CurrentParserSection.DATA)) {
                parsedSections.add(new DataKernelSection(accum));
            } else if (currentParserSection.equals(CurrentParserSection.TEXT)) {
                parsedSections.add(new TextKernelSection(accum));
            }
        }
    }

    protected static boolean firstLineStartsWith(LinesKernelSection section, String expectedString) {
        return section.lines.get(0).startsWith(expectedString);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TextKernel that = (TextKernel) o;
        return Objects.equals(sections, that.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sections);
    }
}
