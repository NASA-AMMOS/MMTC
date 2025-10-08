package edu.jhuapl.sd.sig.mmtc.table;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;

import edu.jhuapl.sd.sig.mmtc.products.model.RawTelemetryTable;
import org.junit.jupiter.api.*;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RawTelemetryTableTests {
    private TimeCorrelationCliAppConfig config;
    private RawTelemetryTable table;

    void loadTable(String path) throws Exception {
        table = new RawTelemetryTable(Paths.get(path));
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
