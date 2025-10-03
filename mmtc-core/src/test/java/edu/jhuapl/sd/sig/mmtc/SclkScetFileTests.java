package edu.jhuapl.sd.sig.mmtc;

import edu.jhuapl.sd.sig.mmtc.products.model.SclkScet;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkScetFile;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SclkScetFileTests {
    @BeforeAll
    static void teardown() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
    }

    @Test
    public void testIsNegativeLeapSecond() {
        SclkScet a = new SclkScet(
                "000345534716.0",
                TimeConvert.parseIsoDoyUtcStr("2016-366T23:59:58.419640"),
                 68.184,
                2.0000000000
        );

        SclkScet b = new SclkScet(
                "000345534717.0",
                TimeConvert.parseIsoDoyUtcStr("2017-001T00:00:00.419640"),
                67.184,
                1.0000000000
        );

        Assertions.assertTrue(SclkScetFile.isNegativeLeapSecondEntryPair(a, b));
    }
}
