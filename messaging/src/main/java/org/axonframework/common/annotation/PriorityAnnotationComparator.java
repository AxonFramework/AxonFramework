/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.annotation;

import org.axonframework.common.Priority;

import java.lang.reflect.AnnotatedElement;
import java.util.Comparator;

/**
 * Comparator that compares objects based on the value on an {@link org.axonframework.common.Priority @Priority}
 * annotation.
 *
 * @param <T> The type of object to compare
 * @author Allard Buijze
 * @since 2.1.2
 */
public class PriorityAnnotationComparator<T> implements Comparator<T> {

    private static final PriorityAnnotationComparator INSTANCE = new PriorityAnnotationComparator();

    /**
     * Returns the instance of the comparator.
     *
     * @param <T> The type of values to compare
     * @return a comparator comparing objects based on their @Priority annotations
     */
    @SuppressWarnings("unchecked")
    public static <T> PriorityAnnotationComparator<T> getInstance() {
        return INSTANCE;
    }

    private PriorityAnnotationComparator() {
    }

    @Override
    public int compare(T o1, T o2) {
        return Integer.compare(getPriority(o2.getClass()), getPriority(o1.getClass()));
    }

    private int getPriority(AnnotatedElement annotatedElement) {
        return AnnotationUtils.findAnnotationAttributes(annotatedElement, Priority.class)
                              .map(m -> (int) m.get("priority"))
                              .orElse(Priority.NEUTRAL);
    }
}
