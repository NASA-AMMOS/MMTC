package edu.jhuapl.sd.sig.mmtc.util;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;

import java.time.OffsetDateTime;

/**
 * This class represents a CCSDS Day Segmented (CDS) Time Code, which includes
 * a series of segments to define a particular point in time, where each
 * segment represents a binary counter of a certain width or precision.
 *
 * @see <a href="https://public.ccsds.org/Pubs/301x0b4e1.pdf">CCSDS 301.0-B-4 Time Code Formats</a>
 *
 */
public class CdsTimeCode {
    private static final double SEC_PER_DAY      = 86400.0;
    private static final double MILLISEC_PER_SEC = 1000.0;
    private static final double MICROSEC_PER_SEC = 1000000.0;

    /**
     * Used to identify the length of the sub-millisecond segment.
     */
    public enum Resolution {
        /**
         * The sub-millisecond segment represents a resolution of one
         * microsecond.
         */
        MICROSECOND,

        /**
         * The sub-millisecond segment represents a resolution of one tenth of
         * one microsecond.
         */
        TENTH_MICROSECOND,
    }

    private int day;
    private int msOfDay;
    private int subMs;
    private Resolution resolution;

    /**
     * Create an empty CDS time code.
     */
    public CdsTimeCode() {
        this(0, 0, 0);
    }

    /**
     * Create a CDS time code with the specified day, millisecond of day, and
     * sub-millisecond of day.
     *
     * The epoch is set to the standard 1958 epoch, and the resolution is set
     * to 1/10 microsecond.
     *
     * @param day the day number
     * @param ms the millisecond of day
     * @param subMs the sub-millisecond of day (if applicable)
     */
    public CdsTimeCode(int day, int ms, int subMs) {
        this(day, ms, subMs, Resolution.TENTH_MICROSECOND);
    }

    /**
     * Create a CDS time code with the specified day, millisecond of day, and
     * sub-millisecond of day, resolution, and epoch.

     * @param day the day number
     * @param ms the millisecond of day
     * @param subMs the sub-millisecond of day (if applicable)
     * @param resolution the length of the sub-millisecond segment
     */
    private CdsTimeCode(int day, int ms, int subMs, Resolution resolution) {
        this.day = day;
        this.msOfDay = ms;
        this.subMs = subMs;
        this.resolution = resolution;
    }

    /**
     * Create a CDS time from a specified string value.
     *
     * The epoch is set to the standard 1958 epoch, and the resolution is set
     * to 1/10 microsecond.
     *
     * @param strCdsTime the time code given as a string
     */
    public CdsTimeCode(String strCdsTime) {
        this(strCdsTime, Resolution.TENTH_MICROSECOND);
    }

    /**
     * Create a CDS time code from a specified string value, resolution, and
     * epoch.
     *
     * @param strCdsTime the time code given as a string
     * @param resolution the length of the sub-millisecond segment
     */
    private CdsTimeCode(String strCdsTime, Resolution resolution) {
        String[] segments = strCdsTime.split("::");

        if (segments.length != 3) {
            throw new IllegalArgumentException("Invalid day segmented time string: " + strCdsTime);
        }

        this.day = Integer.parseInt(segments[0]);
        this.msOfDay = Integer.parseInt(segments[1]);
        this.subMs = Integer.parseInt(segments[2]);
        this.resolution = resolution;
    }

    /**
     * @return the day of the CDS epoch
     */
    public Integer getDayOfEpoch() { return day; }

    /**
     * @return the second of the day
     */
    public Integer getMsOfDay() { return msOfDay; }

    /**
     * @return the millisecond of the second of the day
     */
    public Integer getSubMillisec() { return subMs; }

    /**
     * @return the resolution of the time code
     */
    public Resolution getResolution() {
        return resolution;
    }

    /**
     * Get the number of seconds difference between this time code and another
     *
     * Positive values imply 'this' is earlier than 'other'
     * Negative values imply 'this' is later than 'other'
     *
     * @param other the other time code
     * @return the difference between the two time codes in seconds
     * @throws MmtcException if an undefined resolution is provided (this should never happen)
     */
    public double getDeltaSeconds(CdsTimeCode other) throws MmtcException {
        double thisSeconds = this.day * SEC_PER_DAY + this.msOfDay / MILLISEC_PER_SEC;
        double otherSeconds = other.getDayOfEpoch() * SEC_PER_DAY + other.getMsOfDay() / MILLISEC_PER_SEC;

        switch (this.resolution) {
            case MICROSECOND:
                thisSeconds += this.subMs / MICROSEC_PER_SEC;
                break;
            case TENTH_MICROSECOND:
                thisSeconds += this.subMs / MICROSEC_PER_SEC / 10;
                break;
            default:
                throw new MmtcException("ERROR: Invalid resolution in 1st CDS Time Code.");
        }

        switch (other.getResolution()) {
            case MICROSECOND:
                otherSeconds += other.getSubMillisec() / MICROSEC_PER_SEC;
                break;
            case TENTH_MICROSECOND:
                otherSeconds += other.getSubMillisec() / MICROSEC_PER_SEC / 10;
                break;
            default:
                throw new MmtcException("ERROR: Invalid resolution in 2nd CDS Time Code.");
        }

        return Math.abs(thisSeconds - otherSeconds);
    }

    /**
     * @return true if the time code is null
     */
    public boolean isNull() {
        return (this.day == 0) && (this.msOfDay == 0) && (this.subMs == 0);
    }

    /**
     * Convert the CDS time code to the standard string format.
     *
     * @return the time code as a string delimited by '::'
     */
    @Override
    public String toString() {
        return String.format("%d::%d::%04d", day, msOfDay, subMs);
    }

    /**
     * Converts a CdsTimeCode object to a Java OffsetDateTime object.
     *
     * @return the CDS time as an OffsetDateTime object
     * @throws TimeConvertException if the time format could not be converted
     */
    public OffsetDateTime toTime() throws TimeConvertException {
        String utcStr = TimeConvert.cdsToIsoUtc(this.day, this.msOfDay, this.subMs);
        return TimeConvert.parseIsoDoyUtcStr(utcStr);
    }
}
