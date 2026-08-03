package edu.jhuapl.sd.sig.mmtc.products.model.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// a section of a text kernel, read into a list of lines
public abstract class LinesKernelSection extends KernelSection {
    protected List<String> lines;

    public LinesKernelSection(TextKernel.KernelTextLineAccumulator kernelText) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(kernelText.lines));
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @Override
    public List<String> toOutputLines() {
        return Collections.unmodifiableList(this.lines);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LinesKernelSection that = (LinesKernelSection) o;
        return Objects.equals(lines, that.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(lines);
    }
}
