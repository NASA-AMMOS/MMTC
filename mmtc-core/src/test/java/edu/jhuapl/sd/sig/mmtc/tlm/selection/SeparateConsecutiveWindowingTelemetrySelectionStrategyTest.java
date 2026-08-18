package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.NoTelemetryFoundException;
import edu.jhuapl.sd.sig.mmtc.app.TelemetryQualityException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationCliInputConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
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

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());

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

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2017-352T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());

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

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(
                    timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), spiedConfig)
            );

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());

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

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(
                    timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), spiedConfig)
            );

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());

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

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            TelemetryQualityException thrownException = assertThrows(
                    TelemetryQualityException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::unsatisfiedFilters)
            );

            assertEquals("All candidate sample sets failed filters. Only 3 frames from the query interval are left, which is not enough to build another candidate sample set. A sample set requires 5 frames.", thrownException.getMessage());

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneWithinRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2020-001T00:00:00.000Z", "2021-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            NoTelemetryFoundException thrownException = assertThrows(
                    NoTelemetryFoundException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("No telemetry found within query window", thrownException.getMessage());

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());
        }
    }

    @Test
    public void testSelectingLatestSamplesNotEnoughWithinRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-340T00:00:00.000Z", "2017-341T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            NoTelemetryFoundException thrownException = assertThrows(
                    NoTelemetryFoundException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("No telemetry found within query window", thrownException.getMessage());

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneInTelemetry() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_EMPTY);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            NoTelemetryFoundException thrownException = assertThrows(
                    NoTelemetryFoundException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("No telemetry found within query window", thrownException.getMessage());

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());
        }
    }

    @Test
    public void testFiltersThrowingException() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SeparateConsecutiveWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSeparateConsecutiveWindows(
                    spiedConfig,
                    spiedConfig
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::throwingFilters)
            );

            assertEquals("Test exception from filter", thrownException.getMessage());

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleRange().get().getStart(), spiedConfig.getResolvedTargetSampleRange().get().getStop());
        }
    }
}
