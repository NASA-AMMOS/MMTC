package edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk;

import edu.jhuapl.sd.sig.mmtc.products.model.TextProductException;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.NewSclkKernel;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewSclkKernelTest {

    @Test
    public void readWriteTest() throws IOException, TimeConvertException {
        NewSclkKernel sclkKernel = NewSclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"));

        // sanity checks on the triplets
        assertEquals(1455, sclkKernel.getTriplets().size());

        List<CorrelationTriplet> triplets = sclkKernel.getTriplets();
        CorrelationTriplet firstTriplet = triplets.get(0);
        assertEquals(0, firstTriplet.encSclk);
        assertEquals("19-JAN-2006-18:09:05.184000", firstTriplet.tdtStr);
        assertEquals(1.00000000000, firstTriplet.clkChgRate);

        CorrelationTriplet lastTriplet = triplets.get(triplets.size() - 1);
        assertEquals(17672056750000.0, lastTriplet.encSclk);
        assertEquals("02-APR-2017-12:14:45.693714", lastTriplet.tdtStr);
        assertEquals(1.00000001162, lastTriplet.clkChgRate);

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
        NewSclkKernel sclkKernel = NewSclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_0000.tsc"));

        assertEquals(1, sclkKernel.getTriplets().size());
        CorrelationTriplet firstTriplet = sclkKernel.getTriplets().get(0);
        assertEquals(0, firstTriplet.encSclk);
        assertEquals("19-JAN-2006-18:09:05.184000", firstTriplet.tdtStr);
        assertEquals(1.00000000000, firstTriplet.clkChgRate);

        CorrelationTriplet lastTriplet = sclkKernel.getLastTriplet();
        assertEquals(firstTriplet, lastTriplet);

        sclkKernel = NewSclkKernel.read(Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1876.tsc"));
        assertEquals(1877, sclkKernel.getTriplets().size());
        lastTriplet = sclkKernel.getLastTriplet();

        assertEquals(21119278300000l, lastTriplet.encSclk);
        assertEquals("09-JUN-2019-11:28:37.488441", lastTriplet.tdtStr);
        assertEquals(1.00000001166, lastTriplet.clkChgRate);
    }

    @Test
    public void testGetVersionString() {
        assertEquals(
                "1454",
                NewSclkKernel.getVersionString(
                    Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"),
                    "new-horizons",
                    "_"
                )
        );

        assertEquals(
                "0001",
                NewSclkKernel.getVersionString(
                        Paths.get("src/test/resources/nh_kernels/sclk/new-horizons_0001.tsc"),
                        "new-horizons",
                        "_"
                )
        );

        assertEquals(
                "0001",
                NewSclkKernel.getVersionString(
                        Paths.get("src/test/resources/nh_kernels/sclk/new_horizons_0001.tsc"),
                        "new_horizons",
                        "_"
                )
        );
    }
}
