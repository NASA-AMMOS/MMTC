package edu.jhuapl.sd.sig.mmtc.correlation.config;

import org.apache.commons.cli.Option;

import java.time.OffsetDateTime;
import java.util.Collection;

public class CorrelationRangeCliConfig extends CorrelationCliConfig {
    private OffsetDateTime startTime;
    private OffsetDateTime stopTime;

    CorrelationRangeCliConfig(String[] args, Collection<Option> additionalOptions) {
        super(args, additionalOptions);
    }

    final OffsetDateTime getStartTime() {
        return startTime;
    }

    final OffsetDateTime getStopTime() {
        return stopTime;
    }

    @Override
    public boolean load() {
        super.load();

        String[] posArgs = cmdLine.getArgs();

        if (posArgs.length == 2) {
            // Convert the input data start/stop times to Java OffsetDateTime.
            startTime = formDateTime(posArgs[0]);
            stopTime  = formDateTime(posArgs[1]);
        } else {
            System.out.println("Incorrect number of command line arguments. 2 are required, " + posArgs.length + " were provided.");
            help.printHelp(BASE_CLI_USAGE, opts);
            return false;
        }

        return true;
    }
}
