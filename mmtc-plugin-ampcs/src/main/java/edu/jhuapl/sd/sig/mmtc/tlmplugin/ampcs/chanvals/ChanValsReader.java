package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.AmpcsTelemetrySourceConfig;
import org.apache.commons.csv.CSVRecord;

import java.time.OffsetDateTime;
import java.util.*;

public abstract class ChanValsReader {
    protected final AmpcsTelemetrySourceConfig config;
    protected final Map<String, SingleChanValReader> channelValueReadersByChannelId = new HashMap<>();

    public static class ChanValsForSameScetResult {
        public final OffsetDateTime commonScet;
        public final Map<String, Double> chanvalsById;

        public ChanValsForSameScetResult(OffsetDateTime commonScet, Map<String, Double> chanvalsById) {
            this.commonScet = commonScet;
            this.chanvalsById = Collections.unmodifiableMap(chanvalsById);
        }
    }

    public ChanValsReader(AmpcsTelemetrySourceConfig config) {
        this.config = config;
    }

    public void read(CSVRecord row) {
        final String rowChannelId = row.get(config.getChannelIdFieldName());

        if (channelValueReadersByChannelId.containsKey(rowChannelId)) {
            channelValueReadersByChannelId.get(rowChannelId).read(row);
        }
    }
}
