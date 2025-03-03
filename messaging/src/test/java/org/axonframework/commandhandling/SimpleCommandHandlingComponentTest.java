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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;


class SimpleCommandHandlingComponentTest {

    private final AtomicBoolean command1Handled = new AtomicBoolean(false);
    private final AtomicBoolean command2HandledParent = new AtomicBoolean(false);
    private final AtomicBoolean command2HandledChild = new AtomicBoolean(false);
    private final AtomicBoolean command3Handled = new AtomicBoolean(false);

    private final CommandHandlingComponent handlingComponent = SimpleCommandHandlingComponent
            .create("MySuperComponent")
            .subscribe(
                    new QualifiedName("Command1"),
                    (command, context) -> {
                        command1Handled.set(true);
                        return MessageStream.empty().cast();
                    }
            )
            .subscribe(
                    new QualifiedName("Command2"),
                    (command, context) -> {
                        command2HandledParent.set(true);
                        return MessageStream.empty().cast();
                    }
            )
            .subscribe(
                    SimpleCommandHandlingComponent
                            .create("MySubComponent")
                            .subscribe(
                                    new QualifiedName("Command2"),
                                    (command, context) -> {
                                        command2HandledChild.set(true);
                                        return MessageStream.empty().cast();
                                    }
                            )
                            .subscribe(
                                    new QualifiedName("Command3"),
                                    (command, context) -> {
                                        command3Handled.set(true);
                                        return MessageStream.empty().cast();
                                    }
                            )
            );

    @Test
    void handlesTheMostSpecificRegisteredHandler() {
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

        command2HandledChild.set(false);
        handlingComponent.handle(new GenericCommandMessage<>(new MessageType("Command3"), ""), ProcessingContext.NONE);
        assertFalse(command1Handled.get());
        assertFalse(command2HandledParent.get());
        assertFalse(command2HandledChild.get());
        assertTrue(command3Handled.get());
    }

    @Test
    void supportedCommandReturnsAllSupportedCommands() {
        assertEquals(3, handlingComponent.supportedCommands().size());
        assertTrue(handlingComponent.supportedCommands().contains(new QualifiedName("Command1")));
        assertTrue(handlingComponent.supportedCommands().contains(new QualifiedName("Command2")));
        assertTrue(handlingComponent.supportedCommands().contains(new QualifiedName("Command3")));
    }

    @Test
    void handleWithUnknownPayloadReturnsInFailure() {
        CompletionException exception = assertThrows(CompletionException.class, () -> {
            handlingComponent.handle(new GenericCommandMessage<>(new MessageType("Command4"), ""),
                                     ProcessingContext.NONE)
                             .asCompletableFuture()
                             .join();
        });
        assertInstanceOf(NoHandlerForCommandException.class, exception.getCause());
    }
}