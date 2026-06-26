package edu.jhuapl.sd.sig.mmtc.products.model.kernel;

public enum KernelValueFormat {
    INTEGER,
    FLOAT,
    SCIENTIFIC_NOTATION,
    CAL_STR;

    public static KernelValueFormat formatOf(String val) {
        if (val.startsWith("@")) {
            return CAL_STR;
        } else if (val.contains("E")) {
            return SCIENTIFIC_NOTATION;
        } else if (val.contains(".")) {
            return FLOAT;
        } else {
            return INTEGER;
        }
    }
}
