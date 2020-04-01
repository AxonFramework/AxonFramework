/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReactiveCommandGateway}.
 *
 * @author Milan Savic
 */
class DefaultReactiveCommandGatewayTest {

    private DefaultReactiveCommandGateway reactiveCommandGateway;
    private MessageHandler<CommandMessage<?>> commandMessageHandler;
    private RetryScheduler mockRetryScheduler;

    @BeforeEach
    void setUp() {
        CommandBus commandBus = SimpleCommandBus.builder().build();
        mockRetryScheduler = mock(RetryScheduler.class);
        commandMessageHandler = spy(new MessageHandler<CommandMessage<?>>() {
            @Override
            public Object handle(CommandMessage<?> message) {
                return "handled";
            }
        });
        commandBus.subscribe(String.class.getName(), commandMessageHandler);
        commandBus.subscribe(Integer.class.getName(), message -> {
            throw new RuntimeException();
        });
        commandBus.subscribe(Boolean.class.getName(),
                             message -> "" + message.getMetaData().getOrDefault("key1", "")
                                     + message.getMetaData().getOrDefault("key2", ""));
        reactiveCommandGateway = DefaultReactiveCommandGateway.builder()
                                                              .commandBus(commandBus)
                                                              .retryScheduler(mockRetryScheduler)
                                                              .build();
    }

    @Test
    void testSend() throws Exception {
        Mono<String> result = reactiveCommandGateway.send("command");
        verifyZeroInteractions(commandMessageHandler);
        StepVerifier.create(result)
                    .expectNext("handled")
                    .verifyComplete();
        verify(commandMessageHandler).handle(any());
        verifyZeroInteractions(mockRetryScheduler);
    }

    @Test
    void testSendFails() {
        StepVerifier.create(reactiveCommandGateway.send(5))
                    .verifyError(RuntimeException.class);
        verify(mockRetryScheduler).scheduleRetry(any(), any(), anyList(), any());
    }

    @Test
    void testSendWithDispatchInterceptor() {
        reactiveCommandGateway
                .registerCommandDispatchInterceptor(() -> cmdMono -> cmdMono
                        .map(cmd -> cmd.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveCommandGateway
                .registerCommandDispatchInterceptor(() -> cmdMono -> cmdMono
                        .map(cmd -> cmd.andMetaData(Collections.singletonMap("key2", "value2"))));

        StepVerifier.create(reactiveCommandGateway.send(true))
                    .expectNext("value1value2")
                    .verifyComplete();

        registration2.cancel();

        StepVerifier.create(reactiveCommandGateway.send(true))
                    .expectNext("value1")
                    .verifyComplete();
    }

    @Test
    void testDispatchInterceptorThrowingAnException() {
        reactiveCommandGateway
                .registerCommandDispatchInterceptor(() -> cmdMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testDispatchInterceptorReturningErrorMono() {
        reactiveCommandGateway
                .registerCommandDispatchInterceptor(() -> cmdMono -> Mono.error(new RuntimeException()));
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyError(RuntimeException.class);
    }
}
