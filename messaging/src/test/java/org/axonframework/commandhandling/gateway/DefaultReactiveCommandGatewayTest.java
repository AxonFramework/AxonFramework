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
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.Registration;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.Collections;

/**
 * Tests for {@link ReactiveCommandGateway}.
 *
 * @author Milan Savic
 */
public class DefaultReactiveCommandGatewayTest {

    private DefaultReactiveCommandGateway reactiveCommandGateway;

    @BeforeEach
    void setUp() {
        CommandBus commandBus = SimpleCommandBus.builder().build();
        commandBus.subscribe(String.class.getName(), message -> "handled");
        commandBus.subscribe(Integer.class.getName(), message -> {
            throw new RuntimeException();
        });
        commandBus.subscribe(Boolean.class.getName(),
                             message -> "" + message.getMetaData().getOrDefault("key1", "")
                                     + message.getMetaData().getOrDefault("key2", ""));
        reactiveCommandGateway = new DefaultReactiveCommandGateway(commandBus);
    }

    @Test
    void testSend() {
        StepVerifier.create(reactiveCommandGateway.send("command"))
                    .expectNext("handled")
                    .verifyComplete();
    }

    @Test
    void testSendFails() {
        StepVerifier.create(reactiveCommandGateway.send(5))
                    .verifyError(RuntimeException.class);
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
}
