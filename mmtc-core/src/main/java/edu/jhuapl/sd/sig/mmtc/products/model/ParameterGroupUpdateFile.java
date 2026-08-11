package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ParameterGroupUpdateFile extends AbstractTimeCorrelationTable {
    // a CSV with columns: associated Run ID, Run Time, related SCLK version number, then the five current uplinkcmdfile parameters

    public static final String RUN_TIME = "Run Time";
    public static final String RUN_ID = "Run ID";
    public static final String SCLK_VERSION = "Associated SCLK Kernel Version";
    public static final String SCLK_COARSE = "Coarse SCLK";
    public static final String ET = "ET";
    public static final String TDT = "TDT";
    public static final String TDT_CALENDAR = "TDT Calendar";
    public static final String CLK_CHG_RATE = "Clock Change Rate (s/s)";

    public static final List<String> COLUMNS = Collections.unmodifiableList(Arrays.asList(
        RUN_TIME,
        RUN_ID,
        SCLK_VERSION,
        SCLK_COARSE,
        ET,
        TDT,
        TDT_CALENDAR,
        CLK_CHG_RATE
    ));

    /**
     * Create the table file object from the specified path.
     *
     * @param path the path to the table
     */
    public ParameterGroupUpdateFile(Path path) {
        super(path);
    }

    @Override
    List<String> getHeaders() {
        return COLUMNS;
    }

    public static void generateNewParameterUpdateGroupRecord(TimeCorrelationContext ctx, ParameterGroupUpdateFile parameterGroupUpdateFile, TableRecord newRec) throws TimeConvertException {
        final DecimalFormat gtFmt = new DecimalFormat("#.000000");
        final DecimalFormat clkChgFmt = new DecimalFormat("#.00000000000");

        newRec.setValue(RUN_TIME,           TimeConvert.timeToIsoUtcString(ctx.appRunTime));
        if (ctx.config.isDryRun()) {
            newRec.setValue(RUN_ID, "-");
        } else {
            newRec.setValue(RUN_ID, String.format("%05d", ctx.runId.get()));
        }
        newRec.setValue(SCLK_VERSION,       ctx.newSclkVersionString.get());
        newRec.setValue(SCLK_COARSE,        Long.toString(ctx.correlation.target.get().getTargetSample().getTkSclkCoarse()));
        newRec.setValue(ET,                 gtFmt.format(ctx.correlation.target.get().getTargetSampleEtG()));
        newRec.setValue(TDT,                gtFmt.format(ctx.correlation.target.get().getTargetSampleTdtG()));
        newRec.setValue(TDT_CALENDAR,       TimeConvert.tdtToTdtCalStr(ctx.correlation.target.get().getTargetSampleTdtG()));
        newRec.setValue(CLK_CHG_RATE,       clkChgFmt.format(ctx.correlation.predicted_clock_change_rate.get()));
    }

    public static ProductWriteResult appendRowFor(TimeCorrelationContext ctx) throws MmtcException {
        final ParameterGroupUpdateFile parameterFile = new ParameterGroupUpdateFile(ctx.config.getParameterGroupUpdateFilePath());
        final TableRecord newRec = new TableRecord(COLUMNS);

        try {
            generateNewParameterUpdateGroupRecord(ctx, parameterFile, newRec);
            parameterFile.writeRecord(newRec);
        } catch (TimeConvertException e) {
            throw new MmtcException(e);
        }

        return new ProductWriteResult(
                parameterFile.getPath(),
                parameterFile.getLastLineNumber()
        );
    }
}
