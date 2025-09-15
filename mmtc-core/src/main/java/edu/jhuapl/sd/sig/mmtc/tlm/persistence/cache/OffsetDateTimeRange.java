package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import com.google.common.collect.Range;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.model.OffsetDateTimeRangeEntity;

import java.time.OffsetDateTime;
import java.util.Objects;

public class OffsetDateTimeRange implements Comparable<OffsetDateTimeRange> {
    // closed bound
    private OffsetDateTime start;

    // open bound
    private OffsetDateTime stop;

    public OffsetDateTimeRange(OffsetDateTime start, OffsetDateTime stop) {
        if (! start.isBefore(stop)) {
            throw new IllegalArgumentException(String.format("Invalid start and stop times: %s and %s", start, stop));
        }

        this.start = start;
        this.stop = stop;
    }

    public Range<OffsetDateTime> toRange() {
        return Range.closedOpen(start, stop);
    }

    public static OffsetDateTimeRange fromRange(Range<OffsetDateTime> range) {
        return new OffsetDateTimeRange(range.lowerEndpoint(), range.upperEndpoint());
    }

    public OffsetDateTime getStart() {
        return start;
    }

    public OffsetDateTime getStop() {
        return stop;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OffsetDateTimeRange that = (OffsetDateTimeRange) o;
        return Objects.equals(start, that.start) && Objects.equals(stop, that.stop);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, stop);
    }

    @Override
    public int compareTo(OffsetDateTimeRange other) {
        if (! this.start.equals(other.start)) {
            return this.start.compareTo(other.start);
        } else {
            return this.stop.compareTo(other.stop);
        }
    }

    @Override
    public String toString() {
        return "OffsetDateTimeRange{" +
                "start=" + start +
                ", stop=" + stop +
                '}';
    }
}
