package edu.jhuapl.sd.sig.mmtc;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;

import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.model.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class SclkKernelTests {
    public static Path getAbsolutePathOfTestResources() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().resolve("src/test/resources");
    }

    @BeforeAll
    static void teardown() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
    }

    /**
     * Verify that the SclkKernel.readSourceProduct() method can read and store an input SCLK kernel properly
     * and that it can identify return the last data record in the file.
     */
    @Test
    @DisplayName("SclkKernel.readSourceProduct Test 1")
    void testReadSeedKernel() throws IOException, TextProductException {
        String tscDir     = "src/test/resources/SclkKernelTests";
        String newTscName = "mmtc01.tsc";
        String sourceTsc  = "src/test/resources/nh_kernels/sclk/new-horizons_0000.tsc";

        SclkKernel tsc = new SclkKernel(tscDir, newTscName);

        tsc.setSourceFilespec(sourceTsc);
        tsc.readSourceProduct();
        String lastrec = tsc.getLastSourceProdDataRec();

        String expectedRec = "0     @19-JAN-2006-18:09:05.184000     1.00000000000";
        assertEquals(expectedRec, lastrec);

        assertEquals(1, tsc.getSourceProductDataRecCount());
    }

    /**
     * Verify that the SclkKernel.readSourceProduct() method can read and store an input SCLK kernel properly
     * and that it can identify return the last data record in the file.
     */
    @Test
    @DisplayName("SclkKernel.readSourceProduct Test 1")
    void readSourceProduct_Test1() throws IOException, TextProductException {
        String tscDir     = "src/test/resources/SclkKernelTests";
        String newTscName = "mmtc01.tsc";
        String sourceTsc  = "src/test/resources/nh_kernels/sclk/new-horizons_1876.tsc";

        SclkKernel tsc = new SclkKernel(tscDir, newTscName);
    
        tsc.setSourceFilespec(sourceTsc);
        tsc.readSourceProduct();
        String lastrec = tsc.getLastSourceProdDataRec();

        String expectedRec = "21119278300000     @09-JUN-2019-11:28:37.488441     1.00000001166";
        assertEquals(expectedRec, lastrec);

        assertEquals(1877, tsc.getSourceProductDataRecCount());
    }

    @Test
    void testGetVersionString() {
        SclkKernel kernel = new SclkKernel("src/test/resources/SclkKernelTests", "src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");
        assertEquals("1454", kernel.getVersionString("new-horizons", "_"));

        kernel = new SclkKernel("src/test/resources/SclkKernelTests", "src/test/resources/nh_kernels/sclk/new-horizons_0001.tsc");
        assertEquals("0001", kernel.getVersionString("new-horizons", "_"));

        kernel = new SclkKernel("src/test/resources/SclkKernelTests", "src/test/resources/nh_kernels/sclk/new_horizons_0001.tsc");
        assertEquals("0001", kernel.getVersionString("new_horizons", "_"));
    }
}
