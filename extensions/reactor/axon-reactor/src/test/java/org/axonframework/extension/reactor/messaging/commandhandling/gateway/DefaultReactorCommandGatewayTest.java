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

import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandResult;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link DefaultReactorCommandGateway}.
 *
 * @author Theo Emanuelsson
 */
class DefaultReactorCommandGatewayTest {

    private static final MessageTypeResolver TEST_MESSAGE_TYPE_RESOLVER =
            payloadType -> Optional.of(new MessageType(payloadType.getSimpleName()));

    private CommandGateway mockCommandGateway;

    private DefaultReactorCommandGateway testSubject;

    @BeforeEach
    void setUp() {
        mockCommandGateway = mock(CommandGateway.class);
        testSubject = new DefaultReactorCommandGateway(mockCommandGateway, TEST_MESSAGE_TYPE_RESOLVER);
    }

    @Nested
    class Send {

        @Test
        void wrapsObjectIntoCommandMessageAndReturnsTypedResult() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("OK"));
            when(mockCommandGateway.send(any(), (ProcessingContext) isNull())).thenReturn(commandResult);

            // when / then
            TestPayload payload = new TestPayload();
            StepVerifier.create(testSubject.send(payload, String.class))
                        .expectNext("OK")
                        .verifyComplete();

            ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(mockCommandGateway).send(captor.capture(), (ProcessingContext) isNull());
            assertThat(captor.getValue().payload()).isEqualTo(payload);
        }

        @Test
        void resolvesMessageTypeUsingMessageTypeResolver() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("OK"));
            when(mockCommandGateway.send(any(), (ProcessingContext) isNull())).thenReturn(commandResult);

            // when
            TestPayload payload = new TestPayload();
            StepVerifier.create(testSubject.send(payload, String.class))
                        .expectNext("OK")
                        .verifyComplete();

            // then
            var expectedMessageType = new MessageType("TestPayload");
            ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(mockCommandGateway).send(captor.capture(), (ProcessingContext) isNull());
            assertThat(captor.getValue().type()).isEqualTo(expectedMessageType);
        }

        @Test
        void passesCommandMessageAsIsPreservingRoutingKeyAndPriority() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("OK"));
            when(mockCommandGateway.send(any(), (ProcessingContext) isNull())).thenReturn(commandResult);

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
            verify(mockCommandGateway).send(captor.capture(), (ProcessingContext) isNull());
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
            when(mockCommandGateway.send(any(), (ProcessingContext) isNull())).thenReturn(commandResult);

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
            when(mockCommandGateway.send(any(), (ProcessingContext) isNull())).thenReturn(commandResult);

            // when / then
            StepVerifier.create(testSubject.send(new TestPayload()))
                        .verifyComplete();

            verify(mockCommandGateway).send(any(), (ProcessingContext) isNull());
        }

        @Test
        void returnsExceptionalMonoWhenCommandGatewayFails() {
            // given
            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.getResultMessage())
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
            when(mockCommandGateway.send(any(), (ProcessingContext) isNull())).thenReturn(commandResult);

            // when / then
            StepVerifier.create(testSubject.send(new TestPayload()))
                        .expectError(RuntimeException.class)
                        .verify();
        }
    }

    @Nested
    class Interceptors {

        @Test
        void dispatchInterceptorsInvokedInOrder() {
            // given — each interceptor records the current metadata size, proving execution order
            ReactorMessageDispatchInterceptor<CommandMessage> first = (message, context, chain) -> {
                var enriched = message.andMetadata(Metadata.with("first", "value-" + message.metadata().size()));
                return chain.proceed(enriched, context);
            };
            ReactorMessageDispatchInterceptor<CommandMessage> second = (message, context, chain) -> {
                var enriched = message.andMetadata(Metadata.with("second", "value-" + message.metadata().size()));
                return chain.proceed(enriched, context);
            };

            testSubject = new DefaultReactorCommandGateway(
                    mockCommandGateway, TEST_MESSAGE_TYPE_RESOLVER, List.of(first, second)
            );

            CommandResult commandResult = mock(CommandResult.class);
            when(commandResult.resultAs(String.class))
                    .thenReturn(CompletableFuture.completedFuture("ok"));
            when(mockCommandGateway.send(any(), (ProcessingContext) isNull())).thenReturn(commandResult);

            // when
            StepVerifier.create(testSubject.send(new TestPayload(), String.class))
                        .expectNext("ok")
                        .verifyComplete();

            // then — first ran with 0 existing metadata entries, second ran with 1
            ArgumentCaptor<CommandMessage> captor = ArgumentCaptor.forClass(CommandMessage.class);
            verify(mockCommandGateway).send(captor.capture(), (ProcessingContext) isNull());
            assertThat(captor.getValue().metadata().get("first")).isEqualTo("value-0");
            assertThat(captor.getValue().metadata().get("second")).isEqualTo("value-1");
        }

        @Test
        void interceptorCanRejectMessage() {
            // given
            ReactorMessageDispatchInterceptor<CommandMessage> rejecting = (message, context, chain) ->
                    Mono.error(new IllegalArgumentException("rejected"));
            testSubject = new DefaultReactorCommandGateway(
                    mockCommandGateway, TEST_MESSAGE_TYPE_RESOLVER, List.of(rejecting)
            );

            // when / then
            StepVerifier.create(testSubject.send(new TestPayload(), String.class))
                        .expectError(IllegalArgumentException.class)
                        .verify();
        }
    }

    @Nested
    class ConstructorValidation {

        @Test
        void rejectsNullCommandGateway() {
            assertThatThrownBy(() ->
                    new DefaultReactorCommandGateway(null, TEST_MESSAGE_TYPE_RESOLVER)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullMessageTypeResolver() {
            assertThatThrownBy(() ->
                    new DefaultReactorCommandGateway(mockCommandGateway, null)
            ).isInstanceOf(NullPointerException.class);
        }
    }

    private static class TestPayload {

    }
}
