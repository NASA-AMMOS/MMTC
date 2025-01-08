package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.filter.GroundStationFilter;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SeparateConsecutiveWindowingTelemetrySelectionStrategyTest extends BaseTelemetrySelectionStrategyTest {
    @Test
    public void testSelectingLatestSamples() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect they're the most recent 5 samples available in the set
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(375925235, 32077, sampleSet.get(0));
            assertEqualTkSclk(375928821, 18753, sampleSet.get(1));

            assertEqualTkSclk(375962471, 4003, sampleSet.get(2));
            assertEqualTkSclk(375962471, 4003, tcTarget.getTargetSample());

            assertEqualTkSclk(375964861, 12867, sampleSet.get(3));
            assertEqualTkSclk(375968458, 23708, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesWithinOlderRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2017-352T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect they're the most recent 5 samples available in the set, bounded by the end query time
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(375824437,	30312, sampleSet.get(0));
            assertEqualTkSclk(375828036,	34772, sampleSet.get(1));

            assertEqualTkSclk(375831636,	43924, sampleSet.get(2));
            assertEqualTkSclk(375831636,	43924, tcTarget.getTargetSample());

            assertEqualTkSclk(375835237,	3075, sampleSet.get(3));
            assertEqualTkSclk(375838841,	49207, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesWithFilter() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(
                    timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), config)
            );

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect they're the second most recent set of 5 samples, not overlapping with any prior samples, available in the set that share a common ground station
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(375908397,	44599, sampleSet.get(0));
            assertEqualTkSclk(375910830,	18512, sampleSet.get(1));

            assertEqualTkSclk(375914430,	31002, sampleSet.get(2));
            assertEqualTkSclk(375914430,	31002, tcTarget.getTargetSample());

            assertEqualTkSclk(375918030,	40154, sampleSet.get(3));
            assertEqualTkSclk(375921630,	49305, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesWithFilterLessRecent() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowingOnlyStation55");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(
                    timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), config)
            );

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect that this filter will choose the:
            // - most recent set of 5 samples that share a common ground station of 55
            // - that are aligned on a non-overlapping window of size 5
            // these are located in rows 16660 - 16664 of the CSV
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(368643399,	6590, sampleSet.get(0));
            assertEqualTkSclk(368646991,	18969, sampleSet.get(1));

            assertEqualTkSclk(368650597,	14284, sampleSet.get(2));
            assertEqualTkSclk(368650597,	14284, tcTarget.getTargetSample());

            assertEqualTkSclk(368654189,	26662, sampleSet.get(3));
            assertEqualTkSclk(368657795,	21977, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneMatchingFilter() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::unsatisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneWithinRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2020-001T00:00:00.000Z", "2021-001T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        }
    }

    @Test
    public void testSelectingLatestSamplesNotEnoughWithinRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2017-340T00:00:00.000Z", "2017-341T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneInTelemetry() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_EMPTY);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        }
    }

    @Test
    public void testFiltersThrowingException() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z");
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::throwingFilters)
            );

            assertEquals("Test exception from filter", thrownException.getMessage());

            verify(tlmSource, times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        }
    }
}