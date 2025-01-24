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

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
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

    private static final GenericEventMessage<TestPayload> TEST_EVENT =
            new GenericEventMessage<>(new MessageType("test", "event", "0.0.1"), TestPayload.INSTANCE);

    @Test
    void resolveSetsExpectedTags() {
        Set<Tag> expectedTags = Set.of(new Tag("id", TestPayload.INSTANCE.identifier),
                                       new Tag("otherId", TestPayload.INSTANCE.otherIdentifier));

        PayloadBasedTagResolver<TestPayload> testSubject =
                PayloadBasedTagResolver.forPayloadType(TestPayload.class)
                                       .withResolver(event -> new Tag("id", event.identifier))
                                       .withResolver(event -> "otherId", TestPayload::otherIdentifier);

        Set<Tag> result = testSubject.resolve(TEST_EVENT);

        assertEquals(2, result.size());
        assertEquals(expectedTags, result);
    }

    @Test
    void emptyTagSetWhenNoTagResolversAreGiven() {
        PayloadBasedTagResolver<TestPayload> testSubject = PayloadBasedTagResolver.forPayloadType(TestPayload.class);

        Set<Tag> result = testSubject.resolve(TEST_EVENT);

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyTagSetWhenResolvingForAnotherPayload() {
        PayloadBasedTagResolver<String> testSubject =
                PayloadBasedTagResolver.forPayloadType(String.class)
                                       .withResolver(event -> new Tag("id", event));

        Set<Tag> result = testSubject.resolve(TEST_EVENT);

        assertTrue(result.isEmpty());
    }

    @Test
    void noDuplicateTagsForDuplicatedTagResolver() {
        Function<TestPayload, Tag> tagResolver = event -> new Tag("id", event.identifier);
        PayloadBasedTagResolver<TestPayload> testSubject = PayloadBasedTagResolver.forPayloadType(TestPayload.class)
                                                                                  .withResolver(tagResolver)
                                                                                  .withResolver(tagResolver);

        Set<Tag> result = testSubject.resolve(TEST_EVENT);

        assertEquals(1, result.size());
    }

    @Test
    void throwsNullPointerExceptionForNullTagResolver() {
        PayloadBasedTagResolver<TestPayload> testSubject = PayloadBasedTagResolver.forPayloadType(TestPayload.class);
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.withResolver(null));
    }

    @Test
    void throwsNullPointerExceptionForNullKeyResolver() {
        PayloadBasedTagResolver<TestPayload> testSubject = PayloadBasedTagResolver.forPayloadType(TestPayload.class);
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.withResolver(null, TestPayload::identifier));
    }

    @Test
    void throwsNullPointerExceptionForNullValueResolver() {
        PayloadBasedTagResolver<TestPayload> testSubject = PayloadBasedTagResolver.forPayloadType(TestPayload.class);
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.withResolver(TestPayload::identifier, null));
    }

    record TestPayload(String identifier,
                       String otherIdentifier) {

        static final TestPayload INSTANCE = new TestPayload(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
}