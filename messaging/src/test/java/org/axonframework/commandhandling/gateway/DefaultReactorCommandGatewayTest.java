package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultReactorCommandGateway}.
 *
 * @author Sara Pellegrini
 * @since 4.4
 */
public class DefaultReactorCommandGatewayTest {

    private CommandBusStub commandBus;
    private ReactorCommandGateway gateway;

    @BeforeEach
    public void setUp() {
        commandBus = new CommandBusStub();
        gateway = DefaultReactorCommandGateway.builder()
                                              .commandBus(commandBus)
                                              .build();
    }

    @Test
    void testInterceptorOrder() {

        // int 1 -> metadata on command k1 -> v1
        Map<String, String> metadata1 = new HashMap<>();
        metadata1.put("k1", "v1");
        registerMessageMapping(gateway, command -> command.andMetaData(metadata1));

        // int 2 -> copy metadata from command into results
        registerResultMapping(gateway,
                              (command, result) -> new GenericCommandResultMessage<>(command.getMetaData().get("k1")));

        // int 3 -> metadata on command k1 -> v2
        Map<String, String> metadata2 = new HashMap<>();
        metadata1.put("k1", "v2");
        registerMessageMapping(gateway, command -> command.andMetaData(metadata2));

        //send
        Mono<String> results = gateway.send("");

        // verify -> results equals v2
        StepVerifier.create(results)
                    .expectNextMatches(result -> result.equals("v2"))
                    .verifyComplete();

        // verify -> command sent has k1 -> v2
        CommandMessage<?> sentCommand = commandBus.lastSentCommand();
        assertEquals("v2", sentCommand.getMetaData().get("k1"));
    }

    @Test
    void testResultFiltering() {
        registerResultsFilter(gateway, result -> result.getMetaData().containsKey("K"));
        // int 1 -> flux of results is filtered

        Mono<CommandResultMessage<?>> results = gateway.send("");
        StepVerifier.create(results)
                    .verifyComplete();
        // verify -> command has been sent
        assertEquals(1, commandBus.numberOfSentCommands());
    }

    @Test
    void testCommandFiltering() {
        registerMessageFilter(gateway, result -> result.getMetaData().containsKey("K"));
        // int 1 -> flux of results is filtered

        Mono<CommandResultMessage<?>> results = gateway.send("");
        StepVerifier.create(results)
                    .verifyComplete();
        // verify -> command has not been sent
        assertEquals(0, commandBus.numberOfSentCommands());
    }

    @Test
    void testCommandDispatchAndResultHandlerInterceptor() {
        // int 1 -> add Principal to command and results
        Map<String, String> principalMetadata = new HashMap<>();
        principalMetadata.put("username", "admin");
        registerMapping(gateway,
                        command -> command.andMetaData(principalMetadata),
                        (command, result) -> result.andMetaData(principalMetadata));

        // int 2 -> validate authorizations and results
        registerMapping(gateway,
                        command -> {
                            assert command.getMetaData().get("username").equals("admin");
                            return command;
                        },
                        (command, result) -> {
                            assert result.getMetaData().get("username").equals("admin");
                            return result;
                        });

        Mono<CommandResultMessage<?>> results = gateway.send("");

        StepVerifier.create(results)
                    .expectNextCount(1)
                    .verifyComplete();

        assertEquals(1, commandBus.numberOfSentCommands());
    }

    @Test
    void testCommandResultHandlerChain() {
        // int 1 -> metadata on result k1 -> v1
        Map<String, String> metadata1 = new HashMap<>();
        metadata1.put("k1", "v1");
        registerResultMapping(gateway, (command, result) -> result.andMetaData(metadata1));

        // int 2 -> metadata on result k1 -> v2
        Map<String, String> metadata2 = new HashMap<>();
        metadata2.put("k1", "v2");
        registerResultMapping(gateway, (command, result) -> result.andMetaData(metadata2));

        // int 3 -> metadata on result k2 -> v3
        Map<String, String> metadata3 = new HashMap<>();
        metadata3.put("k2", "v3");
        registerResultMapping(gateway, (command, result) -> result.andMetaData(metadata3));

        Mono<CommandResultMessage<?>> results = gateway.send("");

        // verify -> results have k1 -> v2 and k2 -> v3
        StepVerifier.create(results)
                    .expectNextMatches(result -> result.getMetaData().get("k1").equals("v2") &&
                            result.getMetaData().get("k1").equals("v2"));
    }

    @Test
    void testResultErrorMapping() {
        commandBus = new CommandBusStub(GenericCommandResultMessage
                                                .asCommandResultMessage(new RuntimeException("oops")));
        gateway = DefaultReactorCommandGateway.builder()
                                              .commandBus(commandBus)
                                              .build();
        gateway.registerResultHandlerInterceptor((command, results) -> results
                .onErrorResume(t -> Flux.just(GenericCommandResultMessage.asCommandResultMessage(t.getMessage()))));

        Mono<String> result = gateway.send("");

        StepVerifier.create(result)
                    .expectNext("oops")
                    .verifyComplete();
    }

    @Test
    void testCommandMessageAlteration() {
        registerResultMapping(gateway, (command, result) ->
                command.getMetaData()
                       .containsKey("kX") ? new GenericCommandResultMessage<Object>("new Payload") : result);
        Map<String, String> commandMetadata = new HashMap<>();
        commandMetadata.put("kX", "vX");
        GenericCommandMessage<String> myCommand = new GenericCommandMessage<>("");
        Mono<String> resultsWithMetaData = gateway.send(myCommand.andMetaData(commandMetadata));
        StepVerifier.create(resultsWithMetaData)
                    .expectNextMatches("new Payload"::equals)
                    .verifyComplete();
        // command "MyCommandName" is sent
        Mono<String> resultsWithoutMetaData = gateway.send(myCommand);
        // verify that the metadata is in the results
        StepVerifier.create(resultsWithoutMetaData)
                    .expectNextMatches(""::equals)
                    .verifyComplete();
    }

    private void registerMessageMapping(
            ReactorCommandGateway gateway,
            Function<CommandMessage<?>, CommandMessage<?>> mapping) {
        gateway.registerDispatchInterceptor(mono -> mono.map(mapping));
    }

    private void registerResultMapping(
            ReactorCommandGateway gateway,
            BiFunction<CommandMessage<?>, CommandResultMessage<?>, CommandResultMessage<?>> mapping) {
        gateway.registerResultHandlerInterceptor((command, flux) -> flux.map(r -> mapping.apply(command, r)));
    }

    private void registerMapping(
            ReactorCommandGateway gateway,
            Function<CommandMessage<?>, CommandMessage<?>> commandMapping,
            BiFunction<CommandMessage<?>, CommandResultMessage<?>, CommandResultMessage<?>> resultMapping) {
        registerMessageMapping(gateway, commandMapping);
        registerResultMapping(gateway, resultMapping);
    }

    private void registerMessageFilter(ReactorCommandGateway gateway,
                                       Predicate<CommandMessage<?>> predicate) {
        gateway.registerDispatchInterceptor(mono -> mono.filter(predicate));
    }

    private void registerResultsFilter(ReactorCommandGateway gateway,
                                       Predicate<CommandResultMessage<?>> predicate) {
        gateway.registerResultHandlerInterceptor((command, flux) -> flux.filter(predicate));
    }
}
