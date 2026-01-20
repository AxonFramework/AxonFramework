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

package org.axonframework.messaging.core.annotation;

import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver.AnnotationSpecification;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.queryhandling.annotation.Query;
import org.junit.jupiter.api.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AnnotationMessageTypeResolver}.
 *
 * @author Steven van Beelen
 */
class AnnotationMessageTypeResolverTest {

    private MessageTypeResolver fallback;

    private AnnotationMessageTypeResolver testSubject;

    @BeforeEach
    void setUp() {
        fallback = mock(MessageTypeResolver.class);

        testSubject = new AnnotationMessageTypeResolver(fallback);
    }

    @Nested
    class CommandMessageResolution {

        @Test
        void classAnnotatedWithCommandReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation.test-command-domain-name", "1.33.7");

            Optional<MessageType> result = testSubject.resolve(TestCommand.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Test
        void classAnnotatedWithCommandIncludingNamespaceReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("context", "test-command-domain-name", "1.33.7");

            Optional<MessageType> result = testSubject.resolve(TestCommandWithNamespace.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Command(name = "test-command-domain-name", version = "1.33.7")
        private record TestCommand(String id) {

        }

        @Command(name = "test-command-domain-name", version = "1.33.7", namespace = "context")
        private record TestCommandWithNamespace(String id) {

        }
    }

    @Nested
    class EventMessageResolution {

        @Test
        void classAnnotatedWithEventReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation", "event-business-name", "42");

            Optional<MessageType> result = testSubject.resolve(TestEvent.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Test
        void classAnnotatedWithEventIncludingNamespaceReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("context", "event-business-name", "42");

            Optional<MessageType> result = testSubject.resolve(TestEventWithNamespace.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Event(name = "event-business-name", version = "42")
        private record TestEvent(String id) {

        }

        @Event(name = "event-business-name", version = "42", namespace = "context")
        private record TestEventWithNamespace(String id) {

        }
    }

    @Nested
    class QueryMessageResolution {

        @Test
        void classAnnotatedWithQueryReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation", "non-of-your-business-query-name", "9001");

            Optional<MessageType> result = testSubject.resolve(TestQuery.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Test
        void classAnnotatedWithQueryIncludingNamespaceReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("context", "non-of-your-business-query-name", "9001");

            Optional<MessageType> result = testSubject.resolve(TestQueryWithNamespace.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Query(name = "non-of-your-business-query-name", version = "9001")
        private record TestQuery(String id) {

        }

        @Query(name = "non-of-your-business-query-name", version = "9001", namespace = "context")
        private record TestQueryWithNamespace(String id) {

        }
    }

    @Nested
    class QueryResponseMessageResolution {

        @Test
        void classAnnotatedWithQueryResponseReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation", "non-of-your-business-query-response-name", "9002");

            Optional<MessageType> result = testSubject.resolve(TestQueryResponse.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Test
        void classAnnotatedWithQueryResponseIncludingNamespaceReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("context", "non-of-your-business-query-response-name", "9002");

            Optional<MessageType> result = testSubject.resolve(TestQueryResponseWithNamespace.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);

        }

        @Query(name = "non-of-your-business-query-response-name", version = "9002")
        private record TestQueryResponse(String id) {

        }
        @Query(name = "non-of-your-business-query-response-name", version = "9002", namespace = "context")
        private record TestQueryResponseWithNamespace(String id) {

        }
    }

    @Nested
    class MetaAnnotatedResolution {

        @Test
        void classAnnotatedWithMetaAnnotatedMessageReturnsExpectedMessageType() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation", "meta-annotated", "-1");

            Optional<MessageType> result = testSubject.resolve(MetaAnnotatedMessage.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Message(name = "meta-annotated", version = "-1", messageType = org.axonframework.messaging.core.Message.class)
        private @interface MyMessageSpecificAnnotation {

        }

        @MyMessageSpecificAnnotation
        private record MetaAnnotatedMessage(String id) {

        }
    }

    @Nested
    class CustomAnnotationResolution {

        @Test
        void customAnnotationSpecificationIsHonored() {
            AnnotationSpecification specification = new AnnotationSpecification(CustomMessageAnnotation.class,
                                                                                "customName",
                                                                                "customVersion",
                                                                                "customNamespace");
            AnnotationMessageTypeResolver customAnnotationTestSubject =
                    new AnnotationMessageTypeResolver(null, specification);

            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation", "customName", "customVersion");

            Optional<MessageType> result = customAnnotationTestSubject.resolve(CustomAnnotatedMessage.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        // Intentionally public to ensure the AnnotationUtils can access the properties.
        @Retention(RetentionPolicy.RUNTIME)
        public @interface CustomMessageAnnotation {

            String customName() default "customName";

            String customVersion() default "customVersion";

            String customNamespace() default "";
        }

        @CustomMessageAnnotation
        private record CustomAnnotatedMessage(String id) {

        }
    }

    @Nested
    class FallbackResolution {

        @Test
        void fallbackIsInvokedInAbsenceOfSpecificAnnotation() {
            MessageType expectedType = new MessageType("fallback", "2025");
            when(fallback.resolve(any())).thenReturn(Optional.of(expectedType));

            Optional<MessageType> result = testSubject.resolve(Object.class);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expectedType);
        }

        @Test
        void fallbackIsNotInvokedInPresenceOfSpecificAnnotationWithoutParameters() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation.EventWithDefaultParameters");
            Optional<MessageType> actual = testSubject.resolve(EventWithDefaultParameters.class);

            assertThat(actual).isPresent();
            assertThat(actual).contains(expectedType);
            verify(fallback, never()).resolve(any());
        }

        @Test
        void fallbackIsNotInvokedInPresenceOfSpecificAnnotationWithOnlyVersionSpecified() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation.EventWithVersionParameter", "42");
            Optional<MessageType> actual = testSubject.resolve(EventWithVersionParameter.class);

            assertThat(actual).isPresent();
            assertThat(actual).contains(expectedType);
            verify(fallback, never()).resolve(any());
        }

        @Test
        void fallbackIsNotInvokedInPresenceOfSpecificAnnotationWithOnlyNameSpecified() {
            MessageType expectedType = new MessageType("org.axonframework.messaging.core.annotation.MyName");
            Optional<MessageType> actual = testSubject.resolve(EventWithNameParameter.class);

            assertThat(actual).isPresent();
            assertThat(actual).contains(expectedType);
            verify(fallback, never()).resolve(any());
        }

        @Test
        void fallbackIsNotInvokedInPresenceOfSpecificAnnotationWithOnlyNamespaceSpecified() {
            MessageType expectedType = new MessageType("custom.namespace.EventWithNamespaceParameter");
            Optional<MessageType> actual = testSubject.resolve(EventWithNamespaceParameter.class);

            assertThat(actual).isPresent();
            assertThat(actual).contains(expectedType);
            verify(fallback, never()).resolve(any());
        }

        @Event
        private record EventWithDefaultParameters(String id) {

        }
        @Event(version = "42")
        private record EventWithVersionParameter(String id) {

        }

        @Event(name = "MyName")
        private record EventWithNameParameter(String id) {

        }
        @Event(namespace = "custom.namespace")
        private record EventWithNamespaceParameter(String id) {

        }

    }
}