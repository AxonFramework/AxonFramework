/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.tracing;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@link LoggingSpanFactory} only logs statement, but should still provide basic requirements such as returning a
 * non-null span, and the span returning itself in certain situations.
 */
class LoggingSpanFactoryTest {

    @Test
    void createRootTraceReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createRootTrace(() -> "Trace");
        assertNotNull(trace);
    }

    @Test
    void createHandlerSpanReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createHandlerSpan(() -> "Trace",
                                                                   new GenericEventMessage<>("payload"),
                                                                   true);
        assertNotNull(trace);
    }

    @Test
    void createDispatchSpanReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createDispatchSpan(() -> "Trace",
                                                                    new GenericEventMessage<>("payload"));
        assertNotNull(trace);
    }

    @Test
    void createInternalSpanWithMessageReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace",
                                                                    new GenericEventMessage<>("payload"));
        assertNotNull(trace);
    }

    @Test
    void createInternalSpanWithoutMessageReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace");
        assertNotNull(trace);
    }

    @Test
    void propagateContextReturnsOriginal() {
        GenericEventMessage<String> message = new GenericEventMessage<>("payload");
        GenericEventMessage<String> result = NoOpSpanFactory.INSTANCE.propagateContext(message);
        assertSame(message, result);
    }

    @Test
    void internalSpanCanBeStartedAndEnded() {
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace");
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
    }

    @Test
    void internalSpanCanBeStartedAndEndedWithUnitOfWorkActive() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("My command");
        DefaultUnitOfWork<CommandMessage<Object>> uow = new DefaultUnitOfWork<>(command);
        uow.start();
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace");
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
        uow.commit();
    }

    @Test
    void handlingSpanCanBeStartedAndEnded() {
        GenericEventMessage message = new GenericEventMessage("payload");
        Span trace = LoggingSpanFactory.INSTANCE.createHandlerSpan(() -> "Trace", message, true);
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
    }

    @Test
    void rootSpanCanBeStartedAndEnded() {
        Span trace = LoggingSpanFactory.INSTANCE.createRootTrace(() -> "Trace");
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
    }

    @Test
    void dispatchSpanCanBeStartedAndEnded() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("My command");
        Span trace = LoggingSpanFactory.INSTANCE.createDispatchSpan(() -> "Trace", command);
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
    }

    @Test
    void dispatchSpanCanBeStartedAndEndedWhileUnitOfWorkActive() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("My command");
        DefaultUnitOfWork<CommandMessage<Object>> uow = new DefaultUnitOfWork<>(command);
        uow.start();
        GenericEventMessage message = new GenericEventMessage("payload");
        Span trace = LoggingSpanFactory.INSTANCE.createDispatchSpan(() -> "Trace", message);
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
        uow.commit();
    }

    @Test
    void internalSpanWithMessageCanBeStartedAndEnded() {
        GenericEventMessage message = new GenericEventMessage("payload");
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace", message);
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
    }

    @Test
    void internalSpanWithMessageCanBeStartedAndEndedWhileUnitOfWorkActive() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("My command");
        DefaultUnitOfWork<CommandMessage<Object>> uow = new DefaultUnitOfWork<>(command);
        uow.start();
        GenericEventMessage message = new GenericEventMessage("payload");
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace", message);
        trace.start()
             .recordException(new RuntimeException("My test exception"))
             .end();
        uow.commit();
    }
}
