/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.util;

import java.util.LinkedList;
import java.util.List;

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
     * Returns a list of objects of <code>expectedType</code> contained in the given <code>collection</code>. Any
     * objects in the collection that are not assignable to the given <code>expectedType</code> are filtered out.
     * <p/>
     * The order of the items in the list is the same as the order they were provided by the collection. The given
     * <code>collection</code> remains unchanged by this method.
     * <p/>
     * If the given collection is null, en empty list is returned.
     *
     * @param collection   An iterable (e.g. Collection) containing the unfiltered items.
     * @param expectedType The type items in the returned List must be assignable to.
     * @param <T>          The type items in the returned List must be assignable to.
     * @return a list of objects of <code>expectedType</code>. May be empty, but never <code>null</code>.
     */

    public static <T> List<T> filterByType(Iterable<?> collection, Class<T> expectedType) {
        List<T> filtered = new LinkedList<T>();
        if (collection != null) {
            for (Object item : collection) {
                if (item != null && expectedType.isInstance(item)) {
                    filtered.add(expectedType.cast(item));
                }
            }
        }
        return filtered;
    }

}
