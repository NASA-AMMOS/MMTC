package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Container class for an SCLK/SCET data object. The SCLK/SCET is one of the primary time correlation products.
 */
public class SclkScet {

    /**
     * Spacecraft clock count in coarse and fine ticks, separated by a '.'
     */
    private String sclk;

    /*
     * Spacecraft Event Time (SCET) in UTC
     */
    private OffsetDateTime scet;

    /**
     * The current Delta Universal Time (DUT). This is the TDT offset plus current leap seconds.
     */
    private Double dut;

    /**
     * The SCLK change rate.
     */
    private Double sclkrate;

    /**
     * A sample time correlation record pattern. Default value is used if creating a file from scratch.
     */
    private static String sclkScetRecordPattern = "000000000000.000  2010-001T00:00:00.000 66.184 1.00000000000";

    /**
     * The number of fields in an SCLK/SCET time correlation record.
     */
    private static final int numRecordFields = 4;

    /**
     * Sets the number of digits of fractions of a second that SCET strings will be represented by.
     */
    private static int scetStrSecondsPrecision = 3;

    /* Indices for the fields in a quadruplet record. */
    public static final int SCLK     = 0;
    public static final int SCET     = 1;
    public static final int DUT      = 2;
    public static final int SCLKRATE = 3;

    /**
     * Class constructor.
     *
     * @param sclk     IN SCLK count in ticks
     * @param scet     IN SCET time in UTC
     * @param dut      IN Delta Universal Time (DUT) in seconds
     * @param sclkrate IN clock change rate
     */
    public SclkScet(String sclk, OffsetDateTime scet, Double dut, Double sclkrate) {
        this.sclk     = sclk;
        this.scet     = scet;
        this.dut      = dut;
        this.sclkrate = sclkrate;
    }


    void setSclkrate(Double sclkrate) {this.sclkrate = sclkrate;}

    public String getSclk()     {return sclk;}
    public OffsetDateTime getScet() {return scet;}
    public Double getDut()      {return dut;}
    public Double getSclkrate() {return sclkrate;}

    /**
     * Gets the number of digits in the fractions of a second part to which the SCET is provided.
     *
     * @return the number of fractional digits of precision
     */
    public static Integer getScetStrSecondsPrecision() {
        return scetStrSecondsPrecision;
    }

    /**
     * Sets the number of digits for the fraction of second in a SCET string.
     *
     * @param precision IN number of digits of precision
     */
    public static void setScetStrSecondsPrecision(int precision) {
        scetStrSecondsPrecision = precision;
    }


    public String toString() {
       String sclkScetRec;

       try {
           String record = sclkScetRecordPattern;
           String[] quadrupletFields = splitRecord(sclkScetRecordPattern);

           /* New SCLK value. */
           String record0 = record.replace(quadrupletFields[SCLK], sclk);

           /* New SCET value in UTC as an ISO calendar string. */
           String roundedScet = TimeConvert.roundTimeStrPrecision(TimeConvert.timeToIsoUtcString(scet, scetStrSecondsPrecision), scetStrSecondsPrecision);
           String record1 = record0.replaceFirst(quadrupletFields[SCET], roundedScet);

           /* New DUT in seconds. */
           DecimalFormat dutFormat = new DecimalFormat("00.000");
           dutFormat.setRoundingMode(RoundingMode.HALF_UP);
           String dutstr = dutFormat.format(dut);
           String record2 = record1.replaceFirst(quadrupletFields[DUT], dutstr);

           /* New clock change rate. */
           DecimalFormat clkChgRateFormat = new DecimalFormat("0.0000000000");
           clkChgRateFormat.setRoundingMode(RoundingMode.HALF_UP);
           // Convert the sclkrate to a String. Add 0.000000000005 to assure that it rounds up.
           String chgRateStr = clkChgRateFormat.format(sclkrate + 0.000000000005);

           /* Add a leading space to the record if there is not already one per spec. */
           String leadingSpace = "";
           if (!record2.startsWith(" ")) {
               leadingSpace = " ";
           }
           sclkScetRec = leadingSpace + record2.replaceFirst(quadrupletFields[SCLKRATE], chgRateStr);

       } catch (Exception e) {
           sclkScetRec = "";
        }

        return sclkScetRec;
    }


    /**
     * Splits an SCLK/SCET record into its constituent fields.
     *
     * @param record IN the record to parse
     * @return an array of Strings containing the fields of the SCLK/SCET data object
     * @throws TextProductException if the record could not be parsed
     */
    public static String[] splitRecord(String record) throws TextProductException {

        /* Split the record into its constituent fields. */
        String[] fields = record.trim().split("\\s+");
        if (fields.length != numRecordFields) {
            throw new TextProductException("Invalid SCLK/SCET record.");
        }

        return fields;
    }


    /**
     * Parses an SCLK/SCET record into its constituent fields.
     *
     * @param record IN the record to parse
     * @return an SCLK/SCET data object
     * @throws TextProductException if the record could not be parsed
     */
    public static SclkScet parseRecord(String record) throws TextProductException {

        /* Split the record into its constituent fields. */
        String[] fields = splitRecord(record);

        String sclk     = fields[SCLK];
        String datetime = fields[SCET];
        Double dut      = Double.parseDouble(fields[DUT]);
        Double sclkrate = Double.parseDouble(fields[SCLKRATE]);

        return new SclkScet(sclk, TimeConvert.parseIsoDoyUtcStr(datetime), dut, sclkrate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SclkScet)) return false;
        SclkScet sclkScet = (SclkScet) o;
        return Objects.equals(sclk, sclkScet.sclk) && Objects.equals(scet, sclkScet.scet) && Objects.equals(dut, sclkScet.dut) && Objects.equals(sclkrate, sclkScet.sclkrate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sclk, scet, dut, sclkrate);
    }
}
