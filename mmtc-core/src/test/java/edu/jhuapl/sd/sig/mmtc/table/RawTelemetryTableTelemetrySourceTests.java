package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.filter.ValidFilter;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class RawTelemetryTableTelemetrySourceTests {
    private TimeCorrelationAppConfig config;
    private RawTelemetryTableTelemetrySource tableTlmSource;

    void loadConfigAndTlmSource(String[] args, String rawTlmTablePath) throws Exception {
        config = spy(new TimeCorrelationAppConfig(args));
        when(config.getString("telemetry.source.plugin.rawTlmTable.tableFile.uri")).thenReturn(rawTlmTablePath);

        try {
            tableTlmSource = new RawTelemetryTableTelemetrySource();
            tableTlmSource.applyConfiguration(config);
        } catch (Exception e) {
            fail("Failed to configure RawTelemetryTable", e);
        }
    }

    void loadSpice() {
        try {
            TimeConvert.loadSpiceLib();
            TimeConvert.loadSpiceKernels(config.getKernelsToLoad());
        }
        catch (TimeConvertException | MmtcException ex) {
            fail("Unable to load SPICE files. " + ex);
        }
    }

    @Test
    void testGetSamplesEmpty() throws Exception {
        loadConfigAndTlmSource(
                new String[] {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_empty.csv"
        );

        try {
            List<FrameSample> samples = tableTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
            assertEquals(0, samples.size());
        }
        catch (MmtcException ex) {
            fail("Error getting samples in range." + ex);
        }
    }

    @Test
    void testGetSamplesOneInRange() throws Exception {
        loadConfigAndTlmSource(
                new String[]{"2006-01-20T01:00:00.000Z", "2006-01-20T01:30:00.000Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_one.csv"
        );
        loadSpice();

        try {
            List<FrameSample> samples = tableTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
            assertEquals(1, samples.size());
            TimeConvert.unloadSpiceKernels();
        }
        catch (MmtcException ex) {
            fail("Error getting samples in range. " + ex);
        }
    }

    @Test
    void testGetSamplesOneOutOfRange() throws Exception {
        loadConfigAndTlmSource(
                new String[] {"2006-01-20T02:00:00.000Z", "2006-01-20T03:00:00.000Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_one.csv"
        );
        loadSpice();

        try {
            List<FrameSample> samples = tableTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
            assertEquals(0, samples.size());
            TimeConvert.unloadSpiceKernels();
        }
        catch (MmtcException ex) {
            fail("Error getting samples in range. " + ex);
        }
    }

    @Test
    void testGetSamplesSomeInRange() throws Exception {
        loadConfigAndTlmSource(
                new String[] {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_some.csv"
        );
        loadSpice();

        try {
            List<FrameSample> samples = tableTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
            assertTrue(samples.size() >= config.getSamplesPerSet());
            TimeConvert.unloadSpiceKernels();
        }
        catch (MmtcException ex) {
            fail("Error getting samples in range. " + ex);
        }
    }

    @Test
    void testUseUnallowableFilter() throws Exception {
        TimeCorrelationAppConfig mockedConfig = mock(TimeCorrelationAppConfig.class);
        Map<String, TimeCorrelationFilter> enabledFilters = new HashMap<>();
        enabledFilters.put(TimeCorrelationAppConfig.VALID_FILTER, new ValidFilter());
        when(mockedConfig.getFilters()).thenReturn(enabledFilters);
        when(mockedConfig.getString("telemetry.source.plugin.rawTlmTable.tableFile.uri")).thenReturn("src/test/resources/tables/RawTelemetryTable_empty.csv");

        RawTelemetryTableTelemetrySource tlmArchive = new RawTelemetryTableTelemetrySource();

        assertThrows(
                MmtcException.class,
                () -> tlmArchive.applyConfiguration(mockedConfig),
                "When using the RawTelemetryTable telemetry source, the " +
                        TimeCorrelationAppConfig.VALID_FILTER +
                        " filter is not applicable and must be disabled by setting the configuration option " +
                        "filter.<filter name>.enabled to false."
        );
    }
}
