package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.products.definition.BuiltInOutputProductDefinitionFactory;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import edu.jhuapl.sd.sig.mmtc.products.model.RunHistoryFile;
import edu.jhuapl.sd.sig.mmtc.products.model.TableRecord;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class RunHistoryFileTests {
    // a shortcut to get just the basic built-in product defs
    private static final List<OutputProductDefinition<?>> BUILTIN_DEFS = new BuiltInOutputProductDefinitionFactory().getApplicableTypes().stream().map(t -> new BuiltInOutputProductDefinitionFactory().create(t, null, null)).collect(Collectors.toList());

    @Test
    public void testRunHistoryFileBasicRead() throws MmtcException {
        // with no rollbacks, with every column at least partially populated
        final RunHistoryFile runHistoryFile = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh.csv"), BUILTIN_DEFS);
        assertEquals(4, runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).size());
        assertEquals(4, runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).size());

        List<TableRecord> recs = runHistoryFile.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS);
        TableRecord lastRec = recs.get(recs.size() - 1);
        assertEquals("2024-12-12T21:59:35.011Z", lastRec.getValue(RunHistoryFile.RUN_TIME));
        assertEquals("00004", lastRec.getValue(RunHistoryFile.RUN_ID));
        assertEquals("1003", lastRec.getValue("Latest SCLK Kernel Pre-run"));
        assertEquals("1734040773", lastRec.getValue("Latest Uplink Command File Post-run"));

        // with no rollbacks, with the uplink cmd file columns not populated
        final RunHistoryFile runHistoryFileNoUplink = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-no-uplink.csv"), BUILTIN_DEFS);
        assertEquals(4, runHistoryFileNoUplink.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).size());
        assertEquals(4, runHistoryFileNoUplink.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).size());

        recs = runHistoryFileNoUplink.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS);
        lastRec = recs.get(recs.size() - 1);
        assertEquals("2024-12-12T21:59:35.011Z", lastRec.getValue(RunHistoryFile.RUN_TIME));
        assertEquals("00004", lastRec.getValue(RunHistoryFile.RUN_ID));
        assertEquals("1003", lastRec.getValue("Latest SCLK Kernel Pre-run"));
        assertEquals("-", lastRec.getValue("Latest Uplink Command File Post-run"));

        // with a rollback
        final RunHistoryFile runHistoryFileRollback = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-rollback.csv"), BUILTIN_DEFS);
        assertEquals(6, runHistoryFileRollback.readRecords(RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS).size());
        assertEquals(4, runHistoryFileRollback.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS).size());

        recs = runHistoryFileRollback.readRecords(RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS);
        lastRec = recs.get(recs.size() - 1);
        assertEquals("2025-07-22T15:52:19.078Z", lastRec.getValue(RunHistoryFile.RUN_TIME));
        assertEquals("00006", lastRec.getValue(RunHistoryFile.RUN_ID));
        assertEquals("1003", lastRec.getValue("Latest SCLK Kernel Pre-run"));
        assertEquals("1753199537", lastRec.getValue("Latest Uplink Command File Post-run"));
    }

    @Test
    public void testRunHistoryFileValueQueries() throws URISyntaxException, MmtcException {
        // with no rollbacks, with every column at least partially populated
        final RunHistoryFile runHistoryFile = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh.csv"), BUILTIN_DEFS);
        assertEquals(Optional.of("1734040773"), runHistoryFile.getLatestNonEmptyValueOfCol("Latest Uplink Command File Post-run", RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS));
        assertTrue(runHistoryFile.anyValuesInColumn("Latest Uplink Command File Post-run"));
        assertEquals(Optional.of("1002"), runHistoryFile.getValueOfColForRun("00002", "Latest SCLK Kernel Post-run"));
        assertEquals(Optional.of("1004"), runHistoryFile.getLatestNonEmptyValueOfCol("Latest SCLK Kernel Post-run", RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS));

        // with no rollbacks, with the uplink cmd file columns not populated
        final RunHistoryFile runHistoryFileNoUplink = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-no-uplink.csv"), BUILTIN_DEFS);
        assertEquals(Optional.empty(), runHistoryFileNoUplink.getLatestNonEmptyValueOfCol("Latest Uplink Command File Post-run", RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS));
        assertEquals(Optional.empty(), runHistoryFileNoUplink.getLatestValueOfCol("Latest Uplink Command File Post-run", RunHistoryFile.RollbackEntryOption.INCLUDE_ROLLBACKS));
        assertFalse(runHistoryFileNoUplink.anyValuesInColumn("Latest Uplink Command File Post-run"));

        // with a rollback
        final RunHistoryFile runHistoryFileRollback = new RunHistoryFile(Paths.get("src/test/resources/RunHistoryFileTests/RunHistoryFile-nh-rollback.csv"), BUILTIN_DEFS);
        assertEquals(Optional.of("1753199537"), runHistoryFileRollback.getLatestNonEmptyValueOfCol("Latest Uplink Command File Post-run", RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS));
        assertEquals(Optional.of("1753199537"), runHistoryFileRollback.getLatestValueOfCol("Latest Uplink Command File Post-run", RunHistoryFile.RollbackEntryOption.IGNORE_ROLLBACKS));
        assertTrue(runHistoryFileRollback.anyValuesInColumn("Latest Uplink Command File Post-run"));
    }
}
