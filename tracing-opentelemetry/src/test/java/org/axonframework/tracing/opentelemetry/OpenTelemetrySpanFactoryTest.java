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

package org.axonframework.tracing.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.tracing.SpanAttributesProvider;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenTelemetrySpanFactoryTest {

    private final static OpenTelemetry otelMock = mock(OpenTelemetry.class);
    private final Tracer tracer = mock(Tracer.class);
    private final TextMapPropagator textMapPropagator = mock(TextMapPropagator.class);
    private final SpanAttributesProvider spanAttributesProvider = mock(SpanAttributesProvider.class);
    private final SpanBuilder spanBuilder = mock(SpanBuilder.class);
    private final ContextKey<Object> parentKey = ContextKey.named("opentelemetry-trace-span-key");

    private OpenTelemetrySpanFactory factory;

    @BeforeEach
    void setUp() {
        // We need to mock out a lot of the OTEL functionality. Without java agent instrumentation, it only uses a NOOP
        // variant, which is useless for the scope of these tests.
        when(otelMock.getTracer(any())).thenReturn(tracer);
        when(otelMock.getPropagators()).thenReturn(() -> textMapPropagator);
        doAnswer(invocationOnMock -> {
            invocationOnMock.getArgument(2, MetadataContextSetter.class)
                            .set(invocationOnMock.getArgument(1), "traceparent", "MY_TRACE_PARENT");
            return null;
        }).when(textMapPropagator).inject(any(Context.class), any(), eq(MetadataContextSetter.INSTANCE));

        doAnswer(invocationOnMock -> {
            MetadataContextGetter getter = invocationOnMock.getArgument(2, MetadataContextGetter.class);
            Message message = invocationOnMock.getArgument(1, Message.class);
            Context context = invocationOnMock.getArgument(0, Context.class);
            return context.with(parentKey, getter.get(message, "traceparent"));
        }).when(textMapPropagator).extract(any(Context.class), any(Message.class), eq(MetadataContextGetter.INSTANCE));

        // Mock out span builder methods
        when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
        when(spanBuilder.addLink(any())).thenReturn(spanBuilder);
        when(spanBuilder.setNoParent()).thenReturn(spanBuilder);
        when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
        when(spanBuilder.setAttribute(anyString(), any())).thenReturn(spanBuilder);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);

        factory = OpenTelemetrySpanFactory.builder()
                                          .tracer(tracer)
                                          .addSpanAttributeProviders(Collections.singletonList(spanAttributesProvider))
                                          .textMapGetter(MetadataContextGetter.INSTANCE)
                                          .textMapSetter(MetadataContextSetter.INSTANCE)
                                          .build();
    }

    @BeforeAll
    static void beforeAll() {
        GlobalOpenTelemetry.set(otelMock);
    }

    @Test
    void testPropagatesContextInjectsMetadata() {
        EventMessage<Object> originalMessage = GenericEventMessage.asEventMessage("MyEvent");
        EventMessage<Object> modifiedMessage = factory.propagateContext(originalMessage);

        assertNotNull(modifiedMessage.getMetaData().get("traceparent"));
    }

    @Test
    void testCreateRootTracesCreatesSpanWithNoParentLinkedToCurrent() {
        SpanContext spanContext = Span.current().getSpanContext();
        org.axonframework.tracing.Span span = factory.createRootTrace("MyRootTrace");

        verify(spanBuilder).setNoParent();
        verify(spanBuilder).addLink(spanContext);
        verify(spanBuilder).setSpanKind(SpanKind.INTERNAL);
    }

    @Test
    void testCreateHandlerSpanExtractsParentContext() {
        Message<?> message = generateMessageWithTraceId("1");
        factory.createChildHandlerSpan("MyRootTrace", message);

        ArgumentCaptor<Context> parentCaptor = ArgumentCaptor.forClass(Context.class);
        verify(spanBuilder).setParent(parentCaptor.capture());
        verify(spanBuilder).setSpanKind(SpanKind.CONSUMER);
        assertEquals("1", parentCaptor.getValue().get(parentKey));
    }

    @Test
    void testCreateHandlerSpanAddsLinks() {
        Message<?> message = generateMessageWithTraceId("1");
        factory.createChildHandlerSpan("MyRootTrace", message, generateMessageWithTraceId("2"), generateMessageWithTraceId("3"));

        verify(spanBuilder).setParent(any());
        verify(spanBuilder).setSpanKind(SpanKind.CONSUMER);
        verify(spanBuilder, times(2)).addLink(any());
    }

    @Test
    void testCreateHandlerSpanExtractsLinkedContext() {
        Message<?> message = generateMessageWithTraceId("1");
        factory.createLinkedHandlerSpan("MyRootTrace", message);

        verify(spanBuilder, times(1)).addLink(any());
        verify(spanBuilder).setSpanKind(SpanKind.CONSUMER);
        verify(spanBuilder).setNoParent();
    }

    @Test
    void testCreateHandlerSpanAddsAttributes() {
        Message<?> message = generateMessageWithTraceId("1");
        when(spanAttributesProvider.provideForMessage(any())).thenReturn(Collections.singletonMap("myKey", "myValue"));
        factory.createLinkedHandlerSpan("MyRootTrace", message);

        verify(spanBuilder).setAttribute("myKey", "myValue");
    }

    @Test
    void testCreateDispatchSpanAddsLinks() {
        Message<?> message = generateMessageWithTraceId("1");
        factory.createDispatchSpan("MyRootTrace", message, generateMessageWithTraceId("2"), generateMessageWithTraceId("3"));

        verify(spanBuilder).setSpanKind(SpanKind.PRODUCER);
        verify(spanBuilder, times(2)).addLink(any());
    }

    @Test
    void testCreateDispatchSpanAddsAttributes() {
        Message<?> message = generateMessageWithTraceId("1");
        when(spanAttributesProvider.provideForMessage(any())).thenReturn(Collections.singletonMap("myKey", "myValue"));
        factory.createDispatchSpan("MyRootTrace", message);

        verify(spanBuilder).setAttribute("myKey", "myValue");
    }

    @Test
    void testCreateInternalSpanWithoutMessage() {
        factory.createInternalSpan("MyRootTrace");

        verify(spanBuilder).setSpanKind(SpanKind.INTERNAL);
    }

    @Test
    void testCreateInternalSpanAddsAttributes() {
        Message<?> message = generateMessageWithTraceId("1");
        when(spanAttributesProvider.provideForMessage(any())).thenReturn(Collections.singletonMap("myKey", "myValue"));
        factory.createInternalSpan("MyRootTrace", message);

        verify(spanBuilder).setSpanKind(SpanKind.INTERNAL);
        verify(spanBuilder).setAttribute("myKey", "myValue");
    }

    private Message<?> generateMessageWithTraceId(String traceId) {
        return GenericEventMessage.asEventMessage("MyEvent")
                                  .andMetaData(Collections.singletonMap("traceparent", traceId));
    }

    @Test
    void testBuilderRejectsNullTracer() {
        OpenTelemetrySpanFactory.Builder builder = OpenTelemetrySpanFactory.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.tracer(null));
    }

    @Test
    void testBuilderRejectsNullSpanAttributeProviders() {
        OpenTelemetrySpanFactory.Builder builder = OpenTelemetrySpanFactory.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.addSpanAttributeProviders(null));
    }

    @Test
    void testBuilderRejectsNullTextMapGetter() {
        OpenTelemetrySpanFactory.Builder builder = OpenTelemetrySpanFactory.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.textMapGetter(null));
    }

    @Test
    void testBuilderRejectsNullTextMapSetter() {
        OpenTelemetrySpanFactory.Builder builder = OpenTelemetrySpanFactory.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.textMapSetter(null));
    }
}
