/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling;

import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.common.util.MockException;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleCommandHandlingComponent}.
 *
 * @author Mitchell Herrijgers
 */
class SimpleCommandHandlingComponentTest {

    private final AtomicBoolean command1Handled = new AtomicBoolean(false);
    private final AtomicBoolean command2HandledParent = new AtomicBoolean(false);
    private final AtomicBoolean command2HandledChild = new AtomicBoolean(false);
    private final AtomicBoolean command3Handled = new AtomicBoolean(false);

    private final SimpleCommandHandlingComponent handlingComponent = SimpleCommandHandlingComponent
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
        GenericCommandMessage command1 = new GenericCommandMessage(new MessageType("Command1"), "");
        handlingComponent.handle(command1, StubProcessingContext.forMessage(command1));
        assertTrue(command1Handled.get());
        assertFalse(command2HandledParent.get());
        assertFalse(command2HandledChild.get());
        assertFalse(command3Handled.get());

        command1Handled.set(false);

        GenericCommandMessage command2 = new GenericCommandMessage(new MessageType("Command2"), "");
        handlingComponent.handle(command2, StubProcessingContext.forMessage(command2));
        assertFalse(command1Handled.get());
        assertFalse(command2HandledParent.get());
        assertTrue(command2HandledChild.get());
        assertFalse(command3Handled.get());

        command2HandledChild.set(false);
        GenericCommandMessage command3 = new GenericCommandMessage(new MessageType("Command3"), "");
        handlingComponent.handle(command3, StubProcessingContext.forMessage(command3));
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
            GenericCommandMessage command = new GenericCommandMessage(new MessageType("Command4"), "");
            handlingComponent.handle(command, StubProcessingContext.forMessage(command))
                             .asCompletableFuture()
                             .join();
        });
        assertInstanceOf(NoHandlerForCommandException.class, exception.getCause());
    }

    @Test
    void handleReturnsFailedMessageStreamForExceptionThrowingCommandHandlingComponent() {
        QualifiedName faultyCommand = new QualifiedName("Error!");
        CommandHandler faultyCommandHandler = (command, context) -> {
            throw new MockException();
        };
        handlingComponent.subscribe(faultyCommand, faultyCommandHandler);

        CommandMessage command = new GenericCommandMessage(new MessageType(faultyCommand), "");
        MessageStream.Single<CommandResultMessage> result =
                handlingComponent.handle(command, StubProcessingContext.forMessage(command));

        Optional<Throwable> resultError = result.error();
        assertThat(resultError).isPresent();
        assertThat(resultError.get()).isInstanceOf(MockException.class);
    }

    @Test
    void handleReturnsFailedMessageStreamForExceptionThrowingCommandHandler() {
        QualifiedName faultyCommand = new QualifiedName("Error!");
        CommandHandlingComponent faultyComponent = mock(CommandHandlingComponent.class);
        when(faultyComponent.handle(any(), any())).thenThrow(new MockException());
        when(faultyComponent.supportedCommands()).thenReturn(Set.of(faultyCommand));
        handlingComponent.subscribe(faultyComponent);

        CommandMessage command = new GenericCommandMessage(new MessageType(faultyCommand), "");
        MessageStream<CommandResultMessage> result =
                handlingComponent.handle(command, StubProcessingContext.forMessage(command));

        Optional<Throwable> resultError = result.error();
        assertThat(resultError).isPresent();
        assertThat(resultError.get()).isInstanceOf(MockException.class);
    }

    @Test
    void rejectsDuplicateCommandHandler() {
        assertThatThrownBy(() -> SimpleCommandHandlingComponent
            .create("MySuperComponent")
            .subscribe(
                    new QualifiedName("Command1"),
                    (command, context) -> {
                        return MessageStream.empty().cast();
                    }
            )
            .subscribe(
                    new QualifiedName("Command1"),
                    (command, context) -> {
                        return MessageStream.empty().cast();
                    }
            )
        ).isInstanceOf(DuplicateCommandHandlerSubscriptionException.class);
    }
}