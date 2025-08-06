package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.MmtcCli;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
     */
    public void write(UplinkCommand commandString) throws IOException  {
        writer = new BufferedWriter(new FileWriter(filespec));
        writer.write(commandString.toString());
        writer.close();
        logger.info(MmtcCli.USER_NOTICE, "Wrote new uplink command file at: " + Paths.get(filespec));
    }
}
