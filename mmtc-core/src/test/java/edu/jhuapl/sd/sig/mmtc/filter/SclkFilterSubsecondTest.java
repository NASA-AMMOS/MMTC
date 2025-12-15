package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliInputConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SclkFilterSubsecondTest {
    // An alternate version of SclkFilterTest designed to test the filter with much smaller values and sclkMaxDeltaVarianceSec threshold
    private final SclkFilter sclkFilter = new SclkFilter();

    @Test
    public void testFramesWithOnlyTkSclk() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/SclkFilterSubsecondTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(1, 100),
                            frameSampleWithTkSclk(1, 200),
                            frameSampleWithTkSclk(1, 300),
                            frameSampleWithTkSclk(1, 400),
                            frameSampleWithTkSclk(1, 500)
                    )),
                    "five frames with short evenly-spaced TK SCLK deltas"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(1, 1 * 12000),
                            frameSampleWithTkSclk(2, 2 * 12000),
                            frameSampleWithTkSclk(3, 3 * 12000),
                            frameSampleWithTkSclk(4, 4 * 12000),
                            frameSampleWithTkSclk(5, 5 * 12000)
                    )),
                    "five frames with long evenly-spaced TK SCLK deltas"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(1, 1000),
                            frameSampleWithTkSclk(1, 2000),
                            frameSampleWithTkSclk(1, 3000 + 121),
                            frameSampleWithTkSclk(1, 4000),
                            frameSampleWithTkSclk(1, 5000)
                    )),
                    "five frames with short unevenly-spaced TK SCLK delta"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(1000, 1 * 9000),
                            frameSampleWithTkSclk(1000, 2 * 9000),
                            frameSampleWithTkSclk(1000, 3 * 9000 + 121),
                            frameSampleWithTkSclk(1000, 4 * 9000),
                            frameSampleWithTkSclk(1000, 5 * 9000)
                    )),
                    "five frames with long unevenly-spaced TK SCLK delta"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(15000, 1 * 12000),
                            frameSampleWithTkSclk(15000, 2 * 12000),
                            frameSampleWithTkSclk(15000, 3 * 12000 - 121),
                            frameSampleWithTkSclk(15000, 4 * 12000),
                            frameSampleWithTkSclk(15000, 5 * 12000)
                    )),
                    "five frames with long unevenly-spaced TK SCLK delta"
            );
        }
    }

    @Test
    public void testFramesWithOnlySclk() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/SclkFilterSubsecondTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            // passes even when no TK SCLK is provided
            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclk(1, 100),
                            frameSampleWithSclk(1, 200),
                            frameSampleWithSclk(1, 300),
                            frameSampleWithSclk(1, 400),
                            frameSampleWithSclk(1, 500)
                    )),
                    "five frames with only short SCLK deltas"
            );
        }
    }

    @Test
    public void testFramesWithBothSclkAndTkSclk() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/SclkFilterSubsecondTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclkPair(10, 10, 10, 10),
                            frameSampleWithSclkPair(20, 20, 20, 20),
                            frameSampleWithSclkPair(30, 30, 30, 30),
                            frameSampleWithSclkPair(40, 40, 40, 40),
                            frameSampleWithSclkPair(50, 50, 50, 50)
                    )),
                    "five frames with short SCLK deltas and short TK SCLK deltas"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclkPair(10, 10, 10,  10),
                            frameSampleWithSclkPair(20, 20, 20, 20),
                            frameSampleWithSclkPair(30, 10000, 30, 30),
                            frameSampleWithSclkPair(40, 10010, 40, 40),
                            frameSampleWithSclkPair(50, 10020, 50, 50)
                    )),
                    "five frames with long SCLK deltas and short TK SCLK deltas"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclkPair(10, 5001, 10, 10),
                            frameSampleWithSclkPair(10, 5002, 10, 20),
                            frameSampleWithSclkPair(10, 5003, 10, 30 + 8000),
                            frameSampleWithSclkPair(10, 5004, 10, 40),
                            frameSampleWithSclkPair(10, 5005, 10, 50)
                    )),
                    "five frames with short SCLK deltas and long TK SCLK deltas"
            );
        }
    }

    private void testFilterPasses(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(sclkFilter.process(frameSamples, config), message);
    }

    private void testFilterFails(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(sclkFilter.process(frameSamples, config), message);
    }

    private static FrameSample frameSampleWithTkSclk(int tkSclkCoarse, int tkSclkFine) {
        final FrameSample fs = new FrameSample();
        fs.setTkSclkCoarse(tkSclkCoarse);
        fs.setTkSclkFine(tkSclkFine);
        return fs;
    }

    private static FrameSample frameSampleWithSclk(int sclkCoarse, int sclkFine) {
        final FrameSample fs = new FrameSample();
        fs.setSclkCoarse(sclkCoarse);
        fs.setSclkFine(sclkFine);
        return fs;
    }
    private static FrameSample frameSampleWithSclkPair(int sclkCoarse, int sclkFine, int tkSclkCoarse, int tkSclkFine) {
        final FrameSample fs = new FrameSample();
        fs.setSclkCoarse(sclkCoarse);
        fs.setSclkFine(sclkFine);

        fs.setTkSclkCoarse(tkSclkCoarse);
        fs.setTkSclkFine(tkSclkFine);
        return fs;
    }
}