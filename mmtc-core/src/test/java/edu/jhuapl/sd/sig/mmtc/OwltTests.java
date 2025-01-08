package edu.jhuapl.sd.sig.mmtc;

import java.util.*;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import edu.jhuapl.sd.sig.mmtc.util.Owlt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit Test cass for functions in Class Owlt.
 *
 * NOTE:
 * In order to load the JNISpice library, the VM Options within the Run Configuration of each
 * test that uses SPICE must contain the line below. Tests that use the logging or
 * configuration must contain the options given two lines below.
 * -Djava.library.path=/path/to/JNISpice/lib
 * -Dlog4j.configurationFile=/path/to/log4j2.properties
 */

public class OwltTests {
    private static final int SPICE_EARTH_CENTER_OF_MASS_ID = 399;
    private static final int SPICE_NH_SC_ID = -98;

    @BeforeAll
    static void setup() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();

        Map<String, String> kernelsToLoad = new HashMap<>();

        if (!Owlt.isInitialized()) {
            kernelsToLoad.put("src/test/resources/nh_kernels/mk/nh_tk_meta_mmtc_test.tm", "mk");
            kernelsToLoad.put("src/test/resources/nh_kernels/lsk/naif0012.tls", "lsk");
            kernelsToLoad.put("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc", "sclk");
            kernelsToLoad.put("src/test/resources/nh_kernels/spk/nh_pred_alleph_od124.bsp", "spk");
            Owlt.initialize(kernelsToLoad);
        }

        assertTrue(Owlt.isInitialized());
    }

    @AfterAll
    static void teardown() {
        TimeConvert.unloadSpiceKernels();
        assertFalse(Owlt.isInitialized());
    }

    /**
     * From NH flyby date. Note that there are two overloaded versions of Owlt.getDownlinkOwlt.
     * The one that computes OWLT based on an ISO UTC calendar time converts that value to ET
     * and passes it to the one that receives ET. Therefore, both functions are tested with
     * this test case.
     */
    @Test
    void getDownlinkOwlt_test1() throws TimeConvertException {
        final String cds = "21014::03240641::0020"; /* July 14, 2015 - day of NH Pluto encounter */
        final String utc = TimeConvert.cdsToIsoUtc(cds);
        final Integer dsn_id = 399063; /* DSS-63 */

        final Double owlt = Owlt.getDownlinkOwlt(dsn_id, SPICE_NH_SC_ID, utc);

        assertEquals(15921.056815, owlt, 0.001);
    }

    /**
     * From NH flyby date. Note that there are two overloaded versions of Owlt.getDownlinkOwlt.
     * The one that computes OWLT based on an ISO UTC calendar time converts that value to ET
     * and passes it to the one that receives ET. Therefore, both functions are tested with
     * this test case.
     */
    @Test
    void getDownlinkOwlt_test2() throws TimeConvertException {
        final String utc = "2015-07-15T00:54:0.641002";
        final Integer dsn_id = 399063; /* DSS-63 */

        final Double owlt = Owlt.getDownlinkOwlt(dsn_id, SPICE_NH_SC_ID, utc);

        assertEquals(15921.056815, owlt, 0.001);
    }

    /**
     * From NH flyby date. Note that there are two overloaded versions of Owlt.getDownlinkOwlt.
     * This function calls one that computes OWLT based on an ISO UTC calendar time, which then
     * converts that value to ET and passes it to the one that receives ET. Therefore, all three
     * of these functions are tested with this test case.
     *
     * NOTE: The spacecraft ephemeris used here is not the one used in the original timekeeping
     * run. It is a later reconstructed ephemeris. This is likely why the actual and expected
     * OWLT values differ by nearly 0.01 seconds.
     */
    @Test
    void getDownlinkOwltCds_test1() throws TimeConvertException {
        final String cds = "21902::01833119::6840";
        final String utc = TimeConvert.cdsToIsoUtc(cds);
        final Integer dsn_id = 399014; /* DSS-14 */

        final Double owlt = Owlt.getDownlinkOwlt(dsn_id, SPICE_NH_SC_ID, utc);

        assertEquals(20529.972271, owlt, 0.01);
    }

    @Test
    void testVariousDownlinkOwltToEarthCenterOfMass() throws TimeConvertException {
        // the chronos invocations, which specify the same kernel set as this test class, supplied the ground truth for this test
        // note that chronos calculates the OWLT from the given SC to the Earth's center of mass
        /*
        $ chronos -setup src/test/resources/nh_kernels/mk/nh_tk_meta_mmtc_test.tm src/test/resources/nh_kernels/lsk/naif0012.tls src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc src/test/resources/nh_kernels/spk/nh_pred_alleph_od124.bsp -SC -98 -from UTC -fromType ERT -to UTC -toType LT -time 2015-001T01:02:03.456
                    16102.381          (UTC/LT)
        $ chronos -setup src/test/resources/nh_kernels/mk/nh_tk_meta_mmtc_test.tm src/test/resources/nh_kernels/lsk/naif0012.tls src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc src/test/resources/nh_kernels/spk/nh_pred_alleph_od124.bsp -SC -98 -from UTC -fromType ERT -to UTC -toType LT -time 2015-101T01:02:03.456
                    15977.910          (UTC/LT)
        $ chronos -setup src/test/resources/nh_kernels/mk/nh_tk_meta_mmtc_test.tm src/test/resources/nh_kernels/lsk/naif0012.tls src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc src/test/resources/nh_kernels/spk/nh_pred_alleph_od124.bsp -SC -98 -from UTC -fromType ERT -to UTC -toType LT -time 2016-029T01:02:03.456
                    17691.616          (UTC/LT)
        $ chronos -setup src/test/resources/nh_kernels/mk/nh_tk_meta_mmtc_test.tm src/test/resources/nh_kernels/lsk/naif0012.tls src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc src/test/resources/nh_kernels/spk/nh_pred_alleph_od124.bsp -SC -98 -from UTC -fromType ERT -to UTC -toType LT -time 2016-300T01:02:03.456
                    18513.011          (UTC/LT)
        */
        Map<String, Double> mapFromTimesToExpectedOwlts = new HashMap<>();
        mapFromTimesToExpectedOwlts.put("2015-001T01:02:03.456", 16102.381);
        mapFromTimesToExpectedOwlts.put("2015-101T01:02:03.456", 15977.910);
        mapFromTimesToExpectedOwlts.put("2016-029T01:02:03.456", 17691.616);
        mapFromTimesToExpectedOwlts.put("2016-300T01:02:03.456", 18513.011);

        for (Map.Entry<String, Double> entry : mapFromTimesToExpectedOwlts.entrySet()) {
            final String utcTime = entry.getKey();
            final Double expectedOwlt = entry.getValue();
            final Double calculatedOwlt = Owlt.getDownlinkOwlt(SPICE_EARTH_CENTER_OF_MASS_ID, SPICE_NH_SC_ID, utcTime);

            // ensure that the like-for-like (SC to Earth center of mass) expected OWLT and MMTC-calculated OWLT are within 0.001 sec of each other
            assertEquals(
                    expectedOwlt,
                    calculatedOwlt,
                    0.001
            );

            // as an extra step, ensure that when calculating OWLT to e.g. DSS-63, the result is within .1 second of that calculated from the expected OWLT to the Earth center of mass
            final int dss63SpiceId = 399063;
            final Double calculatedOwltToDss63 = Owlt.getDownlinkOwlt(dss63SpiceId, SPICE_NH_SC_ID, utcTime);
            assertEquals(
                    expectedOwlt,
                    calculatedOwltToDss63,
                    0.1
            );
        }
    }
}
