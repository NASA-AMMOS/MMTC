package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.TestHelper;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.RawTelemetryTableTelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public abstract class BaseTelemetrySelectionStrategyTest {
    protected static final String RAW_TLM_TBL_NH_REFORMATTED = "src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv";
    protected static final String RAW_TLM_TBL_NH_EMPTY = "src/test/resources/tables/RawTelemetryTable_NH_empty.csv";

    @BeforeAll
    static void setup() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();

        List<String> kernelsToLoad = Arrays.asList(
                "src/test/resources/nh_kernels/lsk/naif0012.tls",
                "src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc"
        );
        TimeConvert.loadSpiceKernels(kernelsToLoad);
    }

    @AfterAll
    static void teardown() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
    }

    /*
    protected static TelemetrySource getSpiedRawTelemetrySourceFor(TimeCorrelationRunConfig config, String path) throws Exception {
        RawTelemetryTableTelemetrySource rawTlmTable = new RawTelemetryTableTelemetrySource();
        TimeCorrelationRunConfig spiedConfig = Mockito.spy(config);
        when(spiedConfig.getString("telemetry.source.plugin.rawTlmTable.tableFile.path")).thenReturn(path);
        rawTlmTable.applyConfiguration(spiedConfig);
        return Mockito.spy(rawTlmTable);
    }

     */

    protected static TimeCorrelationRunConfig getConfigWithSpiedRawTelemetrySourceFor(TimeCorrelationRunConfig config, String path) throws Exception {
        TimeCorrelationRunConfig spiedConfig = Mockito.spy(config);
        when(spiedConfig.getString("telemetry.source.plugin.rawTlmTable.tableFile.path")).thenReturn(path);

        RawTelemetryTableTelemetrySource rawTlmTable = Mockito.spy(new RawTelemetryTableTelemetrySource());
        rawTlmTable.applyConfiguration(spiedConfig);
        when(spiedConfig.getTelemetrySource()).thenReturn(rawTlmTable);

        return spiedConfig;
    }

    protected static Boolean satisfiedFilters(TimeCorrelationTarget tcTarget) {
        return true;
    }

    protected static Boolean unsatisfiedFilters(TimeCorrelationTarget tcTarget) {
        return false;
    }

    protected static Boolean throwingFilters(TimeCorrelationTarget tcTarget) throws MmtcException {
        throw new MmtcException("Test exception from filter");
    }

    protected static void assertEqualTkSclk(int expectedTkSclkCoarse, int expectedTkSclkFine, FrameSample frameSample) {
        assertEquals(expectedTkSclkCoarse, frameSample.getTkSclkCoarse());
        assertEquals(expectedTkSclkFine, frameSample.getTkSclkFine());
    }
}
