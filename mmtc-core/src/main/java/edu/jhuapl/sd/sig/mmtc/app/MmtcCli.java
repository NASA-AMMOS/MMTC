package edu.jhuapl.sd.sig.mmtc.app;

import edu.jhuapl.sd.sig.mmtc.autocorrelate.AutocorrelateApp;
import edu.jhuapl.sd.sig.mmtc.cfg.app.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.products.migration.BuiltInOutputProductMigrationManager;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.sandbox.MmtcSandboxCreator;
import edu.jhuapl.sd.sig.mmtc.tlm.persistence.cache.TelemetryCacheUserOperations;
import edu.jhuapl.sd.sig.mmtc.trending.TrendingApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MmtcCli {
    public static final Marker USER_NOTICE = MarkerManager.getMarker("USER_NOTICE");
    public static final Marker LOGFILE_ONLY = MarkerManager.getMarker("LOGFILE_ONLY");

    private static final Logger logger = LogManager.getLogger();

    public enum ApplicationCommand {
        CORRELATE,
        ROLLBACK,
        CREATE_SANDBOX,
        MIGRATE,
        PRECACHE,
        CACHE_STATS,
        TREND,
        AUTOCORRELATE
    }

    private static class ApplicationInvocation {
        private final ApplicationCommand command;
        private final String[] args;

        public ApplicationInvocation(ApplicationCommand command, String... args) {
            this.command = command;
            this.args = args;
        }
    }

    private static ApplicationInvocation determineApplicationCommand(String... cliArgs) {
        if (Arrays.asList("-v", "--version").contains(cliArgs[0])) {
            System.out.println(new BuildInfo());
            System.exit(0);
        }

        if (Arrays.asList("-h", "--help").contains(cliArgs[0])) {
            final String helpMessage =
                    "usage: mmtc [correlation|rollback|create-sandbox|migrate|precache|cache-stats] [options] <additional arguments>\n" +
                    " -h,--help      Print this message.\n" +
                    " -v,--version   Print the MMTC version.\n" +
                    "\n" +
                    "MMTC can be invoked with one of the following commands:\n" +
                    "- [correlate|correlation]: run a new correlation (this is the default command if none\n" +
                    "is specified)\n" +
                    "- rollback: roll back (undo) one or many correlations\n" +
                    "- create-sandbox: create a copy of this MMTC installation to run locally,\n" +
                    "without affecting this installation\n" +
                    "- migrate: migrate MMTC's output products from a prior version of MMTC\n" +
                    "- precache: query the configured telemetry source to proactively retrieve\n" +
                    "and store time correlation telemetry into a local cache\n" +
                    "- cache-stats: log statistics about the locally-cached telemetry\n" +
                    "- trend: calculate and report timekeeping trending information\n" +
                    "\n" +
                    "For more information on any of these commands, run: mmtc <command> --help";
            System.out.println(helpMessage);
            System.exit(0);
        }

        if (cliArgs[0].equalsIgnoreCase("rollback")) {
            return new ApplicationInvocation(ApplicationCommand.ROLLBACK, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("create-sandbox")) {
            return new ApplicationInvocation(ApplicationCommand.CREATE_SANDBOX, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("correlate")) {
            return new ApplicationInvocation(ApplicationCommand.CORRELATE, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("correlation")) {
            return new ApplicationInvocation(ApplicationCommand.CORRELATE, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("migrate")) {
            return new ApplicationInvocation(ApplicationCommand.MIGRATE, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("precache")) {
            return new ApplicationInvocation(ApplicationCommand.PRECACHE, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("cache-stats")) {
            return new ApplicationInvocation(ApplicationCommand.CACHE_STATS, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("trend")) {
            return new ApplicationInvocation(ApplicationCommand.TREND, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("autocorrelate")) {
            return new ApplicationInvocation(ApplicationCommand.AUTOCORRELATE, removeFirstElement(cliArgs));
        } else {
            // to maintain backwards compatibility on MMTC's CLI
            return new ApplicationInvocation(ApplicationCommand.CORRELATE, cliArgs);
        }
    }

    private static String[] removeFirstElement(String... arr) {
        List<String> arrList = new ArrayList<>(Arrays.asList(arr));
        arrList.remove(0);
        return arrList.toArray(new String[arr.length - 1]);
    }

    /**
     * Entry point of the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) throws Exception {
        BuildInfo.log(logger);

        final ApplicationInvocation appInvoc = determineApplicationCommand(args);

        final MmtcConfig cfg;

        try {
            cfg = new MmtcConfig();
            cfg.validate();
        } catch (Exception e) {
            logger.fatal("MMTC correlation initialization failed.", e);
            throw new MmtcException("MMTC correlation initialization failed.", e);
        }

        cfg.acquireLockFile();

        int exitCode = 0;

        switch (appInvoc.command) {
            case CORRELATE: {
                try {
                    new TimeCorrelationApp(appInvoc.args).run();
                } catch (NoTelemetryFoundException e) {
                    logger.info(LOGFILE_ONLY, "No telemetry found within the specified time range", e);
                    logger.info("No telemetry found within the specified time range.");
                    exitCode = 2;
                } catch (TelemetryQualityException e) {
                    logger.warn(LOGFILE_ONLY, "All telemetry found within the query window did not pass filters or validation", e);
                    logger.warn("All telemetry found within the query window did not pass filters or validation.");
                    exitCode = 3;
                } catch (Exception e) {
                    // any other exception
                    logger.fatal("MMTC correlation run failed.", e);
                    exitCode = 1;
                }
                break;
            }
            case ROLLBACK: {
                try {
                    new TimeCorrelationRollback(appInvoc.args).rollback(Optional.empty());
                } catch (Exception e) {
                    logger.fatal("Rollback failed.", e);
                    exitCode = 1;
                }
                break;
            }
            case CREATE_SANDBOX: {
                try {
                    new MmtcSandboxCreator(appInvoc.args).create();
                } catch (Exception e) {
                    logger.fatal("Sandbox creation failed.", e);
                    exitCode = 1;
                }
                break;
            }
            case MIGRATE: {
                try {
                    new BuiltInOutputProductMigrationManager(appInvoc.args).migrate();
                } catch (Exception e) {
                    logger.fatal("Output product migration failed.", e);
                    exitCode = 1;
                }
                break;
            }
            case PRECACHE: {
                try {
                    TelemetryCacheUserOperations.precache(appInvoc.args);
                } catch (Exception e) {
                    logger.fatal("Precaching failed.", e);
                    exitCode = 1;
                }
                break;
            }
            case CACHE_STATS: {
                try {
                    TelemetryCacheUserOperations.logCacheStatistics(appInvoc.args);
                } catch (Exception e) {
                    logger.fatal("Failed to calculate cache statistics.", e);
                    exitCode = 1;
                }
                break;
            }
            case TREND: {
                try {
                    new TrendingApp(appInvoc.args).run();
                } catch (Exception e) {
                    logger.fatal("Failed to generate new trending products.", e);
                    exitCode = 1;
                }
                break;
            }
            case AUTOCORRELATE: {
                try {
                    new AutocorrelateApp(appInvoc.args).run();
                } catch (Exception e) {
                    // any other exception
                    logger.fatal("MMTC autocorrelate run failed.", e);
                    exitCode = 1;
                }
                break;
            }
            default: {
                logger.fatal("Unrecognized command: " + appInvoc.command);
                exitCode = 1;
            }
        }

        cfg.releaseLockFile();

        System.exit(exitCode);
    }
}
