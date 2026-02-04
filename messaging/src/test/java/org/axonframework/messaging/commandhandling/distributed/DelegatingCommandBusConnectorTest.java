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

package org.axonframework.messaging.commandhandling.distributed;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.distributed.CommandBusConnector;
import org.axonframework.messaging.commandhandling.distributed.DelegatingCommandBusConnector;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DelegatingCommandBusConnector}.
 *
 * @author Jens Mayer
 */
class DelegatingCommandBusConnectorTest {

    private final CommandBusConnector delegate = mock(CommandBusConnector.class);
    private final ProcessingContext processingContext = mock(ProcessingContext.class);
    private final CommandBusConnector.Handler handler = mock(CommandBusConnector.Handler.class);
    private final ComponentDescriptor componentDescriptor = mock(ComponentDescriptor.class);

    private TestDelegatingCommandBusConnector wrappedConnector;

    @BeforeEach
    void setUp() {
        wrappedConnector = new TestDelegatingCommandBusConnector(delegate);
    }

    @Test
    void testDispatchDelegatesToWrappedConnector() {
        // Given
        CommandMessage command = asCommandMessage("command");
        CompletableFuture<CommandResultMessage> expectedResult =
                CompletableFuture.completedFuture(asCommandResultMessage("result"));

        when(delegate.dispatch(command, processingContext)).thenReturn(expectedResult);

        // When
        CompletableFuture<CommandResultMessage> result = wrappedConnector.dispatch(command, processingContext);

        // Then
        assertSame(expectedResult, result);
        verify(delegate).dispatch(command, processingContext);
    }

    @Test
    void testDispatchWithNullProcessingContext() {
        // Given
        CommandMessage command = asCommandMessage("command");
        CompletableFuture<CommandResultMessage> expectedResult =
                CompletableFuture.completedFuture(asCommandResultMessage("result"));

        when(delegate.dispatch(command, null)).thenReturn(expectedResult);

        // When
        CompletableFuture<CommandResultMessage> result = wrappedConnector.dispatch(command, null);

        // Then
        assertSame(expectedResult, result);
        verify(delegate).dispatch(command, null);
    }


    @Test
    void testSubscribeDelegatesToWrappedConnector() {
        QualifiedName commandName = asQualifiedName("TestCommand");
        int loadFactor = 100;

        wrappedConnector.subscribe(commandName, loadFactor);
        verify(delegate).subscribe(commandName, loadFactor);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUnsubscribeDelegatesToWrappedConnector(boolean expectedResult) {
        QualifiedName commandName = asQualifiedName("TestCommand");
        when(delegate.unsubscribe(commandName)).thenReturn(expectedResult);

        boolean result = wrappedConnector.unsubscribe(commandName);
        assertEquals(expectedResult, result);
        verify(delegate).unsubscribe(commandName);
    }

    @Test
    void testOnIncomingCommandDelegatesToWrappedConnector() {
        wrappedConnector.onIncomingCommand(handler);
        verify(delegate).onIncomingCommand(handler);
    }

    @Test
    void testDescribeToDelegatesToWrappedConnector() {
        wrappedConnector.describeTo(componentDescriptor);
        verify(componentDescriptor).describeWrapperOf(delegate);
        verifyNoMoreInteractions(delegate);
    }

    private CommandMessage asCommandMessage(String payload) {
        return new GenericCommandMessage(MessageType.fromString("commandmessage#1.0"), payload);
    }

    private CommandResultMessage asCommandResultMessage(String payload) {
        return new GenericCommandResultMessage(MessageType.fromString("commandresult#1.0"), payload);
    }

    private QualifiedName asQualifiedName(String name) {
        return new QualifiedName(null, name);
    }

    /**
     * Concrete test implementation of the abstract WrappedCommandBusConnector for testing purposes.
     */
    private static class TestDelegatingCommandBusConnector extends DelegatingCommandBusConnector {

        protected TestDelegatingCommandBusConnector(CommandBusConnector delegate) {
            super(delegate);
        }
    }
}