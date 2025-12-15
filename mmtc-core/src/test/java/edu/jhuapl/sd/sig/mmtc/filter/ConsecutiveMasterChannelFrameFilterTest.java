package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliInputConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConsecutiveMasterChannelFrameFilterTest {
    private final int[] SEQ_MCFCS = new int[]{0, 1, 2, 3, 4, 5};

    private final int[] RESTARTING_MCFCS = new int[]{0, 1, 2, 0, 1, 2};
    private final int[] JUMPING_MCFCS = new int[]{0, 1, 2, 4, 5, 6};
    private final int[] REPEATED_MCFCS = new int[]{0, 1, 2, 2, 3, 4};

    private final int[] TWO_SEQ_MCFCS = new int[]{0, 1};
    private final int[] TWO_JUMPING_MCFCS = new int[]{2, 4};
    private final int[] TWO_REPEATED_MCFCS = new int[]{2, 2};

    private final int[] BASIC_ROLLOVER = new int[]{254, 255, 0, 1};

    private final int[] IMMEDIATE_ROLLOVER = new int[]{255, 0, 1};

    private final int[] END_RIGHT_BEFORE_ROLLOVER = new int[]{253, 254, 255};

    private final int[] NONSEQ_ROLLOVER_1 = new int[]{254, 255, 0, 255};

    private final int[] NONSEQ_ROLLOVER_2 = new int[]{254, 255, 0, 0};

    private final int[] NONSEQ_ROLLOVER_3 = new int[]{254, 0, 1};

    private final ConsecutiveMasterChannelFrameFilter mcfcFilter = new ConsecutiveMasterChannelFrameFilter();

    @Test
    public void testSequentialMcfcs() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveMasterChannelFrameFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));


            testFilterPasses(config, getFrameSamples(SEQ_MCFCS), "six frames with sequential MCFCs");

            testFilterPasses(config, getFrameSamples(TWO_SEQ_MCFCS), "two frames with sequential MCFCs");
        }
    }

    @Test
    public void testNonSequentialMcfcs() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveMasterChannelFrameFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            testFilterFails(config, getFrameSamples(RESTARTING_MCFCS), "six frames with restarting MCFCs");

            testFilterFails(config, getFrameSamples(JUMPING_MCFCS), "six frames with jumping MCFCs");

            testFilterFails(config, getFrameSamples(REPEATED_MCFCS), "six frames with repeated MCFCs");

            testFilterFails(config, getFrameSamples(TWO_JUMPING_MCFCS), "two frames with jumping MCFCs");

            testFilterFails(config, getFrameSamples(TWO_REPEATED_MCFCS), "two frames with repeated MCFCs");

            testFilterFails(config, Collections.EMPTY_LIST, "empty sample set");
        }
    }

    @Test
    public void testRolloverCases() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveMasterChannelFrameFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            testFilterPasses(config, getFrameSamples(BASIC_ROLLOVER), "basic rollover");

            testFilterPasses(config, getFrameSamples(IMMEDIATE_ROLLOVER), "immediate rollover");

            testFilterPasses(config, getFrameSamples(END_RIGHT_BEFORE_ROLLOVER), "end right before rollover");

            testFilterFails(config, getFrameSamples(NONSEQ_ROLLOVER_1), "non-sequential rollover 1");

            testFilterFails(config, getFrameSamples(NONSEQ_ROLLOVER_2), "non-sequential rollover 2");

            testFilterFails(config, getFrameSamples(NONSEQ_ROLLOVER_3), "non-sequential rollover 3");
        }
    }

    @Test
    public void testWithLargerSupplementalSampleOffset() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveMasterChannelFrameFilterTestWithLargerSupplementalSampleOffset");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            testFilterPasses(config, getFrameSamples(new int[]{0, 3, 6, 9, 12}), "basic sequence");

            testFilterFails(config, getFrameSamples(new int[]{0, 2, 6, 9, 12}), "basic sequence");

            testFilterPasses(config, getFrameSamples(new int[]{255, 2, 5, 8}), "immediate rollover 1");

            testFilterPasses(config, getFrameSamples(new int[]{254, 1, 4, 7}), "immediate rollover 2");

            testFilterFails(config, getFrameSamples(new int[]{255, 0, 3, 6, 9}), "non-sequential rollover 1");
        }
    }

    @Test
    public void testWithUnpopulatedFrameSamples() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveMasterChannelFrameFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            testFilterPasses(config, Arrays.asList(new FrameSample(), new FrameSample()), "two empty frames");
        }
    }

    @Test
    public void testWithZeroMaxMcfc() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveMasterChannelFrameFilterTestWithZeroMaxMcfc");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig(cliArgs));

            assertThrows(MmtcException.class, () -> {
                mcfcFilter.process(getFrameSamples(new int[]{0, 3, 6, 9, 12}), config);
            });
        }
    }

    private void testFilterPasses(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(mcfcFilter.process(frameSamples, config), message);
    }

    private void testFilterFails(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(mcfcFilter.process(frameSamples, config), message);
    }



    private List<FrameSample> getFrameSamples(int[] mcfcs) {
        List<FrameSample> samples = new ArrayList<>();

        for (int i = 0; i < mcfcs.length; i++) {
            FrameSample fs = new FrameSample();
            fs.setMcfc(mcfcs[i]);

            // the last frame in a realistic samples set won't have a supplemental frame (and thus supplemental MCFC)
            if (i != mcfcs.length - 1) {
                fs.setSuppMcfc(mcfcs[i + 1]);
            }

            samples.add(fs);
        }

        return samples;
    }
}