package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;

import java.nio.file.Path;
import java.util.*;

/**
 * Represents an instance of the Raw Telemetry Table CSV file, which is used to
 * store CCSDS frame header data in addition to target and supplemental frame
 * SCLK values. This class can be used as both a telemetry source and an
 * output.
 */
public class RawTelemetryTable extends AbstractTimeCorrelationTable {
    public static final String PATH_ID = "Path ID";
    public static final String TARGET_FRAME_ERT = "Target Frame ERT";
    public static final String TARGET_FRAME_UTC = "Target ERT String";
    public static final String TARGET_FRAME_SCLK_COARSE = "Target Secondary Hdr SCLK Coarse";
    public static final String TARGET_FRAME_SCLK_FINE = "Target Secondary Hdr SCLK Fine";
    public static final String SUPPL_FRAME_ERT = "Supp Frame ERT";
    public static final String SUPPL_FRAME_SCLK_COARSE = "Supp SCLK Coarse";
    public static final String SUPPL_FRAME_SCLK_FINE = "Supp SCLK Fine";
    public static final String RF_ENCODING = "Encoding Type";
    public static final String MCFC = "MCFC";
    public static final String VCFC = "VCFC";
    public static final String VCID = "VCID";
    public static final String DATA_RATE_BPS = "Data Rate BPS";
    public static final String FRAME_SIZE_BITS = "Frame Size Bits";

    public RawTelemetryTable(Path path) {
        super(path);
    }

    public List<String> getHeaders() {
        return Arrays.asList(
                RUN_TIME,
                PATH_ID,
                TARGET_FRAME_ERT,
                TARGET_FRAME_SCLK_COARSE,
                TARGET_FRAME_SCLK_FINE,
                SUPPL_FRAME_SCLK_COARSE,
                SUPPL_FRAME_SCLK_FINE,
                SUPPL_FRAME_ERT,
                RF_ENCODING,
                MCFC,
                VCID,
                VCFC,
                TARGET_FRAME_UTC,
                DATA_RATE_BPS,
                FRAME_SIZE_BITS
        );
    }

    /**
     * Write the current time correlation's sample set to the raw telemetry table.
     *
     * @param context the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if there's an unhandled issue writing the raw telemetry table
     * @return a ProductWriteResult describing the updated product
     */
    public static ProductWriteResult appendCorrelationFrameSamplesToRawTelemetryTable(TimeCorrelationContext context) throws MmtcException {
        final RawTelemetryTable rawTlmTable = new RawTelemetryTable(context.config.getRawTelemetryTablePath());

        for (FrameSample sample : context.correlation.target.get().getSampleSet()) {
            TableRecord rec = sample.toRawTelemetryTableRecord(rawTlmTable.getHeaders());
            rec.setValue(RawTelemetryTable.RUN_TIME, context.appRunTime.toString());
            rawTlmTable.writeRecord(rec);
        }

        return new ProductWriteResult(
                context.config.getRawTelemetryTablePath(),
                rawTlmTable.getLastLineNumber()
        );
    }
}
