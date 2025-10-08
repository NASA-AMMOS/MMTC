package edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlm.CachingTelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlm.FrameSample;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

/**
 * A class which implements a few user-invoked functions on the cache.
 */
public class TelemetryCacheUserOperations {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Proactively retrieves telemetry over any uncovered ranges within the input range.  Does not perform a correlation.
     *
     * @param args two positional argments (start time, stop time) and any optional telemetry source options
     * @throws Exception if the operation was not completed successfully
     */
    public static void precache(String... args) throws Exception {
        // this is set up inline and incongruously to other entry points due to its direct reliance on TimeCorrelationAppConfig,
        // and need to override the help option.
        // todo this should eventually be changed to be separate, but that requires separating CLI args out of configuration classes and
        // a change to the TelemetrySource interface (in applyConfiguration)

        final Options opts = new Options();
        opts.addOption("h", "help", false, "Print this message.");
        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmdLine = parser.parse(opts, args);

        if (cmdLine.hasOption("h") || cmdLine.hasOption("help")) {
            final HelpFormatter help = new HelpFormatter();
            final String helpFooter = "\nPrepares ('warms') the MMTC telemetry cache, covering the specified time range.";
            help.printHelp("mmtc precache [telemetry source options] <start-time> <stop-time>", "", opts, helpFooter);
            System.exit(0);
        }

        final TimeCorrelationAppConfig config = new TimeCorrelationCliAppConfig(args);

        logger.info(String.format("Querying and caching telemetry from %s to %s...", config.getStartTime(), config.getStopTime()));
        try {
            config.getTelemetrySource().connect();
            List<FrameSample> samplesInRange = config.getTelemetrySource().getSamplesInRange(config.getStartTime(), config.getStopTime());
            logger.info(String.format("Caching complete.  %d samples in the given time range are cached.", samplesInRange.size()));
        } finally {
            config.getTelemetrySource().disconnect();
        }
    }

    /**
     * Logs statistics about the cache to the log appenders (files, stdout).
     *
     * @param args no args are expected or required
     * @throws Exception if the operation was not completed successfully
     */
    public static void logCacheStatistics(String... args) throws Exception {
        final TelemetryCacheOperationsConfig config = new TelemetryCacheOperationsConfig(args);

        // we can safely cast the tlmSource to CachingTelemetrySource because TelemetryPrecacherConfig ensures that caching is enabled before allowing operations on the telemetry cache
        final TelemetrySource tlmSource = config.getTelemetrySource();
        if (! (tlmSource instanceof CachingTelemetrySource)) {
            throw new MmtcException("Configured telemetry source must be CachingTelemetrySource for this operation");
        }

        final CachingTelemetrySource cachingTlmSource = (CachingTelemetrySource) tlmSource;
        logger.info(USER_NOTICE, "Cache statistics:");
        final Map<String, String> cacheStats = cachingTlmSource.getCacheStatistics();
        for (Map.Entry<String, String> entry : cacheStats.entrySet()) {
            logger.info(USER_NOTICE, String.format("\t%s: %s", entry.getKey(), entry.getValue()));
        }
    }
}
