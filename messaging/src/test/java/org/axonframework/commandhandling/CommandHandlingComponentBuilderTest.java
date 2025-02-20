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

package org.axonframework.commandhandling;

import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation2.AnnotatedCommandHandlingComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;


class CommandHandlingComponentBuilderTest {

    @Test
    void name() {
        AtomicBoolean command1Handled = new AtomicBoolean(false);
        AtomicBoolean command2HandledParent = new AtomicBoolean(false);
        AtomicBoolean command2HandledChild = new AtomicBoolean(false);
        AtomicBoolean command3Handled = new AtomicBoolean(false);

        CommandHandlingComponent handlingComponent = SimpleCommandHandlingComponent
                .forComponent("MySuperComponent")
                .registerInterceptor((uow, chain) -> {
                    // Intercept
                    return chain.proceedSync();
                })
                // Method one, factory of CommandHandlingComponent
                .subscribe(
                        // Third-level layer
                        AnnotationBasedCommandHandlingComponentFactory
                                .createHandlingComponent("MyAnnotatedCommandHandler", new MyAnnotatedCommandHandler())
                )
                // Method two, direct implementation of CommandHandlingComponent
                .subscribe(new AnnotatedCommandHandlingComponent<>(new MyAnnotatedCommandHandler()))
                .subscribe(
                        new QualifiedName("Command1"),
                        (command, context) -> {
                            command1Handled.set(true);
                            return MessageStream.empty();
                        }
                )
                // Second layer
                .subscribe(
                        SimpleCommandHandlingComponent
                                .forComponent("MySubComponent")
                                .subscribe(
                                        new QualifiedName("Command2"),
                                        (command, context) -> {
                                            command2HandledChild.set(true);
                                            return MessageStream.empty();
                                        }
                                )
                                .subscribe(
                                        new QualifiedName("Command3"),
                                        (command, context) -> {
                                            command3Handled.set(true);
                                            return MessageStream.empty();
                                        }
                                )
                );

        handlingComponent.handle(new GenericCommandMessage<>(new MessageType("Command1"), ""), ProcessingContext.NONE);
        assertTrue(command1Handled.get());
        assertFalse(command2HandledParent.get());
        assertFalse(command2HandledChild.get());
        assertFalse(command3Handled.get());

        command1Handled.set(false);

        handlingComponent.handle(new GenericCommandMessage<>(new MessageType("Command2"), ""), ProcessingContext.NONE);
        assertFalse(command1Handled.get());
        assertFalse(command2HandledParent.get());
        assertTrue(command2HandledChild.get());
        assertFalse(command3Handled.get());

    }


    class MyAnnotatedCommandHandler {

        @org.axonframework.commandhandling.annotation.CommandHandler(commandName = "MyCommand")
        public void handle(String command) {
            // Bla
        }
    }


    class AnnotationBasedCommandHandlingComponentFactory {
        static <T> CommandHandlingComponent createHandlingComponent(String name, T instance) {
            // Inspect
            SimpleCommandHandlingComponent component = SimpleCommandHandlingComponent.forComponent(name);
            // For every found handler, register. Might even use nesting here.
            return component;
        }
    }

}