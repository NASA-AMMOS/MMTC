package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.CachingTelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.RawTelemetryTableTelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TelemetryCacheTest {
    private TimeCorrelationAppConfig config;
    private RawTelemetryTableTelemetrySource vanillaRawTlmTableSource;
    private RawTelemetryTableTelemetrySource spiedRawTlmTableSource;
    private CachingTelemetrySource cachingTlmSource;

    private Path cacheFilepath;

    @BeforeEach
    public void setup() {
        cacheFilepath = Paths.get("/tmp", "mmtc-test-tlm-cache-" + UUID.randomUUID() + ".sqlite");
    }

    @AfterEach
    public void teardown() throws IOException {
        Files.delete(cacheFilepath);
        TimeConvert.unloadSpiceKernels();
    }

    void loadConfigAndTestTlmSources(String[] args, String rawTlmTablePath) throws Exception {
        config = spy(new TimeCorrelationAppConfig(args));
        when(config.getString("telemetry.source.plugin.rawTlmTable.tableFile.path")).thenReturn(rawTlmTablePath);

        vanillaRawTlmTableSource = new RawTelemetryTableTelemetrySource();
        vanillaRawTlmTableSource.applyConfiguration(config);

        spiedRawTlmTableSource = Mockito.spy(new RawTelemetryTableTelemetrySource());
        cachingTlmSource = new CachingTelemetrySource(cacheFilepath.toAbsolutePath(), spiedRawTlmTableSource);
        cachingTlmSource.applyConfiguration(config);

        TimeConvert.loadSpiceLib();
        TimeConvert.loadSpiceKernels(config.getKernelsToLoad());
    }

    @Test
    public void simpleTest() throws Exception {
        loadConfigAndTestTlmSources(
                new String[] {"2017-01-07T00:00:00.000Z", "2017-01-10T00:00:00.000Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv"
        );

        List<FrameSample> samplesReturnByRawTlmTable = vanillaRawTlmTableSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
        List<FrameSample> samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());

        // ensure that, with an empty cache, the caching TLM source retrieves the exact same samples as the actual raw TLM table source
        assertEquals(24, samplesReturnByRawTlmTable.size());
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        assertEquals(samplesReturnByRawTlmTable, samplesReturnedByCachingTlmSource);

        // ensure that, with a populated cache, the caching TLM source retrieves the exact same samples as the actual raw TLM table source and that the spied inner telemetry source hasn't been called again
        samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        assertEquals(samplesReturnByRawTlmTable, samplesReturnedByCachingTlmSource);
    }

    @Test
    public void noSamplesTest() throws Exception {
        loadConfigAndTestTlmSources(
                new String[] {"2017-002T00:00:00Z", "2017-003T00:00:00Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv"
        );

        List<FrameSample> samplesReturnByRawTlmTable = vanillaRawTlmTableSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
        List<FrameSample> samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());

        assertEquals(0, samplesReturnByRawTlmTable.size());
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(config.getStartTime(), config.getStopTime());
        assertEquals(samplesReturnByRawTlmTable, samplesReturnedByCachingTlmSource);
    }

    @Test
    public void overlappingRegionsTest() throws Exception {
        loadConfigAndTestTlmSources(
                new String[] {"2017-002T00:00:00Z", "2017-003T00:00:00Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv"
        );

        List<FrameSample> samplesReturnByRawTlmTable = vanillaRawTlmTableSource.getSamplesInRange(config.getStartTime(), config.getStopTime());
        List<FrameSample> samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(config.getStartTime(), config.getStopTime());

        assertEquals(0, samplesReturnByRawTlmTable.size());
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(Mockito.any(), Mockito.any());
        assertEquals(samplesReturnByRawTlmTable, samplesReturnedByCachingTlmSource);

        // an inner region should not create any more queries to the underlying raw tlm table telemetry source
        samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(odt("2017-002T08:00:00"), odt("2017-002T16:00:00"));
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(Mockito.any(), Mockito.any());
        assertEquals(0, samplesReturnedByCachingTlmSource.size());

        // a region that extends the earlier side should create one more query to the underlying raw tlm table telemetry source
        samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(odt("2016-365T00:00:00"), odt("2017-002T12:00:00"));
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(2)).getSamplesInRange(Mockito.any(), Mockito.any());
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(odt("2016-365T00:00:00"), odt("2017-002T00:00:00"));
        assertEquals(6, samplesReturnedByCachingTlmSource.size());

        // a region that extends both side should create two more queries to the underlying tlm source, one on each side
        samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(odt("2016-364T00:00:00"), odt("2017-008T00:00:00"));
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(4)).getSamplesInRange(Mockito.any(), Mockito.any());
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(odt("2016-364T00:00:00"), odt("2016-365T00:00:00"));
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(1)).getSamplesInRange(odt("2017-003T00:00:00"), odt("2017-008T00:00:00"));
        assertEquals(20, samplesReturnedByCachingTlmSource.size());

        // one final query over the whole region creates no more underlying queries, and matches that returned by the underlying tlm source
        samplesReturnByRawTlmTable = vanillaRawTlmTableSource.getSamplesInRange(odt("2016-364T00:00:00"), odt("2017-008T00:00:00"));
        samplesReturnedByCachingTlmSource = cachingTlmSource.getSamplesInRange(odt("2016-364T00:00:00"), odt("2017-008T00:00:00"));
        Mockito.verify(spiedRawTlmTableSource, Mockito.times(4)).getSamplesInRange(Mockito.any(), Mockito.any());
        assertEquals(samplesReturnByRawTlmTable, samplesReturnedByCachingTlmSource);
    }

    @Test
    public void directMmtcSqliteCacheDbFrameSampleQueryRangeTest() throws Exception {
        loadConfigAndTestTlmSources(
                new String[] {"2017-002T00:00:00Z", "2017-003T00:00:00Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv"
        );

        // use reflection to get a reference to cachingTlmSource.telemetryCache.jdbi
        final TelemetryCache tlmCache = (TelemetryCache) FieldUtils.readField(cachingTlmSource, "telemetryCache", true);
        final Jdbi jdbi = (Jdbi) FieldUtils.readField(tlmCache, "jdbi", true);

        OffsetDateTimeRange originalRange = new OffsetDateTimeRange(odt("2016-364T01:02:03.45678911"), odt("2017-008T00:00:00"));
        List<OffsetDateTimeRange> originalRanges = Arrays.asList(originalRange);

        jdbi.withHandle(handle -> {
            TelemetrySqliteCacheDatabaseOperations.writeAllFrameSampleCoveredQueryRanges(handle, originalRanges);
            return true;
        });

        assertEquals(
                originalRanges,
                jdbi.withHandle(handle -> TelemetrySqliteCacheDatabaseOperations.readAllFrameSampleCoveredQueryRanges(handle))
        );

        List<OffsetDateTimeRange> newOriginalRanges = Arrays.asList(
                new OffsetDateTimeRange(odt("2016-364T01:02:03.45678911"), odt("2017-008T00:00:00")),
                new OffsetDateTimeRange(odt("2017-020T01:02:03.45678911"), odt("2017-030T00:00:00"))
        );

        jdbi.withHandle(handle -> {
            TelemetrySqliteCacheDatabaseOperations.writeAllFrameSampleCoveredQueryRanges(handle, newOriginalRanges);
            return true;
        });

        assertEquals(
                newOriginalRanges,
                jdbi.withHandle(handle -> TelemetrySqliteCacheDatabaseOperations.readAllFrameSampleCoveredQueryRanges(handle))
        );
    }

    @Test
    public void directMmtcSqliteCacheDbFrameSampleTest() throws Exception {
        loadConfigAndTestTlmSources(
                new String[] {"2017-002T00:00:00Z", "2017-003T00:00:00Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv"
        );

        // use reflection to get a reference to cachingTlmSource.telemetryCache.jdbi
        final TelemetryCache tlmCache = (TelemetryCache) FieldUtils.readField(cachingTlmSource, "telemetryCache", true);
        final Jdbi jdbi = (Jdbi) FieldUtils.readField(tlmCache, "jdbi", true);

        FrameSample fs0 = getTestFrameSampleWithErt("2024-360T12:34:56.28910234");
        FrameSample fs1 = getTestFrameSampleWithErtStr("2024-360T12:34:56.78910234");
        FrameSample fs2 = getTestFrameSampleWithErtStr("2024-360T12:34:57.78910234");
        fs2.setTkValid(false);
        FrameSample fs3 = getTestFrameSampleWithErtStr("2024-360T12:34:58.78910234");
        fs3.setTkValid(true);

        List<FrameSample> originalSamples = Arrays.asList(fs0, fs1, fs2, fs3);

        jdbi.withHandle(handle -> {
            TelemetrySqliteCacheDatabaseOperations.writeFrameSamples(handle, originalSamples);
            return true;
        });

        assertEquals(
                originalSamples,
                jdbi.withHandle(handle -> TelemetrySqliteCacheDatabaseOperations.readFrameSamples(handle, new OffsetDateTimeRange(odt("2024-360T00:00:00"), odt("2024-361T00:00:00"))))
        );
    }

    @Test
    public void directMmtcSqliteCacheDbFrameSampleDuplicateErtTest() throws Exception {
        loadConfigAndTestTlmSources(
                new String[] {"2017-002T00:00:00Z", "2017-003T00:00:00Z"},
                "src/test/resources/tables/RawTelemetryTable_NH_reformatted.csv"
        );

        // use reflection to get a reference to cachingTlmSource.telemetryCache.jdbi
        final TelemetryCache tlmCache = (TelemetryCache) FieldUtils.readField(cachingTlmSource, "telemetryCache", true);
        final Jdbi jdbi = (Jdbi) FieldUtils.readField(tlmCache, "jdbi", true);

        FrameSample fs0 = getTestFrameSampleWithErtStr("2024-360T12:34:56.78910234");
        FrameSample fs1 = getTestFrameSampleWithErtStr("2024-360T12:34:56.78910234");

        List<FrameSample> originalSamples = Arrays.asList(fs0, fs1);

        UnableToExecuteStatementException thrownException = assertThrows(
                UnableToExecuteStatementException.class,
                () -> {
                    jdbi.withHandle(handle -> {
                        TelemetrySqliteCacheDatabaseOperations.writeFrameSamples(handle, originalSamples);
                        return true;
                    });
                }
        );

        assertTrue(thrownException.getMessage().contains("SQLITE_CONSTRAINT_UNIQUE"));
    }

    private static OffsetDateTime odt(String odt) {
        return TimeConvert.parseIsoDoyUtcStr(odt);
    }

    private static FrameSample getTestFrameSampleWithErtStr(String ert) {
        FrameSample fs = new FrameSample();

        fs.setSclkCoarse(23456);
        fs.setSclkFine(100);
        // fs.setErt();
        fs.setErtStr(ert);
        fs.setScet("2024-360T13:34:56.78910234");
        fs.setPathId(14);
        fs.setVcid(42);
        fs.setVcfc(511);
        fs.setMcfc(789);
        fs.setTkSclkCoarse(23457);
        fs.setTkSclkFine(101);
        fs.setTkVcid(43);
        fs.setTkVcfc(512);
        fs.setTkDataRateBps(BigDecimal.valueOf(1.023456789));
        fs.setTkRfEncoding("0xDEADBEEF");
        // fs.setTkIsValid();
        fs.setSuppVcid(41);
        fs.setSuppVcfc(99);
        fs.setSuppMcfc(788);
        // fs.setSuppErt();
        fs.setSuppErtStr("2024-360T12:34:57.78910234");
        fs.setFrameSizeBits(1080);

        return fs;
    }

    private static FrameSample getTestFrameSampleWithErt(String ert) throws TimeConvertException {
        FrameSample fs = new FrameSample();

        fs.setSclkCoarse(23456);
        fs.setSclkFine(100);
        fs.setErt(TimeConvert.isoUtcToCds(ert));
        // fs.setErtStr();
        fs.setScet("2024-360T13:34:56.78910234");
        fs.setPathId(14);
        fs.setVcid(42);
        fs.setVcfc(511);
        fs.setMcfc(789);
        fs.setTkSclkCoarse(23457);
        fs.setTkSclkFine(101);
        fs.setTkVcid(43);
        fs.setTkVcfc(512);
        fs.setTkDataRateBps(BigDecimal.valueOf(1.023456789));
        fs.setTkRfEncoding("0xDEADBEEF");
        // fs.setTkIsValid();
        fs.setSuppVcid(41);
        fs.setSuppVcfc(99);
        fs.setSuppMcfc(788);
        fs.setSuppErt(TimeConvert.isoUtcToCds("2024-360T12:34:57.78910234"));
        // fs.setSuppErtStr();
        fs.setFrameSizeBits(1080);

        return fs;
    }
}