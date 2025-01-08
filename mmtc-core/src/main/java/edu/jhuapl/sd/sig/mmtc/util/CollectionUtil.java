package edu.jhuapl.sd.sig.mmtc.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CollectionUtil {

    /**
     * Checks the given candidate set of elements against a collection of allowable sets.  If at least one allowable
     * set is a superset of the candidate set, then this method returns true.  Otherwise, it returns false.
     *
     * @param allowableSets allowed sets of elements
     * @param candidateSet set of elements to check against the allowed sets
     * @return true if any allowed set of elements is a superset of the candidate set, false otherwise
     * @param <T> the type of element within the sets
     */
    public static <T> boolean setsContainIntersectingSet(Collection<Set<T>> allowableSets, Set<T> candidateSet) {
        for (Set<T> allowableSet : allowableSets) {
            if (allowableSet.containsAll(candidateSet)) {
                return true;
            }
        }

        return false;
    }

    public static <T> Set<T> supersetOf(Collection<Set<T>> sets) {
        Set<T> results = new HashSet<>();
        for (Set<T> set : sets) {
            results.addAll(set);
        }
        return results;
    }

    public static String prettyPrint(Collection<Integer> ints) {
        return new ArrayList<>(ints).stream().sorted().map(i -> Integer.toString(i)).collect(Collectors.joining(","));
    }
}
