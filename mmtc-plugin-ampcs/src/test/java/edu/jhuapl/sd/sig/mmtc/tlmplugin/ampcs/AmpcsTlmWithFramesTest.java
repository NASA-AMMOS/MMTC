package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.filter.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AmpcsTlmWithFramesTest {
    @Test
    void testUseUnallowableFilters() throws Exception {
        final Map<String, TimeCorrelationFilter> disallowedFilters = new HashMap<>();
        disallowedFilters.put(TimeCorrelationCliAppConfig.ERT_FILTER, new ErtFilter());
        disallowedFilters.put(TimeCorrelationCliAppConfig.SCLK_FILTER, new SclkFilter());
        disallowedFilters.put(TimeCorrelationCliAppConfig.VCID_FILTER, new VcidFilter());
        disallowedFilters.put(TimeCorrelationCliAppConfig.CONSEC_FRAMES_FILTER, new ConsecutiveFrameFilter());
        disallowedFilters.put(TimeCorrelationCliAppConfig.CONSEC_MC_FRAME_FILTER, new ConsecutiveMasterChannelFrameFilter());

        for (Map.Entry<String, TimeCorrelationFilter> disallowedFilter : disallowedFilters.entrySet()) {
            final String disallowedFilterName = disallowedFilter.getKey();
            final TimeCorrelationFilter filter = disallowedFilter.getValue();

            TimeCorrelationCliAppConfig mockedConfig = mock(TimeCorrelationCliAppConfig.class);
            Map<String, TimeCorrelationFilter> enabledFilters = new HashMap<>();
            enabledFilters.put(disallowedFilterName, filter);
            when(mockedConfig.getFilters()).thenReturn(enabledFilters);
            // needed for AmpcsTelemetrySourceConfig to instantiate
            when(mockedConfig.containsNonEmptyKey("telemetry.source.plugin.ampcs.oscillator.activeOscillatorSelectionMode")).thenReturn(true);
            when(mockedConfig.getString("telemetry.source.plugin.ampcs.oscillator.activeOscillatorSelectionMode")).thenReturn("by_vcid");

            AmpcsTlmWithFrames tlmArchive = new AmpcsTlmWithFrames();

            assertThrows(
                    MmtcException.class,
                    () -> tlmArchive.applyConfiguration(mockedConfig),
                    "When using the AmpcsTlmArchive telemetry source, the " +
                            disallowedFilterName +
                            " filter is not applicable and must be disabled by setting the configuration option " +
                            "filter.<filter name>.enabled to false."
            );
        }
    }
}