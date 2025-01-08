package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SingleChanValReaderTest extends ChanValTest {

    @Test
    public void simpleTest() throws IOException {
        CSVParser csvRecords = parseCsv("/test-chanvals.csv");

        SingleChanValReader readerABC2222 = new SingleChanValReader(config, new ChanValReadConfig("ABC-2222", "dn"), TimeConvert.parseIsoDoyUtcStr("2024-031T00:00:00"));
        SingleChanValReader readerABC5555 = new SingleChanValReader(config, new ChanValReadConfig("ABC-5555", "eu"), TimeConvert.parseIsoDoyUtcStr("2024-031T00:00:00"));
        SingleChanValReader readerABC9999 = new SingleChanValReader(config, new ChanValReadConfig("ABC-9999", "dn"), TimeConvert.parseIsoDoyUtcStr("2024-031T00:00:00"));

        csvRecords.forEach(r -> {
            readerABC2222.read(r);
            readerABC5555.read(r);
            readerABC9999.read(r);
        });

        assertEquals(22.0, readerABC2222.getValueClosestToTargetScet());
        assertEquals(1.00000001234, readerABC5555.getValueClosestToTargetScet());
        assertTrue(Double.isNaN(readerABC9999.getValueClosestToTargetScet()));
    }

    @Test
    public void testNearestInTime() {
        Map<String, Double> scetsPairedToClosestVal = new HashMap<>();
        scetsPairedToClosestVal.put("2024-030T23:00:00", 21.0);
        scetsPairedToClosestVal.put("2024-030T23:29:59", 21.0);
        scetsPairedToClosestVal.put("2024-030T23:30:01", 22.0);
        scetsPairedToClosestVal.put("2024-031T00:00:00", 22.0);
        scetsPairedToClosestVal.put("2024-031T00:25:19", 22.0);
        scetsPairedToClosestVal.put("2024-031T13:00:00", 23.0);

        scetsPairedToClosestVal.forEach((scet, expectedVal) -> {
            SingleChanValReader reader = new SingleChanValReader(config, new ChanValReadConfig("ABC-2222", "dn"), TimeConvert.parseIsoDoyUtcStr(scet));

            try {
                // have to reset the iterator each read through
                CSVParser csvRecords = parseCsv("/test-chanvals.csv");
                csvRecords.forEach(reader::read);
                assertEquals(expectedVal, reader.getValueClosestToTargetScet(), "Unexpected value at " + scet);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}