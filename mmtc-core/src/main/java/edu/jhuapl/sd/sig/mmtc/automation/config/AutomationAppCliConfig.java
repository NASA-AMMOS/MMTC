package edu.jhuapl.sd.sig.mmtc.automation.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcSuccessfulExitException;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

public class AutomationAppCliConfig {
    private static final Logger logger = LogManager.getLogger();

    private final static String BASE_CLI_USAGE = "mmtc auto";

    protected HelpFormatter help = new HelpFormatter();
    protected Options opts = new Options();
    protected CommandLine cmdLine;
    private String[] args;

    public AutomationAppCliConfig(String[] args) {
        opts.addOption("h", "help", false, "Print this message.");
        this.args = args;
    }

    private boolean isOptionDefined(Option option) {
        return opts.hasShortOption(option.getOpt()) || opts.hasLongOption(option.getLongOpt());
    }

    public String[] getArgs() {
        return Arrays.copyOf(this.args, this.args.length);
    }

    public boolean load() {
        try {
            logger.info("Command line arguments: ["
                    + Arrays.stream(this.args).map(a -> "\"" + a + "\"").collect(Collectors.joining(","))
                    + "]"
            );

            CommandLineParser parser = new DefaultParser();
            cmdLine = parser.parse(opts, args);

            // Print help and exit, regardless of any other arguments
            if (isHelpSet()) {
                help.printHelp(BASE_CLI_USAGE, opts);
                throw new MmtcSuccessfulExitException();
            }
        } catch (ParseException ex) {
            String msg = "Error parsing command line arguments - Improperly formed command line.";
            System.out.println(msg);
            help.printHelp(BASE_CLI_USAGE, opts);
            logger.error(msg, ex);
            return false;
        }

        String[] posArgs = cmdLine.getArgs();

        // check validity of combination of CLI args
        if (posArgs.length != 0) {
            System.out.println("Incorrect number of command line arguments.  None may be specified.");
            help.printHelp("mmtc auto", opts);
            return false;
        }
        return true;
    }

    public String getOptionValue(String shortOpt) {
        return cmdLine.getOptionValue(shortOpt);
    }

    public boolean hasOption(char shortOpt) {
        return cmdLine.hasOption(shortOpt);
    }

    private boolean isHelpSet() {
        return cmdLine.hasOption("h") || cmdLine.hasOption("help");
    }
}
