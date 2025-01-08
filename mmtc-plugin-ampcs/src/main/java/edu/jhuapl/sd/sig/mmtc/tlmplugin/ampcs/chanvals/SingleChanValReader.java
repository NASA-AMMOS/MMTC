package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.AmpcsTelemetrySourceConfig;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.commons.csv.CSVRecord;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class SingleChanValReader {
    private final String targetChannelId;
    private final OffsetDateTime targetScet;
    private final String numericalColToRead;
    private final AmpcsTelemetrySourceConfig config;

    private final Map<OffsetDateTime, Double> valuesByScet = new HashMap<>();
    private final boolean retainAll;
    private final Function<Double, Boolean> acceptanceCriterion;

    private OffsetDateTime closestScet = null;
    private Double closestValue = Double.NaN;

    public SingleChanValReader(AmpcsTelemetrySourceConfig config, ChanValReadConfig readConfig, OffsetDateTime targetScet) {
        this.config = config;
        this.targetChannelId = readConfig.channelId;
        this.targetScet = targetScet;
        this.numericalColToRead = readConfig.readField;
        this.retainAll = readConfig.retainAll;
        this.acceptanceCriterion = readConfig.acceptanceCriterion;
    }

    public void read(CSVRecord channelRow) {
        if (! channelRow.get(config.getChannelIdFieldName()).equals(targetChannelId)) {
            return;
        }

        // if there is somehow no value in the desired column (DN or EU), skip it
        final String chanVal = channelRow.get(numericalColToRead);
        if (chanVal.isEmpty()) {
            throw new IllegalStateException(String.format("Channel value for %s at %s (col %s) is empty", targetChannelId, targetChannelId, numericalColToRead));
        }

        final double newValue = Double.parseDouble(chanVal);

        // if the new value doesn't meet its criterion (e.g. a TDT value that is not at or later than a certain value), then skip it
        if (! acceptanceCriterion.apply(newValue)) {
            return;
        }

        final OffsetDateTime newScet = TimeConvert.parseIsoDoyUtcStr(channelRow.get(config.getChannelScetFieldName()));

        if (closestScet == null || (Duration.between(newScet, targetScet).abs().compareTo(Duration.between(closestScet, targetScet).abs()) < 0)) {
            closestScet = newScet;
            closestValue = newValue;
            valuesByScet.put(newScet, newValue);
        }

        if (retainAll) {
            valuesByScet.put(newScet, newValue);
        }
    }

    public Double getValueClosestToTargetScet() {
        return closestValue;
    }

    public Set<OffsetDateTime> getAllScets() {
        return valuesByScet.keySet();
    }

    public Double getValueForScet(OffsetDateTime scet) {
        return valuesByScet.get(scet);
    }
}
