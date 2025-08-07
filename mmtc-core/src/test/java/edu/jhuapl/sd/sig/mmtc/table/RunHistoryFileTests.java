package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RunHistoryFileTests {

    @Test
    public void testRunHistoryFileBasicRead() throws URISyntaxException, MmtcException {
        // with no rollbacks, with every column at least partially populated
        final RunHistoryFile runHistoryFile = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh.csv"));
        assertEquals(4, runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).size());
        assertEquals(4, runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).size());

        List<TableRecord> recs = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS);
        TableRecord lastRec = recs.get(recs.size() - 1);
        assertEquals("2024-12-12T21:59:35.011Z", lastRec.getValue(RunHistoryFile.RUN_TIME));
        assertEquals("00004", lastRec.getValue(RunHistoryFile.RUN_ID));
        assertEquals("1003", lastRec.getValue(RunHistoryFile.PRERUN_SCLK));
        assertEquals("1734040773", lastRec.getValue(RunHistoryFile.POSTRUN_UPLINKCMD));

        // with no rollbacks, with the uplink cmd file columns not populated
        final RunHistoryFile runHistoryFileNoUplink = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-no-uplink.csv"));
        assertEquals(4, runHistoryFileNoUplink.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).size());
        assertEquals(4, runHistoryFileNoUplink.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).size());

        recs = runHistoryFileNoUplink.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS);
        lastRec = recs.get(recs.size() - 1);
        assertEquals("2024-12-12T21:59:35.011Z", lastRec.getValue(RunHistoryFile.RUN_TIME));
        assertEquals("00004", lastRec.getValue(RunHistoryFile.RUN_ID));
        assertEquals("1003", lastRec.getValue(RunHistoryFile.PRERUN_SCLK));
        assertEquals("-", lastRec.getValue(RunHistoryFile.POSTRUN_UPLINKCMD));

        // with a rollback
        final RunHistoryFile runHistoryFileRollback = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-rollback.csv"));
        assertEquals(6, runHistoryFileRollback.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).size());
        assertEquals(4, runHistoryFileRollback.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).size());

        recs = runHistoryFileRollback.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS);
        lastRec = recs.get(recs.size() - 1);
        assertEquals("2025-07-22T15:52:19.078Z", lastRec.getValue(RunHistoryFile.RUN_TIME));
        assertEquals("00006", lastRec.getValue(RunHistoryFile.RUN_ID));
        assertEquals("1003", lastRec.getValue(RunHistoryFile.PRERUN_SCLK));
        assertEquals("1753199537", lastRec.getValue(RunHistoryFile.POSTRUN_UPLINKCMD));
    }

    @Test
    public void testRunHistoryFileValueQueries() throws URISyntaxException, MmtcException {
        // with no rollbacks, with every column at least partially populated
        final RunHistoryFile runHistoryFile = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh.csv"));
        assertEquals(Optional.of("1734040773"), runHistoryFile.getLatestNonEmptyValueOfCol(RunHistoryFile.POSTRUN_UPLINKCMD, RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS));
        assertTrue(runHistoryFile.anyValuesInColumn(RunHistoryFile.POSTRUN_UPLINKCMD));
        assertEquals(Optional.of("1002"), runHistoryFile.getValueOfColForRun("00002", RunHistoryFile.POSTRUN_SCLK));
        assertEquals(Optional.of("1004"), runHistoryFile.getLatestNonEmptyValueOfCol(RunHistoryFile.POSTRUN_SCLK, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS));

        // with no rollbacks, with the uplink cmd file columns not populated
        final RunHistoryFile runHistoryFileNoUplink = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-no-uplink.csv"));
        assertEquals(Optional.empty(), runHistoryFileNoUplink.getLatestNonEmptyValueOfCol(RunHistoryFile.POSTRUN_UPLINKCMD, RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS));
        assertEquals(Optional.empty(), runHistoryFileNoUplink.getLatestValueOfCol(RunHistoryFile.POSTRUN_UPLINKCMD, RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS));
        assertFalse(runHistoryFileNoUplink.anyValuesInColumn(RunHistoryFile.POSTRUN_UPLINKCMD));

        // with a rollback
        final RunHistoryFile runHistoryFileRollback = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-rollback.csv"));
        assertEquals(Optional.of("1753199537"), runHistoryFileRollback.getLatestNonEmptyValueOfCol(RunHistoryFile.POSTRUN_UPLINKCMD, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS));
        assertEquals(Optional.of("1753199537"), runHistoryFileRollback.getLatestValueOfCol(RunHistoryFile.POSTRUN_UPLINKCMD, RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS));
        assertTrue(runHistoryFileRollback.anyValuesInColumn(RunHistoryFile.POSTRUN_UPLINKCMD));
    }
}
