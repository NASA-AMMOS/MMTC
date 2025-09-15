package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrameSampleValidatorTest {
    private static final int TK_SCLK_MODULUS = 128;

    @Test
    public void testBasicValidCases() throws MmtcException {
        assertValid(new ArrayList<>());

        List<FrameSample> validFrameSamples = new ArrayList<>();

        FrameSample fs;

        // the below frame samples should pass validation both in a list by themselves and in a list together

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:00:00.000000");
        fs.setPathId(1);
        fs.setTkSclkCoarse(0);
        fs.setTkSclkFine(0);
        fs.setTkDataRateBps(BigDecimal.valueOf(1));
        fs.setDerivedTdBe(1.0);
        assertValid(fs);
        validFrameSamples.add(fs);


        fs = new FrameSample();
        fs.setErtStr("2010-001T00:00:01.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(127);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertValid(fs);
        validFrameSamples.add(fs);

        assertValid(validFrameSamples);
    }

    @Test
    public void testBasicInvalidCases() {
        assertNotValid(new FrameSample());

        List<FrameSample> invalidFrameSamples = new ArrayList<>();

        FrameSample fs;

        fs = new FrameSample();
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(123);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertNotValid(fs);
        invalidFrameSamples.add(fs);

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:00:00.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(128);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertNotValid(fs);
        invalidFrameSamples.add(fs);

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:01:00.000000");
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(123);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertNotValid(fs);
        invalidFrameSamples.add(fs);

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:02:00.000000");
        fs.setPathId(2);
        fs.setTkSclkFine(123);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertNotValid(fs);
        invalidFrameSamples.add(fs);

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:03:00.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertNotValid(fs);
        invalidFrameSamples.add(fs);

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:04:00.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(123);
        fs.setDerivedTdBe(2.365);
        assertNotValid(fs);
        invalidFrameSamples.add(fs);

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:05:00.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(123);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        assertNotValid(fs);
        invalidFrameSamples.add(fs);

        assertNotValid(invalidFrameSamples);
    }

    @Test
    public void testDuplicateErtCase() throws MmtcException {
        List<FrameSample> validFrameSamples = new ArrayList<>();

        FrameSample fs;

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:00:01.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(127);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertValid(fs);
        validFrameSamples.add(fs);

        fs = new FrameSample();
        fs.setErtStr("2010-001T00:00:01.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1235);
        fs.setTkSclkFine(127);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertValid(fs);
        validFrameSamples.add(fs);

        assertNotValid(validFrameSamples);
    }

    @Test
    public void testManyValidSingleInvalidFrameSamples() {
        List<FrameSample> frameSamples = new ArrayList<>();

        FrameSample fs;

        // valid
        fs = new FrameSample();
        fs.setErtStr("2010-001T00:00:00.000000");
        fs.setPathId(1);
        fs.setTkSclkCoarse(0);
        fs.setTkSclkFine(0);
        fs.setTkDataRateBps(BigDecimal.valueOf(1));
        fs.setDerivedTdBe(1.0);
        frameSamples.add(fs);

        // invalid
        fs = new FrameSample();
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(123);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        assertNotValid(fs);
        frameSamples.add(fs);

        // valid
        fs = new FrameSample();
        fs.setErtStr("2010-001T00:00:00.000000");
        fs.setPathId(2);
        fs.setTkSclkCoarse(1234);
        fs.setTkSclkFine(123);
        fs.setTkDataRateBps(BigDecimal.valueOf(1024));
        fs.setDerivedTdBe(2.365);
        frameSamples.add(fs);

        assertNotValid(frameSamples);
    }

    private void assertValid(FrameSample fs) throws MmtcException {
        assertValid(Arrays.asList(fs));
    }

    private void assertValid(List<FrameSample> fsl) throws MmtcException {
        FrameSampleValidator.validate(fsl, TK_SCLK_MODULUS);
    }

    private void assertNotValid(FrameSample fs) {
        assertNotValid(Arrays.asList(fs));
    }

    private void assertNotValid(List<FrameSample> fsl) {
        assertThrows(MmtcException.class, () -> {
            FrameSampleValidator.validate(fsl, TK_SCLK_MODULUS);
        });
    }
}