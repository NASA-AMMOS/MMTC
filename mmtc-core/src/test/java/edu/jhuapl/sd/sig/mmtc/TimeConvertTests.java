package edu.jhuapl.sd.sig.mmtc;

import edu.jhuapl.sd.sig.mmtc.util.CdsTimeCode;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import spice.basic.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit Test cass for functions in Class TimeConvert.
 *
 * NOTE:
 * In order to load the JNISpice library, the VM Options within the Run Configuration of each
 * test that uses SPICE must contain the line below. Tests that use the logging or
 * configuration must contain the options given two lines below.
 * -Djava.library.path=/path/to/JNISpice/lib
 * -Dlog4j.configurationFile=/path/to/log4j2.properties
 */

public class TimeConvertTests {

    @BeforeAll
    static void teardown() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
    }

    @Test
    @DisplayName("loadSpiceKernels Test 1")
    void loadSpiceKernels_test1() throws TimeConvertException {
        Map<String, String> kernelsToLoad = new HashMap<String, String>();
        kernelsToLoad.put("src/test/resources/nh_kernels/lsk/naif0012.tls", "lsk");
        kernelsToLoad.put("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc", "sclk");

        assertFalse(TimeConvert.kernelsLoaded());

        /* Function to test */
        TimeConvert.loadSpiceKernels(kernelsToLoad);
        assertTrue(TimeConvert.kernelsLoaded());
        TimeConvert.unloadSpiceKernels(kernelsToLoad);
        assertFalse(TimeConvert.kernelsLoaded());
    }

    @Test
    @DisplayName("cdsToIsoUtc Test 1")
    void cdsToIsoUtc_test1() throws TimeConvertException {
        String cds = "17557::84628545::8970";
        Integer cdsDay = new Integer(17557);
        Integer cdsMsOfDay = new Integer(84628545);
        Integer cdsSubMs = new Integer(8970);
        String utc;

        /* Function to test */
        utc = TimeConvert.cdsToIsoUtc(cdsDay, cdsMsOfDay, cdsSubMs);

        System.out.println("cdsToIsoUtc_test1: UTC value = " + utc);
        assertEquals("2006-026T23:30:28.545897", utc);
    }

    @Test
    @DisplayName("cdsToIsoUtc Test 2")
    void cdsToIsoUtc_test2() throws TimeConvertException {
            String cds = "21902::41473294::1970";
            Integer cdsDay = new Integer(21902);
            Integer cdsMsOfDay = new Integer(41473294);
            Integer cdsSubMs = new Integer(1970);
            String utc;

            /* Function to test */
            utc = TimeConvert.cdsToIsoUtc(cdsDay, cdsMsOfDay, cdsSubMs);

            System.out.println("cdsToIsoUtc_test2: UTC value = " + utc);
            assertEquals("2017-353T11:31:13.294197", utc);
    }

    @Test
    @DisplayName("cdsToIsoUtc Test 3")
    void cdsToIsoUtc_test3() throws TimeConvertException {
        String cds = "21883::55835368::5580";
        String utc;

        /* Function to test */
        utc = TimeConvert.cdsToIsoUtc(cds);

        System.out.println("cdsToIsoUtc_test3: UTC value = " + utc);
        assertEquals("2017-334T15:30:35.368558", utc);
    }

    @Test
    @DisplayName("isoUtcToCds Test 1")
    void isoUtcToCds_test1() throws TimeConvertException {
        // UTC date and CDS values taken from NH Raw Telemetry Table.

        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2017-334T15:30:35.368558");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test1: CDS value = " + cdsStr);
        assertEquals("21883::55835368::5580", cdsStr);
    }

    @Test
    @DisplayName("isoUtcToCds Test 2")
    void isoUtcToCds_test2() throws TimeConvertException {
        // UTC date and CDS values taken from NH Raw Telemetry Table.

        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2006-026T23:30:28.545897");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test2: CDS value = " + cdsStr);
        assertEquals("17557::84628545::8970", cdsStr);
    }


    @Test
    @DisplayName("isoUtcToCds Test 3")
    void isoUtcToCds_test3() throws TimeConvertException {
        // UTC date and CDS values taken from NH Raw Telemetry Table.
        // Test using a date in leap year before Feb. 29.
        
        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2016-030T07:07:05.255404");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test3: CDS value = " + cdsStr);
        assertEquals("21213::25625255::4040", cdsStr);
    }

    @Test
    @DisplayName("isoUtcToCds Test 4")
    void isoUtcToCds_test4() throws TimeConvertException {
        // UTC date and CDS values taken from NH Raw Telemetry Table.
        // Test using a date in leap year after Feb. 29. Also test edge condition, last day of year.


        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2016-365T05:31:37.919448");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test4: CDS value = " + cdsStr);
        assertEquals("21548::19897919::4480", cdsStr);
    }

    @Test
    @DisplayName("isoUtcToCds Test 5")
    void isoUtcToCds_test5() throws TimeConvertException {
        // UTC date and CDS values taken from NH Raw Telemetry Table.
        // Test edge condition (1st of year).

        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2016-001T20:55:47.784105");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test5: CDS value = " + cdsStr);
        assertEquals("21184::75347784::1050", cdsStr);
    }

    @Test
    @DisplayName("isoUtcToCds Test 6")
    void isoUtcToCds_test6() throws TimeConvertException {
        // UTC date and CDS values taken from NH Raw Telemetry Table.
        // Test using a date in leap year after Feb. 29. Also test edge condition, last day of year.

        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2016-365T05:31:37");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test6: CDS value = " + cdsStr);
        assertEquals("21548::19897000::0000", cdsStr);
    }

    @Test
    @DisplayName("isoUtcToCds Test 7")
    void isoUtcToCds_test7() {
        // UTC date and CDS values taken from NH Raw Telemetry Table.
        // Test more than 6 figures to the right of the seconds decimal.
        // Currently expected to throw an exception since no more than 6 digits are allowed.

        assertThrows(
                TimeConvertException.class,
                () -> TimeConvert.isoUtcToCds("2016-001T20:55:47.78410512345"),
                "Invalid ISO UTC string 2016-001T20:55:47.78410512345 to isoUtcToCds(). Form must be 'yyyy-doyThh:mm:ss.ssssss'. Note that the fraction of second may contain no more than 6 digits."
        );
    }

    @Test
    @DisplayName("isoUtcToCds Test 8")
    void isoUtcToCds_test8() throws TimeConvertException {
        // UTC date and CDS values taken from NH Raw Telemetry Table.
        // Test that a "Z" indicator does not cause a problem.

        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2016-365T05:31:37.919448Z");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test8: CDS value = " + cdsStr);
        assertEquals("21548::19897919::4480", cdsStr);

    }

    @Test
    @DisplayName("isoUtcToCds Test 9")
    void isoUtcToCds_test9() throws TimeConvertException {
        // UTC date and CDS values taken from EC fixed packets.

        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2020-322T21:45:52.730000");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test9: CDS value = " + cdsStr);
        assertEquals("22966::78352730::0000", cdsStr);
    }

    @Test
    @DisplayName("isoUtcToCds Test 10")
    void isoUtcToCds_test10() throws TimeConvertException {
        // UTC string taken from NH RawTelemetryTable_NH_all.csv[M17319]. Expected CDS taken from
        // RawTelemetryTable_NH_all.csv[C17319]

        /* Function to test */
        CdsTimeCode cds = TimeConvert.isoUtcToCds("2017-353T11:31:13.294197");

        String cdsStr = cds.toString();
        System.out.println("isoUtcToCds_test10: CDS value = " + cdsStr);
        assertEquals("21902::41473294::1970", cdsStr);
    }

    @Test
    @DisplayName("isoUtcToCds Test 11")
    void isoUtcToCds_test11() {
        // UTC string taken from NH RawTelemetryTable_NH_all.csv[M17319]. Expected CDS taken from
        // RawTelemetryTable_NH_all.csv[C17319]. This test verifies that the function throws an
        // exception if the time value contains more than 9 digits of precision.

        TimeConvertException thrownTimeConvertException = assertThrows(TimeConvertException.class, () -> {
            TimeConvert.isoUtcToCds("2017-353T11:31:13.1234567890");
        });

        assertEquals("Invalid ISO UTC string 2017-353T11:31:13.1234567890 to isoUtcToCds(). Form must be 'yyyy-doyThh:mm:ss.sssssssss'. Note that the fraction of second may contain no more than 9 digits.", thrownTimeConvertException.getMessage());
    }
    @Test
    @DisplayName("cdsToEt Test 1")
    void cdsToEt_test1() throws TimeConvertException {
        /*
         * Equivalent UTC string time is 2017-353T11:31:13.294197
         * ET value obtained from NAIF tool:
         * chronos -setup chronos_setup_nh.txt -from utc -to et -totype SECONDS
         *      -time 2017-353T11:31:13.294197
         */

        TimeConvert.loadSpiceKernel("src/test/resources/nh_kernels/lsk/naif0012.tls");

        String cds = "21902::41473294::1970";
        Integer cdsDay = new Integer(21902);
        Integer cdsMsOfDay = new Integer(41473294);
        Integer cdsSubMs = new Integer(1970);

        /* Function to test */
        Double et = TimeConvert.cdsToEt(cdsDay, cdsMsOfDay, cdsSubMs);

        DecimalFormat secfmt = new DecimalFormat("##.######");
        secfmt.setRoundingMode(RoundingMode.HALF_UP);
        String secStr = secfmt.format(et.doubleValue());
        System.out.println("cdsToEt_test1: ET value = " + secStr);

        assertEquals(566955142.477760, et.doubleValue(), 0.00001);
    }


    @Test
    @DisplayName("cdsToTdt Test 1")
    void cdsToTdt_test1() throws SpiceErrorException, TimeConvertException {
        /*
         * Equivalent UTC string time is 2017-353T11:31:13.294197
         * cds and expected TDT values taken from New Horizons
         * OPERATIONS_SCLK_KERNEL_PARMS.tbl.
         *
         * Expected TDT accounts for OWLT in New Horizons data. Therefore,
         * subtract the OWLT of 20533.047277 seconds from the computed TDT to
         * make it match the expected TDT within 0.5 seconds. The expected
         * TDT also has other offsets within it that contribute the
         * within 0.5 second difference.
         * */

        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        String cds = "21902::41473294::1970";
        Integer cdsDay = new Integer(21902);
        Integer cdsMsOfDay = new Integer(41473294);
        Integer cdsSubMs = new Integer(1970);

        /* Function to test */
        Double tdt = TimeConvert.cdsToTdt(cdsDay, cdsMsOfDay, cdsSubMs);

        DecimalFormat secfmt = new DecimalFormat("##.######");
        secfmt.setRoundingMode(RoundingMode.HALF_UP);
        String secStr = secfmt.format(tdt.doubleValue() - 20533.047277);
        System.out.println("cdsToTdt_test1: TDT value = " + secStr);

        /* assertEquals(566955142.477760, tdt.doubleValue(), 0.00001); */
        assertEquals(566934608.95675, (tdt.doubleValue() - 20533.047277), 0.5);
    }


    @Test
    @DisplayName("cdsToTdtStr Test 1")
    void cdsToTdtStr_test1() throws SpiceErrorException, TimeConvertException {
        /*
         * Equivalent UTC string time is 19-DEC-2017-11:31:13.294197
         * cds and equivalent TDT values taken from New Horizons
         * OPERATIONS_SCLK_KERNEL_PARMS.tbl.
         * Compute expected TDT string value by adding 32.184 + 37 to
         * the UTC value (TDT-UTC offset of 32.184 seconds plus
         * 37 leap seconds as of 2017)
         * */
        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        String cds = "21902::41473294::1970";
        Integer cdsDay = new Integer(21902);
        Integer cdsMsOfDay = new Integer(41473294);
        Integer cdsSubMs = new Integer(1970);

        /* Function to test */
        String tdt = TimeConvert.cdsToTdtStr(cdsDay, cdsMsOfDay, cdsSubMs);


        System.out.println("cdsToTdtStr_test1: TDT value = " + tdt);

        assertEquals("19-DEC-2017-11:32:22.478197", tdt);
    }


    @Test
    @DisplayName("tdtStrToTdt Test 1")
    void tdtStrToTdt_test1() throws SpiceErrorException, TimeConvertException {
        /*
         * TDT input and expected values taken from New Horizons
         * OPERATIONS_SCLK_KERNEL_PARMS.tbl.
         * */

        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        /* Function to test */
        Double tdt = TimeConvert.tdtCalStrToTdt("19-DEC-2017-05:50:08.956750");

        DecimalFormat secfmt = new DecimalFormat("##.######");
        secfmt.setRoundingMode(RoundingMode.HALF_UP);
        String secStr = secfmt.format(tdt.doubleValue());
        System.out.println("tdtStrToTdt_test1: Expected TDT value = " +
                secfmt.format(566934608.95675));
        System.out.println("tdtStrToTdt_test1: Actual   TDT value = " + secStr);

        assertEquals(566934608.95675, tdt.doubleValue());
    }


    @Test
    @DisplayName("tdtToTdtStr Test 1")
    void tdtToTdtStr_test1() throws SpiceErrorException, TimeConvertException {
        /*
         * TDT input and expected values taken from New Horizons
         * OPERATIONS_SCLK_KERNEL_PARMS.tbl.
         * */

        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        /* Function to test */
        String tdtstr = TimeConvert.tdtToTdtCalStr(new Double(566934608.95675));

        System.out.println("tdtToTdtStr_test1: TDT value          = " + tdtstr);

        assertEquals("19-DEC-2017-05:50:08.956750", tdtstr);
    }


    @Test
    @DisplayName("etToTdt Test 1")
    void etToTdt_test1() throws SpiceErrorException, TimeConvertException {
        /*
         * TDT input and expected values taken from New Horizons
         * OPERATIONS_SCLK_KERNEL_PARMS.tbl. UTC input to chronos
         * computed by subtracting (32.184 + 37 leapsec) from NH TDT.
         *
         * ET value (566955142.477760) obtained from NAIF tool:
         * chronos_setup_nh.txt -from utc -to et -totype SECONDS -format '##########.######'
         *    -time 2017-353T11:31:13.294197
         *
         * Expected TDT accounts for OWLT in New Horizons data. Therefore,
         * subtract the OWLT of 20533.047277 seconds from the computed TDT to
         * make it match the expected TDT within 0.5 seconds. The expected
         * TDT also has other offsets within it that contribute the
         * within 0.5 second difference.
         * */

        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        /* Function to test */
        Double tdt = TimeConvert.etToTdt(new Double(566955142.477760));

        DecimalFormat secfmt = new DecimalFormat("##.######");
        secfmt.setRoundingMode(RoundingMode.HALF_UP);
        String secStr = secfmt.format(tdt.doubleValue());
        System.out.println("etToTdt_test1: Expected TDT value = " +
                secfmt.format(566934608.95675));
        System.out.println("etToTdt_test1: Actual   TDT value = " + secStr);

        assertEquals(566934608.95675, (tdt.doubleValue() - 20533.047277), 0.5);
    }

    @Test
    @DisplayName("utcToEt Test 1")
    void utcToEt_test1() throws SpiceErrorException, TimeConvertException {
        /*
         * ET value (566955142.477760) obtained from NAIF tool:
         * chronos_setup_nh.txt -from utc -to et -totype SECONDS -format '##########.######'
         *    -time 2017-353T11:31:13.294197
         * */

        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        /* Function to test */
        Double et = TimeConvert.utcToEt("2017-353T11:31:13.294197");

        DecimalFormat secfmt = new DecimalFormat("##.######");
        secfmt.setRoundingMode(RoundingMode.HALF_UP);
        String secStr = secfmt.format(et);
        System.out.println("utcToEt_test1: Expected et value = " +
                secfmt.format(566955142.477760));
        System.out.println("utcToEt_test1: Actual et value = " + secStr);

        assertEquals(566955142.477760, et, 0.001);
    }

    /**
     * SCLK values taken from the New Horizons RAW_TELEMETRY.tbl.
     */
    @Test
    @DisplayName("sclkToSclkStr Test 1")
    void sclkToSclkStr_test1() throws TimeConvertException {
        loadBasicNhKernels();

        String sclkStr = TimeConvert.sclkToSclkStr(98, 3, 375968446, 26163);
        assertEquals("3/375968446:26163", sclkStr);
    }

    @Test
    public void sclkToSclkDoubleTest() throws TimeConvertException {
        loadBasicNhKernels();
        final double actualSclk = TimeConvert.sclkStrToSclk("3/375968446:26163", 50_000, 98);

        /*
         * Expected value below calculated via:
         *
         * chronos -setup mmtc-core/src/test/resources/nh_kernels/lsk/naif0012.tls mmtc-end-to-end-tests/src/test/resources/regression-test-datasets/nh/output/sclk/new-horizons_1001.tsc -sc -98 -from sclk -fromtype sclk -to sclk -totype ticks -time "3/375968446.26163"
         *             18798422326163                                                (SCLK/TICKS)
         *
         *  (and then dividing 18798422326163 by 50_000 yields 375968446.52326)
         */
        assertEquals(375968446.52326, actualSclk);
    }

    /**
     * SCLK values taken from the New Horizons RAW_TELEMETRY.tbl.
     */
    @Test
    @DisplayName("sclkToSclkStr Test 2")
    void sclkToSclkStr_test2() throws TimeConvertException {
        loadBasicNhKernels();

        /* Function to test */
        String sclkStr = TimeConvert.sclkToSclkStr(98, 3, 375968458, 23708);

        assertEquals("3/375968458:23708", sclkStr);
    }

    @Test
    public void sclkToSclkDoubleTest2() throws TimeConvertException {
        loadBasicNhKernels();
        final double actualSclk = TimeConvert.sclkStrToSclk("3/375968458:23708", 50_000, 98);

        /*
         * Expected value below calculated via:
         *
         * chronos -setup mmtc-core/src/test/resources/nh_kernels/lsk/naif0012.tls mmtc-end-to-end-tests/src/test/resources/regression-test-datasets/nh/output/sclk/new-horizons_1001.tsc -sc -98 -from sclk -fromtype sclk -to sclk -totype ticks -time "3/375968458:23708"
         *            18798422923708                                                (SCLK/TICKS)
         *
         *  (and then dividing 18798422923708 by 50_000 yields 375968458.47416)
         *
         * NOTE: Expected value aligns with a value created by appending NH iMET+TF_Offset
         * (NH OPS_TIME_ENGR.xlsx[B1543] + OPERATIONS_SCLK_KERNEL_PARMS.tbl.xlsx[L17319].
         * OPERATIONS_SCLK_KERNEL_PARMS.tbl.xlsx[L17319] gives TF_Offset=0.47417. Computing 23708./50000.=0.47416.
         */
        assertEquals(375968458.47416, actualSclk);
    }

    @Test
    @DisplayName("sclkToEt Test 1")
    void sclkToEt_test1() throws SpiceErrorException, TimeConvertException {
        // SCLK values taken from the New Horizons RAW_TELEMETRY.tbl.

        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");
        KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");

        Integer naif_sc_id  = -98;
        Integer partition   = 3;
        Integer coarse      = 353441130;
        Integer fine        = 23453;

        // function under test
        Double et = TimeConvert.sclkToEt(naif_sc_id, partition, coarse, fine);

        /*
         * Expected value independently calculated:
         *
         * $ chronos -setup mmtc-core/src/test/resources/nh_kernels/lsk/naif0012.tls mmtc-core/src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc -sc -98 -from sclk -fromtype sclk -to et -totype seconds -time "3/353441130:23453"
         *         544407281.164                                             (ET/SECONDS)
         */
        double expectedEtFrmChronos = 544407281.164;

        DecimalFormat secfmt = new DecimalFormat("##.########");
        secfmt.setRoundingMode(RoundingMode.HALF_UP);
        String secStr = secfmt.format(et.doubleValue());
        System.out.println("sclkToEt_test1: Expected et value = " +
                secfmt.format(expectedEtFrmChronos));
        System.out.println("sclkToEt_test1: Actual   et value = " + secStr);

        assertEquals(expectedEtFrmChronos, et, 0.001);
    }


    @Test
    void sclkToEncSclk_test1() throws SpiceErrorException, TimeConvertException {
        // SCLK values taken from the New Horizons RAW_TELEMETRY.tbl.
        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");
        KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");

        Integer naif_sc_id = new Integer(-98);
        Integer partition = new Integer(3);
        Integer coarse = new Integer(353441135);
        Integer fine       = new Integer(0);

        /* Function to test */
        Double encSclk_a = TimeConvert.sclkToEncSclk(naif_sc_id, partition, coarse, fine);
        Double encSclk_b = TimeConvert.sclkToEncSclk(naif_sc_id, partition, coarse * 1.0);

        assertEquals( 17672056750000., encSclk_a, 0.1);
        assertEquals( 17672056750000., encSclk_b, 0.1);
    }


    @Test
    void sclkToEncSclk_test2() throws SpiceErrorException, TimeConvertException {
        // SCLK values taken from the New Horizons RAW_TELEMETRY.tbl.
        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");
        KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");

        Integer naif_sc_id = -98;
        Integer partition = 3;
        Integer coarse = 353318723;
        Integer fine = 0;

        /* Function to test */
        Double encSclk_a = TimeConvert.sclkToEncSclk(naif_sc_id, partition, coarse, fine);
        Double encSclk_b = TimeConvert.sclkToEncSclk(naif_sc_id, partition, coarse * 1.0);

        assertEquals(17665936150000., encSclk_a, 0.01);
        assertEquals(17665936150000., encSclk_b, 0.01);
    }

    @Test
    void sclkToEncSclk_test3() throws SpiceErrorException, TimeConvertException {
        // SCLK values taken from the New Horizons RAW_TELEMETRY.tbl and modified
        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");
        KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");

        Integer naif_sc_id = -98;
        Integer partition = 3;
        Integer coarse = 353318723;
        Integer fine = 12_319; // NH SCLK fine tick modulus is 50,000

        /* Function to test */
        Double encSclk_a = TimeConvert.sclkToEncSclk(naif_sc_id, partition, coarse, fine);
        Double encSclk_b = TimeConvert.sclkToEncSclk(
                naif_sc_id,
                partition,
                new BigDecimal(coarse).add(
                                new BigDecimal(fine)
                                        .setScale(16, RoundingMode.HALF_UP)
                                        .divide(new BigDecimal(50_000), RoundingMode.HALF_UP)
                        )
                        .doubleValue()
        );

        assertEquals(17665936150000. + fine, encSclk_a, 0.01);
        assertEquals(17665936150000. + fine, encSclk_b, 0.01);
    }

    @Test
    void leapSeconds_Test1() throws TimeConvertException {
        TimeConvert.loadSpiceKernel("src/test/resources/nh_kernels/lsk/naif0012.tls");

        List<TimeConvert.LeapSecond> leapSeconds = TimeConvert.parseLeapSeconds();
        assertEquals(28, leapSeconds.size());
        TimeConvert.LeapSecond item;
        OffsetDateTime date;
        int numls;

        ListIterator<TimeConvert.LeapSecond> itr = leapSeconds.listIterator();
        while(itr.hasNext()) {
            item = itr.next();
            date = item.leapSecOccurrence;
            numls = item.leapSecDeltaEt;
            System.out.println(date+"  " + numls);
        }

        TimeConvert.unloadSpiceKernels();
    }

    @Test
    void leapSeconds_Test2() throws TimeConvertException {
        TimeConvert.loadSpiceKernel("src/test/resources/naif0013-neg.tls");

        List<TimeConvert.LeapSecond> leapSeconds = TimeConvert.parseLeapSeconds();
        assertEquals(29, leapSeconds.size());

        assertEquals(37, leapSeconds.get(27).leapSecDeltaEt);
        assertEquals(36, leapSeconds.get(28).leapSecDeltaEt);

        TimeConvert.unloadSpiceKernels();
    }


    @Test
    @DisplayName("utcTdtOffset Test 1")
    void utcTdtOffset_Test1() throws SpiceErrorException, TimeConvertException {
        /**
         * Verify that the method can retrieve the list of leap seconds from SPICE as provided in the
         * Leap Seconds Kernel.
         */

        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        Double offset = TimeConvert.utcTdtOffset();
        assertEquals(offset, 32.184, 3);
    }


    @Test
    void getDeltaEtBefore_Test1() throws TimeConvertException, SpiceErrorException {
        KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");

        Integer ls1 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("1973-092T21:14:32.456987"));
        assertEquals(12, ls1.intValue());

        Integer ls2 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("1980-074T21:14:32.456987"));
        assertEquals(19, ls2.intValue());

        Integer ls3 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2012-153T16:43:22.123456"));
        assertEquals(34, ls3.intValue());

        Integer ls4 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2012-183T13:43:12.123756"));
        assertEquals(35, ls4.intValue());

        Integer ls2017 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2017-001T00:00:00.000000"));
        assertEquals(36, ls2017.intValue());

        Integer ls2017_2 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2017-001T00:00:00.000001"));
        assertEquals(37, ls2017_2.intValue());
    }

    @Test
    void getDeltaEtBeforeNegLeapSec() throws TimeConvertException, SpiceErrorException {
        KernelDatabase.load("src/test/resources/naif0013-neg.tls");

        Integer ls2017 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2017-001T00:00:00.000000"));
        assertEquals(36, ls2017.intValue());

        Integer ls2017_2 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2017-001T00:00:00.000001"));
        assertEquals(37, ls2017_2.intValue());

        Integer ls2025 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2025-001T00:00:00.000000"));
        assertEquals(37, ls2025.intValue());

        Integer ls2025_2 = TimeConvert.getDeltaEtBefore(TimeConvert.parseIsoDoyUtcStr("2025-001T00:00:00.000001"));
        assertEquals(36, ls2025_2.intValue());
    }

    @Test
    @DisplayName("eq Test 1")
    void eq_test1() {
        Double first  = new Double(1.234567);
        Double second = new Double(1.234550);
        boolean areEqual = TimeConvert.eq(first, second, 0.0001);
        assertTrue(areEqual);
    }


    @Test
    @DisplayName("eq Test 2")
    void eq_test2() {
        Double first  = new Double(1.234567);
        Double second = new Double(1.234550);
        boolean areEqual = TimeConvert.eq(first, second, 0.000000001);
        assertFalse(areEqual);
    }


    @Test
    @DisplayName("ne Test 1")
    void ne_test1() {
        Double first  = new Double(1.234567);
        Double second = new Double(1.235550);
        boolean areEqual = TimeConvert.ne(first, second, 0.0000000001);
        assertTrue(areEqual);
    }

    @Test
    @DisplayName("ne Test 2")
    void ne_test2() {
        Double first  = new Double(1.234566);
        Double second = new Double(1.234567);
        boolean areEqual = TimeConvert.ne(first, second, 0.01);
        assertFalse(areEqual);
    }

    @Test
    @DisplayName("nameToNaifId_test1")
    void nameToNaifId_test1() throws TimeConvertException {
        // Verify that the nameToNaifId function works. Need to load the DSN stations ephemeris kernel.


        Integer naifID = TimeConvert.nameToNaifId("DSS-63");
        assertEquals(naifID.intValue(), 399063);
    }

    @Test
    @DisplayName("getSclkTickRate_test1")
    void getSclkTickRate_test1() throws TimeConvertException, SpiceErrorException {
        // Verify that the getSclkTickRate function works. Need to load the SCLK kernel.

        KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");

        int naif_sc_id = -98;
        Integer tickRate = TimeConvert.getSclkKernelTickRate(naif_sc_id);
        assertEquals(tickRate.intValue(), 50000);
    }

    @Test
    @DisplayName("getNumSclkStages_test1")
    void getNumSclkStages_test1() throws SpiceErrorException, TimeConvertException {
        /*
         * Verify that the getNumSclkStages function works. Need to load the SCLK kernel.
         * Verifies that it returns the value in SCLK01_N_FIELDS_98 of the SCLK kernel.
         * For the kernel used here, that number should be 2.
         */
        KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");

        int naif_sc_id = -98;
        Integer stages = TimeConvert.getNumSclkStages(naif_sc_id);
        assertEquals(stages.intValue(), 2);
    }

    @Test
    @DisplayName("getNumSclkStages_test2")
    void getNumSclkStages_test2() throws SpiceErrorException, TimeConvertException {
        /*
         * Verify that the getNumSclkStages function works. Need to load the SCLK kernel.
         * Verifies that it returns the value in SCLK01_N_FIELDS_98 of the SCLK kernel.
         * For the fake kernel used here, that number should be 3.
         */

        KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_fake_3stage.tsc");

        int naif_sc_id = -98;
        Integer tickRate = TimeConvert.getNumSclkStages(naif_sc_id);
        assertEquals(tickRate.intValue(), 3);
    }


    @Test
    @DisplayName("getLoadedKernelNames_test1")
    void getLoadedKernelNames_test1() throws TimeConvertException {
        // Verify that the getLoadedKernelNames function works. Need to load all kernels.

        TimeConvert.unloadSpiceKernels();

        /*
         * Total kernels loaded is 12 from the nh_tk_meta_mmtc_test.tm metakernel plus the
         * four below.
         */
        Map<String, String> kernelsToLoad = new HashMap<>();
        kernelsToLoad.put("src/test/resources/nh_kernels/mk/nh_tk_meta_mmtc_test.tm", "mk");
        kernelsToLoad.put("src/test/resources/nh_kernels/lsk/naif0012.tls", "lsk");
        kernelsToLoad.put("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc", "sclk");
        kernelsToLoad.put("src/test/resources/nh_kernels/spk/nh_pred_alleph_od124.bsp", "spk");
        TimeConvert.loadSpiceKernels(kernelsToLoad);

        List<String> klist = TimeConvert.getLoadedKernelNames();

        for (String kernel : klist) {
            System.out.println(kernel);
        }

        assertEquals(16, klist.size());
        TimeConvert.unloadSpiceKernels();
    }

    @Test
    void parseIsoDoyUtcStr_test1() {
        String utc = "2019-322T21:37:15.133";
        OffsetDateTime timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        String newUtcStr = TimeConvert.timeToIsoUtcString(timeobj);

        assertEquals(newUtcStr, utc+"000");
    }

    @Test
    void parseIsoDoyUtcStr_test2() {
        String utc = "2019-322T21:37:15";
        OffsetDateTime timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        String newUtcStr = TimeConvert.timeToIsoUtcString(timeobj);

        assertEquals(newUtcStr, utc+".000000");
    }

    @Test
    void parseIsoDoyUtcStr_test3() {
        String utc = "2019-322T21:37:15.123";
        OffsetDateTime timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        assertEquals(123000000, timeobj.getNano());

        utc = "2019-322T21:37:15.1234";
        timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        assertEquals(123400000, timeobj.getNano());

        utc = "2019-322T21:37:15.12345";
        timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        assertEquals(123450000, timeobj.getNano());

        utc = "2019-322T21:37:15.123456";
        timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        assertEquals(123456000, timeobj.getNano());

        utc = "2019-322T21:37:15.1234567";
        timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        assertEquals(123456700, timeobj.getNano());

        utc = "2019-322T21:37:15.12345678";
        timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        assertEquals(123456780, timeobj.getNano());

        utc = "2019-322T21:37:15.123456789";
        timeobj = TimeConvert.parseIsoDoyUtcStr(utc);
        assertEquals(123456789, timeobj.getNano());

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    TimeConvert.parseIsoDoyUtcStr("2019-322T21:37:15.123456789123");
                },
                "Cannot convert times with higher-than-nanosecond-precision: 2019-322T21:37:15.123456789123"
        );
    }

    @Test
    @DisplayName("isNumeric Test 1")
    void isNumeric_test1() {
        // Verify that the isNumeric() function works.

        String value = "7";
        boolean isNum = TimeConvert.isNumeric(value);

        assertTrue(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 2")
    void isNumeric_test2() {
        // Verify that the isNumeric() function works.

        String value = "1234";
        boolean isNum = TimeConvert.isNumeric(value);

        assertTrue(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 3")
    void isNumeric_test3() {
        String value = "-456";
        boolean isNum = TimeConvert.isNumeric(value);

        assertTrue(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 4")
    void isNumeric_test4() {
        String value = "0";
        boolean isNum = TimeConvert.isNumeric(value);

        assertTrue(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 5")
    void isNumeric_test5() {
        String value = "3.14159";
        boolean isNum = TimeConvert.isNumeric(value);

        assertTrue(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 6")
    void isNumeric_test6() {
        String value = "";
        boolean isNum = TimeConvert.isNumeric(value);

        assertFalse(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 7")
    void isNumeric_test7() {
        // Verify that the isNumeric() function works.

        String value = " ";
        boolean isNum = TimeConvert.isNumeric(value);

        assertFalse(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 8")
    void isNumeric_test8() {
        // Verify that the isNumeric() function works.

        String value = "NaN";
        boolean isNum = TimeConvert.isNumeric(value);

        assertFalse(isNum);
    }

    @Test
    @DisplayName("isNumeric Test 9")
    void isNumeric_test9() {
        // Verify that the isNumeric() function works.

        String value = "notanumber";
        boolean isNum = TimeConvert.isNumeric(value);

        assertFalse(isNum);
    }

    @Test
    public void testSclkStrToSclk() throws TimeConvertException {
        loadBasicNhKernels();

        final double computedSclk = TimeConvert.sclkStrToSclk("1/12345:10268", 50_000, 98);

        final double fractional = 10268 / 50_000.0;
        final double expectedSclk = 12345 + fractional;

        assertEquals(expectedSclk, computedSclk);
    }

    @Test
    public void testSclkToSclkStr() throws TimeConvertException {
        loadBasicNhKernels();

        final String computedSclkStr = TimeConvert.sclkToSclkStr(98, 1, 12345, 10_268);

        assertEquals("1/12345:10268", computedSclkStr);
    }

    private void loadBasicNhKernels() throws TimeConvertException {
        Map<String, String> kernelsToLoad = new HashMap<>();
        kernelsToLoad.put("src/test/resources/nh_kernels/lsk/naif0012.tls", "lsk");
        kernelsToLoad.put("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc", "sclk");
        TimeConvert.loadSpiceKernels(kernelsToLoad);
    }

    @Test
    public void testNanosBetween() {
        final OffsetDateTime now = OffsetDateTime.now();
        final OffsetDateTime later = now.plusMinutes(5);

        // it'll be positive if the first term is earlier than the second term
        assertTrue(ChronoUnit.NANOS.between(now, later) > 0);
        assertEquals(5L * 60 * 1000 * 1000 * 1000, ChronoUnit.NANOS.between(now, later));

        // it'll be negative if the first term is later than the second term
        assertTrue(ChronoUnit.NANOS.between(later, now) < 0);
        assertEquals(-5L * 60 * 1000 * 1000 * 1000, ChronoUnit.NANOS.between(later, now));
    }
}
