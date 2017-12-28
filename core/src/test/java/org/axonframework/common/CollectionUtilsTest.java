/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Spliterator;
import java.util.Spliterators;

import static org.junit.Assert.assertEquals;

public class CollectionUtilsTest {

    @Test
    public void testCreateCollectionFromPotentialCollection() {
        Collection<String> collectionFromElement = CollectionUtils.asCollection("item");
        Collection<String> collectionFromNull = CollectionUtils.asCollection(null);
        Collection<String> collectionFromArray = CollectionUtils.asCollection(new String[]{"item1", "item2"});
        Collection<String> collectionFromCollection = CollectionUtils.asCollection(Arrays.asList("item1", "item2"));
        Collection<String> collectionFromSpliterator = CollectionUtils.asCollection(Spliterators.spliterator(new String[] {"item1", "item2"}, Spliterator.ORDERED));
        Collection<String> collectionFromIterable = CollectionUtils.asCollection((Iterable) () -> Arrays.asList("item1", "item2").iterator());

        assertEquals(1, collectionFromElement.size());
        assertEquals(0, collectionFromNull.size());
        assertEquals(2, collectionFromArray.size());
        assertEquals(2, collectionFromCollection.size());
        assertEquals(2, collectionFromSpliterator.size());
        assertEquals(2, collectionFromIterable.size());
    }
}
