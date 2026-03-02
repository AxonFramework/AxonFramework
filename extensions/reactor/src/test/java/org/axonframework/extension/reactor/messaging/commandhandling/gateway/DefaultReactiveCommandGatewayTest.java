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

package org.axonframework.extension.reactor.messaging.commandhandling.gateway;

import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandResult;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link DefaultReactiveCommandGateway}.
 */
class DefaultReactiveCommandGatewayTest {

    private static final MessageTypeResolver TEST_MESSAGE_TYPE_RESOLVER =
            payloadType -> Optional.of(new MessageType(payloadType.getSimpleName()));

    private CommandGateway mockCommandGateway;

    private DefaultReactiveCommandGateway testSubject;

    @BeforeEach
    void setUp() {
        mockCommandGateway = mock(CommandGateway.class);
        testSubject = DefaultReactiveCommandGateway.builder()
                .commandGateway(mockCommandGateway)
                .messageTypeResolver(TEST_MESSAGE_TYPE_RESOLVER)
                .build();
    }

    @Nested
    class Send {

        @Test
        void wrapsObjectIntoCommandMessageAndReturnsTypedResult() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("OK"));
            when(mockCommandGateway.send(any())).thenReturn(commandResult);

            // when / then
            TestPayload payload = new TestPayload();
            StepVerifier.create(testSubject.send(payload, String.class))
                        .expectNext("OK")
                        .verifyComplete();

            ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(mockCommandGateway).send(captor.capture());
            assertThat(captor.getValue().payload()).isEqualTo(payload);
        }

        @Test
        void resolvesMessageTypeUsingMessageTypeResolver() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("OK"));
            when(mockCommandGateway.send(any())).thenReturn(commandResult);

            // when
            TestPayload payload = new TestPayload();
            StepVerifier.create(testSubject.send(payload, String.class))
                        .expectNext("OK")
                        .verifyComplete();

            // then
            var expectedMessageType = new MessageType("TestPayload");
            ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(mockCommandGateway).send(captor.capture());
            assertThat(captor.getValue().type()).isEqualTo(expectedMessageType);
        }

        @Test
        void passesCommandMessageAsIsPreservingRoutingKeyAndPriority() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("OK"));
            when(mockCommandGateway.send(any())).thenReturn(commandResult);

            CommandMessage commandMessage = new GenericCommandMessage(
                    new MessageType("CustomCommand"), "payload",
                    Metadata.with("pre", "existing"), "myRoutingKey", 42
            );

            // when
            StepVerifier.create(testSubject.send(commandMessage, String.class))
                        .expectNext("OK")
                        .verifyComplete();

            // then
            ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(mockCommandGateway).send(captor.capture());
            CommandMessage dispatched = captor.getValue();
            assertThat(dispatched.payload()).isEqualTo("payload");
            assertThat(dispatched.type()).isEqualTo(new MessageType("CustomCommand"));
            assertThat(dispatched.metadata().get("pre")).isEqualTo("existing");
            assertThat(dispatched.routingKey()).hasValue("myRoutingKey");
            assertThat(dispatched.priority()).hasValue(42);
        }

        @Test
        void returnsExceptionalMonoWhenCommandGatewayCompletesExceptionally() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("oops")));
            when(mockCommandGateway.send(any())).thenReturn(commandResult);

            // when / then
            StepVerifier.create(testSubject.send(new TestPayload(), String.class))
                        .expectErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"))
                        .verify();
        }
    }

    @Nested
    class SendVoid {

        @Test
        void delegatesAndCompletesEmpty() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.getResultMessage())
                    .thenAnswer(inv -> CompletableFuture.completedFuture(
                            new GenericCommandResultMessage(new MessageType("result"), null)
                    ));
            when(mockCommandGateway.send(any())).thenReturn(commandResult);

            // when / then
            StepVerifier.create(testSubject.send(new TestPayload()))
                        .verifyComplete();

            verify(mockCommandGateway).send(any());
        }

        @Test
        void returnsExceptionalMonoWhenCommandGatewayFails() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.getResultMessage())
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
            when(mockCommandGateway.send(any())).thenReturn(commandResult);

            // when / then
            StepVerifier.create(testSubject.send(new TestPayload()))
                        .expectError(RuntimeException.class)
                        .verify();
        }
    }

    @Nested
    class Interceptors {

        @Test
        void interceptorsRunInRegistrationOrderAndCanEnrichMetadata() {
            // given
            ReactiveMessageDispatchInterceptor<CommandMessage> first = (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("key1", "value1"));
                return chain.proceed(enriched);
            };
            ReactiveMessageDispatchInterceptor<CommandMessage> second = (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("key2", "value2"));
                return chain.proceed(enriched);
            };

            testSubject.registerDispatchInterceptor(first);
            testSubject.registerDispatchInterceptor(second);

            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("ok"));
            when(mockCommandGateway.send(any())).thenReturn(commandResult);

            // when
            StepVerifier.create(testSubject.send(new TestPayload(), String.class))
                        .expectNext("ok")
                        .verifyComplete();

            // then
            ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(mockCommandGateway).send(captor.capture());
            assertThat(captor.getValue().metadata().get("key1")).isEqualTo("value1");
            assertThat(captor.getValue().metadata().get("key2")).isEqualTo("value2");
        }

        @Test
        void interceptorCanRejectMessage() {
            // given
            ReactiveMessageDispatchInterceptor<CommandMessage> rejecting = (message, chain) ->
                    reactor.core.publisher.Mono.error(new IllegalArgumentException("rejected"));
            testSubject.registerDispatchInterceptor(rejecting);

            // when / then
            StepVerifier.create(testSubject.send(new TestPayload(), String.class))
                        .expectError(IllegalArgumentException.class)
                        .verify();
        }
    }

    @Nested
    class BuilderValidation {

        @Test
        void rejectsNullCommandGateway() {
            assertThatThrownBy(() ->
                    DefaultReactiveCommandGateway.builder().commandGateway(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullMessageTypeResolver() {
            assertThatThrownBy(() ->
                    DefaultReactiveCommandGateway.builder().messageTypeResolver(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBuildWithoutCommandGateway() {
            assertThatThrownBy(() ->
                    DefaultReactiveCommandGateway.builder()
                            .messageTypeResolver(TEST_MESSAGE_TYPE_RESOLVER)
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBuildWithoutMessageTypeResolver() {
            assertThatThrownBy(() ->
                    DefaultReactiveCommandGateway.builder()
                            .commandGateway(mockCommandGateway)
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }
    }

    private static class TestPayload {

    }
}
