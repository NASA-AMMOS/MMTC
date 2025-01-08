package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.app.MmtcRollbackException;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunHistoryFile extends AbstractTimeCorrelationTable {
    public enum RollbackEntryOption {
        IGNORE_ROLLBACKS,
        INCLUDE_ROLLBACKS
    }

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
    public static final String PRERUN_SUMMARYTABLE = "Latest SummaryTable Line Pre-run";
    public static final String POSTRUN_SUMMARYTABLE = "Latest SummaryTable Line Post-run";
    public static final String PRERUN_RAWTLMTABLE = "Latest RawTlmTable Line Pre-run";
    public static final String POSTRUN_RAWTLMTABLE = "Latest RawTlmTable Line Post-run";
    public static final String PRERUN_UPLINKCMD = "Latest Uplink Command File Pre-run";
    public static final String POSTRUN_UPLINKCMD = "Latest Uplink Command File Post-run";

    public RunHistoryFile(URI uri) {
        super(uri);
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
                PRERUN_SUMMARYTABLE,
                POSTRUN_SUMMARYTABLE,
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
     * @throws MmtcRollbackException if the table exists but can't be parsed
     */
    public List<TableRecord> readRunHistoryFile(RollbackEntryOption option) throws MmtcException, MmtcRollbackException {
        List<TableRecord> records = new ArrayList<>();
        if(!this.getFile().exists()) {
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
}
