/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

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
     * @param collection1   the first collection
     * @param collection2   the second collection
     * @param factoryMethod function to initialize the new collection
     * @param <S>           the type of elements in the collections
     * @param <T>           the type of collection
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

    /**
     * Returns a Collection instance that contains the elements of the given {@code potentialCollection}. If not a
     * Collection-like structure, it will return a Collection with the given {@code potentialCollection} as the sole
     * element.
     * <p>
     * The following structures are recognized and will have their elements placed inside a Collection:
     * <ul>
     * <li>{@link Stream}</li>
     * <li>{@link Collection}</li>
     * <li>Array</li>
     * <li>{@link Spliterator}</li>
     * <li>{@link Iterable}</li>
     * </ul>
     *
     * @param potentialCollection The instance to collect all elements from
     * @param <R>                 The type of instance contained in the resulting Collection
     * @return A Collection of the elements contained in the given {@code potentialCollection}
     */
    @SuppressWarnings("unchecked")
    public static <R> Collection<R> asCollection(Object potentialCollection) {
        if (potentialCollection == null) {
            return Collections.emptyList();
        }
        if (potentialCollection instanceof Collection) {
            return ((Collection<R>) potentialCollection);
        }
        if (potentialCollection.getClass().isArray()) {
            return Arrays.asList((R[]) potentialCollection);
        }
        if (potentialCollection instanceof Stream) {
            return ((Stream<R>) potentialCollection).collect(toList());
        }
        if (potentialCollection instanceof Spliterator) {
            return StreamSupport.stream((Spliterator<R>) potentialCollection, false).collect(toList());
        }
        if (potentialCollection instanceof Iterable) {
            return StreamSupport.stream(((Iterable<R>) potentialCollection).spliterator(), false).collect(toList());
        }
        return Collections.singletonList((R) potentialCollection);
    }

    /**
     * Returns a collection containing the elements that are in both given collections {@code collection1} and
     * {@code collection2}, using the given {@code collectionBuilder} to create an instance for the new collection. The
     * items are added to the resulting collection in the order as found in collection2.
     *
     * @param collection1       The first collection
     * @param collection2       The second collection
     * @param collectionBuilder The factory for the returned collection instance
     * @param <T>               The type of element contained in the resulting collection
     * @param <C>               The type of collection to return
     * @return a collection containing the elements that were found in both given collections
     */
    public static <T, C extends Collection<T>> C intersect(Collection<? extends T> collection1, Collection<? extends T> collection2, Supplier<C> collectionBuilder) {
        C result = collectionBuilder.get();
        HashSet<T> items = new HashSet<>(collection2);
        for (T next : collection1) {
            if (!items.add(next)) {
                result.add(next);
            }
        }
        return result;
    }

    /**
     * Returns a new Map with the entries from given {@code existingMap} as well as an entry with given {@code newKey}
     * and given {@code newValue}.
     *
     * @param existingMap A map containing the base elements to copy.
     * @param newKey      The key of the new element to add.
     * @param newValue    The value of the new element to add.
     * @param <K>         The type of key
     * @param <V>         The type of value
     * @return a new Map instance containing the elements in the base map and the new key/value pair
     */
    public static <K, V> Map<K, V> mapWith(Map<K, V> existingMap, K newKey, V newValue) {
        HashMap<K, V> newMap = new HashMap<>(existingMap);
        newMap.put(newKey, newValue);
        return newMap;
    }
}

