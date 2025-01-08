package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.fail;

class SummaryTableTests {
    private SummaryTable table;

    void loadTable(String path) {
        try {
            URI uri = new URI(path);
            table = new SummaryTable(uri);
            table.resetParser();
        }
        catch (URISyntaxException | MmtcException ex) {
            fail("Failed to load SummaryTable file. " + ex);
        }
    }

    @Test
    void testHeadersMatch() {
        loadTable("src/test/resources/tables/SummaryTable_empty.csv");
        List<String> expectedHeaders = table.getHeaders();
        List<String> parsedHeaders = new ArrayList<>(table.parser.getHeaderMap().keySet());
        assertLinesMatch(expectedHeaders, parsedHeaders);
    }
}
