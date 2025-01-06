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

import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test class validating the {@link PayloadBasedTagResolver}
 *
 * @author Steven van Beelen
 */
class PayloadBasedTagResolverTest {

    @Test
    void resolveSetsExpectedTags() {
        Set<Tag> expectedTags = Set.of(new Tag("id", TestEvent.INSTANCE.identifier),
                                       new Tag("otherId", TestEvent.INSTANCE.otherIdentifier));

        PayloadBasedTagResolver<TestEvent> testSubject =
                PayloadBasedTagResolver.forEvent(TestEvent.class)
                                       .withResolver(event -> new Tag("id", event.identifier))
                                       .withResolver(event -> "otherId", TestEvent::otherIdentifier);

        Set<Tag> result = testSubject.resolve(TestEvent.INSTANCE);

        assertEquals(2, result.size());
        assertEquals(expectedTags, result);
    }

    @Test
    void emptyTagSetWhenNoTagResolversAreGiven() {
        PayloadBasedTagResolver<TestEvent> testSubject = PayloadBasedTagResolver.forEvent(TestEvent.class);

        Set<Tag> result = testSubject.resolve(TestEvent.INSTANCE);

        assertTrue(result.isEmpty());
    }

    @Test
    void noDuplicateTagsForDuplicatedTagResolver() {
        Function<TestEvent, Tag> tagResolver = event -> new Tag("id", event.identifier);
        PayloadBasedTagResolver<TestEvent> testSubject = PayloadBasedTagResolver.forEvent(TestEvent.class)
                                                                                .withResolver(tagResolver)
                                                                                .withResolver(tagResolver);

        Set<Tag> result = testSubject.resolve(TestEvent.INSTANCE);

        assertEquals(1, result.size());
    }

    @Test
    void throwsAxonConfigurationExceptionForNullTagResolver() {
        PayloadBasedTagResolver<TestEvent> testSubject = PayloadBasedTagResolver.forEvent(TestEvent.class);
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> testSubject.withResolver(null));
    }

    @Test
    void throwsAxonConfigurationExceptionForNullKeyResolver() {
        PayloadBasedTagResolver<TestEvent> testSubject = PayloadBasedTagResolver.forEvent(TestEvent.class);
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> testSubject.withResolver(null, TestEvent::identifier));
    }

    @Test
    void throwsAxonConfigurationExceptionForNullValueResolver() {
        PayloadBasedTagResolver<TestEvent> testSubject = PayloadBasedTagResolver.forEvent(TestEvent.class);
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> testSubject.withResolver(TestEvent::identifier, null));
    }

    record TestEvent(String identifier,
                     String otherIdentifier) {

        static final TestEvent INSTANCE = new TestEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
}