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

package org.axonframework.extension.reactor.messaging.eventhandling.gateway;

import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link DefaultReactiveEventGateway}.
 */
class DefaultReactiveEventGatewayTest {

    private EventGateway mockEventGateway;

    private DefaultReactiveEventGateway testSubject;

    @BeforeEach
    void setUp() {
        mockEventGateway = mock(EventGateway.class);
        when(mockEventGateway.publish(anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));

        testSubject = DefaultReactiveEventGateway.builder()
                .eventGateway(mockEventGateway)
                .messageTypeResolver(new ClassBasedMessageTypeResolver())
                .build();
    }

    @Nested
    class Publish {

        @Test
        void publishSingleEvent() {
            // when / then
            StepVerifier.create(testSubject.publish("Event1"))
                        .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
            verify(mockEventGateway).publish(captor.capture());
            List<Object> result = captor.getValue();
            assertThat(result).hasSize(1);
            assertThat(((EventMessage) result.getFirst()).payload()).isEqualTo("Event1");
        }

        @Test
        void publishMultipleEvents() {
            // when / then
            StepVerifier.create(testSubject.publish("Event1", "Event2"))
                        .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
            verify(mockEventGateway).publish(captor.capture());
            List<Object> result = captor.getValue();
            assertThat(result).hasSize(2);
            assertThat(((EventMessage) result.get(0)).payload()).isEqualTo("Event1");
            assertThat(((EventMessage) result.get(1)).payload()).isEqualTo("Event2");
        }

        @Test
        void publishEventMessage() {
            // given
            var payload = new TestPayload(UUID.randomUUID().toString());
            var eventMessage = new GenericEventMessage(new MessageType("TestPayload"), payload)
                    .withMetadata(Metadata.with("key", "value"));

            // when / then
            StepVerifier.create(testSubject.publish(eventMessage))
                        .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
            verify(mockEventGateway).publish(captor.capture());
            List<Object> result = captor.getValue();
            assertThat(result).hasSize(1);
            // EventMessage passed through should retain its identity
            EventMessage published = (EventMessage) result.getFirst();
            assertThat(published.payload()).isEqualTo(payload);
            assertThat(published.metadata().get("key")).isEqualTo("value");
        }

        @Test
        void publishNoEvents() {
            // given
            ArrayList<Object> noEvents = new ArrayList<>();

            // when / then
            StepVerifier.create(testSubject.publish(noEvents))
                        .verifyComplete();

            // The delegate is still called, but with an empty list
            // (reactive gateway collects into a list before delegating)
        }

        @Test
        void publishVarargsCallsPublishList() {
            // when / then
            StepVerifier.create(testSubject.publish("Event1", "Event2", "Event3"))
                        .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
            verify(mockEventGateway).publish(captor.capture());
            assertThat(captor.getValue()).hasSize(3);
        }

        @Test
        void resolvedMessageTypeIsCorrect() {
            // when / then
            StepVerifier.create(testSubject.publish("Event1"))
                        .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
            verify(mockEventGateway).publish(captor.capture());
            EventMessage published = (EventMessage) captor.getValue().getFirst();
            assertThat(published.type().qualifiedName().name()).isEqualTo("java.lang.String");
        }
    }

    @Nested
    class Interceptors {

        @Test
        void interceptorEnrichesMetadata() {
            // given
            ReactiveMessageDispatchInterceptor<EventMessage> interceptor = (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("enriched", "true"));
                return chain.proceed(enriched);
            };
            testSubject.registerDispatchInterceptor(interceptor);

            // when / then
            StepVerifier.create(testSubject.publish("Event1"))
                        .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
            verify(mockEventGateway).publish(captor.capture());
            EventMessage published = (EventMessage) captor.getValue().getFirst();
            assertThat(published.metadata().get("enriched")).isEqualTo("true");
        }

        @Test
        void interceptorsRunInRegistrationOrder() {
            // given
            ReactiveMessageDispatchInterceptor<EventMessage> first = (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("order", "1"));
                return chain.proceed(enriched);
            };
            ReactiveMessageDispatchInterceptor<EventMessage> second = (message, chain) -> {
                // second interceptor sees "1" from first and overwrites with "2"
                var enriched = message.andMetadata(Metadata.with("order", "2"));
                return chain.proceed(enriched);
            };
            testSubject.registerDispatchInterceptor(first);
            testSubject.registerDispatchInterceptor(second);

            // when / then
            StepVerifier.create(testSubject.publish("Event1"))
                        .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Object>> captor = ArgumentCaptor.forClass(List.class);
            verify(mockEventGateway).publish(captor.capture());
            EventMessage published = (EventMessage) captor.getValue().getFirst();
            // second interceptor ran last, so its value wins
            assertThat(published.metadata().get("order")).isEqualTo("2");
        }

        @Test
        void interceptorCanRejectMessage() {
            // given
            ReactiveMessageDispatchInterceptor<EventMessage> rejecting = (message, chain) ->
                    reactor.core.publisher.Mono.error(new IllegalArgumentException("rejected"));
            testSubject.registerDispatchInterceptor(rejecting);

            // when / then
            StepVerifier.create(testSubject.publish("Event1"))
                        .expectError(IllegalArgumentException.class)
                        .verify();
        }
    }

    @Nested
    class BuilderValidation {

        @Test
        void rejectsNullEventGateway() {
            assertThatThrownBy(() ->
                    DefaultReactiveEventGateway.builder().eventGateway(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullMessageTypeResolver() {
            assertThatThrownBy(() ->
                    DefaultReactiveEventGateway.builder().messageTypeResolver(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBuildWithoutEventGateway() {
            assertThatThrownBy(() ->
                    DefaultReactiveEventGateway.builder()
                            .messageTypeResolver(new ClassBasedMessageTypeResolver())
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBuildWithoutMessageTypeResolver() {
            assertThatThrownBy(() ->
                    DefaultReactiveEventGateway.builder()
                            .eventGateway(mockEventGateway)
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }
    }

    private record TestPayload(String value) {

    }
}
