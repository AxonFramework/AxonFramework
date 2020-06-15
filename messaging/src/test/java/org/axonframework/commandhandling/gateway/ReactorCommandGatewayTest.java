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
 * @author Sara Pellegrini
 * @since 4.4
 */
public class ReactorCommandGatewayTest {


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


    private void registerStopMessageChain(ReactorCommandGateway gateway) {
        gateway.registerDispatchInterceptor(mono -> Mono.empty());
    }

    private void registerStopResultsChain(ReactorCommandGateway gateway) {
        gateway.registerResultHandlerInterceptor((command, flux) -> Flux.empty());
    }

    private void registerMessageFilter(ReactorCommandGateway gateway,
                                       Predicate<CommandMessage<?>> predicate) {
        gateway.registerDispatchInterceptor(mono -> mono.filter(predicate));
    }

    private void registerResultsFilter(ReactorCommandGateway gateway,
                                       Predicate<CommandResultMessage<?>> predicate) {
        gateway.registerResultHandlerInterceptor((command, flux) -> flux.filter(predicate));
    }

    private ReactorCommandGateway gateway(Sender sender) {
        return DefaultReactorCommandGateway.builder()
                                           .commandBus(sender)
                                           .build();
    }


    @Test
    void test1() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

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
        Mono<String> results = gateway.send(new GenericCommandMessage<>(""));

        // verify -> results equals v2
        StepVerifier
                .create(results)
                .expectNextMatches(result -> result.equals("v2"))
                .verifyComplete();

        // verify -> command sent has k1 -> v2
        CommandMessage<?> sentCommand = sender.lastSentCommand();
        assertEquals("v2", sentCommand.getMetaData().get("k1"));
    }

    @Test
    void test2() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

        // int 1 -> stop chain of command
        registerStopMessageChain(gateway);

        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));

        StepVerifier.create(results).expectComplete().verify();

        // verify -> command has not been sent
        assertEquals(0, sender.numberOfSentCommands());
    }

    @Test
    void test3() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);
        // int 1 -> stop chain of results
        registerStopResultsChain(gateway);

        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));

        StepVerifier.create(results).expectComplete().verify();
    }

    @Test
    void test4() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

        // int 1 -> mono of command complete exceptionally
        registerMessageMapping(gateway, command -> {
            throw new RuntimeException("MyError");
        });

        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));
        StepVerifier.create(results).expectNextCount(0).expectError(RuntimeException.class).verify();
    }

    @Test
    void test5() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

        // int 1 -> flux of results complete exceptionally
        registerResultMapping(gateway, (command, result) -> {
            throw new RuntimeException("MyError");
        });

        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));
        StepVerifier.create(results).expectNextCount(0).expectError(RuntimeException.class).verify();
    }

    @Test
    void test6() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

        registerResultsFilter(gateway, result -> result.getMetaData().containsKey("K"));
        // int 1 -> flux of results is filtered

        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));
        StepVerifier.create(results).verifyComplete();
        // verify -> command has not been sent
        assertEquals(1, sender.numberOfSentCommands());
    }

    @Test
    void test6bis() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

        registerMessageFilter(gateway, result -> result.getMetaData().containsKey("K"));
        // int 1 -> flux of results is filtered

        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));
        StepVerifier.create(results).verifyComplete();
        // verify -> command has not been sent
        assertEquals(0, sender.numberOfSentCommands());
    }

    @Test
    void test7() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

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


        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));

        StepVerifier.create(results).expectNextCount(1).verifyComplete();

        assertEquals(1, sender.numberOfSentCommands());
    }

    @Test
    void test8() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

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

        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));

        // verify -> results have k1 -> v2 and k2 -> v3
        StepVerifier.create(results).expectNextMatches(result ->
                                                               result.getMetaData().get("k1").equals("v2") &&
                                                                       result.getMetaData().get("k1").equals("v2"));
    }

    @Test
    void test9() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

        // command handler completes exceptionally
        registerMessageMapping(gateway, command -> {
            throw new RuntimeException("OHOH");
        });
        Mono<CommandResultMessage<?>> results = gateway.send(new GenericCommandMessage<>(""));

        // verify gateway.send(command).doOnError(isTriggered);
        StepVerifier.create(results).expectNextCount(0).expectError().verify();

        assertEquals(0, sender.numberOfSentCommands());
    }

    @Test
    void test10() {
//        Sender sender = new Sender();
//        Gateway<CommandMessage<?>, CommandResultMessage<?>> gateway = gateway(sender);

        //???
        // int 1 -> map a result flux that completes exceptionally into a default value
        // command handler completes exceptionally
        // verify gateway.send(command).doOnComplete(isTriggered);
    }

    @Test
    void test11() {
        Sender sender = new Sender();
        ReactorCommandGateway gateway = gateway(sender);

        registerResultMapping(gateway, (command, result) ->
                command.getMetaData()
                       .containsKey("kX") ? new GenericCommandResultMessage<Object>("new Payload") : result);
        Map<String, String> commandMetadata = new HashMap<>();
        commandMetadata.put("kX", "vX");
        GenericCommandMessage<String> myCommand = new GenericCommandMessage<>("");
        Mono<String> results = gateway.send(myCommand.andMetaData(commandMetadata));
        StepVerifier.create(results)
                    .expectNextMatches(result -> result.equals("new Payload"))
                    .verifyComplete();
        // command "MyCommandName" is sent
        Mono<String> results2 = gateway.send(myCommand);
        // verify that the metadata is in the results
        StepVerifier.create(results2)
                    .expectNextMatches(result -> result.equals(""))
                    .verifyComplete();
    }
}
