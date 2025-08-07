package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.fail;

class TimeHistoryFileTests {
    private TimeHistoryFile table;

    void loadTable(String path) {
        try {
            table = new TimeHistoryFile(Paths.get(path));
            table.resetParser();
        }
        catch (MmtcException ex) {
            fail("Failed to load TimeHistoryFile file." + ex);
        }
    }

    @Test
    void testHeadersMatch() {
        loadTable("src/test/resources/tables/TimeHistoryFile_empty.csv");
        List<String> expectedHeaders = table.getHeaders();
        List<String> parsedHeaders = new ArrayList<>(table.parser.getHeaderMap().keySet());
        assertLinesMatch(expectedHeaders, parsedHeaders);
    }
}
