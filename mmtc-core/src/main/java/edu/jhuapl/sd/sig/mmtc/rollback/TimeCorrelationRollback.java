package edu.jhuapl.sd.sig.mmtc.rollback;

import edu.jhuapl.sd.sig.mmtc.app.MmtcCli;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.MmtcRollbackException;
import edu.jhuapl.sd.sig.mmtc.cfg.RollbackConfig;
import edu.jhuapl.sd.sig.mmtc.products.OutputProductTypeDefinition;
import edu.jhuapl.sd.sig.mmtc.products.SclkKernelProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class TimeCorrelationRollback {
    public static final int ROLLBACK_WINDOW_SIZE = 10;
    private static final Logger logger = LogManager.getLogger();

    private final RollbackConfig config;
    private final RunHistoryFile runHistoryFile;

    // stateful with operations of rollback
    private int rawRecordsSelectedRunIndex;
    private String rollbackOperationCommencementMessage;
    private List<TableRecord> rawRunHistoryRecords;
    private RollbackOperations rollbackOps;

    public TimeCorrelationRollback(String... args) throws Exception {
        this.config = new RollbackConfig(args);
        this.runHistoryFile = new RunHistoryFile(config.getRunHistoryFilePath());

        if (! runHistoryFile.exists()) {
            throw new MmtcRollbackException("Run History File not found; MMTC must have run at least one correlation that is recorded in its Run History File to use rollback.");
        }
    }

    /**
     * The primary method for rolling back MMTC's output products to a previous state. All rollback processes happen before
     * TimeCorrelationApp is instantiated in main() and consequently use a unique empty config constructor.
     * Rollback is initiated by passing "rollback" as the first arg when invoking MMTC.
     * @throws MmtcRollbackException if rollback is unsuccessful for any reason
     * (this includes products being modified between rollback being initiated and deletion confirmation being given by the user)
     */
    public void rollback() throws MmtcRollbackException {
        try {
            prepareRollback();
        } catch (Exception e) {
            throw new MmtcRollbackException("Rollback aborted; no changes were made. Please see error details and retry.", e);
        }

        try {
            performRollback();
        } catch (Exception e) {
            throw new MmtcRollbackException("Rollback did not complete successfully.  Please see error details and manually resolve the issue.", e);
        }
    }

    private void prepareRollback() throws MmtcException {
        Scanner scanner = new Scanner(System.in);
        rawRunHistoryRecords = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS); // Will be used to rewrite complete RunHistoryFile
        List<TableRecord> runHistoryRecords = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS); // Will be used for rollback and shouldn't include rolled back entries

        if (runHistoryRecords.isEmpty()) {
            throw new MmtcException("Cannot execute a rollback, as there are no available correlation runs to roll back to.  Please reference the Run History File for more details.");
        }

        logger.info(USER_NOTICE, String.format("...MMTC v.%s...\n", MmtcCli.BUILD_INFO.version));
        System.out.printf("Initiating rollback. %d most recent runs: \n", ROLLBACK_WINDOW_SIZE);
        for (int i = runHistoryRecords.size() - 1; i >= Math.max(runHistoryRecords.size() - ROLLBACK_WINDOW_SIZE, 0); i--) {
            TableRecord currentRecord = runHistoryRecords.get(i);
            System.out.printf("%s: MMTC run time %s by user %s (produced SCLK kernel %s)\n",
                    currentRecord.getValue(RunHistoryFile.RUN_ID),
                    currentRecord.getValue(RunHistoryFile.RUN_TIME),
                    currentRecord.getValue(RunHistoryFile.RUN_USER),
                    currentRecord.getValue(RunHistoryFile.getPostRunProductColNameFor(new SclkKernelProductDefinition()))
            );
        }

        final TableRecord latestRun = runHistoryRecords.get(runHistoryRecords.size() - 1);


        System.out.println("\nPlease choose a run to become the new latest run (including leading zeros), or enter '0' to roll back to MMTC's initial state (with no output products): ");
        String selectedRunId = scanner.nextLine();

        logger.info(String.format("Run ID %s selected by user.", selectedRunId));
        if (Integer.parseInt(selectedRunId) == 0) {
            System.out.printf(String.format("Rolling back to the initial state will revert all output products from the latest run (%s, SCLK Kernel %s) " +
                            "to a nonexistent state, except for the seed kernel.%n",
                    latestRun.getValue("Run ID"),
                    latestRun.getValue("Latest SCLK Kernel Post-run"))
            );

            rawRecordsSelectedRunIndex = -1;
            rollbackOps = RollbackOperations.toInitialState(runHistoryFile, config);

            rollbackOperationCommencementMessage = String.format(
                    "Rollback confirmed by user.  Output products will be reverted from their current state (as of run ID %s) to their initial state before any runs of MMTC. Modifying MMTC output products...",
                    latestRun.getValue(RunHistoryFile.RUN_ID)
            );
        } else {
            // Translate selected run ID to corresponding index of the run history file
            final int selectedRunIndex = IntStream.range(0, runHistoryRecords.size())
                    .filter(i -> runHistoryRecords
                            .get(i)
                            .getValue("Run ID")
                            .equals(selectedRunId))
                    .findAny()
                    .orElse(-1);

            // Do the same thing for the raw record list which potentially has different indices due to the presence of rolled back entries
            rawRecordsSelectedRunIndex = IntStream.range(0, rawRunHistoryRecords.size())
                    .filter(i -> rawRunHistoryRecords
                            .get(i)
                            .getValue("Run ID")
                            .equals(selectedRunId))
                    .findAny()
                    .orElse(-1);

            if (selectedRunIndex == -1) {
                throw new MmtcRollbackException(String.format("Invalid Run ID %s selected while initiating rollback, please check the Run History File and retry with a different ID.", selectedRunId));
            }

            TableRecord selectedRun = runHistoryRecords.get(selectedRunIndex);

            // Determine which files will be deleted or truncated based on the selected run
            if (selectedRun.equals(latestRun)) {
                throw new MmtcRollbackException("The selected run is also the latest run and rollback would not change any output products, aborting.");
            }

            System.out.printf("Rolling back will revert all output products from the latest run (%s, SCLK Kernel %s) " +
                            "to the state they were in after run %s at %s with the following effects: %n",
                    latestRun.getValue("Run ID"),
                    latestRun.getValue("Latest SCLK Kernel Post-run"),
                    selectedRun.getValue("Run ID"),
                    selectedRun.getValue("Run Time"));

            rollbackOps = RollbackOperations.fromRunHistoryFile(runHistoryFile, config, selectedRunId);

            rollbackOperationCommencementMessage = String.format(
                    "Rollback confirmed by user.  Output products will be reverted from their current state (as of run ID %s) to their state as of run ID %s. Modifying MMTC output products...",
                    latestRun.getValue(RunHistoryFile.RUN_ID),
                    selectedRun.getValue(RunHistoryFile.RUN_ID)
            );
        }

        System.out.println("- The following files will be deleted: ");
        for (String description : rollbackOps.getDescriptionOfFilesToDelete()) {
            System.out.printf("    - %s\n", description);
        }

        System.out.println("- The following files will be truncated: ");
        for (String description : rollbackOps.getDescriptionOfFilesToTruncate()) {
            System.out.printf("    - %s\n", description);
        }

        System.out.println("\033[0;1mIt is recommended to save copies of all these files for your anomaly reporting system before continuing, as these deletions cannot be undone!");

        System.out.println("Continue? [y/N]: ");
        String confirmRollback = scanner.nextLine();
        scanner.close();

        if (!confirmRollback.equalsIgnoreCase("y")) {
            throw new MmtcRollbackException("Rollback aborted due to user input, no changes made.");
        }
    }

    private void performRollback() throws Exception {
        logger.info(rollbackOperationCommencementMessage);

        Optional<String> failureMessages = rollbackOps.performAll();

        // Mark all runs newer than the NEW latest run as rolled back
        for (int i = rawRecordsSelectedRunIndex + 1; i < rawRunHistoryRecords.size(); i++) {
            rawRunHistoryRecords.get(i).setValue("Rolled Back?", "true");
        }

        runHistoryFile.writeToTableFromTableRecords(rawRunHistoryRecords);

        if (failureMessages.isPresent()) {
            throw new MmtcRollbackException("Rollback incomplete! The following error(s) occurred:\n" + failureMessages);
        } else {
            logger.info(USER_NOTICE, "Rollback completed successfully.");
        }
    }

    public static class RollbackOperations {
        private final List<ProductRollbackOperation> rollbackOperations;

        private RollbackOperations(List<ProductRollbackOperation> rollbackOperations) {
            this.rollbackOperations = Collections.unmodifiableList(rollbackOperations);
        }

        public static RollbackOperations fromRunHistoryFile(RunHistoryFile runHistoryFile, RollbackConfig config, String newRunIdThatWouldBeCurrentAfterRollback) throws MmtcException {
            List<ProductRollbackOperation> calculatedRollbackOperations = new ArrayList<>();

            for (OutputProductTypeDefinition outputProductDef : OutputProductTypeDefinition.all()) {
                String postRunOutputProductColName = RunHistoryFile.getPostRunProductColNameFor(outputProductDef);

                Optional<ProductRollbackOperation> operation = ProductRollbackOperation.calculateFor(
                        outputProductDef,
                        config,
                        runHistoryFile.getLatestValueOfCol(postRunOutputProductColName, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS),
                        runHistoryFile.getValueOfColForRun(newRunIdThatWouldBeCurrentAfterRollback, postRunOutputProductColName)
                );

                operation.ifPresent(calculatedRollbackOperations::add);
            }

            return new RollbackOperations(calculatedRollbackOperations);
        }

        public static RollbackOperations toInitialState(RunHistoryFile runHistoryFile, RollbackConfig config) throws MmtcException {
            List<ProductRollbackOperation> calculatedRollbackOperations = new ArrayList<>();

            for (OutputProductTypeDefinition outputProductDef : OutputProductTypeDefinition.all()) {
                String preRunOutputProductColName = RunHistoryFile.getPreRunProductColNameFor(outputProductDef);
                String postRunOutputProductColName = RunHistoryFile.getPostRunProductColNameFor(outputProductDef);

                Optional<ProductRollbackOperation> operation = ProductRollbackOperation.calculateFor(
                        outputProductDef,
                        config,
                        runHistoryFile.getLatestValueOfCol(postRunOutputProductColName, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS),
                        runHistoryFile.getValueOfColForRun(runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).get(0).getValue(RunHistoryFile.RUN_ID), preRunOutputProductColName)
                );

                operation.ifPresent(calculatedRollbackOperations::add);
            }

            return new RollbackOperations(calculatedRollbackOperations);
        }

        public List<String> getDescriptionOfFilesToTruncate() {
            return rollbackOperations.stream()
                    .filter(op -> op instanceof TableTruncationOperation)
                    .map(op -> ((TableTruncationOperation) op).getOperationDescription())
                    .collect(Collectors.toList());
        }

        public List<File> getFilesToDelete() {
            return rollbackOperations.stream()
                    .filter(op -> op instanceof MultiFileDeletionOperation)
                    .map(op -> ((MultiFileDeletionOperation) op).getOutputProductFilesToDelete())
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }

        public List<String> getDescriptionOfFilesToDelete() {
            return getFilesToDelete().stream()
                    .map(f -> f.toPath().toAbsolutePath().toString())
                    .collect(Collectors.toList());
        }

        public Optional<String> performAll() {
            List<String> errors = new ArrayList<>();

            rollbackOperations.forEach(op -> {
                errors.addAll((List<String>) op.perform());
            });

            if (errors.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(errors.stream().map(errorMsg -> "    - " + errorMsg).collect(Collectors.joining("\n")));
            }
        }
    }

    public static abstract class ProductRollbackOperation<T extends OutputProductTypeDefinition.ResolvedProductLocation> {
        public final T configuredDef;

        public ProductRollbackOperation(T configuredDef) {
            this.configuredDef = configuredDef;
        }

        public static Optional<ProductRollbackOperation> calculateFor(OutputProductTypeDefinition outputProductDef, RollbackConfig config, Optional<String> currentLatestProductVersion, Optional<String> newLatestProductVersion) throws MmtcException {
            if (currentLatestProductVersion.equals(newLatestProductVersion)) {
                return Optional.empty();
            } else {
                // here, we purposefully lazily retrieve config values (only reading it when necessary, so we don't e.g. read SCLK-SCET output product config keys if we're not rolling back a SCLK-SCET file)
                return Optional.of(outputProductDef.getRollbackOperation(config, newLatestProductVersion));
            }
        }

        public abstract List<String> perform();
    }

    public static class TableTruncationOperation extends ProductRollbackOperation<OutputProductTypeDefinition.ResolvedProductPath> {
        private final String newLatestProductVersion;

        public TableTruncationOperation(OutputProductTypeDefinition.ResolvedProductPath configuredDef, Optional<String> newLatestProductVersion) throws MmtcException {
            super(configuredDef);
            this.newLatestProductVersion = newLatestProductVersion.orElseThrow(() -> new MmtcException("A table truncation operation must be given a non-empty row number to truncate the file to"));

            // check to ensure file is present
            if (! fileExistsAndIsWritable(configuredDef.pathToProduct)) {
                throw new MmtcException(String.format("Rollback would truncate rows from file %s, but there is no such writable file present on the filesystem.  Please resolve this issue and retry.", configuredDef.pathToProduct.toAbsolutePath()));
            }
        }

        public String getOperationDescription() {
            return String.format("%s (%d rows truncated)", configuredDef.pathToProduct.toAbsolutePath(), calcLinesToTruncate());
        }

        public Integer calcLinesToTruncate() {
            return configuredDef.table.getLastLineNumber() - Integer.parseInt(newLatestProductVersion);
        }

        @Override
        public List<String> perform() {
            try {String message = String.format("Removed %d lines from %s",
                    configuredDef.table.truncateRecords(Integer.parseInt(newLatestProductVersion)),
                    configuredDef.pathToProduct);
                logger.info(USER_NOTICE, message);
                return Collections.emptyList();
            } catch (Exception e) {
                logger.error(String.format("Failed to truncate file %s", configuredDef.pathToProduct.toString()), e);
                return Arrays.asList("Failed to remove " + configuredDef.pathToProduct.toString() + "; see logged exception for more information.");
            }
        }
    }

    public static class MultiFileDeletionOperation extends ProductRollbackOperation<OutputProductTypeDefinition.ResolvedProductDirAndPrefix> {
        private final Optional<String> newLatestProductVersion;

        public MultiFileDeletionOperation(OutputProductTypeDefinition.ResolvedProductDirAndPrefix configuredDef, Optional<String> newLatestProductVersion) throws MmtcException {
            super(configuredDef);
            this.newLatestProductVersion = newLatestProductVersion;

            // check to ensure all files are present
            for (File f : getOutputProductFilesToDelete()) {
                if (! fileExistsAndIsWritable(f.toPath())) {
                    throw new MmtcException(String.format("Rollback would remove file %s, but there is no such writable file is present on the filesystem.  Please resolve this issue and retry.", f.toPath().toAbsolutePath()));
                }
            }
        }

        /**
         * Helper method for rollback that creates an ArrayList of files that should be deleted based on what the
         * NEW latest products should be. All files that are newer than those having the IDs provided will be selected.
         * @return an ArrayList of Files to be deleted by rollback
         */
        public List<File> getOutputProductFilesToDelete() {
            List<File> filesToDelete = new ArrayList<>();
            FilenameFilter fileFilter = (dir1, name) -> name.startsWith(configuredDef.filenamePrefix);
            File[] foundFiles = configuredDef.containingDirectory.toFile().listFiles(fileFilter);

            if (foundFiles == null) {
                throw new RuntimeException("Unexpectedly found no files to delete in " + configuredDef.containingDirectory);
            }

            for (File foundFile : foundFiles){
                int fileId = Integer.parseInt(foundFile.getName().replaceAll("^\\D*(\\d+).*$", "$1"));
                if ((! newLatestProductVersion.isPresent() || fileId > Integer.parseInt(newLatestProductVersion.get()))) {
                    logger.info(String.format("Removing %s because its version %d is earlier than new latest version %s", foundFile, fileId, newLatestProductVersion));
                    filesToDelete.add(foundFile);
                }
            }

            return filesToDelete;
        }

        @Override
        public List<String> perform() {
            List<String> errors = new ArrayList<>();

            getOutputProductFilesToDelete().stream().forEach(f -> {
                try {
                    if (f.delete()) {
                        if (!f.exists()) {
                            logger.info(USER_NOTICE, "Removed " + f.getName());
                        } else {
                            errors.add("Failed to remove " + f.getName());
                        }
                    } else {
                        errors.add("Failed to remove " + f.getName());
                    }
                } catch (Exception e) {
                    logger.error(String.format("Failed to remove file %s", f.toString()), e);
                    errors.add("Failed to remove " + f.getName() + "; see logged exception for more information.");
                }
            });

            return errors;
        }
    }

    public static class SingleFileDeletionOperation extends ProductRollbackOperation<OutputProductTypeDefinition.ResolvedProductPath> {
        public SingleFileDeletionOperation(OutputProductTypeDefinition.ResolvedProductPath configuredDef) throws MmtcException {
            super(configuredDef);

            // check to ensure file is present
            if (! fileExistsAndIsWritable(configuredDef.pathToProduct)) {
                throw new MmtcException(String.format("Rollback would remove file %s, but there is no such writable file is present on the filesystem.  Please resolve this issue and retry.", configuredDef.pathToProduct.toAbsolutePath()));
            }
        }

        /**
         * Helper method for rollback that creates an ArrayList of files that should be deleted based on what the
         * NEW latest products should be. All files that are newer than those having the IDs provided will be selected.
         *
         * @return an ArrayList of Files to be deleted by rollback
         */
        public List<File> getOutputProductFilesToDelete() {
            return Arrays.asList(configuredDef.pathToProduct.toFile());
        }

        @Override
        public List<String> perform() {
            List<String> errors = new ArrayList<>();

            getOutputProductFilesToDelete().stream().forEach(f -> {
                try {
                    if (f.delete()) {
                        if (!f.exists()) {
                            logger.info(USER_NOTICE, "Removed " + f.getName());
                        } else {
                            errors.add("Failed to remove " + f.getName());
                        }
                    } else {
                        errors.add("Failed to remove " + f.getName());
                    }
                } catch (Exception e) {
                    logger.error(String.format("Failed to remove file %s", f.toString()), e);
                    errors.add("Failed to remove " + f.getName() + "; see logged exception for more information.");
                }
            });

            return errors;
        }
    }

    public static boolean fileExistsAndIsWritable(Path p) {
        return Files.exists(p) && Files.isWritable(p);
    }
}