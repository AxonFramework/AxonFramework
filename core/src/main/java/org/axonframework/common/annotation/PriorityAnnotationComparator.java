package org.axonframework.common.annotation;

import org.axonframework.common.Priority;

import java.util.Comparator;

/**
 * Comparator that compares objects based on the value on an {@link org.axonframework.common.Priority @Priority}
 * annotation.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
public class PriorityAnnotationComparator<T> implements Comparator<T> {

    private static final PriorityAnnotationComparator INSTANCE = new PriorityAnnotationComparator();

    @SuppressWarnings("unchecked")
    public static <T> PriorityAnnotationComparator<T> getInstance() {
        return INSTANCE;
    }

    private PriorityAnnotationComparator() {
    }

    @Override
    public int compare(T o1, T o2) {
        Priority annotation1 = o1.getClass().getAnnotation(Priority.class);
        Priority annotation2 = o2.getClass().getAnnotation(Priority.class);
        int prio1 = annotation1 == null ? Priority.NEUTRAL : annotation1.value();
        int prio2 = annotation2 == null ? Priority.NEUTRAL : annotation2.value();

        return (prio1 > prio2) ? -1 : ((prio2 == prio1) ? 0 : 1);
    }
}
