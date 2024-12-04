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

package org.axonframework.tracing;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@link NoOpSpanFactory} is not supposed to do anything, but should still provide basic requirements such as
 * returning a non-null span, and the span returning itself in certain situations.
 */
class NoOpSpanFactoryTest {

    private static final EventMessage<String> TEST_EVENT =
            new GenericEventMessage<>(new QualifiedName("test", "event", "0.0.1"), "payload");

    @Test
    void createRootTraceReturnsNoOpSpan() {
        Span trace = NoOpSpanFactory.INSTANCE.createRootTrace(() -> "Trace");
        assertInstanceOf(NoOpSpanFactory.NoOpSpan.class, trace);
    }

    @Test
    void createHandlerSpanReturnsNoOpSpan() {
        Span trace = NoOpSpanFactory.INSTANCE.createHandlerSpan(() -> "Trace", TEST_EVENT, true);
        assertInstanceOf(NoOpSpanFactory.NoOpSpan.class, trace);
    }

    @Test
    void createDispatchSpanReturnsNoOpSpan() {
        Span trace = NoOpSpanFactory.INSTANCE.createDispatchSpan(() -> "Trace", TEST_EVENT);
        assertInstanceOf(NoOpSpanFactory.NoOpSpan.class, trace);
    }

    @Test
    void createInternalSpanWithMessageReturnsNoOpSpan() {
        Span trace = NoOpSpanFactory.INSTANCE.createInternalSpan(() -> "Trace", TEST_EVENT);
        assertInstanceOf(NoOpSpanFactory.NoOpSpan.class, trace);
    }

    @Test
    void createInternalSpanWithoutMessageReturnsNoOpSpan() {
        Span trace = NoOpSpanFactory.INSTANCE.createInternalSpan(() -> "Trace");
        assertInstanceOf(NoOpSpanFactory.NoOpSpan.class, trace);
    }

    @Test
    void propagateContextReturnsOriginal() {
        EventMessage<String> message = TEST_EVENT;
        EventMessage<String> result = NoOpSpanFactory.INSTANCE.propagateContext(message);
        assertSame(message, result);
    }

    @Test
    void noOpSpanReturnsSelfOnStart() {
        NoOpSpanFactory.NoOpSpan noOpSpan = new NoOpSpanFactory.NoOpSpan();
        assertSame(noOpSpan, noOpSpan.start());
    }

    @Test
    void noOpSpanReturnsSelfOnRecordException() {
        NoOpSpanFactory.NoOpSpan noOpSpan = new NoOpSpanFactory.NoOpSpan();
        assertSame(noOpSpan, noOpSpan.recordException(new RuntimeException("")));
    }
}
