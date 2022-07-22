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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.tracing.AxonSpan;
import org.axonframework.tracing.AxonSpanFactory;
import org.axonframework.tracing.SpanAttributesProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.axonframework.tracing.AxonSpanUtils.determineMessageName;

public class OpenTelemetryAxonSpanFactory implements AxonSpanFactory {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("axon-opentelemetry");
    private final List<SpanAttributesProvider> spanAttributesProviders;
    private final boolean useParentsInsteadOfLinks;
    private final TextMapPropagator textMapPropagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

    public OpenTelemetryAxonSpanFactory(List<SpanAttributesProvider> spanAttributesProviders,
                                        boolean useParentsInsteadOfLinks) {
        this.spanAttributesProviders = spanAttributesProviders;
        this.useParentsInsteadOfLinks = useParentsInsteadOfLinks;
    }


    public <M extends Message<?>> M propagateContext(M message) {
        HashMap<String, String> additionalMetadataProperties = new HashMap<>();
        textMapPropagator.inject(Context.current(), additionalMetadataProperties, MetadataContextSetter.INSTANCE);
        return (M) message.andMetaData(additionalMetadataProperties);
    }

    @Override
    public AxonSpan createRootTrace(String operationName) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                                        .setSpanKind(SpanKind.INTERNAL);
        if (!useParentsInsteadOfLinks) {
            spanBuilder.addLink(Span.current().getSpanContext()).setNoParent();
        }
        return new OpenTelemetryAxonSpan(spanBuilder);
    }

    @Override
    public AxonSpan createHandlerSpan(String operationName, Message<?> parentMessage, boolean forceSameTrace) {
        Context parentContext = textMapPropagator.extract(Context.current(),
                                                          parentMessage,
                                                          MetadataContextGetter.INSTANCE);
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, parentMessage))
                                        .setSpanKind(SpanKind.CONSUMER);
        if (forceSameTrace || (useParentsInsteadOfLinks && !messageIsOld(parentMessage))) {
            spanBuilder.setParent(parentContext);
        } else {
            spanBuilder.addLink(Span.fromContext(parentContext).getSpanContext()).setNoParent();
        }
        addMessageAttributes(spanBuilder, parentMessage);
        return new OpenTelemetryAxonSpan(spanBuilder);
    }

    @Override
    public AxonSpan createDispatchSpan(String operationName, Message<?> parentMessage) {
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, parentMessage))
                                        .setSpanKind(SpanKind.PRODUCER);
        return new OpenTelemetryAxonSpan(spanBuilder);
    }

    @Override
    public AxonSpan createInternalSpan(String operationName) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                                        .setSpanKind(SpanKind.INTERNAL);
        return new OpenTelemetryAxonSpan(spanBuilder);
    }

    @Override
    public AxonSpan createInternalSpan(String operationName, Message<?> message) {
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, message))
                                        .setSpanKind(SpanKind.INTERNAL);
        addMessageAttributes(spanBuilder, message);
        return new OpenTelemetryAxonSpan(spanBuilder);
    }

    @Override
    public void registerTagProvider(SpanAttributesProvider provider) {
        spanAttributesProviders.add(provider);
    }

    private boolean messageIsOld(Message<?> message) {
        if (message instanceof EventMessage<?>) {
            Instant timestamp = ((EventMessage<?>) message).getTimestamp();
            return Instant.now().isBefore(timestamp.minus(2, ChronoUnit.MINUTES));
        }
        return false;
    }

    private String formatName(String operationName, Message<?> message) {
        return String.format("%s %s",
                             operationName,
                             determineMessageName(message));
    }


    private void addMessageAttributes(SpanBuilder spanBuilder, Message<?> message) {
        if (message == null) {
            return;
        }
        spanAttributesProviders.forEach(supplier -> {
            Map<String, String> attributes = supplier.provideForMessage(message);
            if (attributes != null) {
                attributes.forEach(spanBuilder::setAttribute);
            }
        });
    }
}
