package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationAppConfig;
import edu.jhuapl.sd.sig.mmtc.filter.ConsecutiveMasterChannelFrameFilter;
import edu.jhuapl.sd.sig.mmtc.filter.TimeCorrelationFilter;
import edu.jhuapl.sd.sig.mmtc.tlm.TelemetrySource;
import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.util.TemporaryTkConfigProperties;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JUnit Test case for functions in Class AmpcsTlmArchive.
 *
 * NOTE:
 * In order to load the JNISpice library, the VM Options within the Run Configuration of each
 * test that uses SPICE must contain the line below. Tests that use the logging or
 * configuration must contain the options given two lines below.
 * -Djava.library.path=/path/to/JNISpice/lib -Dlog4j.configurationFile=/path/to/log4j2.properties
 */
public class AmpcsTlmArchiveTests {
    @Test
    void testConnect() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/valid-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z", "--ampcs-session-id", "10"};
                TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

                AmpcsTlmArchive tlm = new AmpcsTlmArchive();
                tlm.applyConfiguration(config);
                tlm.connect();

                String actualSessionId = tlm.getSessionId();

                assertTrue(tlm.isConnectedToAmpcs());
                assertEquals("10", actualSessionId);
            }
        }
    }

    @Test
    void testUseUnallowableFilter() throws Exception {
        TimeCorrelationAppConfig mockedConfig = mock(TimeCorrelationAppConfig.class);
        Map<String, TimeCorrelationFilter> enabledFilters = new HashMap<>();
        enabledFilters.put(TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER, new ConsecutiveMasterChannelFrameFilter());
        when(mockedConfig.getFilters()).thenReturn(enabledFilters);
        // needed for AmpcsTelemetrySourceConfig to instantiate
        when(mockedConfig.containsNonEmptyKey("telemetry.source.plugin.ampcs.oscillator.activeOscillatorSelectionMode")).thenReturn(true);
        when(mockedConfig.getString("telemetry.source.plugin.ampcs.oscillator.activeOscillatorSelectionMode")).thenReturn("by_vcid");

        AmpcsTlmArchive tlmArchive = new AmpcsTlmArchive();

        assertThrows(
                MmtcException.class,
                () -> tlmArchive.applyConfiguration(mockedConfig),
                "When using the AmpcsTlmArchive telemetry source, the " +
                        TimeCorrelationAppConfig.CONSEC_MC_FRAME_FILTER +
                        " filter is not applicable and must be disabled by setting the configuration option " +
                        "filter.<filter name>.enabled to false."
        );
    }

    @Test
    void testGetGncParms() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/valid-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                // session 101 tells the fake chill_get_chanvals to return GNC parms
                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z", "--ampcs-session-id", "101"};
                TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

                AmpcsTlmArchive tlm = new AmpcsTlmArchive();
                tlm.applyConfiguration(config);
                tlm.connect();

                OffsetDateTime scet = TimeConvert.parseIsoDoyUtcStr("2006-020T01:00:00.000");

                TelemetrySource.GncParms gnc_parms = tlm.getGncTkParms(scet, 0.0);
                System.out.println(gnc_parms);

                double gncsclk = gnc_parms.getGncSclk();
                assertEquals(98765432.11111, gncsclk);

                double tdt_s = gnc_parms.getTdt_s();
                assertEquals(222222222.333333, tdt_s);

                double sclk1 = gnc_parms.getSclk1();
                assertEquals(0000123456.54321, sclk1);

                double tdt1 = gnc_parms.getTdt1();
                assertEquals(123456789.123456, tdt1);

                double clkrate1 = gnc_parms.getClkchgrate1();
                assertEquals(1.00000001234, clkrate1);
            }
        }
    }

    @Test
    void testGetOscillatorTemperature() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/valid-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                // session 102 tells the fake chill_get_chanvals to return oscillator temperature
                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z", "--ampcs-session-id", "102"};
                TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

                AmpcsTlmArchive tlm = new AmpcsTlmArchive();
                tlm.applyConfiguration(config);
                tlm.connect();

                OffsetDateTime scet = TimeConvert.parseIsoDoyUtcStr("2019-322T21:37:14.937000");

                double temp = tlm.getOscillatorTemperature(scet, "1");

                System.out.println("Oscillator temperature = " + String.valueOf(temp));

                assertEquals(123.4, temp);
            }
        }
    }

    @Test
    public void testUnsetPacketHeaderFineSclkModulus() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/missing-tkPacketHeaderFineSclk-val/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                // session 102 tells the fake chill_get_chanvals to return oscillator temperature
                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z", "--ampcs-session-id", "102"};
                TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

                AmpcsTlmArchive tlm = new AmpcsTlmArchive();
                tlm.applyConfiguration(config);
                tlm.connect();

                assertThrows(
                        NoSuchElementException.class,
                        () -> tlm.getSamplesInRange(config.getStartTime(), config.getStopTime())
                );
            }
        }
    }

    @Test
    public void testInvalidPacketHeaderFineSclkModulus() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/invalid-tkPacketHeaderFineSclk-val/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                // session 102 tells the fake chill_get_chanvals to return oscillator temperature
                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z", "--ampcs-session-id", "102"};
                TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(cliArgs);

                AmpcsTlmArchive tlm = new AmpcsTlmArchive();
                tlm.applyConfiguration(config);
                tlm.connect();

                assertThrows(
                        MmtcException.class,
                        () -> tlm.getSamplesInRange(config.getStartTime(), config.getStopTime()),
                        "AmpcsTlmArchive requires a positive packet header fine SCLK modulus to be set"
                );
            }
        }
    }
}
