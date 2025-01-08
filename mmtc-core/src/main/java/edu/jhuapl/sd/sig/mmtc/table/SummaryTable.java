package edu.jhuapl.sd.sig.mmtc.table;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an instance of the Summary Table, which contains a combination of
 * raw telemetry data and computed values related to the SCLK kernel.
 */
public class SummaryTable extends AbstractTimeCorrelationTable {
    public static final String STATION_ID = "Ground Station ID";
    public static final String TARGET_FRAME_ERT = "Target Frame ERT";
    public static final String TARGET_FRAME_UTC = "Target Frame UTC";
    public static final String TARGET_FRAME_VCID = "Target Frame VCID";
    public static final String TARGET_FRAME_VCFC = "Target Frame VCFC";
    public static final String TARGET_FRAME_ENC_SCLK = "TK Encoded SCLK"; // TODO: This is from the supp frame or TK pkt. Should change constant name.
    public static final String SCLK_PARTITION_NUM = "SCLK Partition";
    public static final String CLK_CHANGE_RATE_PREDICTED = "Predicted Clk Change Rate";
    public static final String CLK_CHANGE_RATE_INTERP = "Interpolated Clk Change Rate";
    public static final String OWLT_SEC = "OWLT (sec)";
    public static final String TDT_G = "TDT(G)";
    public static final String TDT_G_STR = "TDT(G) String";
    public static final String SPACECRAFT_TIME_DELAY = "TD SC (sec)";
    public static final String BITRATE_TIME_ERROR = "TD BE (sec)";
    public static final String TF_OFFSET = "TF Offset";
    public static final String DATA_RATE_BPS = "Data Rate (bps)";
    public static final String FRAME_SIZE_BITS = "Frame size (bits)";

    public SummaryTable(URI uri) {
        super(uri);
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList(
                RUN_TIME,
                STATION_ID,
                TARGET_FRAME_ERT,
                TARGET_FRAME_UTC,
                TARGET_FRAME_VCID,
                TARGET_FRAME_VCFC,
                TARGET_FRAME_ENC_SCLK,
                SCLK_PARTITION_NUM,
                CLK_CHANGE_RATE_PREDICTED,
                CLK_CHANGE_RATE_INTERP,
                OWLT_SEC,
                TDT_G,
                TDT_G_STR,
                SPACECRAFT_TIME_DELAY,
                BITRATE_TIME_ERROR,
                TF_OFFSET,
                DATA_RATE_BPS,
                FRAME_SIZE_BITS
        );
    }
}
