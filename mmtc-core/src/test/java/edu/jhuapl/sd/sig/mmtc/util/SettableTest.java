package edu.jhuapl.sd.sig.mmtc.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SettableTest {

    @Test
    public void basicTest() {
        Settable<Double> testSettable = new Settable<>();

        assertFalse(testSettable.isSet());
        assertThrows(
                IllegalStateException.class,
                testSettable::get,
                "Value not set."
                );

        testSettable.set(1.234);
        assertTrue(testSettable.isSet());
        assertEquals(1.234, testSettable.get());

        assertThrows(
                IllegalStateException.class,
                () -> { testSettable.set(9.876); },
                "Value already set."
        );

        assertThrows(
                UnsupportedOperationException.class,
                testSettable::toString,
                "Cannot serialize Settable to a String."
        );
    }
}
