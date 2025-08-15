package org.axonframework.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.annotation.QueryHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AsyncMessageHandlerTest {
    private final CommandBus commandBus = new SimpleCommandBus();
    private final CommandGateway commandGateway = new DefaultCommandGateway(commandBus, new ClassBasedMessageTypeResolver());
    private final QueryBus queryBus = SimpleQueryBus.builder().build();
    private final QueryGateway queryGateway = DefaultQueryGateway.builder().queryBus(queryBus).messageNameResolver(new ClassBasedMessageTypeResolver()).build();

    record CheckIfPrime(int value) {}
    record GetKnownPrimes() {}

    @Disabled("Returning a Future other than CompletableFuture is not supported")
    @Test
    public void annotatedCommandHandlerShouldUseFutureReturnType() {
        commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(new FutureHandler()));

        assertCommands();
    }

    @Test
    public void annotatedCommandHandlerShouldUseCompletableFutureReturnType() {
        commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(new CompletableFutureHandler()));

        assertCommands();
    }

    @Test
    public void annotatedCommandHandlerShouldUseMonoReturnType() {
        commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(new MonoHandler()));

        assertCommands();
    }

    @Test
    public void annotatedCommandHandlerShouldUseBooleanReturnType() {
        commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(new BooleanHandler()));

        assertCommands();
    }

    @Test
    public void annotatedQueryHandlerShouldUseIterableReturnType() {
        // TODO missing subscribe method for potential AnnotatedQueryHandlingComponent
        //queryBus.subscribe(new AnnotatedQueryHandlingComponent<>(new ListQueryHandler()));

        //assertQuery();
    }

    @Test
    public void declarativeCommandHandlerShouldUseMonoReturnType() {
        commandBus.subscribe(
            new QualifiedName(CheckIfPrime.class.getName()),
            (command, context) -> {
                CommandResultMessage<Boolean> data = new GenericCommandResultMessage<>(null, isPrime(((CheckIfPrime)command.payload()).value()));

                return MessageStream.fromMono(Mono.just(data));
            }
        );

        assertCommands();
    }

    @Test
    public void declarativeCommandHandlerShouldUseCompletableFutureReturnType() {
        commandBus.subscribe(
            new QualifiedName(CheckIfPrime.class.getName()),
            (command, context) -> {
                CommandResultMessage<Boolean> value = new GenericCommandResultMessage<>(null, isPrime(((CheckIfPrime)command.payload()).value()));

                return MessageStream.fromFuture(CompletableFuture.completedFuture(value));
            }
        );

        assertCommands();
    }

    @Test
    public void declarativeCommandHandlerShouldUseBooleanReturnType() {
        commandBus.subscribe(
            new QualifiedName(CheckIfPrime.class.getName()),
            (command, context) -> MessageStream.just(new GenericCommandResultMessage<>(null, isPrime(((CheckIfPrime)command.payload()).value())))
        );

        assertCommands();
    }

    // TODO there seems to be a difference between how CommandHandler and QueryHandler can be subscribed.
    // One only works with Message wrappers, while the other can accept direct results, making the code a lot simpler.
    @Test
    public void declarativeQueryHandlerShouldUseFluxReturnType() throws Exception {
        queryBus.subscribe(
            GetKnownPrimes.class.getName(),
            Integer.class,
            (query, context) -> Flux.just(2, 3, 5, 7)
        );

        assertQuery();
    }

    @Test
    public void declarativeQueryHandlerShouldUseIterableReturnType() throws Exception {
        queryBus.subscribe(
            GetKnownPrimes.class.getName(),
            Integer.class,
            (query, context) -> List.of(2, 3, 5, 7)
        );

        assertQuery();
    }

    private void assertCommands() {
        assertThat(commandGateway.sendAndWait(new CheckIfPrime(2), Boolean.class)).isTrue();
        assertThat(commandGateway.sendAndWait(new CheckIfPrime(4), Boolean.class)).isFalse();
        assertThatThrownBy(() -> commandGateway.sendAndWait(new CheckIfPrime(10), Boolean.class))
            .isInstanceOf(CommandExecutionException.class)
            .cause()
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("unsupported value: 10");
    }

    private void assertQuery() throws Exception {
        List<Integer> primes = queryGateway.query(new GetKnownPrimes(), ResponseTypes.multipleInstancesOf(Integer.class)).get();

        assertThat(primes).isEqualTo(List.of(2, 3, 5, 7));
    }

    static class CompletableFutureHandler {
        @CommandHandler
        public Future<Boolean> handle(CheckIfPrime cmd) {
            return CompletableFuture.completedFuture(isPrime(cmd.value));
        }
    }

    static class FutureHandler {
        @CommandHandler
        public Future<Boolean> handle(CheckIfPrime cmd) {
            return new FutureTask<>(() -> isPrime(cmd.value));
        }
    }

    static class MonoHandler {
        @CommandHandler
        public Mono<Boolean> handle(CheckIfPrime cmd) {
            return Mono.just(isPrime(cmd.value));
        }
    }

    static class BooleanHandler {
        @CommandHandler
        public boolean handle(CheckIfPrime cmd) {
            return isPrime(cmd.value);
        }
    }

    static class ListQueryHandler {
        @QueryHandler
        public List<Integer> handle(GetKnownPrimes cmd) {
            return List.of(2, 3, 5, 7);
        }
    }

    private static boolean isPrime(int n) {
        return switch(n) {
            case 0, 1, 4 -> false;
            case 2, 3, 5 -> true;
            default -> throw new IllegalArgumentException("unsupported value: " + n);
        };
    }
}
