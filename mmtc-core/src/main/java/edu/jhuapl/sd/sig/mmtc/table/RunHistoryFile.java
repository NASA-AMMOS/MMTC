package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.MmtcRollbackException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunHistoryFile extends AbstractTimeCorrelationTable {
    public static final RunHistoryOutputProductDefinitions OUTPUT_PRODUCT_DEFINITIONS = new RunHistoryOutputProductDefinitions();

    public static final String RUN_TIME = "Run Time";
    public static final String RUN_ID = "Run ID";
    public static final String ROLLEDBACK = "Rolled Back?";
    public static final String RUN_USER = "Run User";
    public static final String CLI_ARGS = "MMTC Invocation Args Used";
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

    public enum RollbackEntryOption {
        IGNORE_ROLLBACKS,
        INCLUDE_ROLLBACKS
    }

    public RunHistoryFile(Path path) {
        super(path);
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList(
                RUN_TIME,
                RUN_ID,
                ROLLEDBACK,
                RUN_USER,
                CLI_ARGS,
                PRERUN_SCLK,
                POSTRUN_SCLK,
                PRERUN_SCLKSCET,
                POSTRUN_SCLKSCET,
                PRERUN_TIMEHIST,
                POSTRUN_TIMEHIST,
                PRERUN_RAWTLMTABLE,
                POSTRUN_RAWTLMTABLE,
                PRERUN_UPLINKCMD,
                POSTRUN_UPLINKCMD
        );
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

    @FunctionalInterface
    public interface ConfiguredOutputProductDefinitionConverter {
        ConfiguredOutputProductDefinition apply(MmtcConfig conf) throws MmtcException;
    }

    public static class OutputProductTypeDefinition {
        public enum Type {
            LINE_APPENDED_CSV,
            ENTIRE_FILE
        }

        public final String preRunOutputProductColName;
        public final String postRunOutputProductColName;
        public final Type productType;
        public final ConfiguredOutputProductDefinitionConverter toConfiguredOutputProductDefinition;

        public OutputProductTypeDefinition(String preRunOutputProductColName, String postRunOutputProductColName, Type productType, ConfiguredOutputProductDefinitionConverter toConfiguredOutputProductDefinition) {
            this.preRunOutputProductColName = preRunOutputProductColName;
            this.postRunOutputProductColName = postRunOutputProductColName;
            this.productType = productType;
            this.toConfiguredOutputProductDefinition = toConfiguredOutputProductDefinition;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            OutputProductTypeDefinition that = (OutputProductTypeDefinition) o;
            return Objects.equals(preRunOutputProductColName, that.preRunOutputProductColName) && Objects.equals(postRunOutputProductColName, that.postRunOutputProductColName) && productType == that.productType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(preRunOutputProductColName, postRunOutputProductColName, productType);
        }
    }

    public static class RunHistoryOutputProductDefinitions {
        public final List<OutputProductTypeDefinition> all;

        public RunHistoryOutputProductDefinitions() {
            all = Collections.unmodifiableList(Arrays.asList(
                    new OutputProductTypeDefinition(
                            PRERUN_SCLK,
                            POSTRUN_SCLK,
                            OutputProductTypeDefinition.Type.ENTIRE_FILE,
                            (conf) -> new ConfiguredEntireFileOutputProductDefinition(
                                    conf.getSclkKernelOutputDir().toAbsolutePath(),
                                    conf.getSclkKernelBasename()
                            )
                    ),

                    new OutputProductTypeDefinition(
                            PRERUN_SCLKSCET,
                            POSTRUN_SCLKSCET,
                            OutputProductTypeDefinition.Type.ENTIRE_FILE,
                            (conf) -> {
                                conf.validateSclkScetConfiguration();

                                return new ConfiguredEntireFileOutputProductDefinition(
                                        conf.getSclkScetOutputDir().toAbsolutePath(),
                                        conf.getSclkScetFileBasename()
                                );
                            }
                    ),


                    new OutputProductTypeDefinition(
                            PRERUN_RAWTLMTABLE,
                            POSTRUN_RAWTLMTABLE,
                            OutputProductTypeDefinition.Type.LINE_APPENDED_CSV,
                            (conf) -> new ConfiguredAppendedCsvOutputProductDefinition(
                                    Paths.get(conf.getRawTelemetryTablePath().toString().replace("file://", "")),
                                    new RawTelemetryTable(conf.getRawTelemetryTablePath()
                                    )
                            )
                    ),

                    new OutputProductTypeDefinition(
                            PRERUN_TIMEHIST,
                            POSTRUN_TIMEHIST,
                            OutputProductTypeDefinition.Type.LINE_APPENDED_CSV,
                            (conf) -> new ConfiguredAppendedCsvOutputProductDefinition(
                                        Paths.get(conf.getTimeHistoryFilePath().toString().replace("file://", "")),
                                        new TimeHistoryFile(conf.getTimeHistoryFilePath()
                                        )
                            )
                    ),

                    new OutputProductTypeDefinition(
                            PRERUN_UPLINKCMD,
                            POSTRUN_UPLINKCMD,
                            OutputProductTypeDefinition.Type.ENTIRE_FILE,
                            (conf) -> {
                                conf.validateUplinkCmdFileConfiguration();

                                return new ConfiguredEntireFileOutputProductDefinition(
                                        Paths.get(conf.getUplinkCmdFileDir()).toAbsolutePath(),
                                        conf.getUplinkCmdFileBasename()
                                );
                            }
                    )
            ));
        }
    }

    public interface ConfiguredOutputProductDefinition {
    }

    public static class ConfiguredEntireFileOutputProductDefinition implements ConfiguredOutputProductDefinition {
        public final Path containingDirectory;
        public final String basename;

        public ConfiguredEntireFileOutputProductDefinition(Path containingDirectory, String basename) {
            this.containingDirectory = containingDirectory;
            this.basename = basename;
        }
    }

    public static class ConfiguredAppendedCsvOutputProductDefinition implements ConfiguredOutputProductDefinition {
        public final Path pathToProduct;
        public final AbstractTimeCorrelationTable table;

        public ConfiguredAppendedCsvOutputProductDefinition(Path pathToProduct, AbstractTimeCorrelationTable table) {
            this.pathToProduct = pathToProduct;
            this.table = table;
        }
    }
}
