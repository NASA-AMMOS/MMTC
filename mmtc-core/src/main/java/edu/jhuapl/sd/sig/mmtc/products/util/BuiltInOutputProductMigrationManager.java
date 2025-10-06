package edu.jhuapl.sd.sig.mmtc.products.util;

import edu.jhuapl.sd.sig.mmtc.app.BuildInfo;
import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MigrationConfig;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.products.definition.AppendedFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

public class BuiltInOutputProductMigrationManager {
    private static final Logger logger = LogManager.getLogger();

    private static final List<String> MIGRATEABLE_MMTC_VERSIONS = Arrays.asList(
            "1.5.1",
            "1.6.0"
    );

    private final MmtcConfig config;
    private final String currentMmtcAndProductVersion;
    private final HashMap<String, Callable<Void>> migrations;

    public BuiltInOutputProductMigrationManager(String[] args) throws Exception {
        this(new MigrationConfig(args));
    }

    public BuiltInOutputProductMigrationManager(MmtcConfig config) {
        this.config = config;
        this.currentMmtcAndProductVersion = new BuildInfo().getNumericalVersion();

        this.migrations = new HashMap<>();
        this.migrations.put("1.5.1", () -> null);
        this.migrations.put("1.6.0", this::migrateToMmtc1_6_0);
    }

    public String getVersionOfExistingProducts() throws MmtcException {
        if (Files.exists(config.getRunHistoryFilePath())) {
            final GenericCsv runHistoryCsv = new GenericCsv(config.getRunHistoryFilePath());

            if (runHistoryCsv.hasColumn(RunHistoryFile.MMTC_BUILT_IN_OUTPUT_PRODUCT_VERSION)) {
                // if the Run History File exists and has the expected column, then read the value from the last row and return that
                final RunHistoryFile runHistoryFile = new RunHistoryFile(config.getRunHistoryFilePath(), config.getAllOutputProductDefs());
                Optional<String> mmtcProdVersion = runHistoryFile.getLatestValueOfCol(RunHistoryFile.MMTC_BUILT_IN_OUTPUT_PRODUCT_VERSION, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS);

                if (mmtcProdVersion.isPresent()) {
                    return mmtcProdVersion.get();
                } else {
                    // the Run History File is present but empty of non-rolled back runs, so no products should exist and therefore they're current with the current version of MMTC
                    return this.currentMmtcAndProductVersion;
                }
            } else {
                // if the Run History File exists but does not have the expected column, it must be MMTC v1.5.1
                return "1.5.1";
            }
        } else {
            // if the Run History File doesn't exist, assume that no products exist and therefore they're current with the current version of MMTC
            return this.currentMmtcAndProductVersion;
        }
    }

    public boolean existingProductsRequireMigration() throws MmtcException {
        return ! getVersionOfExistingProducts().equals(this.currentMmtcAndProductVersion);
    }

    public void assertExistingProductsDoNotRequireMigration() throws MmtcException {
        String versionOfExistingProducts = getVersionOfExistingProducts();

        if (existingProductsRequireMigration()) {
            throw new IllegalStateException(String.format("Please run migration to bring output products from MMTC %s up to date with the current installation of MMTC %s", versionOfExistingProducts, this.currentMmtcAndProductVersion));
        }
    }

    public void migrate() throws MmtcException, IOException {
        String versionOfExistingProducts = getVersionOfExistingProducts();

        if (versionOfExistingProducts.equals(this.currentMmtcAndProductVersion)) {
            logger.info(USER_NOTICE, "No migration required; exiting.");
            return;
        }

        if (! MIGRATEABLE_MMTC_VERSIONS.contains(versionOfExistingProducts)) {
            throw new MmtcException(String.format("Cannot migrate forward from MMTC %s; please manually migrate or start with a clean installation", versionOfExistingProducts));
        }

        // before migrating, back up all current output products to a zip archive
        try {
            FileZipArchiver.writeAllOutputProductsToArchive(config, "pre-migration-backup");
        } catch (IOException e) {
            throw new MmtcException("Could not create backup archive; no changes were made to output files. Please see error details and retry.", e);
        }

        // migrate, in order, version-by-version up to the current one
        List<String> versionsToMigrateTo = MIGRATEABLE_MMTC_VERSIONS.subList(
                MIGRATEABLE_MMTC_VERSIONS.indexOf(versionOfExistingProducts) + 1,
                MIGRATEABLE_MMTC_VERSIONS.indexOf(this.currentMmtcAndProductVersion) + 1
        );

        for (String v : versionsToMigrateTo) {
            try {
                migrations.get(v).call();
            } catch (Exception e) {
                // give advice on restoring from backup
                throw new MmtcException("Failed to migrate output products", e);
            }
        }

        logger.info(USER_NOTICE, "Migration complete.");
    }

    /**
     * Migrates MMTC output products from 1.5.1 to 1.6.0.
     *
     * @return null
     * @throws MmtcException
     */
    private Void migrateToMmtc1_6_0() throws MmtcException {
        final String mmtc160MigrationLogPrefix = "MMTC 1.6.0 migration: ";

        // only need to migrate 'appended' products for now; no need to migrate 'whole file' products

        // check all prerequisites before making any modifications
        if (! config.containsKey("table.summaryTable.uri")) {
            throw new MmtcException("Please populate the config key table.summaryTable.uri while migrations run; you may remove it afterwards.");
        }

        final Path summaryTablePath = Paths.get(config.getString("table.summaryTable.uri").replace("file:///", "/"));
        if (! Files.exists(summaryTablePath)) {
            throw new MmtcException("Summary Table not found: " + summaryTablePath);
        }

        if (! Files.exists(config.getTimeHistoryFilePath())) {
            throw new MmtcException("Time History File not found: " + config.getTimeHistoryFilePath());
        }

        if (! Files.exists(config.getRunHistoryFilePath())) {
            throw new MmtcException("Run History File not found: " + config.getRunHistoryFilePath());
        }

        // update the Time History File, including transferring values from the Summary Table
        {
            final GenericCsv thf = new GenericCsv(config.getTimeHistoryFilePath());
            final GenericCsv summTable = new GenericCsv(summaryTablePath);

            thf.renameColumn("ClkChgRate(s/s)", "Predicted Clk Chg Rate (s/s)");
            thf.addColumnAtIndexWithValues("Interpolated Clk Chg Rate (s/s)", 6, summTable.readValuesForColumn("Interpolated Clk Change Rate"));
            thf.addColumnAtIndexWithValues("TD SC (sec)", 19, summTable.readValuesForColumn("TD SC (sec)"));
            thf.addColumnAtIndexWithValues("TD BE (sec)", 20, summTable.readValuesForColumn("TD BE (sec)"));
            thf.addColumnAtIndexWithValues("TF Offset", 21, summTable.readValuesForColumn("TF Offset"));

            thf.write();
            logger.info(USER_NOTICE, mmtc160MigrationLogPrefix + "migrated Time History File at " + config.getTimeHistoryFilePath());
        }

        // Summary Table has been removed, but let's leave it there for manual deletion
        logger.info(USER_NOTICE, mmtc160MigrationLogPrefix + String.format(
                "please note that the Summary Table file has been deprecated, with all relevant values moved into the Time History File.  Please feel free to remove the Summary Table file at %s",
                summaryTablePath
        ));

        // update the Run History File
        final GenericCsv rhf = new GenericCsv(config.getRunHistoryFilePath());
        {
            // remove Summary Table from Run History File
            rhf.removeColumn("Latest SummaryTable Line Pre-run");
            rhf.removeColumn("Latest SummaryTable Line Post-run");

            // add missing version columns
            rhf.addColumnAtIndexWithValues("MMTC Version", 2, Collections.nCopies(rhf.getNumRows(), "1.5.1"));
            rhf.addColumnAtIndexWithValues("Built-In Output Product Version", 3, Collections.nCopies(rhf.getNumRows(), "1.5.1"));

            // change the representation of an empty CSV from '1' to '-'
            {
                List<OutputProductDefinition<?>> builtInAppendedProdDefs = config.getAllOutputProductDefs().stream()
                        .filter(OutputProductDefinition::isBuiltIn)
                        .filter(def -> def instanceof AppendedFileOutputProductDefinition)
                        .collect(Collectors.toList());

                for (OutputProductDefinition<?> builtInAppendedProdDef : builtInAppendedProdDefs) {
                    String preRunProdColName = RunHistoryFile.getPreRunProductColNameFor(builtInAppendedProdDef);
                    rhf.updateColValWhereEqualToOldValue(preRunProdColName, "1", "-");
                }
            }

            // add '-' for the smoothing triplet column, as this feature was introduced with 1.6.0
            {
                rhf.addColumnAtIndexWithValues("Smoothing Triplet TDT", 7, Collections.nCopies(rhf.getNumRows(), "-"));
            }
        }

        // finally, update the RHF's latest entry with the RHF to indicate a migration has occurred to this version
        rhf.updateLastRowWithColVal("Built-In Output Product Version", "1.6.0");
        rhf.write();
        logger.info(USER_NOTICE, mmtc160MigrationLogPrefix + "migrated Run History File at " + config.getRunHistoryFilePath());

        return null;
    }
}
