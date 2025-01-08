package edu.jhuapl.sd.sig.mmtc;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

public class TestHelper {
    public static void ensureSpiceIsLoadedAndUnloadAllKernels() throws TimeConvertException {
        if (!TimeConvert.spiceLibLoaded()) {
            TimeConvert.loadSpiceLib();
        }
        TimeConvert.unloadSpiceKernels();
    }
}
