package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.model.FrameSampleEntity;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.model.OffsetDateTimeRangeEntity;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.jdbi.v3.core.Handle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides static methods that implement the required read and write actions on the local sqlite database file.
 */
class MmtcSqliteCacheDb {
    private static final String FRAME_SAMPLE_TABLENAME = "frame_samples";
    private static final String FRAME_SAMPLE_QUERY_RANGE_HISTORY_TABLENAME = "frame_sample_query_range_history";
    private static final String MMTC_METADATA_TABLENAME = "mmtc_metadata";


    public static synchronized List<OffsetDateTimeRange> readAllFrameSampleCoveredQueryRanges(Handle handle) {
        return handle.createQuery("SELECT * FROM \"" + FRAME_SAMPLE_QUERY_RANGE_HISTORY_TABLENAME + "\"")
                .mapToBean(OffsetDateTimeRangeEntity.class)
                .list()
                .stream()
                .map(OffsetDateTimeRangeEntity::toOffsetDateTimeRange)
                .collect(Collectors.toList());
    }

    public static synchronized void writeAllFrameSampleCoveredQueryRanges(Handle handle, List<OffsetDateTimeRange> ranges) {
        // drop all rows from this table and re-add all covered ranges, as CoverageTracker may have merged previously-disjoint query ranges

        handle.execute("DELETE FROM \"" + FRAME_SAMPLE_QUERY_RANGE_HISTORY_TABLENAME + "\"");

        ranges.forEach(r -> {
            handle.createUpdate("INSERT INTO \"" + FRAME_SAMPLE_QUERY_RANGE_HISTORY_TABLENAME + "\" (\"startSec\", \"startNanoOfSec\", \"stopSec\", \"stopNanoOfSec\") VALUES (:startSec, :startNanoOfSec, :stopSec, :stopNanoOfSec)")
                    .bindBean(OffsetDateTimeRangeEntity.fromOffsetDateTimeRange(r))
                    .execute();
        });
    }

    public static synchronized List<FrameSample> readFrameSamples(Handle handle, OffsetDateTimeRange queryRange) {
        // many of MMTC's integrated telemetry archives support ERT queries with precision higher than millisecond, so add an extra ms to the end of the query range and filter the results post-pass for accurate results
        // in other words: a FrameSample's ertStr field may contain values with a higher precision than millisecond, so perform second-level filtering on that field after retrieval from the cache

        return handle.createQuery(String.format(
                        "SELECT * FROM \"" + FRAME_SAMPLE_TABLENAME + "\" WHERE \"ertEpochMs\" BETWEEN %d and %d ORDER BY \"ertEpochMs\" ASC",
                        queryRange.getStart().toInstant().toEpochMilli(),
                        queryRange.getStop().toInstant().toEpochMilli() + 1
                ))
                .mapToBean(FrameSampleEntity.class)
                .list()
                .stream()
                .map(FrameSampleEntity::toFrameSample)
                .filter(fs -> ! (TimeConvert.parseIsoDoyUtcStr(fs.getErtStr()).isAfter(queryRange.getStop())))
                .collect(Collectors.toList());
    }

    public static synchronized Long readNumFrameSamples(Handle handle) {
        return handle.createQuery("SELECT count(1) FROM \"" + FRAME_SAMPLE_TABLENAME + "\"")
                .mapTo(Long.class)
                .one();

    }

    public static synchronized void writeFrameSamples(Handle handle, List<FrameSample> frameSamples) {
        final StringBuilder insertStatementBuilder = new StringBuilder("INSERT INTO \"" + FRAME_SAMPLE_TABLENAME + "\" (");
        insertStatementBuilder.append(FrameSampleEntity.FIELD_NAMES.stream().map(f -> "\"" + f + "\"").collect(Collectors.joining(",")));
        insertStatementBuilder.append(") VALUES (");
        insertStatementBuilder.append(FrameSampleEntity.FIELD_NAMES.stream().map(f -> ":" + f).collect(Collectors.joining(",")));
        insertStatementBuilder.append(")");

        final String insertStatement =  insertStatementBuilder.toString();

        for (FrameSample sample : frameSamples) {
            handle.createUpdate(insertStatement)
                    .bindBean(FrameSampleEntity.fromFrameSample(sample))
                    .execute();
        }
    }

    public static synchronized void writeMetadata(Handle handle) {
        final Map<String, String> metadataMap = new HashMap<>();

        metadataMap.put(
                "MMTC_VERSION", new BuildInfo().version
        );
        metadataMap.put(
                "MMTC_VERSION_STRING", new BuildInfo().toString()
        );

        metadataMap.forEach((k,v) -> {
            handle.createUpdate("INSERT INTO \"" + MMTC_METADATA_TABLENAME + "\" (\"key\", \"value\") VALUES (:key, :value)")
                    .bind("key", k)
                    .bind("value", v)
                    .execute();
        });
    }
}
