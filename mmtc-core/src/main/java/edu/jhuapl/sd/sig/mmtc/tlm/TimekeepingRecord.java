package edu.jhuapl.sd.sig.mmtc.tlm;

import java.math.BigDecimal;

/**
 * Represents the data from a single timekeeping packet.
 */
public class TimekeepingRecord {
    private int sclkCoarse;
    private int sclkFine;
    private boolean invalidFlag;
    private int targetFrameVcid;
    private int targetFrameVcfc;
    private int encodingMethod;
    private BigDecimal downlinkDataRate;

    /**
     * Initialization constructor. Instantiates an object holding the given timekeeping packet field values.
     *
     * @param sclkCoarse The value of the BIT 0 Timestamp (seconds) field
     * @param sclkFine The value of the BIT 0 Timestamp (subseconds) field
     * @param invalidFlag The value of the Target Frame SCLK/MET Missed Flag field
     * @param targetFrameVcid The value of the Target Frame VCID field
     * @param targetFrameVcfc The value of the Target Frame VCFC field
     * @param encodingMethod The value of the Target Frame Encoding Mode field
     * @param downlinkDataRate The value of the Target Frame Downlink Rate field
     */
    public TimekeepingRecord(
            int sclkCoarse,
            int sclkFine,
            boolean invalidFlag,
            int targetFrameVcid,
            int targetFrameVcfc,
            int encodingMethod,
            BigDecimal downlinkDataRate
    ) {
        this.sclkCoarse = sclkCoarse;
        this.sclkFine = sclkFine;
        this.invalidFlag = invalidFlag;
        this.targetFrameVcid = targetFrameVcid;
        this.targetFrameVcfc = targetFrameVcfc;
        this.encodingMethod =encodingMethod;
        this.downlinkDataRate = downlinkDataRate;
    }

    public int getSclkCoarse() {
        return sclkCoarse;
    }

    public int getSclkFine() {
        return sclkFine;
    }

    public boolean getInvalidFlag() {
        return invalidFlag;
    }

    public int getTargetFrameVcid() {
        return targetFrameVcid;
    }

    public int getTargetFrameVcfc() {
        return targetFrameVcfc;
    }

    public int getEncodingMethod() {
        return encodingMethod;
    }

    public BigDecimal getDownlinkDataRate() {
        return downlinkDataRate;
    }
}