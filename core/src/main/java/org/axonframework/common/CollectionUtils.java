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
     * Merge two collections into a new collection instance. The new collection is created using the given {@code
     * factoryMethod}.
     * <p>
     * If any of the two inputs collections is {@code null} or empty the other input collection is returned (even if
     * that one is {@code null} as well).
     *
     * @param collection1 the first collection
     * @param collection2 the second collection
     * @param factoryMethod function to initialize the new collection
     * @param <S> the type of elements in the collections
     * @param <T> the type of collection
     * @return a collection that combines both collections
     */
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
