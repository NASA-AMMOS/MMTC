package edu.jhuapl.sd.sig.mmtc.app;

import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationRunConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.Owlt;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public class TimeCorrelationTarget {
    // input values
    private final List<FrameSample> sampleSet;
    private final FrameSample targetSample;
    private final TimeCorrelationRunConfig config;
    private final int tk_sclk_fine_tick_modulus;

    // computed values, assigned in `computeCorrelationValues` below
    private String groundStationId;
    private OffsetDateTime ert;
    private double owlt;
    private Double encSclk;
    private Double etG;
    private double tf_offset;
    private String ertGCalcLogStatement;
    private Double tdtG;

    public TimeCorrelationTarget(List<FrameSample> sampleSet, TimeCorrelationRunConfig config, int tk_sclk_fine_tick_modulus) throws MmtcException {
        this.sampleSet = sampleSet;
        this.targetSample = sampleSet.get(sampleSet.size() / 2);
        this.tk_sclk_fine_tick_modulus = tk_sclk_fine_tick_modulus;
        this.config = config;

        try {
            computeCorrelationValues();
        } catch (TimeConvertException e) {
            throw new MmtcException("Unable to calculate required values", e);
        }
    }

    private void computeCorrelationValues() throws MmtcException, TimeConvertException {
        this.groundStationId = config.getStationId(targetSample.getPathId());

        Double stationErtEt = TimeConvert.utcToEt(targetSample.getErtStr());
        this.ert = TimeConvert.parseIsoDoyUtcStr(targetSample.getErtStr());

        // Compute the downlink One-Way Light travel Time (OWLT) for the target frame. If in test mode,
        // set it to the value provided in the command line.
        if (config.isTestMode()) {
            owlt = config.getTestModeOwlt();
        } else {
            int gsNaifId = TimeConvert.nameToNaifId(groundStationId);
            owlt = Owlt.getDownlinkOwlt(gsNaifId, config.getNaifSpacecraftId(), stationErtEt);
        }

        // Convert the SCLK to encoded SCLK. The fine part of the SCLK is set to zero.
        this.encSclk = TimeConvert.sclkToEncSclk(
                config.getNaifSpacecraftId(),
                config.getSclkPartition(TimeConvert.parseIsoDoyUtcStr(targetSample.getErtStr())),
                targetSample.getTkSclkCoarse(),
                0
        );

        // Check for invalid zero-value SCLK values.
        if (targetSample.getTkSclkCoarse() < 1.) {
            throw new MmtcException("ERROR: Raw SCLK value " + targetSample.getTkSclkCoarse() + " is invalid.");
        }
        if (encSclk < 1.) {
            throw new MmtcException("ERROR: Computed encoded SCLK value " + encSclk + " is invalid.");
        }

        // Compute the ET(G) (i.e. TDB(G)) value for the target frame.
        double td_be = targetSample.getDerivedTdBe();
        this.etG = computeErt_g(owlt, stationErtEt, td_be);

        // Compute TDT(G) from ERT in ET form.
        this.tdtG = TimeConvert.etToTdt(etG);
        if (tdtG < 1.) {
            throw new MmtcException("ERROR: Invalid computed TDT(G): " + tdtG);
        }
    }

    public List<FrameSample> getSampleSet() {
        return Collections.unmodifiableList(sampleSet);
    }

    public FrameSample getTargetSample() {
        return targetSample;
    }

    /**
     * Computes the ground receipt time in Ephemeris Time. It applies the formula
     * adjusted Ground Time = Station ERT - TD_be - TD_sc - TF_offset, where
     * <ul>
     *     <li> adjusted Ground Time is the ground time in ET after subtracting the other terms
     *     <li> Station ERT is the raw ERT converted to ET
     *     <li> TD_be is the bitrate-dependent time delay error
     *     <li> owlt is the one-way light travel time
     *     <li> TD_sc is the time delay within the spacecraft read from configuration parameters
     *     <li> TF_offset is the offset from the reference event on the spacecraft to reported SCLK
     * </ul>
     *
     * @param owlt         IN the downlink One-Way Light Time
     * @param stationErtEt   IN the Earth Received Time in ET
     * @param td_be        IN the bitrate-dependent time delay
     * @return             the adjusted Earth Received Time adjusted
     * @throws TimeConvertException if any values could not be computed
     */
    private Double computeErt_g(Double owlt, Double stationErtEt, Double td_be) {
        final Integer vSclk = targetSample.getTkSclkFine();

        // Compute the offset of the SCLK to the spacecraft reference event in seconds.
        this.tf_offset = ((double)vSclk + 0.5)/(double) tk_sclk_fine_tick_modulus;

        double td_sc = config.getSpacecraftTimeDelaySec();

        // Compute the adjusted ERT by subtracting out the OWLT and other terms.
        double adjErt = stationErtEt - td_be - owlt - td_sc - tf_offset;

        this.ertGCalcLogStatement = "Adjusted ERT calculation:\n" +
                "vSclk        : " + vSclk + "\n" +
                "tf_offset_sclk_modulus: " + tk_sclk_fine_tick_modulus + "\n" +
                "tf_offset    : " + tf_offset + "\n" +
                "stationErtEt : " + stationErtEt + "\n" +
                "td_be        : " + td_be  + "\n" +
                "owlt         : " + owlt + "\n" +
                "td_sc        : " + td_sc + "\n" +
                "adjErt       : " + adjErt;

        return adjErt;
    }

    public double getTargetSampleOwlt() {
        return owlt;
    }

    public double getTargetSampleEncSclk() {
        return encSclk;
    }

    public double getTargetSampleEtG() {
        return etG;
    }

    public String getTargetSampleErtGCalcLogStatement() {
        return ertGCalcLogStatement;
    }

    public double getTargetSampleTfOffset() {
        return tf_offset;
    }

    public double getTargetSampleTdtG() {
        return tdtG;
    }

    public String getTargetSampleGroundStationId() {
        return groundStationId;
    }

    public OffsetDateTime getTargetSampleErt() {
        return ert;
    }
}
