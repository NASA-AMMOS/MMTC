package edu.jhuapl.sd.sig.mmtc.app;

import edu.jhuapl.sd.sig.mmtc.products.util.BuiltInOutputProductMigrationManager;
import edu.jhuapl.sd.sig.mmtc.rollback.TimeCorrelationRollback;
import edu.jhuapl.sd.sig.mmtc.sandbox.MmtcSandboxCreator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MmtcCli {
    public static final Marker USER_NOTICE = MarkerManager.getMarker("USER_NOTICE");

    public static final String MMTC_TITLE = "Multi-Mission Time Correlation (MMTC)";
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    private static final Logger logger = LogManager.getLogger();

    public enum ApplicationCommand {
        CORRELATION,
        ROLLBACK,
        CREATE_SANDBOX,
        MIGRATE
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
            System.out.println(MmtcCli.BUILD_INFO);
            System.exit(0);
        }

        if (Arrays.asList("-h", "--help").contains(cliArgs[0])) {
            final String helpMessage =
                    "usage: mmtc [correlation|rollback|create-sandbox|migrate] [options] <additional arguments>\n" +
                    " -h,--help      Print this message.\n" +
                    " -v,--version   Print the MMTC version number.\n" +
                    "\n" +
                    "MMTC can be invoked with one of several commands:\n" +
                    "- correlation: run a new correlation (this is the default command if none\n" +
                    "is specified)\n" +
                    "- rollback: roll back (undo) one or many correlations\n" +
                    "- create-sandbox: create a copy of this MMTC installation to run locally,\n" +
                    "without affecting this installation\n" +
                    "- migrate: migrate MMTC's built-in output products forward to match the structure\n" +
                    "of the current version" +
                    "\n" +
                    "For more information on any of these commands, run: mmtc <command> --help";
            System.out.println(helpMessage);
            System.exit(0);
        }

        if (cliArgs[0].equalsIgnoreCase("rollback")) {
            return new ApplicationInvocation(ApplicationCommand.ROLLBACK, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("create-sandbox")) {
            return new ApplicationInvocation(ApplicationCommand.CREATE_SANDBOX, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("correlation")) {
            return new ApplicationInvocation(ApplicationCommand.CORRELATION, removeFirstElement(cliArgs));
        } else if (cliArgs[0].equalsIgnoreCase("migrate")) {
            return new ApplicationInvocation(ApplicationCommand.MIGRATE, removeFirstElement(cliArgs));
        } else {
            // to maintain backwards compatibility on MMTC's CLI
            return new ApplicationInvocation(ApplicationCommand.CORRELATION, cliArgs);
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
    public static void main(String[] args) {
        logger.info(String.format("************ %s version %s ************", MMTC_TITLE, BUILD_INFO.version));
        logger.info(String.format("Commit %s built at %s", BUILD_INFO.commit, BUILD_INFO.buildDate));

        final ApplicationInvocation appInvoc = determineApplicationCommand(args);

        switch (appInvoc.command) {
            case CORRELATION: {
                try {
                    new TimeCorrelationApp(appInvoc.args).run();
                } catch (Exception ex) {
                    logger.fatal("MMTC correlation run failed.", ex);
                    System.exit(1);
                }
                break;
            }
            case ROLLBACK: {
                try {
                    new TimeCorrelationRollback(appInvoc.args).rollback();
                } catch (Exception e) {
                    logger.fatal("Rollback failed.", e);
                    System.exit(1);
                }
                break;
            }
            case CREATE_SANDBOX: {
                try {
                    new MmtcSandboxCreator(appInvoc.args).create();
                } catch (Exception e) {
                    logger.fatal("Sandbox creation failed.", e);
                    System.exit(1);
                }
                break;
            }
            case MIGRATE: {
                try {
                    new BuiltInOutputProductMigrationManager(appInvoc.args).migrate();
                } catch (Exception e) {
                    logger.fatal("Output product migration failed.", e);
                    System.exit(1);
                }
                break;
            }
            default: {
                logger.fatal("Unrecognized command: " + appInvoc.command);
                System.exit(1);
            }
        }
    }
}
