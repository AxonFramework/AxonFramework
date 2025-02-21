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

package org.axonframework.commandhandling.retry;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.retry.RetryScheduler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link RetryingCommandBus}.
 *
 * @author Allard Buijze
 */
class RetryingCommandBusTest {

    private static final MessageType TEST_COMMAND_TYPE = new MessageType("command");
    private static final MessageType TEST_RESULT_TYPE = new MessageType("result");

    private CommandBus delegate;
    private RetryScheduler retryScheduler;
    private RetryingCommandBus testSubject;

    @BeforeEach
    void setUp() {
        delegate = mock();
        retryScheduler = mock();
        testSubject = new RetryingCommandBus(delegate, retryScheduler);
    }

    @Test
    void shouldReturnSuccessResultImmediately() throws ExecutionException, InterruptedException {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "Test");
        Message<Object> result = new GenericMessage<>(TEST_RESULT_TYPE, "OK");
        when(delegate.dispatch(any(), any())).thenAnswer(i -> CompletableFuture.completedFuture(result));

        ProcessingContext processingContext = mock();
        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(testCommand, processingContext);

        assertSame(result, actual.get());
    }

    @Test
    void shouldDelegateToRetrySchedulerOnFailure() throws ExecutionException, InterruptedException {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "Test");
        Message<Object> successResult = new GenericMessage<>(TEST_RESULT_TYPE, "OK");
        when(delegate.dispatch(any(), any()))
                .thenAnswer(i -> CompletableFuture.failedFuture(new MockException("Simulating failure")));
        when(retryScheduler.scheduleRetry(any(), any(), any(), any()))
                .thenAnswer(i -> MessageStream.just(successResult));

        ProcessingContext processingContext = mock();
        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(testCommand, processingContext);

        ArgumentCaptor<RetryScheduler.Dispatcher<CommandMessage<?>, Message<?>>> dispatcherCaptor =
                ArgumentCaptor.forClass(RetryScheduler.Dispatcher.class);
        verify(retryScheduler).scheduleRetry(eq(testCommand),
                                             eq(processingContext),
                                             isA(MockException.class),
                                             dispatcherCaptor.capture());

        verify(delegate, times(1)).dispatch(any(), any());

        dispatcherCaptor.getValue().dispatch(testCommand, processingContext);

        verify(delegate, times(2)).dispatch(any(), any());

        assertSame(successResult, actual.get());
    }

    @Test
    void shouldReturnedFailureIfRetrySchedulerReturnsFailure() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(TEST_COMMAND_TYPE, "Test");
        when(delegate.dispatch(any(), any()))
                .thenAnswer(i -> CompletableFuture.failedFuture(new MockException("Simulating failure")));
        when(retryScheduler.scheduleRetry(any(),
                                          any(),
                                          any(),
                                          any())).thenAnswer(i -> MessageStream.failed(new MockException(
                "Simulating failure")));

        ProcessingContext processingContext = mock();
        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(testCommand, processingContext);

        ArgumentCaptor<RetryScheduler.Dispatcher<CommandMessage<?>, Message<?>>> dispatcherCaptor =
                ArgumentCaptor.forClass(RetryScheduler.Dispatcher.class);
        verify(retryScheduler).scheduleRetry(eq(testCommand),
                                             eq(processingContext),
                                             isA(MockException.class),
                                             dispatcherCaptor.capture());

        verify(delegate, times(1)).dispatch(any(), any());

        dispatcherCaptor.getValue().dispatch(testCommand, processingContext);

        verify(delegate, times(2)).dispatch(any(), any());

        assertTrue(actual.isDone());
        assertTrue(actual.isCompletedExceptionally());
        assertInstanceOf(MockException.class, actual.exceptionNow());
    }

    @Test
    void shouldDelegateHandlerSubscription() {
        QualifiedName testHandlerName = new QualifiedName("handler");
        CommandHandler handler = mock();
        testSubject.subscribe(testHandlerName, handler);

        verify(delegate).subscribe(testHandlerName, handler);
    }

    @Test
    void shouldDescribeItsComponents() {
        ComponentDescriptor descriptor = mock();
        testSubject.describeTo(descriptor);

        verify(descriptor).describeWrapperOf(delegate);
        verify(descriptor).describeProperty("retryScheduler", retryScheduler);
    }
}