package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VcidFilterTest {

    private final VcidFilter vcidFilter = new VcidFilter();

    @Test
    public void testFramesWithVcidAndTkVcid() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/VcidFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcidPair(5, 5),
                            frameSampleWithVcidPair(5, 5),
                            frameSampleWithVcidPair(5, 5),
                            frameSampleWithVcidPair(5, 5),
                            frameSampleWithVcidPair(5, 5)
                    )),
                    "five frames with the same VCIDs from a group of size one"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcidPair(0, 0),
                            frameSampleWithVcidPair(0, 0),
                            frameSampleWithVcidPair(0, 0),
                            frameSampleWithVcidPair(0, 0),
                            frameSampleWithVcidPair(0, 0)
                    )),
                    "five frames with the same VCIDs from a group of size three"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcidPair(0, 6),
                            frameSampleWithVcidPair(6, 7),
                            frameSampleWithVcidPair(7, 7),
                            frameSampleWithVcidPair(7, 0),
                            frameSampleWithVcidPair(0, 6)
                    )),
                    "five frames with various VCIDs from a group of size three"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcidPair(0, 5),
                            frameSampleWithVcidPair(5, 6),
                            frameSampleWithVcidPair(6, 7),
                            frameSampleWithVcidPair(7, 0),
                            frameSampleWithVcidPair(0, 5)
                    )),
                    "five frames with various VCIDs from various groups"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcidPair(0, 0),
                            frameSampleWithVcidPair(0, 0),
                            frameSampleWithVcidPair(0, 5),
                            frameSampleWithVcidPair(5, 0),
                            frameSampleWithVcidPair(0, 0)
                    )),
                    "five frames with various VCIDs from various groups"
            );
        }
    }

    @Test
    public void testFramesWithOnlyVcid() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/FilterTests/VcidFilterTest");

            String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
            TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcid(5),
                            frameSampleWithVcid(5),
                            frameSampleWithVcid(5),
                            frameSampleWithVcid(5),
                            frameSampleWithVcid(5)
                    )),
                    "five frames with the same VCIDs from a group of size one"
            );

            testFilterPasses(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcid(0),
                            frameSampleWithVcid(6),
                            frameSampleWithVcid(7),
                            frameSampleWithVcid(0),
                            frameSampleWithVcid(6)
                    )),
                    "five frames with various VCIDs from a group of size three"
            );

            testFilterFails(
                    config,
                    Collections.unmodifiableList(Arrays.asList(
                            frameSampleWithVcid(0),
                            frameSampleWithVcid(5),
                            frameSampleWithVcid(6),
                            frameSampleWithVcid(7),
                            frameSampleWithVcid(0)
                    )),
                    "five frames with various VCIDs from two different groups"
            );
        }
    }

    private void testFilterPasses(TimeCorrelationCliAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(vcidFilter.process(frameSamples, config), message);
    }

    private void testFilterFails(TimeCorrelationCliAppConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(vcidFilter.process(frameSamples, config), message);
    }

    private static FrameSample frameSampleWithTkVcid(int tkVcid) {
        final FrameSample fs = new FrameSample();
        fs.setTkVcid(tkVcid);
        return fs;
    }

    private static FrameSample frameSampleWithVcid(int vcid) {
        final FrameSample fs = new FrameSample();
        fs.setVcid(vcid);
        return fs;
    }
    private static FrameSample frameSampleWithVcidPair(int vcid, int tkVcid) {
        final FrameSample fs = new FrameSample();
        fs.setVcid(vcid);
        fs.setTkVcid(tkVcid);
        return fs;
    }
}