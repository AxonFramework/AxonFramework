/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.annotation;

import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.sequencing.MetadataSequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPolicy;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.eventhandling.sequencing.SequencingPolicy;
import org.junit.jupiter.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link MethodSequencingPolicyEventHandlerDefinition} to verify proper wrapping of
 * event handlers with sequencing policy annotation.
 *
 * @author Mateusz Nowak
 */
class MethodSequencingPolicyEventHandlerDefinitionTest {

    private MethodSequencingPolicyEventHandlerDefinition testSubject;
    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;

    @BeforeEach
    void setUp() {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new MethodSequencingPolicyEventHandlerDefinition();
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();
    }

    @Nested
    class MethodLevelAnnotation {

        @Test
        void methodWithSequentialPolicyAnnotation() {
            MessageHandlingMember<MethodLevelPolicyTest> handler = createHandler(
                    MethodLevelPolicyTest.class, "sequentialPolicyMethod", String.class
            );
            MessageHandlingMember<MethodLevelPolicyTest> wrappedHandler = testSubject.wrapHandler(handler);

            assertNotSame(handler, wrappedHandler);
            SequencingPolicy policy = getSequencingPolicy(wrappedHandler);
            assertEquals(SequentialPolicy.class, policy.getClass());
        }

        @Test
        void methodWithMetadataPolicyAnnotation() {
            MessageHandlingMember<MethodLevelPolicyTest> handler = createHandler(
                    MethodLevelPolicyTest.class, "metadataPolicyMethod", TestEvent.class
            );
            MessageHandlingMember<MethodLevelPolicyTest> wrappedHandler = testSubject.wrapHandler(handler);

            assertNotSame(handler, wrappedHandler);
            SequencingPolicy policy = getSequencingPolicy(wrappedHandler);
            assertEquals(MetadataSequencingPolicy.class, policy.getClass());
        }

        @Test
        void methodWithPropertyPolicyAnnotation() {
            MessageHandlingMember<MethodLevelPolicyTest> handler = createHandler(
                    MethodLevelPolicyTest.class, "propertyPolicyMethod", TestEvent.class
            );
            MessageHandlingMember<MethodLevelPolicyTest> wrappedHandler = testSubject.wrapHandler(handler);

            assertNotSame(handler, wrappedHandler);
            SequencingPolicy policy = getSequencingPolicy(wrappedHandler);
            assertEquals(PropertySequencingPolicy.class, policy.getClass());
        }

        @Test
        void methodWithInvalidParameterCount() {
            assertThrows(UnsupportedHandlerException.class, () -> {
                MessageHandlingMember<MethodLevelPolicyTest> handler = createHandler(
                        MethodLevelPolicyTest.class, "invalidParameterCountMethod", TestEvent.class
                );
                testSubject.wrapHandler(handler);
            });
        }

        @Test
        void methodWithCustomPolicyWithInvalidClassPosition() {
            assertThrows(UnsupportedHandlerException.class, () -> {
                MessageHandlingMember<MethodLevelPolicyTest> handler = createHandler(
                        MethodLevelPolicyTest.class, "invalidClassPositionMethod", TestEvent.class
                );
                testSubject.wrapHandler(handler);
            });
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

            assertNotSame(handler, wrappedHandler);
            SequencingPolicy policy = getSequencingPolicy(wrappedHandler);
            assertEquals(SequentialPolicy.class, policy.getClass());
        }
    }

    @Nested
    class NoAnnotation {

        @Test
        void methodWithoutAnnotationNotWrapped() {
            MessageHandlingMember<NoAnnotationTest> handler =
                    createHandler(NoAnnotationTest.class, "methodWithoutAnnotation", String.class);
            MessageHandlingMember<NoAnnotationTest> wrappedHandler = testSubject.wrapHandler(handler);

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

            assertNotSame(handler, wrappedHandler);
            SequencingPolicy policy = getSequencingPolicy(wrappedHandler);
            // Should be MetadataSequencingPolicy from method, not SequentialPolicy from class
            assertEquals(MetadataSequencingPolicy.class, policy.getClass());
        }
    }

    @Nested
    class ResetHandlerNotWrapped {

        @Test
        void resetHandlerWithClassLevelPolicyNotWrapped() {
            MessageHandlingMember<ClassWithResetHandler> handler = createHandler(
                    ClassWithResetHandler.class, "onReset"
            );
            MessageHandlingMember<ClassWithResetHandler> wrappedHandler = testSubject.wrapHandler(handler);

            // ResetHandler methods should NOT be wrapped with sequencing policy
            assertSame(handler, wrappedHandler);
        }

        @Test
        void resetHandlerWithMethodLevelPolicyNotWrapped() {
            MessageHandlingMember<ResetHandlerWithMethodPolicy> handler = createHandler(
                    ResetHandlerWithMethodPolicy.class, "onReset"
            );
            MessageHandlingMember<ResetHandlerWithMethodPolicy> wrappedHandler = testSubject.wrapHandler(handler);

            // ResetHandler methods should NOT be wrapped with sequencing policy even if annotated
            assertSame(handler, wrappedHandler);
        }
    }

    @Nested
    class MetaAnnotationSupport {

        @Test
        void customEventHandlerAnnotationWithClassLevelPolicyIsWrapped() {
            MessageHandlingMember<CustomEventHandlerWithClassPolicy> handler = createHandler(
                    CustomEventHandlerWithClassPolicy.class, "handleWithCustomAnnotation", String.class
            );
            MessageHandlingMember<CustomEventHandlerWithClassPolicy> wrappedHandler = testSubject.wrapHandler(handler);

            // Custom meta-annotated EventHandler should be wrapped
            assertNotSame(handler, wrappedHandler);
            SequencingPolicy policy = getSequencingPolicy(wrappedHandler);
            assertEquals(SequentialPolicy.class, policy.getClass());
        }
    }

    private static MessageStream<?> returnTypeConverter(Object result) {
        return MessageStream.just(new GenericMessage(new MessageType(ObjectUtils.nullSafeTypeOf(result)), result));
    }

    private SequencingPolicy getSequencingPolicy(MessageHandlingMember<?> wrappedHandler) {
        var handler = (MethodSequencingPolicyEventHandlerDefinition.SequencingPolicyEventMessageHandlingMember<?>) wrappedHandler;
        return handler.sequencingPolicy();
    }

    private <T> MessageHandlingMember<T> createHandler(Class<T> targetClass, String methodName, Class<?>... parameterTypes) {
        try {
            return handlerDefinition.createHandler(
                    targetClass,
                    targetClass.getDeclaredMethod(methodName, parameterTypes),
                    parameterResolver,
                    MethodSequencingPolicyEventHandlerDefinitionTest::returnTypeConverter
            ).orElseThrow(() -> new IllegalArgumentException("Handler creation failed"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method used to build handler does not exist: " + methodName, e);
        }
    }

    // Test class for method-level annotation
    static class MethodLevelPolicyTest {
        @SuppressWarnings("unused")
        @EventHandler
        @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = SequentialPolicy.class)
        void sequentialPolicyMethod(String payload) {
        }

        @SuppressWarnings("unused")
        @EventHandler
        @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"userId"})
        void metadataPolicyMethod(TestEvent payload) {
        }

        @SuppressWarnings("unused")
        @EventHandler
        @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"aggregateId"})
        void propertyPolicyMethod(TestEvent payload) {
        }

        @SuppressWarnings("unused")
        @EventHandler
        @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"param1", "param2", "param3"})
        void invalidParameterCountMethod(TestEvent payload) {
        }

        @SuppressWarnings("unused")
        @EventHandler
        @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = InvalidClassPositionPolicy.class, parameters = {"someParameter"})
        void invalidClassPositionMethod(TestEvent payload) {
        }
    }

    // Test class for no annotation
    static class NoAnnotationTest {
        @SuppressWarnings("unused")
        @EventHandler
        void methodWithoutAnnotation(String payload) {
        }
    }

    // Test event record with properties for PropertySequencingPolicy
    public record TestEvent(String aggregateId, String eventType, long timestamp) {
    }

    // Test policy with Class parameter in wrong position (should fail)
    static class InvalidClassPositionPolicy implements SequencingPolicy {
        public InvalidClassPositionPolicy(String parameter, Class<?> payloadClass) {
            // Class parameter is not first - this should cause an error
        }

        @Override
        public java.util.Optional<Object> getSequenceIdentifierFor(
                EventMessage event,
                ProcessingContext context) {
            return java.util.Optional.of("test");
        }
    }

    // Test class with class-level annotation
    @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = SequentialPolicy.class)
    static class ClassLevelPolicyTest {
        @SuppressWarnings("unused")
        @EventHandler
        void eventHandlerMethod(String payload) {
        }
    }

    // Test class where method overrides class-level annotation
    @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = SequentialPolicy.class)
    static class MethodOverridesClassTest {
        @SuppressWarnings("unused")
        @EventHandler
        @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"override"})
        void eventHandlerMethod(String payload) {
        }
    }

    // Test class with class-level SequencingPolicy and a ResetHandler - the ResetHandler should NOT be wrapped
    @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = SequentialPolicy.class)
    static class ClassWithResetHandler {
        @SuppressWarnings("unused")
        @EventHandler
        void eventHandlerMethod(String payload) {
        }

        @SuppressWarnings("unused")
        @ResetHandler
        void onReset() {
        }
    }

    // Test class with method-level SequencingPolicy on a ResetHandler - should NOT be wrapped
    static class ResetHandlerWithMethodPolicy {
        @SuppressWarnings("unused")
        @ResetHandler
        @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = SequentialPolicy.class)
        void onReset() {
        }
    }

    // Custom annotation meta-annotated with @EventHandler
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @EventHandler
    @interface CustomEventHandler {
    }

    // Test class with custom meta-annotation and class-level SequencingPolicy
    @org.axonframework.messaging.eventhandling.annotation.SequencingPolicy(type = SequentialPolicy.class)
    static class CustomEventHandlerWithClassPolicy {
        @SuppressWarnings("unused")
        @CustomEventHandler
        void handleWithCustomAnnotation(String payload) {
        }
    }
}