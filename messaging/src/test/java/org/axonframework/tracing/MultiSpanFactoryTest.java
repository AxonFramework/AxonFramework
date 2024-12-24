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

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiSpanFactoryTest {

    private final SpanFactory spanFactory1 = mock(SpanFactory.class);
    private final Span mockSpan1 = mock(Span.class);
    private final SpanFactory spanFactory2 = mock(SpanFactory.class);
    private final Span mockSpan2 = mock(Span.class);
    private final SpanFactory multiSpanFactory = new MultiSpanFactory(Arrays.asList(spanFactory1, spanFactory2));

    private final GenericEventMessage<?> message =
            new GenericEventMessage<>(new QualifiedName("test", "event", "0.0.1"), "payload");
    private final Supplier<String> stringSupplier = () -> "Trace";

    @Test
    void rootTracesCreatedWillDelegateToAllFactories() {
        when(spanFactory1.createRootTrace(any())).thenReturn(mockSpan1);
        when(spanFactory2.createRootTrace(any())).thenReturn(mockSpan2);

        Span span = multiSpanFactory.createRootTrace(() -> "Trace").start();

        Mockito.verify(mockSpan1).start();
        Mockito.verify(mockSpan2).start();

        Mockito.verify(mockSpan1, never()).end();
        Mockito.verify(mockSpan2, never()).end();

        RuntimeException exception = new RuntimeException("My Exception");
        span.recordException(exception).end();

        Mockito.verify(mockSpan1).end();
        Mockito.verify(mockSpan2).end();
        Mockito.verify(mockSpan1).recordException(exception);
        Mockito.verify(mockSpan2).recordException(exception);
    }

    @Test
    void handlerSpansCreatedWillDelegateToAllFactories() {
        multiSpanFactory.createHandlerSpan(stringSupplier, message, false);

        Mockito.verify(spanFactory1).createHandlerSpan(stringSupplier, message, false);
        Mockito.verify(spanFactory2).createHandlerSpan(stringSupplier, message, false);
    }

    @Test
    void dispatchSpansCreatedWillDelegateToAllFactories() {
        multiSpanFactory.createDispatchSpan(stringSupplier, message);

        Mockito.verify(spanFactory1).createDispatchSpan(stringSupplier, message);
        Mockito.verify(spanFactory2).createDispatchSpan(stringSupplier, message);
    }

    @Test
    void internalSpansCreatedWillDelegateToAllFactories() {
        multiSpanFactory.createInternalSpan(stringSupplier);

        Mockito.verify(spanFactory1).createInternalSpan(stringSupplier);
        Mockito.verify(spanFactory2).createInternalSpan(stringSupplier);
    }

    @Test
    void internalSpansWithMessageCreatedWillDelegateToAllFactories() {
        multiSpanFactory.createInternalSpan(stringSupplier, message);

        Mockito.verify(spanFactory1).createInternalSpan(stringSupplier, message);
        Mockito.verify(spanFactory2).createInternalSpan(stringSupplier, message);
    }

    @Test
    void registerSpanAttributeProviderWillDelegateToAllFactories() {
        SpanAttributesProvider provider = mock(SpanAttributesProvider.class);
        multiSpanFactory.registerSpanAttributeProvider(provider);

        Mockito.verify(spanFactory1).registerSpanAttributeProvider(provider);
        Mockito.verify(spanFactory2).registerSpanAttributeProvider(provider);
    }

    @Test
    void propagateContextDelegateToAllFactories() {
        Message original = mock(Message.class);
        Message modifiedFirst = mock(Message.class);
        Message modifiedSecond = mock(Message.class);

        when(spanFactory1.propagateContext(original)).thenReturn(modifiedFirst);
        when(spanFactory2.propagateContext(modifiedFirst)).thenReturn(modifiedSecond);

        Message result = multiSpanFactory.propagateContext(original);
        assertSame(result, modifiedSecond);
    }
}
