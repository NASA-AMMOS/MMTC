package edu.jhuapl.sd.sig.mmtc.autocorrelate.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.MmtcSuccessfulExitException;
import edu.jhuapl.sd.sig.mmtc.correlation.config.CorrelationCliConfig;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.now;

public class AutocorrelateCliConfig  {
    private static final Logger logger = LogManager.getLogger();

    private final static String BASE_CLI_USAGE = "mmtc autocorrelate [options] [up to time]";

    protected HelpFormatter help = new HelpFormatter();
    protected Options opts = new Options();
    protected CommandLine cmdLine;
    private String[] args;

    private OffsetDateTime runUntilTime;

    public AutocorrelateCliConfig(String[] args) throws MmtcException {
        opts.addOption("h", "help", false, "Print this message.");
        this.args = args;

        if (! load()) {
            throw new MmtcException("Error parsing command line arguments.");
        }
    }

    private boolean isOptionDefined(Option option) {
        return opts.hasShortOption(option.getOpt()) || opts.hasLongOption(option.getLongOpt());
    }

    public String[] getArgs() {
        return Arrays.copyOf(this.args, this.args.length);
    }

    public final OffsetDateTime getRunUntilTime() {
        return runUntilTime;
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
        } catch (DateTimeParseException ex) {
            String msg = "Error parsing command line arguments - Invalid start/stop time value(s). Valid formats are yyyy-DDDTHH:mm:ss.SSS or yyyy-mm-ddTHH:mm:ss.SSS.";
            System.out.println(msg);
            logger.error(msg, ex);
            return false;
        }
        catch (ParseException ex) {
            String msg = "Error parsing command line arguments - Improperly formed command line.";
            System.out.println(msg);
            help.printHelp(BASE_CLI_USAGE, opts);
            logger.error(msg, ex);
            return false;
        }

        String[] posArgs = cmdLine.getArgs();

        // check validity of combination of CLI args
        if (posArgs.length > 1) {
            System.out.println("Incorrect number of command line arguments. 0 or 1 may be specified, but " + posArgs.length + " were provided.");
            help.printHelp("mmtc autocorrelate [options] <run-until-time>", opts);
            return false;
        }

        if (posArgs.length == 1) {
            runUntilTime = CorrelationCliConfig.formDateTime(posArgs[0]);
        } else {
            runUntilTime = now();
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
