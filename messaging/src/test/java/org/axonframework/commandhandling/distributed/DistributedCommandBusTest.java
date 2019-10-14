/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.*;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class DistributedCommandBusTest {

    private DistributedCommandBus testSubject;

    @Mock
    private CommandRouter mockCommandRouter;
    @Spy
    private CommandBusConnector mockConnector = new StubCommandBusConnector();
    @Mock
    private MessageMonitor<? super CommandMessage<?>> mockMessageMonitor;
    @Mock
    private MessageMonitor.MonitorCallback mockMonitorCallback;
    @Mock
    private Member mockMember;

    @BeforeEach
    void setUp() {
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .messageMonitor(mockMessageMonitor)
                                           .build();
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);
    }

    @Test
    void testDispatchWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
    }

    @Test
    void testDefaultCallbackIsUsedWhenFireAndForget() {
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .messageMonitor(mockMessageMonitor)
                                           .defaultCommandCallback(mockCallback)
                                           .build();

        CommandMessage<Object> message = GenericCommandMessage.asCommandMessage("test");
        testSubject.dispatch(message);

        verify(mockCallback).onResult(eq(message), any());
    }

    @Test
    void testDispatchFailingCommandWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testDispatchWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        testSubject = DistributedCommandBus.builder().commandRouter(mockCommandRouter).connector(mockConnector).build();
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testUnknownCommandWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        testSubject = DistributedCommandBus.builder().commandRouter(mockCommandRouter).connector(mockConnector).build();
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("unknown");
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());

        CommandCallback callback = mock(CommandCallback.class);
        testSubject.dispatch(testCommandMessage, callback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();

        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void testDispatchWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");

        CommandCallback mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertFalse(commandResultMessageCaptor.getValue().isExceptional());
        assertNull(commandResultMessageCaptor.getValue().getPayload());
    }

    @Test
    void testUnknownCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());

        CommandCallback mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(any());
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void testDispatchFailingCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");

        CommandCallback mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertEquals(Exception.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    private static class StubCommandBusConnector implements CommandBusConnector {

        @Override
        public <C> void send(Member destination, CommandMessage<? extends C> command) {
            //Do nothing
        }

        @Override
        public <C, R> void send(Member destination, CommandMessage<C> command, CommandCallback<? super C, R> callback) {
            if ("fail".equals(command.getPayload())) {
                callback.onResult(command, asCommandResultMessage(new Exception("Failing")));
            } else {
                callback.onResult(command, new GenericCommandResultMessage<>((R) null));
            }
        }

        @Override
        public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
            return null;
        }

        @Override
        public Registration registerHandlerInterceptor(
                MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
            return null;
        }
    }
}
