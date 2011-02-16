/*
 * Copyright (c) 2011. Axon Framework
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

package org.axonframework.saga.repository;

import org.axonframework.saga.AssociationValue;
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
        testSubject.add(av(anObject), "1");
        testSubject.add(av(anObject), "1");
        assertEquals("Wrong count after adding an object twice", 1, testSubject.size());
        testSubject.add(av(new Object()), "1");
        assertEquals("Wrong count after adding two objects", 2, testSubject.size());
        testSubject.add(av(new FixedHashAndToString(1, "a")), "1");
        testSubject.add(av(new FixedHashAndToString(1, "a")), "1");
        assertEquals("Wrong count after adding equal objects", 3, testSubject.size());
        testSubject.add(av(new FixedHashAndToString(1, "b")), "1");
        assertEquals("Wrong count after adding two FixedHash instances", 4, testSubject.size());
        testSubject.add(av(new FixedHashAndToString(2, "a")), "1");
        testSubject.add(av(new FixedHashAndToString(3, "a")), "1");
        assertEquals("Wrong count after adding two FixedHash instances", 6, testSubject.size());
        testSubject.add(av("a"), "1");
        testSubject.add(av("a"), "1");
        assertEquals("Wrong count after adding two identical Strings", 7, testSubject.size());
        testSubject.add(av("b"), "1");
        assertEquals("Wrong count after adding two identical Strings", 8, testSubject.size());

        testSubject.add(av("a"), "2");
        assertEquals("Wrong count after adding two identical Strings for different saga", 9, testSubject.size());
        assertEquals(2, testSubject.findSagas(av("a")).size());
    }

    @Test
    public void testRemoveItems() {
        testStoreVarietyOfItems();
        assertEquals("Wrong initial item count", 9, testSubject.size());
        testSubject.remove(av("a"), "1");
        assertEquals("Wrong item count", 8, testSubject.size());
        testSubject.remove(av("a"), "2");
        assertEquals("Wrong item count", 7, testSubject.size());
        testSubject.remove(av(new FixedHashAndToString(1, "a")), "1");
        assertEquals("Wrong item count", 6, testSubject.size());

        testSubject.clear();
        assertTrue(testSubject.isEmpty());
        assertEquals("Wrong item count", 0, testSubject.size());
    }

    private AssociationValue av(Object value) {
        return new AssociationValue("key", value);
    }

    @Test
    public void testFindAssociations() {
        List<AssociationValue> usedAssociations = new ArrayList<AssociationValue>(1000);
        for (int t = 0; t < 1000; t++) {
            String key = UUID.randomUUID().toString();
            for (int i = 0; i < 10; i++) {
                AssociationValue associationValue = new AssociationValue(key, UUID.randomUUID().toString());
                if (usedAssociations.size() < 1000) {
                    usedAssociations.add(associationValue);
                }
                testSubject.add(associationValue, key);
            }
        }

        assertEquals(10000, testSubject.size());
        for (AssociationValue item : usedAssociations) {
            Set<String> actualResult = testSubject.findSagas(item);
            assertEquals("Failure on item: " + usedAssociations.indexOf(item), 1, actualResult.size());
            assertEquals(item.getKey(), actualResult.iterator().next());
        }
    }

    private static class FixedHashAndToString {

        private final int hash;
        private final String toString;

        private FixedHashAndToString(int hash, String toString) {
            this.hash = hash;
            this.toString = toString;
        }

        @Override
        public String toString() {
            return toString;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
