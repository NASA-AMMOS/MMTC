package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig.ClockChangeRateMode;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;


/**
 * Defines the command line configuration options for the MMTC application.
 */
public class CorrelationCommandLineConfig implements IConfiguration {
    private HelpFormatter help = new HelpFormatter();
    private Options opts = new Options();
    private CommandLine cmdLine;
    private String[] args;
    private OffsetDateTime startTime;
    private OffsetDateTime stopTime;
    private ClockChangeRateMode clockChangeRateMode;
    private double clockChangeRateAssignedValue;
    private String clockChangeRateAssignedKey;
    private boolean isClockChangeRateModeExplicitlySet;

    private static final String ClockChangeRateOptionCompute = "clkchgrate-compute";
    private static final String ClockChangeRateOptionNoDrift = "clkchgrate-nodrift";
    private static final String ClockChangeRateOptionAssign = "clkchgrate-assign";

    private static final Logger logger = LogManager.getLogger();

    CorrelationCommandLineConfig(String[] args) {
        this(args, Collections.emptyList());
    }

    CorrelationCommandLineConfig(String[] args, Collection<Option> additionalOptions) {
        OptionGroup clockChangeRateGroup = new OptionGroup();

        clockChangeRateGroup.addOption(Option.builder()
                .longOpt(ClockChangeRateOptionCompute)
                .hasArg(true)
                .argName("method")
                .desc("Compute the clock change rate from telemetry data." +
                        "<method> may be 'i' or 'p', where 'i' indicates interpolate " +
                        "the penultimate change rate, and 'p' indicates leave as-is.")
                .build()
        );

        clockChangeRateGroup.addOption(Option.builder()
                .longOpt(ClockChangeRateOptionAssign)
                .hasArg(true)
                .argName("value")
                .desc("Assign the clock change rate to <value>.")
                .build()
        );

        clockChangeRateGroup.addOption(Option.builder()
                .longOpt(ClockChangeRateOptionNoDrift)
                .hasArg(false)
                .desc("Assign the clock change rate to 1.000000000000000.")
                .build()
        );

        opts.addOptionGroup(clockChangeRateGroup);

        opts.addOption(
                "c",
                "generate-cmd-file",
                false,
                "Generate an uplink command file containing parameters to be sent to the spacecraft."
        );

        opts.addOption(
                "F",
                "disable-contact-filter",
                false,
                "Disable the contact filter. This overrides the presence of the contact filter settings in the configuration file."
        );

        opts.addOption(
                "T",
                "test-mode-owlt",
                true,
                "Run in test mode, which allows the user to override one-way-light-time."
        );

        opts.addOption(
                "D",
                "dry-run",
                false,
                "Enables dry-run mode, resulting in correlation outputs being printed & logged but not written to the filesystem. The run history file likewise won't be modified."
        );

        opts.addOption("h", "help", false, "Print this message.");

        for (Option additionalOption : additionalOptions) {
            if (isOptionDefined(additionalOption)) {
                throw new IllegalStateException(String.format("Telemetry source plugin attempted to define option that is already defined in MMTC: %s", additionalOption));
            }

            opts.addOption(additionalOption);
        }

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
            String[] posArgs = cmdLine.getArgs();

            // Print help and exit, regardless of any other arguments
            if (isHelpSet()) {
                help.printHelp("mmtc correlation [options] <start-time> <stop-time>", opts);
                System.exit(0);
            }

            setClockChangeRateMode();

            if (posArgs.length == 2) {
                // Convert the input data start/stop times to Java OffsetDateTime.
                startTime = formDateTime(posArgs[0]);
                stopTime  = formDateTime(posArgs[1]);
            } else {
                System.out.println("Incorrect number of command line arguments. 2 are required, " + posArgs.length + " were provided.");
                help.printHelp("mmtc correlation [options] <start-time> <stop-time>", opts);
                return false;
            }
        }
        catch (DateTimeParseException ex) {
            String msg = "Error parsing command line arguments - Invalid start/stop time value(s). Valid formats are yyyy-DDDTHH:mm:ss.SSS or yyyy-mm-ddTHH:mm:ss.SSS.";
            System.out.println(msg);
            logger.error(msg, ex);
            return false;
        }
        catch (ParseException ex) {
            String msg = "Error parsing command line arguments - Improperly formed command line.";
            System.out.println(msg);
            help.printHelp("mmtc [options] <start-time> <stop-time>", opts);
            logger.error(msg, ex);
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "";
    }

    final OffsetDateTime getStartTime() {
        return startTime;
    }

    final OffsetDateTime getStopTime() {
        return stopTime;
    }

    boolean isTestMode() {
        return cmdLine.hasOption("T") || cmdLine.hasOption("test-mode-owlt");
    }

    boolean isDryRun() {
        return cmdLine.hasOption("D") || cmdLine.hasOption("dry-run");
    }

    double getTestModeOwlt() {
        return Double.parseDouble(cmdLine.getOptionValue('T'));
    }

    boolean isGenerateCmdFile() {
        return cmdLine.hasOption("c") || cmdLine.hasOption("generate-cmd-file");
    }

    boolean isContactFilterDisabled() {
        return cmdLine.hasOption("F") || cmdLine.hasOption("disable-contact-filter");
    }

    boolean hasClockChangeRateMode() {
        return isClockChangeRateModeExplicitlySet;
    }

    ClockChangeRateMode getClockChangeRateMode() {
        return clockChangeRateMode;
    }

    double getClockChangeRateAssignedValue() {
        return clockChangeRateAssignedValue;
    }

    private void setClockChangeRateMode() throws ParseException {
        isClockChangeRateModeExplicitlySet = true;
        if (cmdLine.hasOption(ClockChangeRateOptionCompute)) {
            final String method = cmdLine.getOptionValue(ClockChangeRateOptionCompute);

            switch (method) {
                case "i":
                    clockChangeRateMode = ClockChangeRateMode.COMPUTE_INTERPOLATED;
                    break;
                case "p":
                    clockChangeRateMode = ClockChangeRateMode.COMPUTE_PREDICTED;
                    break;
                default:
                    throw new ParseException("Invalid clock change rate compute method: " + method);
            }
        }
        else if (cmdLine.hasOption(ClockChangeRateOptionNoDrift)) {
            clockChangeRateMode = ClockChangeRateMode.NO_DRIFT;
        }
        else if (cmdLine.hasOption(ClockChangeRateOptionAssign)) {
            String assignValue = cmdLine.getOptionValue(ClockChangeRateOptionAssign);
            clockChangeRateMode = ClockChangeRateMode.ASSIGN;

            try {
                clockChangeRateAssignedValue = Double.parseDouble(assignValue);
            } catch (NumberFormatException ex) {
                logger.debug(String.format("Assigned clock change rate %s appears to reference a named preset.", assignValue));
                clockChangeRateMode = ClockChangeRateMode.ASSIGN_KEY;
                clockChangeRateAssignedKey = assignValue;
            }
        }
        else {
            isClockChangeRateModeExplicitlySet = false;
        }
    }

    public String getOptionValue(char shortOpt) {
        return cmdLine.getOptionValue(shortOpt);
    }

    public boolean hasOption(char shortOpt) {
        return cmdLine.hasOption(shortOpt);
    }

    private boolean isHelpSet() {
        return cmdLine.hasOption("h") || cmdLine.hasOption("help");
    }

    /**
     * Converts an input date/time string into an OffsetDateTime object.
     * Input strings are expected to be in either ISO yyyy-DDDTHH:mm:ss.SSS+Z or yyyy-mm-ddTHH:mm:ss.SSS+Z format.
     * Since the above ISO Day of Year (DoY) format is not intrinsically supported by OffsetDateTime.parse(),
     * it is necessary to use a custom formatter and to prepare the input string to conform to it. This function
     * will accept other date/time string formats that the OffsetDateTime.parse() function can process, but these
     * are not formally supported.
     *
     * @param dateTimeStr IN:the date/time string
     * @return the date/time as an OffsetDateTime object
     */
    public OffsetDateTime formDateTime(String dateTimeStr) {

        String workingStr;
        OffsetDateTime date_time;

        // Time/Date formats may be either ISO yyyy-DDDTHH:mm:ss.SSSZ or yyyy-mm-ddTHH:mm:ss.SSSZ formats.
        // Determine if the input time string is in ISO DoY format.
        int num_dashesStr    = StringUtils.countMatches(dateTimeStr, '-');
        boolean strContainsT = (StringUtils.countMatches(dateTimeStr,'T') == 1);

        // An ISO date/stime string has a 'T' in it and the DoY form has only one '-' separating year from DoY.
        if ((num_dashesStr == 1) && strContainsT) {
            // Assume time/date format is in DOY format (preferred).

            // Trim off any trailing 'Z' character indicating Zulu time (i.e., UTC). UTC is always assumed.
            if (dateTimeStr.endsWith("Z") || dateTimeStr.endsWith("z")) {
                workingStr = dateTimeStr.substring(0, dateTimeStr.length()-1);
            } else {
                workingStr = dateTimeStr;
            }

            date_time = TimeConvert.parseIsoDoyUtcStr(workingStr);
        } else {
            // Assume time/date format is in a format supported by the Java OffsetDateTime month/day-of-month format.
            // Append a 'Z' for Zulu time to the string if not already provided.
            if (dateTimeStr.endsWith("Z") || dateTimeStr.endsWith("z")) {
                workingStr = dateTimeStr;
            } else {
                workingStr = dateTimeStr + 'Z';
            }

            date_time = OffsetDateTime.parse(workingStr);
        }

        // truncate to the nearest microsecond
        return date_time.truncatedTo(ChronoUnit.MICROS);
    }

    public String getClockChangeRateAssignedKey() {
        return clockChangeRateAssignedKey;
    }
}
