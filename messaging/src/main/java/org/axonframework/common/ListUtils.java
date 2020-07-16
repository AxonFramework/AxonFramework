package org.axonframework.common;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility methods for operations on lists.
 *
 * @author Stefan Andjelkovic
 * @since 4.4
 */
public abstract class ListUtils {

    private ListUtils() {
        // prevent instantiation
    }

    /** 
     * Returns a new list containing unique elements from the given {@code list}. Original list is not modified.
     *
     * @param list Original list that will not be modified
     * @param <E> The type of elements in the list
     * @return List with unique elements
     */
    public static <E> List<E> distinct(final List<E> list) {
        return list.stream().distinct().collect(Collectors.toList());
    }
}
