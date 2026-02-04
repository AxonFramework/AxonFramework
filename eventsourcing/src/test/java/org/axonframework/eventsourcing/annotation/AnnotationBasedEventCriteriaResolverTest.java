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

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnotationBasedEventCriteriaResolverTest {

    private static final Configuration configuration = mock(Configuration.class);
    private static final MessageTypeResolver messageTypeResolver =
            (clazz) -> Optional.of(new MessageType(null, "MyMessageType", "0.0.5"));
    private static final EventSink eventSink = mock(EventSink.class);
    private static final CommandGateway commandGateway = mock(CommandGateway.class);

    @BeforeEach
    void setUp() {
        when(configuration.getOptionalComponent(MessageTypeResolver.class)).thenReturn(
                Optional.of(messageTypeResolver)
        );
        when(configuration.getOptionalComponent(EventSink.class)).thenReturn(Optional.of(eventSink));
        when(configuration.getOptionalComponent(CommandGateway.class)).thenReturn(Optional.of(commandGateway));
    }

    @Test
    void resolvedRightBuilderMethodForDifferentIdTypes() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(FunctionalEventSourcedEntity.class,
                                                                  Object.class,
                                                                  configuration);

        var criteriaString = resolver.resolve("id", new StubProcessingContext());
        assertEquals(EventCriteria.havingTags("aggregateIdentifierOfString", "id"), criteriaString);

        var criteriaLong = resolver.resolve(1L, new StubProcessingContext());
        assertEquals(EventCriteria.havingTags("aggregateIdentifierOfLong", "1"), criteriaLong);
    }

    @Test
    void throwsWhenEventCriteriaBuilderReturnsNull() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(FunctionalEventSourcedEntity.class,
                                                                  Object.class,
                                                                  configuration);

        var exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(1, new StubProcessingContext())
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
                                                                  configuration);

        var criteria = resolver.resolve(0.0, new StubProcessingContext());
        assertEquals(EventCriteria.havingTags("fallbackTagKey", "0.0"), criteria);
    }

    @EventSourcedEntity(tagKey = "fallbackTagKey")
    static class FunctionalEventSourcedEntity {

        @SuppressWarnings("unused")
        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(String id) {
            return EventCriteria.havingTags("aggregateIdentifierOfString", id);
        }

        @SuppressWarnings("unused")
        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(Long id) {
            return EventCriteria.havingTags("aggregateIdentifierOfLong", id.toString());
        }

        @SuppressWarnings("unused")
        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(Integer id) {
            return null;
        }
    }

    @Test
    void usesSimpleClassNameAsTagNameIfNoTagKeyPropertyIsSet() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(DefaultEventSourcedEntity.class,
                                                                  Object.class,
                                                                  configuration);

        var criteria = resolver.resolve("id", new StubProcessingContext());
        assertEquals(EventCriteria.havingTags("DefaultEventSourcedEntity", "id"), criteria);
    }

    @EventSourcedEntity()
    static class DefaultEventSourcedEntity {

    }

    @Test
    void canInjectManyParameters() {
        var resolver = new AnnotationBasedEventCriteriaResolver<>(EntityWithManyInjectionParameters.class,
                                                                  Object.class,
                                                                  configuration);

        var criteria = resolver.resolve("id", new StubProcessingContext());
        assertEquals(EventCriteria.havingTags("aggregateIdentifier", "id"), criteria);
    }

    @EventSourcedEntity
    static class EntityWithManyInjectionParameters {

        @SuppressWarnings("unused")
        @EventCriteriaBuilder
        public static EventCriteria buildCriteria(String id, MessageTypeResolver messageTypeResolver,
                                                  EventSink eventSink, CommandGateway commandGateway,
                                                  Configuration configuration) {
            assertSame(AnnotationBasedEventCriteriaResolverTest.messageTypeResolver, messageTypeResolver);
            assertSame(AnnotationBasedEventCriteriaResolverTest.eventSink, eventSink);
            assertSame(AnnotationBasedEventCriteriaResolverTest.commandGateway, commandGateway);
            assertSame(AnnotationBasedEventCriteriaResolverTest.configuration, configuration);
            return EventCriteria.havingTags("aggregateIdentifier", id);
        }
    }

    @Nested
    class ConfigurationProblems {

        @Test
        void eventCriteriaBuilderWithNonStaticBuilderThrowsException() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver<>(EntityWithNonStaticEventCriteriaBuilder.class,
                                                                     Object.class,
                                                                     configuration));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must be static. Violating method: buildCriteria(java.lang.String)",
                    exception.getMessage()
            );
        }

        @EventSourcedEntity
        static class EntityWithNonStaticEventCriteriaBuilder {

            @SuppressWarnings("unused")
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
                                                                     configuration));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must have at least one parameter which is the identifier. Violating method: buildCriteria()",
                    exception.getMessage()
            );
        }


        @EventSourcedEntity
        static class EntityWithZeroArgEventCriteriaBuilder {

            @SuppressWarnings("unused")
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
                                                                     configuration));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder must return an EventCriteria. Violating method: buildCriteria(java.lang.String)",
                    exception.getMessage()
            );
        }


        @EventSourcedEntity
        static class EntityWithPrivateEventCriteriaBuilder {

            @SuppressWarnings("unused")
            @EventCriteriaBuilder
            private static EventCriteria buildCriteria(String id) {
                return EventCriteria.havingTags("aggregateIdentifier", id);
            }
        }

        @Test
        void eventCriteriaBuilderWithPrivateBuilderWorks() {
            var resolver = new AnnotationBasedEventCriteriaResolver<>(EntityWithPrivateEventCriteriaBuilder.class,
                                                                      Object.class,
                                                                      configuration);
            var criteria = resolver.resolve("id", new StubProcessingContext());
            assertEquals(EventCriteria.havingTags("aggregateIdentifier", "id"), criteria);
        }


        @EventSourcedEntity
        static class EntityWithVoidReturnValue {

            @SuppressWarnings("unused")
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
                                                                     configuration));
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
                                                                     configuration));
            assertEquals(
                    "Multiple @EventCriteriaBuilder methods found with the same parameter type: buildCriteriaOne(java.lang.String), buildCriteriaTwo(java.lang.String)",
                    exception.getMessage()
            );
        }


        @EventSourcedEntity
        static class EntityWithDuplicatedParameterType {

            @SuppressWarnings("unused")
            @EventCriteriaBuilder
            public static EventCriteria buildCriteriaOne(String id) {
                return EventCriteria.havingAnyTag();
            }

            @SuppressWarnings("unused")
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
                    configuration
            );

            var criteria = resolver.resolve("id", new StubProcessingContext());
            assertEquals(EventCriteria.havingTags("aggregateIdentifier", "id")
                                      .andBeingOneOfTypes("MyMessageType"), criteria);
        }

        @EventSourcedEntity
        static class EntityWithInjectedMessageTypeResolver {

            @SuppressWarnings("unused")
            @EventCriteriaBuilder
            public static EventCriteria buildCriteria(String id, MessageTypeResolver messageTypeResolver) {
                return EventCriteria.havingTags("aggregateIdentifier", id)
                                    .andBeingOneOfTypes(messageTypeResolver, Integer.class);
            }
        }

        @Test
        void throwsOnUnknownParameterOfEventCriteriaBuilderMethod() {
            var exception = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnnotationBasedEventCriteriaResolver<>(EntityWithUnknownInjectionParameter.class,
                                                                     Object.class,
                                                                     configuration));
            assertEquals(
                    "Method annotated with @EventCriteriaBuilder declared a parameter which is not a component: java.lang.Integer. Violating method: buildCriteria(java.lang.String,java.lang.Integer)",
                    exception.getMessage()
            );
        }

        @EventSourcedEntity
        static class EntityWithUnknownInjectionParameter {

            @SuppressWarnings("unused")
            @EventCriteriaBuilder
            public static EventCriteria buildCriteria(String id, Integer integer) {
                return EventCriteria.havingTags("aggregateIdentifier", id);
            }
        }
    }
}