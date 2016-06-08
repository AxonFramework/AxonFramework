/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.common;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Utility methods for operations on collections.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class CollectionUtils {

    private CollectionUtils() {
        // prevent instantiation
    }

    /**
     * Finds an annotation of given <code>annotationType</code> from the given <code>annotations</code>. If
     * <code>annotations</code> contains multiple annotations of the given type, the first one is returned. If none
     * is found, this method returns <code>null</code>.
     *
     * @param annotations    The annotations to search in
     * @param annotationType The type of annotation to search for
     * @param <T>            The type of annotation to search for
     * @return the first annotation found, or <code>null</code> if no such annotation is present
     */
    @SuppressWarnings({"unchecked"})
    @Deprecated
    public static <T> T getAnnotation(Annotation[] annotations, Class<T> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationType)) {
                return (T) annotation;
            }
        }
        return null;
    }

    /**
     * Returns the first non-null value in the given {@code values}
     *
     * @param value1 The first value to inspect
     * @param value2 The second value to inspect
     * @param <T>    The type of values
     * @return The first non-null value, or {@code null} if all values are {@code null}.
     */
    public static <T> T firstNonNull(T value1, T value2) {
        return value1 == null ? value2 : value1;
    }

    public static <S, T extends Collection<S>> T merge(T collection1, T collection2, Supplier<T> factoryMethod) {
        if (collection1 == null || collection1.isEmpty()) {
            return collection2;
        }
        if (collection2 == null || collection2.isEmpty()) {
            return collection1;
        }
        T combined = factoryMethod.get();
        combined.addAll(collection1);
        combined.addAll(collection2);
        return combined;
    }
}
