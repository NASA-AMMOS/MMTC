package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.AmpcsTelemetrySourceConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

public class TargetScetMultiChanValsReader extends ChanValsReader {
    protected static final Logger logger = LogManager.getLogger();

    public TargetScetMultiChanValsReader(AmpcsTelemetrySourceConfig config, List<ChanValReadConfig> readerConfigs, OffsetDateTime targetScet) {
        super(config);

        for (ChanValReadConfig readConfig : readerConfigs) {
            channelValueReadersByChannelId.put(readConfig.channelId, new SingleChanValReader(config, readConfig, targetScet));
        }
    }

    public double getValueClosestToTargetScetFor(String channelId) {
        if (! channelValueReadersByChannelId.containsKey(channelId)) {
            throw new IllegalArgumentException("No such channel ID in reader: " + channelId);
        }

        return channelValueReadersByChannelId.get(channelId).getValueClosestToTargetScet();
    }

    public ChanValsForSameScetResult getPairedValuesForSameScetNoEarlierThan(OffsetDateTime noEarlierThanScet, String... channelIds) {
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
            logger.warn("No common SCET was found for " + Arrays.toString(channelIds));
            for (String channelId : channelIds) {
                results.put(channelId, Double.NaN);
            }

            return new ChanValsForSameScetResult(null, results);
        }


        for (String channelId : channelIds) {
            results.put(channelId, channelValueReadersByChannelId.get(channelId).getValueForScet(closestCommonScet.get()));
        }

        return new ChanValsForSameScetResult(closestCommonScet.get(), results);
    }
}
