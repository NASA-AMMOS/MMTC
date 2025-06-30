package edu.jhuapl.sd.sig.mmtc;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;

import edu.jhuapl.sd.sig.mmtc.products.SclkKernel;
import edu.jhuapl.sd.sig.mmtc.products.TextProductException;
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


    /**
     * Verify that the SclkKernel.createNewSclkKernel() can generate a new SCLK kernel which is identical to
     * the original except that it has a new filename, new values for the FILENAME and CREATION_DATE fields
     * and has a new time correlation record appended to the end of the data. It is necessarhy to examine
     * the new SCLK kernel manually to verify that it has been created properly and that the expected changes
     * have been made.
     */
    @Test
    @DisplayName("SclkKernel.createFile Test 1")
    void createFile_Test1() throws IOException, TextProductException, TimeConvertException {
        Path tscDir     = getAbsolutePathOfTestResources().resolve("SclkKernelTests");
        String newTscName = "mmtc01.tsc";
        Path sourceTsc  = getAbsolutePathOfTestResources().resolve("nh_kernels/sclk/new-horizons_1454.tsc");

        SclkKernel tsc = new SclkKernel(tscDir.toString(), newTscName);
        tsc.setProductCreationTime(OffsetDateTime.now());
        tsc.setSourceFilespec(sourceTsc.toString());
        tsc.setNewTriplet(17672059990000., "02-APR-2017-19:00:01.123456", 1.00000001999);

        String newFilePath = tscDir + File.separator + newTscName;
        Path newFile       = Paths.get(newFilePath);

        Files.deleteIfExists(newFile);

        /* Create a new SCLK kernel from an existing one and update it. */
        tsc.createNewSclkKernel(sourceTsc.toString());

        /* Verify that the SCLK exists. */
        File tscfd = new File(tscDir+File.separator+newTscName);
        assertTrue(tscfd.exists());
    }


    /**
     * Verify that the SclkKernel.setReplacementClockChgRate() causes an updated SCLK kernel to also contain
     * a new value for the clock change rate of the last record from the original source kernel. The FILENAME
     * and CREATION_DATE fields will be updated and a new time correlation record will be added. It is necessarhy
     * to examine the new SCLK kernel manually to verify that it has been created properly and that the expected
     * changes have been made.
     */
    @Test
    @DisplayName("SclkKernel.setReplacementClockChgRate Test 1")
    void setReplacementClockChgRate_Test1() throws IOException, TextProductException, TimeConvertException {
        String tscDir     = "src/test/resources/SclkKernelTests";
        String newTscName = "mmtc01.tsc";
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
