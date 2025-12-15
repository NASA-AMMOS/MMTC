package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliInputConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataRateFilterTest {

    private final MaxDataRateFilter maxDataRateFilter = new MaxDataRateFilter();
    private final MinDataRateFilter minDataRateFilter = new MinDataRateFilter();

    @Test
    public void testFramesWithExcessiveDownlinkBps() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/DataRateFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            List<FrameSample> samples = Arrays.asList(
                    frameSampleWithDataRateBps(5000),
                    frameSampleWithDataRateBps(5000),
                    frameSampleWithDataRateBps(1200000)
            );
            testFilterMaxFails(
                    config,
                    samples,
                    "three frames with one having downlinkBps above threshold (> 1 000 000)"
            );

            testFilterMinPasses(
                    config,
                    samples,
                    "three frames with one having downlinkBps above threshold (> 1 000 000)"
            );
        }
    }

    @Test
    public void testFramesWithInsufficientDownlinkBps() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/DataRateFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            List<FrameSample> samples = Arrays.asList(
                    frameSampleWithDataRateBps(200),
                    frameSampleWithDataRateBps(80),
                    frameSampleWithDataRateBps(5000)
            );

            testFilterMinFails(
                    config,
                    samples,
                    "three frames with one having downlinkBps below threshold (< 100)"
            );

            testFilterMaxPasses(
                    config,
                    samples,
                    "three frames with one having downlinkBps below threshold (< 100)"
            );
        }
    }

    @Test
    public void testFramesWithAcceptableDownlinkBps() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/DataRateFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            List<FrameSample> samples = Arrays.asList(
                    frameSampleWithDataRateBps(100),
                    frameSampleWithDataRateBps(1000),
                    frameSampleWithDataRateBps(10000),
                    frameSampleWithDataRateBps(100000),
                    frameSampleWithDataRateBps(1000000)
            );
            testFilterMaxPasses(
                    config,
                    samples,
                    "five frames from the minDataRate threshold (>= 100) to the maxDataRate threshold (<= 1000000) evaluated by max"
            );

            testFilterMinPasses(
                    config,
                    samples,
                    "five frames from the minDataRate threshold (>= 100) to the maxDataRate threshold (<= 1000000) evaluated by min"
            );
        }
    }

    private void testFilterMaxPasses(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(maxDataRateFilter.process(frameSamples, config), message);
    }

    private void testFilterMinPasses(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(minDataRateFilter.process(frameSamples, config), message);
    }

    private void testFilterMaxFails(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(maxDataRateFilter.process(frameSamples, config), message);
    }

    private void testFilterMinFails(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(minDataRateFilter.process(frameSamples, config), message);
    }

    private static FrameSample frameSampleWithDataRateBps(int dataRateBps) {
        final FrameSample fs = new FrameSample();
        fs.setTkDataRateBps(BigDecimal.valueOf(dataRateBps));
        return fs;
    }
}