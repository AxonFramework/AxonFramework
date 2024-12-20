/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterceptingCommandBusTest {

    private static final QualifiedName TEST_COMMAND_NAME = new QualifiedName("test", "command", "0.0.1");

    private InterceptingCommandBus testSubject;
    private CommandBus mockCommandBus;
    private MessageHandlerInterceptor<Message<?>> handlerInterceptor1;
    private MessageHandlerInterceptor<CommandMessage<?>> handlerInterceptor2;
    private MessageDispatchInterceptor<CommandMessage<?>> dispatchInterceptor1;
    private MessageDispatchInterceptor<Message<?>> dispatchInterceptor2;

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
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_NAME, "test");
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                GenericCommandResultMessage.asCommandResultMessage("ok")));

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand, ProcessingContext.NONE);

        ArgumentCaptor<CommandMessage<?>> dispatchedMessage = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockCommandBus).dispatch(dispatchedMessage.capture(), any());

        CommandMessage<?> actualDispatched = dispatchedMessage.getValue();
        assertEquals(Map.of("dispatch1", "value-0", "dispatch2", "value-1"),
                     actualDispatched.getMetaData(),
                     "Expected command interceptors to be invoked in registered order");

        assertTrue(result.isDone());
        assertEquals(Map.of("dispatch1", "value-1", "dispatch2", "value-0"),
                     result.get().getMetaData(),
                     "Expected result interceptors to be invoked in reverse order");
    }

    @Test
    void earlyReturnAvoidsMessageDispatch() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_NAME, "test");
        doReturn(MessageStream.failed(new MockException("Simulating early return"))).when(dispatchInterceptor2)
                                                                                    .interceptOnDispatch(any(),
                                                                                                         any(),
                                                                                                         any());

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand, ProcessingContext.NONE);

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.exceptionNow());
        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
        verify(mockCommandBus, never()).dispatch(any(), any());
    }

    @Test
    void dualProceedCausesDuplicateMessageDispatch() throws Exception {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_NAME, "test");
        when(mockCommandBus.dispatch(any(), any())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                GenericCommandResultMessage.asCommandResultMessage("ok")));

        doAnswer(i -> {
            i.callRealMethod();
            return i.callRealMethod();
        }).when(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand, ProcessingContext.NONE);

        assertTrue(result.isDone());
        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2, times(2)).interceptOnDispatch(any(), any(), any());
        verify(mockCommandBus, times(2)).dispatch(any(), any());

        assertEquals(Map.of("dispatch1", "value-1", "dispatch2", "value-0"),
                     result.get().getMetaData());
    }

    @Test
    void exceptionsInDispatchInterceptorReturnFailedStream() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_NAME, "test");
        doThrow(new MockException("Simulating failure in interceptor"))
                .when(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());

        CompletableFuture<? extends Message<?>> result = testSubject.dispatch(testCommand, ProcessingContext.NONE);

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.exceptionNow());

        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void handlerInterceptorsInvokedOnHandle() throws Exception {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_NAME, "test");
        AtomicReference<CommandMessage<?>> handledMessage = new AtomicReference<>();
        testSubject.subscribe("test", message -> {
            handledMessage.set(message);
            return GenericCommandResultMessage.asCommandResultMessage("ok");
        });

        ArgumentCaptor<MessageHandler<CommandMessage<?>, CommandResultMessage<?>>> handlerCaptor = ArgumentCaptor.forClass(
                MessageHandler.class);
        verify(mockCommandBus).subscribe(eq("test"), handlerCaptor.capture());

        MessageHandler<CommandMessage<?>, CommandResultMessage<?>> actualHandler = handlerCaptor.getValue();

        ProcessingContext processingContext = mock(ProcessingContext.class);
        var result = actualHandler.handle(testCommand, processingContext);

        CommandMessage<?> actualHandled = handledMessage.get();
        assertEquals(Map.of("handler1", "value-0", "handler2", "value-1"),
                     actualHandled.getMetaData(),
                     "Expected command interceptors to be invoked in registered order");

        assertEquals(Map.of("handler1", "value-1", "handler2", "value-0"),
                     result.firstAsCompletableFuture().get().message().getMetaData(),
                     "Expected result interceptors to be invoked in reverse order");
    }

    @Test
    void exceptionsInHandlerInterceptorReturnFailedStream() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_NAME, "Request");
        doThrow(new MockException("Simulating failure in interceptor"))
                .when(handlerInterceptor2).interceptOnHandle(any(), any(), any());

        MessageHandler<CommandMessage<?>, CommandResultMessage<?>> actualHandler = subscribeHandler(
                message -> GenericCommandResultMessage.asCommandResultMessage("ok"));

        ProcessingContext context = mock(ProcessingContext.class);
        var result = actualHandler.handle(testCommand, context);
        assertTrue(result.firstAsCompletableFuture().isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.firstAsCompletableFuture().exceptionNow());

        verify(handlerInterceptor1).interceptOnHandle(any(), eq(context), any());
        verify(handlerInterceptor2).interceptOnHandle(any(), eq(context), any());
    }

    @Test
    void dualProceedCausesDuplicateMessageHandling() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_NAME, "test");
        doAnswer(i -> {
            i.callRealMethod();
            return i.callRealMethod();
        }).when(handlerInterceptor1).interceptOnHandle(any(), any(), any());

        List<CommandMessage<?>> handledMessages = new ArrayList<>();

        MessageHandler<CommandMessage<?>, CommandResultMessage<?>> actualHandler = subscribeHandler(
                message -> {
                    handledMessages.add(message);
                    return GenericCommandResultMessage.asCommandResultMessage("ok");
                });

        ProcessingContext processingContext = mock(ProcessingContext.class);
        var result = actualHandler.handle(testCommand, processingContext);

        assertTrue(result.firstAsCompletableFuture().isDone());
        verify(handlerInterceptor1).interceptOnHandle(any(), any(), any());
        verify(handlerInterceptor2, times(2)).interceptOnHandle(any(), any(), any());
        assertEquals(2, handledMessages.size());

        assertEquals(Map.of("handler1", "value-0", "handler2", "value-1"),
                     handledMessages.get(0).getMetaData());
        assertEquals(Map.of("handler1", "value-0", "handler2", "value-1"),
                     handledMessages.get(1).getMetaData());
        assertEquals(Map.of("handler1", "value-1", "handler2", "value-0"),
                     result.firstAsCompletableFuture().join().message().getMetaData());
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
    @SuppressWarnings("unchecked")
    private MessageHandler<CommandMessage<?>, CommandResultMessage<?>> subscribeHandler(
            MessageHandler<CommandMessage<?>, CommandResultMessage<?>> handler) {
        testSubject.subscribe("test", handler);

        ArgumentCaptor<MessageHandler<CommandMessage<?>, CommandResultMessage<?>>> handlerCaptor = ArgumentCaptor.forClass(
                MessageHandler.class);
        verify(mockCommandBus).subscribe(eq("test"), handlerCaptor.capture());
        return handlerCaptor.getValue();
    }

    private static class AddMetaDataCountInterceptor<M extends Message<?>>
            implements MessageHandlerInterceptor<M>, MessageDispatchInterceptor<M> {

        private final String key;
        private final String value;

        public AddMetaDataCountInterceptor(String key, String prefix) {
            this.key = key;
            this.value = prefix;
        }

        @Override
        public Object handle(@Nonnull UnitOfWork<? extends M> unitOfWork, @Nonnull InterceptorChain interceptorChain) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <M1 extends M, R extends Message<?>> MessageStream<? extends R> interceptOnDispatch(@Nonnull M1 message,
                                                                                                   @Nullable ProcessingContext context,
                                                                                                   @Nonnull InterceptorChain<M1, R> interceptorChain) {
            return interceptorChain.proceed((M1) message.andMetaData(Map.of(key, buildValue(message))), context)
                                   .mapMessage(m -> (R) ((Message<?>) m).andMetaData(Map.of(key, buildValue(m))));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <M1 extends M, R extends Message<?>> MessageStream<? extends R> interceptOnHandle(@Nonnull M1 message,
                                                                                                 @Nonnull ProcessingContext context,
                                                                                                 @Nonnull InterceptorChain<M1, R> interceptorChain) {
            return interceptorChain.proceed((M1) message.andMetaData(Map.of(key, buildValue(message))), context)
                                   .mapMessage(m -> (R) m.andMetaData(Map.of(key, buildValue(m))));
        }

        private String buildValue(Message<?> message) {
            return value + "-" + message.getMetaData().size();
        }

        @Nonnull
        @Override
        public BiFunction<Integer, M, M> handle(@Nonnull List<? extends M> messages) {
            throw new UnsupportedOperationException();
        }
    }
}