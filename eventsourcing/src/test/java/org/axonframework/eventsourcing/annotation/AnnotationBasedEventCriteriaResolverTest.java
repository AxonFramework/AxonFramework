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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationBasedEventCriteriaResolverTest {

    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @Test
    void resolvedRightBuilderMethodForDifferentIdTypes() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(FunctionalEventSourcedEntity.class,
                                                                  Object.class,
                                                                  messageTypeResolver);

        var criteriaString = resolver.resolve("id");
        assertEquals(EventCriteria.havingTags("aggregateIdentifierOfString", "id"), criteriaString);

        var criteriaLong = resolver.resolve(1L);
        assertEquals(EventCriteria.havingTags("aggregateIdentifierOfLong", "1"), criteriaLong);
    }

    @Test
    void throwsWhenEventCriteriaBuilderReturnsNull() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(FunctionalEventSourcedEntity.class,
                                                                  Object.class,
                                                                  messageTypeResolver);

        var exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(1)
        );
        assertEquals(
                "The @EventCriteriaBuilder method returned null. The method must return a non-null EventCriteria. Violating method: buildCriteria(java.lang.Integer)",
                exception.getMessage()
        );
    }

    @Test
    void usesTagKeyPropertyIfNoEventCriteriaBuilderMatches() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(FunctionalEventSourcedEntity.class,
                                                                  Object.class,
                                                                  messageTypeResolver);

        var criteria = resolver.resolve(0.0);
        assertEquals(EventCriteria.havingTags("fallbackTagKey", "0.0"), criteria);
    }

    @EventSourcedEntity(tagKey = "fallbackTagKey")
    static class FunctionalEventSourcedEntity {

        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(String id) {
            return EventCriteria.havingTags("aggregateIdentifierOfString", id);
        }

        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(Long id) {
            return EventCriteria.havingTags("aggregateIdentifierOfLong", id.toString());
        }

        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(Integer id) {
            return null;
        }
    }

    @Test
    void usesSimpleClassNameAsTagNameIfNoTagKeyPropertyIsSet() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(DefaultEventSourcedEntity.class,
                                                                  Object.class,
                                                                  messageTypeResolver);

        var criteria = resolver.resolve("id");
        assertEquals(EventCriteria.havingTags("DefaultEventSourcedEntity", "id"), criteria);
    }

    @EventSourcedEntity()
    static class DefaultEventSourcedEntity {
    }

    @Nested
    class ConfigurationProblems {

        @Test
        void eventCriteriaBuilderWithNonStaticBuilderThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver<>(EntityWithNonStaticEventCriteriaBuilder.class,
                                                                   Object.class,
                                                                   messageTypeResolver));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must be static. Violating method: buildCriteria(java.lang.String)",
                    exception.getMessage()
            );
        }

        @EventSourcedEntity
        static class EntityWithNonStaticEventCriteriaBuilder {

            @EventCriteriaBuilder
            public EventCriteria buildCriteria(String id) {
                return EventCriteria.havingTags("aggregateIdentifier", id);
            }
        }

        @Test
        void eventCriteriaBuilderWithZeroArgBuilderThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver<>(EntityWithZeroArgEventCriteriaBuilder.class,
                                                                     Object.class,
                                                                     messageTypeResolver));
            assertEquals(
                    "No ID type found in @EventCriteriaBuilder method. Offending method: buildCriteria()",
                    exception.getMessage()
            );
        }


        @EventSourcedEntity
        static class EntityWithZeroArgEventCriteriaBuilder {

            @EventCriteriaBuilder
            public static EventCriteria buildCriteria() {
                return EventCriteria.havingAnyTag();
            }
        }

        @Test
        void eventCriteriaBuilderWithVoidReturnValueThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver<>(EntityWithVoidReturnValue.class,
                                                                     Object.class,
                                                                     messageTypeResolver));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must return an EventCriteria. Violating method: buildCriteria(java.lang.String)",
                    exception.getMessage()
            );
        }


        @EventSourcedEntity
        class EntityWithPrivateEventCriteriaBuilder {

            @EventCriteriaBuilder
            private static EventCriteria buildCriteria(String id) {
                return EventCriteria.havingTags("aggregateIdentifier", id);
            }
        }

        @Test
        void eventCriteriaBuilderWithPrivateBuilderWorks() {
            var resolver = new AnnotationBasedEventCriteriaResolver<>(EntityWithPrivateEventCriteriaBuilder.class,
                                                                      Object.class,
                                                                      messageTypeResolver);
            var criteria = resolver.resolve("id");
            assertEquals(EventCriteria.havingTags("aggregateIdentifier", "id"), criteria);
        }


        @EventSourcedEntity
        class EntityWithVoidReturnValue {

            @EventCriteriaBuilder
            public static void buildCriteria(String id) {
            }
        }

        @Test
        void eventCriteriaBuilderWithNonEventSourcedEntityThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver<>(NonEventSourcedEntity.class,
                                                                     Object.class,
                                                                     messageTypeResolver));
            assertEquals(
                    "The given class is not an @EventSourcedEntity",
                    exception.getMessage()
            );
        }

        static class NonEventSourcedEntity {

        }

        @Test
        void eventCriteriaBuilderWithDuplicatedParamterTypeThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver<>(EntityWithDuplicatedParameterType.class,
                                                                     Object.class,
                                                                     messageTypeResolver));
            assertEquals(
                    "Multiple @EventCriteriaBuilder methods found with the same parameter type: buildCriteriaOne(java.lang.String), buildCriteriaTwo(java.lang.String)",
                    exception.getMessage()
            );
        }


        @EventSourcedEntity
        static class EntityWithDuplicatedParameterType {

            @EventCriteriaBuilder
            public static EventCriteria buildCriteriaOne(String id) {
                return EventCriteria.havingAnyTag();
            }

            @EventCriteriaBuilder
            public static EventCriteria buildCriteriaTwo(String id) {
                return EventCriteria.havingAnyTag();
            }
        }

        @Test
        void canInjectMessageTypeResolver() {
            var resolver = new AnnotationBasedEventCriteriaResolver<>(
                    EntityWithInjectedMessageTypeResolver.class,
                    Object.class,
                    (clazz) -> new MessageType(null, "MyMessageType", "0.0.5")
            );

            var criteria = resolver.resolve("id");
            assertEquals(EventCriteria.havingTags("aggregateIdentifier", "id")
                                 .andBeingOneOfTypes("MyMessageType"), criteria);
        }

        @EventSourcedEntity
        static class EntityWithInjectedMessageTypeResolver {

            @EventCriteriaBuilder
            public static EventCriteria buildCriteria(String id, MessageTypeResolver messageTypeResolver) {
                return EventCriteria.havingTags("aggregateIdentifier", id)
                                    .andBeingOneOfTypes(messageTypeResolver, Integer.class);
            }
        }
    }
}