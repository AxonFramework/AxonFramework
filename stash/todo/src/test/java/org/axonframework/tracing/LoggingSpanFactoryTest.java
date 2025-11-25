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

package org.axonframework.tracing;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.tracing.NoOpSpanFactory;
import org.axonframework.messaging.tracing.Span;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@link LoggingSpanFactory} only logs statement, but should still provide basic requirements such as returning a
 * non-null span, and the span returning itself in certain situations.
 */
// TODO #3594 - Be sure to return this instance once the UnitOfWork has been replaced
class LoggingSpanFactoryTest {

    private static final EventMessage TEST_EVENT =
            new GenericEventMessage(new MessageType("event"), "payload");
    private static final MessageType TEST_COMMAND_TYPE = new MessageType("command");

    @Test
    void createRootTraceReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createRootTrace(() -> "Trace");
        assertNotNull(trace);
    }

    @Test
    void createHandlerSpanReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createHandlerSpan(() -> "Trace", TEST_EVENT, true);
        assertNotNull(trace);
    }

    @Test
    void createDispatchSpanReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createDispatchSpan(() -> "Trace", TEST_EVENT);
        assertNotNull(trace);
    }

    @Test
    void createInternalSpanWithMessageReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace", TEST_EVENT);
        assertNotNull(trace);
    }

    @Test
    void createInternalSpanWithoutMessageReturnsNoOpSpan() {
        Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace");
        assertNotNull(trace);
    }

    @Test
    void propagateContextReturnsOriginal() {
        EventMessage message = TEST_EVENT;
        EventMessage result = NoOpSpanFactory.INSTANCE.propagateContext(message);
        assertSame(message, result);
    }

    @Test
    void internalSpanCanBeStartedAndEnded() {
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace");
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
        });
    }

    @Test
    void internalSpanCanBeStartedAndEndedWithUnitOfWorkActive() {
        CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "My command");
        LegacyDefaultUnitOfWork<CommandMessage> uow = new LegacyDefaultUnitOfWork<>(command);
        uow.start();
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace");
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
            uow.commit();
        });
    }

    @Test
    void handlingSpanCanBeStartedAndEnded() {
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createHandlerSpan(() -> "Trace", TEST_EVENT, true);
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
        });
    }

    @Test
    void rootSpanCanBeStartedAndEnded() {
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createRootTrace(() -> "Trace");
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
        });
    }

    @Test
    void dispatchSpanCanBeStartedAndEnded() {
        CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "My command");
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createDispatchSpan(() -> "Trace", command);
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
        });
    }

    @Test
    void dispatchSpanCanBeStartedAndEndedWhileUnitOfWorkActive() {
        CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "My command");
        LegacyDefaultUnitOfWork<CommandMessage> uow = new LegacyDefaultUnitOfWork<>(command);
        uow.start();
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createDispatchSpan(() -> "Trace", TEST_EVENT);
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
        });
        uow.commit();
    }

    @Test
    void internalSpanWithMessageCanBeStartedAndEnded() {
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace", TEST_EVENT);
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
        });
    }

    @Test
    void internalSpanWithMessageCanBeStartedAndEndedWhileUnitOfWorkActive() {
        CommandMessage command = new GenericCommandMessage(TEST_COMMAND_TYPE, "My command");
        LegacyDefaultUnitOfWork<CommandMessage> uow = new LegacyDefaultUnitOfWork<>(command);
        uow.start();
        assertDoesNotThrow(() -> {
            Span trace = LoggingSpanFactory.INSTANCE.createInternalSpan(() -> "Trace", TEST_EVENT);
            trace.start()
                 .recordException(new RuntimeException("My test exception"))
                 .end();
        });
        uow.commit();
    }
}
