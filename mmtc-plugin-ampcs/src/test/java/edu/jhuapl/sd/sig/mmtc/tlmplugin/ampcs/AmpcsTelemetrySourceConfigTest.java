package edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs;

import edu.jhuapl.sd.sig.mmtc.cfg.TimeCorrelationCliAppConfig;
import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.chanvals.ChanValReadConfig;
import edu.jhuapl.sd.sig.mmtc.tlmplugin.ampcs.util.TemporaryTkConfigProperties;
import edu.jhuapl.sd.sig.mmtc.util.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class AmpcsTelemetrySourceConfigTest {

    @Test
    public void testGetActiveOscillatorSelectionMode() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/valid-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
                TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

                AmpcsTelemetrySourceConfig ampcsConfig = new AmpcsTelemetrySourceConfig(config);

                assertEquals(AmpcsTelemetrySourceConfig.ActiveOscillatorSelectionMode.by_vcid, ampcsConfig.getActiveOscillatorSelectionMode());
            }
        }

        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/fixed-osc-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
                TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

                AmpcsTelemetrySourceConfig ampcsConfig = new AmpcsTelemetrySourceConfig(config);

                assertEquals(AmpcsTelemetrySourceConfig.ActiveOscillatorSelectionMode.fixed, ampcsConfig.getActiveOscillatorSelectionMode());
                assertEquals("1", ampcsConfig.getFixedActiveOscillatorId());
            }
        }

        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/no-osc-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
                TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

                AmpcsTelemetrySourceConfig ampcsConfig = new AmpcsTelemetrySourceConfig(config);

                assertEquals(AmpcsTelemetrySourceConfig.ActiveOscillatorSelectionMode.none, ampcsConfig.getActiveOscillatorSelectionMode());
            }
        }
    }

    @Test
    public void testGetActiveOscillatorIdByVcid() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/valid-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
                TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

                AmpcsTelemetrySourceConfig ampcsConfig = new AmpcsTelemetrySourceConfig(config);

                assertEquals("1", ampcsConfig.getActiveOscillatorIdByVcid(0));
                assertEquals("1", ampcsConfig.getActiveOscillatorIdByVcid(1));
                assertEquals("2", ampcsConfig.getActiveOscillatorIdByVcid(32));
                assertEquals("2", ampcsConfig.getActiveOscillatorIdByVcid(33));
                assertThrows(
                        IllegalStateException.class,
                        () -> ampcsConfig.getActiveOscillatorIdByVcid(3)
                );

                ChanValReadConfig osc1TempChanValReadConfig = ampcsConfig.getOscTempChanValReadConfig("1").get();
                assertEquals("T-10014", osc1TempChanValReadConfig.channelId);
                assertEquals("dn", osc1TempChanValReadConfig.readField);

                ChanValReadConfig osc2TempChanValReadConfig = ampcsConfig.getOscTempChanValReadConfig("2").get();
                assertEquals("T-10015", osc2TempChanValReadConfig.channelId);
                assertEquals("dn", osc2TempChanValReadConfig.readField);

                assertThrows(
                        IllegalArgumentException.class,
                        () -> ampcsConfig.getOscTempChanValReadConfig("3")
                );
            }
        }
    }

    @Test
    public void testGetActiveOscillatorIdByVcidWithOscillatorNames() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/named-oscillators/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
                TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

                AmpcsTelemetrySourceConfig ampcsConfig = new AmpcsTelemetrySourceConfig(config);

                assertEquals("prime", ampcsConfig.getActiveOscillatorIdByVcid(0));
                assertEquals("prime", ampcsConfig.getActiveOscillatorIdByVcid(1));
                assertEquals("backup", ampcsConfig.getActiveOscillatorIdByVcid(32));
                assertEquals("backup", ampcsConfig.getActiveOscillatorIdByVcid(33));
                assertEquals("spare", ampcsConfig.getActiveOscillatorIdByVcid(64));
                assertEquals("spare", ampcsConfig.getActiveOscillatorIdByVcid(65));
                assertThrows(
                        IllegalStateException.class,
                        () -> ampcsConfig.getActiveOscillatorIdByVcid(3)
                );

                ChanValReadConfig osc1TempChanValReadConfig = ampcsConfig.getOscTempChanValReadConfig("prime").get();
                assertEquals("T-10014", osc1TempChanValReadConfig.channelId);
                assertEquals("dn", osc1TempChanValReadConfig.readField);

                ChanValReadConfig osc2TempChanValReadConfig = ampcsConfig.getOscTempChanValReadConfig("backup").get();
                assertEquals("T-10015", osc2TempChanValReadConfig.channelId);
                assertEquals("dn", osc2TempChanValReadConfig.readField);

                ChanValReadConfig osc3TempChanValReadConfig = ampcsConfig.getOscTempChanValReadConfig("spare").get();
                assertEquals("T-10016", osc3TempChanValReadConfig.channelId);
                assertEquals("dn", osc3TempChanValReadConfig.readField);

                assertThrows(
                        IllegalArgumentException.class,
                        () -> ampcsConfig.getOscTempChanValReadConfig("foobar")
                );
            }
        }
    }

    @Test
    public void testGetActiveRadioId() throws Exception {
        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/valid-config/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
                TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

                AmpcsTelemetrySourceConfig ampcsConfig = new AmpcsTelemetrySourceConfig(config);

                assertEquals("-", ampcsConfig.getActiveRadioId());
            }
        }

        try (TemporaryTkConfigProperties tkConfigProps = TemporaryTkConfigProperties.withTestTkPacketDescriptionFile("../mmtc-plugin-ampcs/src/test/resources/config/with-active-radio-id/")) {
            try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
                mockedEnvironment
                        .when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
                        .thenReturn(tkConfigProps.getTestTkConfigDir().toString());

                String[] cliArgs = {"2006-01-20T01:00:00.000Z", "2006-01-20T10:00:00.000Z"};
                TimeCorrelationCliAppConfig config = new TimeCorrelationCliAppConfig(cliArgs);

                AmpcsTelemetrySourceConfig ampcsConfig = new AmpcsTelemetrySourceConfig(config);

                assertEquals("2", ampcsConfig.getActiveRadioId());
            }
        }
    }
}