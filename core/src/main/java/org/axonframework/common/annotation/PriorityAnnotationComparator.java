/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
