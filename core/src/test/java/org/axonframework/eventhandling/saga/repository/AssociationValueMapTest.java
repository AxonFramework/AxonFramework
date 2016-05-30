/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.saga.repository;

import org.axonframework.eventhandling.saga.AssociationValue;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class AssociationValueMapTest {

    private AssociationValueMap testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AssociationValueMap();
    }

    @Test
    public void testStoreVarietyOfItems() {
        assertTrue(testSubject.isEmpty());

        Object anObject = new Object();
        testSubject.add(av("1"), "T", "1");
        testSubject.add(av("1"), "T", "1");
        assertEquals("Wrong count after adding an object twice", 1, testSubject.size());
        testSubject.add(av("2"), "T", "1");
        assertEquals("Wrong count after adding two objects", 2, testSubject.size());
        testSubject.add(av("a"), "T", "1");
        testSubject.add(av("a"), "T", "1");
        assertEquals("Wrong count after adding two identical Strings", 3, testSubject.size());
        testSubject.add(av("b"), "T", "1");
        assertEquals("Wrong count after adding two identical Strings", 4, testSubject.size());

        testSubject.add(av("a"), "T", "2");
        testSubject.add(av("a"), "Y", "2");
        assertEquals("Wrong count after adding two identical Strings for different saga", 6, testSubject.size());
        assertEquals(2, testSubject.findSagas("T", av("a")).size());
    }

    @Test
    public void testRemoveItems() {
        testStoreVarietyOfItems();
        assertEquals("Wrong initial item count", 6, testSubject.size());
        testSubject.remove(av("a"), "T", "1");
        assertEquals("Wrong item count", 5, testSubject.size());
        testSubject.remove(av("a"), "T", "2");
        assertEquals("Wrong item count", 4, testSubject.size());

        testSubject.clear();
        assertTrue(testSubject.isEmpty());
        assertEquals("Wrong item count", 0, testSubject.size());
    }

    private AssociationValue av(String value) {
        return new AssociationValue("key", value);
    }

    @Test
    public void testFindAssociations() {
        List<AssociationValue> usedAssociations = new ArrayList<>(1000);
        for (int t = 0; t < 1000; t++) {
            String key = UUID.randomUUID().toString();
            for (int i = 0; i < 10; i++) {
                AssociationValue associationValue = new AssociationValue(key, UUID.randomUUID().toString());
                if (usedAssociations.size() < 1000) {
                    usedAssociations.add(associationValue);
                }
                testSubject.add(associationValue, "type", key);
            }
        }

        assertEquals(10000, testSubject.size());
        for (AssociationValue item : usedAssociations) {
            Set<String> actualResult = testSubject.findSagas("type", item);
            assertEquals("Failure on item: " + usedAssociations.indexOf(item), 1, actualResult.size());
            assertEquals(item.getKey(), actualResult.iterator().next());
        }
    }
}
