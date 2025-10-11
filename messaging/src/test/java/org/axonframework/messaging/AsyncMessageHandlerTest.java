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

package org.axonframework.messaging;

import org.awaitility.Awaitility;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandBusTestUtils;
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.annotations.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotations.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.annotations.AnnotatedEventHandlingComponent;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.annotations.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusTestUtils;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryPriorityCalculator;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;
import org.axonframework.queryhandling.annotations.QueryHandler;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncMessageHandlerTest {

    private static final ParameterResolverFactory PARAMETER_RESOLVER_FACTORY = new DefaultParameterResolverFactory();

    private final CommandBus commandBus = CommandBusTestUtils.aCommandBus();
    private final CommandGateway commandGateway = new DefaultCommandGateway(commandBus,
                                                                            new ClassBasedMessageTypeResolver(),
                                                                            CommandPriorityCalculator.defaultCalculator(),
                                                                            new AnnotationRoutingStrategy());
    private final QueryBus queryBus = QueryBusTestUtils.aQueryBus();
    private final QueryGateway queryGateway = new DefaultQueryGateway(queryBus,
                                                                      new ClassBasedMessageTypeResolver(),
                                                                      QueryPriorityCalculator.defaultCalculator());
    private final EventBus eventBus = new SimpleEventBus();  // TODO #3392 - Replace for actual EventSink implementation.
    private final AtomicBoolean eventHandlerCalled = new AtomicBoolean();

    record CheckIfPrime(int value) {
        // command
    }

    record GetKnownPrimes() {
        // query
    }

    record EchoValue(int... values) {
        // query
    }

    record PrimeChecked(int value) {
        // event
    }

    @Nested
    class AnnotationBased {

        @Nested
        class EventHandlers {

            @Test
            void withVoidReturnTypeShouldBeCalled() {
                var ehc = new AnnotatedEventHandlingComponent<>(new VoidEventHandler(), PARAMETER_RESOLVER_FACTORY);
                ProcessingContext testContext =
                        StubProcessingContext.withComponent(EventConverter.class, PassThroughConverter.EVENT_INSTANCE);

                eventBus.subscribe((messages, context) -> {
                    messages.forEach(m -> ehc.handle(m, testContext));
                    return CompletableFuture.completedFuture(null);
                });

                assertEvents();
            }

            @Test
            void returningMonoShouldExecuteIt() {
                var ehc = new AnnotatedEventHandlingComponent<>(new MonoEventHandler(), PARAMETER_RESOLVER_FACTORY);
                ProcessingContext testContext =
                        StubProcessingContext.withComponent(EventConverter.class, PassThroughConverter.EVENT_INSTANCE);

                eventBus.subscribe((messages, context) -> {
                    messages.forEach(m -> ehc.handle(m, testContext));
                    return CompletableFuture.completedFuture(null);
                });

                assertEvents();
            }

            @Test
            void returningCompletableFutureShouldExecuteIt() {
                var ehc = new AnnotatedEventHandlingComponent<>(new CompletableFutureEventHandler(),
                                                                PARAMETER_RESOLVER_FACTORY);
                ProcessingContext testContext =
                        StubProcessingContext.withComponent(EventConverter.class, PassThroughConverter.EVENT_INSTANCE);

                eventBus.subscribe((messages, context) -> {
                    messages.forEach(m -> ehc.handle(m, testContext));
                    return CompletableFuture.completedFuture(null);
                });

                assertEvents();
            }
        }

        @Nested
        class CommandHandlers {

            @Test
            void returningCompletableFutureShouldUseItsResult() {
                commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(new CompletableFutureCommandHandler(),
                                                                             PassThroughConverter.MESSAGE_INSTANCE));

                assertCommands();
            }

            @Test
            void returningMonoShouldUseItsResult() {
                commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(new MonoCommandHandler(),
                                                                             PassThroughConverter.MESSAGE_INSTANCE));

                assertCommands();
            }

            @Test
            void returningBooleanShouldUseResult() {
                commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(new BooleanCommandHandler(),
                                                                             PassThroughConverter.MESSAGE_INSTANCE));

                assertCommands();
            }
        }

        @Nested
        class QueryHandlers {

            @Test
            void returningJustShouldUseResult() {
                int echoInt = 1;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new JustQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<Integer> result = queryGateway.query(new EchoValue(echoInt), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).isEqualTo(echoInt);
            }

            @Test
            void returningPrimitiveShouldUseResult() {
                int echoInt = 1;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new PrimitiveQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<Integer> result = queryGateway.query(new EchoValue(echoInt), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).isEqualTo(echoInt);
            }

            @Test
            void returningOptionalShouldUseResult() {
                int echoInt = 1;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new OptionalQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<Integer> result = queryGateway.query(new EchoValue(echoInt), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).isEqualTo(echoInt);
            }

            @Test
            void returningCompletableFutureShouldUseItsResult() {
                int echoInt = 1;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new CompletableFutureQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<Integer> result = queryGateway.query(new EchoValue(echoInt), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).isEqualTo(echoInt);
            }

            @Test
            void returningMonoShouldUseItsResult() {
                int echoInt = 1;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new MonoQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<Integer> result = queryGateway.query(new EchoValue(echoInt), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).isEqualTo(echoInt);
            }

            @Test
            void returningIterableShouldUseItsResult() {
                int echoIntOne = 1;
                int echoIntTwo = 2;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new IterableQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<List<Integer>> result =
                        queryGateway.queryMany(new EchoValue(echoIntOne, echoIntTwo), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).contains(echoIntOne, echoIntTwo);
            }

            @Test
            void returningStreamShouldUseItsResult() {
                int echoIntOne = 1;
                int echoIntTwo = 2;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new StreamQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<List<Integer>> result =
                        queryGateway.queryMany(new EchoValue(echoIntOne, echoIntTwo), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).contains(echoIntOne, echoIntTwo);
            }

            @Test
            void returningFluxShouldUseItsResult() {
                int echoIntOne = 1;
                int echoIntTwo = 2;
                queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new FluxQueryHandler(),
                                                                         PassThroughConverter.MESSAGE_INSTANCE));

                CompletableFuture<List<Integer>> result =
                        queryGateway.queryMany(new EchoValue(echoIntOne, echoIntTwo), Integer.class, null);
                assertThat(result).isDone();
                assertThat(result.join()).contains(echoIntOne, echoIntTwo);
            }
        }
    }

    @Nested
    class Declarative {

        @Nested
        class EventHandlers {
            // TODO #3392 - Once EventSink is created, can adds declartive tests here which subscribe with a QualifiedName
        }

        @Nested
        class CommandHandlers {

            @Test
            void returningCompletableFutureShouldUseItsResult() {
                commandBus.subscribe(
                        new QualifiedName(CheckIfPrime.class.getName()),
                        (command, context) -> {
                            CommandResultMessage value = new GenericCommandResultMessage(
                                    null, isPrime(((CheckIfPrime) command.payload()).value())
                            );

                            return MessageStream.fromFuture(CompletableFuture.completedFuture(value));
                        }
                );

                assertCommands();
            }

            @Test
            void returningMonoShouldUseItsResult() {
                commandBus.subscribe(
                        new QualifiedName(CheckIfPrime.class.getName()),
                        (command, context) -> {
                            CommandResultMessage data = new GenericCommandResultMessage(
                                    null, isPrime(((CheckIfPrime) command.payload()).value())
                            );

                            return MessageStream.fromMono(Mono.just(data));
                        }
                );

                assertCommands();
            }

            @Test
            void returningBooleanShouldUseResult() {
                commandBus.subscribe(
                        new QualifiedName(CheckIfPrime.class.getName()),
                        (command, context) -> MessageStream.just(new GenericCommandResultMessage(
                                null, isPrime(((CheckIfPrime) command.payload()).value())
                        ))
                );

                assertCommands();
            }
        }

        @Nested
        class QueryHandlers {

            @Test
            void declarativeQueryHandlerShouldUseFluxReturnType() throws Exception {
                queryBus.subscribe(
                        new QualifiedName(GetKnownPrimes.class),
                        new QualifiedName(Integer.class),
                        (query, context) -> MessageStream.fromFlux(
                                Flux.just(2, 3, 5, 7)
                                    .map(i -> new GenericQueryResponseMessage(new MessageType(Integer.class), i))
                        )
                );

                assertQuery();
            }

            @Test
            void declarativeQueryHandlerShouldUseIterableReturnType() throws Exception {
                queryBus.subscribe(
                        new QualifiedName(GetKnownPrimes.class),
                        new QualifiedName(Integer.class),
                        (query, context) -> MessageStream.fromIterable(List.of(
                                new GenericQueryResponseMessage(new MessageType(Integer.class), 2),
                                new GenericQueryResponseMessage(new MessageType(Integer.class), 3),
                                new GenericQueryResponseMessage(new MessageType(Integer.class), 5),
                                new GenericQueryResponseMessage(new MessageType(Integer.class), 7)
                        ))
                );

                assertQuery();
            }
        }
    }

    private void assertEvents() {
        eventBus.publish(null, new GenericEventMessage(new MessageType(PrimeChecked.class), new PrimeChecked(5)));

        Awaitility.await().untilAsserted(() -> assertThat(eventHandlerCalled).isTrue());
    }

    private void assertCommands() {
        assertThat(commandGateway.sendAndWait(new CheckIfPrime(2), Boolean.class)).isTrue();
        assertThat(commandGateway.sendAndWait(new CheckIfPrime(4), Boolean.class)).isFalse();
        assertThatThrownBy(() -> commandGateway.sendAndWait(new CheckIfPrime(10), Boolean.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported value: 10");
    }

    private void assertQuery() throws Exception {
        List<Integer> primes = queryGateway.queryMany(new GetKnownPrimes(), Integer.class, null)
                                           .get();

        assertThat(primes).isEqualTo(List.of(2, 3, 5, 7));
    }

    private class VoidEventHandler {

        @SuppressWarnings("unused")
        @EventHandler
        public void handle(PrimeChecked event) {
            eventHandlerCalled.set(true);
        }
    }

    private class MonoEventHandler {

        @SuppressWarnings("unused")
        @EventHandler
        public Mono<Void> handle(PrimeChecked event) {
            return Mono.fromRunnable(() -> eventHandlerCalled.set(true));
        }
    }

    private class CompletableFutureEventHandler {

        @SuppressWarnings("unused")
        @EventHandler
        public CompletableFuture<Void> handle(PrimeChecked event) {
            return CompletableFuture.runAsync(() -> eventHandlerCalled.set(true));
        }
    }

    private static class CompletableFutureCommandHandler {

        @SuppressWarnings("unused")
        @CommandHandler
        public Future<Boolean> handle(CheckIfPrime cmd) {
            return CompletableFuture.completedFuture(isPrime(cmd.value));
        }
    }

    private static class MonoCommandHandler {

        @SuppressWarnings("unused")
        @CommandHandler
        public Mono<Boolean> handle(CheckIfPrime cmd) {
            return Mono.just(isPrime(cmd.value));
        }
    }

    private static class BooleanCommandHandler {

        @SuppressWarnings("unused")
        @CommandHandler
        public boolean handle(CheckIfPrime cmd) {
            return isPrime(cmd.value);
        }
    }

    private static class JustQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public Integer handle(EchoValue query) {
            return query.values()[0];
        }
    }

    private static class PrimitiveQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public int handle(EchoValue query) {
            return query.values()[0];
        }
    }

    private static class OptionalQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public Optional<Integer> handle(EchoValue query) {
            return Optional.of(query.values()[0]);
        }
    }

    private static class CompletableFutureQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public CompletableFuture<Integer> handle(EchoValue query) {
            return CompletableFuture.completedFuture(query.values()[0]);
        }
    }

    private static class IterableQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public Iterable<Integer> handle(EchoValue query) {
            return List.of(query.values()[0], query.values()[1]);
        }
    }

    private static class StreamQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public Stream<Integer> handle(EchoValue query) {
            return Stream.of(query.values()[0], query.values()[1]);
        }
    }

    private static class MonoQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public Mono<Integer> handle(EchoValue query) {
            return Mono.just(query.values()[0]);
        }
    }

    private static class FluxQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public Flux<Integer> handle(EchoValue query) {
            return Flux.just(query.values()[0], query.values()[1]);
        }
    }

    private static boolean isPrime(int n) {
        return switch (n) {
            case 0, 1, 4 -> false;
            case 2, 3, 5 -> true;
            default -> throw new IllegalArgumentException("unsupported value: " + n);
        };
    }
}
