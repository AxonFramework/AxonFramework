/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.Optional;
import javax.annotation.Nonnull;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.axonframework.commandhandling.distributed.DistributedCommandBus.INITIAL_LOAD_FACTOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DistributedCommandBus}.
 *
 * @author Allard Buijze
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class DistributedCommandBusTest {

    private DistributedCommandBus testSubject;

    @Mock
    private CommandRouter mockCommandRouter;
    @Spy
    private final CommandBusConnector mockConnector = new StubCommandBusConnector();
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
    }

    @Test
    void dispatchWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, NoOpCallback.INSTANCE);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
    }

    @Test
    void defaultCallbackIsUsedWhenFireAndForget() {
        CommandMessage<Object> message = GenericCommandMessage.asCommandMessage("test");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .messageMonitor(mockMessageMonitor)
                                           .defaultCommandCallback(mockCallback)
                                           .build();

        testSubject.dispatch(message);

        verify(mockCallback).onResult(eq(message), any());
    }

    @Test
    void dispatchFailingCommandWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, NoOpCallback.INSTANCE);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
    }

    @Test
    void dispatchWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .build();

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();
    }

    @Test
    void unknownCommandWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("unknown");
        CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .build();

        testSubject.dispatch(testCommandMessage, callback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();

        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void dispatchWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertFalse(commandResultMessageCaptor.getValue().isExceptional());
        assertNull(commandResultMessageCaptor.getValue().getPayload());
    }

    @Test
    void unknownCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(any());
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void dispatchFailingCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertEquals(Exception.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void localSegmentReturnsTheCommandBusConnectorsLocalSegmentResult() {
        CommandBus expectedLocalSegment = mock(CommandBus.class);
        when(mockConnector.localSegment()).thenReturn(Optional.of(expectedLocalSegment));

        CommandBus resultLocalSegment = testSubject.localSegment();

        assertEquals(expectedLocalSegment, resultLocalSegment);
    }

    @Test
    void disconnectRemovesAllSubscribedCommandHandlers() {
        testSubject.disconnect();
        verify(mockCommandRouter).updateMembership(INITIAL_LOAD_FACTOR, DenyAll.INSTANCE);
    }

    @Test
    void shutdownDispatchingInitiatesShutdownOfCommandBusConnector() {
        testSubject.shutdownDispatching();
        verify(mockConnector).initiateShutdown();
    }

    @Test
    void loadFactorDefault() {
        assertEquals(INITIAL_LOAD_FACTOR, testSubject.getLoadFactor());
    }

    @Test
    void updateLoadFactor() {
        int expectedLoadFactor = 42;

        testSubject.updateLoadFactor(expectedLoadFactor);

        assertEquals(expectedLoadFactor, testSubject.getLoadFactor());
    }

    private static class StubCommandBusConnector implements CommandBusConnector {

        @Override
        public <C> void send(@Nonnull Member destination, @Nonnull CommandMessage<? extends C> command) {
            //Do nothing
        }

        @Override
        public <C, R> void send(@Nonnull Member destination, @Nonnull CommandMessage<C> command,
                                @Nonnull CommandCallback<? super C, R> callback) {
            if ("fail".equals(command.getPayload())) {
                callback.onResult(command, asCommandResultMessage(new Exception("Failing")));
            } else {
                callback.onResult(command, new GenericCommandResultMessage<>((R) null));
            }
        }

        @Override
        public Registration subscribe(@Nonnull String commandName,
                                      @Nonnull MessageHandler<? super CommandMessage<?>> handler) {
            return null;
        }

        @Override
        public Registration registerHandlerInterceptor(
                @Nonnull MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor
        ) {
            return null;
        }
    }
}
