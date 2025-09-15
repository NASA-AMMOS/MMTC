package edu.jhuapl.sd.sig.mmtc.tlm.persistence.model;

import com.google.common.collect.Range;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.OffsetDateTimeRange;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class OffsetDateTimeRangeEntity {
    // start is closed bound
    private long startSec;
    private int startNanoOfSec;

    // stop is open bound
    private long stopSec;
    private int stopNanoOfSec;

    public OffsetDateTimeRangeEntity() {}

    public long getStartSec() {
        return startSec;
    }

    public void setStartSec(long startSec) {
        this.startSec = startSec;
    }

    public int getStartNanoOfSec() {
        return startNanoOfSec;
    }

    public void setStartNanoOfSec(int startNanoOfSec) {
        this.startNanoOfSec = startNanoOfSec;
    }

    public long getStopSec() {
        return stopSec;
    }

    public void setStopSec(long stopSec) {
        this.stopSec = stopSec;
    }

    public int getStopNanoOfSec() {
        return stopNanoOfSec;
    }

    public void setStopNanoOfSec(int stopNanoOfSec) {
        this.stopNanoOfSec = stopNanoOfSec;
    }

    public OffsetDateTimeRange toOffsetDateTimeRange() {
        return new OffsetDateTimeRange(
                Instant.ofEpochSecond(startSec).plusNanos(startNanoOfSec).atOffset(ZoneOffset.UTC),
                Instant.ofEpochSecond(stopSec).plusNanos(stopNanoOfSec).atOffset(ZoneOffset.UTC)
        );
    }

    public static OffsetDateTimeRangeEntity fromOffsetDateTimeRange(OffsetDateTimeRange offsetDateTimeRange) {
        final Instant startInstant = offsetDateTimeRange.getStart().toInstant();
        final Instant stopInstant = offsetDateTimeRange.getStop().toInstant();

        final OffsetDateTimeRangeEntity odtre = new OffsetDateTimeRangeEntity();

        odtre.setStartSec(startInstant.getEpochSecond());
        odtre.setStartNanoOfSec(startInstant.getNano());

        odtre.setStopSec(stopInstant.getEpochSecond());
        odtre.setStopNanoOfSec(stopInstant.getNano());

        return odtre;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OffsetDateTimeRangeEntity that = (OffsetDateTimeRangeEntity) o;
        return startSec == that.startSec && startNanoOfSec == that.startNanoOfSec && stopSec == that.stopSec && stopNanoOfSec == that.stopNanoOfSec;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startSec, startNanoOfSec, stopSec, stopNanoOfSec);
    }
}
