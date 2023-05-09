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

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link Span} implementation that uses OpenTelemetry's {@link io.opentelemetry.api.trace.Span} to provide tracing
 * capabilities to an application.
 * <p>
 * These traces should always be created using the {@link OpenTelemetrySpanFactory} since this will make sure the proper
 * parent context is extracted before creating the {@link Span}.
 * <p>
 * {@inheritDoc}
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class OpenTelemetrySpan implements Span {

    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetrySpan.class);

    private final SpanBuilder spanBuilder;
    private final List<Scope> scopes = new CopyOnWriteArrayList<>();
    private io.opentelemetry.api.trace.Span span = null;
    private boolean ended = false;

    /**
     * Creates the span, based on the {@link SpanBuilder} provided. This {@link SpanBuilder} will supply the
     * {@link io.opentelemetry.api.trace.Span} when the {@link #start()} method is invoked.
     *
     * @param spanBuilder The provider of the {@link io.opentelemetry.api.trace.Span}.
     */
    public OpenTelemetrySpan(SpanBuilder spanBuilder) {
        Objects.requireNonNull(spanBuilder, "Span builder can not be null!");
        this.spanBuilder = spanBuilder;
    }

    @Override
    public Span start() {
        if (span == null) {
            span = spanBuilder.startSpan();
        } else {
            logger.warn("An attempt was made to start span with id [{}] of trace [{}] a second time",
                         span.getSpanContext().getSpanId(),
                         span.getSpanContext().getTraceId());
        }
        return this;
    }

    @Override
    public SpanScope makeCurrent() {
        if (span == null) {
            logger.warn(
                    "Span was attempted to be made current while not started yet! Please report this to the Axon Framework team.",
                    new IllegalStateException("Span attempted to be made current while not started"));
            // Return empty scope as to not influence user's code
            return () -> {
            };
        }
        Scope scope = span.makeCurrent();
        scopes.add(scope);
        return () -> {
            scopes.remove(scope);
            scope.close();
        };
    }


    @Override
    public void end() {
        if (span == null) {
            logger.warn(
                    "Span was attempted to be ended while not started yet! Please report this to the Axon Framework team.",
                    new IllegalStateException("Span attempted to be ended while not started"));
            return;
        }
        if (ended) {
            logger.warn(
                    "Span ended a second time! Will ignore this ended invocation. Please report this to the Axon Framework team.",
                    new IllegalStateException("Span ended a second time"));
            return;
        }
        if (!scopes.isEmpty()) {
            logger.warn(
                    "Span ended without all scopes! Please report this to the Axon Framework team. This might influence reliability of your OpenTelemetry traces.",
                    new IllegalStateException("Span ended with still " + scopes.size() + " open!"));
        }
        span.end();
        ended = true;
    }

    @Override
    public Span recordException(Throwable t) {
        span.recordException(t);
        span.setStatus(StatusCode.ERROR, t.getMessage());
        return this;
    }
}
