package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.apache.commons.cli.*;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MmtcSandboxCreatorConfig extends MmtcConfig {
    private final TelemetrySource telemetrySource;
    private final Path newSandboxPath;

    public MmtcSandboxCreatorConfig(String... args) throws Exception {
        super();
        this.telemetrySource = this.initTlmSource();

        final Options opts = new Options();
        opts.addOption("h", "help", false, "Print this message.");

        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmdLine = parser.parse(opts, args);

        if (cmdLine.hasOption("h") || cmdLine.hasOption("help")) {
            final HelpFormatter help = new HelpFormatter();
            final String helpFooter = "\nCreates a new MMTC sandbox at the given path, based on the configuration of this MMTC installation.";
            help.printHelp("mmtc create-sandbox <new-sandbox-path>", "", opts, helpFooter);
            System.exit(0);
        }

        if (cmdLine.getArgList().size() != 1) {
            throw new MmtcException("Error parsing command line arguments.");
        }

        newSandboxPath = Paths.get(cmdLine.getArgList().get(0)).toAbsolutePath();
    }

    public Path getNewSandboxPath() {
        return newSandboxPath;
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }
}
