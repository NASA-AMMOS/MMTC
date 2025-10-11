package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.apache.commons.cli.*;

public class MigrationConfig extends MmtcConfig {
    public MigrationConfig(String... args) throws Exception {
        super();

        final Options opts = new Options();
        opts.addOption("h", "help", false, "Print this message.");

        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmdLine = parser.parse(opts, args);

        if (cmdLine.hasOption("h") || cmdLine.hasOption("help")) {
            final HelpFormatter help = new HelpFormatter();
            final String helpFooter = "\nInvoke the MMTC migration feature on the console.  Takes no CLI arguments.";
            help.printHelp("mmtc migrate", "", opts, helpFooter);
            System.exit(0);
        }

        if (cmdLine.getArgList().size() != 0) {
            throw new MmtcException("Error parsing command line arguments.");
        }
    }
}
