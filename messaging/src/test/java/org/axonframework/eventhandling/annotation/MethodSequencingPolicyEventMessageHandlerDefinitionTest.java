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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.ObjectUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.annotations.SequencingPolicy;
import org.axonframework.eventhandling.sequencing.MetadataSequencingPolicy;
import org.axonframework.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.eventhandling.sequencing.SequentialPolicy;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link MethodSequencingPolicyEventMessageHandlerDefinition} to verify proper wrapping of
 * event handlers with sequencing policy annotations.
 *
 * @author Mateusz Nowak
 */
class MethodSequencingPolicyEventMessageHandlerDefinitionTest {

    private MethodSequencingPolicyEventMessageHandlerDefinition testSubject;
    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;

    @BeforeEach
    void setUp() {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new MethodSequencingPolicyEventMessageHandlerDefinition();
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();
    }

    @Nested
    class MethodLevelAnnotation {

        @Test
        void methodWithSequentialPolicyAnnotation() {
            SequencingPolicyEventMessageHandlingMember<?> handler = sequencingPolicyHandler("sequentialPolicyMethod");
            assertNotNull(handler.sequencingPolicy());
            assertEquals(SequentialPolicy.class, handler.sequencingPolicy().getClass());
        }

        @Test
        void methodWithMetadataPolicyAnnotation() {
            SequencingPolicyEventMessageHandlingMember<?> handler = sequencingPolicyHandlerForTestEvent("metadataPolicyMethod");
            assertNotNull(handler.sequencingPolicy());
            assertEquals(MetadataSequencingPolicy.class, handler.sequencingPolicy().getClass());
        }

        @Test
        void methodWithPropertyPolicyAnnotation() {
            SequencingPolicyEventMessageHandlingMember<?> handler = sequencingPolicyHandlerForTestEvent("propertyPolicyMethod");
            assertNotNull(handler.sequencingPolicy());
            assertEquals(PropertySequencingPolicy.class, handler.sequencingPolicy().getClass());
        }

        @Test
        void methodWithInvalidParameterCount() {
            assertThrows(UnsupportedHandlerException.class,
                    () -> sequencingPolicyHandlerForTestEvent("invalidParameterCountMethod"));
        }

        @Test
        void methodWithCustomPolicyWithInvalidClassPosition() {
            assertThrows(UnsupportedHandlerException.class,
                    () -> sequencingPolicyHandlerForTestEvent("invalidClassPositionMethod"));
        }
    }

    @Nested
    class ClassLevelAnnotation {

        @Test
        void classLevelAnnotationAppliedToMethod() {
            MessageHandlingMember<ClassLevelPolicyTest> handler = createHandler(
                    ClassLevelPolicyTest.class, "eventHandlerMethod", String.class
            );
            MessageHandlingMember<ClassLevelPolicyTest> wrappedHandler = testSubject.wrapHandler(handler);

            assertTrue(wrappedHandler instanceof SequencingPolicyEventMessageHandlingMember);
            SequencingPolicyEventMessageHandlingMember<ClassLevelPolicyTest> policyHandler =
                    (SequencingPolicyEventMessageHandlingMember<ClassLevelPolicyTest>) wrappedHandler;
            assertEquals(SequentialPolicy.class, policyHandler.sequencingPolicy().getClass());
        }
    }

    @Nested
    class NoAnnotation {

        @Test
        void methodWithoutAnnotationNotWrapped() {
            MessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest> handler =
                    createHandler(MethodSequencingPolicyEventMessageHandlerDefinitionTest.class,
                            "methodWithoutAnnotation", String.class);
            MessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest> wrappedHandler =
                    testSubject.wrapHandler(handler);

            assertSame(handler, wrappedHandler);
        }
    }

    @Nested
    class MethodOverridesClass {

        @Test
        void methodLevelOverridesClassLevel() {
            MessageHandlingMember<MethodOverridesClassTest> handler = createHandler(
                    MethodOverridesClassTest.class, "eventHandlerMethod", String.class
            );
            MessageHandlingMember<MethodOverridesClassTest> wrappedHandler = testSubject.wrapHandler(handler);

            assertTrue(wrappedHandler instanceof SequencingPolicyEventMessageHandlingMember);
            SequencingPolicyEventMessageHandlingMember<MethodOverridesClassTest> policyHandler =
                    (SequencingPolicyEventMessageHandlingMember<MethodOverridesClassTest>) wrappedHandler;
            // Should be MetadataSequencingPolicy from method, not SequentialPolicy from class
            assertEquals(MetadataSequencingPolicy.class, policyHandler.sequencingPolicy().getClass());
        }
    }

    // TODO This local static function should be replaced with a dedicated interface that converts types.
    // TODO However, that's out of the scope of the unit-of-rework branch and thus will be picked up later.
    private static MessageStream<?> returnTypeConverter(Object result) {
        return MessageStream.just(new GenericMessage(new MessageType(ObjectUtils.nullSafeTypeOf(result)), result));
    }

    private SequencingPolicyEventMessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest>
            sequencingPolicyHandler(String methodName) {
        MessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest> handler =
                createHandler(MethodSequencingPolicyEventMessageHandlerDefinitionTest.class, methodName, String.class);
        MessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest> wrappedHandler =
                testSubject.wrapHandler(handler);

        assertTrue(wrappedHandler instanceof SequencingPolicyEventMessageHandlingMember,
                "Method should be wrapped with SequencingPolicyEventMessageHandlingMember");

        //noinspection unchecked
        return (SequencingPolicyEventMessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest>)
                wrappedHandler;
    }

    private SequencingPolicyEventMessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest>
            sequencingPolicyHandlerForTestEvent(String methodName) {
        MessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest> handler =
                createHandler(MethodSequencingPolicyEventMessageHandlerDefinitionTest.class, methodName, TestEvent.class);
        MessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest> wrappedHandler =
                testSubject.wrapHandler(handler);

        assertTrue(wrappedHandler instanceof SequencingPolicyEventMessageHandlingMember,
                "Method should be wrapped with SequencingPolicyEventMessageHandlingMember");

        //noinspection unchecked
        return (SequencingPolicyEventMessageHandlingMember<MethodSequencingPolicyEventMessageHandlerDefinitionTest>)
                wrappedHandler;
    }

    private <T> MessageHandlingMember<T> createHandler(Class<T> targetClass, String methodName, Class<?>... parameterTypes) {
        try {
            return handlerDefinition.createHandler(
                    targetClass,
                    targetClass.getDeclaredMethod(methodName, parameterTypes),
                    parameterResolver,
                    MethodSequencingPolicyEventMessageHandlerDefinitionTest::returnTypeConverter
            ).orElseThrow(() -> new IllegalArgumentException("Handler creation failed"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method used to build handler does not exist: " + methodName, e);
        }
    }

    // Test methods for method-level annotations
    @SuppressWarnings("unused")
    @EventHandler
    @SequencingPolicy(type = SequentialPolicy.class)
    private void sequentialPolicyMethod(String payload) {
    }

    @SuppressWarnings("unused")
    @EventHandler
    @SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"userId"})
    private void metadataPolicyMethod(TestEvent payload) {
    }

    @SuppressWarnings("unused")
    @EventHandler
    @SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"aggregateId"})
    private void propertyPolicyMethod(TestEvent payload) {
    }

    @SuppressWarnings("unused")
    @EventHandler
    @SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"param1", "param2", "param3"})
    private void invalidParameterCountMethod(TestEvent payload) {
    }

    @SuppressWarnings("unused")
    @EventHandler
    @SequencingPolicy(type = InvalidClassPositionPolicy.class, parameters = {"someParameter"})
    private void invalidClassPositionMethod(TestEvent payload) {
    }

    @SuppressWarnings("unused")
    @EventHandler
    private void methodWithoutAnnotation(String payload) {
    }

    // Test event record with properties for PropertySequencingPolicy
    public record TestEvent(String aggregateId, String eventType, long timestamp) {
    }

    // Test policy with Class parameter in wrong position (should fail)
    static class InvalidClassPositionPolicy implements org.axonframework.eventhandling.sequencing.SequencingPolicy {
        public InvalidClassPositionPolicy(String parameter, Class<?> payloadClass) {
            // Class parameter is not first - this should cause an error
        }

        @Override
        public java.util.Optional<Object> getSequenceIdentifierFor(
                org.axonframework.eventhandling.EventMessage event,
                org.axonframework.messaging.unitofwork.ProcessingContext context) {
            return java.util.Optional.of("test");
        }
    }

    // Test class with class-level annotation
    @SequencingPolicy(type = SequentialPolicy.class)
    static class ClassLevelPolicyTest {
        @SuppressWarnings("unused")
        @EventHandler
        void eventHandlerMethod(String payload) {
        }
    }

    // Test class where method overrides class-level annotation
    @SequencingPolicy(type = SequentialPolicy.class)
    static class MethodOverridesClassTest {
        @SuppressWarnings("unused")
        @EventHandler
        @SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"override"})
        void eventHandlerMethod(String payload) {
        }
    }
}