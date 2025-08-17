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
                UnsupportedOperationException.class,
                testSettable::toString,
                "Cannot serialize Settable to a String."
        );
    }

    /*
    todo mmtc-10: figure out what the ideal equals and hashcode impl for Settable should be
    @Test
    public void testEqualsAndHashcode() {
        Settable<Double> sameOne = new Settable<>();
        Settable<Double> sameTwo = new Settable<>();
        Settable<Double> differentOne = new Settable<>();

        assertEquals(sameOne, sameTwo);
        assertEquals(sameOne.hashCode(), sameTwo.hashCode());

        sameOne.set(1.234);
        sameTwo.set(1.234);

        assertEquals(sameOne, sameTwo);
        assertEquals(sameOne.hashCode(), sameTwo.hashCode());
        assertNotEquals(sameOne, differentOne);
        assertNotEquals(sameOne.hashCode(), differentOne.hashCode());

        differentOne.set(2.345);
        assertNotEquals(sameOne, differentOne);
        assertNotEquals(sameOne.hashCode(), differentOne.hashCode());
    }
     */
}
