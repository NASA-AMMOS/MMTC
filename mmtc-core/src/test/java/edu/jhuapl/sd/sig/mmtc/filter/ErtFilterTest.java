package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.CdsTimeCode;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ErtFilterTest {

    private final ErtFilter ertFilter = new ErtFilter();

    @Test
    public void testErtFilter() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/ErtFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(10),
                            frameSampleWithDeltaEpochErt(20),
                            frameSampleWithDeltaEpochErt(30),
                            frameSampleWithDeltaEpochErt(40),
                            frameSampleWithDeltaEpochErt(50)
                    )),
                    "five frames with short evenly-spaced ERT deltas"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(1 * 12000),
                            frameSampleWithDeltaEpochErt(2 * 12000),
                            frameSampleWithDeltaEpochErt(3 * 12000),
                            frameSampleWithDeltaEpochErt(4 * 12000),
                            frameSampleWithDeltaEpochErt(5 * 12000)
                    )),
                    "five frames with long evenly-spaced ERT deltas"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(10),
                            frameSampleWithDeltaEpochErt(20),
                            frameSampleWithDeltaEpochErt(30 + 121),
                            frameSampleWithDeltaEpochErt(40),
                            frameSampleWithDeltaEpochErt(50)
                    )),
                    "five frames with short unevenly-spaced ERT delta"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(10),
                            frameSampleWithDeltaEpochErt(20),
                            frameSampleWithDeltaEpochErt(30 - 121),
                            frameSampleWithDeltaEpochErt(40),
                            frameSampleWithDeltaEpochErt(50)
                    )),
                    "five frames with short unevenly-spaced ERT delta"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(1 * 12000),
                            frameSampleWithDeltaEpochErt(2 * 12000),
                            frameSampleWithDeltaEpochErt(3 * 12000 + 121),
                            frameSampleWithDeltaEpochErt(4 * 12000),
                            frameSampleWithDeltaEpochErt(5 * 12000)
                    )),
                    "five frames with long unevenly-spaced ERT delta"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(1 * 12000),
                            frameSampleWithDeltaEpochErt(2 * 12000),
                            frameSampleWithDeltaEpochErt(3 * 12000 - 121),
                            frameSampleWithDeltaEpochErt(4 * 12000),
                            frameSampleWithDeltaEpochErt(5 * 12000)
                    )),
                    "five frames with long unevenly-spaced ERT delta"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(0),
                            frameSampleWithDeltaEpochErt(1200),
                            frameSampleWithDeltaEpochErt(2400),
                            frameSampleWithDeltaEpochErt(3600),
                            frameSampleWithDeltaEpochErt(4800)
                    )),
                    "five frames with long evenly-spaced ERT deltas"
            );


            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(0),
                            frameSampleWithDeltaEpochErt(1200),
                            frameSampleWithDeltaEpochErt(2400+120+1)
                    )),
                    "five frames with long unevenly-spaced ERT deltas"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithDeltaEpochErt(0),
                            frameSampleWithDeltaEpochErt(1200),
                            frameSampleWithDeltaEpochErt(2400-120-1)
                    )),
                    "five frames with long unevenly-spaced ERT deltas"
            );
        }
    }

    private void testFilterPasses(TimeCorrelationAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(ertFilter.process(frameSamples, config), message);
    }

    private void testFilterFails(TimeCorrelationAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(ertFilter.process(frameSamples, config), message);
    }

    private static FrameSample frameSampleWithDeltaEpochErt(int deltaErtSec) {
        final FrameSample fs = new FrameSample();
        fs.setErt(new CdsTimeCode(1, deltaErtSec * 1000, 0));
        return fs;
    }
}