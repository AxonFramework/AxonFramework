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

package org.axonframework.messaging.commandhandling.tracing;

import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.tracing.TestSpanFactory;
import org.axonframework.common.util.MockException;
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
        CommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), "Say hi!");

        when(delegate.dispatch(any(), any())).thenAnswer(
                i -> {
                    spanFactory.verifySpanActive("CommandBus.dispatchCommand");
                    spanFactory.verifySpanPropagated("CommandBus.dispatchCommand",
                                                     i.getArgument(0, CommandMessage.class));
                    return FutureUtils.emptyCompletedFuture();
                }
        );

        testSubject.dispatch(testCommand, StubProcessingContext.forMessage(testCommand));
        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
    }


    @Test
    void dispatchIsCorrectlyTracedDuringException() {
        CommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), "Say hi!");

        when(delegate.dispatch(any(), any())).thenAnswer(i -> {
            spanFactory.verifySpanPropagated("CommandBus.dispatchCommand",
                                             i.getArgument(0, CommandMessage.class));
            return CompletableFuture.failedFuture(new RuntimeException("Some exception"));
        });
        testSubject.subscribe(new QualifiedName(String.class),
                              (command, context) -> {
                                  throw new RuntimeException("Some exception");
                              });

        var actual = testSubject.dispatch(testCommand, StubProcessingContext.forMessage(testCommand));

        assertTrue(actual.isCompletedExceptionally());

        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
        spanFactory.verifySpanHasException("CommandBus.dispatchCommand", RuntimeException.class);
    }

    @Test
    void verifyHandlerSpansAreCreatedOnHandlerInvocation() {
        CommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), "Test");
        ArgumentCaptor<CommandHandler> captor = ArgumentCaptor.forClass(CommandHandler.class);
        when(delegate.subscribe(any(QualifiedName.class), captor.capture())).thenReturn(null);

        testSubject.subscribe(testCommand.type().qualifiedName(),
                              (command, processingContext) -> {
                                  spanFactory.verifySpanActive("CommandBus.handleCommand");
                                  return MessageStream.just(new GenericCommandResultMessage(
                                          new MessageType("result"), "ok"
                                  ));
                              });

        captor.getValue().handle(testCommand, StubProcessingContext.forMessage(testCommand));
        spanFactory.verifySpanCompleted("CommandBus.handleCommand");
    }

    @Test
    void verifyHandlerSpansAreCompletedOnExceptionInHandlerInvocation() {
        CommandMessage testCommand = new GenericCommandMessage(new MessageType("command"), "Test");
        ArgumentCaptor<CommandHandler> captor = ArgumentCaptor.forClass(CommandHandler.class);
        when(delegate.subscribe(any(QualifiedName.class), captor.capture())).thenReturn(null);

        testSubject.subscribe(testCommand.type().qualifiedName(),
                              (command, processingContext) -> {
                                  spanFactory.verifySpanActive("CommandBus.handleCommand");
                                  throw new MockException("Simulating failure");
                              });

        try {
            captor.getValue().handle(testCommand, StubProcessingContext.forMessage(testCommand));
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