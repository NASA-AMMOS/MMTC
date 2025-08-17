package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.MmtcRollbackException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.products.AppendedFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.EntireFileOutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.OutputProductTypeDefinition;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunHistoryFile extends AbstractTimeCorrelationTable {
    private static final Logger logger = LogManager.getLogger();

    public static final String RUN_TIME = "Run Time";
    public static final String RUN_ID = "Run ID";
    public static final String ROLLEDBACK = "Rolled Back?";
    public static final String RUN_USER = "Run User";
    public static final String CLI_ARGS = "MMTC Invocation Args Used";

    /*
    public static final String PRERUN_SCLK = "Latest SCLK Kernel Pre-run";
    public static final String POSTRUN_SCLK = "Latest SCLK Kernel Post-run";
    public static final String PRERUN_SCLKSCET = "Latest SCLKSCET File Pre-run";
    public static final String POSTRUN_SCLKSCET = "Latest SCLKSCET File Post-run";
    public static final String PRERUN_TIMEHIST = "Latest TimeHistoryFile Line Pre-run";
    public static final String POSTRUN_TIMEHIST = "Latest TimeHistoryFile Line Post-run";
    public static final String PRERUN_RAWTLMTABLE = "Latest RawTlmTable Line Pre-run";
    public static final String POSTRUN_RAWTLMTABLE = "Latest RawTlmTable Line Post-run";
    public static final String PRERUN_UPLINKCMD = "Latest Uplink Command File Pre-run";
    public static final String POSTRUN_UPLINKCMD = "Latest Uplink Command File Post-run";
     */

    public enum RollbackEntryOption {
        IGNORE_ROLLBACKS,
        INCLUDE_ROLLBACKS
    }

    public RunHistoryFile(Path path) {
        super(path);
    }

    public static String getPreRunProductColNameFor(OutputProductTypeDefinition<?> def) {
        if (def instanceof EntireFileOutputProductDefinition) {
            return String.format("Latest %s Pre-run", def.name);
        } else if (def instanceof AppendedFileOutputProductDefinition) {
            return String.format("Latest %s Line Pre-run", def.name);
        } else {
            throw new IllegalArgumentException("Unknown product type: " + def.getClass().getSimpleName());
        }
    }

    public static String getPostRunProductColNameFor(OutputProductTypeDefinition<?> def) {
        if (def instanceof EntireFileOutputProductDefinition) {
            return String.format("Latest %s Post-run", def.name);
        } else if (def instanceof AppendedFileOutputProductDefinition) {
            return String.format("Latest %s Line Post-run", def.name);
        } else {
            throw new IllegalArgumentException("Unknown product type: " + def.getClass().getSimpleName());
        }
    }

    @Override
    public List<String> getHeaders() {
        List<String> headers =  new ArrayList<>(Arrays.asList(
                RUN_TIME,
                RUN_ID,
                ROLLEDBACK,
                RUN_USER,
                CLI_ARGS
        ));

        OutputProductTypeDefinition.all().forEach(def -> {
            headers.add(getPreRunProductColNameFor(def));
            headers.add(getPostRunProductColNameFor(def));
        });

        logger.info("Run History File headers: " + headers.toString());

        return headers;
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
        if (!this.getFile().exists()) {
            return records;
        }

        resetParser();

        for (CSVRecord record : parser) {
            TableRecord newRecord = new TableRecord(getHeaders());
            if (record.get("Rolled Back?").equals("true") && option.equals(RollbackEntryOption.IGNORE_ROLLBACKS)) {
                continue;
            }

            for (String column : getHeaders()) {
                newRecord.setValue(column, record.get(column));
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
