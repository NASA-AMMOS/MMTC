package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.NoTelemetryFoundException;
import edu.jhuapl.sd.sig.mmtc.app.TelemetryQualityException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationCliInputConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.config.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.filter.GroundStationFilter;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder.ASCENDING;
import static edu.jhuapl.sd.sig.mmtc.tlm.selection.TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder.DESCENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SlidingWindowingTelemetrySelectionStrategyTest extends BaseTelemetrySelectionStrategyTest {
    @Test
    public void testSelectingLatestSamples() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());

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
    public void testSelectingEarlierSamplesAscending() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);
            Mockito.doReturn(TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder.ASCENDING).when(spiedConfig).getTargetSampleRangeErtSeekOrder();

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect they're the most recent 5 samples available in the set
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(26219, 5197, sampleSet.get(0));
            assertEqualTkSclk(26520, 44266, sampleSet.get(1));

            assertEqualTkSclk(30119, 7341, sampleSet.get(2));
            assertEqualTkSclk(30119, 7341, tcTarget.getTargetSample());

            assertEqualTkSclk(33719, 48734, sampleSet.get(3));
            assertEqualTkSclk(37320, 40128, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingEarlierSamplesAscendingStationFilter() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowingOnlyStation45");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);
            Mockito.doReturn(TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder.ASCENDING).when(spiedConfig).getTargetSampleRangeErtSeekOrder();

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), spiedConfig));

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect they're the most recent 5 samples available in the set
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(89523, 32500, sampleSet.get(0));
            assertEqualTkSclk(93124, 23893, sampleSet.get(1));

            assertEqualTkSclk(96725, 15286, sampleSet.get(2));
            assertEqualTkSclk(96725, 15286, tcTarget.getTargetSample());

            assertEqualTkSclk(100326, 6679, sampleSet.get(3));
            assertEqualTkSclk(103927, 2372, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesWithinOlderRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2017-352T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());

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
                    .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(
                    timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), spiedConfig)
            );

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect they're the most recent set of 5 samples available in the set that share a common ground station
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(375914430, 31002, sampleSet.get(0));
            assertEqualTkSclk(375918030, 40154, sampleSet.get(1));

            assertEqualTkSclk(375921630, 49305, sampleSet.get(2));
            assertEqualTkSclk(375921630, 49305, tcTarget.getTargetSample());

            assertEqualTkSclk(375925235, 32077, sampleSet.get(3));
            assertEqualTkSclk(375928821, 18753, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesWithFilterLessRecent() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowingOnlyStation55");

            final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);

            WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                    spiedConfig,
                    spiedConfig
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(
                    timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), spiedConfig)
            );

            verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());

            // we expect 5 samples, as this is the sample set size given in the config we're using
            assertEquals(5, tcTarget.getSampleSet().size());

            // we expect that this filter will choose the most recent set of 5 samples that share a common ground station of 55
            // these are located in rows 16973 - 16977 of the CSV
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(371656159,	43349, sampleSet.get(0));
            assertEqualTkSclk(371659762,	5208, sampleSet.get(1));

            assertEqualTkSclk(371663364,	17068, sampleSet.get(2));
            assertEqualTkSclk(371663364,	17068, tcTarget.getTargetSample());

            assertEqualTkSclk(371666966,	28927, sampleSet.get(3));
            assertEqualTkSclk(371670568,	40786, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneMatchingFilter() throws Exception {
        for (TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder seekOrder : Arrays.asList(DESCENDING, ASCENDING)) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

                final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);
                Mockito.doReturn(seekOrder).when(spiedConfig).getTargetSampleRangeErtSeekOrder();

                WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                        spiedConfig,
                        spiedConfig
                );

                TelemetryQualityException thrownException = assertThrows(
                        TelemetryQualityException.class,
                        () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::unsatisfiedFilters)
                );

                assertEquals("Not enough frames from the query interval remaining.", thrownException.getMessage());

                verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());
            }
        };
    }

    @Test
    public void testSelectingLatestSamplesNoneWithinRange() throws Exception {
        for (TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder seekOrder : Arrays.asList(DESCENDING, ASCENDING)) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

                final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2020-001T00:00:00.000Z", "2021-001T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);
                Mockito.doReturn(seekOrder).when(spiedConfig).getTargetSampleRangeErtSeekOrder();

                WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                        spiedConfig,
                        spiedConfig
                );

                NoTelemetryFoundException thrownException = assertThrows(
                        NoTelemetryFoundException.class,
                        () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
                );

                assertEquals("No telemetry found within query window", thrownException.getMessage());

                verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());
            }
        }
    }

    @Test
    public void testSelectingLatestSamplesNotEnoughWithinRange() throws Exception {
        for (TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder seekOrder : Arrays.asList(DESCENDING, ASCENDING)) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

                final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-353T00:00:00.000Z", "2017-354T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);
                Mockito.doReturn(seekOrder).when(spiedConfig).getTargetSampleRangeErtSeekOrder();

                WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                        spiedConfig,
                        spiedConfig
                );

                TelemetryQualityException thrownException = assertThrows(
                        TelemetryQualityException.class,
                        () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
                );

                assertEquals("Not enough frames found within the query interval to build a sample set. A sample set requires 5 frames; 4 were found.", thrownException.getMessage());

                verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());
            }
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneInTelemetry() throws Exception {
        for (TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder seekOrder : Arrays.asList(DESCENDING, ASCENDING)) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

                final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_EMPTY);
                Mockito.doReturn(seekOrder).when(spiedConfig).getTargetSampleRangeErtSeekOrder();

                WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                        spiedConfig,
                        spiedConfig
                );

                NoTelemetryFoundException thrownException = assertThrows(
                        NoTelemetryFoundException.class,
                        () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
                );

                assertEquals("No telemetry found within query window", thrownException.getMessage());

                verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());
            }
        }
    }

    @Test
    public void testFiltersThrowingException() throws Exception {
        for (TelemetrySelectionAndAdjustmentOptions.TargetSampleRangeErtSeekOrder seekOrder : Arrays.asList(DESCENDING, ASCENDING)) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn("src/test/resources/TelemetrySelection/SlidingWindowing");

                final TimeCorrelationRunConfig spiedConfig = getConfigWithSpiedRawTelemetrySourceFor(new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z")), RAW_TLM_TBL_NH_REFORMATTED);
                Mockito.doReturn(seekOrder).when(spiedConfig).getTargetSampleRangeErtSeekOrder();

                WindowingTelemetrySelectionStrategy tlmSelecStrat = WindowingTelemetrySelectionStrategy.forSlidingWindow(
                        spiedConfig,
                        spiedConfig
                );

                MmtcException thrownException = assertThrows(
                        MmtcException.class,
                        () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::throwingFilters)
                );

                assertEquals("Test exception from filter", thrownException.getMessage());

                verify(spiedConfig.getTelemetrySource(), times(1)).getSamplesInRange(spiedConfig.getResolvedTargetSampleErtRange().get().getStart(), spiedConfig.getResolvedTargetSampleErtRange().get().getStop());
            }
        }
    }
}
