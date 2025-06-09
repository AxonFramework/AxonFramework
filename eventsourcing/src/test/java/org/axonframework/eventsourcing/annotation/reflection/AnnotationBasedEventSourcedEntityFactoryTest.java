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

package org.axonframework.eventsourcing.annotation.reflection;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnotationBasedEventSourcedEntityFactoryTest {

    @Spy
    private ParameterResolverFactory parameterResolverFactory = ClasspathParameterResolverFactory.forClass(this.getClass());

    @Spy
    private MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @Mock
    private EventMessage<?> eventMessage;


    @BeforeEach
    void setUp() {
        lenient().when(eventMessage.type()).thenReturn(new MessageType("matching-test-type"));
    }

    @Nested
    class Constructors {

        private AnnotationBasedEventSourcedEntityFactory<EventMessageTestEntity, String> factory;

        @BeforeEach
        void setUp() {
            factory = new AnnotationBasedEventSourcedEntityFactory<>(
                    EventMessageTestEntity.class,
                    String.class,
                    parameterResolverFactory,
                    messageTypeResolver
            );
        }

        @Test
        void usesIdConstructorWithoutMessage() {
            EventMessageTestEntity entity = factory.create("test-id", null, new StubProcessingContext());
            assertNotNull(entity);
            assertEquals("test-id", entity.getId());
            assertNull(entity.getEventMessage());
        }

        @Test
        void usesEventMessageConstructorWithEventMessage() {
            EventMessageTestEntity entity = factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            assertNotNull(entity);
            assertEquals("test-id", entity.getId());
            assertSame(eventMessage, entity.getEventMessage());
        }

        public static class EventMessageTestEntity {

            private final String id;
            private final EventMessage<?> eventMessage;

            @EntityCreator
            public EventMessageTestEntity(String id) {
                this.id = id;
                this.eventMessage = null;
            }

            @EntityCreator
            public EventMessageTestEntity(String id, EventMessage<?> eventMessage) {
                this.id = id;
                this.eventMessage = eventMessage;
            }

            public String getId() {
                return id;
            }

            public EventMessage<?> getEventMessage() {
                return eventMessage;
            }
        }

    }

    @Nested
    class PayloadTypeMatchingForEventMessage {

        private AnnotationBasedEventSourcedEntityFactory<PayloadTypeSpecificTestEntity, String> factory;

        @BeforeEach
        void setUp() {
            factory = new AnnotationBasedEventSourcedEntityFactory<>(
                    PayloadTypeSpecificTestEntity.class,
                    String.class,
                    parameterResolverFactory,
                    messageTypeResolver
            );
        }

        @Test
        void usesEventMessageConstructorWithCorrectPayloadType() {
            PayloadTypeSpecificTestEntity entity = factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            assertNotNull(entity);
            assertSame(eventMessage, entity.getEventMessage());
        }

        @Test
        void throwsErrorIfNoMatchingPayloadType() {
            when(eventMessage.type()).thenReturn(new MessageType("non-matching-test-type"));
            AxonConfigurationException exception = assertThrows(AxonConfigurationException.class, () -> {
                factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            });
            assertTrue(exception.getMessage().contains("No suitable @EntityCreator found"));
        }

        @Test
        void throwsErrorIfRuntimeParametersDontMatch() {
            when(eventMessage.type()).thenReturn(new MessageType("metadata-required-test-type"));
            when(eventMessage.getMetaData()).thenReturn(MetaData.emptyInstance());
            AxonConfigurationException exception = assertThrows(AxonConfigurationException.class, () -> {
                factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            });
            assertTrue(exception.getMessage().contains("No @EntityCreator matched for entity id"));
        }

        public static class PayloadTypeSpecificTestEntity {

            private final EventMessage<?> eventMessage;

            @EntityCreator(payloadQualifiedNames = "matching-test-type")
            public PayloadTypeSpecificTestEntity(EventMessage<String> eventMessage) {
                this.eventMessage = eventMessage;
            }


            @EntityCreator(payloadQualifiedNames = "metadata-required-test-type")
            public PayloadTypeSpecificTestEntity(EventMessage<String> eventMessage, @MetaDataValue(required = true, value = "blabla") Integer blabla) {
                this.eventMessage = eventMessage;
            }

            public EventMessage<?> getEventMessage() {
                return eventMessage;
            }
        }
    }


    @Nested
    class PayloadTypeMatchingForPayload {

        private AnnotationBasedEventSourcedEntityFactory<PayloadSpecificTestEntity, String> factory;

        @BeforeEach
        void setUp() {
            factory = new AnnotationBasedEventSourcedEntityFactory<>(
                    PayloadSpecificTestEntity.class,
                    String.class,
                    parameterResolverFactory,
                    messageTypeResolver
            );
        }

        @Test
        void usesEventPayloadConstructorWithCorrectPayloadType() {
            eventMessage = new GenericEventMessage<>(new MessageType(PayloadSpecificPayload.class), new PayloadSpecificPayload("my-specific-payload"));
            PayloadSpecificTestEntity entity = factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            assertNotNull(entity);
            assertEquals("my-specific-payload", entity.getPayload());
        }

        @Test
        void throwsErrorIfNoMatchingPayloadType() {
            eventMessage = new GenericEventMessage<>(new MessageType("non-matching-test-type"), new PayloadSpecificPayload("my-specific-payload"));
            AxonConfigurationException exception = assertThrows(AxonConfigurationException.class, () -> {
                factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            });
            assertTrue(exception.getMessage().contains("No suitable @EntityCreator found"));
        }

        public static class PayloadSpecificTestEntity {

            private final String payload;

            @EntityCreator()
            public PayloadSpecificTestEntity(PayloadSpecificPayload payload) {
                this.payload = payload.payload;
            }

            public String getPayload() {
                return payload;
            }
        }

        public record PayloadSpecificPayload(
                String payload
        ) {}
    }

    @Nested
    class FactoryMethod {

        private AnnotationBasedEventSourcedEntityFactory<FactoryMethodsTestEntity, String> factory;

        @BeforeEach
        void setUp() {
            factory = new AnnotationBasedEventSourcedEntityFactory<>(
                    FactoryMethodsTestEntity.class,
                    String.class,
                    parameterResolverFactory,
                    messageTypeResolver
            );
        }

        @Test
        void usesIdFactoryMethodForNullEventMessage() {
            FactoryMethodsTestEntity entity = factory.create("test-id", null, new StubProcessingContext());
            assertNotNull(entity);
            assertEquals("test-id", entity.getId());
            assertNull(entity.getEventMessage());
        }

        @Test
        void usesEventMessageFactoryMethodForEventMessage() {
            FactoryMethodsTestEntity entity = factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            assertNotNull(entity);
            assertEquals("test-id", entity.getId());
            assertSame(eventMessage, entity.getEventMessage());
        }

        public static class FactoryMethodsTestEntity {

            private final String id;
            private final EventMessage<?> eventMessage;

            private FactoryMethodsTestEntity(String id, EventMessage<?> eventMessage) {
                this.id = id;
                this.eventMessage = eventMessage;
            }

            @EntityCreator
            public static FactoryMethodsTestEntity create(String id) {
                return new FactoryMethodsTestEntity(id, null);
            }

            @EntityCreator
            public static FactoryMethodsTestEntity create(String id, EventMessage<?> eventMessage) {
                return new FactoryMethodsTestEntity(id, eventMessage);
            }

            public String getId() {
                return id;
            }

            public EventMessage<?> getEventMessage() {
                return eventMessage;
            }
        }
    }

    @Nested
    class MostSpecific {

        @Test
        void invokesMetadataMethodIfPresent() {
            var factory = new AnnotationBasedEventSourcedEntityFactory<>(
                    MostSpecificHandlerEntity.class,
                    String.class,
                    parameterResolverFactory,
                    messageTypeResolver
            );

            when(eventMessage.getMetaData()).thenReturn(MetaData.from(Collections.singletonMap("blabla", "blabla")));
            var entity = factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            assertEquals("id-and-metadata", entity.invoked);
        }

        @Test
        void invokesSimpleMethodIfNoMetadataPresent() {
            var factory = new AnnotationBasedEventSourcedEntityFactory<>(
                    MostSpecificHandlerEntity.class,
                    String.class,
                    parameterResolverFactory,
                    messageTypeResolver
            );

            when(eventMessage.getMetaData()).thenReturn(MetaData.emptyInstance());
            var entity = factory.create("test-id", eventMessage, StubProcessingContext.forMessage(eventMessage));
            assertEquals("simply-id", entity.invoked);
        }

        @Test
        void invokesSimpleMethodIfMessageNotPresent() {
            var factory = new AnnotationBasedEventSourcedEntityFactory<>(
                    MostSpecificHandlerEntity.class,
                    String.class,
                    parameterResolverFactory,
                    messageTypeResolver
            );

            var entity = factory.create("test-id", null, new StubProcessingContext());
            assertEquals("simply-id", entity.invoked);
        }

        static class MostSpecificHandlerEntity {

            private final String invoked;

            @EntityCreator
            public MostSpecificHandlerEntity(String id) {
                this.invoked = "simply-id";
            }

            @EntityCreator
            public MostSpecificHandlerEntity(String id,
                                             @MetaDataValue(required = true, value = "blabla") String blabla) {
                this.invoked = "id-and-metadata";
            }
        }
    }

    @Nested
    class InvalidConfigurations {

        @Test
        void throwsOnNonStaticFactoryMethod() {
            var exception = assertThrows(AxonConfigurationException.class, () -> {
                new AnnotationBasedEventSourcedEntityFactory<>(
                        InvalidEntityNonStaticMethod.class,
                        String.class,
                        Collections.singleton(InvalidEntityNonStaticMethod.class),
                        parameterResolverFactory,
                        messageTypeResolver
                );
            });
            assertTrue(exception.getMessage().contains("Method-based @EntityCreator must be static"));
        }

        @Test
        void throwsOnInvalidFactoryMethodReturnType() {
            var exception = assertThrows(AxonConfigurationException.class, () -> {
                new AnnotationBasedEventSourcedEntityFactory<>(
                        InvalidEntityReturnType.class,
                        String.class,
                        Collections.singleton(InvalidEntityReturnType.class),
                        parameterResolverFactory,
                        messageTypeResolver
                );
            });
            assertTrue(exception.getMessage()
                                .contains("@EntityCreator must return the entity type or a subtype"));
        }

        @Test
        void throwsOnMissingFactoryMethods() {
            var exception = assertThrows(AxonConfigurationException.class, () -> {
                new AnnotationBasedEventSourcedEntityFactory<>(
                        NoAnnotatedMethodsEntity.class,
                        String.class,
                        Collections.singleton(NoAnnotatedMethodsEntity.class),
                        parameterResolverFactory,
                        messageTypeResolver
                );
            });
            assertTrue(exception.getMessage().contains(
                    "No @EntityCreator present on entity of type"));
        }

        public static class InvalidEntityNonStaticMethod {

            @EntityCreator
            public InvalidEntityNonStaticMethod create(String id) {
                return new InvalidEntityNonStaticMethod();
            }
        }

        public static class InvalidEntityReturnType {

            @EntityCreator
            public static String create(String id) {
                return id;
            }
        }

        public static class NoAnnotatedMethodsEntity {

            public NoAnnotatedMethodsEntity(String id) {
            }
        }
    }
}
