package edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk;

import edu.jhuapl.sd.sig.mmtc.products.model.kernel.KernelValueFormat;

import java.util.regex.Matcher;

import static edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel.TRIPLET_LINE_PATTERN;

public class SclkCoefficientFormat {
    final int encSclkRightAlignedIndex;
    final KernelValueFormat encSclkFormat;
    final int encSclkScale;

    final int preTdtSpaceCount;
    final KernelValueFormat tdtFormat;
    final int tdtScale;

    final int preClkRateSpaceCount;
    final KernelValueFormat clkRateFormat;
    final int clkRateScale;

    final int postClkRateSpaceCount;

    public SclkCoefficientFormat(
            int encSclkRightAlignedIndex,
            KernelValueFormat encSclkFormat,
            int encSclkScale,

            int preTdtSpaceCount,
            KernelValueFormat tdtFormat,
            int tdtScale,

            int preClkRateSpaceCount,
            KernelValueFormat clkRateFormat,
            int clkRateScale,

            int postClkRateSpaceCount
    ) {
        this.encSclkRightAlignedIndex = encSclkRightAlignedIndex;
        this.encSclkFormat = encSclkFormat;
        this.encSclkScale = encSclkScale;

        this.preTdtSpaceCount = preTdtSpaceCount;
        this.tdtFormat = tdtFormat;
        this.tdtScale = tdtScale;

        this.preClkRateSpaceCount = preClkRateSpaceCount;
        this.clkRateFormat = clkRateFormat;
        this.clkRateScale = clkRateScale;

        this.postClkRateSpaceCount = postClkRateSpaceCount;
    }

    public static SclkCoefficientFormat parseFormat(String tripletLine) {
        final Matcher matcher = TRIPLET_LINE_PATTERN.matcher(tripletLine);

        if (! matcher.matches()) {
            throw new IllegalStateException("Line does not match expected format: " + tripletLine);
        }

        // encoded SCLK
        final String spacesBeforeEncSclk = matcher.group(1);
        final String encSclk = matcher.group(2);
        final KernelValueFormat encSclkFormat = KernelValueFormat.formatOf(encSclk);
        final int encSclkScale;
        switch(encSclkFormat) {
            case SCIENTIFIC_NOTATION:
                encSclkScale = encSclk.substring(encSclk.indexOf(".") + 1, encSclk.indexOf("E")).length();
                break;
            case INTEGER:
                encSclkScale = 0;
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + encSclkFormat);
        }

        // TDT
        final String spacesBeforeTdt = matcher.group(3);
        final String tdt = matcher.group(4);
        final KernelValueFormat tdtFormat = KernelValueFormat.formatOf(tdt);
        final int tdtScale;
        switch(tdtFormat){
            case SCIENTIFIC_NOTATION:
                tdtScale = tdt.substring(tdt.indexOf(".") + 1, tdt.indexOf("E")).length();
                break;
            case CAL_STR:
                tdtScale = tdt.substring(tdt.indexOf(".") + 1).length();
                break;
            case FLOAT:
                tdtScale = tdt.substring(tdt.indexOf(".") + 1).length();
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + tdtFormat);
        }

        // CLKRATE
        final String spacesBeforeClkRate = matcher.group(5);
        final String clkRate = matcher.group(6);
        final String postClkRateSpaces = matcher.group(7);
        final KernelValueFormat clkRateFormat = KernelValueFormat.formatOf(clkRate);
        final int clkRateScale;
        switch(clkRateFormat){
            case SCIENTIFIC_NOTATION:
                clkRateScale = clkRate.substring(clkRate.indexOf(".") + 1, clkRate.indexOf("E")).length();
                break;
            case FLOAT:
                clkRateScale = clkRate.substring(clkRate.indexOf(".") + 1).length();
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + clkRateFormat);
        }

        return new SclkCoefficientFormat(
                spacesBeforeEncSclk.length() + encSclk.length(),
                encSclkFormat,
                encSclkScale,
                spacesBeforeTdt.length(),
                tdtFormat,
                tdtScale,
                spacesBeforeClkRate.length(),
                clkRateFormat,
                clkRateScale,
                postClkRateSpaces.length()
        );
    }
}
