package edu.jhuapl.sd.sig.mmtc;


import edu.jhuapl.sd.sig.mmtc.products.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TimeCorrelationTests {
    @BeforeAll
    static void teardown() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
    }
    @Test
    @DisplayName("Test with NH Data Test 1")
    void setReplacementClockChgRate_Test1() throws IOException, TextProductException, TimeConvertException {
        String tscDir     = "src/test/resources/SclkKernelTests";
        String newTscName = "nh2.tsc";
        String sourceTsc  = "src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc";


        SclkKernel tsc = new SclkKernel(tscDir, newTscName);
        tsc.setProductCreationTime(OffsetDateTime.now());
        tsc.setSourceFilespec(sourceTsc);
        tsc.setNewTriplet(17672059990000., "02-APR-2017-19:00:01.123456", 1.00000001999);

        String newFilePath = tscDir + File.separator + newTscName;
        Path newFile       = Paths.get(newFilePath);

        Files.deleteIfExists(newFile);

        /* Set an interpolated clock change rate for the penultimate record. */
        tsc.setReplacementClockChgRate(0.99999999991);

        /* Create a new SCLK kernel from an existing one and update it. */
        tsc.createNewSclkKernel(sourceTsc);

        /* Verify that the SCLK exists. */
        File tscfd = new File(tscDir+File.separator+newTscName);
        assertTrue(tscfd.exists());
    }
}
