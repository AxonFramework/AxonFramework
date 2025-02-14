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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.annotations.EventTag;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationBasedTagResolverTest {

    private AnnotationBasedTagResolver testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationBasedTagResolver();
    }

    @Nested
    @DisplayName("Record Tests")
    class RecordTests {

        record TestRecord(
                @EventTag String id,
                @EventTag(key = "customKey") String value,
                String nonTagged
        ) {

        }

        record NullableRecord(
                @EventTag String id,
                @EventTag String nullValue
        ) {

        }

        @Test
        @DisplayName("Should resolve tags from record components")
        void shouldResolveTagsFromRecord() {
            // given
            TestRecord payload = new TestRecord("123", "test", "ignored");
            EventMessage<?> event = createEventMessage(payload);

            // when
            Set<Tag> result = testSubject.resolve(event);

            // then
            assertEquals(2, result.size());
            assertTrue(result.contains(new Tag("id", "123")));
            assertTrue(result.contains(new Tag("customKey", "test")));
        }

        @Test
        @DisplayName("Should handle null values in record components")
        void shouldHandleNullValuesInRecord() {
            // given
            NullableRecord payload = new NullableRecord("123", null);
            EventMessage<?> event = createEventMessage(payload);

            // when
            Set<Tag> result = testSubject.resolve(event);

            // then
            assertEquals(1, result.size());
            assertTrue(result.contains(new Tag("id", "123")));
        }
    }

    @Nested
    @DisplayName("Class Tests")
    class ClassTests {

        class TestClass {

            @EventTag
            private final String id;

            @EventTag(key = "customField")
            private final String value;

            private final String nonTagged;

            TestClass(String id, String value, String nonTagged) {
                this.id = id;
                this.value = value;
                this.nonTagged = nonTagged;
            }

            @EventTag
            public String getTaggedMethod() {
                return "methodValue";
            }

            @EventTag(key = "customMethod")
            public String getCustomTaggedMethod() {
                return "customMethodValue";
            }
        }

        @Test
        @DisplayName("Should resolve tags from fields and methods")
        void shouldResolveTagsFromFieldsAndMethods() {
            // given
            TestClass payload = new TestClass("123", "test", "ignored");
            EventMessage<?> event = createEventMessage(payload);

            // when
            Set<Tag> result = testSubject.resolve(event);

            // then
            assertEquals(4, result.size());
            assertTrue(result.contains(new Tag("id", "123")));
            assertTrue(result.contains(new Tag("customField", "test")));
            assertTrue(result.contains(new Tag("taggedMethod", "methodValue")));
            assertTrue(result.contains(new Tag("customMethod", "customMethodValue")));
        }
    }

    @Nested
    @DisplayName("Error Cases")
    class ErrorCases {

        @Test
        @DisplayName("Should handle null event")
        void shouldThrowExceptionOnNullEvent() {
            // when/then
            assertThrows(IllegalArgumentException.class, () -> testSubject.resolve(null));
        }

        @Test
        @DisplayName("Should handle null payload")
        void shouldHandleNullPayload() {
            // given
            EventMessage<?> event = createEventMessage(null);

            // when
            Set<Tag> result = testSubject.resolve(event);

            // then
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Complex Cases")
    class ComplexCases {

        record ComplexRecord(
                @EventTag String id,
                @EventTag(key = "items") Map<String, Integer> items,
                @EventTag(key = "count") Integer count
        ) {

        }

        @Test
        @DisplayName("Should handle complex types")
        void shouldHandleComplexTypes() {
            // given
            Map<String, Integer> items = Map.of("item1", 1, "item2", 2);
            ComplexRecord payload = new ComplexRecord("123", items, 42);
            EventMessage<?> event = createEventMessage(payload);

            // when
            Set<Tag> result = testSubject.resolve(event);

            // then
            assertEquals(3, result.size());
            assertTrue(result.contains(new Tag("id", "123")));
            assertTrue(result.contains(new Tag("items", items.toString())));
            assertTrue(result.contains(new Tag("count", "42")));
        }
    }

    private EventMessage<?> createEventMessage(Object payload) {
        return new GenericEventMessage<>(new MessageType("event"), payload);
    }
}