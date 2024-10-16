/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.Context.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating implementation of the {@link Context}.
 *
 * @param <C> The {@link Context} implementation under test.
 * @author Steven van Beelen
 */
public abstract class ContextTestSuite<C extends Context> {

    private static final String EXPECTED_RESOURCE_VALUE = "testContext";
    private static final ResourceKey<String> TEST_RESOURCE_KEY = ResourceKey.create(EXPECTED_RESOURCE_VALUE);

    /**
     * Build a test subject of type {@code C} for this suite.
     *
     * @return A test subject of type {@code C} for this suite.
     */
    public abstract C testSubject();

    @Test
    void containsResourceReturnsAsExpected() {
        C testSubject = testSubject();

        assertFalse(testSubject.containsResource(TEST_RESOURCE_KEY));

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));
    }

    @Test
    void getResourceReturnsAsExpected() {
        C testSubject = testSubject();

        assertNull(testSubject.getResource(TEST_RESOURCE_KEY));

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertEquals(EXPECTED_RESOURCE_VALUE, testSubject.getResource(TEST_RESOURCE_KEY));
    }

    @Test
    void putResourceAddsResources() {
        C testSubject = testSubject();

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));
        assertEquals(EXPECTED_RESOURCE_VALUE, testSubject.getResource(TEST_RESOURCE_KEY));
    }

    @Test
    void updateResourceInsertsResourcesForNonExistingResource() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        C testSubject = testSubject();

        assertFalse(testSubject.containsResource(TEST_RESOURCE_KEY));

        String result = testSubject.updateResource(TEST_RESOURCE_KEY, resource -> {
            invoked.set(true);
            return EXPECTED_RESOURCE_VALUE;
        });

        assertEquals(EXPECTED_RESOURCE_VALUE, result);
        assertTrue(invoked.get());
    }

    @Test
    void updateResourceUpdatesResources() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        C testSubject = testSubject();
        testSubject.putResource(TEST_RESOURCE_KEY, "previousResource");

        String result = testSubject.updateResource(TEST_RESOURCE_KEY, resource -> {
            invoked.set(true);
            return EXPECTED_RESOURCE_VALUE;
        });

        assertNotEquals("previousResource", result);
        assertEquals(EXPECTED_RESOURCE_VALUE, result);
        assertTrue(invoked.get());
    }

    @Test
    void updateResourceRemovesResourcesWhenMappingResultsInNull() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        C testSubject = testSubject();
        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        String result = testSubject.updateResource(TEST_RESOURCE_KEY, resource -> {
            invoked.set(true);
            return null;
        });

        assertNull(result);
        assertNull(testSubject.getResource(TEST_RESOURCE_KEY));
        assertTrue(invoked.get());
    }

    @Test
    void putResourceIfAbsentDoesNothingWhenResourceAlreadyExists() {
        String unusedResourceValue = "someOtherValue";

        C testSubject = testSubject();

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);
        testSubject.putResourceIfAbsent(TEST_RESOURCE_KEY, unusedResourceValue);

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));
        assertNotEquals(unusedResourceValue, testSubject.getResource(TEST_RESOURCE_KEY));
        assertEquals(EXPECTED_RESOURCE_VALUE, testSubject.getResource(TEST_RESOURCE_KEY));
    }

    @Test
    void putResourceIfAbsentAddsResource() {
        C testSubject = testSubject();

        assertFalse(testSubject.containsResource(TEST_RESOURCE_KEY));

        testSubject.putResourceIfAbsent(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));
        assertEquals(EXPECTED_RESOURCE_VALUE, testSubject.getResource(TEST_RESOURCE_KEY));
    }

    @Test
    void computeResourceIfAbsentDoesNothingWhenResourceAlreadyExists() {
        String unusedResourceValue = "someOtherValue";
        AtomicBoolean invoked = new AtomicBoolean(false);

        C testSubject = testSubject();

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        testSubject.computeResourceIfAbsent(TEST_RESOURCE_KEY, () -> {
            invoked.set(true);
            return unusedResourceValue;
        });

        assertFalse(invoked.get());
        assertNotEquals(unusedResourceValue, testSubject.getResource(TEST_RESOURCE_KEY));
        assertEquals(EXPECTED_RESOURCE_VALUE, testSubject.getResource(TEST_RESOURCE_KEY));
    }

    @Test
    void computeResourceIfAbsentAddsResource() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        C testSubject = testSubject();

        assertFalse(testSubject.containsResource(TEST_RESOURCE_KEY));

        testSubject.computeResourceIfAbsent(TEST_RESOURCE_KEY, () -> {
            invoked.set(true);
            return EXPECTED_RESOURCE_VALUE;
        });

        assertTrue(invoked.get());
        assertEquals(EXPECTED_RESOURCE_VALUE, testSubject.getResource(TEST_RESOURCE_KEY));
    }

    @Test
    void removeResourceRemovesResources() {
        C testSubject = testSubject();

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));

        testSubject.removeResource(TEST_RESOURCE_KEY);

        assertFalse(testSubject.containsResource(TEST_RESOURCE_KEY));
    }

    @Test
    void removeMatchingValueResourceRemovesMatchingResource() {
        C testSubject = testSubject();

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));

        testSubject.removeResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertFalse(testSubject.containsResource(TEST_RESOURCE_KEY));
    }

    @Test
    void removeMatchingValueResourceRemovesNothingWhenNoResourceMatches() {
        C testSubject = testSubject();

        testSubject.putResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));

        testSubject.removeResource(TEST_RESOURCE_KEY, "noneMatchingValue");

        assertTrue(testSubject.containsResource(TEST_RESOURCE_KEY));
    }
}