package edu.jhuapl.sd.sig.mmtc;

import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import spice.basic.KernelDatabase;
import spice.basic.SpiceErrorException;

/**
 * JUnit Test cass for testing SPICE kernels for syntactic correctness.
 *
 * NOTE:
 * In order to load the JNISpice library, the VM Options within the Run Configuration of each
 * test that uses SPICE must contain the line below. Tests that use the logging or
 * configuration must contain the options given two lines below.
 * -Djava.library.path=/path/to/JNISpice/lib
 * -Dlog4j.configurationFile=/path/to/log4j2.properties
 */

public class KernelLoadTests {
    @BeforeAll
    static void teardown() throws TimeConvertException {
        TestHelper.ensureSpiceIsLoadedAndUnloadAllKernels();
    }

    @Test
    @DisplayName("loadSpiceKernels Test 1")
    void loadSpiceKernels_test1() throws SpiceErrorException, TimeConvertException {
        System.loadLibrary("JNISpice");
        KernelDatabase.load("src/test/resources/nh_kernels/mk/nh_tk_meta_mmtc_test.tm");
        System.out.println("SPICE kernels loaded:\n" + String.join("\n", TimeConvert.getLoadedKernelNames()));
        System.out.println("Kernels successfully loaded.");
    }
}

