package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;

import org.junit.jupiter.api.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RawTelemetryTableTests {
    private TimeCorrelationAppConfig config;
    private RawTelemetryTable table;

    void loadTable(String path) throws Exception {
        table = new RawTelemetryTable(new URI(path));
        table.resetParser();
    }


    @Test
    void testHeadersMatch() throws Exception {
        loadTable("src/test/resources/tables/RawTelemetryTable_empty.csv");
        List<String> expectedHeaders = table.getHeaders();
        List<String> parsedHeaders = new ArrayList<>(table.parser.getHeaderMap().keySet());
        assertLinesMatch(expectedHeaders, parsedHeaders);
    }
}
