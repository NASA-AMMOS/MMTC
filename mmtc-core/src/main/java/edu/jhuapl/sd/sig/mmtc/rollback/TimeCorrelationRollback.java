package edu.jhuapl.sd.sig.mmtc.rollback;

import edu.jhuapl.sd.sig.mmtc.app.MmtcRollbackException;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.table.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;

import static edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp.USER_NOTICE;

public class TimeCorrelationRollback {
    public static final int ROLLBACK_WINDOW_SIZE = 10;
    private static final Logger logger = LogManager.getLogger();
    private final RunHistoryFile runHistoryFile;
    private final OutputProduct timeHist;
    private final OutputProduct tlmTable;
    private final OutputProduct sclkKernels;

    private final Optional<OutputProduct> sclkscetFiles;
    private final Optional<OutputProduct> uplinkFiles;

    public TimeCorrelationRollback() throws Exception {
        TimeCorrelationAppConfig config = new TimeCorrelationAppConfig();
        
        this.runHistoryFile = new RunHistoryFile(config.getRunHistoryFileUri());

        this.timeHist = new OutputProduct(RunHistoryFile.POSTRUN_TIMEHIST, config.getTimeHistoryFileUri());
        timeHist.setTable(new TimeHistoryFile(timeHist.getUri()));

        this.tlmTable = new OutputProduct(RunHistoryFile.POSTRUN_RAWTLMTABLE, config.getRawTelemetryTableUri());
        tlmTable.setTable(new RawTelemetryTable(tlmTable.getUri()));

        this.sclkKernels = new OutputProduct(RunHistoryFile.POSTRUN_SCLK,
                config.getSclkKernelOutputDir().toAbsolutePath(),
                config.getSclkKernelBasename());

        if (this.runHistoryFile.anyValuesInColumn(RunHistoryFile.PRERUN_SCLKSCET) || this.runHistoryFile.anyValuesInColumn(RunHistoryFile.POSTRUN_SCLKSCET)) {
            // if MMTC has ever written SCLK-SCET files in the past, ensure that its configuration is still populated to be able to manipulate them
            if (config.isSclkScetConfigurationComplete()) {
                this.sclkscetFiles = Optional.of(
                        new OutputProduct(
                            RunHistoryFile.POSTRUN_SCLKSCET,
                            config.getSclkScetOutputDir().toAbsolutePath(),
                            config.getSclkScetFileBasename()
                        )
                );
            } else {
                throw new MmtcRollbackException("Rollback aborted due to inconsistent state: SCLK-SCET file information is present in the Run History File, but SCLK-SCET configuration is not fully specified in the configuration file.  No changes were made.  Please resolve and retry.");
            }
        } else {
            // if it hasn't, then no need to check configuration, and don't operate on them
            this.sclkscetFiles = Optional.empty();
        }

        if (this.runHistoryFile.anyValuesInColumn(RunHistoryFile.PRERUN_UPLINKCMD) || this.runHistoryFile.anyValuesInColumn(RunHistoryFile.POSTRUN_UPLINKCMD)) {
            // if MMTC has ever written uplink command files in the past, ensure that its configuration is still populated to be able to manipulate them
            if (config.isUplinkFileConfigurationComplete()) {
                this.uplinkFiles = Optional.of(
                        new OutputProduct(
                                RunHistoryFile.POSTRUN_UPLINKCMD,
                                Paths.get(config.getUplinkCmdFileDir()).toAbsolutePath(),
                                config.getUplinkCmdFileBasename()
                        )
                );
            } else {
                throw new MmtcRollbackException("Rollback aborted due to inconsistent state: Uplink command file information is present in the Run History File, but uplink command file configuration is not fully specified in the configuration file.  No changes were made.  Please resolve and retry.");
            }
        } else {
            // if it hasn't, then no need to check configuration, and don't operate on them
            this.uplinkFiles = Optional.empty();
        }
    }

    /**
     * The primary method for rolling back MMTC's output products to a previous state. All rollback processes happen before
     * TimeCorrelationApp is instantiated in main() and consequently use a unique empty config constructor.
     * Rollback is initiated by passing "rollback" as the first arg when invoking MMTC.
     * @throws Exception if TimeCorrelationAppConfig can't be instantiated or if any output products are inaccessible
     * (this includes products being modified between rollback being initiated and deletion confirmation being given by the user)
     */
    public void rollback() throws Exception {
        final List<OutputProduct> allProducts = new ArrayList<>(Arrays.asList(timeHist, tlmTable, sclkKernels));
        sclkscetFiles.ifPresent(allProducts::add);
        uplinkFiles.ifPresent(allProducts::add);

        final List<OutputProduct> tableProducts = new ArrayList<>(Arrays.asList(timeHist, tlmTable));

        final List<OutputProduct> textProducts = new ArrayList<>(Collections.singletonList(sclkKernels));
        sclkscetFiles.ifPresent(textProducts::add);
        uplinkFiles.ifPresent(textProducts::add);

        Scanner scanner = new Scanner(System.in);
        List<TableRecord> rawRunHistoryRecords = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS); // Will be used to rewrite complete RunHistoryFile
        List<TableRecord> runHistoryRecords = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS); // Will be used for rollback and shouldn't include rolled back entries
        logger.info(USER_NOTICE, String.format("...MMTC v.%s...\n", TimeCorrelationApp.BUILD_INFO.version));
        System.out.printf("Initiating rollback. %d most recent runs: \n", ROLLBACK_WINDOW_SIZE);
        for (int i = runHistoryRecords.size() - 1; i >= Math.max(runHistoryRecords.size() - ROLLBACK_WINDOW_SIZE, 0); i--) {
            TableRecord currentRecord = runHistoryRecords.get(i);
            System.out.printf("%s: MMTC run time %s by user %s (produced SCLK kernel %s)\n",
                    currentRecord.getValue("Run ID"),
                    currentRecord.getValue("Run Time"),
                    currentRecord.getValue("Active Run User"),
                    currentRecord.getValue("Latest SCLK Kernel Post-run"));
        }

        // Load the selected run
        System.out.println("\nPlease choose a run to become the new latest run (including leading zeros): ");
        String selectedRunId = scanner.nextLine();
        // Translate selected run ID to corresponding index of the run history file
        final int selectedRunIndex = IntStream.range(0, runHistoryRecords.size())
                .filter(i -> runHistoryRecords
                        .get(i)
                        .getValue("Run ID")
                        .equals(selectedRunId))
                .findAny()
                .orElse(-1);
        // Do the same thing for the raw record list which potentially has different indices due to the presence of rolled back entries
        final int rawRecordsSelectedRunIndex = IntStream.range(0, rawRunHistoryRecords.size())
                .filter(i -> rawRunHistoryRecords
                        .get(i)
                        .getValue("Run ID")
                        .equals(selectedRunId))
                .findAny()
                .orElse(-1);
        if (selectedRunIndex == -1) {
            throw new MmtcRollbackException(String.format("Invalid Run ID selected while initiating rollback, run %s is not recorded in the RunHistoryFile.",selectedRunId));
        }
        TableRecord selectedRun = runHistoryRecords.get(selectedRunIndex);
        logger.info(String.format("Run ID %s selected by user.", selectedRunId));

        // Determine which files will be deleted or truncated based on the selected run
        TableRecord latestRun = runHistoryRecords.get(runHistoryRecords.size()-1);
        if(selectedRun.equals(latestRun)) {
            throw new MmtcRollbackException("The selected run is also the latest run and rollback would not change any output products, aborting.");
        }

        for(OutputProduct product : allProducts) {
            product.setNewLatestVal(selectedRun.getValue(product.getNameInRunHistory()));
        }
        System.out.printf("Rolling back will revert all output products from the latest run (%s, SCLK Kernel %s) " +
                        "to the state they were in after run %s at %s with the following effects: %n",
                latestRun.getValue("Run ID"),
                latestRun.getValue("Latest SCLK Kernel Post-run"),
                selectedRun.getValue("Run ID"),
                selectedRun.getValue("Run Time"));

        ArrayList<File> filesToDelete = new ArrayList<>();
        for (OutputProduct product : textProducts) {
            filesToDelete.addAll(getOutputFilesToDelete(product));
        }
        for (File f : filesToDelete) {
            System.out.printf("- Will delete %s\n",f.toPath());
        }
        System.out.println("- The following files will be truncated: ");
        for (OutputProduct product : tableProducts) {
            System.out.printf("    - %s (%d rows removed)\n", product.getPath(), product.calcLinesToTruncate());
        }
        System.out.println("\033[0;1mIt is recommended to save copies of all these files for your anomaly reporting system before continuing, as these deletions cannot be undone!");

        System.out.println("Continue? [y/N]: ");
        String confirmRollback = scanner.nextLine();
        scanner.close();
        if (!confirmRollback.equalsIgnoreCase("y")) {
            throw new MmtcRollbackException("Rollback aborted due to user input, no changes made.");
        }
        logger.info(String.format("Rollback confirmed by user.  Output products will be reverted from their current state (as of run ID %s) to their state as of run ID %s. Modifying MMTC output products...", latestRun.getValue("Run ID"), selectedRun.getValue("Run ID")));

        // Delete requisite files
        for (File f : filesToDelete) {
            if(f.delete()) {
                if(!f.exists()) {
                    logger.info(USER_NOTICE, "Removed "+f.getName());
                } else {
                    logger.error("Failed to remove "+f.getName());
                }
            } else {
                logger.error("Failed to remove "+f.getName());
            }
        }
        ArrayList<File> deletionFailures = verifyDeletedFiles(filesToDelete);

        // Truncate requisite files
        for(OutputProduct product : tableProducts) {
            String message = String.format("Removed %d lines from %s",
                    product.getTable().truncateRecords(product.getNewLatestVal()),
                    product.getPath());
            logger.info(USER_NOTICE, message);
        }

        // Mark all runs newer than the NEW latest run as rolled back
        for (int i = rawRecordsSelectedRunIndex+1; i < rawRunHistoryRecords.size(); i++) {
            rawRunHistoryRecords.get(i).setValue("Rolled Back?", "true");
        }
        runHistoryFile.writeToTableFromTableRecords(rawRunHistoryRecords);

        if(!deletionFailures.isEmpty()) {
            throw new MmtcRollbackException("Rollback incomplete! The following file(s) could not be deleted: "+ deletionFailures);
        } else {
            logger.info(USER_NOTICE, "Rollback completed successfully.");
        }
    }

    /** Verifies that all elements in the list of files meant to be deleted actually no longer exist
     * @param filesToDelete List of files marked for deletion
     * @return All files (if any) from the input list that still exist
     */
    private ArrayList<File> verifyDeletedFiles(ArrayList<File> filesToDelete) {
        ArrayList<File> presentFiles = new ArrayList<>();
        for (File f : filesToDelete) {
            if(f.exists()) {
                presentFiles.add(f);
                logger.debug(String.format("Verifying that targeted output files have been deleted: %s NOT deleted.", f));
            } else {
                logger.debug(String.format("Verifying that targeted output files have been deleted: %s Deleted.", f));
            }
        }
        return presentFiles;
    }

    /**
     * Helper method for rollback that creates an ArrayList of files that should be deleted based on what the
     * NEW latest products should be. All files that are newer than those having the IDs provided will be selected.
     * @param product The OutputProduct object representing either SCLK kernels, SCLKSCET files, or UplinkCmd files
     * @return an ArrayList of Files to be deleted by rollback
     * @throws MmtcRollbackException  if any of the relevant output dirs are empty
     */
    private static ArrayList<File> getOutputFilesToDelete(OutputProduct product) throws MmtcRollbackException {
        ArrayList<File> filesToDelete = new ArrayList<>();
        FilenameFilter fileFilter = (dir1, name) -> name.startsWith(product.getBaseName());
        File[] foundFiles = product.getFile().listFiles(fileFilter);
        try {
            for (File foundFile : foundFiles ){
                int fileId = Integer.parseInt(foundFile.getName().replaceAll("^\\D*(\\d+).*$", "$1"));
                if (fileId > product.getNewLatestVal()) {
                    filesToDelete.add(foundFile);
                }
            }
            return filesToDelete;
        } catch (NullPointerException e) {
            throw new MmtcRollbackException("Failed to find the expected output files during rollback. " +
                    "This usually means MMTC doesn't have permission to access or modify output products.", e);
        }
    }
}