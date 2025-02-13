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

package org.axonframework.configuration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.commandhandling.config.CommandBusBuilder;
import org.axonframework.commandhandling.tracing.CommandBusSpanFactory;
import org.axonframework.commandhandling.tracing.DefaultCommandBusSpanFactory;
import org.axonframework.commandhandling.tracing.TracingCommandBus;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

class NewDefaultConfigurerTest {

    @Test
    void commandBusConfiguring() {
        NewDefaultConfigurer.configurer()
                            // 1
                            .registerCommandBus(
                                    CommandBusBuilder.forSimpleCommandBus()
                                                     .withTracing(DefaultCommandBusSpanFactory.builder().build())
//                                                     .decorate((config, delegate) -> new TracingCommandBus(
//                                                             delegate,
//                                                             config.getComponent(CommandBusSpanFactory.class)
//                                                     ))
                            )
                            // 2
                            .registerDecorator(
                                    CommandBus.class,
                                    5, // order
                                    (config, delegate) -> c -> new TracingCommandBus(delegate,
                                                                                     c.getComponent(CommandBusSpanFactory.class))
                            )
                            .registerCustomizer(
                                    CommandBus.class,
                                    6,
                                    commandBusComponentBuilder -> commandBusComponentBuilder.decorate(
                                            (config, delegate) -> c -> delegate
                                    )
                            )

                            .registerCommandBus(config -> new SimpleCommandBus())

                            .registerCommandHandler(config -> new SimpleCommandHandlingComponent()
                                    .subscribe(
                                            new QualifiedName(TestCommand.class),
                                            (command, context) -> {
                                                System.out.println("Handle test command");
                                                return MessageStream.empty();
                                            }
                                    )
                            )
                            .registerCommandHandler(config -> (command, context) -> {
                                // TODO for this to work, a CommandHandler should be able to provide it's own QualifiedName some how.
                                System.out.println("Handle test command");
                                return MessageStream.empty();
                            });
    }

    class TestCommand {

    }
}