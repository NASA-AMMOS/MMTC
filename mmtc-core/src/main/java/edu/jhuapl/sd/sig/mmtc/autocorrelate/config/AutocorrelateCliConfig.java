package edu.jhuapl.sd.sig.mmtc.autocorrelate.config;

import edu.jhuapl.sd.sig.mmtc.correlation.config.CorrelationCliConfig;
import org.apache.commons.cli.Option;

import java.time.OffsetDateTime;
import java.util.Collection;

import static edu.jhuapl.sd.sig.mmtc.util.TimeConvert.now;

public class AutocorrelateCliConfig extends CorrelationCliConfig {
    private OffsetDateTime untilTime;

    AutocorrelateCliConfig(String[] args) {
        super(Mode.AUTOCORRELATE, args);
    }

    public AutocorrelateCliConfig(String[] args, Collection<Option> additionalOptions) {
        super(Mode.AUTOCORRELATE, args, additionalOptions);
    }

    public final OffsetDateTime getUntilTime() {
        return untilTime;
    }

    @Override
    public boolean load() {
        super.load();

        String[] posArgs = cmdLine.getArgs();

        if (posArgs.length == 0) {
            untilTime = now();
        } else if (posArgs.length == 1) {
            untilTime = formDateTime(posArgs[0]);
        } else {
            System.out.println("Incorrect number of command line arguments. 1 is required, " + posArgs.length + " were provided.");
            help.printHelp("mmtc autocorrelate [options] <until-time>", opts);
            return false;
        }

        return true;
    }
}
