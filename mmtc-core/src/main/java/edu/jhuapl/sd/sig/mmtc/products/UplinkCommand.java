package edu.jhuapl.sd.sig.mmtc.products;

import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * <P>The structure of the Uplink Command File record. This file contains the information needed
 * as input to an external command generator that will update the time correlation parameters
 * onboard the spacecraft.</P>
 *
 * <P>The Uplink Command file is a CSV text file that contains a single record. This one record
 * contains five fields:
 * <I>SCLK (coarse), ephemeris time, TDT in numeric form, TDT in calendar string form,
 * clock change rate</I>. Only the coarse SCLK is provided because the corresponding TDT(G) has been
 * adjusted to align with the whole tick coarse SCLK boundary. This is the same value as the Supplemental Frame
 * SCLK Coarse value given in the RawTlmTable and the same value written to the SCLK/SCET file if it is being
 * produced.</P>
 * A new version of the Uplink Command File is created each time the application runs.
 */
public class UplinkCommand {

    /**
     * The course component of the SCLK.
     */
    private int sclk_course;

    /**
     * The ephemris time (TDB) associated with the SCLK.
     */
    private double et;

    /**
     * The TDT corresponding to the SCLK in numeric seconds of epoch form.
     */
    private double tdt;

    /**
     * The TDT corresponding to the SCLK in calendar string form.
     */
    private String tdt_str;

    /**
     * The clock change reate associated with the SCLK to ET time correlation.
     */
    private double clkChgRate;

    /**
     * Class constructor that sets the values for the Uplink Comman File record.
     * @param sclkCourse  IN the course component of the SCLK
     * @param et          IN the ephemeris time corresponding to the SCLK time
     * @param tdt         IN the TDT corresponding to the SCLK in numeric form
     * @param tdtStr      IN the TDT corresponding to the SCLK calendar string form
     * @param clkChgRate  IN the clock change rate
     */
    public UplinkCommand(int sclkCourse, double et, double tdt, String tdtStr, double clkChgRate) {
        this.sclk_course = sclkCourse;
        this.et          = et;
        this.tdt         = tdt;
        this.tdt_str     = tdtStr;
        this.clkChgRate  = clkChgRate;
    }

    public String toString() {
        DecimalFormat gtfmt = new DecimalFormat("#.000000");
        gtfmt.setRoundingMode(RoundingMode.HALF_UP);
        DecimalFormat clkchgfmt = new DecimalFormat("#.00000000000");
        clkchgfmt.setRoundingMode(RoundingMode.HALF_UP);

        String cmd =
                String.valueOf(sclk_course)           + "," +
                        gtfmt.format(et)              + "," +
                        gtfmt.format(tdt)             + "," +
                        tdt_str                       + "," +
                        clkchgfmt.format(clkChgRate);
        return cmd;
    }
}
