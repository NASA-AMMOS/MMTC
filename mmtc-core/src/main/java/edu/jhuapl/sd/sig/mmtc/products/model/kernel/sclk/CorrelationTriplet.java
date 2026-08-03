package edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk;

import edu.jhuapl.sd.sig.mmtc.products.model.kernel.KernelValueFormat;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Matcher;

import static edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.SclkKernel.TRIPLET_LINE_PATTERN;

public class CorrelationTriplet {
    // 0-based on SCLK epoch
    public final BigDecimal encSclk;

    // TDT seconds past the J2000 epoch
    public final BigDecimal tdt;

    // Rate in TDT (seconds) / most significant clock count
    public final BigDecimal clkChgRate;

    public CorrelationTriplet(double encSclk, double tdt, double clkChgRate) {
        this(new BigDecimal(encSclk), new BigDecimal(tdt), new BigDecimal(clkChgRate));
    }

    public CorrelationTriplet(BigDecimal encSclk, BigDecimal tdt, BigDecimal clkChgRate) {
        this.encSclk = encSclk;
        this.tdt = tdt;
        this.clkChgRate = clkChgRate;
    }

    // trim leading and trailing spaces, and leave only a single space between values
    public String formatWithSingleSpaces(SclkCoefficientFormat fmt) throws TimeConvertException {
        return format(fmt).trim().replaceAll(" {2,}", " ");
    }

    public String format(SclkCoefficientFormat fmt) throws TimeConvertException {
        String outputLine = "";

        switch (fmt.encSclkFormat) {
            case SCIENTIFIC_NOTATION:
                outputLine += String.format("%" + fmt.encSclkRightAlignedIndex + "." + fmt.encSclkScale + "E", encSclk.doubleValue());
                break;
            case INTEGER:
                outputLine += String.format("%" + fmt.encSclkRightAlignedIndex + "d", (long) Math.floor(encSclk.doubleValue()));
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + fmt.encSclkFormat);
        }

        // pre-TDT padding
        outputLine = appendSpaces(outputLine, fmt.preTdtSpaceCount);

        switch (fmt.tdtFormat) {
            case SCIENTIFIC_NOTATION:
                outputLine += String.format("%." + fmt.tdtScale + "E", tdt.doubleValue());
                break;
            case CAL_STR:
                outputLine += "@" + getTdtCalStr(fmt.tdtScale);
                break;
            case FLOAT:
                outputLine += String.format("%." + fmt.tdtScale + "f", tdt.doubleValue());
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + fmt.tdtFormat);
        }

        // pre-clkrate padding
        outputLine = appendSpaces(outputLine, fmt.preClkRateSpaceCount);

        switch (fmt.clkRateFormat) {
            case SCIENTIFIC_NOTATION:
                outputLine += String.format("%." + fmt.clkRateScale + "E", clkChgRate.doubleValue());
                break;
            case FLOAT:
                outputLine += String.format("%." + fmt.clkRateScale + "f", clkChgRate.doubleValue());
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + fmt.clkRateFormat);
        }

        // post-clkrate padding
        outputLine = appendSpaces(outputLine, fmt.postClkRateSpaceCount);

        return outputLine;
    }

    public double getEncSclk() {
        return this.encSclk.doubleValue();
    }

    public long getEncSclkAsLong() {
        return (long) Math.floor(this.encSclk.doubleValue());
    }

    public double getTdt() {
        return this.tdt.doubleValue();
    }

    public String getTdtCalStr() throws TimeConvertException {
        return TimeConvert.tdtToTdtCalStr(this.tdt.doubleValue());
    }

    public String getTdtCalStr(int subsecPrecision) throws TimeConvertException {
        return TimeConvert.tdtToTdtCalStr(this.tdt.doubleValue(), subsecPrecision);
    }

    public double getClkChgRate() {
        return this.clkChgRate.doubleValue();
    }

    public static CorrelationTriplet parse(String tripletLine) throws TimeConvertException {
        final Matcher matcher = TRIPLET_LINE_PATTERN.matcher(tripletLine);

        if (! matcher.matches()) {
            throw new IllegalStateException("Line does not match expected format: '" + tripletLine + "'");
        }

        // encoded SCLK
        final String encSclkStrAsRead = matcher.group(2);
        final KernelValueFormat encSclkFormat = KernelValueFormat.formatOf(encSclkStrAsRead);
        final BigDecimal encSclk;
        switch(encSclkFormat) {
            case SCIENTIFIC_NOTATION:
            case INTEGER:
                encSclk = new BigDecimal(encSclkStrAsRead);
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + encSclkFormat);
        }

        // TDT
        final String tdtStrAsRead = matcher.group(4);
        final KernelValueFormat tdtFormat = KernelValueFormat.formatOf(tdtStrAsRead);
        final BigDecimal tdt;
        switch(tdtFormat){
            case SCIENTIFIC_NOTATION:
            case FLOAT:
                tdt = new BigDecimal(tdtStrAsRead);
                break;
            case CAL_STR:
                tdt = new BigDecimal(TimeConvert.tdtCalStrToTdt(tdtStrAsRead.replace("@", "")));
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + tdtFormat);
        }

        // CLKRATE
        final String clkRateStrAsRead = matcher.group(6);
        final KernelValueFormat clkRateFormat = KernelValueFormat.formatOf(clkRateStrAsRead);
        final BigDecimal clkRate;
        switch(clkRateFormat){
            case SCIENTIFIC_NOTATION:
            case FLOAT:
                clkRate =  new BigDecimal(clkRateStrAsRead);
                break;
            default:
                throw new IllegalStateException("Unexpected value format: " + clkRateFormat);
        }

        return new CorrelationTriplet(encSclk, tdt, clkRate);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CorrelationTriplet that = (CorrelationTriplet) o;
        return Objects.equals(encSclk.doubleValue(), that.encSclk.doubleValue()) && Objects.equals(tdt.doubleValue(), that.tdt.doubleValue()) && Objects.equals(clkChgRate.doubleValue(), that.clkChgRate.doubleValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(encSclk.doubleValue(), tdt.doubleValue(), clkChgRate.doubleValue());
    }

    @Override
    public String toString() {
        return "CorrelationTriplet{" +
                "encSclk=" + encSclk +
                ", tdt=" + tdt +
                ", clkChgRate=" + clkChgRate +
                '}';
    }

    private static String appendSpaces(String str, int numSpaces) {
        String appendedStr = str;

        for (int i = 0; i < numSpaces; i++) {
            appendedStr += " ";
        }

        return appendedStr;
    }
}
