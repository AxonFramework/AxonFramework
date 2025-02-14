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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the behaviour of the {@link AnnotationBasedTagResolver}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AnnotationBasedTagResolverTest {

    private final AnnotationBasedTagResolver tagResolver = new AnnotationBasedTagResolver();

    @Test
    void shouldThrowExceptionOnNullEvent() {
        // when/then
        assertThrows(NullPointerException.class, () -> tagResolver.resolve(null));
    }

    @Nested
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
        void shouldResolveTagsFromRecord() {
            // given
            var payload = new TestRecord("123", "test", "ignored");
            var event = anEventMessage(payload);

            // when
            var result = tagResolver.resolve(event);

            // then
            assertEquals(2, result.size());
            assertTrue(result.contains(new Tag("id", "123")));
            assertTrue(result.contains(new Tag("customKey", "test")));
        }

        @Test
        void shouldIgnoreTagsWithNullValues() {
            // given
            var payload = new NullableRecord("123", null);
            var event = anEventMessage(payload);

            // when
            var result = tagResolver.resolve(event);

            // then
            var nullValueTagNotPresent = result.stream().noneMatch(it -> it.key().equals("nullValue"));
            assertTrue(nullValueTagNotPresent);
        }
    }

    @Nested
    class ClassTests {

        static class TestClass {

            @EventTag
            private final String id;

            @EventTag(key = "customField")
            private final Integer value;

            private final String nonTagged;

            TestClass(String id, Integer value, String nonTagged) {
                this.id = id;
                this.value = value;
                this.nonTagged = nonTagged;
            }

            @EventTag(key = "taggedMethod")
            public String getTaggedMethod() {
                return "methodValue";
            }

            @EventTag(key = "customMethod")
            public String getCustomTaggedMethod() {
                return "customMethodValue";
            }
        }

        @Test
        void shouldResolveTagsFromFieldsAndMethods() {
            // given
            var payload = new TestClass("123", 456, "ignored");
            var event = anEventMessage(payload);

            // when
            var result = tagResolver.resolve(event);

            // then
            assertEquals(4, result.size());
            assertTrue(result.contains(new Tag("id", "123")));
            assertTrue(result.contains(new Tag("customField", "456")));
            assertTrue(result.contains(new Tag("taggedMethod", "methodValue")));
            assertTrue(result.contains(new Tag("customMethod", "customMethodValue")));
        }

        @Test
        void shouldNotResolveTagsFromNotAnnotatedMembers() {
            // given
            var payload = new TestClass("123", 456, "ignored");
            var event = anEventMessage(payload);

            // when
            var result = tagResolver.resolve(event);

            // then
            assertFalse(result.contains(new Tag("nonTagged", "ignored")));
        }
    }

    @Nested
    class ComplexCases {

        record ComplexRecord(
                @EventTag String id,
                @EventTag(key = "items") Map<String, Integer> items,
                @EventTag(key = "count") Integer count
        ) {

        }

        @Test
        void shouldHandleComplexTypes() {
            // given
            var items = Map.of("item1", 1, "item2", 2);
            var payload = new ComplexRecord("123", items, 42);
            var event = anEventMessage(payload);

            // when
            var result = tagResolver.resolve(event);

            // then
            assertEquals(3, result.size());
            assertTrue(result.contains(new Tag("id", "123")));
            assertTrue(result.contains(new Tag("items", items.toString())));
            assertTrue(result.contains(new Tag("count", "42")));
        }
    }

    @Nested
    class InvalidMethodTests {

        static class InvalidMethodClass {

            @EventTag
            public String methodWithParameters(String param) {
                return param;
            }
        }

        static class VoidMethodClass {

            @EventTag
            public void voidMethod() {
                // Method with void return type
            }
        }

        static class ExceptionThrowingMethodClass {

            @EventTag
            private String methodThrowingException() {
                throw new RuntimeException("Test exception");
            }
        }

        static class GetterMethodClass {

            @EventTag
            public String getName() {
                return "name";
            }

            @EventTag
            public String getidentifier() { // doesn't follow getter convention (lowercase after 'get')
                return "123";
            }

            @EventTag
            public String get() { // too short to be a getter
                return "value";
            }
        }

        @Test
        void shouldThrowExceptionForVoidMethod() {
            // given
            var payload = new VoidMethodClass();
            var event = anEventMessage(payload);

            // when/then
            var exception = assertThrows(
                    AnnotationBasedTagResolver.TagResolutionException.class,
                    () -> tagResolver.resolve(event)
            );
            assertTrue(exception.getMessage().contains("should not return void"));
        }

        @Test
        void shouldThrowExceptionForMethodWithParameters() {
            // given
            var payload = new InvalidMethodClass();
            var event = anEventMessage(payload);

            // when/then
            var exception = assertThrows(
                    AnnotationBasedTagResolver.TagResolutionException.class,
                    () -> tagResolver.resolve(event)
            );
            assertTrue(exception.getMessage().contains("should not contain any parameters"));
        }

        @Test
        void shouldWrapMethodInvocationException() {
            // given
            var payload = new ExceptionThrowingMethodClass();
            var event = anEventMessage(payload);

            // when/then
            var exception = assertThrows(
                    AnnotationBasedTagResolver.TagResolutionException.class,
                    () -> tagResolver.resolve(event)
            );
            assertTrue(exception.getMessage().contains("Failed to resolve tag from method"));
            assertInstanceOf(InvocationTargetException.class, exception.getCause());
        }

        @Test
        void shouldStripGetPartFromGetterMethods() {
            // given
            var payload = new GetterMethodClass();
            var event = anEventMessage(payload);

            // when
            var result = tagResolver.resolve(event);

            // then
            assertEquals(3, result.size());
            assertTrue(result.contains(new Tag("name", "name")));
            assertTrue(result.contains(new Tag("getidentifier", "123"))); // not a proper getter
            assertTrue(result.contains(new Tag("get", "value"))); // not a proper getter
        }
    }

    @Nested
    class InheritanceAndVisibilityTests {

        static class BaseClass {

            @EventTag
            private final String privateBaseField = "privateBaseValue";

            @EventTag(key = "customBase")
            protected String protectedBaseField = "protectedBaseValue";

            @EventTag
            private String getBaseValue() {
                return "baseMethodValue";
            }
        }

        static class InheritedTagClass extends BaseClass {

            @EventTag
            private final String privateChildField = "privateChildValue";

            @EventTag
            public String publicChildField = "publicChildValue";

            @EventTag
            public String getChildValue() {
                return "childMethodValue";
            }
        }

        @Test
        void shouldResolveAllFieldsAndMethodsRegardlessOfVisibility() {
            // given
            var payload = new InheritedTagClass();
            var event = anEventMessage(payload);

            // when
            var result = tagResolver.resolve(event);

            // then
            assertEquals(6, result.size());
            // Parent class tags
            assertTrue(result.contains(new Tag("privateBaseField", "privateBaseValue")));
            assertTrue(result.contains(new Tag("customBase", "protectedBaseValue")));
            assertTrue(result.contains(new Tag("baseValue", "baseMethodValue")));
            // Child class tags
            assertTrue(result.contains(new Tag("privateChildField", "privateChildValue")));
            assertTrue(result.contains(new Tag("publicChildField", "publicChildValue")));
            assertTrue(result.contains(new Tag("childValue", "childMethodValue")));
        }
    }

    private EventMessage<?> anEventMessage(Object payload) {
        return new GenericEventMessage<>(new MessageType("event"), payload);
    }
}