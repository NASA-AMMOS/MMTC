package edu.jhuapl.sd.sig.mmtc.filter;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidFilterTest {
    private final ValidFilter validFilter = new ValidFilter();
    private final TimeCorrelationRunConfig config = Mockito.mock(TimeCorrelationRunConfig.class);

    @Test
    public void testPassingCases() throws MmtcException {
        testFilterPasses(
                config,
                Arrays.asList(validFrameSample()),
                "single valid frame"
        );

        testFilterPasses(
                config,
                Arrays.asList(
                        validFrameSample(),
                        validFrameSample(),
                        validFrameSample()
                ),
                "three valid frames"
        );
    }

    @Test
    public void testFailureCases() throws MmtcException {
        testFilterFails(
                config,
                Collections.emptyList(),
                "empty sample set"
        );

        testFilterFails(
                config,
                Arrays.asList(invalidFrameSample()),
                "single invalid frame"
        );

        testFilterFails(
                config,
                Arrays.asList(
                        validFrameSample(),
                        invalidFrameSample(),
                        validFrameSample()
                ),
                "single invalid frame within valid frames"
        );
    }

    private void testFilterPasses(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertTrue(validFilter.process(frameSamples, config), message);
    }

    private void testFilterFails(TimeCorrelationRunConfig config, List<FrameSample> frameSamples, String message) throws MmtcException {
        assertFalse(validFilter.process(frameSamples, config), message);
    }

    private static FrameSample frameSampleWithUnsetValidField() {
        return new FrameSample();
    }

    private static FrameSample validFrameSample() {
        final FrameSample fs = new FrameSample();
        fs.setTkValid(true);
        return fs;
    }

    private static FrameSample invalidFrameSample() {
        final FrameSample fs = new FrameSample();
        fs.setTkValid(false);
        return fs;
    }
}