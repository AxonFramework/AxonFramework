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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating implementations of the {@link Context}.
 *
 * @param <C> The {@link Context} implementation under test.
 * @author Steven van Beelen
 */
public abstract class ContextTestSuite<C extends Context> {

    protected static final String EXPECTED_RESOURCE_VALUE = "testContext";
    protected static final ResourceKey<String> TEST_RESOURCE_KEY = ResourceKey.create(EXPECTED_RESOURCE_VALUE);

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

        //noinspection unchecked
        C testSubjectWithResources = (C) testSubject.withResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertFalse(testSubject.containsResource(TEST_RESOURCE_KEY));
        assertTrue(testSubjectWithResources.containsResource(TEST_RESOURCE_KEY));
    }

    @Test
    void getResourceReturnsAsExpected() {
        C testSubject = testSubject();

        assertNull(testSubject.getResource(TEST_RESOURCE_KEY));

        //noinspection unchecked
        C testSubjectWithResources = (C) testSubject.withResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertNull(testSubject.getResource(TEST_RESOURCE_KEY));
        assertEquals(EXPECTED_RESOURCE_VALUE, testSubjectWithResources.getResource(TEST_RESOURCE_KEY));
    }

    @Test
    void withResourceReturnsNewContextInstanceWithTheExpectedResources() {
        String expectedResourceValueTwo = "resourceTwo";
        ResourceKey<String> testResourceKeyTwo = ResourceKey.create(expectedResourceValueTwo);

        C testSubject = testSubject();

        assertNull(testSubject.getResource(TEST_RESOURCE_KEY));

        //noinspection unchecked
        C resultOne = (C) testSubject.withResource(TEST_RESOURCE_KEY, EXPECTED_RESOURCE_VALUE);

        assertNotEquals(testSubject, resultOne);
        assertEquals(EXPECTED_RESOURCE_VALUE, resultOne.getResource(TEST_RESOURCE_KEY));

        //noinspection unchecked
        C resultTwo = (C) resultOne.withResource(testResourceKeyTwo, expectedResourceValueTwo);

        assertNotEquals(testSubject, resultTwo);
        assertNotEquals(resultOne, resultTwo);
        assertEquals(EXPECTED_RESOURCE_VALUE, resultTwo.getResource(TEST_RESOURCE_KEY));
        assertEquals(expectedResourceValueTwo, resultTwo.getResource(testResourceKeyTwo));
    }
}