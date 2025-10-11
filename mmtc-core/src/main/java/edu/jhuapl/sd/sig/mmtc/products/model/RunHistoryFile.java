package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.MmtcRollbackException;
import edu.jhuapl.sd.sig.mmtc.products.definition.AppendedFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RunHistoryFile extends AbstractTimeCorrelationTable {
    private static final Logger logger = LogManager.getLogger();

    public static final String RUN_TIME = "Run Time";
    public static final String RUN_ID = "Run ID";
    public static final String MMTC_VERSION = "MMTC Version";
    public static final String MMTC_BUILT_IN_OUTPUT_PRODUCT_VERSION = "Built-In Output Product Version";
    public static final String ROLLEDBACK = "Rolled Back?";
    public static final String RUN_USER = "Run User";
    public static final String CLI_ARGS = "MMTC Invocation Args Used";
    public static final String SMOOTHING_TRIPLET_TDT = "Smoothing Triplet TDT";

    private final List<String> headers;
    private final List<String> newOutputProductHeadersToEstablishEmptyVersionsFor;
    private final List<DeconfiguredOutputProductColPair> deconfiguredOutputProductsToTrack;

    public enum RollbackEntryOption {
        IGNORE_ROLLBACKS,
        INCLUDE_ROLLBACKS
    }

    public static class DeconfiguredOutputProductColPair {
        public final String preRunColName;
        public final String postRunColName;

        private DeconfiguredOutputProductColPair(String preRunColName, String postRunColName) {
            this.preRunColName = preRunColName;
            this.postRunColName = postRunColName;
        }

        @Override
        public String toString() {
            return "DeconfiguredOutputProductColPair{" +
                    "preRunColName='" + preRunColName + '\'' +
                    ", postRunColName='" + postRunColName + '\'' +
                    '}';
        }
    }

    public RunHistoryFile(Path path, List<OutputProductDefinition<?>> allOutputProdDefs) throws MmtcException {
        super(path);

        if (getFile().exists()) {
            // if the file exists, read the order of its columns and update as necessary
            // reading from disk helps ensure a stable ordering of product names, even when optional products are deconfigured

            final List<String> currentHeaders = new ArrayList<>(readExistingHeadersFromFile());
            final List<String> newHeaders = new ArrayList<>();

            // look over all currently-configured output products to check whether there are now output product plugins defined since the last run
            allOutputProdDefs.forEach(def -> {
                final String prodPreRunColName = getPreRunProductColNameFor(def);
                final String prodPostRunColName = getPostRunProductColNameFor(def);

                if (! currentHeaders.contains(prodPreRunColName)) {
                    currentHeaders.add(prodPreRunColName);
                    newHeaders.add(prodPreRunColName);
                }

                if (! currentHeaders.contains(prodPostRunColName)) {
                    currentHeaders.add(prodPostRunColName);
                    newHeaders.add(prodPostRunColName);
                }
            });

            this.headers = Collections.unmodifiableList(currentHeaders);
            this.newOutputProductHeadersToEstablishEmptyVersionsFor = Collections.unmodifiableList(newHeaders);
            this.deconfiguredOutputProductsToTrack = Collections.unmodifiableList(determineDeconfiguredProducts(allOutputProdDefs));
        } else {
            // if the file does not exist, establish a new column ordering

            // default columns
            final List<String> headers = new ArrayList<>(Arrays.asList(
                    RUN_TIME,
                    RUN_ID,
                    MMTC_VERSION,
                    MMTC_BUILT_IN_OUTPUT_PRODUCT_VERSION,
                    ROLLEDBACK,
                    RUN_USER,
                    CLI_ARGS,
                    SMOOTHING_TRIPLET_TDT
            ));

            // a column for each currently-configured output product
            allOutputProdDefs.forEach(def -> {
                headers.add(getPreRunProductColNameFor(def));
                headers.add(getPostRunProductColNameFor(def));
            });

            this.headers = Collections.unmodifiableList(headers);
            this.newOutputProductHeadersToEstablishEmptyVersionsFor = new ArrayList<>();
            this.deconfiguredOutputProductsToTrack = new ArrayList<>();
        }
    }

    private List<DeconfiguredOutputProductColPair> determineDeconfiguredProducts(List<OutputProductDefinition<?>> allOutputProdDefs) throws MmtcException {
        return readExistingHeadersFromFile().stream()
                .filter(col -> col.startsWith("Latest"))
                .filter(col -> col.endsWith("Pre-run"))
                .filter(col -> allOutputProdDefs.stream().noneMatch(def -> col.equals(getPreRunProductColNameFor(def))))
                .map(col -> new DeconfiguredOutputProductColPair(col, col.replace("Pre", "Post")))
                .collect(Collectors.toList());
    }

    @Override
    public void writeRecord(TableRecord record) throws MmtcException {
        // modify the record to carry forward values of output product versions that are no longer written
        TableRecord updatedRec = new TableRecord(record);

        for (DeconfiguredOutputProductColPair deconfiguredOutProdColPair : deconfiguredOutputProductsToTrack) {
            final String latestProdVersionPreAndPostRun = getLatestNonEmptyValueOfCol(deconfiguredOutProdColPair.postRunColName, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).orElse("-");
            updatedRec.setValue(deconfiguredOutProdColPair.preRunColName, latestProdVersionPreAndPostRun);
            updatedRec.setValue(deconfiguredOutProdColPair.postRunColName, latestProdVersionPreAndPostRun);
        }

        super.writeRecord(updatedRec);
    }

    public static String getPreRunProductColNameFor(OutputProductDefinition<?> def) {
        if (def instanceof EntireFileOutputProductDefinition) {
            return String.format("Latest %s Pre-run", def.getName());
        } else if (def instanceof AppendedFileOutputProductDefinition) {
            return String.format("Latest %s Line Pre-run", def.getName());
        } else {
            throw new IllegalArgumentException("Unknown product type: " + def.getClass().getSimpleName());
        }
    }

    public static String getPostRunProductColNameFor(OutputProductDefinition<?> def) {
        if (def instanceof EntireFileOutputProductDefinition) {
            return String.format("Latest %s Post-run", def.getName());
        } else if (def instanceof AppendedFileOutputProductDefinition) {
            return String.format("Latest %s Line Post-run", def.getName());
        } else {
            throw new IllegalArgumentException("Unknown product type: " + def.getClass().getSimpleName());
        }
    }

    @Override
    public List<String> getHeaders() {
        return headers;
    }

    public List<DeconfiguredOutputProductColPair> getDeconfiguredOutputProductsToTrack() {
        return Collections.unmodifiableList(deconfiguredOutputProductsToTrack);
    }

    private List<String> readExistingHeadersFromFile() throws MmtcException {
        try {
            resetParser();
            final List<String> existingHeaders = parser.getHeaderNames();
            parser.close();
            return existingHeaders;
        } catch (IOException e) {
            throw new MmtcException(e);
        }
    }

    public void updateRowsForNewProducts() throws MmtcException {
        if (! getFile().exists()) {
            return;
        }

        if (newOutputProductHeadersToEstablishEmptyVersionsFor.isEmpty()) {
            return;
        }

        logger.info("Updating Run History File with additional columns: " + newOutputProductHeadersToEstablishEmptyVersionsFor);
        final List<TableRecord> allExistingRecords = readRecords(RollbackEntryOption.INCLUDE_ROLLBACKS);
        for (TableRecord existingRecord : allExistingRecords) {
            for (String newHeader : newOutputProductHeadersToEstablishEmptyVersionsFor) {
                existingRecord.setValue(newHeader, "-");
            }
        }

        writeToTableFromTableRecords(allExistingRecords);
    }

    /**
     * Reads the existing RunHistoryFile and returns its contents as a list of TableRecords each representing
     * individual runs. It intentionally ignores any runs previously used in rollback as indicated by the "Rolled Back?"
     * flag.
     * @param option specifies whether RunHistory entries previously involved in rollback will also be returned
     * @return A list of existing runs/entries/rows as TableRecords or an empty list if the file doesn't exist yet
     * @throws MmtcException if the table exists but can't be parsed
     */
    public List<TableRecord> readRecords(RollbackEntryOption option) throws MmtcException {
        List<TableRecord> records = new ArrayList<>();
        if (! this.getFile().exists()) {
            return records;
        }

        resetParser();

        for (CSVRecord record : parser) {
            TableRecord newRecord = new TableRecord(getHeaders());
            if (record.get(ROLLEDBACK).equals("true") && option.equals(RollbackEntryOption.IGNORE_ROLLBACKS)) {
                continue;
            }

            for (String column : getHeaders()) {
                if (record.isMapped(column)) {
                    newRecord.setValue(column, record.get(column));
                }
            }
            records.add(newRecord);
        }
        try {
            parser.close();
        } catch (IOException e) {
            throw new MmtcRollbackException("Rollback failed while reading RunHistoryFile: could not close parser");
        }
        return records;
    }

    public Collection<String> getSmoothingTripletTdtGValsToIgnoreDuringLookback() throws MmtcException {
        return readRecords(RollbackEntryOption.IGNORE_ROLLBACKS).stream()
                .map(rec -> rec.getValue(SMOOTHING_TRIPLET_TDT))
                .filter(tdtG -> ! tdtG.equals("-"))
                .collect(Collectors.toSet());
    }

    private TableRecord getRunHistoryRowForRunId(String runId) throws MmtcException {
        return readRecords(RollbackEntryOption.INCLUDE_ROLLBACKS).stream()
                .filter(r -> r.getValue(RUN_ID).equals(runId))
                .findFirst()
                .orElseThrow(() -> new MmtcException(String.format("Run ID %s not found in run history file.", runId)));
    }

    public Optional<String> getValueOfColForRun(String runId, String columnName) throws MmtcException {
        return valToOptional(getRunHistoryRowForRunId(runId).getValue(columnName));
    }

    private Optional<String> valToOptional(String val) {
        if (val.equals("-")) {
            return Optional.empty();
        } else {
            return Optional.of(val);
        }
    }

    public Optional<String> getLatestValueOfCol(String columnName, RollbackEntryOption option) throws MmtcException {
        Optional<String> rec = readRecords(option).stream()
                .map(r -> r.getValue(columnName))
                .reduce((a,b) -> b);

        if (rec.equals(Optional.of("-"))) {
            return Optional.empty();
        } else {
            return rec;
        }
    }

    public Optional<String> getLatestNonEmptyValueOfCol(String columnName, RollbackEntryOption option) throws MmtcException {
        Optional<String> rec = readRecords(option).stream()
                .filter(r -> ! (r.getValue(columnName).equals("-")))
                .map(r -> r.getValue(columnName))
                .reduce((a,b) -> b);

        return rec;
    }

    public boolean anyValuesInColumn(String columnName) throws MmtcException {
        List<TableRecord> recs = readRecords(RollbackEntryOption.INCLUDE_ROLLBACKS);
        return recs.stream().anyMatch(r -> ! r.getValue(columnName).equals("-"));
    }
}