package edu.jhuapl.sd.sig.mmtc.products.model.kernel;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.util.List;

// a section of a text kernel that can be rendered to a list of lines
public abstract class KernelSection {
    public abstract List<String> toOutputLines() throws TimeConvertException;
}
