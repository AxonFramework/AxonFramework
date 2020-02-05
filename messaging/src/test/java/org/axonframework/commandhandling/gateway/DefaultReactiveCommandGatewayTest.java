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
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

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
        CommandGateway commandGateway = DefaultCommandGateway.builder()
                                                             .commandBus(commandBus)
                                                             .build();
        reactiveCommandGateway = new DefaultReactiveCommandGateway(commandGateway);
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
}
