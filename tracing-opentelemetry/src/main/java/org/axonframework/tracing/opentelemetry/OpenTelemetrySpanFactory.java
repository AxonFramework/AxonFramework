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
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.SpanFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.axonframework.tracing.SpanUtils.determineMessageName;

/**
 * Creates {@link Span} implementations that are compatible with OpenTelemetry java agent instrumentation. OpenTelemetry
 * is a standard to collect logging, tracing and metrics from applications. This {@link SpanFactory} focusses on
 * supporting the tracing part of the standard.
 * <p>
 * To get started with OpenTelemetry, <a href="https://opentelemetry.io/docs/">check out their documentation</a>.
 */
public class OpenTelemetrySpanFactory implements SpanFactory {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("axon-opentelemetry");
    private final List<SpanAttributesProvider> spanAttributesProviders;
    private final boolean useParentsInsteadOfLinks;
    private final TextMapPropagator textMapPropagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

    /**
     * Creates a new {@link SpanFactory} that creates {@link Span} implementations that are compatible with
     * OpenTelemetry.
     *
     * @param spanAttributesProviders A list of {@link SpanAttributesProvider}s, which add metadata to span based on the
     *                                input of a {@link Message}.
     */
    public OpenTelemetrySpanFactory(List<SpanAttributesProvider> spanAttributesProviders) {
        this(spanAttributesProviders, false);
    }

    /**
     * Creates a new {@link SpanFactory} that creates {@link Span} implementations that are compatible with
     * OpenTelemetry.
     *
     * @param spanAttributesProviders  A list of {@link SpanAttributesProvider}s, which add metadata to span based on
     *                                 the input of a {@link Message}.
     * @param useParentsInsteadOfLinks To make traces comprehensible, we can split them up and link them together
     *                                 instead of forming one big trace. Supplying true as an argument will create large
     *                                 traces instead.
     */
    public OpenTelemetrySpanFactory(List<SpanAttributesProvider> spanAttributesProviders,
                                    boolean useParentsInsteadOfLinks) {
        this.spanAttributesProviders = spanAttributesProviders;
        this.useParentsInsteadOfLinks = useParentsInsteadOfLinks;
    }


    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        HashMap<String, String> additionalMetadataProperties = new HashMap<>();
        textMapPropagator.inject(Context.current(), additionalMetadataProperties, MetadataContextSetter.INSTANCE);
        return (M) message.andMetaData(additionalMetadataProperties);
    }

    @Override
    public Span createRootTrace(String operationName) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                                        .setSpanKind(SpanKind.INTERNAL);
        if (!useParentsInsteadOfLinks) {
            spanBuilder.addLink(io.opentelemetry.api.trace.Span.current().getSpanContext()).setNoParent();
        }
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createHandlerSpan(String operationName, Message<?> parentMessage, boolean forceSameTrace) {
        Context parentContext = textMapPropagator.extract(Context.current(),
                                                          parentMessage,
                                                          MetadataContextGetter.INSTANCE);
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, parentMessage))
                                        .setSpanKind(SpanKind.CONSUMER);
        if (forceSameTrace || (useParentsInsteadOfLinks && !messageIsOld(parentMessage))) {
            spanBuilder.setParent(parentContext);
        } else {
            spanBuilder.addLink(io.opentelemetry.api.trace.Span.fromContext(parentContext).getSpanContext())
                       .setNoParent();
        }
        addMessageAttributes(spanBuilder, parentMessage);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createDispatchSpan(String operationName, Message<?> parentMessage) {
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, parentMessage))
                                        .setSpanKind(SpanKind.PRODUCER);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createInternalSpan(String operationName) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                                        .setSpanKind(SpanKind.INTERNAL);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createInternalSpan(String operationName, Message<?> message) {
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, message))
                                        .setSpanKind(SpanKind.INTERNAL);
        addMessageAttributes(spanBuilder, message);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public void registerTagProvider(SpanAttributesProvider provider) {
        spanAttributesProviders.add(provider);
    }

    /**
     * Determines whether the message is old that we can disregard it as part of the older trace, two minutes by
     * default.
     * <p>
     * This prevents replays of event messages being added to very old command traces, if the
     * {@code useParentsInsteadOfLinks} option is enabled.
     *
     * @param message The message to determine for whether it's too old
     * @return Whether it's too old to be part of the original trace
     */
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
