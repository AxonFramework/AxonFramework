package org.axonframework;

import org.apache.commons.collections.set.ListOrderedSet;

import java.util.Arrays;
import java.util.Set;

/**
 * TODO: remove copy when moving from incubator to normal project
 *
 * @author Jettro Coenradie
 */
public class TestUtils {
    @SuppressWarnings({"unchecked"})
    public static <T> Set<T> setOf(T... items) {
        return ListOrderedSet.decorate(Arrays.asList(items));
    }

}
