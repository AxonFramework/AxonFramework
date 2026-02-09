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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.*;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.common.util.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.core.MessagingTestUtils.commandResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link InterceptingCommandBus}.
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 */
class InterceptingCommandBusTest {

    private static final MessageType TEST_COMMAND_TYPE = new MessageType("command");

    private InterceptingCommandBus testSubject;
    private CommandBus mockCommandBus;
    private MessageDispatchInterceptor<Message> dispatchInterceptor1;
    private MessageDispatchInterceptor<Message> dispatchInterceptor2;

    @BeforeEach
    void setUp() {
        mockCommandBus = mock(CommandBus.class);
        dispatchInterceptor1 = spy(new AddMetadataCountInterceptor<>("dispatch1", "value"));
        dispatchInterceptor2 = spy(new AddMetadataCountInterceptor<>("dispatch2", "value"));

        testSubject = new InterceptingCommandBus(mockCommandBus,
                                                 List.of(dispatchInterceptor1, dispatchInterceptor2));
    }

    @Test
    void dispatchInterceptorsInvokedOnDispatch() throws Exception {
        when(mockCommandBus.dispatch(any(), any()))
                .thenAnswer(invocation -> completedFuture(commandResult("ok")));

        CommandMessage testCommand = new GenericCommandMessage(TEST_COMMAND_TYPE, "test");
        CompletableFuture<CommandResultMessage> result = testSubject
                .dispatch(testCommand, StubProcessingContext.forMessage(testCommand));

        ArgumentCaptor<CommandMessage> dispatchedMessage = ArgumentCaptor.forClass(CommandMessage.class);
        verify(mockCommandBus).dispatch(dispatchedMessage.capture(), any());

        CommandMessage actualDispatched = dispatchedMessage.getValue();
        assertEquals(Metadata.from(
                             Map.of("dispatch1", "value-0",
                                    "dispatch2", "value-1")
                     ),
                     actualDispatched.metadata(),
                     "Expected command interception to be invoked in registered order");

        assertTrue(result.isDone());
        assertEquals(Map.of("dispatch1", "value-1", "dispatch2", "value-0"),
                     result.get().metadata(),
                     "Expected result interception to be invoked in reverse order");
    }

    @Test
    void dispatchInterceptorsAreInvokedForEveryMessage() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        MessageDispatchInterceptor<Message> countingInterceptor = (message, context, chain) -> {
            counter.incrementAndGet();
            return chain.proceed(message, context);
        };
        InterceptingCommandBus countingTestSubject =
                new InterceptingCommandBus(mockCommandBus, List.of(countingInterceptor));

        when(mockCommandBus.dispatch(any(), any()))
                .thenAnswer(invocation -> completedFuture(commandResult("ok")));

        CommandMessage firstCommand = new GenericCommandMessage(TEST_COMMAND_TYPE, "first");
        CommandMessage secondCommand = new GenericCommandMessage(TEST_COMMAND_TYPE, "second");

        countingTestSubject.dispatch(firstCommand, StubProcessingContext.forMessage(firstCommand))
                           .get();
        countingTestSubject.dispatch(secondCommand, StubProcessingContext.forMessage(secondCommand))
                           .get();

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void earlyReturnAvoidsMessageDispatch() {
        CommandMessage testCommand = new GenericCommandMessage(TEST_COMMAND_TYPE, "test");
        doReturn(MessageStream.failed(new MockException("Simulating early return"))).when(dispatchInterceptor2)
                                                                                    .interceptOnDispatch(any(),
                                                                                                         any(),
                                                                                                         any());

        CompletableFuture<? extends Message> result =
                testSubject.dispatch(testCommand, StubProcessingContext.forMessage(testCommand));

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.exceptionNow());
        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
        verify(mockCommandBus, never()).dispatch(any(), any());
    }

    @Test
    void exceptionsInDispatchInterceptorReturnFailedStream() {
        CommandMessage testCommand = new GenericCommandMessage(TEST_COMMAND_TYPE, "test");
        doThrow(new MockException("Simulating failure in interceptor"))
                .when(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());

        CompletableFuture<? extends Message> result =
                testSubject.dispatch(testCommand, StubProcessingContext.forMessage(testCommand));

        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(MockException.class, result.exceptionNow());

        verify(dispatchInterceptor1).interceptOnDispatch(any(), any(), any());
        verify(dispatchInterceptor2).interceptOnDispatch(any(), any(), any());
    }

    @Test
    void subscribeDelegatesDirectlyToDelegate() {
        QualifiedName testHandlerName = new QualifiedName("handler");
        CommandHandler handler = (command, context) -> MessageStream.just(commandResult("ok"));

        // when
        testSubject.subscribe(testHandlerName, handler);

        // then — handler is passed directly without wrapping
        verify(mockCommandBus).subscribe(eq(testHandlerName), eq(handler));
    }

    @Test
    void describeIncludesAllRelevantProperties() {
        ComponentDescriptor mockComponentDescriptor = mock(ComponentDescriptor.class);
        testSubject.describeTo(mockComponentDescriptor);

        verify(mockComponentDescriptor).describeWrapperOf(eq(mockCommandBus));
        verify(mockComponentDescriptor).describeProperty(argThat(i -> i.contains("dispatch")),
                                                         eq(List.of(dispatchInterceptor1, dispatchInterceptor2)));
    }


    @SuppressWarnings("unchecked")
    private static class AddMetadataCountInterceptor<M extends Message>
            implements MessageHandlerInterceptor<M>, MessageDispatchInterceptor<M> {

        private final String key;
        private final String value;

        public AddMetadataCountInterceptor(String key, String prefix) {
            this.key = key;
            this.value = prefix;
        }

        @Override
        @Nonnull
        public MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<M> interceptorChain) {
            var intercepted = (M) message.andMetadata(Map.of(key, buildValue(message)));
            return interceptorChain
                    .proceed(intercepted, context)
                    .mapMessage(m -> m.andMetadata(Map.of(key, buildValue(m))));
        }

        @Override
        @Nonnull
        public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<M> interceptorChain) {
            var intercepted = (M) message.andMetadata(Map.of(key, buildValue(message)));
            return interceptorChain
                    .proceed(intercepted, context)
                    .mapMessage(m -> m.andMetadata(Map.of(key, buildValue(m))));
        }

        private String buildValue(Message message) {
            return value + "-" + message.metadata().size();
        }
    }
}