package edu.jhuapl.sd.sig.mmtc.products.model.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}
