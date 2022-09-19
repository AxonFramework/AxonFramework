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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * {@link Span} implementation that uses OpenTelemetry's {@link io.opentelemetry.api.trace.Span} to provide tracing
 * capabilities to an application.
 * <p>
 * These traces should always be created using the {@link OpenTelemetrySpanFactory} since this will make sure the proper
 * parent context is extracted before creating the {@link Span}.
 * <p>
 * Each {@link #start()} should result in an {@link #end()} being called. Only when the last {@code end()} is called
 * will the OpenTelemetry span actually be ended. This is because {@link Scope}s that are kept active should be closed
 * before ending the span, or memory leaks can occur. This is also the reason the {@code scopes} are kept in a
 * {@link Deque}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class OpenTelemetrySpan implements Span {

    private final SpanBuilder spanBuilder;
    private final Deque<Scope> scopeQueue = new ArrayDeque<>();
    private io.opentelemetry.api.trace.Span span = null;

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
        }
        scopeQueue.addFirst(span.makeCurrent());
        return this;
    }

    @Override
    public void end() {
        if (!scopeQueue.isEmpty()) {
            scopeQueue.remove().close();
        }
        if (scopeQueue.isEmpty()) {
            span.end();
        }
    }

    @Override
    public Span recordException(Throwable t) {
        span.recordException(t);
        span.setStatus(StatusCode.ERROR, t.getMessage());
        return this;
    }
}
