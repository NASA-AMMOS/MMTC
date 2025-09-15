package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.apache.commons.cli.*;

public class TelemetryCacheOperationsConfig extends MmtcConfig {
    private final TelemetrySource telemetrySource;

    public TelemetryCacheOperationsConfig(String... args) throws Exception {
        super();
        this.telemetrySource = this.initTlmSource();

        final Options opts = new Options();
        opts.addOption("h", "help", false, "Print this message.");
        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmdLine = parser.parse(opts, args);

        if (cmdLine.hasOption("h") || cmdLine.hasOption("help")) {
            final HelpFormatter help = new HelpFormatter();
            final String helpFooter = "\nLogs cache statistics from the MMTC telemetry cache.";
            help.printHelp("mmtc cache-stats", "", opts, helpFooter);
            System.exit(0);
        }
    }

    public TelemetrySource getTelemetrySource() {
        return telemetrySource;
    }
}
