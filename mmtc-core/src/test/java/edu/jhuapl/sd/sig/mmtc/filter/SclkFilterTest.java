package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SclkFilterTest {

    private final SclkFilter sclkFilter = new SclkFilter();

    @Test
    public void testFramesWithOnlyTkSclk() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/SclkFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(10),
                            frameSampleWithTkSclk(20),
                            frameSampleWithTkSclk(30),
                            frameSampleWithTkSclk(40),
                            frameSampleWithTkSclk(50)
                    )),
                    "five frames with short evenly-spaced TK SCLK deltas"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(1 * 12000),
                            frameSampleWithTkSclk(2 * 12000),
                            frameSampleWithTkSclk(3 * 12000),
                            frameSampleWithTkSclk(4 * 12000),
                            frameSampleWithTkSclk(5 * 12000)
                    )),
                    "five frames with long evenly-spaced TK SCLK deltas"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(10),
                            frameSampleWithTkSclk(20),
                            frameSampleWithTkSclk(30 + 121),
                            frameSampleWithTkSclk(40),
                            frameSampleWithTkSclk(50)
                    )),
                    "five frames with short unevenly-spaced TK SCLK delta"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(1 * 12000),
                            frameSampleWithTkSclk(2 * 12000),
                            frameSampleWithTkSclk(3 * 12000 + 121),
                            frameSampleWithTkSclk(4 * 12000),
                            frameSampleWithTkSclk(5 * 12000)
                    )),
                    "five frames with long unevenly-spaced TK SCLK delta"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithTkSclk(1 * 12000),
                            frameSampleWithTkSclk(2 * 12000),
                            frameSampleWithTkSclk(3 * 12000 - 121),
                            frameSampleWithTkSclk(4 * 12000),
                            frameSampleWithTkSclk(5 * 12000)
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
                    .thenReturn("src/test/resources/FilterTests/SclkFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

            // passes even when no TK SCLK is provided
            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclk(10),
                            frameSampleWithSclk(20),
                            frameSampleWithSclk(30),
                            frameSampleWithSclk(40),
                            frameSampleWithSclk(50)
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
                    .thenReturn("src/test/resources/FilterTests/SclkFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclkPair(10, 10),
                            frameSampleWithSclkPair(20, 20),
                            frameSampleWithSclkPair(30, 30),
                            frameSampleWithSclkPair(40, 40),
                            frameSampleWithSclkPair(50, 50)
                    )),
                    "five frames with short SCLK deltas and short TK SCLK deltas"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclkPair(10, 10),
                            frameSampleWithSclkPair(20, 20),
                            frameSampleWithSclkPair(10000, 30),
                            frameSampleWithSclkPair(10010, 40),
                            frameSampleWithSclkPair(10020, 50)
                    )),
                    "five frames with long SCLK deltas and short TK SCLK deltas"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithSclkPair(5001, 10),
                            frameSampleWithSclkPair(5002, 20),
                            frameSampleWithSclkPair(5003, 30 + 8000),
                            frameSampleWithSclkPair(5004, 40),
                            frameSampleWithSclkPair(5005, 50)
                    )),
                    "five frames with short SCLK deltas and long TK SCLK deltas"
            );
        }
    }

    private void testFilterPasses(TimeCorrelationAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(sclkFilter.process(frameSamples, config), message);
    }

    private void testFilterFails(TimeCorrelationAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(sclkFilter.process(frameSamples, config), message);
    }

    private static FrameSample frameSampleWithTkSclk(int tkSclkCoarse) {
        final FrameSample fs = new FrameSample();
        fs.setTkSclkCoarse(tkSclkCoarse);
        return fs;
    }

    private static FrameSample frameSampleWithSclk(int sclkCoarse) {
        final FrameSample fs = new FrameSample();
        fs.setSclkCoarse(sclkCoarse);
        return fs;
    }
    private static FrameSample frameSampleWithSclkPair(int sclkCoarse, int tkSclkCoarse) {
        final FrameSample fs = new FrameSample();
        fs.setSclkCoarse(sclkCoarse);
        fs.setTkSclkCoarse(tkSclkCoarse);
        return fs;
    }
}