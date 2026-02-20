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
import org.axonframework.messaging.core.DelayedMessageStream;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.sequencing.SequencingPolicy;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.stubbing.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
@ExtendWith(MockitoExtension.class)
class CommandSequencingInterceptorTest {

    private static final CommandMessage TEST_MESSAGE_1 = new GenericCommandMessage(new MessageType("message"),
                                                                                   "payload");
    private static final CommandMessage TEST_MESSAGE_2 = new GenericCommandMessage(new MessageType("message"),
                                                                                   "payload");
    @Mock
    private SequencingPolicy<CommandMessage> sequencingPolicy;
    @Mock
    private MessageHandlerInterceptorChain<CommandMessage> interceptorChain1;
    @Mock
    private ProcessingContext ctx1;
    @Mock
    private Message exCmd1Result;
    @Captor
    private ArgumentCaptor<Consumer<ProcessingContext>> ctx1CompletionCapture;
    @Mock
    private MessageHandlerInterceptorChain<CommandMessage> interceptorChain2;
    @Mock
    private ProcessingContext ctx2;
    @Mock
    private Message exCmd2Result;
    @Captor
    private ArgumentCaptor<Consumer<ProcessingContext>> ctx2CompletionCapture;

    private CommandSequencingInterceptor<CommandMessage> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new CommandSequencingInterceptor<>(sequencingPolicy);
    }

    @Test
    void interceptOnHandleSerializesCommandExecutionForSameSequence() throws ExecutionException, InterruptedException {
        Object exSequenceIdentifier = "sequenceIdentifier";
        ExecutorService executor = Executors.newFixedThreadPool(2);

        when(sequencingPolicy.sequenceIdentifierFor(any(), any()))
                .thenReturn(Optional.of(exSequenceIdentifier));
        when(interceptorChain1.proceed(any(), any()))
                .thenAnswer((Answer<MessageStream<? extends Message>>) invocation -> MessageStream.just(exCmd1Result));
        when(interceptorChain2.proceed(any(), any()))
                .thenAnswer((Answer<MessageStream<? extends Message>>) invocation -> MessageStream.just(exCmd2Result));

        // handle command 1
        Future<? extends MessageStream<?>> ctx1Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_1, ctx1, interceptorChain1));

        // verify command 1 returns immediately with the result message stream not wrapped in DelayedMessageStream
        await().untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx1Execution.state()));
        MessageStream<?> cmd1ResultStream = ctx1Execution.get();
        assertFalse(cmd1ResultStream instanceof DelayedMessageStream<?>);
        assertTrue(cmd1ResultStream.hasNextAvailable());
        assertEquals(exCmd1Result, cmd1ResultStream.next().orElseThrow().message());
        // verify sequencing policy is called and capture the context completion callback
        verify(sequencingPolicy)
                .sequenceIdentifierFor(TEST_MESSAGE_1, ctx1);
        verify(ctx1)
                .doFinally(ctx1CompletionCapture.capture());
        verify(interceptorChain1)
                .proceed(TEST_MESSAGE_1, ctx1);

        // handle command 2 before releasing ctx1 lock by invoking the context completion callback
        Future<? extends MessageStream<?>> ctx2Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_2, ctx2, interceptorChain2));

        // verify command 2 returns immediately, but with the result message stream wrapped in DelayedMessageStream
        // verify the wrapped command 2 result message stream does not have the result message available yet
        await().untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx2Execution.state()));
        MessageStream<?> cmd2ResultStream = ctx2Execution.get();
        assertInstanceOf(DelayedMessageStream.class, cmd2ResultStream);
        await()
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> assertFalse(cmd2ResultStream.hasNextAvailable()));

        // release lock of ctx1
        ctx1CompletionCapture.getValue().accept(ctx1);

        // verify the wrapped command 2 result message stream has the result available now after releasing the lock
        await()
                .untilAsserted(() -> assertTrue(cmd2ResultStream.hasNextAvailable()));
        assertEquals(exCmd2Result, cmd2ResultStream.next().orElseThrow().message());
        // verify sequencing policy is called and capture the context completion callback
        verify(sequencingPolicy)
                .sequenceIdentifierFor(TEST_MESSAGE_2, ctx2);
        verify(ctx2)
                .doFinally(ctx2CompletionCapture.capture());
        verify(interceptorChain2)
                .proceed(TEST_MESSAGE_2, ctx2);

        // release lock of ctx2
        ctx2CompletionCapture.getValue().accept(ctx2);

        verifyNoMoreInteractions(sequencingPolicy, interceptorChain1, interceptorChain2, ctx1, ctx2);

        executor.shutdown();
    }

    @Test
    void interceptOnHandleDoesNotSerializeCommandExecutionForDifferentSequence()
            throws ExecutionException, InterruptedException {
        Object exSequenceIdentifier1 = "sequenceIdentifier1";
        Object exSequenceIdentifier2 = "sequenceIdentifier2";
        ExecutorService executor = Executors.newFixedThreadPool(2);

        when(sequencingPolicy.sequenceIdentifierFor(TEST_MESSAGE_1, ctx1))
                .thenReturn(Optional.of(exSequenceIdentifier1));
        when(sequencingPolicy.sequenceIdentifierFor(TEST_MESSAGE_2, ctx2))
                .thenReturn(Optional.of(exSequenceIdentifier2));
        when(interceptorChain1.proceed(any(), any()))
                .thenAnswer((Answer<MessageStream<? extends Message>>) invocation -> MessageStream.just(exCmd1Result));
        when(interceptorChain2.proceed(any(), any()))
                .thenAnswer((Answer<MessageStream<? extends Message>>) invocation -> MessageStream.just(exCmd2Result));

        // handle command 1
        Future<? extends MessageStream<?>> ctx1Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_1, ctx1, interceptorChain1));

        // verify command 1 returns immediately with the result message stream not wrapped in DelayedMessageStream
        await().untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx1Execution.state()));
        MessageStream<?> cmd1ResultStream = ctx1Execution.get();
        assertFalse(cmd1ResultStream instanceof DelayedMessageStream<?>);
        assertTrue(cmd1ResultStream.hasNextAvailable());
        assertEquals(exCmd1Result, cmd1ResultStream.next().orElseThrow().message());
        // verify sequencing policy is called and capture the context completion callback
        verify(sequencingPolicy)
                .sequenceIdentifierFor(TEST_MESSAGE_1, ctx1);
        verify(ctx1)
                .doFinally(ctx1CompletionCapture.capture());
        verify(interceptorChain1)
                .proceed(TEST_MESSAGE_1, ctx1);

        // handle command 2
        Future<? extends MessageStream<?>> ctx2Execution =
                executor.submit(() -> testSubject.interceptOnHandle(TEST_MESSAGE_2, ctx2, interceptorChain2));

        // verify command 2 returns immediately with the result message stream not wrapped in DelayedMessageStream, despite the lock on ctx1 not yet released
        await().untilAsserted(() -> assertEquals(Future.State.SUCCESS, ctx2Execution.state()));
        MessageStream<?> cmd2ResultStream = ctx2Execution.get();
        assertFalse(cmd2ResultStream instanceof DelayedMessageStream<?>);
        assertTrue(cmd2ResultStream.hasNextAvailable());
        assertEquals(exCmd2Result, cmd2ResultStream.next().orElseThrow().message());
        // verify sequencing policy is called and capture the context completion callback
        verify(sequencingPolicy)
                .sequenceIdentifierFor(TEST_MESSAGE_2, ctx2);
        verify(ctx2)
                .doFinally(ctx2CompletionCapture.capture());
        verify(interceptorChain2)
                .proceed(TEST_MESSAGE_2, ctx2);

        // release lock of ctx1
        ctx1CompletionCapture.getValue().accept(ctx1);
        // release lock of ctx2
        ctx2CompletionCapture.getValue().accept(ctx2);

        verifyNoMoreInteractions(sequencingPolicy, interceptorChain1, interceptorChain2, ctx1, ctx2);

        executor.shutdown();
    }

    @Test
    void interceptOnHandleSkipsSerializationForEmptyIdentifier() {
        ProcessingContext testContext = new StubProcessingContext();
        MessageHandlerInterceptorChain<CommandMessage> handlerInterceptorChain = mock();

        when(sequencingPolicy.sequenceIdentifierFor(any(), any()))
                .thenReturn(Optional.empty());

        testSubject.interceptOnHandle(TEST_MESSAGE_1, testContext, handlerInterceptorChain);

        verify(sequencingPolicy).sequenceIdentifierFor(TEST_MESSAGE_1, testContext);
        verify(handlerInterceptorChain)
                .proceed(TEST_MESSAGE_1, testContext);
        verifyNoMoreInteractions(handlerInterceptorChain, sequencingPolicy);
    }
}