package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcCli;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.products.definition.util.ProductWriteResult;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The UplinkCmdFile Class creates the Uplink Command File which is a file optionally produced during each run
 * that contains a single line of CSV test. This text contains the time correlation record that can be used by
 * a ground system to deliver in a command to the spacecraft to reset its onboard time correlation parameters.
 */
public class UplinkCmdFile {
    private static Logger logger = LogManager.getLogger(UplinkCmdFile.class);

    public static final String FILE_SUFFIX = ".csv";

    private String         filespec;
    private BufferedWriter writer;

    public UplinkCmdFile(String filespec) {
        this.filespec = filespec;
    }

    /**
     * Writes the CSV format uplink command record to the Uplink Command File.
     *
     * @param commandString the uplink command parameters
     * @throws IOException if the file cannot be written
     * @return the Path where the new file was written
     */
    public Path write(UplinkCommand commandString) throws IOException  {
        writer = new BufferedWriter(new FileWriter(filespec));
        writer.write(commandString.toString());
        writer.close();
        logger.info(MmtcCli.USER_NOTICE, "Wrote new uplink command file at: " + Paths.get(filespec));
        return Paths.get(filespec);
    }

    /**
     * Writes a new Uplink Command File
     * @param ctx the current time correlation context from which to pull information for the output product
     *
     * @throws MmtcException if the Uplink Command File cannot be written
     * @return a ProductWriteResult describing the newly-written product
     */
    public static ProductWriteResult writeNewProduct(TimeCorrelationContext ctx) throws MmtcException {
        String cmdFilespec = "";
        try {
            final UplinkCommand uplinkCmd = new UplinkCommand(
                    ctx.correlation.target.get().getTargetSample().getTkSclkCoarse(),
                    ctx.correlation.target.get().getTargetSampleEtG(),
                    ctx.correlation.target.get().getTargetSampleTdtG(),
                    TimeConvert.tdtToTdtStr(ctx.correlation.target.get().getTargetSampleTdtG()),
                    ctx.correlation.predicted_clock_change_rate.get()
            );

            final String cmdFilename = ctx.config.getUplinkCmdFileBasename() +
                    ctx.appRunTime.toEpochSecond() +
                    UplinkCmdFile.FILE_SUFFIX;

            final UplinkCmdFile cmdFile = new UplinkCmdFile(Paths.get(
                    ctx.config.getUplinkCmdFileDir(), cmdFilename
            ).toString());

            return new ProductWriteResult(
                    cmdFile.write(uplinkCmd),
                    Long.toString(ctx.appRunTime.toEpochSecond())
            );
        } catch (IOException | TimeConvertException ex) {
            throw new MmtcException("Unable to write the Uplink Command File: " + cmdFilespec, ex);
        }
    }
}
