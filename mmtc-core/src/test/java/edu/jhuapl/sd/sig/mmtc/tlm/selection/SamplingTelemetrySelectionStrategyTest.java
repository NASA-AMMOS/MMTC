package edu.jhuapl.sd.sig.mmtc.tlm.selection;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliInputConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.filter.GroundStationFilter;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SamplingTelemetrySelectionStrategyTest extends BaseTelemetrySelectionStrategyTest {

    @Test
    public void testQueryPeriodGenerationLongInputTimespan() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            // throws because we're making the 'filters' fail
            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::unsatisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges = getQueriedRanges(tlmSource);

            generalQueryPeriodAssertions(config, queriedRanges);

            // query period is once every two days, so should have 365/2 = ~183 query periods
            assertEquals(183, queriedRanges.size());
        }
    }

    @Test
    public void testQueryPeriodGenerationWithInputTimespanShorterThanQueryWidth() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2018-001T00:00:00.000Z", "2018-001T04:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            // throws because there's no telemetry in range
            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges = getQueriedRanges(tlmSource);

            generalQueryPeriodAssertions(config, queriedRanges);

            // query period is once every two days, so should have 365/2 = ~183 query periods
            assertEquals(1, queriedRanges.size());

            Pair<OffsetDateTime, OffsetDateTime> onlyQueryRange = queriedRanges.get(0);
            assertEquals(4, Duration.between(onlyQueryRange.getLeft(), onlyQueryRange.getRight()).toHours());
        }
    }

    @Test
    public void testQueryPeriodGenerationWithInputTimespanMuchShorterThanQueryWidth() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2018-001T00:00:00.000Z", "2018-001T00:00:30.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            // throws because there's no telemetry in range
            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges = getQueriedRanges(tlmSource);

            generalQueryPeriodAssertions(config, queriedRanges);

            // query period is once every two days, so should have 365/2 = ~183 query periods
            assertEquals(1, queriedRanges.size());

            Pair<OffsetDateTime, OffsetDateTime> onlyQueryRange = queriedRanges.get(0);
            assertEquals(30, Duration.between(onlyQueryRange.getLeft(), onlyQueryRange.getRight()).getSeconds());
        }
    }

    @Test
    public void testQueryPeriodGenerationWithInputTimespanJustLongerThanQueryWidth() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2018-001T00:00:00.000Z", "2018-001T13:00:30.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            // throws because there's no telemetry in range
            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());

            List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges = getQueriedRanges(tlmSource);

            generalQueryPeriodAssertions(config, queriedRanges);

            // query period is once every two days, so should have 365/2 = ~183 query periods
            assertEquals(1, queriedRanges.size());

            Pair<OffsetDateTime, OffsetDateTime> onlyQueryRange = queriedRanges.get(0);
            assertEquals(12, Duration.between(onlyQueryRange.getLeft(), onlyQueryRange.getRight()).toHours());
        }
    }

    @Test
    public void testQueryPeriodGenerationWithQueryWidthLongerThanQueryFrequency() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-6h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2018-001T00:00:00.000Z", "2018-003T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            // throws because invalid configuration was given to
            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Sampling telemetry selection strategy requires a query width <= sampling rate, but the configured query width is 720 minutes and the sampling rate is 360 minutes", thrownException.getMessage());
        }
    }

    @Test
    public void testQueryPeriodGenerationWithQueryWidthEqualToQueryFrequency() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-12h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2018-001T00:00:00.000Z", "2018-003T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            // throws because there's no telemetry in range
            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges = getQueriedRanges(tlmSource);

            generalQueryPeriodAssertions(config, queriedRanges);

            // query period is once every 12 hours, so we'd have four of them over two days
            assertEquals(4, queriedRanges.size());
        }
    }

    @Test
    public void testQueryPeriodGenerationShortInputTimespanOneQueryRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-352T00:00:00.000Z", "2017-353T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters);

            // we should have been able to build a valid sample set (i.e. TimeCorrelationTarget) from the first query
            List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges = getQueriedRanges(tlmSource);
            generalQueryPeriodAssertions(config, queriedRanges);
            assertEquals(1, queriedRanges.size());
            assertEquals(OffsetDateTime.parse("2017-12-18T12:00:00Z"), queriedRanges.get(0).getLeft());
            assertEquals(OffsetDateTime.parse("2017-12-19T00:00:00Z"), queriedRanges.get(0).getRight());

            // the five FrameSamples should be the most recent five ones just before 2017-353
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(375910830,	18512, sampleSet.get(0));
            assertEqualTkSclk(375914430,	31002, sampleSet.get(1));

            assertEqualTkSclk(375918030,	40154, sampleSet.get(2));
            assertEqualTkSclk(375918030,	40154, tcTarget.getTargetSample());

            assertEqualTkSclk(375921630,	49305, sampleSet.get(3));
            assertEqualTkSclk(375925235,	32077, sampleSet.get(4));
        }
    }

    @Test
    public void testQueryPeriodGenerationLongInputTimespanWithFilter() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h-OnlyStation55");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2017-353T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            TimeCorrelationTarget tcTarget = tlmSelecStrat.get(
                    timeCorrelationTarget -> new GroundStationFilter().process(timeCorrelationTarget.getSampleSet(), config)
            );

            // we should have been able to build a valid sample set (i.e. TimeCorrelationTarget) from the first query
            List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges = getQueriedRanges(tlmSource);
            generalQueryPeriodAssertions(config, queriedRanges);
            assertEquals(31, queriedRanges.size());

            // the five FrameSamples should be the most recent five ones just before 2017-353
            List<FrameSample> sampleSet = tcTarget.getSampleSet();

            assertEqualTkSclk(370717291,	45881, sampleSet.get(0));
            assertEqualTkSclk(370720301,	768, sampleSet.get(1));

            assertEqualTkSclk(370723903,	12627, sampleSet.get(2));
            assertEqualTkSclk(370723903,	12627, tcTarget.getTargetSample());

            assertEqualTkSclk(370727491,	1145, sampleSet.get(3));
            assertEqualTkSclk(370731093,	13005, sampleSet.get(4));
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneMatchingFilter() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2018-001T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::unsatisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneWithinRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2020-001T00:00:00.000Z", "2021-001T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());
        }
    }

    @Test
    public void testSelectingLatestSamplesNotEnoughWithinRange() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-340T00:00:00.000Z", "2017-341T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());
        }
    }

    @Test
    public void testSelectingLatestSamplesNoneInTelemetry() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2006-01-20T01:00:00.000Z", "2018-01-20T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_EMPTY);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::satisfiedFilters)
            );

            assertEquals("Unable to find valid sample set", thrownException.getMessage());
        }
    }

    @Test
    public void testFiltersThrowingException() throws Exception {
        try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
            mockedEnvironment
                    .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                    .thenReturn("src/test/resources/TelemetrySelection/Sampling/width-12h-rate-48h");

            final TimeCorrelationRunConfig config = new TimeCorrelationRunConfig(new TimeCorrelationCliInputConfig("-T", "0.0", "2017-001T00:00:00.000Z", "2017-353T00:00:00.000Z"));
            final TelemetrySource tlmSource = getSpiedRawTelemetrySourceFor(config, RAW_TLM_TBL_NH_REFORMATTED);

            SamplingTelemetrySelectionStrategy tlmSelecStrat = new SamplingTelemetrySelectionStrategy(
                    config,
                    tlmSource,
                    NH_FINE_TICK_MODULUS
            );

            MmtcException thrownException = assertThrows(
                    MmtcException.class,
                    () -> tlmSelecStrat.get(BaseTelemetrySelectionStrategyTest::throwingFilters)
            );

            assertEquals("Test exception from filter", thrownException.getMessage());

            verify(tlmSource, times(1)).getSamplesInRange(any(), any());
        }
    }

    private static List<Pair<OffsetDateTime, OffsetDateTime>> getQueriedRanges(TelemetrySource spiedTelemetrySource) {
        Collection<Invocation> invocations = mockingDetails(spiedTelemetrySource).getInvocations();

        return invocations.stream()
                .filter(i -> i.getMethod().getName().equals("getSamplesInRange"))
                .sorted(Comparator.comparingInt(Invocation::getSequenceNumber))
                .map(i -> Pair.of(
                                (OffsetDateTime) i.getArgument(0),
                                (OffsetDateTime) i.getArgument(1)
                        )
                ).collect(Collectors.toList());
    }

    private void generalQueryPeriodAssertions(TimeCorrelationRunConfig config, List<Pair<OffsetDateTime, OffsetDateTime>> queriedRanges) {
        OffsetDateTime prevStartTime = null;
        OffsetDateTime prevStopTime = null;

        for (Pair<OffsetDateTime, OffsetDateTime> queriedRange : queriedRanges) {
            if (prevStartTime == null) {
                assertEquals(config.getResolvedTargetSampleRange().get().getStop(), queriedRange.getRight());
            } else {
                // check query frequency

                assertEquals(
                        prevStartTime.minusMinutes(config.getSamplingSampleSetBuildingStrategySamplingRateMinutes()),
                        queriedRange.getLeft()
                );

                assertEquals(
                        prevStopTime.minusMinutes(config.getSamplingSampleSetBuildingStrategySamplingRateMinutes()),
                        queriedRange.getRight()
                );
            }

            assertFalse(queriedRange.getLeft().isBefore(config.getResolvedTargetSampleRange().get().getStart()));

            // check query duration
            assertTrue(queriedRange.getLeft().isBefore(queriedRange.getRight()));

            if (queriedRange.getLeft().equals(config.getResolvedTargetSampleRange().get().getStart())) {
                // if the period is bounded on the left by the input start time, allow it to be less than the normal query width
                assertTrue(Duration.between(queriedRange.getLeft(), queriedRange.getRight()).toMinutes() <= config.getSamplingSampleSetBuildingStrategyQueryWidthMinutes());
            } else {
                assertEquals(
                        Duration.of(config.getSamplingSampleSetBuildingStrategyQueryWidthMinutes(), ChronoUnit.MINUTES),
                        Duration.between(queriedRange.getLeft(), queriedRange.getRight())
                );
            }

            prevStartTime = queriedRange.getLeft();
            prevStopTime = queriedRange.getRight();
        }
    }
}
