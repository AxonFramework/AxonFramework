/*
 * Copyright (c) 2012-2017. Axon Framework
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

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class DistributedCommandBusTest {

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

    @Before
    public void setUp() {
        testSubject = new DistributedCommandBus(mockCommandRouter, mockConnector, mockMessageMonitor);
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);
    }

    @Test
    public void testDispatchWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
    }

    @Test
    public void testDispatchFailingCommandWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
    }

    @Test
    public void testDispatchWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        testSubject = new DistributedCommandBus(mockCommandRouter, mockConnector);
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();
    }

    @Test
    public void testUnknownCommandWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        testSubject = new DistributedCommandBus(mockCommandRouter, mockConnector);
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("unknown");
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());

        try {
            testSubject.dispatch(testCommandMessage);
            fail("Expected NoHandlerForCommandException");
        } catch (NoHandlerForCommandException e) {
            // expected
        }

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();
    }

    @Test
    public void testDispatchWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");

        CommandCallback mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
        verify(mockCallback).onSuccess(testCommandMessage, null);
    }

    @Test
    public void testUnknownCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());

        CommandCallback mockCallback = mock(CommandCallback.class);
        try {
            testSubject.dispatch(testCommandMessage);
            fail("Expected NoHandlerForCommandException");
        } catch (NoHandlerForCommandException e) {
            // expected
        }

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(any());
        verify(mockCallback, never()).onFailure(any(), any());
        verify(mockCallback, never()).onSuccess(any(), any());
    }

    @Test
    public void testDispatchFailingCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");

        CommandCallback mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
        verify(mockCallback).onFailure(eq(testCommandMessage), isA(Exception.class));
    }

    private static class StubCommandBusConnector implements CommandBusConnector {
        @Override
        public <C> void send(Member destination, CommandMessage<? extends C> command) {
            //Do nothing
        }

        @Override
        public <C, R> void send(Member destination, CommandMessage<C> command, CommandCallback<? super C, R> callback) {
            if ("fail".equals(command.getPayload())) {
                callback.onFailure(command, new Exception("Failing"));
            } else {
                callback.onSuccess(command, null);
            }
        }

        @Override
        public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
            return null;
        }

        @Override
        public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
            return null;
        }
    }
}
