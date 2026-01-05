package edu.jhuapl.sd.sig.mmtc.util;

import java.lang.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Year;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationTarget;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationMetricsConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spice.basic.*;

/**
 * <P>The TimeConvert class is a static class that provides functions to compute various time-related values.
 *
 * Most of the functions in this class rely on the JPL/NAIF SPICE library
 * to perform their operations. The SPICE library is imported using the
 * JNISpice Java interface to their CSPICE library. Unless loaded already in
 * the calling application, the <code>loadSpiceLib()</code> function should be
 * called to load CSPICE and the necessary SPICE kernels before any
 * other functions in this class are called. It needs to be called only once.
 *
 * <P>The NAIF SPICE documentation is extensive, available on-line, and provides
 * a full description of SPICE and its usage.
 *
 * @see <a href="https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/req/sclk.html">SCLK</a>
 * @see <a href="https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/index.html">CSPICE Toolkit</a>
 * @see <a href="https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/FORTRAN/req/naif_ids.html">NAIF Integer ID Codes</a>
 * @see <a href="https://public.ccsds.org/Pubs/301x0b4e1.pdf">CCSDS 301.0-B-4 Time Code Formats</a>
 *
 * <P>The following SPICE kernels need to be loaded before most of the functions in
 * this class can be called:
 *
 * <ul>
 *     <li> NAIF Generic Leap Seconds kernel (*.tls)
 *     <li> Mission-Specific Spacecraft SCLK kernel (*.tsc)
 * </ul>
 */
public class TimeConvert {
    private static final Logger logger = LogManager.getLogger();

    /* Container for a leap second date pair. The date (leapSecOccurrence) is the datetime (in UTC) when the leap
     * second became (or becomes) effective.  The new value is the delta in ET from that day forward.
     */
    public static class LeapSecond {
        public OffsetDateTime leapSecOccurrence;
        public int leapSecDeltaEt;

        LeapSecond(OffsetDateTime leapSecOccurrence, int leapSecDeltaEt) {
            this.leapSecOccurrence = leapSecOccurrence;
            this.leapSecDeltaEt = leapSecDeltaEt;
        }
    }

    // Regex for ISO Day Of Year (DOY) format (yyyy-doyThh:mm:ss.sssssssss).
    private static final Pattern ISO_UTC_DOY_REGEX = Pattern.compile("^(\\d{4})-(\\d{3})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d{1,9})?[Zz]?$");

    // Formatter for ISO Day Of Year (DOY) format. Assumes UTC.
    public static final DateTimeFormatter ISO_UTC_DOY_FORMAT = DateTimeFormatter.ofPattern("yyyy-DDD'T'HH:mm:ss.SSSSSSSSS").withZone(ZoneOffset.UTC);
    public static final String ISO_UTC_DOY_FORMAT_NO_SUBSECONDS = "yyyy-DDD'T'HH:mm:ss.";

    // The length of an ISO UTC DOY format date/time string defined above (without the ' ' surrounding the 'T').
    public static final int ISO_UTC_DOY_FORMAT_LEN = 27;

    public static final long CDS_EPOCH_YEAR      = 1958;
    public static final int  SECONDS_PER_DAY     = 86400;
    public static final int  SECONDS_PER_HOUR    = 3600;
    public static final int  MSEC_PER_SECOND     = 1000;
    public static final int  NS_PER_SECOND       = 1000_000_000;

    // The maximum length of a SPICE file specification. (SPICE does provide a way to load larger file
    // specifications using the concatenation character '+', but this is not supported here.)
    public static final int MAX_SPICE_FILENAME_LEN = 255;

    /* Indicates if the SPICE library has been loaded. */
    private static boolean spiceLibIsLoaded = false;

     /**
     * Converts an ISO DOY format calendar string (yyyy-doyThh:mm:ss.ssssss) to a Java OffsetDateTime object.
     *
     * @param utc IN:the UTC time in yyyy-doyThh:mm:ss.sssssssss format
     * @return the time as an OffsetDateTime object
     */
    public static OffsetDateTime parseIsoDoyUtcStr(String utc) {
        final int ISOUTCDOYLEN = "yyyy-doyThh:mm:ss.SSSSSSSSS".length();

        // Make sure that the input UTC string contains fractions of second to nine decimal places.
        // If it does not, pad with trailing zeroes.
        String utcStr;
        if (! utc.contains(".")) {
            utcStr = utc + ".000000000";
        } else if (utc.length() < ISOUTCDOYLEN) {
            utcStr = StringUtils.rightPad(utc, ISOUTCDOYLEN, "0");
        } else if (utc.length() > ISOUTCDOYLEN) {
            throw new IllegalArgumentException("Cannot convert times with higher-than-nanosecond-precision: " + utc);
        } else {
            utcStr = utc;
        }

        LocalDateTime local = LocalDateTime.parse(utcStr, ISO_UTC_DOY_FORMAT);
        return local.atOffset(ZoneOffset.UTC);
    }


    /**
     * Converts a Java OffsetDateTime object to an ISO UTC DOY calendar string.
     * @param t IN:the time object
     * @return an ISO UTC calendar string
     */
    public static String timeToIsoUtcString(OffsetDateTime t) {
        return timeToIsoUtcString(t, 6);
    }

    public static String timeToIsoUtcString(OffsetDateTime t, int subsecondPrecision) {
        if (! (subsecondPrecision >= 1)) {
            throw new IllegalArgumentException("Subsecond precision must be 1 or greater");
        }

        String subsecondFormat = "";
        for (int i = 0; i < subsecondPrecision; i++) { subsecondFormat += "S"; }

        return t.format(DateTimeFormatter.ofPattern(ISO_UTC_DOY_FORMAT_NO_SUBSECONDS + subsecondFormat).withZone(ZoneOffset.UTC));
    }

    /**
     * Loads the SPICE JNI library.
     *
     * @throws TimeConvertException when System.loadLibrary() fails
     */
    public static synchronized void loadSpiceLib() throws TimeConvertException {
        if (! spiceLibIsLoaded) {
            try {
                System.loadLibrary("JNISpice");
                spiceLibIsLoaded = true;
            } catch (Exception e) {
                throw new TimeConvertException("Error initializing SPICE library", e);
            }
        }
    }

    /**
     * Indicates if the SPICE library has been loaded.
     * @return true if the SPICE library has already been loaded
     */
    public static boolean spiceLibLoaded() {
        return spiceLibIsLoaded;
    }


    /**
     * Load the indicated SPICE kernels.
     *
     * @param kernelsToLoad  IN a Map of strings that contain the names of SPICE kernels to load
     * @throws TimeConvertException when KernelDatabase.load() fails
     */
    public static void loadSpiceKernels(Map<String, String> kernelsToLoad) throws TimeConvertException {
        try {
            for (Map.Entry<String, String> entry : kernelsToLoad.entrySet()) {
                if (entry.getKey().length() > MAX_SPICE_FILENAME_LEN) {
                    String kernelFilespecMsg = "Kernel file specification '" + entry.getKey() +
                            "' exceeds the maximum length of " + Integer.toString(MAX_SPICE_FILENAME_LEN) +
                            " characters and cannot be loaded.";
                    throw new TimeConvertException(kernelFilespecMsg);
                }

                KernelDatabase.load(entry.getKey());
            }
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Unable to load SPICE kernels " + e.getMessage(), e);
        }
    }


    /**
     * Loads a single SPICE kernel.
     *
     * @param path the kernel to load
     * @throws TimeConvertException if the kernel could not be loaded
     */
    public static void loadSpiceKernel(String path) throws TimeConvertException {
        try {
            KernelDatabase.load(path);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Unable to load SPICE kernel: " + path + " : " + e.getMessage(), e);
        }
    }


    /**
     * Unload the indicated SPICE kernels.
     *
     * @param kernelsToUnload  IN a Map of strings that contain the names of SPICE kernels to unload
     * @throws TimeConvertException if a kernel or kernels could not be removed from the SPICE kernel pool
     */
    public static void unloadSpiceKernels(Map<String, String> kernelsToUnload) throws TimeConvertException {
        try {
            for (Map.Entry<String, String> entry : kernelsToUnload.entrySet()) {
                KernelDatabase.unload(entry.getKey());
            }
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Unable to unload SPICE kernels: " + e.getMessage(), e);
        }
    }

    /**
     * Unload all SPICE kernels.
     */
    public static void unloadSpiceKernels() {
        KernelDatabase.clear();
    }

    /**
     * Converts a CCSDS Day Segmented Time (CDS) code formatted time value
     * to an ISO UTC calendar string. CDS is the time format used by the
     * NASA Deep Space Network (DSN) to indicate Earth Received Time (ERT) in
     * the Ground Receipt Header (GRH) that it attaches to all received
     * telemetry frames.
     * <p>
     * Described in CCSDS 301.0-B-4 Time Code Formats, CDS has the form:
     * {@literal <days>::<ms of day>::<submillisecond>} from the
     * 1958 epoch of 01-JAN-1958 00:00h UTC. In this case of this function,
     * the submillisecond value is assumed to be in tenths of a microsecond.
     * UTC has the form yyyy-doyThh:mm:ss.ssssss.
     *
     * @param cdsDay      IN  day of 1958 epoch
     * @param cdsMsOfDay  IN  millisecond of the day
     * @param cdsSubMs    IN  submillisecond of millisecond (tenth of microsecond of millisec.)
     * @return UTC time as an ISO calendar string
     * @throws TimeConvertException when any error occurs
     */
    public static String cdsToIsoUtc(Integer cdsDay, Integer cdsMsOfDay, Integer cdsSubMs) throws TimeConvertException {
        long   utcYear     = CDS_EPOCH_YEAR;
        int    utcDoY      = cdsDay;
        int    numLeapDays = Year.isLeap(CDS_EPOCH_YEAR) ? 1:0;
        String utc;

        try {
            while (numLeapDays + 365 <= utcDoY) {
                utcYear++;
                utcDoY -= numLeapDays + 365;
                numLeapDays = Year.isLeap(utcYear) ? 1 : 0;
            }

            /* Adjust day of year count from CDS 0-base to UTC 1-base. */
            utcDoY++;

            int secondOfEpochDay = cdsMsOfDay / 1000;

            int secondOfDay = 0;
            double fracsec = 0.;
            int hour = 0;
            int minute = 0;
            double second = 0.;


            if (secondOfEpochDay >= SECONDS_PER_DAY) {
                hour = 23;
                minute = 59;
            } else {
                hour = secondOfEpochDay / SECONDS_PER_HOUR;
                minute = (secondOfEpochDay / 60) % 60;
            }

            secondOfDay = secondOfEpochDay - ((hour * 60) + minute) * 60;
            fracsec = (cdsMsOfDay / 1000.0) - secondOfEpochDay + (cdsSubMs / 10000000.0);

            second = secondOfDay + fracsec;

            /* Create formatting of double-type seconds value. */
            DecimalFormat secfmt = new DecimalFormat("00.000000");
            secfmt.setRoundingMode(RoundingMode.HALF_UP);
            String secStr = secfmt.format(second);

            utc = String.format("%4d-%03dT%02d:%02d:%s", utcYear, utcDoY, hour, minute, secStr);
        }
        catch (Exception e) {
            throw new TimeConvertException("Error converting CDS time to UTC.", e);
        }

        return utc;
    }


    /**
     * Converts a CCSDS Day Segmented Time (CDS) code formatted time value
     * to an ISO UTC calender string. CDS is the time format used by the
     * NASA Deep Space Network (DSN) to indicate Earth Received Time (ERT) in
     * the Ground Receipt Header (GRH) that it attaches to all received
     * telemetry frames.
     * <p>
     * Described in CCSDS 301.0-B-4 Time Code Formats, CDS has the form:
     * {@literal <days>::<ms of day>::<submillisecond>} from the
     * 1958 epoch of 01-JAN-1958 00:00h UTC. In this case of this function,
     * the submillisecond value is assumed to be in tenths of a microsecond.
     * UTC has the form yyyy-doyThh:mm:ss.ssssss.
     *
     * @param cds IN CDS time in form {@literal <days>::<ms of day>::<submillisecond>}
     * @return UTC time as an ISO calendar string
     * @throws TimeConvertException when any error occurs
     */
    public static String cdsToIsoUtc(String cds)
            throws TimeConvertException {

        String delim = "[:]";
        String[] fields = cds.split(delim);

        if (fields.length != 5) {
            throw new TimeConvertException("Invalid CDS time string.");
        }

        /* Split the CDS time string into its constituent parts and then
         * pass them to the other version of this function.
         */
        Integer cdsDay     = Integer.valueOf(fields[0]);
        Integer cdsMsOfDay = Integer.valueOf(fields[2]);
        Integer cdsSubMs   = Integer.valueOf(fields[4]);

        String utc = cdsToIsoUtc(cdsDay, cdsMsOfDay, cdsSubMs);

        return utc;
    }


    /**
     * Converts a CCSDS Day Segmented Time (CDS) code formatted time value
     * to an ISO UTC calendar string. CDS is the time format used by the
     * NASA Deep Space Network (DSN) to indicate Earth Received Time (ERT) in
     * the Ground Receipt Header (GRH) that it attaches to all received
     * telemetry frames.
     * <p>
     * Described in CCSDS 301.0-B-4 Time Code Formats, CDS has the form:
     * {@literal <days>::<ms of day>::<submillisecond>} from the
     * 1958 epoch of 01-JAN-1958 00:00h UTC. In this case of this function,
     * the submillisecond value is assumed to be in tenths of a microsecond.
     * UTC has the form yyyy-doyThh:mm:ss.ssssss.
     *
     * @param cds IN CDS time in form CdsTimeCode form
     * @return UTC time as an ISO calendar string
     * @throws TimeConvertException when any error occurs
     */
    public static String cdsToIsoUtc(CdsTimeCode cds) throws TimeConvertException {
        return cdsToIsoUtc(cds.getDayOfEpoch(), cds.getMsOfDay(), cds.getSubMillisec());
    }


    /**
     * Converts an ISO UTC calendar string of the form yyyy-doyThh:mm:ss.ssssss to a CDS Time
     * code format. Assumes that the CDS submilliseconds field is tenths-of-microseconds.
     *
     * @param utcStr the ISO UTC calendar string
     * @return the CDS Time Code object
     * @throws TimeConvertException if the UTC string could not be converted to CDS
     */
    public static CdsTimeCode isoUtcToCds(String utcStr) throws TimeConvertException {

        Matcher matcher = ISO_UTC_DOY_REGEX.matcher(utcStr);
        if (!matcher.matches()) {
            throw new TimeConvertException("Invalid ISO UTC string " + utcStr +
                    " to isoUtcToCds(). Form must be 'yyyy-doyThh:mm:ss.sssssssss'. " +
                    "Note that the fraction of second may contain no more than 9 digits.");
        }

        // Parse the ISO date string into year, doy, hh, mm, ss.ssssss
        
        String yearStr = matcher.group(1);
        String dayStr = matcher.group(2);
        String hhStr = matcher.group(3);
        String mmStr = matcher.group(4);
        String secStr = matcher.group(5);
        String subsecStr = matcher.group(6);

        int year = Integer.parseInt(yearStr);
        if ((year < 2000) || (year > 2100)) {
            throw new TimeConvertException("Invalid year value " + yearStr + " in ISO string " + utcStr + " to isoUtcToCds().");
        }

        int doy = Integer.parseInt(dayStr);
        if ((doy < 1) || (doy > 366)) {
            throw new TimeConvertException("Invalid doy value " + dayStr + " in ISO string " + utcStr + " to isoUtcToCds().");
        }

        int hour = Integer.parseInt(hhStr);
        if ((hour < 0) || (hour > 23)) {
            throw new TimeConvertException("Invalid hour value " + hhStr + " in ISO string " + utcStr + " to isoUtcToCds().");
        }

        int minute = Integer.parseInt(mmStr);
        if ((minute < 0) || (minute > 59)) {
            throw new TimeConvertException("Invalid minute value " + mmStr + " in ISO string " + utcStr + " to isoUtcToCds().");
        }

        int seconds = Integer.parseInt(secStr);
        if ((seconds < 0) || (seconds > 60)) {
            throw new TimeConvertException("Invalid seconds value " + secStr + " in ISO string " + utcStr + " to isoUtcToCds().");
        }

        int milliseconds = 0;
        int microseconds = 0;
        if (subsecStr != null) {
            // subsecStr is the regex group (\.\d{1,6}) representing the subsecond portion of the ISO UTC string,
            // and we need to extract the millisecond and submillisecond values from it.
            // to avoid floating-point errors from the usual methods (parseDouble, multiply by 1000, cast to int for the milliseconds,
            // subtract the cast value from the original uncast value for the submilliseconds, discover that the submilliseconds is
            // wrong because of accumulated floating point errors from the multiplication and subtraction), we'll work directly on the
            // string and do no floating-point operations. this is OK because the string format is strict and has no locale variations.

            // strip the leading period and right-pad to the full 6-digit length
            String extendedSubsecStr = String.format("%1$-6s", subsecStr.substring(1)).replace(" ", "0");

            // extract the milliseconds substring and the submilliseconds substring
            milliseconds = Integer.parseInt(extendedSubsecStr.substring(0, 3));
            microseconds = Integer.parseInt(extendedSubsecStr.substring(3, 6));
        }

        int yearOfCdsEpoch = year - (int)CDS_EPOCH_YEAR;

        // Compute the number of leap years prior to the current year since the 1958 CDS epoch and add
        // up the total leap days.
        int numLeapDays = 0;
        for (int y=(int)CDS_EPOCH_YEAR; y<year; y++) {
            if (Year.isLeap(y)) {
                numLeapDays++;
            }
        }

        // Total up the days since 1 Jan 1958. Subtract 1 because CDS day counts are zero-based.
        int daysOfEpoch = (yearOfCdsEpoch * 365) + numLeapDays + doy - 1;

        // Compute the milliseconds of the day.
        int msOfDay = ((hour * SECONDS_PER_HOUR) + (minute * 60) + seconds) * MSEC_PER_SECOND + milliseconds;

        // CdsTimeCode defaults to treating the submilliseconds value as tenths-of-microseconds.
        // Convert the microseconds value extracted earlier.
        int tenthsOfMicroseconds = microseconds * 10;

        return new CdsTimeCode(daysOfEpoch, msOfDay, tenthsOfMicroseconds);
    }


    /**
     * Converts a CCSDS Day Segmented Time (CDS) code formatted time value
     * to SPICE Ephemreis Time (ET) which is also Barycentric Dynamical Time
     * (TDB) in seconds of the J2000 epoch. CDS is the time format used by the
     * NASA Deep Space Network (DSN) to indicate Earth Received Time (ERT) in
     * the Ground Receipt Header (GRH) that it attaches to all received
     * telemetry frames.
     * <p>
     * Described in CCSDS 301.0-B-4 Time Code Formats, CDS has the form:
     * {@literal <days>::<ms of day>::<submillisecond>} from the
     * 1958 epoch of 01-JAN-1958 00:00h UTC. In this case of this function,
     * the submillisecond is in tenths of a microsecond.
     *
     * @param cdsDay      IN  day of 1958 epoch
     * @param cdsMsOfDay  IN  millisecond of the day
     * @param cdsSubMs    IN  submillisecond of millisecond (tenth of microsecond of millisec.)
     * @return ephemeris time (et)
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double cdsToEt(Integer cdsDay, Integer cdsMsOfDay, Integer cdsSubMs)
            throws TimeConvertException {

        try {
            /* Convert the CDS time to a UTC calendar string. */
            String utc = cdsToIsoUtc(cdsDay, cdsMsOfDay, cdsSubMs);

            double et = CSPICE.str2et(utc);
            return et;

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting CDS to ET: " + e.getMessage(), e);
        }
    }


    /**
     * Converts a CCSDS Day Segmented Time (CDS) code formatted time value
     * to Terrestrial Dynamical Time in seconds of the J2000 epoch. CDS is
     * the time format used by the NASA Deep Space Network (DSN) to indicate
     * Earth Received Time (ERT) in the Ground Receipt Header (GRH) that it
     * attaches to all received telemetry frames.
     * <p>
     * Described in CCSDS 301.0-B-4 Time Code Formats, CDS has the form:
     * {@literal <days>::<ms of day>::<submillisecond>} from the
     * 1958 epoch of 01-JAN-1958 00:00h UTC. In this case of this function,
     * the submillisecond value is assumed to be in tenths of a microsecond.
     *
     * @param cdsDay      IN  day of 1958 epoch
     * @param cdsMsOfDay  IN  millisecond of the day
     * @param cdsSubMs    IN  submillisecond of millisecond (tenth of microsecond of millisec.)
     * @return TDT
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double cdsToTdt(Integer cdsDay, Integer cdsMsOfDay, Integer cdsSubMs)
        throws TimeConvertException {

        double tdt;

        try {
            double et = cdsToEt(cdsDay, cdsMsOfDay, cdsSubMs);
            tdt = CSPICE.unitim(et, "ET", "TDT");

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting CDS to TDT: " + e.getMessage(), e);
        }

        return tdt;

    }


    /**
     * Converts a CCSDS Day Segmented Time (CDS) code formatted time value
     * to Terrestrial Dynamical Time in calendar string form. CDS is
     * the time format used by the NASA Deep Space Network (DSN) to indicate
     * Earth Received Time (ERT) in the Ground Receipt Header (GRH) that it
     * attaches to all received telemetry frames.
     * <p>
     * Described in CCSDS 301.0-B-4 Time Code Formats, CDS has the form:
     * {@literal <days>::<ms of day>::<submillisecond>} from the
     * 1958 epoch of 01-JAN-1958 00:00h UTC. In this case of this function,
     * the submillisecond value is assumed to be in tenths of a microsecond.
     *
     * This function outputs the TDT as a string of the form:
     *  dd-MMM-yyyy-hh:mm:ss.ssssss
     *  e.g., "19-DEC-2017-05:50:08.956750"
     *
     * This format is compatible with SCLK kernels.
     *
     * @param cdsDay      IN  day of 1958 epoch
     * @param cdsMsOfDay  IN  millisecond of the day
     * @param cdsSubMs    IN  submillisecond of millisecond (tenth of microsecond of millisec.)
     * @return TDT in calendar string form
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static String cdsToTdtStr(Integer cdsDay, Integer cdsMsOfDay, Integer cdsSubMs)
            throws TimeConvertException {

        String tdtStr;

        try {
            /* Convert the CDS time to a UTC calendar string. */
            String utc = cdsToIsoUtc(cdsDay, cdsMsOfDay, cdsSubMs);

            /* Convert UTC to ET. */
            double et = CSPICE.str2et(utc);

            /* Convert ET to TDT in calendar string form. */
            tdtStr = CSPICE.timout(et, "DD-MON-YYYY-HR:MN:SC.######  ::TDT ::RND");

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting CDS to TDT:  " + e.getMessage(), e);
        }

        return tdtStr;
    }


    /**
     * Converts a Terrestrial Dynamical Time (TDT) calendar string to its
     * numeric seconds of J2000 epoch equivalent. The form of the input
     * calendar string. The form of the returned calendar string:
     *  dd-MMM-yyyy-hh:mm:ss.ssssss
     *  e.g., "19-DEC-2017-05:50:08.956750"
     *
     * @param tdtCalStr  IN the TDT in calendar string form
     * @return TDT in numeric seconds of J2000 epoch form
     * @throws TimeConvertException when there is a SPICE error
     */
    public static Double tdtCalStrToTdt(String tdtCalStr) throws TimeConvertException {

        Double tdt;

        try {
            double et = CSPICE.str2et(tdtCalStr + " TDT");
            tdt = etToTdt(et);

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting a TDT String to numeric TDT:  " + e.getMessage(), e);
        }

        return tdt;
    }


    /**
     * Converts a Terrestrial Dynamical Time (TDT) in numeric seconds of J2000
     * epoch to the equivalent calendar string form.
     * The form of the returned calendar string:
     *  dd-MMM-yyyy-hh:mm:ss.ssssss
     *  e.g., "19-DEC-2017-05:50:08.956750"
     *
     * @param tdt  IN the TDT in seconds of J2000 epoch form
     * @return TDT in calendar string form
     * @throws TimeConvertException when there is a SPICE error
     */
    public static String tdtToTdtCalStr(Double tdt) throws TimeConvertException {
        return tdtToTdtCalStr(tdt, 6);

    }

    public static String tdtToTdtCalStr(Double tdt, int subsecPrecision) throws TimeConvertException {
        String tdtStr;

        if (Double.isNaN(tdt)) {
            return "";
        }

        try {

            double et = CSPICE.unitim(tdt, "TDT", "ET");
            String subsecStr = String.join("", Collections.nCopies(subsecPrecision, "#"));
            tdtStr = CSPICE.timout(et, String.format("DD-MON-YYYY-HR:MN:SC.%s  ::TDT ::RND", subsecStr));

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting a TDT to a TDT string:  " + e.getMessage(), e);
        }

        return tdtStr;
    }


    /**
     * Converts a TDT string to a UTC ISO day of year calendar string.
     *
     * @param tdtCalStr     IN the TDT as a calendar string
     * @param precision          IN the number of digits of fractional seconds
     * @return the UTC string
     * @throws TimeConvertException if the TDT could not be converted to UTC
     */
    public static String tdtCalStrToUtc(String tdtCalStr, Integer precision) throws TimeConvertException {

        String utc;

        try {

            double et  = CSPICE.str2et(tdtCalStr + " TDT");
            utc        = CSPICE.et2utc(et, "ISOD", precision);

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting a TDT String to UTC:  " + e.getMessage(), e);
        }

        return utc;
    }


    /**
     * Converts Ephermis Time (ET), which is equivalent to Barycentric
     * Dynamical Time (TDB), to Terrestrial Dynamical Time (TDT) in seconds of
     * the J2000 epoch.
     *
     * @param et  IN the ephemeris time to convert
     * @return TDT in seconds of the J2000 epoch
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double etToTdt(Double et) throws TimeConvertException {

        double tdt;

        try {
                tdt = CSPICE.unitim(et, "ET", "TDT");

            } catch (SpiceErrorException e) {
                throw new TimeConvertException("Error converting ET to TDT: " + e.getMessage(), e);
            }

        return tdt;
    }


    /**
     * Converts a UTC value in ISO calendar stirng form to Ephermis Time (ET), which
     * is equivalent to Barycentric Dynamical Time (TDB), in seconds of the
     * J2000 epoch.
     *
     * @param utc  IN the UTC time to convert
     * @return the time in ephemeris time form
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double utcToEt(String utc) throws TimeConvertException {

        double et;
        try {
            et = CSPICE.str2et(utc);

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting UTC value " + utc + " to ET:  " + e.getMessage(), e);
        }

        return et;
    }


    /**
     * Returns a list of the leap seconds added since January 1, 1972 and when they were added.
     * This method reads these values from data provided in the Leap Seconds Kernel, which must already
     * have been loaded.
     *
     * @return a list of the leap seconds and the dates they were added
     * @throws TimeConvertException when unable to obtain the list of leap seconds
     */
    public static List<LeapSecond> parseLeapSeconds() throws TimeConvertException {
        double[] leapSecData;
        int      rowDeltaAtUtcIndex;
        double   dateval;
        int      leapSecDeltaEt;

        List<LeapSecond> allLeapSeconds = new ArrayList<>();

        try {
            /* Get the leap seconds array from SPICE. The gdpool() function returns an array of
             * doubles with the leap second count followed by the date when it took effect given
             * in plain seconds since the J2000 epoch, not including leap seconds. Values prior to
             * the start of that epoch are negative. Convert this date form to a UTC calendar string
             * by using this numeric seconds count as a close-enough approximation of ET. All leap
             * second start dates begin at midnight, so assume times of 00h:00mm.
             */
            leapSecData = CSPICE.gdpool(
                    "DELTET/DELTA_AT",  // variable name
                    0,                    // index of component to start retrieval at for variable
                    1000                  // maximum number of values to return
            );

            for (int rowDeltaEtIndex=0; rowDeltaEtIndex+1<leapSecData.length; rowDeltaEtIndex+=2) {
                rowDeltaAtUtcIndex       = rowDeltaEtIndex+1;
                leapSecDeltaEt           = (int) leapSecData[rowDeltaEtIndex];
                dateval                  = leapSecData[rowDeltaAtUtcIndex] + 1.;

                String date = CSPICE.timout(dateval, "YYYY-DOY::TDB");
                LeapSecond leapSec = new LeapSecond(TimeConvert.parseIsoDoyUtcStr(date + "T00:00:00"), leapSecDeltaEt);
                allLeapSeconds.add(leapSec);
            }
        } catch (KernelVarNotFoundException e) {
            throw new TimeConvertException("Error getting leap seconds. Leap Seconds Kernel data not loaded:  " + e.getMessage(), e);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error getting leap seconds. Cannot obtain leap seconds:  " + e.getMessage(), e);
        }

        return allLeapSeconds;
    }


    /**
     * Returns the number of leap seconds that have occurred before a particular time. The data for the leap seconds
     * are provided in the NAIF generic Leap Seconds Kernel (LSK). Input date/time is in ISO UTC day of
     * year format (i.e., yyyy-doyThh:mm:ss.ssssss).
     *
     * @param time IN the datetime for which the number of leap seconds are sought in UTC ISO DOY format
     * @return the number of leap seconds
     * @throws TimeConvertException when the leap seconds could not be determined
     */
    public static Integer getDeltaEtBefore(OffsetDateTime time) throws TimeConvertException {
        List<LeapSecond> leapSeconds = TimeConvert.parseLeapSeconds();

        if (leapSeconds.size() < 2) {
            throw new TimeConvertException("Cannot get number of leap seconds. Invalid LSK data.");
        }

        // find the last leap second that is before the given time, and return its count
        return leapSeconds.stream()
                .filter(ls -> ls.leapSecOccurrence.isBefore(time))
                .reduce((first, second) -> second)
                .map(leapSecond -> leapSecond.leapSecDeltaEt).orElseThrow(() -> new TimeConvertException("No leap seconds detected before " + time + "; this is probably an error with the input leap second data."));
    }

    /**
     * Returns the number of leap seconds that have occurred as of a particular time, including at that time.
     * The data for the leap seconds are provided in the NAIF generic Leap Seconds Kernel (LSK). Input date/time is in
     * ISO UTC day of year format (i.e., yyyy-doyThh:mm:ss.ssssss).
     *
     * @param time IN the datetime for which the number of leap seconds are sought in UTC ISO DOY format
     * @return the number of leap seconds
     * @throws TimeConvertException when the leap seconds could not be determined
     */
    public static Integer getDeltaEtAsOf(OffsetDateTime time) throws TimeConvertException {
        List<LeapSecond> leapSeconds = TimeConvert.parseLeapSeconds();

        if (leapSeconds.size() < 2) {
            throw new TimeConvertException("Cannot get number of leap seconds. Invalid LSK data.");
        }

        // find the last leap second that is before the given time, and return its count
        return leapSeconds.stream()
                .filter(ls -> (! ls.leapSecOccurrence.isAfter(time)))
                .reduce((first, second) -> second)
                .map(leapSecond -> leapSecond.leapSecDeltaEt).orElseThrow(() -> new TimeConvertException("No leap seconds detected as of " + time + "; this is probably an error with the input leap second data."));
    }

    /**
     * Returns the offset of UTC from Terrestrial Dynamical Time (TDT) in seconds. This method reads this value
     * from data provided in the Leap Seconds Kernel, which must already have been loaded.
     * @return the offset of TDT from UTC not counting leap seconds
     * @throws TimeConvertException if the TDT/UTC offset could not be obtained
     */
    public static Double utcTdtOffset() throws TimeConvertException {

        double[] offset;

        try {
            offset = CSPICE.gdpool("DELTET/DELTA_T_A", 0, 1);

        } catch (KernelVarNotFoundException e) {
            throw new TimeConvertException("Error getting TDT/UTC offset. Leap Seconds Kernel data not loaded:  " + e.getMessage(), e);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error getting TDT/UTC offset. Cannot obtain offset from LSK data:  " + e.getMessage(), e);
        }

        return offset[0];
    }


    /**
     * Get the tick rate (number of ticks per second) from the SCLK kernel data. This is the fine time
     * or subseconds modulus. It is associated in the kernel by the keyword SCLK01_MODULI_nnn where nnn
     * is the NAIF spacecraft identifier. The SCLK kernel is required and assumed to have been loaded.
     *
     * @param scid the NAIF spacecraft ID
     * @return the tick rate
     * @throws TimeConvertException if the tick rate could not be obtained from the SCLK kernel
     */
    public static Integer getSclkKernelTickRate(Integer scid) throws TimeConvertException {
        String keyword = "SCLK01_MODULI_" + Math.abs(scid);
        Integer tickRate;

        try {
            int[] sclkModuli = CSPICE.gipool(keyword, 1, 1);
            tickRate = sclkModuli[0];

        } catch (KernelVarNotFoundException e) {
            throw new TimeConvertException("Error getting SCLK subseconds modulus. SCLK Kernel data not loaded:  " + e.getMessage(), e);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error getting SCLK modulus. Cannot obtain subseconds modulus from SCLK data:  " + e.getMessage(), e);
        }

        return tickRate;
    }


    /**
     * Get the number of clock stages for the current SCLK. The number of stages is the sum of the parts
     * of the SCLK. 1/[coarse]:[fine] is a 2-stage clock. 1/[coarse]:[fine]:[subfine] would be a 3-stage
     * clock. Most modern spacecraft clocks are 2-stage. This information is given in the SCLK Kernel in
     * the SCLK01_N_FIELDS_nnn field. This function reads the value of this field and returns it.
     * @param scid the NAIF spacecraft ID
     * @return the number of stages of the SCLK
     * @throws TimeConvertException if the number of stages could not be obtained
     */
    public static Integer getNumSclkStages(Integer scid) throws TimeConvertException {

        String keyword = "SCLK01_N_FIELDS_" + Math.abs(scid);
        Integer numStages;

        try {
            int[] fields = CSPICE.gipool(keyword, 0, 1);
            numStages = fields[0];

        } catch (KernelVarNotFoundException e) {
            throw new TimeConvertException("Error getting number of SCLK stages. SCLK Kernel data not loaded:  " + e.getMessage(), e);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error getting number of SCLK stages. Cannot read " + keyword + " from SCLK Kernel:  " + e.getMessage(), e);
        }

        return numStages;
    }


    /**
     * Receives the components of a raw SCLK and converts it to a SPICE 'SCLK String'.
     * Only two-stage clocks are supported.
     *
     * @param partition       IN the relevant spacecraft clock partition
     * @param coarse          IN the number of coarse ticks of the clock
     * @param fine            IN the number of fine ticks of the clock
     * @param scid            IN the spacecraft ID
     * @return the SCLK in string form
     * @throws TimeConvertException when input parameters are out of range
     */
    public static String sclkToSclkStr(Integer scid, Integer partition, Integer coarse, Integer fine) throws TimeConvertException {
        final String delimiter = getSclkStrDelimiterFromSclkKernel(scid);
        return partition + "/" + coarse + delimiter + fine;
    }


    /**
     * Receives the components of a raw SCLK and converts it to Ephemeris
     * Time (ET).
     *
     * @param scid      IN the NAIF-designated spacecraft ID code
     * @param partition IN the relevant spacecraft clock partition
     * @param coarse    IN the number of ticks of the clock
     * @param fine      IN the fraction of ticks of the clock
     * @return SCLK converted to ephemeris time
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double sclkToEt(Integer scid, Integer partition, Integer coarse, Integer fine) throws TimeConvertException {
        // Get the SCLK in string form
        String sclk = sclkToSclkStr(scid, partition, coarse, fine);

        double et;

        try {
            et = CSPICE.scs2e(scid, sclk);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting SCLK to ET:  " + e.getMessage(), e);
        }

        return et;
    }

    /**
     * Receives a raw SCLK as a double and converts it to Encoded SCLK
     * form, also a double.
     *
     * @param scid      IN the NAIF-designated spacecraft ID code
     * @param partition IN the relevant spacecraft clock partition
     * @param sclk      IN SCLK as a double
     * @return the SCLK in encoded SCLK string form
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double sclkToEncSclk(Integer scid, Integer partition, Double sclk) throws TimeConvertException {
        final int sclkCoarse = (int) Math.floor(sclk);
        final int sclkFine = new BigDecimal(sclk)
                .setScale(16, RoundingMode.HALF_UP)
                .subtract(new BigDecimal(sclkCoarse))
                .multiply(new BigDecimal(getSclkKernelTickRate(scid)))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        return sclkToEncSclk(scid, partition, sclkCoarse, sclkFine);
    }

    /**
     * Receives the components of a raw SCLK and converts it to Encoded SCLK
     * form, which is a double-precision real number.
     *
     * @param scid      IN the NAIF-designated spacecraft ID code
     * @param partition IN the relevant spacecraft clock partition
     * @param coarse    IN the number of ticks of the clock
     * @param fine      IN the fraction of ticks of the clock
     * @return the SCLK in encoded SCLK string form
     * @throws TimeConvertException when a SPICE error occurs
     */
    public static Double sclkToEncSclk(Integer scid, Integer partition, Integer coarse, Integer fine) throws TimeConvertException {
        final String sclk = sclkToSclkStr(scid, partition, coarse, fine);

        try {
            return CSPICE.scencd(scid, sclk);
        } catch (SpiceErrorException e) {
            throw new TimeConvertException(String.format("Error converting raw SCLK (%s) to encoded SCLK:  " + e.getMessage(), sclk), e);
        }
    }


    /**
     * Converts an encoded SCLK value to raw SCLK ticks. The SCLK kernel needs to have been
     * loaded.
     *
     * @param scid     IN the NAIF-designated spacecraft ID code
     * @param tickRate IN the number of ticks per second of the fine time
     * @param encSclk  IN the encoded SCLK value
     * @return the number of SCLK ticks
     * @throws TimeConvertException if the encoded SCLK could not be converted to ticks
     */
    public static Double encSclkToSclk(Integer scid, Integer tickRate, Double encSclk) throws TimeConvertException {
        Double sclk;
        String sclkStr;

        try {
            double et = CSPICE.sct2e(scid, encSclk);
            sclkStr = CSPICE.sce2s(scid, et);

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting encoded SCLK to SCLK ticks:  " + e.getMessage(), e);
        }

        sclk = TimeConvert.sclkStrToSclk(sclkStr, tickRate, scid);

        return sclk;
    }


    /**
     * Convert a UTC SCET time to SCLK ticks.
     *
     * @param scid     IN the NAIF-designated spacecraft ID code
     * @param tickRate IN the number of ticks per second of the fine time
     * @param utc      IN SCET time in UTC as an ISO DOY calendar string
     * @return the sclk
     * @throws TimeConvertException if the UTC could not be converted to SCLK
     */
    public static Double utcToSclk(Integer scid, Integer tickRate, String utc) throws TimeConvertException {

        Double sclk;
        String sclkStr;

        try {
            double et    = CSPICE.str2et(utc);
            sclkStr = CSPICE.sce2s(scid, et);

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Error converting UTC to SCLK ticks: " + e.getMessage(), e);
        }

        sclk = sclkStrToSclk(sclkStr, tickRate, scid);

        return sclk;
    }


    /**
     * Converts an SCLK string of form "p/nnnnnnnn:ssss" or "p/nnnnnnnn.ssss" or where p is the clock
     * partition number, nnnnnnnn is the coarse time integer and ssss is the fine time integer (not fraction.)
     * Only two-stage clocks are supported. The delimiter between coarse and fine time is either ":" or ".". It then
     * divides the fine time by the tick rate in order to compute the decimal fraction of a second.
     *
     * @param sclkStr  IN the SCLK in string form
     * @param tickRate IN the clock number of ticks per second
     * @param scid     IN the NAIF-designated spacecraft ID code
     * @return the SCLK in numeric form
     * @throws TimeConvertException if the clock string contains more than two stages
     */
    public static Double sclkStrToSclk(String sclkStr, Integer tickRate, Integer scid) throws TimeConvertException {
        return sclkStrToSclk(
                sclkStr,
                tickRate,
                getSclkStrDelimiterFromSclkKernel(scid)
        );
    }

    private static String getSclkStrDelimiterFromSclkKernel(Integer scid) throws TimeConvertException {
        // Get the SCLK string field delimeter from the SCLK01_OUTPUT_DELIM_<nn> variable within the SCLK Kernel.
        // The delimeter is defined by this field. It separate the two clock stages in an SCLK string (coarse and fine).
        // It is an integer 1-5 where 1 ("."), 2 (":"), 3 ("-"), or 4 (","). SPICE allows a 5 (space) also, which is not
        // supported here.
        String varname = "SCLK01_OUTPUT_DELIM_" + Math.abs(scid);
        double[] delim_id ;
        try {
            delim_id = CSPICE.gdpool(varname, 0, 1);
        }catch (SpiceErrorException e) {
            throw new TimeConvertException("Error reading SCLK Kernel variable " + varname + ": " + e.getMessage(), e);
        }catch (KernelVarNotFoundException e) {
            throw new TimeConvertException("Error Required SCLK Kernel variable '" + varname + "' not found: " + e.getMessage(), e);
        }
        String delimiter;
        int delimNum = (int)delim_id[0];
        switch (delimNum) {
            case 1: delimiter = ".";
                break;
            case 2: delimiter = ":";
                break;
            case 3: delimiter = "-";
                break;
            case 4: delimiter = ",";
                break;
            default: delimiter = " ";
                throw new TimeConvertException("ERROR: SCLK Kernel field " + varname + " must be 1,2,3,or 4. " +
                        delimNum + " ('" + delimiter + "') is not a supported SCLK string delimiter.");
        }

        return delimiter;
    }

    public static Double sclkStrToSclk(String sclkStr, Integer tickRate, String delimiter) throws TimeConvertException {
        if (!sclkStr.contains(delimiter)) {
            throw new TimeConvertException("ERROR: SCLK string '" + sclkStr + " does not contain expected clock stage delimiter '" +
                    delimiter + ".");
        }
        String[] fields = sclkStr.split("/");
        String[] timefields = fields[1].split(delimiter);

        if (timefields.length > 2) {
            throw new TimeConvertException("Unable to convert SCLK string to numeric SCLK. Three or more stage clocks not supported.");
        }

        Double coarsetime = Double.parseDouble(timefields[0]);
        Double finetime   = Double.parseDouble(timefields[1]);
        Double sclk       = coarsetime + finetime/tickRate;

        return sclk;
    }



    /**
     * Takes an ISO format date/time string of the form yyyy-doyThh:mm:ss.ssss... and returns it with
     * the seconds part rounded to the designated number of digits to the right of the decimal.
     *
     * @param timestr   IN the date/time string to round
     * @param precision IN the number of fraction of second digits to round to
     * @return the rounded date/time string
     * @throws TimeConvertException if the input is not a valid ISO date/time string
     */
    public static String roundTimeStrPrecision(String timestr, Integer precision) throws TimeConvertException {

        /* Make sure the input is an ISO date/time string. */
        String[] fields = timestr.split("T");
        if (fields.length != 2) {
            throw new TimeConvertException("ISO DOY calendar string format expected. Time string: " + timestr);
        }

        /* Split off the time of day part of the string into hh:mm:ss. */
        String[] hhmmss = fields[1].split(":");
        if (hhmmss.length != 3) {
            throw new TimeConvertException("Time value invalid. Time string: " + timestr);
        }

        String pattern = "00.";
        for (int i=0; i<precision; i++) {
            pattern = pattern + "0";
        }


        /* Convert the seconds part of the time into a double, round it to the specified number of fraction
         * of a second digits and then reassemble the date/time string.
         */
        Double seconds = Double.parseDouble(hhmmss[2]);
        DecimalFormat secFormat = new DecimalFormat(pattern);
        secFormat.setRoundingMode(RoundingMode.HALF_UP);
        String secstr = secFormat.format(seconds);

        String revisedTimeStr = fields[0] + "T" + hhmmss[0] + ":" + hhmmss[1] + ":" + secstr;

        return revisedTimeStr;
    }


    /**
     * Gets the NAIF object ID associated with a named object (e.g. a DSN station, planetary body, or spacecraft).
     *
     * @param name the name of the body
     * @return the NAIF ID code for the body
     * @throws TimeConvertException if the NAIF ID could not be determined
     */
    public static Integer nameToNaifId(String name) throws TimeConvertException {

        int naifId;
        try {
            naifId = CSPICE.bodn2c(name);

        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Unable to convert named object \"" + name + "\" to NAIF ID: " + e.getMessage(), e);
        }catch (IDCodeNotFoundException e) {
            throw new TimeConvertException("No NAIF ID found for named object \"" + name + "\": " + e.getMessage(), e);
        }

        return naifId;
    }


    /**
     * Returns a list of the SPICE kernels currently loaded into SPICE.
     *
     * @return A list of the Kernel types followed by the kernel names
     * @throws TimeConvertException if the list could not be obtained
     */
    public static List<String> getLoadedKernelNames() throws TimeConvertException {

        List<String> klist = new ArrayList<>();

        try {
            int numKernelsLoaded = CSPICE.ktotal("ALL");
            for (int i = 0; i < numKernelsLoaded; i++) {
                String kname = KernelDatabase.getFileName(i, "all");
                String ktype = KernelDatabase.getFileType(kname);
                klist.add(ktype + ": \t" + kname);
            }
        } catch (SpiceErrorException e) {
            throw new TimeConvertException("Unable to list the loaded kernels: " + e.getMessage(), e);
        } catch (SpiceKernelNotLoadedException e) {
            throw new TimeConvertException("No SPICE kernels are currently loaded: " + e.getMessage(), e);
        }

        return klist;
    }


    /**
     * Determines if a string is a text representation of a numeric value.
     * @param val IN:the string to evaluate
     * @return true if the value is numeric, false otherwise
     */
    public static boolean isNumeric(String val) {
        if ((val == null) || (val == "NaN")) {
            return false;
        }
        try {
            double numval = Double.parseDouble(val);
        } catch (Exception e) {
            return false;
        }

        return true;
    }


    /**
     *  Equals function. Tests two Double values for equality within the precision given by the epsilon value.
     *
     * @param first   IN the first value to compare
     * @param second  IN the second value to compare
     * @param epsilon the allowable difference between the two values
     * @return true if first and second are equal within the set precision
     */
    public static boolean eq (Double first, Double second, Double epsilon) {
        return Math.abs(first - second) <= epsilon;
    }


    /**
     *  Not Equals function. Tests two Double values for inequality within the precision given by the epsilon value.
     *
     * @param first   IN the first value to compare
     * @param second  IN the second value to compare
     * @param epsilon the 'allowable' difference between the two values that would allow these two values to be considered equal
     * @return true if first and second are NOT equal within the set precision
     */
    public static boolean ne (Double first, Double second, Double epsilon) {
        return !eq(first, second, epsilon);
    }

    public static class FrameSampleMetrics {
        public final double tdtG;
        public final OffsetDateTime scetUtc;
        public final double scetErrorNanos;
        public final double owltSec;

        public FrameSampleMetrics(double tdtG, OffsetDateTime scetUtc, double scetErrorNanos, double owltSec) {
            this.tdtG = tdtG;
            this.scetUtc = scetUtc;
            this.scetErrorNanos = scetErrorNanos;
            this.owltSec = owltSec;
        }
    }

    /**
     * Computes the SCET error for a given FrameSample, assuming that there is a loaded SCLK kernel
     * which this method will use to perform the SCLK -> SCET (UTC) time conversion
     *
     * @param config
     * @param fs
     * @return
     * @throws TimeConvertException
     * @throws MmtcException
     */
    public static FrameSampleMetrics calculateFrameSampleMetrics(TimeCorrelationMetricsConfig config, FrameSample fs) throws TimeConvertException, MmtcException, SpiceErrorException {
        final TimeCorrelationTarget tcTarget = new TimeCorrelationTarget(
                Arrays.asList(fs),
                config,
                config.getTkSclkFineTickModulus()
        );

        // expected SCET is the value TDT(G) value as converted using the SCLK kernel
        double estimatedEtUsingSclkKernel  = CSPICE.sct2e(config.getNaifSpacecraftId(), tcTarget.getTargetSampleEncSclk());
        double estimatedTdtUsingSclkkernel = CSPICE.unitim(estimatedEtUsingSclkKernel, "ET", "TDT");
        final OffsetDateTime estimatedScet = tdtToUtc(estimatedTdtUsingSclkkernel, 9);

        // actual SCET is the actual TDT_G, in SCET terms, that was read on the ground
        final OffsetDateTime actualScet = tdtToUtc(tcTarget.getTargetSampleTdtG(), 9);

        // for our estimated - actual calculation, we want the error term to be negative if the estimated value is larger (later) than the actual value, which would correspond to a 'negative' result from ChronoUnit.between, so invert the sign
        return new FrameSampleMetrics(
                tcTarget.getTargetSampleTdtG(),
                actualScet,
                -1 * ChronoUnit.NANOS.between(estimatedScet, actualScet),
                tcTarget.getTargetSampleOwlt()
        );
    }

    public static OffsetDateTime tdtToUtc(Double tdt, int subsecPrecision) throws TimeConvertException {
        final String tdtGStr = TimeConvert.tdtToTdtCalStr(tdt, subsecPrecision);
        final String utcStr = TimeConvert.tdtCalStrToUtc(tdtGStr, subsecPrecision);
        return TimeConvert.parseIsoDoyUtcStr(utcStr);
    }
}

