package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.AmpcsTelemetrySourceConfig;

import java.time.OffsetDateTime;
import java.util.*;

public class ScetRangeMultiChanValsReader extends ChanValsReader {
    public ScetRangeMultiChanValsReader(AmpcsTelemetrySourceConfig config, List<ChanValReadConfig> readerConfigs) {
        super(config);

        for (ChanValReadConfig readConfig : readerConfigs) {
            channelValueReadersByChannelId.put(readConfig.channelId, new SingleChanValReader(config, readConfig, null));
        }
    }

    public List<ChanValsForSameScetResult> getPairedValuesForSameScet(String... channelIds) {
        for (String channelId : channelIds) {
            if (! channelValueReadersByChannelId.containsKey(channelId)) {
                throw new IllegalArgumentException("No such channel ID in reader: " + channelId);
            }
        }

        // build a set containing all SCETs from any channel value in the list of channel IDs passed into this method
        Set<OffsetDateTime> allCommonScets = new HashSet<>();
        Arrays.stream(channelIds)
                .map(channelValueReadersByChannelId::get)
                .forEach(reader -> allCommonScets.addAll(reader.getAllScets()));

        // reduce the set to that of the intersection of all scets from the channel IDs passed into this method
        Arrays.stream(channelIds)
                .map(channelValueReadersByChannelId::get)
                .forEach(reader -> allCommonScets.retainAll(reader.getAllScets()));

        List<ChanValsForSameScetResult> results = new ArrayList<>();

        for (OffsetDateTime scet : allCommonScets) {
            Map<String, Double> valuesByChannelId = new HashMap<>();
            for (String channelId : channelIds) {
                valuesByChannelId.put(channelId, channelValueReadersByChannelId.get(channelId).getValueForScet(scet));
            }

            results.add(new ChanValsForSameScetResult(scet, valuesByChannelId));
        }

        results.sort((a,b) -> a.commonScet.compareTo(b.commonScet));

        return Collections.unmodifiableList(results);
    }
}
