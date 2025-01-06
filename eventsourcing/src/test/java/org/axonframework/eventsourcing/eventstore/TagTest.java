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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.messaging.Context;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link Tag}.
 *
 * @author Steven van Beelen
 */
class TagTest {

    private static final String TEST_KEY = "key";
    private static final String TEST_VALUE = "value";

    @Test
    void containsExpectedData() {
        Tag testSubject = new Tag(TEST_KEY, TEST_VALUE);

        assertEquals(TEST_KEY, testSubject.key());
        assertEquals(TEST_VALUE, testSubject.value());
    }

    @Test
    void identicalTagsAreEqual() {
        Tag testSubject = new Tag(TEST_KEY, TEST_VALUE);

        assertEquals(testSubject, testSubject);
    }

    @Test
    void assertsEventAndTagsAreNonNull() {
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> new Tag(null, TEST_VALUE));
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> new Tag(TEST_KEY, null));
    }

    @Test
    void addToContextAddsTheGivenTagsToTheGivenContext() {
        Context testContext = Context.empty();
        Set<Tag> testTags = Set.of(new Tag(TEST_KEY, TEST_VALUE));

        testContext = Tag.addToContext(testContext, testTags);

        assertTrue(testContext.containsResource(Tag.RESOURCE_KEY));
    }

    @Test
    void fromContextReturnsAnEmptyOptionalWhenNoTagsArePresent() {
        Context testContext = Context.empty();

        Optional<Set<Tag>> result = Tag.fromContext(testContext);

        assertTrue(result.isEmpty());
    }

    @Test
    void fromContextReturnsAnOptionalWithTheContainedTags() {
        Context testContext = Context.empty();
        Set<Tag> testTags = Set.of(new Tag(TEST_KEY, TEST_VALUE));

        testContext = Tag.addToContext(testContext, testTags);

        Optional<Set<Tag>> result = Tag.fromContext(testContext);

        assertFalse(result.isEmpty());
        assertEquals(testTags, result.get());
    }
}
