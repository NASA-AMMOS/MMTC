package edu.jhuapl.sd.sig.mmtc.app;

import edu.jhuapl.sd.sig.mmtc.automation.AutomationApp;
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

import java.util.*;

public class MmtcCli {
    public static final Marker USER_NOTICE = MarkerManager.getMarker("USER_NOTICE");
    public static final Marker LOGFILE_ONLY = MarkerManager.getMarker("LOGFILE_ONLY");

    private static final Logger logger = LogManager.getLogger();

    public enum ApplicationCommand {
        CORRELATE,
        CORRELATION,
        AUTOCORRELATE,
        TREND,
        AUTO,
        ROLLBACK,
        CREATE_SANDBOX,
        MIGRATE,
        PRECACHE,
        CACHE_STATS
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
            exitWithCode(0);
        }

        if (Arrays.asList("-h", "--help").contains(cliArgs[0])) {
            final String helpMessage =
                    "usage: mmtc <command> [options...] [arguments...]\n" +
                    " -h,--help      Print this message.\n" +
                    " -v,--version   Print the MMTC version.\n" +
                    "\n" +
                    "MMTC can be invoked with one of the following commands:\n" +
                    "- correlate | correlation: run a new correlation (this is the default command if none\n" +
                    "is specified)\n" +
                    "- autocorrelate: automatically run new correlation(s) according to configuration\n" +
                    "- trend: calculate timekeeping trending information and write to output products according to configuration\n" +
                    "- auto: run MMTC as a daemon, automatically correlating and/or trending according to configuration\n" +
                    "- rollback: roll back (undo) one or many correlations\n" +
                    "- create-sandbox: create a copy of this MMTC installation to run locally,\n" +
                    "without affecting this installation\n" +
                    "- migrate: migrate MMTC's output products from a prior version of MMTC\n" +
                    "- precache: query the configured telemetry source to proactively retrieve\n" +
                    "and store time correlation telemetry into a local cache\n" +
                    "- cache-stats: log statistics about the locally-cached telemetry\n" +
                    "\n" +
                    "For more information on any of these commands, run: mmtc <command> --help";
            System.out.println(helpMessage);
            exitWithCode(0);
        }

        try {
            ApplicationCommand appCmd = ApplicationCommand.valueOf(cliArgs[0].toUpperCase().trim().replace("-", "_"));
            return new ApplicationInvocation(appCmd, removeFirstElement(cliArgs));
        } catch (IllegalArgumentException e) {
            // to maintain backwards compatibility on MMTC's CLI
            return new ApplicationInvocation(ApplicationCommand.CORRELATE, cliArgs);
        }
    }

    private static String[] removeFirstElement(String... arr) {
        List<String> arrList = new ArrayList<>(Arrays.asList(arr));
        arrList.remove(0);
        return arrList.toArray(new String[arr.length - 1]);
    }

    @FunctionalInterface
    private interface RunnableThatThrows {
        void run() throws Exception;
    }

    private static class RunnableCommand {
        public final RunnableThatThrows runnable;
        public final String generalErrorMessage;

        private RunnableCommand(RunnableThatThrows runnable, String generalErrorMessage) {
            this.runnable = runnable;
            this.generalErrorMessage = generalErrorMessage;
        }
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

        final RunnableCommand cmdToCall;

        switch (appInvoc.command) {
            case CORRELATE:
            case CORRELATION:
                cmdToCall = new RunnableCommand(
                        () -> new TimeCorrelationApp(appInvoc.args).run(),
                        "MMTC correlation run failed."
                );
                break;
            case AUTOCORRELATE: {
                cmdToCall = new RunnableCommand(
                        () -> new AutocorrelateApp(appInvoc.args).run(),
                        "MMTC autocorrelate run failed."
                );
                break;
            }
            case TREND: {
                cmdToCall = new RunnableCommand(
                        () -> new TrendingApp(appInvoc.args).run(),
                        "Failed to generate new trending products."
                );
                break;
            }
            case AUTO: {
                cmdToCall = new RunnableCommand(
                        () -> new AutomationApp(appInvoc.args).run(),
                "MMTC auto mode exiting due to a failure."
                );
                break;
            }
            case ROLLBACK: {
                cmdToCall = new RunnableCommand(
                        () -> new TimeCorrelationRollback(appInvoc.args).rollback(Optional.empty()),
                "Rollback failed."
                );
                break;
            }
            case CREATE_SANDBOX: {
                cmdToCall = new RunnableCommand(
                        () -> new MmtcSandboxCreator(appInvoc.args).create(),
                        "Sandbox creation failed."
                );
                break;
            }
            case MIGRATE: {
                cmdToCall = new RunnableCommand(
                        () -> new BuiltInOutputProductMigrationManager(appInvoc.args).migrate(),
                        "Output product migration failed."
                );
                break;
            }
            case PRECACHE: {
                cmdToCall = new RunnableCommand(
                        () -> TelemetryCacheUserOperations.precache(appInvoc.args),
                        "Precaching failed."
                );
                break;
            }
            case CACHE_STATS: {
                cmdToCall = new RunnableCommand(
                        () -> TelemetryCacheUserOperations.logCacheStatistics(appInvoc.args),
                        "Failed to calculate cache statistics."
                );
                break;
            }
            default: {
                logger.fatal("Unrecognized command: " + appInvoc.command);
                exitWithCode(1);

                // this return shouldn't be necessary, but prevents a warning on the runCommand call that cmdToCall might not have been initialized
                return;
            }
        }

        exitWithCode(runCommand(cfg, cmdToCall));
    }

    public static int runCommand(MmtcConfig cfg, RunnableCommand command) {
        try {
            cfg.acquireLockFile();
        } catch (MmtcException e) {
            logger.fatal("Failed to acquire lock file");
            exitWithCode(1);
        }

        int exitCode;

        try {
            command.runnable.run();
            exitCode = 0;
        } catch (NoTelemetryFoundException e) {
            logger.info(LOGFILE_ONLY, "No telemetry found within the specified time range", e);
            logger.info(USER_NOTICE, "No telemetry found within the specified time range.");
            exitCode = 2;
        } catch (TelemetryQualityException e) {
            logger.warn(LOGFILE_ONLY, "All telemetry found within the query window did not pass filters or validation", e);
            logger.warn("All telemetry found within the query window did not pass filters or validation.");
            exitCode = 3;
        } catch (MmtcSuccessfulExitException e) {
            // this is when we're returning 'early' from a command, but it was successful, e.g. printing a version number or CLI help use
            exitCode = 0;
        } catch (Exception e) {
            logger.fatal(command.generalErrorMessage, e);
            exitCode = 1;
        }

        try {
            cfg.releaseLockFile();
        } catch (MmtcException e) {
            logger.fatal("Failed to release lock file");
            exitCode = 1;
        }

        return exitCode;
    }

    private static void exitWithCode(int exitCode) {
        // because we've disabled the log4j2 shutdown hook in the bin/mmtc startup script
        LogManager.shutdown();
        System.exit(exitCode);
    }
}
