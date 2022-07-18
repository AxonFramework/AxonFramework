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

package org.axonframework.tracing.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.tracing.AxonSpan;
import org.axonframework.tracing.AxonSpanFactory;
import org.axonframework.tracing.AxonSpanKind;
import org.axonframework.tracing.TagProvider;
import org.axonframework.tracing.tags.AggregateIdentifierTagProvider;
import org.axonframework.tracing.tags.MessageIdTagProvider;
import org.axonframework.tracing.tags.MessageNameTagProvider;
import org.axonframework.tracing.tags.MessageTypeTagProvider;
import org.axonframework.tracing.tags.MetadataTagProvider;
import org.axonframework.tracing.tags.PayloadTypeTagProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class OpenTelemetryAxonSpanFactory implements AxonSpanFactory {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("axon-opentelemetry");
    private final List<TagProvider> tagProviders;
    private final MetadataContextGetter textMapGetter = new MetadataContextGetter();
    private final MetadataContextSetter textMapSetter = new MetadataContextSetter();
    private final TextMapPropagator textMapPropagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

    public OpenTelemetryAxonSpanFactory(List<TagProvider> tagProviders) {
        this.tagProviders = tagProviders;
    }

    public OpenTelemetryAxonSpanFactory() {
        this(Arrays.asList(
                new AggregateIdentifierTagProvider(),
                new MessageIdTagProvider(),
                new MessageNameTagProvider(),
                new MessageTypeTagProvider(),
                new PayloadTypeTagProvider(),
                new MetadataTagProvider()
        ));
    }


    public <M extends Message<?>> M propagateContext(M message) {
        HashMap<String, String> additionalMetadataProperties = new HashMap<>();
        textMapPropagator.inject(Context.current(), additionalMetadataProperties, textMapSetter);
        return (M) message.andMetaData(additionalMetadataProperties);
    }

    @Override
    public AxonSpan create(String operationName) {
        return new OpenTelemetryAxonSpan(operationName);
    }

    @Override
    public void registerTagProvider(TagProvider provider) {
        tagProviders.add(provider);
    }

    public class OpenTelemetryAxonSpan implements AxonSpan {

        private final SpanBuilder spanBuilder;
        private Span startedSpan;

        protected OpenTelemetryAxonSpan(String operationName) {
            this.spanBuilder = tracer.spanBuilder(operationName);
        }

        @Override
        public AxonSpan withMessageAsParent(Message<?> message) {
            if(message instanceof EventMessage<?>) {
                Instant timestamp = ((EventMessage<?>) message).getTimestamp();
                if(Instant.now().isBefore(timestamp.minus(5, ChronoUnit.MINUTES))) {
                    // This is an EventMessage more than 5 minutes old. Let's not make it part of the command trace.
                    return this;
                }
            }
            Context parentContext = textMapPropagator.extract(Context.current(), message, textMapGetter);
            parentContext.makeCurrent();
            return this;
        }

        @Override
        public AxonSpan withSpanKind(AxonSpanKind spanKind) {
            if (spanKind == AxonSpanKind.PRODUCER) {
                spanBuilder.setSpanKind(SpanKind.PRODUCER);
            } else if (spanKind == AxonSpanKind.HANDLER) {
                spanBuilder.setSpanKind(SpanKind.CONSUMER);
            } else if (spanKind == AxonSpanKind.INTERNAL) {
                spanBuilder.setSpanKind(SpanKind.INTERNAL);
            }
            return this;
        }

        @Override
        public AxonSpan withMessageAttributes(Message<?> message) {
            tagProviders.forEach(supplier -> {
                Map<String, String> attributes = supplier.provideForMessage(message);
                if (attributes != null) {
                    attributes.forEach(spanBuilder::setAttribute);
                }
            });
            return this;
        }

        public AxonSpan start() {
            if (startedSpan == null) {
                startedSpan = spanBuilder.startSpan();
                startedSpan.makeCurrent();
            }
            return this;
        }

        public void end() {
            Objects.requireNonNull(startedSpan, "Span was not started yet!");
            startedSpan.end();
        }


        @Override
        public AxonSpan recordException(Throwable t) {
            Objects.requireNonNull(startedSpan, "Span was not started yet!");
            startedSpan.recordException(t);
            startedSpan.setStatus(StatusCode.ERROR, t.getMessage());
            startedSpan.end();
            return this;
        }

        @Override
        public <T> T wrap(Supplier<T> supplier) {
            this.start();
            try (Scope unused = startedSpan.makeCurrent()) {
                return supplier.get();
            } catch (Exception e) {
                this.recordException(e);
                throw e;
            } finally {
                this.end();
            }
        }

        @Override
        public void run(Runnable runnable) {
            this.start();
            try (Scope unused = startedSpan.makeCurrent()) {
                runnable.run();
            } catch (Exception e) {
                this.recordException(e);
                throw e;
            } finally {
                this.end();
            }
        }

        @Override
        public Runnable wrap(Runnable runnable) {
            this.start();
            return () -> {
                try (Scope unused = startedSpan.makeCurrent()) {
                    runnable.run();
                } catch (Exception e) {
                    this.recordException(e);
                    throw e;
                } finally {
                    this.end();
                }
            };
        }

        @Override
        public <T> T wrapCallable(Callable<T> callable) throws Exception {
            this.start();
            try (Scope unused = startedSpan.makeCurrent()) {
                return callable.call();
            } catch (Exception e) {
                this.recordException(e);
                throw e;
            } finally {
                this.end();
            }
        }
    }
}
