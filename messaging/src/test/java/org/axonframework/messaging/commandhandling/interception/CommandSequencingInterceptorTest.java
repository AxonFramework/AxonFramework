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

package org.axonframework.messaging.commandhandling.interception;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.sequencing.CommandSequencingPolicy;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link CommandSequencingInterceptor}.
 *
 * @author Jakob Hatzl
 */
class CommandSequencingInterceptorTest {

    private static final CommandMessage TEST_MESSAGE_1 = new GenericCommandMessage(new MessageType("message"),
                                                                                   "payload");
    private static final CommandMessage TEST_MESSAGE_2 = new GenericCommandMessage(new MessageType("message"),
                                                                                   "payload");
    private CommandSequencingPolicy sequencingPolicy;

    private CommandSequencingInterceptor<CommandMessage> testSubject;

    @BeforeEach
    void setUp() {
        sequencingPolicy = mock();
        testSubject = new CommandSequencingInterceptor<>(sequencingPolicy);
    }

    @Test
    void interceptOnHandleSerializesCommandExecutionForSameSequence() {
        final MessageHandlerInterceptorChain<CommandMessage> interceptorChain1 = mock();
        final ProcessingContext ctx1 = mock();
        final MessageHandlerInterceptorChain<CommandMessage> interceptorChain2 = mock();
        final ProcessingContext ctx2 = mock();
        final Object exSequenceIdentifier = "sequenceIdentifier";
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final ArgumentCaptor<Consumer<ProcessingContext>> ctx1CompleteCapture = ArgumentCaptor.forClass(Consumer.class);
        final ArgumentCaptor<Consumer<ProcessingContext>> ctx2CompleteCapture = ArgumentCaptor.forClass(Consumer.class);

        when(sequencingPolicy.getSequenceIdentifierFor(any(), any()))
                .thenReturn(Optional.of(exSequenceIdentifier));
        when(ctx1.whenComplete(any()))
                .thenReturn(ctx1);
        when(ctx2.whenComplete(any()))
                .thenReturn(ctx2);

        // handle first command
        Future<? extends MessageStream<?>> ctx1Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_1, ctx1, interceptorChain1));
        // verify command 1 completes immediately and capture the context completion / error callbacks
        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx1Execution.state()));
        verify(sequencingPolicy)
                .getSequenceIdentifierFor(TEST_MESSAGE_1, ctx1);
        verify(ctx1)
                .whenComplete(ctx1CompleteCapture.capture());
        verify(ctx1)
                .onError(any());
        verify(interceptorChain1)
                .proceed(TEST_MESSAGE_1, ctx1);

        // verify command 2 is blocked until lock of ctx1 is released
        Future<? extends MessageStream<?>> ctx2Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_2, ctx2, interceptorChain2));
        await()
                .pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertEquals(Future.State.RUNNING, ctx2Execution.state()));

        // release lock of ctx1
        ctx1CompleteCapture.getValue().accept(ctx1);

        // verify command 2 is completed and capture the context completion / error callbacks
        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx2Execution.state()));
        verify(sequencingPolicy)
                .getSequenceIdentifierFor(TEST_MESSAGE_2, ctx2);
        verify(ctx2)
                .whenComplete(ctx2CompleteCapture.capture());
        verify(ctx2)
                .onError(any());
        verify(interceptorChain2)
                .proceed(TEST_MESSAGE_2, ctx2);

        // release lock of ctx2
        ctx2CompleteCapture.getValue().accept(ctx2);

        verifyNoMoreInteractions(sequencingPolicy, interceptorChain1, interceptorChain2, ctx1, ctx2);

        executor.shutdown();
    }

    @Test
    void interceptOnHandleReleasesSerializationLockOnCtxErrorCallback() {
        final MessageHandlerInterceptorChain<CommandMessage> interceptorChain1 = mock();
        final ProcessingContext ctx1 = mock();
        final MessageHandlerInterceptorChain<CommandMessage> interceptorChain2 = mock();
        final ProcessingContext ctx2 = mock();
        final Object exSequenceIdentifier = "sequenceIdentifier";
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final ArgumentCaptor<ProcessingLifecycle.ErrorHandler> ctx1CompleteCapture = ArgumentCaptor.forClass(
                ProcessingLifecycle.ErrorHandler.class);
        final ArgumentCaptor<Consumer<ProcessingContext>> ctx2CompleteCapture = ArgumentCaptor.forClass(Consumer.class);

        when(sequencingPolicy.getSequenceIdentifierFor(any(), any()))
                .thenReturn(Optional.of(exSequenceIdentifier));
        when(ctx1.whenComplete(any()))
                .thenReturn(ctx1);
        when(ctx2.whenComplete(any()))
                .thenReturn(ctx2);

        // handle first command
        Future<? extends MessageStream<?>> ctx1Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_1, ctx1, interceptorChain1));
        // verify command 1 completes immediately and capture the context completion / error callbacks
        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx1Execution.state()));
        verify(sequencingPolicy)
                .getSequenceIdentifierFor(TEST_MESSAGE_1, ctx1);
        verify(ctx1)
                .whenComplete(any());
        verify(ctx1)
                .onError(ctx1CompleteCapture.capture());
        verify(interceptorChain1)
                .proceed(TEST_MESSAGE_1, ctx1);

        // verify command 2 is blocked until lock of ctx1 is released
        Future<? extends MessageStream<?>> ctx2Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_2, ctx2, interceptorChain2));
        await()
                .pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertEquals(Future.State.RUNNING, ctx2Execution.state()));

        // release lock of ctx1 via error callback, arguments are ignored in implementation
        ctx1CompleteCapture.getValue().handle(ctx1, mock(), mock());

        // verify command 2 is completed and capture the context completion / error callbacks
        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx2Execution.state()));
        verify(sequencingPolicy)
                .getSequenceIdentifierFor(TEST_MESSAGE_2, ctx2);
        verify(ctx2)
                .whenComplete(ctx2CompleteCapture.capture());
        verify(ctx2)
                .onError(any());
        verify(interceptorChain2)
                .proceed(TEST_MESSAGE_2, ctx2);

        // release lock of ctx2
        ctx2CompleteCapture.getValue().accept(ctx2);

        verifyNoMoreInteractions(sequencingPolicy, interceptorChain1, interceptorChain2, ctx1, ctx2);

        executor.shutdown();
    }

    @Test
    void interceptOnHandleDoesNotSerializeCommandExecutionForDifferentSequence() {
        final MessageHandlerInterceptorChain<CommandMessage> interceptorChain1 = mock();
        final ProcessingContext ctx1 = mock();
        final MessageHandlerInterceptorChain<CommandMessage> interceptorChain2 = mock();
        final ProcessingContext ctx2 = mock();
        final Object exSequenceIdentifier1 = "sequenceIdentifier1";
        final Object exSequenceIdentifier2 = "sequenceIdentifier2";
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final ArgumentCaptor<Consumer<ProcessingContext>> ctx1CompleteCapture = ArgumentCaptor.forClass(Consumer.class);
        final ArgumentCaptor<Consumer<ProcessingContext>> ctx2CompleteCapture = ArgumentCaptor.forClass(Consumer.class);

        when(sequencingPolicy.getSequenceIdentifierFor(eq(TEST_MESSAGE_1), eq(ctx1)))
                .thenReturn(Optional.of(exSequenceIdentifier1));
        when(sequencingPolicy.getSequenceIdentifierFor(eq(TEST_MESSAGE_2), eq(ctx2)))
                .thenReturn(Optional.of(exSequenceIdentifier2));
        when(ctx1.whenComplete(any()))
                .thenReturn(ctx1);
        when(ctx2.whenComplete(any()))
                .thenReturn(ctx2);

        // handle command 1
        Future<? extends MessageStream<?>> ctx1Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_1, ctx1, interceptorChain1));

        // verify command1 completes immediately and capture the context completion / error callbacks
        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx1Execution.state()));
        verify(sequencingPolicy)
                .getSequenceIdentifierFor(TEST_MESSAGE_1, ctx1);
        verify(ctx1)
                .whenComplete(ctx1CompleteCapture.capture());
        verify(ctx1)
                .onError(any());
        verify(interceptorChain1)
                .proceed(TEST_MESSAGE_1, ctx1);

        // handle command 2
        Future<? extends MessageStream<?>> ctx2Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_2, ctx2, interceptorChain2));
        // verify command 2 completes immediately independently of ctx1 and capture the context completion / error callbacks
        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx2Execution.state()));
        verify(sequencingPolicy)
                .getSequenceIdentifierFor(TEST_MESSAGE_2, ctx2);
        verify(ctx2)
                .whenComplete(ctx2CompleteCapture.capture());
        verify(ctx2)
                .onError(any());
        verify(interceptorChain2)
                .proceed(TEST_MESSAGE_2, ctx2);

        // release lock of ctx1
        ctx1CompleteCapture.getValue().accept(ctx1);
        // release lock of ctx2
        ctx2CompleteCapture.getValue().accept(ctx2);

        verifyNoMoreInteractions(sequencingPolicy, interceptorChain1, interceptorChain2, ctx1, ctx2);

        executor.shutdown();
    }

    @Test
    void interceptOnHandleSkipsSerializationForEmptyIdentifier() {
        final ProcessingContext testContext = new StubProcessingContext();
        final MessageHandlerInterceptorChain<CommandMessage> handlerInterceptorChain = mock();

        when(sequencingPolicy.getSequenceIdentifierFor(any(), any()))
                .thenReturn(Optional.empty());

        testSubject.interceptOnHandle(TEST_MESSAGE_1, testContext, handlerInterceptorChain);

        verify(sequencingPolicy).getSequenceIdentifierFor(TEST_MESSAGE_1, testContext);
        verify(handlerInterceptorChain)
                .proceed(TEST_MESSAGE_1, testContext);
        verifyNoMoreInteractions(handlerInterceptorChain, sequencingPolicy);
    }
}