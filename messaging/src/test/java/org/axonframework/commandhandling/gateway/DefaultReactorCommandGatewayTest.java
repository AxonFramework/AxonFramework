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
import org.axonframework.messaging.MessageHandler;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReactorCommandGateway}.
 *
 * @author Milan Savic
 */
class DefaultReactorCommandGatewayTest {

    private DefaultReactorCommandGateway reactiveCommandGateway;
    private MessageHandler<CommandMessage<?>> commandMessageHandler;
    private RetryScheduler mockRetryScheduler;

    @BeforeEach
    void setUp() {
        CommandBus commandBus = AsynchronousCommandBus.builder().build();


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
        commandBus.subscribe(Integer.class.getName(), message -> {
            throw new RuntimeException();
        });
        commandBus.subscribe(Boolean.class.getName(),
                             message -> "" + message.getMetaData().getOrDefault("key1", "")
                                     + message.getMetaData().getOrDefault("key2", ""));
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
        List<Integer> expectedResults = IntStream.range(1, numberOfCommands + 1)
                                                 .boxed()
                                                 .collect(toList());
        Flux<Object> result = reactiveCommandGateway.sendAll(commands);
        StepVerifier.create(result)
                    .expectNext(expectedResults.toArray(new Integer[0]))
                    .verifyComplete();
        verify(commandMessageHandler, times(numberOfCommands)).handle(any());
    }

    @Test
    void testSendFails() {
        StepVerifier.create(reactiveCommandGateway.send(5))
                    .verifyError(RuntimeException.class);
        verify(mockRetryScheduler).scheduleRetry(any(), any(), anyList(), any());
    }

//    @Test
//    void testSendWithDispatchInterceptor() {
//        reactiveCommandGateway
//                .registerDispatchInterceptor(() -> cmdMono -> cmdMono
//                        .map(cmd -> cmd.andMetaData(Collections.singletonMap("key1", "value1"))));
//        Registration registration2 = reactiveCommandGateway
//                .registerDispatchInterceptor(() -> cmdMono -> cmdMono
//                        .map(cmd -> cmd.andMetaData(Collections.singletonMap("key2", "value2"))));
//
//        StepVerifier.create(reactiveCommandGateway.send(true))
//                    .expectNext("value1value2")
//                    .verifyComplete();
//
//        registration2.cancel();
//
//        StepVerifier.create(reactiveCommandGateway.send(true))
//                    .expectNext("value1")
//                    .verifyComplete();
//    }

//    @Test
//    void testDispatchInterceptorThrowingAnException() {
//        reactiveCommandGateway
//                .registerDispatchInterceptor(() -> cmdMono -> {
//                    throw new RuntimeException();
//                });
//        StepVerifier.create(reactiveCommandGateway.send(true))
//                    .verifyError(RuntimeException.class);
//    }
//
//    @Test
//    void testDispatchInterceptorReturningErrorMono() {
//        reactiveCommandGateway
//                .registerDispatchInterceptor(() -> cmdMono -> Mono.error(new RuntimeException()));
//        StepVerifier.create(reactiveCommandGateway.send(true))
//                    .verifyError(RuntimeException.class);
//    }
}
