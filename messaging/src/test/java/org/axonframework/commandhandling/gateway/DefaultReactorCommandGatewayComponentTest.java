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

import org.axonframework.commandhandling.AsynchronousCommandBus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultReactorCommandGateway} all together with Command Bus.
 *
 * @author Milan Savic
 */
class DefaultReactorCommandGatewayComponentTest {

    private DefaultReactorCommandGateway reactiveCommandGateway;
    private MessageHandler<CommandMessage<?>> commandMessageHandler;
    private MessageHandler<CommandMessage<?>> failingCommandHandler;
    private MessageHandler<CommandMessage<?>> voidCommandHandler;
    private RetryScheduler mockRetryScheduler;
    private CommandBus commandBus;

    @BeforeEach
    void setUp() {
        Hooks.enableContextLossTracking();
        Hooks.onOperatorDebug();

        commandBus = spy(AsynchronousCommandBus.builder().build());

        mockRetryScheduler = mock(RetryScheduler.class);
        commandMessageHandler = spy(new MessageHandler<CommandMessage<?>>() {

            private final AtomicInteger count = new AtomicInteger();

            @Override
            public Object handle(CommandMessage<?> message) {
                if ("backpressure".equals(message.getPayload())) {
                    return count.incrementAndGet();
                }
                return "handled";
            }
        });
        commandBus.subscribe(String.class.getName(), commandMessageHandler);
        failingCommandHandler = spy(new MessageHandler<CommandMessage<?>>() {
            @Override
            public Object handle(CommandMessage<?> message) throws Exception {
                throw new RuntimeException();
            }
        });

        voidCommandHandler = spy(new MessageHandler<CommandMessage<?>>() {
            @Override
            public Object handle(CommandMessage<?> message) throws Exception {
                return null;
            }
        });

        commandBus.subscribe(Integer.class.getName(), failingCommandHandler);
        commandBus.subscribe(Boolean.class.getName(),
                             message -> "" + message.getMetaData().getOrDefault("key1", "")
                                     + message.getMetaData().getOrDefault("key2", ""));
        commandBus.subscribe(Long.class.getName(), voidCommandHandler);
        reactiveCommandGateway = DefaultReactorCommandGateway.builder()
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
    void testSendContext() throws Exception {
        Mono<String> result = reactiveCommandGateway.send("command");
        verifyZeroInteractions(commandMessageHandler);

        Context context = Context.of("k1", "v1");

        StepVerifier.create(result.subscriberContext(context))
                .expectNext("handled")
                .expectAccessibleContext()
                .containsOnly(context)
                .then()
                .verifyComplete();
        verify(commandMessageHandler).handle(any());
        verifyZeroInteractions(mockRetryScheduler);
    }

    @Test
    void testSendVoidHandler() throws Exception {
        Mono<String> result = reactiveCommandGateway.send(1L);
        verifyZeroInteractions(voidCommandHandler);
        StepVerifier.create(result)
                .expectComplete()
                .verify();
        verify(voidCommandHandler).handle(any());
        verifyZeroInteractions(mockRetryScheduler);
    }


    @Test
    void testSendAll() throws Exception {
        Flux<Object> commands = Flux.fromIterable(Arrays.asList("command1", 4, "command2", 5, true));

        Flux<Object> result = reactiveCommandGateway.sendAll(commands);
        verifyZeroInteractions(commandMessageHandler);

        List<Throwable> exceptions = new ArrayList<>(2);
        StepVerifier.create(result.onErrorContinue((t, o) -> exceptions.add(t)))
                    .expectNext("handled", "handled", "")
                    .verifyComplete();

        assertEquals(2, exceptions.size());
        assertTrue(exceptions.get(0) instanceof RuntimeException);
        assertTrue(exceptions.get(1) instanceof RuntimeException);
        verify(commandMessageHandler, times(2)).handle(any());
    }

    @Test
    void testSendAllOrdering() throws Exception {
        int numberOfCommands = 10_000;
        Flux<String> commands = Flux.fromStream(IntStream.range(0, numberOfCommands)
                                                         .mapToObj(i -> "backpressure"));
        Flux<Object> result = reactiveCommandGateway.sendAll(commands);
        StepVerifier.create(result)
                    .expectNext(IntStream.range(1, numberOfCommands + 1)
                                         .boxed().toArray(Integer[]::new))
                    .verifyComplete();
        verify(commandMessageHandler, times(numberOfCommands)).handle(any());
    }

    @Test
    void testDispatchFails() throws Exception {
        StepVerifier.create(reactiveCommandGateway.send(5)
                                                  .retry(5))
                    .verifyError(RuntimeException.class);
        verify(mockRetryScheduler, times(6)).scheduleRetry(any(), any(), anyList(), any());
        verify(failingCommandHandler, times(6)).handle(any());
    }

    @Test
    void testSendWithDispatchInterceptor() {
        reactiveCommandGateway
                .registerDispatchInterceptor(cmdMono -> cmdMono
                        .map(cmd -> cmd.andMetaData(Collections.singletonMap("key1", "value1"))));
        Registration registration2 = reactiveCommandGateway
                .registerDispatchInterceptor(cmdMono -> cmdMono
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
    void testSendWithDispatchInterceptorWithContext() {
        Context context = Context.of("security", true);

        reactiveCommandGateway
                .registerDispatchInterceptor(cmdMono -> cmdMono
                        .filterWhen(v-> Mono.subscriberContext()
                                .filter(it-> it.hasKey("security"))
                                .map(it->it.get("security")))
                        .map(cmd -> cmd.andMetaData(Collections.singletonMap("key1", "value1")))
                );

        StepVerifier.create(reactiveCommandGateway.send(true)
                .subscriberContext(context))
                .expectNext("value1")
                .expectAccessibleContext()
                .containsOnly(context)
                .then()
                .verifyComplete();
    }

    @Test
    void testSendWithDispatchInterceptorWithContextFiltered() {
        Context context = Context.of("security", false);

        reactiveCommandGateway
                .registerDispatchInterceptor(cmdMono -> cmdMono
                        .filterWhen(v-> Mono.subscriberContext()
                                .filter(it-> it.hasKey("security"))
                                .map(it->it.get("security")))
                        .map(cmd -> cmd.andMetaData(Collections.singletonMap("key1", "value1")))
                );

        StepVerifier.create(reactiveCommandGateway.send(true).subscriberContext(context))
                .expectComplete()
                .verify();
    }

    @Test
    void testDispatchInterceptorThrowingAnException() {
        reactiveCommandGateway
                .registerDispatchInterceptor(cmdMono -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyError(RuntimeException.class);
        verify(commandBus, times(0)).dispatch(any());
        verify(commandBus, times(0)).dispatch(any(), any());
    }

    @Test
    void testDispatchInterceptorReturningErrorMono() {
        reactiveCommandGateway
                .registerDispatchInterceptor(cmdMono -> Mono.error(new RuntimeException()));
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyError(RuntimeException.class);
        verify(commandBus, times(0)).dispatch(any());
        verify(commandBus, times(0)).dispatch(any(), any());
    }

    @Test
    void testDispatchInterceptorStoppingTheFlow() {
        reactiveCommandGateway
                .registerDispatchInterceptor(cmdMono -> Mono.empty());
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyComplete();
        verify(commandBus, times(0)).dispatch(any());
        verify(commandBus, times(0)).dispatch(any(), any());
    }

    @Test
    void testSendWithResultInterceptor() {
        reactiveCommandGateway
                .registerResultHandlerInterceptor((command, results) -> results
                        .map(r -> new GenericCommandResultMessage<Object>(r.getPayload() + "value1")));
        Registration registration2 = reactiveCommandGateway
                .registerResultHandlerInterceptor((command, results) -> results
                        .map(r -> new GenericCommandResultMessage<Object>(r.getPayload() + "value2")));

        StepVerifier.create(reactiveCommandGateway.send(true))
                    .expectNext("value1value2")
                    .verifyComplete();

        registration2.cancel();

        StepVerifier.create(reactiveCommandGateway.send(true))
                    .expectNext("value1")
                    .verifyComplete();
    }

    @Test
    void testResultHandlerInterceptorThrowingAnException() {
        reactiveCommandGateway
                .registerResultHandlerInterceptor((command, results) -> {
                    throw new RuntimeException();
                });
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyError(RuntimeException.class);
        verify(commandBus, times(1)).dispatch(any(), any());
    }

    @Test
    void testResultHandlerInterceptorReturningErrorFlux() {
        reactiveCommandGateway
                .registerResultHandlerInterceptor((command, results) -> Flux.error(new RuntimeException()));
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyError(RuntimeException.class);
        verify(commandBus, times(1)).dispatch(any(), any());
    }

    @Test
    void testResultHandlerInterceptorStoppingTheFlow() {
        reactiveCommandGateway
                .registerResultHandlerInterceptor((command, results) -> Flux.empty());
        StepVerifier.create(reactiveCommandGateway.send(true))
                    .verifyComplete();
        verify(commandBus, times(1)).dispatch(any(), any());
    }
}
