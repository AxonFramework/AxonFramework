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

package org.axonframework.commandhandling.tracing;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.TestSpanFactory;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TracingCommandBusTest {

    private TracingCommandBus testSubject;
    private TestSpanFactory spanFactory;
    private CommandBus delegate;
    private DefaultCommandBusSpanFactory commandBusSpanFactory;

    @BeforeEach
    void setUp() {
        delegate = mock(CommandBus.class);
        spanFactory = new TestSpanFactory();
        commandBusSpanFactory = DefaultCommandBusSpanFactory.builder()
                                                            .spanFactory(spanFactory)
                                                            .build();
        testSubject = new TracingCommandBus(delegate, commandBusSpanFactory);
    }

    @Test
    void dispatchIsCorrectlyTraced() {
        when(delegate.dispatch(any(), any())).thenAnswer(
                i -> {
                    spanFactory.verifySpanActive("CommandBus.dispatchCommand");
                    spanFactory.verifySpanPropagated("CommandBus.dispatchCommand",
                                                     i.getArgument(0, CommandMessage.class));
                    return FutureUtils.emptyCompletedFuture();
                }
        );
        testSubject.dispatch(asCommandMessage("Say hi!"), ProcessingContext.NONE);
        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
    }


    @Test
    void dispatchIsCorrectlyTracedDuringException() {
        when(delegate.dispatch(any(), any()))
                .thenAnswer(i -> {
                    spanFactory.verifySpanPropagated("CommandBus.dispatchCommand",
                                                     i.getArgument(0, CommandMessage.class));
                    return CompletableFuture.failedFuture(new RuntimeException("Some exception"));
                });
        testSubject.subscribe(String.class.getName(), command -> {
            throw new RuntimeException("Some exception");
        });
        var actual = testSubject.dispatch(asCommandMessage("Say hi!"), ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());

        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
        spanFactory.verifySpanHasException("CommandBus.dispatchCommand", RuntimeException.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void verifyHandlerSpansAreCreatedOnHandlerInvocation() {
        ArgumentCaptor<MessageHandler> captor = ArgumentCaptor.forClass(MessageHandler.class);
        when(delegate.subscribe(anyString(), captor.capture())).thenReturn(null);

        testSubject.subscribe("test", new MessageHandler<>() {
            @Override
            public Object handleSync(CommandMessage<?> message) {
                return null;
            }

            @Override
            public MessageStream<? extends Message<?>> handle(CommandMessage<?> message,
                                                              ProcessingContext processingContext) {
                spanFactory.verifySpanActive("CommandBus.handleCommand");
                return MessageStream.just(new GenericMessage<>(dottedName("test.message"), "ok"));
            }
        });

        captor.getValue().handle(GenericCommandMessage.asCommandMessage("Test"), ProcessingContext.NONE);
        spanFactory.verifySpanCompleted("CommandBus.handleCommand");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void verifyHandlerSpansAreCompletedOnExceptionInHandlerInvocation() {
        ArgumentCaptor<MessageHandler> captor = ArgumentCaptor.forClass(MessageHandler.class);
        when(delegate.subscribe(anyString(), captor.capture())).thenReturn(null);

        testSubject.subscribe("test", new MessageHandler<>() {
            @Override
            public Object handleSync(CommandMessage<?> message) {
                return null;
            }

            @Override
            public MessageStream<? extends Message<?>> handle(CommandMessage<?> message,
                                                              ProcessingContext processingContext) {
                spanFactory.verifySpanActive("CommandBus.handleCommand");
                throw new MockException("Simulating failure");
            }
        });

        try {
            captor.getValue().handle(GenericCommandMessage.asCommandMessage("Test"), ProcessingContext.NONE);
            fail("Expected a MockException to be thrown from handling a command!");
        } catch (MockException e) {
            spanFactory.verifySpanCompleted("CommandBus.handleCommand");
            spanFactory.verifySpanHasException("CommandBus.handleCommand", MockException.class);
        }
    }

    @Test
    void verifyDescriptionContainsComponents() {
        ComponentDescriptor componentDescriptor = mock(ComponentDescriptor.class);
        testSubject.describeTo(componentDescriptor);
        verify(componentDescriptor).describeWrapperOf(delegate);
        verify(componentDescriptor).describeProperty("spanFactory", commandBusSpanFactory);
    }
}