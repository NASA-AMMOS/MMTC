package edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk;

import edu.jhuapl.sd.sig.mmtc.TestHelper;
import edu.jhuapl.sd.sig.mmtc.products.definition.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import spice.basic.KernelDatabase;
import spice.basic.SpiceErrorException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclkKernelTest {

    @BeforeAll
    static void setup() throws TimeConvertException, SpiceErrorException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");
    }

    @Test
    public void readWriteTest() throws IOException, TimeConvertException {
        SclkKernel sclkKernel = SclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"));

        // sanity checks on the triplets
        assertEquals(1455, sclkKernel.getTriplets().size());

        List<CorrelationTriplet> triplets = sclkKernel.getTriplets();
        CorrelationTriplet firstTriplet = triplets.get(0);
        assertEquals(0, firstTriplet.getEncSclk());
        assertEquals(0, firstTriplet.getEncSclkAsLong());
        assertEquals("19-JAN-2006-18:09:05.184000", firstTriplet.getTdtCalStr());
        assertEquals(1.00000000000, firstTriplet.getClkChgRate());

        CorrelationTriplet lastTriplet = triplets.get(triplets.size() - 1);
        assertEquals(17672056750000.0, lastTriplet.getEncSclk());
        assertEquals(17672056750000L, lastTriplet.getEncSclkAsLong());
        assertEquals("02-APR-2017-12:14:45.693714", lastTriplet.getTdtCalStr());
        assertEquals(1.00000001162, lastTriplet.getClkChgRate());

        // should be able to read and rewrite the same content
        List<String> expectedKernelContent = Files.readAllLines(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"));
        List<String> rewrittenKernelContent = sclkKernel.toLines();
        int numLinesToCheck = Math.max(expectedKernelContent.size(), rewrittenKernelContent.size()) - 1;

        for (int i = 0; i < numLinesToCheck; i++) {
            String expectedLine = null;
            if (i < expectedKernelContent.size()) {
                expectedLine = expectedKernelContent.get(i);
            }

            String rewrittenLine = null;
            if (i < rewrittenKernelContent.size()) {
                rewrittenLine = rewrittenKernelContent.get(i);
            }

            if (expectedLine == null) {
                throw new AssertionFailedError(String.format("No expected line at %d, but rewritten kernel had: '%s'", i, rewrittenLine));
            }

            if (rewrittenLine == null) {
                throw new AssertionFailedError(String.format("No rewritten line at %d, but expected kernel had: '%s'", i, expectedLine));
            }

            assertEquals(expectedLine, rewrittenLine);
        }
    }

    @Test
    public void readTest() throws IOException, TimeConvertException, TextProductException {
        SclkKernel sclkKernel = SclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_0000.tsc"));

        assertEquals(1, sclkKernel.getTriplets().size());

        CorrelationTriplet firstTriplet = sclkKernel.getTriplets().get(0);
        assertEquals(0, firstTriplet.getEncSclk());
        assertEquals(0, firstTriplet.getEncSclkAsLong());
        assertEquals("19-JAN-2006-18:09:05.184000", firstTriplet.getTdtCalStr());
        assertEquals(1.00000000000, firstTriplet.getClkChgRate());

        CorrelationTriplet lastTriplet = sclkKernel.getLastTriplet();
        assertEquals(firstTriplet, lastTriplet);

        sclkKernel = SclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1876.tsc"));
        assertEquals(1877, sclkKernel.getTriplets().size());
        lastTriplet = sclkKernel.getLastTriplet();

        assertEquals(21119278300000.0, lastTriplet.getEncSclk());
        assertEquals(21119278300000L, lastTriplet.getEncSclkAsLong());
        assertEquals("09-JUN-2019-11:28:37.488441", lastTriplet.getTdtCalStr());
        assertEquals(1.00000001166, lastTriplet.getClkChgRate());
    }

    @Test
    public void readAndWriteTestM2020() throws IOException, TimeConvertException, TextProductException, SpiceErrorException {
        SclkKernel sclkKernel = SclkKernel.read(Paths.get("src/test/resources/SclkKernelTests/m2020_168_sclkscet_00009.tsc"));

        assertEquals(242, sclkKernel.getTriplets().size());

        CorrelationTriplet firstTriplet = sclkKernel.getTriplets().get(0);
        assertEquals(0, firstTriplet.getEncSclk());
        assertEquals(0, firstTriplet.getEncSclkAsLong());
        assertEquals(new BigDecimal("-3.5763036976277E-10"), firstTriplet.tdt);
        assertEquals(Double.parseDouble("-3.5763036976277E-10"), firstTriplet.getTdt());
        assertEquals("01-JAN-2000-12:00:00.000000", firstTriplet.getTdtCalStr());
        assertEquals(1.00000000000, firstTriplet.getClkChgRate());

        CorrelationTriplet lastTriplet = sclkKernel.getLastTriplet();
        assertEquals(49770237526016.0, lastTriplet.getEncSclk());
        assertEquals(49770237526016L, lastTriplet.getEncSclkAsLong());
        assertEquals(new BigDecimal("7.5943444760100E+08"), lastTriplet.tdt);
        assertEquals(Double.parseDouble("7.5943444760100E+08"), lastTriplet.getTdt());
        assertEquals("25-JAN-2024-06:00:47.601000", lastTriplet.getTdtCalStr());
        assertEquals(1.0000079500000, lastTriplet.getClkChgRate());

        // append a triplet that is an hour later than the previous final triplet
        CorrelationTriplet newTriplet = new CorrelationTriplet(
                49770237526016.0 + (65536 * 60 * 60),
                TimeConvert.tdtCalStrToTdt("25-JAN-2024-07:00:47.601000"),
                1.0000000000123
        );

        SclkKernel newerSclkKernel = sclkKernel.withAppendedTriplets(Arrays.asList(newTriplet));
        List<String> actualLines = newerSclkKernel.toLines();

        List<String> expectedLines = Files.readAllLines(Paths.get("src/test/resources/SclkKernelTests/m2020_168_sclkscet_00009_appended.tsc"));

        assertEquals(expectedLines, actualLines);
    }

    @Test
    public void formatTripletTest() throws TextProductException, IOException, TimeConvertException {
        SclkKernel sclkKernel = SclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"));

        CorrelationTriplet firstTriplet = sclkKernel.getTriplets().get(0);

        assertEquals(
                "                   0     @19-JAN-2006-18:09:05.184000     1.00000000000       ",
                firstTriplet.format(sclkKernel.getCoefficientsSection().getSclkCoefficientFormat())
        );

        assertEquals(
                "0 @19-JAN-2006-18:09:05.184000 1.00000000000",
                firstTriplet.formatWithSingleSpaces(sclkKernel.getCoefficientsSection().getSclkCoefficientFormat())
        );

        CorrelationTriplet lastTriplet = sclkKernel.getLastTriplet();

        assertEquals(
                "      17672056750000     @02-APR-2017-12:14:45.693714     1.00000001162       ",
                lastTriplet.format(sclkKernel.getCoefficientsSection().getSclkCoefficientFormat())
        );

        assertEquals(
                "17672056750000 @02-APR-2017-12:14:45.693714 1.00000001162",
                lastTriplet.formatWithSingleSpaces(sclkKernel.getCoefficientsSection().getSclkCoefficientFormat())
        );
    }

    @Test
    public void formatTripletTestSciNot() throws IOException, TimeConvertException, TextProductException, SpiceErrorException {
        SclkKernel sclkKernelWithSciNot = SclkKernel.read(Paths.get("src/test/resources/SclkKernelTests/new-horizons_0002_alt.tsc"));

        CorrelationTriplet lastTriplet = sclkKernelWithSciNot.getLastTriplet();

        assertEquals(
                " 2.7962500000000E+09     1.91022072483494E+08              9.99999998760E-01",
                lastTriplet.format(sclkKernelWithSciNot.getCoefficientsSection().getSclkCoefficientFormat())
        );

        assertEquals(
                "2.7962500000000E+09 1.91022072483494E+08 9.99999998760E-01",
                lastTriplet.formatWithSingleSpaces(sclkKernelWithSciNot.getCoefficientsSection().getSclkCoefficientFormat())
        );
    }

    @Test
    public void updateTextFieldTest() throws IOException, TimeConvertException {
        SclkKernel sclkKernel = SclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"));
        SclkKernel updated = sclkKernel.withUpdatedTextField(SclkKernel.TEXT_FIELD_FILENAME, "test_file_name.tsc");
        updated = updated.withUpdatedTextField(SclkKernel.TEXT_FIELD_CREATION_DATE, "38 ABC 01234");

        assertTrue(updated.toLines().stream().anyMatch(line -> line.startsWith("FILENAME = \"test_file_name.tsc\"")));
        assertTrue(updated.toLines().stream().anyMatch(line -> line.startsWith("CREATION_DATE = \"38 ABC 01234\"")));
    }

    @Test
    public void testEqualKernelsDifferentFormatting() throws IOException, TimeConvertException {
        SclkKernel sclkKernelTraditional = SclkKernel.read(Paths.get("src/test/resources/SclkKernelTests/new-horizons_0002.tsc"));
        SclkKernel sclkKernelSciNotation = SclkKernel.read(Paths.get("src/test/resources/SclkKernelTests/new-horizons_0002_alt.tsc"));

        List<CorrelationTriplet> tradTriplets = sclkKernelTraditional.getTriplets();
        List<CorrelationTriplet> sciNotTriplets = sclkKernelSciNotation.getTriplets();

        for (int i = 0; i < 3; i++) {
            assertEquals(tradTriplets.get(i), sciNotTriplets.get(i));
        }

        assertEquals(sclkKernelTraditional, sclkKernelSciNotation);
    }

    @Test
    public void testGetVersionString() {
        assertEquals(
                "1454",
                SclkKernelProductDefinition.getVersionString(
                    Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"),
                    "new-horizons",
                    "_"
                )
        );

        assertEquals(
                "0001",
                SclkKernelProductDefinition.getVersionString(
                        Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_0001.tsc"),
                        "new-horizons",
                        "_"
                )
        );

        assertEquals(
                "0001",
                SclkKernelProductDefinition.getVersionString(
                        Paths.get("src/test/resources/nh_kernels/sclk/new_horizons_0001.tsc"),
                        "new_horizons",
                        "_"
                )
        );
    }
}
