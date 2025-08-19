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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link InterceptingCommandBus}.
 *
 * @author Allard Buijze
 */
class InterceptingCommandBusTest {

    private static final MessageType TEST_COMMAND_TYPE = new MessageType("command");

    private InterceptingCommandBus testSubject;
    private CommandBus mockCommandBus;
    private MessageHandlerInterceptor<CommandMessage<?>> handlerInterceptor1;
    private MessageHandlerInterceptor<CommandMessage<?>> handlerInterceptor2;
    private MessageDispatchInterceptor<CommandMessage<?>> dispatchInterceptor1;
    private MessageDispatchInterceptor<CommandMessage<?>> dispatchInterceptor2;

    @BeforeEach
    void setUp() {
        mockCommandBus = mock(CommandBus.class);
        handlerInterceptor1 = spy(new AddMetaDataCountInterceptor<>("handler1", "value"));
        handlerInterceptor2 = spy(new AddMetaDataCountInterceptor<>("handler2", "value"));
        dispatchInterceptor1 = spy(new AddMetaDataCountInterceptor<>("dispatch1", "value"));
        dispatchInterceptor2 = spy(new AddMetaDataCountInterceptor<>("dispatch2", "value"));

        testSubject = new InterceptingCommandBus(mockCommandBus,
                                                 List.of(handlerInterceptor1, handlerInterceptor2),
                                                 List.of(dispatchInterceptor1, dispatchInterceptor2));
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatchInterceptorsInvokedOnDispatch() throws Exception {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "test");
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                asCommandResultMessage("ok")));

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand,
                                                                              StubProcessingContext.forMessage(
                                                                                      testCommand));

        ArgumentCaptor<CommandMessage<?>> dispatchedMessage = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockCommandBus).dispatch(dispatchedMessage.capture(), any());

        CommandMessage<?> actualDispatched = dispatchedMessage.getValue();
        assertEquals(Map.of("dispatch1", "value-0", "dispatch2", "value-1"),
                     actualDispatched.metaData(),
                     "Expected command interceptors to be invoked in registered order");

        assertTrue(result.isDone());
        assertEquals(Map.of("dispatch1", "value-1", "dispatch2", "value-0"),
                     result.get().metaData(),
                     "Expected result interceptors to be invoked in reverse order");
    }

    @Test
    void earlyReturnAvoidsMessageDispatch() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "test");
        doReturn(MessageStream.failed(new MockException("Simulating early return"))).when(dispatchInterceptor2)
                                                                                    .interceptOnDispatch(any(),
                                                                                                         any(),
                                                                                                         any());

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand,
                                                                              StubProcessingContext.forMessage(
                                                                                      testCommand));

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.exceptionNow());
        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
        verify(mockCommandBus, never()).dispatch(any(), any());
    }

    @Test
    void dualProceedCausesDuplicateMessageDispatch() throws Exception {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "test");
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                asCommandResultMessage("ok")));

        doAnswer(i -> {
            i.callRealMethod();
            return i.callRealMethod();
        }).when(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand,
                                                                              StubProcessingContext.forMessage(
                                                                                      testCommand));

        assertTrue(result.isDone());
        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2, times(2)).interceptOnDispatch(any(), any(), any());
        verify(mockCommandBus, times(2)).dispatch(any(), any());

        assertEquals(Map.of("dispatch1", "value-1", "dispatch2", "value-0"),
                     result.get().metaData());
    }

    @Test
    void exceptionsInDispatchInterceptorReturnFailedStream() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "test");
        doThrow(new MockException("Simulating failure in interceptor"))
                .when(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand,
                                                                              StubProcessingContext.forMessage(
                                                                                      testCommand));

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.exceptionNow());

        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
    }

    @Test
    void handlerInterceptorsInvokedOnHandle() throws Exception {
        QualifiedName testHandlerName = new QualifiedName("handler");
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "test");
        AtomicReference<CommandMessage<?>> handledMessage = new AtomicReference<>();
        testSubject.subscribe(testHandlerName,
                              (command, context) -> {
                                  handledMessage.set(command);
                                  return MessageStream.just(asCommandResultMessage("ok"));
                              }
        );

        ArgumentCaptor<CommandHandler> handlerCaptor = ArgumentCaptor.forClass(CommandHandler.class);
        verify(mockCommandBus).subscribe(eq(testHandlerName), handlerCaptor.capture());

        CommandHandler actualHandler = handlerCaptor.getValue();

        ProcessingContext processingContext = mock(ProcessingContext.class);
        var result = actualHandler.handle(testCommand, processingContext);

        CommandMessage<?> actualHandled = handledMessage.get();
        assertEquals(Map.of("handler1", "value-0", "handler2", "value-1"),
                     actualHandled.metaData(),
                     "Expected command interceptors to be invoked in registered order");

        assertEquals(Map.of("handler1", "value-1", "handler2", "value-0"),
                     result.first().asCompletableFuture().get().message().metaData(),
                     "Expected result interceptors to be invoked in reverse order");
    }

    @Test
    void exceptionsInHandlerInterceptorReturnFailedStream() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "Request");
        doThrow(new MockException("Simulating failure in interceptor"))
                .when(handlerInterceptor2).interceptOnHandle(any(), any(), any());

        CommandHandler actualHandler = subscribeHandler(
                (command, context) -> MessageStream.just(asCommandResultMessage("ok"))
        );

        ProcessingContext context = mock(ProcessingContext.class);
        var result = actualHandler.handle(testCommand, context);
        assertTrue(result.first().asCompletableFuture().isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.first().asCompletableFuture().exceptionNow());

        verify(handlerInterceptor1).interceptOnHandle(any(), eq(context), any());
        verify(handlerInterceptor2).interceptOnHandle(any(), eq(context), any());
    }

    @Test
    void dualProceedCausesDuplicateMessageHandling() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "test");
        doAnswer(i -> {
            i.callRealMethod();
            return i.callRealMethod();
        }).when(handlerInterceptor1).interceptOnHandle(any(), any(), any());

        List<CommandMessage<?>> handledMessages = new ArrayList<>();

        CommandHandler actualHandler = subscribeHandler(
                (command, context) -> {
                    handledMessages.add(command);
                    return MessageStream.just(asCommandResultMessage("ok"));
                });

        ProcessingContext processingContext = mock(ProcessingContext.class);
        var result = actualHandler.handle(testCommand, processingContext);

        assertTrue(result.first().asCompletableFuture().isDone());
        verify(handlerInterceptor1).interceptOnHandle(any(), any(), any());
        verify(handlerInterceptor2, times(2)).interceptOnHandle(any(), any(), any());
        assertEquals(2, handledMessages.size());

        assertEquals(Map.of("handler1", "value-0", "handler2", "value-1"),
                     handledMessages.get(0).metaData());
        assertEquals(Map.of("handler1", "value-0", "handler2", "value-1"),
                     handledMessages.get(1).metaData());
        assertEquals(Map.of("handler1", "value-1", "handler2", "value-0"),
                     result.first().asCompletableFuture().join().message().metaData());
    }

    @Test
    void describeIncludesAllRelevantProperties() {
        ComponentDescriptor mockComponentDescriptor = mock(ComponentDescriptor.class);
        testSubject.describeTo(mockComponentDescriptor);

        verify(mockComponentDescriptor).describeWrapperOf(eq(mockCommandBus));
        verify(mockComponentDescriptor).describeProperty(argThat(i -> i.contains("dispatch")),
                                                         eq(List.of(dispatchInterceptor1, dispatchInterceptor2)));
        verify(mockComponentDescriptor).describeProperty(argThat(i -> i.contains("handler")),
                                                         eq(List.of(handlerInterceptor1, handlerInterceptor2)));
    }

    /**
     * Subscribes the given handler with the command bus and returns the handler as it is subscribed with its delegate
     *
     * @param handler The handling logic for the command
     * @return the handler as wrapped by the surrounding command bus
     */
    private CommandHandler subscribeHandler(CommandHandler handler) {
        QualifiedName name = new QualifiedName("handler");
        testSubject.subscribe(name, handler);

        ArgumentCaptor<CommandHandler> handlerCaptor = ArgumentCaptor.forClass(CommandHandler.class);
        verify(mockCommandBus).subscribe(eq(name), handlerCaptor.capture());
        return handlerCaptor.getValue();
    }

    private static GenericCommandResultMessage<String> asCommandResultMessage(String payload) {
        return new GenericCommandResultMessage<>(new MessageType(payload.getClass()), payload);
    }

    @SuppressWarnings("unchecked")
    private static class AddMetaDataCountInterceptor<M extends CommandMessage<?>>
            implements MessageHandlerInterceptor<M>, MessageDispatchInterceptor<M> {

        private final String key;
        private final String value;

        public AddMetaDataCountInterceptor(String key, String prefix) {
            this.key = key;
            this.value = prefix;
        }

        @Override
        @Nonnull
        public MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<M> interceptorChain) {
            return interceptorChain.proceed((M) message.andMetaData(Map.of(key, buildValue(message))), context)
                                   .mapMessage(m -> ((Message<?>) m).andMetaData(Map.of(key, buildValue(m))));
        }

        @Override
        @Nonnull
        public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<M> interceptorChain) {
            return interceptorChain.proceed((M) message.andMetaData(Map.of(key, buildValue(message))), context)
                                   .mapMessage(m -> m.andMetaData(Map.of(key, buildValue(m))));
        }

        private String buildValue(Message<?> message) {
            return value + "-" + message.metaData().size();
        }
    }
}