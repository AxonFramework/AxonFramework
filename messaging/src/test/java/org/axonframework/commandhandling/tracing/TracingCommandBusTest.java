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
import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TracingCommandBusTest {

    private TracingCommandBus testSubject;
    private TestSpanFactory spanFactory;
    private CommandBus delegate;

    @BeforeEach
    void setUp() {
        delegate = mock(CommandBus.class);
        spanFactory = new TestSpanFactory();
        testSubject = new TracingCommandBus(delegate, DefaultCommandBusSpanFactory.builder()
                                                                                  .spanFactory(spanFactory)
                                                                                  .build());
    }

    @Test
    void dispatchIsCorrectlyTraced() {
        when(delegate.dispatch(any(), any())).thenAnswer(
                i -> {
                    spanFactory.verifySpanActive("CommandBus.dispatchCommand");
                    spanFactory.verifySpanPropagated("CommandBus.dispatchCommand", i.getArgument(0, CommandMessage.class));
                    spanFactory.verifySpanCompleted("CommandBus.handleCommand");
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
                    spanFactory.verifySpanPropagated("CommandBus.dispatchCommand", i.getArgument(0, CommandMessage.class));
                    spanFactory.verifySpanCompleted("CommandBus.handleCommand");
                    return CompletableFuture.failedFuture(new RuntimeException("Some exception"));
                });
        testSubject.subscribe(String.class.getName(), command -> {
            throw new RuntimeException("Some exception");
        });
        var actual = testSubject.dispatch(asCommandMessage("Say hi!"),ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());

        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
        spanFactory.verifySpanHasException("CommandBus.dispatchCommand", RuntimeException.class);
    }

}