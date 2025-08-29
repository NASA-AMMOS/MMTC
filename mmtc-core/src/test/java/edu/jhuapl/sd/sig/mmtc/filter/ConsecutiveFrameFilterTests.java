package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Environment;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConsecutiveFrameFilterTests {
    private final String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
    private int vcfcMax = 16777215;

    // Organized as {{sample VCFCs}, {supplemental VCFCs}}
    private final int[][] SEQ_VCFCs = new int[][]{
        {0, 1, 2, 3, 4, 5},
        {1, 2, 3, 4, 5, 6}
    };

    private final int[][] RESTARTING_VCFCs = new int[][]{
        {0, 1, 2, 0, 1, 2},
        {1, 2, 3, 1, 2, 3}
    };
    private final int[][] JUMPING_VCFCs = new int[][]{
        {0, 1, 2, 4, 5, 6},
        {1, 2, 3, 5, 6, 7}
    };
    private final int[][] REPEATED_VCFCs = new int[][]{
        {0, 1, 2, 2, 3, 4},
        {1, 2, 3, 3, 4, 5}
    };

    private final int[][] TWO_SEQ_VCFCs = new int[][]{
        {0, 1},
        {1, 2}
    };
    private final int[][] TWO_JUMPING_VCFCs = new int[][]{
        {2, 4},
        {3, 5}
    };
    private final int[][] TWO_REPEATED_VCFCs = new int[][]{
        {2,2},
        {3,3}
    };

    private final int[][] BASIC_ROLLOVER = new int[][]{
        {vcfcMax-2, vcfcMax-1, vcfcMax, 0},
        {vcfcMax-1, vcfcMax, 0, 1}
    };

    private final int[][] IMMEDIATE_ROLLOVER = new int[][]{
        {vcfcMax-1, vcfcMax, 0},
        {vcfcMax, 0, 1}
    };

    private final int[][]  END_RIGHT_BEFORE_ROLLOVER = new int[][]{
        {vcfcMax-3, vcfcMax-2, vcfcMax-1},
        {vcfcMax-2, vcfcMax-1, vcfcMax}
    };

    private final int[][] NONSEQ_ROLLOVER_1 = new int[][]{
        {vcfcMax-1, vcfcMax, 0, vcfcMax},
        {vcfcMax, 0, 1, 0}
    };

    private final int[][] NONSEQ_ROLLOVER_2 = new int[][]{
        {vcfcMax-1, vcfcMax, 0, 0},
        {vcfcMax, 0, 1, 1}
    };

    private final int[][] NONSEQ_ROLLOVER_3 = new int[][]{
        {vcfcMax-1, 0, 1},
        {vcfcMax, 1, 2}
    };

    private final int[][] MISMATCHED_SUPPVCFCS = new int[][]{
        {0,1,2,3,4},
        {1,2,3,5,6}
    };

    private final ConsecutiveFrameFilter ConsecutiveFrameFilter = new ConsecutiveFrameFilter();

    @Test
    public void testSequentialVcfcs() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveFrameFilterTest");

            TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);


            testFilterPasses(config, getFrameSamples(SEQ_VCFCs), "six frames with sequential VCFCs");

            testFilterPasses(config, getFrameSamples(TWO_SEQ_VCFCs), "two frames with sequential VCFCs");
        }
    }

    @Test
    public void testNonSequentialVcfcs() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveFrameFilterTest");

            TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

            testFilterPasses(config, getFrameSamples(RESTARTING_VCFCs), "six frames with restarting VCFCs");

            testFilterPasses(config, getFrameSamples(JUMPING_VCFCs), "six frames with jumping VCFCs");

            testFilterPasses(config, getFrameSamples(REPEATED_VCFCs), "six frames with repeated VCFCs");

            testFilterPasses(config, getFrameSamples(TWO_JUMPING_VCFCs), "two frames with jumping VCFCs");

            testFilterPasses(config, getFrameSamples(TWO_REPEATED_VCFCs), "two frames with repeated VCFCs");

            testFilterFails(config, getFrameSamples(MISMATCHED_SUPPVCFCS), "five frames with suppVCFCs that don't have uniform offsets");

            testFilterFails(config, Collections.EMPTY_LIST, "empty sample set");
        }
    }

    @Test
    public void testRolloverCases() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ConsecutiveFrameFilterTest");

            TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

            testFilterPasses(config, getFrameSamples(BASIC_ROLLOVER), "basic rollover");

            testFilterPasses(config, getFrameSamples(IMMEDIATE_ROLLOVER), "immediate rollover");

            testFilterPasses(config, getFrameSamples(END_RIGHT_BEFORE_ROLLOVER), "end right before rollover");

            testFilterPasses(config, getFrameSamples(NONSEQ_ROLLOVER_1), "non-sequential rollover 1");

            testFilterPasses(config, getFrameSamples(NONSEQ_ROLLOVER_2), "non-sequential rollover 2");

            testFilterPasses(config, getFrameSamples(NONSEQ_ROLLOVER_3), "non-sequential rollover 3");
        }
    }

    private void testFilterPasses(TimeCorrelationCliAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(ConsecutiveFrameFilter.process(frameSamples, config), message);
    }

    private void testFilterFails(TimeCorrelationCliAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(ConsecutiveFrameFilter.process(frameSamples, config), message);
    }

    private List<FrameSample> getFrameSamples(int[][] vcfcs) {
        List<FrameSample> samples = new ArrayList<>();

        for (int i = 0; i < vcfcs[0].length; i++) {
            FrameSample fs = new FrameSample();
            fs.setVcfc(vcfcs[0][i]);

            // Add SuppVCIDs
            fs.setSuppVcid(fs.getVcid());

            // Check 5a: VCFC must match SuppTkVCFC
            fs.setTkVcfc(fs.getVcfc());

            // the last frame in a realistic samples set won't have a supplemental frame (and thus supplemental Vcfc)
            if (i != vcfcs[1].length - 1) {
                fs.setSuppVcfc(vcfcs[1][i]);
            }

            samples.add(fs);
        }

        return samples;
    }

}
