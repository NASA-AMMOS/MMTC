package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.util.FileUtils;
import org.jdbi.v3.core.Jdbi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An instance of this class serves as the interface to telemetry caching operations for the rest of the application.
 */
public class TelemetryCache {
    private final Path cacheFilepath;
    private final Jdbi jdbi;
    private final FrameSampleCache frameSampleCache;

    public TelemetryCache(TelemetrySource tlmSource, Path cacheFilepath) throws IOException {
        this.cacheFilepath = cacheFilepath;

        boolean createSqliteDb = false;
        if (! Files.exists(cacheFilepath)) {
            createSqliteDb = true;
        }

        jdbi = Jdbi.create("jdbc:sqlite:" + cacheFilepath.toAbsolutePath());

        if (createSqliteDb) {
            createTables(createSqliteDb);
        }

        frameSampleCache = new FrameSampleCache(tlmSource);
        jdbi.withHandle(frameSampleCache::init);
    }

    private synchronized void createTables(boolean createSqliteDb) throws IOException {
        if (createSqliteDb) {
            jdbi.withHandle(handle -> {
                handle.execute(FileUtils.readResourceToString("/tlm_cache_db/frame_samples.sql"));
                handle.execute("CREATE UNIQUE INDEX idx_ert_epoch_ms ON frame_samples (ertEpochMs);");
                handle.execute(FileUtils.readResourceToString("/tlm_cache_db/frame_sample_query_range_history.sql"));
                handle.execute(FileUtils.readResourceToString("/tlm_cache_db/mmtc_metadata.sql"));

                TelemetrySqliteCacheDatabaseOperations.writeMetadata(handle);

                return null;
            });
        }
    }

    public synchronized List<FrameSample> getSamplesInRange(OffsetDateTime startErt, OffsetDateTime stopErt) throws MmtcException {
        return jdbi.withHandle(handle -> frameSampleCache.getSamplesInRange(handle, startErt, stopErt));
    }

    public synchronized Map<String, String> getCacheStatistics() throws IOException {
        Map<String, String> stats = new TreeMap<>();
        stats.putAll(jdbi.withHandle(frameSampleCache::getCacheStatistics));
        stats.put("Cache size on disk (kB)", Long.toString(Files.size(cacheFilepath) / 1024));
        return stats;
    }
}
