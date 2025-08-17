package edu.jhuapl.sd.sig.mmtc.tlm;

import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import edu.jhuapl.sd.sig.mmtc.util.CdsTimeCode;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;

import static edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable.*;

/**
 * An instance of this class contains data needed for time correlation, which is primarily 'about' a single frame/packet
 * sample, but typically sourced from multiple frames/packets.  This data includes information relating to SCLK,
 * Earth receipt time, ground receipt station, etc.
 * <p>
 * This class and its fields (and MMTC in general) is designed under the assumptions that:
 * - a spacecraft downlinks telemetry about the state of its SCLK values
 * - this telemetry generally consists of a sequence of frames/packets, in which frames/packets transmitted later contain
 *   precise time information about frames/packets transmitted earlier
 * - receiver (e.g. ground station) metadata is available and associated with each frame/packet
 * <p>
 * MMTC uses this information, along with ancillary information, to perofrm its functions.  Each field below
 * has documentation about its nature and usage.
 * <p>
 * This class is independent of the actual telemetry source, i.e. it is agnostic to the original form or location of telemetry.
 * <p>
 * This class, and the rest of MMTC, assumes that the SCLK is a two-stage spacecraft clock.
 */
public class FrameSample {
    public enum ValidState {
        VALID,
        INVALID,
        UNSET
    }

    private static final Logger logger = LogManager.getLogger();

    private static final DecimalFormat DATA_RATE_BPS_FORMAT = new DecimalFormat("#.000");

    static {
        DATA_RATE_BPS_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    /*
     * -----------------------------------------------------------------------
     * The following fields contain metadata from (and about) the "current" packet itself
     * (the packet that this FrameSample instance is meant to represent)
     * or from receiver information. For example, the SCLK value in the packet header,
     * or the ERT reported by the ground station.
     * -----------------------------------------------------------------------
     */

    /**
     * Coarse SCLK, usually from a packet header.
     * Usage: reporting
     */
    private int sclkCoarse;

    /**
     * Fine SCLK, usually from a packet header.
     * Usage: reporting
     */
    private int sclkFine;

    /**
     * ERT as a CDS time code or a String, usually from the DSN station header.
     * Usage: time correlation calculations, filtering, reporting
     */
    private CdsTimeCode ert;
    private boolean ertExplicitlySet;
    private String ertStr;
    private boolean ertStrExplicitlySet;

    /**
     * SCET, usually calculated by ground systems from SCLK in packet header
     * Usage: none as of this time
     */
    private String scet;

    /**
     * DSS station ID, usually from the DSN station header.
     * Usage: time correlation calculations (OWLT calc), filtering, reporting
     */
    private int pathId;

    /**
     * Virtual channel ID, usually from frame header
     * Usage: filtering, reporting
     */
    private int vcid;

    /**
     * Virtual channel frame count, usually from frame header
     * Usage: filtering, reporting
     */
    private int vcfc;

    /**
     * Master channel frame count, usually from frame header
     * Usage: filtering, reporting
     */
    private int mcfc;

    /*
     * -----------------------------------------------------------------------
     * The following fields contain timekeeping data about the current packet, usually reported after
     * the fact by, for example, a subsequent timekeeping packet.  In other
     * words, the data in these fields is about the "current" packet, although it
     * may have been reported by a subsequent packet about its "target" packet, which would be this packet.
     *
     * For example, on the Europa Clipper mission, the Last Frame SCLK value
     * reported in the packet's corresponding timekeeping packet.
     * -----------------------------------------------------------------------
     */

    /**
     * Coarse SCLK of when this sample was radiated by the spacecraft, as reported by a subsequent sample
     * Usage: time correlation calculations, filtering, reporting
     */
    private int tkSclkCoarse;

    /**
     * Fine SCLK of when this sample was radiated by the spacecraft, as reported by a subsequent sample
     * Usage: time correlation calculations, reporting
     */
    private int tkSclkFine;

    /**
     * VCID of this packet, as reported by a subsequent sample about its target packet
     * Usage: filtering, reporting
     */
    private int tkVcid;

    /**
     * VCFC of this packet, as reported by a subsequent sample about its target packet
     * Usage: filtering, reporting
     */
    private int tkVcfc;

    /**
     * Data rate at which this packet was downlinked, in bits per second, as reported by a subsequent sample
     * Usage: time correlation calculations, filtering, reporting
     */
    private BigDecimal tkDataRateBps;

    /**
     * Information about the RF encoding used to downlink this packet, as reported by a subsequent sample
     * Usage: reporting
     */
    private String tkRfEncoding;

    /**
     * Valid flag for the sample, where 'true' indicates a valid packet, as reported by a subsequent sample
     * Usage: filtering
     */
    private ValidState tkIsValid;


    /*
     * -----------------------------------------------------------------------
     * The following fields contain metadata from the supplemental *timekeeping frame/packet* or from the
     * receiver upon receipt of the timekeeping packet. To emphasize, these fields are not about the
     * "current" packet, but about its supplemental (subsequent, later) timekeeping packet. These fields are
     * probably only relevant on missions or in applications that use timekeeping frames/packets.
     * -----------------------------------------------------------------------
     */

    /**
     * VCID of this packet's "supplemental" (subsequent, later) sample
     * Usage: filtering
     */
    private int suppVcid;

    /**
     * VCFC of this packet's "supplemental" (subsequent, later) sample
     * Usage: filtering
     */
    private int suppVcfc;

    /**
     * MCFC of this packet's "supplemental" (subsequent, later) sample
     * Usage: filtering
     */
    private int suppMcfc;

    /**
     * ERT of this packet's "supplemental" (subsequent, later) sample
     * Usage: reporting, and time correlation calculations ONLY for computing downlink data rate iff not read otherwise out of a packet or frame
     */
    private CdsTimeCode suppErt;
    private boolean suppErtExplicitlySet;
    private String suppErtStr;
    private boolean suppErtStrExplicitlySet;

    /**
     * A computed field set by TimeCorrelationApp based on downlink data rate; do not set otherwise.
     * Usage: time correlation calculations, reporting
     */
    private double derivedTdBe;

    /**
     * Optional field to specify this frame's size in bits.  This is used in estimating the downlink data rate (used in
     * time correlation calculations) IFF the downlink data rate is not set.  If this is not set, then the default
     * frame size as provided in configuration is used.
     * Usage: time correlation calculations (iff downlink data rate is not set), reporting
     */
    private int frameSizeBits;

    /**
     * Default constructor, which initializes all fields to values that indicate they are unset.
     */
    public FrameSample() {
        this.sclkCoarse = -1;
        this.sclkFine = -1;
        this.ert = new CdsTimeCode();
        this.ertExplicitlySet = false;
        this.ertStr = "";
        this.ertStrExplicitlySet = false;
        this.scet = "-";
        this.pathId = -1;
        this.vcid = -1;
        this.vcfc = -1;
        this.mcfc = -1;
        this.tkSclkCoarse = -1;
        this.tkSclkFine = -1;
        this.tkVcid = -1;
        this.tkVcfc = -1;
        this.tkDataRateBps = BigDecimal.valueOf(-1.0);
        this.tkRfEncoding = "-";
        this.tkIsValid = ValidState.UNSET;
        this.suppVcid = -1;
        this.suppVcfc = -1;
        this.suppMcfc = -1;
        this.suppErt = new CdsTimeCode();
        this.suppErtExplicitlySet = false;
        this.suppErtStr = "";
        this.suppErtStrExplicitlySet = false;
        this.derivedTdBe = Double.NaN;
        this.frameSizeBits = -1;
    }

    public void setSclkCoarse(int sclkCoarse) { this.sclkCoarse = sclkCoarse; }

    public void setSclkFine(int sclkFine) { this.sclkFine = sclkFine; }

    public CdsTimeCode getErt() {
        if (!this.ertExplicitlySet && this.ertStrExplicitlySet) {
            // If the CDS ERT has never been set but we do have an ERT string,
            // convert the string to CDS and return that.
            try {
                return TimeConvert.isoUtcToCds(this.ertStr);
            }
            catch (TimeConvertException e) {
                logger.error("ert was not explicitly set, and failed to derive it from UTC ertStr value " + this.ertStr, e);
            }
        }
        // Otherwise, either 1) we do have the CDS ERT, or 2a) we only have
        // the ERT string and couldn't derive CDS from it or 2b) we don't
        // have either one. In the case of (2), the CDS ERT field is still its
        // initial "empty" value, and that's what we want to return, anyway.
        return this.ert;
    }
    public void setErt(CdsTimeCode ert) {
        this.ert = ert;
        this.ertExplicitlySet = true;
    }

    public String getErtStr() {
        if (!this.ertStrExplicitlySet && this.ertExplicitlySet) {
            try {
                return TimeConvert.cdsToIsoUtc(this.ert);
            }
            catch (TimeConvertException e) {
                logger.error("ertStr was not explicitly set, and failed to derive it from raw ert value " + this.ert.toString(), e);
            }
        }
        return this.ertStr;
    }
    public void setErtStr(String ertStr) {
        this.ertStr = ertStr;
        this.ertStrExplicitlySet = true;
    }

    public void setScet(String scet) { this.scet = scet; }

    public int getPathId() { return pathId; }
    public void setPathId(int pathId) { this.pathId = pathId; }

    public int getVcid() { return vcid; }
    public void setVcid(int vcid) { this.vcid = vcid; }

    public int getVcfc() { return vcfc; }
    public void setVcfc(int vcfc) { this.vcfc = vcfc; }

    public int getMcfc() { return mcfc; }
    public void setMcfc(int mcfc) { this.mcfc = mcfc; }

    public int getSuppMcfc() { return suppMcfc; }
    public void setSuppMcfc(int suppMcfc) { this.suppMcfc = suppMcfc; }

    public int getTkSclkCoarse() { return this.tkSclkCoarse; }
    public void setTkSclkCoarse(int tkSclkCoarse) { this.tkSclkCoarse = tkSclkCoarse; }

    public int getTkSclkFine() { return this.tkSclkFine; }
    public void setTkSclkFine(int tkSclkFine) { this.tkSclkFine = tkSclkFine; }

    public double getTkSclkComposite(int sclkModulus) {return (this.tkSclkCoarse + ((double) this.tkSclkFine / sclkModulus)); }

    public int getTkVcid() { return this.tkVcid; }
    public void setTkVcid(int tkVcid) { this.tkVcid = tkVcid; }
    public boolean isTkVcidSet() { return this.tkVcid != -1; }

    public int getTkVcfc() { return this.tkVcfc; }
    public void setTkVcfc(int tkVcfc) { this.tkVcfc = tkVcfc; }

    public BigDecimal getTkDataRateBps() { return this.tkDataRateBps; }

    public void setTkDataRateBps(BigDecimal tkDataRateBps) { this.tkDataRateBps = tkDataRateBps; }
    public void setTkDataRateBps(String dataRateBps) {
        if(!dataRateBps.equals("-")) {
            this.tkDataRateBps = new BigDecimal(dataRateBps);
        }
    }
    public boolean isTkDataRateSet() {
        return this.tkDataRateBps.doubleValue() != -1.0;
    }

    public String getTkDataRateBpsAsRoundedString() {
        return DATA_RATE_BPS_FORMAT.format(tkDataRateBps.doubleValue());
    }

    public String getTkRfEncoding() { return this.tkRfEncoding; }
    public void setTkRfEncoding(String tkRfEncoding) { this.tkRfEncoding = tkRfEncoding; }
    public void setTkRfEncoding(int tkRfEncoding) { this.tkRfEncoding = String.valueOf(tkRfEncoding); }

    public ValidState getTkValid() { return this.tkIsValid; }

    public void setTkValid(boolean tkIsValid) {
        if (tkIsValid) {
            this.tkIsValid = ValidState.VALID;
        } else {
            this.tkIsValid = ValidState.INVALID;
        }
    }

    public int getSuppVcid() { return this.suppVcid; }
    public void setSuppVcid(int suppVcid) { this.suppVcid = suppVcid; }

    public int getSuppVcfc() { return this.suppVcfc; }
    public void setSuppVcfc(int suppVcfc) { this.suppVcfc = suppVcfc; }

    public CdsTimeCode getSuppErt() {
        if (!this.suppErtExplicitlySet && this.suppErtStrExplicitlySet) {
            // If the CDS ERT has never been set but we do have an ERT string,
            // convert the string to CDS and return that.
            try {
                return TimeConvert.isoUtcToCds(this.suppErtStr);
            }
            catch (TimeConvertException e) {
                logger.error("suppErt was not explicitly set, and failed to derive it from UTC suppErtStr value " + this.suppErtStr, e);
            }
        }
        // Otherwise, either 1) we do have the CDS ERT, or 2a) we only have
        // the ERT string and couldn't derive CDS from it or 2b) we don't
        // have either one. In the case of (2), the CDS ERT field is still its
        // initial "empty" value, and that's what we want to return, anyway.
        return this.suppErt;
    }
    public void setSuppErt(CdsTimeCode suppErt) {
        this.suppErt = suppErt;
        this.suppErtExplicitlySet = true;
    }

    public String getSuppErtStr() {
        if (!this.suppErtStrExplicitlySet && this.suppErtExplicitlySet) {
            try {
                return TimeConvert.cdsToIsoUtc(this.suppErt);
            }
            catch (TimeConvertException e) {
                logger.error("suppErtStr was not explicitly set, and failed to derive it from raw suppErt value " + this.suppErt.toString(), e);
            }
        }
        return this.suppErtStr;
    }
    public void setSuppErtStr(String suppErtStr) {
        this.suppErtStr = suppErtStr;
        this.suppErtStrExplicitlySet = true;
    }

    public double getDerivedTdBe() {
        return this.derivedTdBe;
    }
    public void setDerivedTdBe(double td_be) {
        this.derivedTdBe = td_be;
    }

    public int getFrameSizeBits() {
        return frameSizeBits;
    }

    public void setFrameSizeBits(int frameSizeBits) {
        this.frameSizeBits = frameSizeBits;
    }
    public void setFrameSizeBits(String frameSizeBits) {
        if(!frameSizeBits.equals("-")) {
            this.frameSizeBits = Integer.parseInt(frameSizeBits);
        }
    }

    public boolean isFrameSizeBitsSet() {
        return this.frameSizeBits != -1;
    }

    /**
     * Convert this frame sample to a raw telemetry table record.
     *
     * @param headers the list of headers corresponding to the raw telemetry table
     * @return the raw telemetry table record
     */
    public TableRecord toRawTelemetryTableRecord(List<String> headers) {
        TableRecord record = new TableRecord(headers);

        record.setValue(PATH_ID, String.valueOf(this.pathId));
        record.setValue(TARGET_FRAME_SCLK_COARSE, String.valueOf(this.sclkCoarse));
        record.setValue(TARGET_FRAME_SCLK_FINE, String.valueOf(this.sclkFine));
        record.setValue(TARGET_FRAME_ERT, getErt().toString());

        record.setValue(SUPPL_FRAME_ERT, this.getSuppErt().toString());
        record.setValue(SUPPL_FRAME_SCLK_COARSE, String.valueOf(this.getTkSclkCoarse()));
        record.setValue(SUPPL_FRAME_SCLK_FINE, String.valueOf(this.getTkSclkFine()));
        record.setValue(RF_ENCODING, this.getTkRfEncoding());
        record.setValue(VCID, String.valueOf(this.getTkVcid()));
        record.setValue(VCFC, String.valueOf(this.getTkVcfc()));

        if (this.getMcfc() != -1) {
            record.setValue(MCFC, String.valueOf(this.getMcfc()));
        }

        record.setValue(DATA_RATE_BPS, getTkDataRateBpsAsRoundedString());
        record.setValue(FRAME_SIZE_BITS, this.isFrameSizeBitsSet() ? String.valueOf(this.getFrameSizeBits()) : "-");

        record.setValue(TARGET_FRAME_UTC, getErtStr());

        return record;
    }

    public String toString() {
        return          "--- FrameSample ---\n" +
                        "sclkCoarse (target)     = " + this.sclkCoarse                                  + "\n" +
                        "sclkFine (target)       = " + this.sclkFine                                    + "\n" +
                        "ert (target)            = " + this.ert.toString()                              + "\n" +
                        "ertExplicitlySet        = " + this.ertExplicitlySet                            + "\n" +
                        "ertStr  (target)        = " + this.ertStr                                      + "\n" +
                        "scet                    = " + this.scet                                        + "\n" +
                        "pathId                  = " + this.pathId                                      + "\n" +
                        "vcid                    = " + this.vcid                                        + "\n" +
                        "vcfc                    = " + this.vcfc                                        + "\n" +
                        "mcfc                    = " + this.mcfc                                        + "\n" +
                        "tkSclkCoarse (suppl)    = " + this.tkSclkCoarse                                + "\n" +
                        "tkSclkFine (suppl)      = " + this.tkSclkFine                                  + "\n" +
                        "tkVcid                  = " + this.tkVcid                                      + "\n" +
                        "tkVcfc                  = " + this.tkVcfc                                      + "\n" +
                        "tkDataRateBps           = " + getTkDataRateBpsAsRoundedString()                + "\n" +
                        "tkRfEncoding            = " + this.tkRfEncoding.toString()                     + "\n" +
                        "tkIsValid               = " + this.tkIsValid                                   + "\n" +
                        "suppErt (suppl)         = " + this.suppErt.toString()                          + "\n" +
                        "suppErtExplicitlySet    = " + this.suppErtExplicitlySet                        + "\n" +
                        "suppErtStr (suppl)      = " + this.suppErtStr                                  + "\n" +
                        "suppErtStrExplicitlySet = " + this.suppErtStrExplicitlySet                     + "\n" +
                        "suppMcfc                = " + this.suppMcfc                                    + "\n" +
                        "derivedTdBe             = " + this.derivedTdBe                                 + "\n" +
                        "frameSizeBits           = " + this.frameSizeBits                               + "\n" +
                        "-------------------\n";
    }

    public String toSummaryString(String groundStationId) {
        StringBuilder sb = new StringBuilder("\n");

        sb.append(String.format(    "\tTarget VCID                   : %d\n", this.vcid));

        if (this.vcfc != -1) {
            sb.append(String.format("\tTarget VCFC                   : %d\n", this.vcfc));
        }

        if (this.mcfc != -1) {
            sb.append(String.format("\tTarget MCFC                   : %d\n", this.mcfc));
        }

        sb.append(String.format(    "\tTarget ERT                    : %s\n", this.getErtStr()));
        sb.append(String.format(    "\tTarget Ground Station         : %s\n", groundStationId));

        sb.append(String.format(    "\tSupplemental VCID             : %d\n", this.suppVcid));

        if (this.suppVcfc != -1) {
            sb.append(String.format("\tSupplemental VCFC             : %d\n", this.suppVcfc));
        }

        if (this.suppMcfc != -1) {
            sb.append(String.format("\tSupplemental VCFC             : %d\n", this.suppMcfc));
        }

        sb.append(String.format(    "\tSupplemental ERT              : %s\n", this.getSuppErtStr()));
        sb.append(String.format(    "\tSupplemental TK SCLK Coarse   : %d\n", this.tkSclkCoarse));
        sb.append(String.format(    "\tSupplemental TK SCLK Fine     : %d\n", this.tkSclkFine));
        sb.append(String.format(    "\tSupplemental TK VCID          : %d\n", this.tkVcid));
        sb.append(String.format(    "\tSupplemental TK VCFC          : %d\n", this.tkVcfc));

        return sb.toString();
    };
}