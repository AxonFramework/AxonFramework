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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultCommandGateway}.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class DefaultCommandGatewayTest {

    private DefaultCommandGateway testSubject;
    private CommandBus mockCommandBus;
    private static final MessageTypeResolver TEST_MESSAGE_NAME_RESOLVER = new MessageTypeResolver() {
        @Override
        public <P> MessageType resolve(P payload) {
            return new MessageType(payload.getClass().getSimpleName());
        }
    };

    @BeforeEach
    void setUp() {
        mockCommandBus = mock(CommandBus.class);
        testSubject = new DefaultCommandGateway(mockCommandBus, TEST_MESSAGE_NAME_RESOLVER);
    }

    @Test
    void wrapsObjectIntoCommandMessage() throws ExecutionException, InterruptedException {
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage<>(new MessageType("result"), "OK")
        ));
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);
        verify(mockCommandBus).dispatch(argThat(m -> m.getPayload().equals(payload)), isNull());
        assertEquals("OK", result.getResultMessage().get().getPayload());
    }

    @Test
    void dispatchReturnsExceptionallyCompletedFutureWhenCommandBusCompletesExceptionally() {
        when(mockCommandBus.dispatch(any(),
                                     any())).thenAnswer(i -> CompletableFuture.failedFuture(new MockException()));
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);
        verify(mockCommandBus).dispatch(argThat(m -> m.getPayload().equals(payload)), isNull());
        assertTrue(result.getResultMessage().isCompletedExceptionally());
    }

    @Test
    void dispatchReturnsExceptionallyCompletedFutureWhenCommandBusReturnsExceptionalMessage() {
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage<>(new MessageType("result"), new MockException())
        ));
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);
        verify(mockCommandBus).dispatch(argThat(m -> m.getPayload().equals(payload)), isNull());
        assertTrue(result.getResultMessage().isCompletedExceptionally());
    }

    @Test
    void resolvesMessageTypeUsingMessageNameResolver() throws ExecutionException, InterruptedException {
        // given
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage<>(new MessageType("result"), "OK")
        ));

        // when
        TestPayload payload = new TestPayload();
        CommandResult result = testSubject.send(payload, null);

        // then
        var expectedMessageType = new MessageType("TestPayload");
        verify(mockCommandBus).dispatch(argThat(m -> m.type().equals(expectedMessageType)), isNull());
        assertEquals("OK", result.getResultMessage().get().getPayload());
    }

    @Test
    void passCommandMessageAsIs() throws ExecutionException, InterruptedException {
        // given
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(
                new GenericCommandResultMessage<>(new MessageType("result"), "OK")
        ));

        // when
        TestPayload payload = new TestPayload();
        var testCommand =
                new GenericCommandMessage<>(new MessageType("command"), payload);
        CommandResult result = testSubject.send(testCommand, null);

        // then
        verify(mockCommandBus).dispatch(argThat(m -> m.equals(testCommand)), isNull());
        assertEquals("OK", result.getResultMessage().get().getPayload());
    }

    private static class TestPayload {

    }
}
