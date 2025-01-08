package edu.jhuapl.sd.sig.mmtc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class TimeCorrelationAppTests {
    @Test
    @DisplayName("Nominal, single separator occurrence")
    void repeatedSclkKernelSeparatorTests_singleSeparator() {
        assertEquals("1", TimeCorrelationApp.constructNextSclkKernelCounter("new-horizons_0.sclk", "_", ".sclk"), "Test without padding failed");
        assertEquals("00001", TimeCorrelationApp.constructNextSclkKernelCounter("new-horizons_00000.sclk", "_", ".sclk"), "Test with padding failed");
	}

	@Test
	@DisplayName("Multiple separator occurrences in basename")
	void repeatedSclkKernelSeparatorTests_multipleSeparators() {
		assertEquals("2", TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_1.sclk", "_", ".sclk"), "Separator in basename without padding failed");
		assertEquals("00002", TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_00001.sclk", "_", ".sclk"), "Separator in basename with padding failed");
        assertEquals("2", TimeCorrelationApp.constructNextSclkKernelCounter("new-horizons__1.sclk", "_", ".sclk"), "Consecutive separators without padding failed");
        assertEquals("00002", TimeCorrelationApp.constructNextSclkKernelCounter("new-horizons__00001.sclk", "_", ".sclk"), "Consecutive separators with padding failed");
        assertEquals("2", TimeCorrelationApp.constructNextSclkKernelCounter("______1.sclk", "_", ".sclk"), "Basename consisting of all separators without padding failed");
        assertEquals("00002", TimeCorrelationApp.constructNextSclkKernelCounter("______00001.sclk", "_", ".sclk"), "Basename consisting of all separators with padding failed");
	}

	@Test
	@DisplayName("Separator in suffix")
	void repeatedSclkKernelSeparatorTests_separatorInSuffix() {
		assertEquals("3", TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_2.s_clk", "_", ".s_clk"), "Test without padding failed");
		assertEquals("00003", TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_00002.s_clk", "_", ".s_clk"), "Test with padding failed");
	}

	@Test
	@DisplayName("Expected exceptions")
	void repeatedSclkKernelSeparatorTests_expectedExceptions() {
		assertThrows(IndexOutOfBoundsException.class, () -> TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_00003.sclk", "_", ".s_clk"), "Expected an exception when suffix isn't present");
		assertThrows(IndexOutOfBoundsException.class, () -> TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_00003.sclk", "-", ".sclk"), "Expected an exception when separator isn't present");
		assertThrows(NumberFormatException.class, () -> TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_00003a.sclk", "_", ".sclk"), "Expected an exception when counter isn't integer");
		assertThrows(NumberFormatException.class, () -> TimeCorrelationApp.constructNextSclkKernelCounter("new_horizons_00003_.sclk", "_", ".sclk"), "Expected an exception when no counter after last separator");
    }
}
