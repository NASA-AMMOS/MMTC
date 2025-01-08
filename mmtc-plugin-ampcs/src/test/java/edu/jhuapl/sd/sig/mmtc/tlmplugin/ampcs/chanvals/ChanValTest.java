package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals;

import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.AmpcsTelemetrySourceConfig;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChanValTest {
    protected AmpcsTelemetrySourceConfig config;

    @BeforeEach
    protected void setup() {
        this.config = mock(AmpcsTelemetrySourceConfig.class);
        when(this.config.getChannelIdFieldName()).thenReturn("channelId");
        when(this.config.getChannelScetFieldName()).thenReturn("scet");
    }

    protected static CSVParser parseCsv(String resourceName) throws IOException {
        return CSVParser.parse(
                SingleChanValReaderTest.class.getResourceAsStream(resourceName),
                StandardCharsets.UTF_8,
                CSVFormat.DEFAULT.withFirstRecordAsHeader()
        );
    }
}
