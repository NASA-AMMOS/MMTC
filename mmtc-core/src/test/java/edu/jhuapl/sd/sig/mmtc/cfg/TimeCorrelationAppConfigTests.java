package edu.jhuapl.sd.sig.mmtc.cfg;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import edu.jhuapl.sd.sig.mmtc.util.Environment;
import spice.basic.KernelDatabase;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provides unit tests of TimeCorrelationXmlPropertiesConfig's ability to reject kernal path configurations that
 * contain consecutive or trailing commas.
 *
 * TimeCorrelationXmlPropertiesConfig doesn't make this particularly easy to test. Without larger refactoring, the best
 * we can to is mock the environment variable that specifies the path where TimeCorrelationXmlPropertiesConfig searches
 * for the config file so we can provide test versions of the default config file.
 *
 * However, note that this approach is a little risky: if we accidentally code an invalid test path, MMTC will still
 * find the default (non-test version) config file from src/main/resources since that's also on the classpath. The
 * tests will then be using unintented test inputs and giving you false results.
 */
class TimeCorrelationAppConfigTests {

    @Test
    @DisplayName("TimeCorrelationAppConfig.getKernelsToLoad baseline/negative test - no exceptions expected")
    void testCorrectCommasInKernelConfigs() throws Exception {
		TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(new String[] {"2020-001T00:00:00", "2020-001T23:59:59"});
		assertDoesNotThrow(
			() -> {
				config.getKernelsToLoad();
			}
		);
	}

    @Test
    @DisplayName("TimeCorrelationAppConfig.getKernelsToLoad trailing commas test")
    void testTrailingCommasInKernelConfigs() throws Exception {
		try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
			mockedEnvironment
				.when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
				.thenReturn("src/test/resources/ConfigTests/trailingCommas");
			TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(new String[] {"2020-001T00:00:00", "2020-001T23:59:59"});
			MmtcException e = assertThrows(
				MmtcException.class,
				() -> {
					config.getKernelsToLoad();
				}
			);
			assertEquals("Consecutive or trailing commas in kernel configurations", e.getMessage());
		}
	}

    @Test
    @DisplayName("TimeCorrelationAppConfig.getKernelsToLoad consecutive commas test")
    void testConsecutiveCommasInKernelConfigs() throws Exception {
		try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
			mockedEnvironment
				.when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
				.thenReturn("src/test/resources/ConfigTests/consecutiveCommas");
			TimeCorrelationAppConfig config = new TimeCorrelationAppConfig(new String[] {"2020-001T00:00:00", "2020-001T23:59:59"});
			MmtcException e = assertThrows(
				MmtcException.class,
				() -> {
					config.getKernelsToLoad();
				}
			);
			assertEquals("Consecutive or trailing commas in kernel configurations", e.getMessage());
		}
	}

	@Test
	void testParseVcidGroups() throws MmtcException {
		assertEquals(Arrays.asList(set(1)), TimeCorrelationAppConfig.parseVcidGroups("testkey", "1"));
		assertEquals(Arrays.asList(set(1), set(2)), TimeCorrelationAppConfig.parseVcidGroups("testkey", "1; 2"));

		assertEquals(Arrays.asList(set(1,2)), TimeCorrelationAppConfig.parseVcidGroups("testkey", "1,2"));
		assertEquals(Arrays.asList(set(1,2)), TimeCorrelationAppConfig.parseVcidGroups("testkey", "1,2;"));
		assertEquals(Arrays.asList(set(1,2), set(3,4)), TimeCorrelationAppConfig.parseVcidGroups("testkey", "1,2; 3,4"));
		assertEquals(Arrays.asList(set(1,2), set(3,4), set(3,5)), TimeCorrelationAppConfig.parseVcidGroups("testkey", "1,2; 3,4; 3,5"));

		assertEquals(Arrays.asList(set(0,6,7), set(5)), TimeCorrelationAppConfig.parseVcidGroups("testkey", "0,6,7; 5"));

		assertThrows(Exception.class, () -> { TimeCorrelationAppConfig.parseVcidGroups("testkey", ""); });
		assertThrows(Exception.class, () -> { TimeCorrelationAppConfig.parseVcidGroups("testkey", " "); });
		assertThrows(Exception.class, () -> { TimeCorrelationAppConfig.parseVcidGroups("testkey", ";"); });
		assertThrows(Exception.class, () -> { TimeCorrelationAppConfig.parseVcidGroups("testkey", " ; "); });
		assertThrows(Exception.class, () -> { TimeCorrelationAppConfig.parseVcidGroups("testkey", " ; 2"); });

		assertThrows(Exception.class, () -> { TimeCorrelationAppConfig.parseVcidGroups("testkey", "1,2 ; a,b"); });
	}

	@Test
	void testGetTkSclkFineTickModulusWithOverride() throws Exception {
		try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
			mockedEnvironment
					.when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
					.thenReturn("src/test/resources/ConfigTests/unusualSclkModulusOverride");
			TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("2020-001T00:00:00", "2020-001T23:59:59");

			assertEquals(12345, config.getTkSclkFineTickModulus());
		}
	}

	@Test
	void testGetTkSclkFineTickModulusNoOverride() throws Exception {
		try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
			mockedEnvironment
					.when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
					.thenReturn("src/test/resources/ConfigTests/noSclkModulusOverride");
			TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("2020-001T00:00:00", "2020-001T23:59:59");

			TimeConvert.loadSpiceLib();
			KernelDatabase.load("src/test/resources/nh_kernels/lsk/naif0012.tls");
			KernelDatabase.load("src/test/resources/nh_kernels/sclk/new-horizons_1454.tsc");

			assertEquals(50000, config.getTkSclkFineTickModulus());
		} finally {
			TimeConvert.unloadSpiceKernels();
		}
	}

	@Test
	void testCatchesMissingRequiredConfigKeys() throws Exception {
		try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
			mockedEnvironment
					.when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
					.thenReturn("src/test/resources/ConfigTests/missingKeys");
			TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("2020-001T00:00:00", "2020-001T23:59:59");

			ArrayList<String> expectedMissingVals = new ArrayList<>();
			expectedMissingVals.add("missionId");
			expectedMissingVals.add("spacecraft.id");
			expectedMissingVals.add("spice.kernel.sclk.baseName");
			expectedMissingVals.add("spice.kernel.sclk.separator");

			ArrayList<String> actualMissingVals = config.validateRequiredConfigKeys("TimeCorrelationConfigProperties-base.xml");
			assertEquals(expectedMissingVals, actualMissingVals, "Validation of config with four missing required entries.");
		}
	}

	@Test
	void testPassesConfigWithRequiredKeys() throws Exception {
		try (MockedStatic<Environment> mockedEnvironment = Mockito.mockStatic(Environment.class, Mockito.CALLS_REAL_METHODS)) {
			mockedEnvironment
					.when(() -> Environment.getEnvironmentVariable("TK_CONFIG_PATH"))
					.thenReturn("src/test/resources/");
			TimeCorrelationAppConfig config = new TimeCorrelationAppConfig("2020-001T00:00:00", "2020-001T23:59:59");

			ArrayList<String> actualMissingVals = config.validateRequiredConfigKeys("TimeCorrelationConfigProperties-base.xml");
			assertTrue(actualMissingVals.isEmpty(), "Validation of normal NH config returns no reported missing keys.");
		}
	}

	private static Set<Integer> set(Integer... ints) {
		return new HashSet<>(Arrays.asList(ints));
	}
}
