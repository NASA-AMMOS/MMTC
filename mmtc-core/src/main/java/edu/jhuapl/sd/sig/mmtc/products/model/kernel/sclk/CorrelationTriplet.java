package edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk;

import edu.jhuapl.sd.sig.mmtc.products.model.kernel.KernelValueFormat;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.math.BigDecimal;
import java.util.regex.Matcher;

import static edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.NewSclkKernel.TRIPLET_LINE_PATTERN;

public class CorrelationTriplet {
    public final double encSclk;
    // todo convert internal tdtStr to a double, or leave the conversion at the kernel read/write boundary?
    // stored without '@' symbol
    public final String tdtStr;
    public final double clkChgRate;

    public CorrelationTriplet(double encSclk, String tdtStr, double clkChgRate) {
        this.encSclk = encSclk;
        this.tdtStr = tdtStr;
        this.clkChgRate = clkChgRate;
    }

    public String format(SclkCoefficientFormat fmt) throws TimeConvertException {
        String outputLine = "";

        switch (fmt.encSclkFormat) {
            case INTEGER:
                outputLine += String.format("%" + fmt.encSclkRightAlignedIndex + "d", (long) Math.floor(encSclk));
                break;
            case SCIENTIFIC_NOTATION:
                outputLine += String.format("%" + fmt.encSclkRightAlignedIndex + "." + fmt.encSclkScale + "E", encSclk);
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + fmt.encSclkFormat);
        }

        // pre-TDT padding
        outputLine = appendSpaces(outputLine, fmt.preTdtSpaceCount);

        switch (fmt.tdtFormat) {
            case SCIENTIFIC_NOTATION:
                // todo check this
                double tdtDbl = TimeConvert.tdtCalStrToTdt(tdtStr);
                outputLine += String.format("%." + fmt.tdtScale + "E", tdtDbl);
                break;
            case CAL_STR:
                // todo apply TDT precision here?
                outputLine += "@" + tdtStr;
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + fmt.tdtFormat);
        }

        // pre-clkrate padding
        outputLine = appendSpaces(outputLine, fmt.preClkRateSpaceCount);

        switch (fmt.clkRateFormat) {
            case SCIENTIFIC_NOTATION:
                outputLine += String.format("%." + fmt.clkRateScale + "E", clkChgRate);
                break;
            case FLOAT:
                outputLine += String.format("%." + fmt.clkRateScale + "f", clkChgRate);
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + fmt.clkRateFormat);
        }

        // post-clkrate padding
        outputLine = appendSpaces(outputLine, fmt.postClkRateSpaceCount);

        return outputLine;
    }

    public static CorrelationTriplet parse(String tripletLine) throws TimeConvertException {
        final Matcher matcher = TRIPLET_LINE_PATTERN.matcher(tripletLine);

        if (! matcher.matches()) {
            throw new IllegalStateException("Line does not match expected format: '" + tripletLine + "'");
        }

        // encoded SCLK
        final String encSclkStrAsRead = matcher.group(2);
        final KernelValueFormat encSclkFormat = KernelValueFormat.formatOf(encSclkStrAsRead);
        final double encSclk;
        switch(encSclkFormat) {
            case INTEGER:
                encSclk = Double.parseDouble(encSclkStrAsRead);
                break;
            case SCIENTIFIC_NOTATION:
                encSclk = new BigDecimal(encSclkStrAsRead).doubleValue();
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + encSclkFormat);
        }

        // TDT
        final String tdtStrAsRead = matcher.group(4);
        final KernelValueFormat tdtFormat = KernelValueFormat.formatOf(tdtStrAsRead);
        final String tdtStrVal;
        switch(tdtFormat){
            case SCIENTIFIC_NOTATION:
                // todo check whether this is correct
                tdtStrVal = TimeConvert.tdtToTdtCalStr(new BigDecimal(tdtStrAsRead).doubleValue());
                break;
            case CAL_STR:
                tdtStrVal = tdtStrAsRead.replace("@", "");
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + tdtFormat);
        }

        // CLKRATE
        final String clkRateStrAsRead = matcher.group(6);
        final KernelValueFormat clkRateFormat = KernelValueFormat.formatOf(clkRateStrAsRead);
        final double clkRate;
        switch(clkRateFormat){
            case SCIENTIFIC_NOTATION:
                clkRate =  new BigDecimal(clkRateStrAsRead).doubleValue();
                break;
            case FLOAT:
                clkRate =  new BigDecimal(clkRateStrAsRead).doubleValue();
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + clkRateFormat);
        }

        return new CorrelationTriplet(encSclk, tdtStrVal, clkRate);
    }

    private static String appendSpaces(String str, int numSpaces) {
        String appendedStr = str;

        for (int i = 0; i < numSpaces; i++) {
            appendedStr += " ";
        }

        return appendedStr;
    }
}
