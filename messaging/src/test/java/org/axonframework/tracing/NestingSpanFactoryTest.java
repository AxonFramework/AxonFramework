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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NestingSpanFactoryTest {

    private final Clock clock = Mockito.spy(Clock.class);
    private final SpanFactory delegate = Mockito.mock(SpanFactory.class);
    private final Span mockSpan = Mockito.mock(Span.class);
    private final NestingSpanFactory spanFactory = NestingSpanFactory.builder()
                                                                     .clock(clock)
                                                                     .delegate(delegate)
                                                                     .build();
    private final Supplier<String> nameSupplier = () -> "spanName";
    EventMessage<Object> message = GenericEventMessage.asEventMessage("payload");

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.now());
    }

    @Test
    void createsNestedSpanIfRecentMessage() {

        spanFactory.createLinkedHandlerSpan(nameSupplier, message);

        verify(delegate).createHandlerSpan(nameSupplier, message, true);
    }

    @Test
    void createsLinkedSpanIfOldMessage() {
        EventMessage<Object> payload = GenericEventMessage.asEventMessage("payload");
        when(clock.instant()).thenReturn(Instant.now().plusSeconds(121));

        Supplier<String> nameSupplier = () -> "spanName";
        spanFactory.createLinkedHandlerSpan(nameSupplier, payload);

        verify(delegate).createHandlerSpan(nameSupplier, payload, false);
    }

    @Test
    void rootTracesCreatedWillDelegate() {
        when(delegate.createRootTrace(any())).thenReturn(mockSpan);

        spanFactory.createRootTrace(nameSupplier);
        verify(delegate).createRootTrace(nameSupplier);
    }

    @Test
    void dispatchSpansCreatedWillDelegate() {
        spanFactory.createDispatchSpan(nameSupplier, message);

        Mockito.verify(delegate).createDispatchSpan(nameSupplier, message);
    }

    @Test
    void internalSpansCreatedWillDelegate() {
        spanFactory.createInternalSpan(nameSupplier);

        Mockito.verify(delegate).createInternalSpan(nameSupplier);
    }

    @Test
    void internalSpansWithMessageCreatedWillDelegate() {
        spanFactory.createInternalSpan(nameSupplier, message);

        Mockito.verify(delegate).createInternalSpan(nameSupplier, message);
    }

    @Test
    void registerSpanAttributeProviderWillDelegate() {
        SpanAttributesProvider provider = mock(SpanAttributesProvider.class);
        spanFactory.registerSpanAttributeProvider(provider);

        Mockito.verify(delegate).registerSpanAttributeProvider(provider);
    }

    @Test
    void propagateContextDelegate() {
        Message original = mock(Message.class);
        Message modified = mock(Message.class);

        when(delegate.propagateContext(original)).thenReturn(modified);

        Message result = spanFactory.propagateContext(original);
        assertSame(result, modified);
    }
}
