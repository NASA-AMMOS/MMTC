package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.AmpcsTelemetrySourceConfig;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

public class ChanValsReader {
    protected static final Logger logger = LogManager.getLogger();

    private final AmpcsTelemetrySourceConfig config;
    private final Map<String, SingleChanValReader> channelValueReadersByChannelId = new HashMap<>();

    public ChanValsReader(AmpcsTelemetrySourceConfig config, List<ChanValReadConfig> readerConfigs, OffsetDateTime targetScet) {
        this.config = config;

        for (ChanValReadConfig readConfig : readerConfigs) {
            channelValueReadersByChannelId.put(readConfig.channelId, new SingleChanValReader(config, readConfig, targetScet));
        }
    }

    public void read(CSVRecord row) {
        final String rowChannelId = row.get(config.getChannelIdFieldName());

        if (channelValueReadersByChannelId.containsKey(rowChannelId)) {
            channelValueReadersByChannelId.get(rowChannelId).read(row);
        }
    }

    public double getValueFor(String channelId) {
        if (! channelValueReadersByChannelId.containsKey(channelId)) {
            throw new IllegalArgumentException("No such channel ID in reader: " + channelId);
        }

        return channelValueReadersByChannelId.get(channelId).getValueClosestToTargetScet();
    }

    public Map<String, Double> getPairedValuesForSameScetNoEarlierThan(OffsetDateTime noEarlierThanScet, String... channelIds) {
        for (String channelId : channelIds) {
            if (! channelValueReadersByChannelId.containsKey(channelId)) {
                throw new IllegalArgumentException("No such channel ID in reader: " + channelId);
            }
        }

        // build a set containing all SCETs from any channel value in the list of channel IDs passed into this method
        Set<OffsetDateTime> allScets = new HashSet<>();
        Arrays.stream(channelIds)
                .map(channelValueReadersByChannelId::get)
                .forEach(reader -> allScets.addAll(reader.getAllScets()));

        // reduce the set to that of the intersection of all scets from the channel IDs passed into this method
        Arrays.stream(channelIds)
                .map(channelValueReadersByChannelId::get)
                .forEach(reader -> allScets.retainAll(reader.getAllScets()));

        Optional<OffsetDateTime> closestCommonScet = allScets.stream().filter(s -> (! s.isBefore(noEarlierThanScet))).min(Comparator.comparing(s -> Duration.between(noEarlierThanScet, s).abs()));

        Map<String, Double> results = new HashMap<>();

        if (! closestCommonScet.isPresent()) {
            logger.warn("No common SCET was found for " + channelIds);
            for (String channelId : channelIds) {
                results.put(channelId, Double.NaN);
            }

            return results;
        }


        for (String channelId : channelIds) {
            results.put(channelId, channelValueReadersByChannelId.get(channelId).getValueForScet(closestCommonScet.get()));
        }

        return results;
    }
}
