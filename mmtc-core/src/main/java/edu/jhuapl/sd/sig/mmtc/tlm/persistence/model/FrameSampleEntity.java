package edu.jhuapl.sd.sig.mmtc.tlm.persistence.model;

import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.util.CdsTimeCode;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A Java bean, ready for use as a DB entity with JDBI and sqlite.
 * See semantic definitions in {@link FrameSample}
 */
public class FrameSampleEntity {

    public static final List<String> FIELD_NAMES = Arrays.asList(
            "ertEpochMs",
            "sclkCoarse",
            "sclkFine",
            "ert",
            "ertExplicitlySet",
            "ertStr",
            "ertStrExplicitlySet",
            "scet",
            "pathId",
            "vcid",
            "vcfc",
            "mcfc",
            "tkSclkCoarse",
            "tkSclkFine",
            "tkVcid",
            "tkVcfc",
            "tkDataRateBps",
            "tkRfEncoding",
            "tkIsValid",
            "suppVcid",
            "suppVcfc",
            "suppMcfc",
            "suppErt",
            "suppErtExplicitlySet",
            "suppErtStr",
            "suppErtStrExplicitlySet",
            "frameSizeBits"
    );

    // Fields unique to FrameSampleEntity

    /**
     * ERT as a number of ms since the Java epoch
     * Usage: querying from the FrameSample cache, if enabled
     */
    private long ertEpochMs;

    // Fields replicated from FrameSample

    private int sclkCoarse;
    private int sclkFine;
    private String ert;
    private boolean ertExplicitlySet;
    private String ertStr;
    private boolean ertStrExplicitlySet;
    private String scet;
    private int pathId;
    private int vcid;
    private int vcfc;
    private int mcfc;
    private int tkSclkCoarse;
    private int tkSclkFine;
    private int tkVcid;
    private int tkVcfc;
    private BigDecimal tkDataRateBps;
    private String tkRfEncoding;
    private FrameSample.ValidState tkIsValid;
    private int suppVcid;
    private int suppVcfc;
    private int suppMcfc;
    private String suppErt;
    private boolean suppErtExplicitlySet;
    private String suppErtStr;
    private boolean suppErtStrExplicitlySet;
    private int frameSizeBits;

    /*
     * Fields that are purposefully not replicated from FrameSample
     */
    // derivedTdBe

    // Used by JDBI
    public FrameSampleEntity() {
        this.sclkCoarse = -1;
        this.sclkFine = -1;
        this.ert = "";
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
        this.tkIsValid = FrameSample.ValidState.UNSET;
        this.suppVcid = -1;
        this.suppVcfc = -1;
        this.suppMcfc = -1;
        this.suppErt = "";
        this.suppErtExplicitlySet = false;
        this.suppErtStr = "";
        this.suppErtStrExplicitlySet = false;
        this.frameSizeBits = -1;
    }

    public static FrameSampleEntity fromFrameSample(FrameSample fs) {
        final FrameSampleEntity fse = new FrameSampleEntity();

        fse.setSclkCoarse(fs.getSclkCoarse());
        fse.setSclkFine(fs.getSclkFine());

        try {
            if (fs.isErtExplicitlySet()) {
                fse.setErt(fs.getErt().toString());
                fse.setErtExplicitlySet(true);
                fse.setErtEpochMs(TimeConvert.parseIsoDoyUtcStr(TimeConvert.cdsToIsoUtc(fs.getErt())).toInstant().toEpochMilli());
            } else if (fs.isErtStrExplicitlySet()) {
                fse.setErtStr(fs.getErtStr());
                fse.setErtStrExplicitlySet(true);
                fse.setErtEpochMs(TimeConvert.parseIsoDoyUtcStr(fs.getErtStr()).toInstant().toEpochMilli());
            }
        } catch (TimeConvertException e) {
            throw new RuntimeException(e);
        }

        fse.setScet(fs.getScet());
        fse.setPathId(fs.getPathId());
        fse.setVcid(fs.getVcid());
        fse.setVcfc(fs.getVcfc());
        fse.setMcfc(fs.getMcfc());
        fse.setTkSclkCoarse(fs.getTkSclkCoarse());
        fse.setTkSclkFine(fs.getTkSclkFine());
        fse.setTkVcid(fs.getTkVcid());
        fse.setTkVcfc(fs.getTkVcfc());
        fse.setTkDataRateBps(fs.getTkDataRateBps());
        fse.setTkRfEncoding(fs.getTkRfEncoding());

        if (fs.getTkValid() != FrameSample.ValidState.UNSET) {
            fse.setTkIsValid(fs.getTkValid());
        }

        fse.setSuppVcid(fs.getSuppVcid());
        fse.setSuppVcfc(fs.getSuppVcfc());
        fse.setSuppMcfc(fs.getSuppMcfc());

        if (fs.isSuppErtExplicitlySet()) {
            fse.setSuppErt(fs.getSuppErt().toString());
            fse.setSuppErtExplicitlySet(true);
        } else if (fs.isSuppErtStrExplicitlySet()) {
            fse.setSuppErtStr(fs.getSuppErtStr());
            fse.setSuppErtStrExplicitlySet(true);
        }

        fse.setFrameSizeBits(fs.getFrameSizeBits());

        return fse;
    }

    public FrameSample toFrameSample() {
        final FrameSample fs = new FrameSample();

        fs.setSclkCoarse(this.sclkCoarse);
        fs.setSclkFine(this.sclkFine);

        if (this.ertExplicitlySet) {
            fs.setErt(new CdsTimeCode(this.ert));
        } else if (this.ertStrExplicitlySet) {
            fs.setErtStr(this.ertStr);
        }

        fs.setScet(this.scet);
        fs.setPathId(this.pathId);
        fs.setVcid(this.vcid);
        fs.setVcfc(this.vcfc);
        fs.setMcfc(this.mcfc);
        fs.setTkSclkCoarse(this.tkSclkCoarse);
        fs.setTkSclkFine(this.tkSclkFine);
        fs.setTkVcid(this.tkVcid);
        fs.setTkVcfc(this.tkVcfc);
        fs.setTkDataRateBps(this.tkDataRateBps);
        fs.setTkRfEncoding(this.tkRfEncoding);

        if (! this.tkIsValid.equals(FrameSample.ValidState.UNSET)) {
            fs.setTkValid(this.tkIsValid.equals(FrameSample.ValidState.VALID));
        }

        fs.setSuppVcid(this.suppVcid);
        fs.setSuppVcfc(this.suppVcfc);
        fs.setSuppMcfc(this.suppMcfc);

        if (this.suppErtExplicitlySet) {
            fs.setSuppErt(new CdsTimeCode(this.suppErt));
        } else if (this.suppErtStrExplicitlySet) {
            fs.setSuppErtStr(this.suppErtStr);
        }

        fs.setFrameSizeBits(this.frameSizeBits);

        return fs;
    }

    public long getErtEpochMs() {
        return ertEpochMs;
    }

    public void setErtEpochMs(long ertEpochMs) {
        this.ertEpochMs = ertEpochMs;
    }

    public int getSclkCoarse() {
        return sclkCoarse;
    }

    public void setSclkCoarse(int sclkCoarse) {
        this.sclkCoarse = sclkCoarse;
    }

    public int getSclkFine() {
        return sclkFine;
    }

    public void setSclkFine(int sclkFine) {
        this.sclkFine = sclkFine;
    }

    public String getErt() {
        return ert;
    }

    public void setErt(String ert) {
        this.ert = ert;
    }

    public boolean isErtExplicitlySet() {
        return ertExplicitlySet;
    }

    public void setErtExplicitlySet(boolean ertExplicitlySet) {
        this.ertExplicitlySet = ertExplicitlySet;
    }

    public String getErtStr() {
        return ertStr;
    }

    public void setErtStr(String ertStr) {
        this.ertStr = ertStr;
    }

    public boolean isErtStrExplicitlySet() {
        return ertStrExplicitlySet;
    }

    public void setErtStrExplicitlySet(boolean ertStrExplicitlySet) {
        this.ertStrExplicitlySet = ertStrExplicitlySet;
    }

    public String getScet() {
        return scet;
    }

    public void setScet(String scet) {
        this.scet = scet;
    }

    public int getPathId() {
        return pathId;
    }

    public void setPathId(int pathId) {
        this.pathId = pathId;
    }

    public int getVcid() {
        return vcid;
    }

    public void setVcid(int vcid) {
        this.vcid = vcid;
    }

    public int getVcfc() {
        return vcfc;
    }

    public void setVcfc(int vcfc) {
        this.vcfc = vcfc;
    }

    public int getMcfc() {
        return mcfc;
    }

    public void setMcfc(int mcfc) {
        this.mcfc = mcfc;
    }

    public int getTkSclkCoarse() {
        return tkSclkCoarse;
    }

    public void setTkSclkCoarse(int tkSclkCoarse) {
        this.tkSclkCoarse = tkSclkCoarse;
    }

    public int getTkSclkFine() {
        return tkSclkFine;
    }

    public void setTkSclkFine(int tkSclkFine) {
        this.tkSclkFine = tkSclkFine;
    }

    public int getTkVcid() {
        return tkVcid;
    }

    public void setTkVcid(int tkVcid) {
        this.tkVcid = tkVcid;
    }

    public int getTkVcfc() {
        return tkVcfc;
    }

    public void setTkVcfc(int tkVcfc) {
        this.tkVcfc = tkVcfc;
    }

    public BigDecimal getTkDataRateBps() {
        return tkDataRateBps;
    }

    public void setTkDataRateBps(BigDecimal tkDataRateBps) {
        this.tkDataRateBps = tkDataRateBps;
    }

    public String getTkRfEncoding() {
        return tkRfEncoding;
    }

    public void setTkRfEncoding(String tkRfEncoding) {
        this.tkRfEncoding = tkRfEncoding;
    }

    public FrameSample.ValidState getTkIsValid() {
        return tkIsValid;
    }

    public void setTkIsValid(FrameSample.ValidState tkIsValid) {
        this.tkIsValid = tkIsValid;
    }

    public int getSuppVcid() {
        return suppVcid;
    }

    public void setSuppVcid(int suppVcid) {
        this.suppVcid = suppVcid;
    }

    public int getSuppVcfc() {
        return suppVcfc;
    }

    public void setSuppVcfc(int suppVcfc) {
        this.suppVcfc = suppVcfc;
    }

    public int getSuppMcfc() {
        return suppMcfc;
    }

    public void setSuppMcfc(int suppMcfc) {
        this.suppMcfc = suppMcfc;
    }

    public String getSuppErt() {
        return suppErt;
    }

    public void setSuppErt(String suppErt) {
        this.suppErt = suppErt;
    }

    public boolean isSuppErtExplicitlySet() {
        return suppErtExplicitlySet;
    }

    public void setSuppErtExplicitlySet(boolean suppErtExplicitlySet) {
        this.suppErtExplicitlySet = suppErtExplicitlySet;
    }

    public String getSuppErtStr() {
        return suppErtStr;
    }

    public void setSuppErtStr(String suppErtStr) {
        this.suppErtStr = suppErtStr;
    }

    public boolean isSuppErtStrExplicitlySet() {
        return suppErtStrExplicitlySet;
    }

    public void setSuppErtStrExplicitlySet(boolean suppErtStrExplicitlySet) {
        this.suppErtStrExplicitlySet = suppErtStrExplicitlySet;
    }

    public int getFrameSizeBits() {
        return frameSizeBits;
    }

    public void setFrameSizeBits(int frameSizeBits) {
        this.frameSizeBits = frameSizeBits;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FrameSampleEntity that = (FrameSampleEntity) o;
        return ertEpochMs == that.ertEpochMs && sclkCoarse == that.sclkCoarse && sclkFine == that.sclkFine && ertExplicitlySet == that.ertExplicitlySet && ertStrExplicitlySet == that.ertStrExplicitlySet && pathId == that.pathId && vcid == that.vcid && vcfc == that.vcfc && mcfc == that.mcfc && tkSclkCoarse == that.tkSclkCoarse && tkSclkFine == that.tkSclkFine && tkVcid == that.tkVcid && tkVcfc == that.tkVcfc && suppVcid == that.suppVcid && suppVcfc == that.suppVcfc && suppMcfc == that.suppMcfc && suppErtExplicitlySet == that.suppErtExplicitlySet && suppErtStrExplicitlySet == that.suppErtStrExplicitlySet && frameSizeBits == that.frameSizeBits && Objects.equals(ert, that.ert) && Objects.equals(ertStr, that.ertStr) && Objects.equals(scet, that.scet) && Objects.equals(tkDataRateBps, that.tkDataRateBps) && Objects.equals(tkRfEncoding, that.tkRfEncoding) && tkIsValid == that.tkIsValid && Objects.equals(suppErt, that.suppErt) && Objects.equals(suppErtStr, that.suppErtStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ertEpochMs, sclkCoarse, sclkFine, ert, ertExplicitlySet, ertStr, ertStrExplicitlySet, scet, pathId, vcid, vcfc, mcfc, tkSclkCoarse, tkSclkFine, tkVcid, tkVcfc, tkDataRateBps, tkRfEncoding, tkIsValid, suppVcid, suppVcfc, suppMcfc, suppErt, suppErtExplicitlySet, suppErtStr, suppErtStrExplicitlySet, frameSizeBits);
    }
}
