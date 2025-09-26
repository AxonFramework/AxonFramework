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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.annotations.AnnotationRoutingStrategy;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.Metadata;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultCommandGateway}.
 *
 * @author Allard Buijze
 */
class DefaultCommandGatewayTest {

    private static final MessageTypeResolver TEST_MESSAGE_NAME_RESOLVER =
            payloadType -> Optional.of(new MessageType(payloadType.getSimpleName()));

    private CommandBus mockCommandBus;

    private DefaultCommandGateway testSubject;

    @BeforeEach
    void setUp() {
        mockCommandBus = mock(CommandBus.class);

        testSubject = new DefaultCommandGateway(mockCommandBus,
                                                TEST_MESSAGE_NAME_RESOLVER,
                                                CommandPriorityCalculator.defaultCalculator(),
                                                new AnnotationRoutingStrategy());
    }

    @Test
    void wrapsObjectIntoCommandMessage() throws ExecutionException, InterruptedException {
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage(new MessageType("result"), "OK")
        ));
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);
        verify(mockCommandBus).dispatch(
                argThat(m -> {
                    Object resultPayload = m.payload();
                    return resultPayload != null && resultPayload.equals(payload);
                }),
                isNull()
        );
        assertEquals("OK", result.getResultMessage().get().payload());
    }

    @Test
    void dispatchReturnsExceptionallyCompletedFutureWhenCommandBusCompletesExceptionally() {
        when(mockCommandBus.dispatch(any(),
                                     any())).thenAnswer(i -> CompletableFuture.failedFuture(new MockException()));
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);
        verify(mockCommandBus).dispatch(
                argThat(m -> {
                    Object resultPayload = m.payload();
                    return resultPayload != null && resultPayload.equals(payload);
                }),
                isNull()
        );
        assertTrue(result.getResultMessage().isCompletedExceptionally());
    }

    @Test
    void dispatchReturnsExceptionallyCompletedFutureWhenCommandBusReturnsExceptionalMessage() {
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage(new MessageType("result"), new MockException())
        ));
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);
        verify(mockCommandBus).dispatch(
                argThat(m -> {
                    Object resultPayload = m.payload();
                    return resultPayload != null && resultPayload.equals(payload);
                }),
                isNull()
        );
        assertTrue(result.getResultMessage().isCompletedExceptionally());
    }

    @Test
    void resolvesMessageTypeUsingMessageNameResolver() throws ExecutionException, InterruptedException {
        // given
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage(new MessageType("result"), "OK")
        ));

        // when
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);

        // then
        var expectedMessageType = new MessageType("TestPayload");
        verify(mockCommandBus).dispatch(argThat(m -> m.type().equals(expectedMessageType)), isNull());
        assertEquals("OK", result.getResultMessage().get().payload());
    }

    @Test
    void passCommandMessageAsIs() throws ExecutionException, InterruptedException {
        // given
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage(new MessageType("result"), "OK")
        ));

        // when
        TestPayload payload = new TestPayload();
        CommandMessage testCommand = new GenericCommandMessage(
                new MessageType("command"), payload, Metadata.emptyInstance(), "routingKey", 42
        );

        CommandResult result = testSubject.send(testCommand, null);

        // then
        ArgumentCaptor<CommandMessage> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockCommandBus).dispatch(commandCaptor.capture(), isNull());
        CommandMessage resultCommand = commandCaptor.getValue();
        assertThat(testCommand.identifier()).isEqualTo(resultCommand.identifier());
        assertThat(testCommand.type()).isEqualTo(resultCommand.type());
        assertThat(testCommand.payload()).isEqualTo(resultCommand.payload());
        assertThat(testCommand.payloadType()).isEqualTo(resultCommand.payloadType());
        assertThat(testCommand.metadata()).isEqualTo(resultCommand.metadata());
        assertThat(testCommand.routingKey()).isEqualTo(resultCommand.routingKey());
        assertThat(testCommand.priority()).isEqualTo(resultCommand.priority());
        assertEquals("OK", result.getResultMessage().get().payload());
    }

    private static class TestPayload {

    }
}
