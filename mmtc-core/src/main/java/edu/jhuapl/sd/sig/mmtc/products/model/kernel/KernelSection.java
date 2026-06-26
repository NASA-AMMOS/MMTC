package edu.jhuapl.sd.sig.mmtc.products.model.kernel;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.util.List;

public abstract class KernelSection {
    public abstract List<String> toOutputLines() throws TimeConvertException;
}
