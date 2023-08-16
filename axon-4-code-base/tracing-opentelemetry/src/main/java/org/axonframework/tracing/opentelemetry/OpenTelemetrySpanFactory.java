/*
 * Copyright (c) 2010-2023. Axon Framework
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
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.axonframework.common.BuilderUtils;
import org.axonframework.messaging.Message;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.SpanFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.axonframework.tracing.SpanUtils.determineMessageName;

/**
 * Creates {@link Span} implementations that are compatible with OpenTelemetry java agent instrumentation. OpenTelemetry
 * is a standard to collect logging, tracing and metrics from applications. This {@link SpanFactory} focuses on
 * supporting the tracing part of the standard.
 * <p>
 * To get started with OpenTelemetry, <a href="https://opentelemetry.io/docs/">check out their documentation</a>. Note
 * that, even after configuring the correct dependencies, you still need to run the application using the OpenTelemetry
 * java agent to export data. Without this, it will have the same effect as the
 * {@link org.axonframework.tracing.NoOpSpanFactory} since the data is not sent anywhere.
 *
 * <p>
 * When creating a trace, the context of the current trace is used as a parent, instead of the one active at the time of
 * starting the span (Default OpenTelemetry behavior). This is done using {@link SpanBuilder#setParent(Context)}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class OpenTelemetrySpanFactory implements SpanFactory {

    private final Tracer tracer;
    private final TextMapPropagator textMapPropagator;
    private final List<SpanAttributesProvider> spanAttributesProviders;
    private final TextMapGetter<Message<?>> textMapGetter;
    private final TextMapSetter<Map<String, String>> textMapSetter;

    /**
     * Instantiate a {@link OpenTelemetrySpanFactory} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link OpenTelemetrySpanFactory} instance.
     */
    public OpenTelemetrySpanFactory(Builder builder) {
        this.spanAttributesProviders = builder.spanAttributesProviders;
        this.tracer = builder.tracer;
        this.textMapPropagator = builder.textMapPropagator;
        this.textMapGetter = builder.textMapGetter;
        this.textMapSetter = builder.textMapSetter;
    }

    /**
     * Instantiate a Builder to create a {@link OpenTelemetrySpanFactory}.
     * <p>
     * The {@link SpanAttributesProvider SpanAttributeProvieders} are defaulted to an empty list, and the {@link Tracer}
     * is defaulted to the tracer defined by {@link GlobalOpenTelemetry}.
     *
     * @return a Builder able to create a {@link OpenTelemetrySpanFactory}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Message<?>> M propagateContext(M message) {
        HashMap<String, String> additionalMetadataProperties = new HashMap<>();
        textMapPropagator.inject(Context.current(), additionalMetadataProperties, textMapSetter);
        return (M) message.andMetaData(additionalMetadataProperties);
    }

    @Override
    public Span createRootTrace(Supplier<String> operationNameSupplier) {
        SpanBuilder spanBuilder = createSpanBuilder(operationNameSupplier.get(), SpanKind.INTERNAL)
                .addLink(io.opentelemetry.api.trace.Span.current().getSpanContext())
                .setNoParent();
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createHandlerSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                  boolean isChildTrace,
                                  Message<?>... linkedParents) {
        Context parentContext = textMapPropagator.extract(Context.current(),
                                                          parentMessage,
                                                          textMapGetter);
        SpanBuilder spanBuilder = createSpanBuilder(formatName(operationNameSupplier.get(), parentMessage),
                                                    SpanKind.CONSUMER);
        if (isChildTrace) {
            spanBuilder.setParent(parentContext);
        } else {
            spanBuilder.addLink(io.opentelemetry.api.trace.Span.fromContext(parentContext).getSpanContext())
                       .setNoParent();
        }
        addLinks(spanBuilder, linkedParents);
        addMessageAttributes(spanBuilder, parentMessage);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createDispatchSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                   Message<?>... linkedSiblings) {
        SpanBuilder spanBuilder = createSpanBuilderWithCurrentContext(
                formatName(operationNameSupplier.get(), parentMessage),
                SpanKind.PRODUCER);
        addLinks(spanBuilder, linkedSiblings);
        addMessageAttributes(spanBuilder, parentMessage);
        return new OpenTelemetrySpan(spanBuilder);
    }

    private void addLinks(SpanBuilder spanBuilder, Message<?>[] linkedMessages) {
        for (Message<?> message : linkedMessages) {
            Context linkedContext = textMapPropagator.extract(Context.current(),
                                                              message,
                                                              textMapGetter);
            spanBuilder.addLink(io.opentelemetry.api.trace.Span.fromContext(linkedContext).getSpanContext());
        }
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier) {
        SpanBuilder spanBuilder = createSpanBuilderWithCurrentContext(
                operationNameSupplier.get(),
                SpanKind.INTERNAL
        );
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier, Message<?> message) {
        SpanBuilder spanBuilder = createSpanBuilderWithCurrentContext(
                formatName(operationNameSupplier.get(), message),
                SpanKind.INTERNAL
        );
        addMessageAttributes(spanBuilder, message);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public void registerSpanAttributeProvider(SpanAttributesProvider provider) {
        spanAttributesProviders.add(provider);
    }

    private String formatName(String operationName, Message<?> message) {
        if (message == null) {
            return operationName;
        }
        return String.format("%s(%s)",
                             operationName,
                             determineMessageName(message));
    }

    private void addMessageAttributes(SpanBuilder spanBuilder, Message<?> message) {
        if (message == null) {
            return;
        }
        spanAttributesProviders.forEach(supplier -> {
            Map<String, String> attributes = supplier.provideForMessage(message);
            attributes.forEach(spanBuilder::setAttribute);
        });
    }

    /**
     * Use the context at moment of creation as parent, not at moment of start. Keep this call, it will break trace
     * correlation otherwise.
     */
    private SpanBuilder createSpanBuilderWithCurrentContext(String name, SpanKind kind) {
        return createSpanBuilder(name, kind).setParent(Context.current());
    }

    private SpanBuilder createSpanBuilder(String name, SpanKind kind) {
        return tracer.spanBuilder(name).setSpanKind(kind);
    }

    /**
     * Builder class to instantiate a {@link OpenTelemetrySpanFactory}.
     * <p>
     * The {@link SpanAttributesProvider SpanAttributeProvieders} are defaulted to an empty list, the {@link Tracer} is
     * defaulted to the tracer defined by {@link GlobalOpenTelemetry}, the {@link TextMapSetter} is defaulted to the
     * {@link MetadataContextSetter} and the {@link TextMapGetter} is defaulted to the {@link MetadataContextGetter}.
     */
    public static class Builder {

        private Tracer tracer = null;
        private TextMapPropagator textMapPropagator = null;
        private TextMapSetter<Map<String, String>> textMapSetter = MetadataContextSetter.INSTANCE;
        private TextMapGetter<Message<?>> textMapGetter = MetadataContextGetter.INSTANCE;

        private final List<SpanAttributesProvider> spanAttributesProviders = new LinkedList<>();

        /**
         * Adds all provided {@link SpanAttributesProvider}s to the {@link SpanFactory}.
         *
         * @param attributesProviders The {@link SpanAttributesProvider}s to add.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder addSpanAttributeProviders(@Nonnull List<SpanAttributesProvider> attributesProviders) {
            BuilderUtils.assertNonNull(attributesProviders, "The attributesProviders should not be null");
            spanAttributesProviders.addAll(attributesProviders);
            return this;
        }

        /**
         * Sets the propagator to be used. A propagator is used to propagate the current context into a message, so it
         * can become the parent of another span despite being in another system.
         *
         * @param propagator The propagator to be used.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder contextPropagators(TextMapPropagator propagator) {
            this.textMapPropagator = propagator;
            return this;
        }

        /**
         * Defines the {@link Tracer} from OpenTelemetry to use.
         *
         * @param tracer The {@link Tracer} to configure for use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder tracer(@Nonnull Tracer tracer) {
            BuilderUtils.assertNonNull(tracer, "The Tracer should not be null");
            this.tracer = tracer;
            return this;
        }

        /**
         * Defines the {@link TextMapSetter} to use, which is used for propagating the context to another thread or
         * service.
         *
         * @param textMapSetter The {@link TextMapSetter} to configure for use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder textMapSetter(TextMapSetter<Map<String, String>> textMapSetter) {
            BuilderUtils.assertNonNull(textMapSetter, "The TextMapSetter should not be null");
            this.textMapSetter = textMapSetter;
            return this;
        }

        /**
         * Defines the {@link TextMapGetter} to use, which is used for extracting the propagated context from another
         * thread or service.
         *
         * @param textMapGetter The {@link TextMapGetter} to configure for use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder textMapGetter(TextMapGetter<Message<?>> textMapGetter) {
            BuilderUtils.assertNonNull(textMapGetter, "The TextMapGetter should not be null");
            this.textMapGetter = textMapGetter;
            return this;
        }

        /**
         * Initializes the {@link OpenTelemetrySpanFactory}.
         *
         * @return The created {@link OpenTelemetrySpanFactory} with the provided configuration.
         */
        public OpenTelemetrySpanFactory build() {
            if (tracer == null) {
                tracer = GlobalOpenTelemetry.getTracer("AxonFramework-OpenTelemetry");
            }
            if (textMapPropagator == null) {
                textMapPropagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
            }
            return new OpenTelemetrySpanFactory(this);
        }
    }
}
