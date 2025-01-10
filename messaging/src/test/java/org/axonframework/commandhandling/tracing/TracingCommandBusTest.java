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

package org.axonframework.commandhandling.tracing;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.configuration.CommandHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.TestSpanFactory;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;

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
        CommandMessage<String> testCommand =
                new GenericCommandMessage<>(new QualifiedName("test", "command", "0.0.1"), "Say hi!");

        when(delegate.dispatch(any(), any())).thenAnswer(
                i -> {
                    spanFactory.verifySpanActive("CommandBus.dispatchCommand");
                    spanFactory.verifySpanPropagated("CommandBus.dispatchCommand",
                                                     i.getArgument(0, CommandMessage.class));
                    return FutureUtils.emptyCompletedFuture();
                }
        );

        testSubject.dispatch(testCommand, ProcessingContext.NONE);
        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
    }


    @Test
    void dispatchIsCorrectlyTracedDuringException() {
        CommandMessage<String> testCommand =
                new GenericCommandMessage<>(new QualifiedName("test", "command", "0.0.1"), "Say hi!");

        when(delegate.dispatch(any(), any())).thenAnswer(i -> {
            spanFactory.verifySpanPropagated("CommandBus.dispatchCommand",
                                             i.getArgument(0, CommandMessage.class));
            return CompletableFuture.failedFuture(new RuntimeException("Some exception"));
        });
        testSubject.subscribe(QualifiedNameUtils.fromClassName(String.class),
                              (command, context) -> {
                                  throw new RuntimeException("Some exception");
                              });

        var actual = testSubject.dispatch(testCommand, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());

        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
        spanFactory.verifySpanHasException("CommandBus.dispatchCommand", RuntimeException.class);
    }

    @Test
    void verifyHandlerSpansAreCreatedOnHandlerInvocation() {
        CommandMessage<String> testCommand =
                new GenericCommandMessage<>(new QualifiedName("test", "command", "0.0.1"), "Test");
        ArgumentCaptor<CommandHandler> captor = ArgumentCaptor.forClass(CommandHandler.class);
        when(delegate.subscribe(any(QualifiedName.class), captor.capture())).thenReturn(null);

        testSubject.subscribe(QualifiedNameUtils.fromDottedName("test"),
                              (command, processingContext) -> {
                                  spanFactory.verifySpanActive("CommandBus.handleCommand");
                                  return MessageStream.just(new GenericCommandResultMessage<>(
                                          new QualifiedName("test", "message", "0.0.1"), "ok"
                                  ));
                              });

        captor.getValue().handle(testCommand, ProcessingContext.NONE);
        spanFactory.verifySpanCompleted("CommandBus.handleCommand");
    }

    @Test
    void verifyHandlerSpansAreCompletedOnExceptionInHandlerInvocation() {
        CommandMessage<String> testCommand =
                new GenericCommandMessage<>(new QualifiedName("test", "command", "0.0.1"), "Test");
        ArgumentCaptor<CommandHandler> captor = ArgumentCaptor.forClass(CommandHandler.class);
        when(delegate.subscribe(any(QualifiedName.class), captor.capture())).thenReturn(null);

        testSubject.subscribe(QualifiedNameUtils.fromDottedName("test"),
                              (command, processingContext) -> {
                                  spanFactory.verifySpanActive("CommandBus.handleCommand");
                                  throw new MockException("Simulating failure");
                              });

        try {
            captor.getValue().handle(testCommand, ProcessingContext.NONE);
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