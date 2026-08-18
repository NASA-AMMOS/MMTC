package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChanValsReaderTest extends ChanValTest {
    @Test
    public void simpleTest() throws IOException {
        List<ChanValReadConfig> readerConfigs = new ArrayList<>();
        readerConfigs.add(new ChanValReadConfig("ABC-2222", "dn"));
        readerConfigs.add(new ChanValReadConfig("ABC-5555", "eu"));
        readerConfigs.add(new ChanValReadConfig("ABC-9999", "dn"));

        TargetScetMultiChanValsReader chanValsReader = new TargetScetMultiChanValsReader(config, readerConfigs, TimeConvert.parseIsoDoyUtcStr("2024-031T00:00:00"));

        CSVParser csvRecords = parseCsv("/test-chanvals.csv");

        csvRecords.forEach(chanValsReader::read);

        assertEquals(22.0, chanValsReader.getValueClosestToTargetScetFor("ABC-2222"));
        assertEquals(1.00000001234, chanValsReader.getValueClosestToTargetScetFor("ABC-5555"));
        assertTrue(Double.isNaN(chanValsReader.getValueClosestToTargetScetFor("ABC-9999")));

        assertThrows(
                IllegalArgumentException.class,
                () -> chanValsReader.getValueClosestToTargetScetFor("ABC-0000"),
                "No such channel ID in reader: ABC-0000"
        );
    }
}
