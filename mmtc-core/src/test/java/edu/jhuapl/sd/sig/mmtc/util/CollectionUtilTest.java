package edu.jhuapl.sd.sig.mmtc.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CollectionUtilTest {
    @Test
    public void singleTestSetContainsIntersectingSet() {
        assertSetsContainSet(true, set(set(1)), set());
        assertSetsContainSet(true, set(set(1)), set(1));
        assertSetsContainSet(false, set(set(1)), set(1, 2));
        assertSetsContainSet(false, set(set(1)), set(2));

        assertSetsContainSet(true, set(set(1, 2)), set());
        assertSetsContainSet(true, set(set(1, 2)), set(1));
        assertSetsContainSet(true, set(set(1, 2)), set(1, 2));
        assertSetsContainSet(false, set(set(1, 2)), set(1, 2, 3));

        assertSetsContainSet(true, set(set(1, 2)), set(2));

        assertSetsContainSet(false, set(set(1, 2)), set(2, 3));
        assertSetsContainSet(false, set(set(1, 2)), set(3));
    }

    @Test
    public void multipleTestSetContainsIntersectingSet() {
        assertSetsContainSet(false, set(), set());

        assertSetsContainSet(true, set(set(1, 2), set(2,3)), set());
        assertSetsContainSet(true, set(set(1, 2), set(2,3)), set(1));
        assertSetsContainSet(true, set(set(1, 2), set(2,3)), set(2));
        assertSetsContainSet(true, set(set(1, 2), set(2,3)), set(3));

        assertSetsContainSet(true, set(set(1, 2), set(2,3)), set(1, 2));
        assertSetsContainSet(true, set(set(1, 2), set(2,3)), set(2, 3));

        assertSetsContainSet(false, set(set(1, 2), set(2,3)), set(1, 3));
        assertSetsContainSet(false, set(set(1, 2), set(2,3)), set(1, 2, 3));
    }

    @Test
    public void supersetTest() {
        assertEquals(set(), CollectionUtil.supersetOf(Arrays.asList(set(), set())));
        assertEquals(set(1), CollectionUtil.supersetOf(Arrays.asList(set(1), set())));
        assertEquals(set(1), CollectionUtil.supersetOf(Arrays.asList(set(1), set(1))));
        assertEquals(set(1, 2), CollectionUtil.supersetOf(Arrays.asList(set(1), set(2))));
        assertEquals(set(1, 2), CollectionUtil.supersetOf(Arrays.asList(set(1), set(1, 2))));
        assertEquals(set(1, 2), CollectionUtil.supersetOf(Arrays.asList(set(1, 2), set(1, 2))));
        assertEquals(set(1, 2, 3), CollectionUtil.supersetOf(Arrays.asList(set(1, 2), set(1, 2, 3))));
        assertEquals(set(1, 2, 3), CollectionUtil.supersetOf(Arrays.asList(set(1, 2), set(2, 3))));
        assertEquals(set(1, 2, 3), CollectionUtil.supersetOf(Arrays.asList(set(1, 2), set(3))));
    }

    private static <T> void assertSetsContainSet(boolean assertion, Collection<Set<T>> allowableSets, Set<T> candidateSet) {
        assertEquals(assertion, CollectionUtil.setsContainIntersectingSet(allowableSets, candidateSet));
    }

    private static <T> Set<T> set(T... elts) {
        return new HashSet<T>(Arrays.asList(elts));
    }
}